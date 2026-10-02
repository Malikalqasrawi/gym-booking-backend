package com.mycompany.gymbooking.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Trainer account. Trainers are created by an admin and join by accepting an emailed invite code,
 * which also sets their password.
 */
@Entity
@DiscriminatorValue("TRAINER")
public class Trainer extends User {

    @Column(length = 100)
    private String specialty;

    @Column(length = 500)
    private String bio;

    private Integer yearsOfExperience;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** JOD per hour. Scale 3 because 1 JOD = 1000 fils. */
    @Column(precision = 8, scale = 3)
    private BigDecimal hourlyRate;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TrainingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(length = 100)
    private String languages;

    @ElementCollection
    @CollectionTable(name = "trainer_tags", joinColumns = @JoinColumn(name = "trainer_id"))
    @OrderColumn(name = "position")
    @Column(name = "tag", length = 40)
    private List<String> tags = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "trainer_certifications", joinColumns = @JoinColumn(name = "trainer_id"))
    @OrderColumn(name = "position")
    @Column(name = "certification", length = 120)
    private List<String> certifications = new ArrayList<>();

    /** Set while the trainer is deactivated. Nullable so existing rows need no backfill. */
    private LocalDateTime deactivatedAt;

    protected Trainer() {
    }

    public Trainer(String fullName, String email, String phone, String passwordHash,
                   String specialty, String bio, int yearsOfExperience) {
        super(fullName, email, phone, passwordHash);
        this.specialty = specialty;
        this.bio = bio;
        this.yearsOfExperience = yearsOfExperience;
    }

    @Override
    public Role getRole() {
        return Role.TRAINER;
    }

    @Override
    public String getDisplayTitle() {
        return "Trainer · " + specialty;
    }

    @Override
    public boolean isActive() {
        return deactivatedAt == null;
    }

    public TrainerStatus getStatus() {
        if (!isActive()) {
            return TrainerStatus.DEACTIVATED;
        }
        return isVerified() ? TrainerStatus.ACTIVE : TrainerStatus.INVITED;
    }

    /** Members can see and book only active trainers who joined and have a complete profile. */
    public boolean isBookable() {
        return getStatus() == TrainerStatus.ACTIVE && branch != null && hasHourlyRate() && hasProfile();
    }

    public void deactivate(LocalDateTime now) {
        this.deactivatedAt = now;
    }

    public void reactivate() {
        this.deactivatedAt = null;
    }

    public LocalDateTime getDeactivatedAt() {
        return deactivatedAt;
    }

    public void assignToBranch(Branch branch) {
        this.branch = branch;
    }

    public Branch getBranch() {
        return branch;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public boolean hasHourlyRate() {
        return hourlyRate != null;
    }

    public void changeHourlyRate(BigDecimal newRate) {
        if (newRate == null || newRate.signum() <= 0) {
            throw new IllegalArgumentException("Hourly rate must be more than 0");
        }
        this.hourlyRate = newRate;
    }

    public void updateProfile(TrainingCategory category, Gender gender, String languages,
                              List<String> tags, List<String> certifications) {
        this.category = category;
        this.gender = gender;
        this.languages = languages;
        this.tags.clear();
        this.tags.addAll(tags);
        this.certifications.clear();
        this.certifications.addAll(certifications);
    }

    public boolean hasProfile() {
        return category != null;
    }

    public TrainingCategory getCategory() {
        return category;
    }

    public Gender getGender() {
        return gender;
    }

    public String getLanguages() {
        return languages;
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public List<String> getCertifications() {
        return Collections.unmodifiableList(certifications);
    }

    /** Session price for the given duration, rounded half-up to 3 decimals (fils). */
    public BigDecimal priceFor(int minutes) {
        if (hourlyRate == null) {
            throw new IllegalStateException("Trainer " + getId() + " has no hourly rate");
        }
        return hourlyRate.multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), 3, RoundingMode.HALF_UP);
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }
}
