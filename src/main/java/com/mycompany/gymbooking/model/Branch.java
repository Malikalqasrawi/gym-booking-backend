package com.mycompany.gymbooking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A gym location, e.g. "Abdoun Branch".
 * Hibernate turns this class into the table "branches" (one row per branch).
 *
 * latitude/longitude are the GPS position, used later to show the branch on the map.
 */
@Entity
@Table(name = "branches")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private LocalTime openingTime;

    @Column(nullable = false)
    private LocalTime closingTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** Needed by JPA. */
    protected Branch() {
    }

    public Branch(String name, String address, String city, double latitude, double longitude,
                  String phone, LocalTime openingTime, LocalTime closingTime) {
        updateDetails(name, address, city, latitude, longitude, phone, openingTime, closingTime);
    }

    /**
     * ENCAPSULATION: instead of 8 separate setters, one method changes all the details together.
     * That's what an "update" (the U in CRUD) does to a branch.
     */
    public final void updateDetails(String name, String address, String city, double latitude, double longitude,
                                    String phone, LocalTime openingTime, LocalTime closingTime) {
        this.name = name;
        this.address = address;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
    }

    /** Is the branch open at this time of day? (Used later when showing available slots.) */
    public boolean isOpenAt(LocalTime time) {
        return !time.isBefore(openingTime) && time.isBefore(closingTime);
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ---------- Getters only: changes go through updateDetails() ----------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getPhone() {
        return phone;
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
