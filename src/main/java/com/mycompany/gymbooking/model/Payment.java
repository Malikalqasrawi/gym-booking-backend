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
 * Payment for a booking. Only the card brand and last four digits are stored; card details go
 * directly from the client to Stripe, which keeps the server out of PCI scope.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique so a booking can never be charged twice. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(nullable = false, length = 20)
    private String provider;

    /** Stripe PaymentIntent id. */
    @Column(length = 100, unique = true)
    private String providerPaymentId;

    /** Amount charged to the card, in {@link #currency}. Scale 3 fits JOD (1 JOD = 1000 fils). */
    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)   // see Booking.status
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 20)
    private String cardBrand;

    @Column(length = 4)
    private String cardLast4;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    @Column(length = 100)
    private String providerRefundId;

    private LocalDateTime refundedAt;

    @Version
    private Long version;

    protected Payment() {
    }

    public Payment(Booking booking, String provider, BigDecimal amount, String currency, LocalDateTime now) {
        this.booking = booking;
        this.provider = provider;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
    }

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

    /** Card brand and last four digits for display, or "Card" if Stripe didn't return them. */
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
