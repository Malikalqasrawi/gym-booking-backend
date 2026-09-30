package com.mycompany.gymbooking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.dto.BookingResponse;
import com.mycompany.gymbooking.dto.PaymentStartResponse;
import com.mycompany.gymbooking.exception.ApiException;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.exception.ConflictException;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.Payment;
import com.mycompany.gymbooking.model.PaymentStatus;
import com.mycompany.gymbooking.payment.BookingPaidEvent;
import com.mycompany.gymbooking.payment.BookingRefundedEvent;
import com.mycompany.gymbooking.payment.GatewayPayment;
import com.mycompany.gymbooking.payment.GatewayPaymentStatus;
import com.mycompany.gymbooking.payment.GatewayRefund;
import com.mycompany.gymbooking.payment.PaymentGateway;
import com.mycompany.gymbooking.payment.PaymentOrder;
import com.mycompany.gymbooking.payment.StripeWebhookVerifier;
import com.mycompany.gymbooking.repository.BookingRepository;
import com.mycompany.gymbooking.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stripe payments and refunds for bookings. Payment status is always read from Stripe, never taken
 * from the client. Every operation locks the booking row first, so confirm, cancel and the webhook
 * can't interleave on the same booking.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final long paidCancelHours;
    private final String merchantName;
    private final StripeWebhookVerifier webhookVerifier;   // null when no webhook secret is configured

    public PaymentServiceImpl(BookingRepository bookingRepository,
                              PaymentRepository paymentRepository,
                              PaymentGateway gateway,
                              ApplicationEventPublisher events,
                              ObjectMapper objectMapper,
                              Clock clock,
                              @Value("${app.booking.paid-cancel-hours}") long paidCancelHours,
                              @Value("${app.payments.merchant-name}") String merchantName,
                              @Value("${app.payments.stripe.webhook-secret:}") String webhookSecret) {
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.paidCancelHours = paidCancelHours;
        this.merchantName = merchantName;
        this.webhookVerifier = webhookSecret.isBlank() ? null : new StripeWebhookVerifier(webhookSecret.trim(), clock);
    }

    /**
     * Calls Stripe inside the transaction while the booking is locked, so repeated taps can't create
     * two payments, at the cost of holding the lock for the duration of the Stripe call.
     */
    @Override
    @Transactional
    public PaymentStartResponse startPayment(Long memberId, Long bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = lockedMemberBooking(memberId, bookingId);
        booking.requirePayable(now);

        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseGet(() -> paymentRepository.save(new Payment(booking, gateway.provider(), now)));

        GatewayPayment gatewayPayment;
        if (payment.getProviderPaymentId() == null) {
            gatewayPayment = gateway.createPayment(new PaymentOrder(
                    idempotencyKey("payment", payment),
                    payment.getAmount(),
                    payment.getCurrency(),
                    "Session #" + booking.getId() + ": " + booking.getTrainer().getFullName() + ", " + BookingTexts.when(booking),
                    Map.of("booking_id", String.valueOf(booking.getId()),
                            "payment_id", String.valueOf(payment.getId()))));
            payment.attachProviderPayment(gatewayPayment.id());
        } else {
            gatewayPayment = gateway.getPayment(payment.getProviderPaymentId());
        }

        if (gatewayPayment.status() == GatewayPaymentStatus.CANCELED) {
            throw new ConflictException("PAYMENT_CANCELED", "This payment was cancelled in Stripe. Please contact the gym.");
        }

        return new PaymentStartResponse(
                booking.getId(),
                gatewayPayment.clientSecret(),
                gateway.publishableKey(),
                merchantName,
                payment.getAmount(),
                payment.getCurrency(),
                booking.payDeadline(),
                refundableUntil(booking),
                gatewayPayment.status() == GatewayPaymentStatus.SUCCEEDED);
    }

    /** noRollbackFor so the refund of a late payment is still committed when PAID_TOO_LATE is thrown. */
    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public BookingResponse confirmPayment(Long memberId, Long bookingId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = lockedMemberBooking(memberId, bookingId);
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .filter(p -> p.getProviderPaymentId() != null)
                .orElseThrow(() -> new ConflictException("PAYMENT_NOT_STARTED", "There's no payment for this booking yet."));

        if (payment.getStatus() == PaymentStatus.PENDING) {
            GatewayPayment gatewayPayment = gateway.getPayment(payment.getProviderPaymentId());
            switch (gatewayPayment.status()) {
                case SUCCEEDED -> settle(booking, payment, gatewayPayment, now);
                case PROCESSING -> throw new ConflictException("PAYMENT_PROCESSING",
                        "Your bank is still processing the payment. Pull down to refresh in a minute.");
                default -> throw new ConflictException("PAYMENT_NOT_COMPLETED",
                        "The payment wasn't completed, so nothing was charged. You can try again.");
            }
        }
        // Not PENDING means it was already settled, possibly by the webhook.
        return BookingResponse.from(booking, payment, now);
    }

    @Override
    @Transactional
    public void handleStripeWebhook(String payload, String signatureHeader) {
        if (webhookVerifier == null) {
            throw new NotFoundException("WEBHOOK_NOT_CONFIGURED", "The Stripe webhook isn't set up on this server.");
        }
        webhookVerifier.verify(payload, signatureHeader);

        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("MALFORMED_JSON", "Webhook body is not JSON");
        }
        String type = event.path("type").asText();
        if (!type.equals("payment_intent.succeeded")) {
            log.debug("Ignoring Stripe event {}", type);
            return;
        }

        String providerPaymentId = event.path("data").path("object").path("id").asText();
        Optional<Long> bookingId = paymentRepository.findBookingIdByProviderPaymentId(providerPaymentId);
        if (bookingId.isEmpty()) {
            log.info("Stripe webhook for a payment this app doesn't know: {}", providerPaymentId);
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Booking booking = bookingRepository.findLockedById(bookingId.get()).orElseThrow();
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElseThrow();
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;   // already settled by confirmPayment
        }

        // Re-fetch from Stripe rather than trusting the event body; this also provides the card details.
        GatewayPayment gatewayPayment = gateway.getPayment(providerPaymentId);
        if (gatewayPayment.status() != GatewayPaymentStatus.SUCCEEDED) {
            return;
        }
        try {
            settle(booking, payment, gatewayPayment, now);
        } catch (ConflictException e) {
            // Late payment (already refunded) or amount mismatch; return 200 so Stripe doesn't keep retrying.
            log.warn("Stripe webhook for booking {}: {}", booking.getId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public Payment refundCancelledBooking(Booking booking, LocalDateTime now) {
        Payment payment = paymentRepository.findByBookingId(booking.getId())
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .orElseThrow(() -> new IllegalStateException("Booking " + booking.getId() + " is PAID but has no completed payment"));

        GatewayRefund refund = gateway.refund(payment.getProviderPaymentId(), idempotencyKey("refund", payment));
        payment.markRefunded(refund.id(), now);
        events.publishEvent(refundedEvent(booking, payment, false));
        log.info("Booking {} cancelled: refunded {} {} ({})", booking.getId(), payment.getAmount(), payment.getCurrency(), refund.id());
        return payment;
    }

    /** Records a succeeded payment and marks the booking PAID, or refunds it if the booking is no longer payable. */
    private void settle(Booking booking, Payment payment, GatewayPayment gatewayPayment, LocalDateTime now) {
        if (gatewayPayment.amount().compareTo(payment.getAmount()) != 0
                || !gatewayPayment.currency().equalsIgnoreCase(payment.getCurrency())) {
            log.error("Payment {} doesn't match: Stripe charged {} {}, the booking costs {} {}", payment.getId(),
                    gatewayPayment.amount(), gatewayPayment.currency(), payment.getAmount(), payment.getCurrency());
            throw new ConflictException("PAYMENT_MISMATCH", "The paid amount doesn't match this booking. Please contact the gym.");
        }
        payment.markSucceeded(gatewayPayment.cardBrand(), gatewayPayment.cardLast4(), now);

        if (booking.canBePaidAt(now)) {
            booking.markPaid(now, refundableUntil(booking));
            events.publishEvent(paidEvent(booking, payment));   // emails are sent after commit
            log.info("Booking {} paid: {} {} with {}", booking.getId(), payment.getAmount(), payment.getCurrency(),
                    payment.getMethodLabel());
            return;
        }

        // The booking expired or was cancelled before the payment arrived: refund in full.
        booking.expireIfOverdue(now);   // persist EXPIRED so the expiry job doesn't send "nothing was charged"
        String status = booking.statusAt(now).name().toLowerCase();
        GatewayRefund refund = gateway.refund(payment.getProviderPaymentId(), idempotencyKey("refund", payment));
        payment.markRefunded(refund.id(), now);
        events.publishEvent(refundedEvent(booking, payment, true));
        log.info("Booking {} was {} when its payment arrived: refunded ({})", booking.getId(), status, refund.id());
        throw new ConflictException("PAID_TOO_LATE",
                "This booking had already " + (status.equals("expired") ? "expired" : "been " + status)
                        + " when the payment arrived, so the full amount was refunded.");
    }

    private LocalDateTime refundableUntil(Booking booking) {
        return booking.getStartsAt().minusHours(paidCancelHours);
    }

    /**
     * Includes the payment's creation time because Stripe keeps keys for 24 hours and payment ids
     * restart when the database is recreated, so a reused id must not collide with an old key.
     */
    private static String idempotencyKey(String action, Payment payment) {
        return "gym-" + action + "-" + payment.getId() + "-" + payment.getCreatedAt().format(KEY_TIME);
    }

    private Booking lockedMemberBooking(Long memberId, Long bookingId) {
        return bookingRepository.findLockedByIdAndMemberId(bookingId, memberId)
                .orElseThrow(() -> new NotFoundException("BOOKING_NOT_FOUND", "Booking not found"));
    }

    private BookingPaidEvent paidEvent(Booking booking, Payment payment) {
        return new BookingPaidEvent(
                booking.getId(),
                booking.getMember().getEmail(),
                booking.getMember().getFullName(),
                booking.getTrainer().getEmail(),
                booking.getTrainer().getFullName(),
                BookingTexts.when(booking),
                BookingTexts.where(booking),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethodLabel(),
                payment.getPaidAt(),
                booking.getRefundableUntil());
    }

    private BookingRefundedEvent refundedEvent(Booking booking, Payment payment, boolean paidTooLate) {
        return new BookingRefundedEvent(
                booking.getId(),
                booking.getMember().getEmail(),
                booking.getMember().getFullName(),
                booking.getTrainer().getFullName(),
                BookingTexts.when(booking),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethodLabel(),
                paidTooLate);
    }
}
