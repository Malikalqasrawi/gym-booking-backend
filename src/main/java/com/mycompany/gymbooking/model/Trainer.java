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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A trainer: accepts or rejects session requests.
 * Has extra fields that members and admins don't have.
 * Trainers are created by the admin, not through sign-up.
 */
@Entity
@DiscriminatorValue("TRAINER")
public class Trainer extends User {

    @Column(length = 100)
    private String specialty;          // e.g. "Strength & conditioning"

    @Column(length = 500)
    private String bio;

    private Integer yearsOfExperience;

    /**
     * RELATIONSHIP: many trainers work at one branch.
     *
     * @ManyToOne  → in MySQL this becomes a column "branch_id" in the users table,
     *               holding the id of a row in the branches table (a FOREIGN KEY).
     * LAZY       → the branch row is only loaded from the database when we actually call getBranch().
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /**
     * Price per hour in Jordanian dinars.
     * BigDecimal, not double: double can't store most decimals exactly (0.1 + 0.2 = 0.30000000000000004),
     * which is not OK for money. scale = 3 because 1 JOD = 1000 fils.
     */
    @Column(precision = 8, scale = 3)
    private BigDecimal hourlyRate;

    /** The main kind of training, used by the app's filter chips. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TrainingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    /** e.g. "Arabic, English" */
    @Column(length = 100)
    private String languages;

    /**
     * A LIST of short tags, e.g. ["Beginners", "Weight loss"].
     *
     * @ElementCollection → a list of simple values gets its OWN table, because one MySQL column
     * can't hold a list. Table "trainer_tags": (trainer_id, position, tag), one row per tag.
     * @OrderColumn keeps them in the order we added them.
     */
    @ElementCollection
    @CollectionTable(name = "trainer_tags", joinColumns = @JoinColumn(name = "trainer_id"))
    @OrderColumn(name = "position")
    @Column(name = "tag", length = 40)
    private List<String> tags = new ArrayList<>();

    /** Same idea: table "trainer_certifications", one row per certificate. */
    @ElementCollection
    @CollectionTable(name = "trainer_certifications", joinColumns = @JoinColumn(name = "trainer_id"))
    @OrderColumn(name = "position")
    @Column(name = "certification", length = 120)
    private List<String> certifications = new ArrayList<>();

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

    /** Puts the trainer at a branch (or moves them to another one). */
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

    /**
     * Fills in the profile shown in the app (category, gender, languages, tags, certificates).
     * The lists are copied, so changing the caller's list later can't change the trainer.
     */
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

    /** Read-only view: other classes can read the tags but not add or remove them. */
    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    public List<String> getCertifications() {
        return Collections.unmodifiableList(certifications);
    }

    /** Price of one session: 20 JOD/h × 90 min → 20 × 90 / 60 = 30.000 JOD. */
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
