package com.mycompany.gymbooking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/**
 * A member's rating of one finished session: 1 to 5 stars and an optional comment. It can't be
 * changed once sent. The trainer may answer it, and the admin may hide it (with a reason), which
 * removes it from the trainer's profile and average.
 */
@Entity
@Table(name = "reviews", indexes = @Index(name = "idx_reviews_trainer", columnList = "trainer_id, hidden"))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One review per session; the unique column stops a second one even under concurrent requests. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_id")
    private Trainer trainer;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(length = 500)
    private String trainerReply;

    private LocalDateTime repliedAt;

    @Column(nullable = false)
    private boolean hidden = false;

    @Column(length = 300)
    private String hiddenReason;

    private LocalDateTime hiddenAt;

    @Version
    private Long version;

    protected Review() {
    }

    public Review(Booking booking, int rating, String comment, LocalDateTime now) {
        this.booking = booking;
        this.member = booking.getMember();
        this.trainer = booking.getTrainer();
        this.rating = rating;
        this.comment = comment == null || comment.isBlank() ? null : comment.trim();
        this.createdAt = now;
    }

    /** The trainer's answer; answering again replaces it. */
    public void reply(String text, LocalDateTime now) {
        this.trainerReply = text.trim();
        this.repliedAt = now;
    }

    public void hide(String reason, LocalDateTime now) {
        this.hidden = true;
        this.hiddenReason = reason.trim();
        this.hiddenAt = now;
    }

    public void show() {
        this.hidden = false;
        this.hiddenReason = null;
        this.hiddenAt = null;
    }

    public Long getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public Member getMember() {
        return member;
    }

    public Trainer getTrainer() {
        return trainer;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getTrainerReply() {
        return trainerReply;
    }

    public LocalDateTime getRepliedAt() {
        return repliedAt;
    }

    public boolean isHidden() {
        return hidden;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public LocalDateTime getHiddenAt() {
        return hiddenAt;
    }
}
