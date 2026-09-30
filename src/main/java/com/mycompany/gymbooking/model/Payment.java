package com.mycompany.gymbooking.model;

import com.mycompany.gymbooking.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The money for one booking (table "payments").
 *
 * What we store: the amount, Stripe's id for the payment ("pi_..."), and the card BRAND and LAST 4 DIGITS
 * for the receipt. What we never store (or even see): the card number, expiry date or CVC. Those go from
 * the phone straight to Stripe, which is what keeps our server out of card-data rules (PCI).
 *
 * ENCAPSULATION like Booking: no setters, only markSucceeded() and markRefunded(), which check the order.
 */
@Entity
@Table(name = "payments")
public class Payment {

    /** Every price in this app is in Jordanian dinars. */
    public static final String CURRENCY = "JOD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** unique = true: a booking can have only ONE payment, so it can't be charged twice. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    /** Who handles the money: "stripe". */
    @Column(nullable = false, length = 20)
    private String provider;

    /** Stripe's id for this payment, e.g. "pi_3Q1x...". Search for it in the Stripe Dashboard. */
    @Column(length = 100, unique = true)
    private String providerPaymentId;

    /** Copied from the booking when the payment starts. 3 decimals: 1 JOD = 1000 fils. */
    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // plain text column, see Booking.status
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** "visa", "mastercard"... (from Stripe, after paying) */
    @Column(length = 20)
    private String cardBrand;

    /** "4242" (from Stripe, after paying) */
    @Column(length = 4)
    private String cardLast4;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    /** Stripe's id for the refund, e.g. "re_3Q1x..." */
    @Column(length = 100)
    private String providerRefundId;

    private LocalDateTime refundedAt;

    @Version
    private Long version;

    /** Needed by JPA. */
    protected Payment() {
    }

    public Payment(Booking booking, String provider, LocalDateTime now) {
        this.booking = booking;
        this.provider = provider;
        this.amount = booking.getPrice();
        this.currency = CURRENCY;
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
    }

    /** Remember Stripe's id right after Stripe created the payment. */
    public void attachProviderPayment(String providerPaymentId) {
        if (this.providerPaymentId != null) {
            throw new IllegalStateException("Payment " + id + " already has a Stripe payment");
        }
        this.providerPaymentId = providerPaymentId;
    }

    public void markSucceeded(String cardBrand, String cardLast4, LocalDateTime now) {
        if (status != PaymentStatus.PENDING) {
            throw new ConflictException("PAYMENT_ALREADY_RECORDED", "This payment was already recorded.");
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.paidAt = now;
    }

    public void markRefunded(String providerRefundId, LocalDateTime now) {
        if (status != PaymentStatus.SUCCEEDED) {
            throw new ConflictException("NOTHING_TO_REFUND", "Only a completed payment can be refunded.");
        }
        this.status = PaymentStatus.REFUNDED;
        this.providerRefundId = providerRefundId;
        this.refundedAt = now;
    }

    /** "Visa •••• 4242", or "Card" if Stripe didn't tell us the details. */
    public String getMethodLabel() {
        if (cardBrand == null || cardLast4 == null) {
            return "Card";
        }
        String brand = switch (cardBrand) {
            case "visa" -> "Visa";
            case "mastercard" -> "Mastercard";
            case "amex" -> "American Express";
            case "discover" -> "Discover";
            case "unionpay" -> "UnionPay";
            default -> cardBrand.substring(0, 1).toUpperCase() + cardBrand.substring(1);
        };
        return brand + " •••• " + cardLast4;
    }

    // ---- Getters ----

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }
}
