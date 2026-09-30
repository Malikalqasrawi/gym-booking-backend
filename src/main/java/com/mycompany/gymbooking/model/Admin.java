package com.mycompany.gymbooking.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * The gym administrator: manages branches, trainers, time slots and bookings.
 */
@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends User {

    protected Admin() {
    }

    public Admin(String fullName, String email, String phone, String passwordHash) {
        super(fullName, email, phone, passwordHash);
    }

    @Override
    public Role getRole() {
        return Role.ADMIN;
    }

    @Override
    public String getDisplayTitle() {
        return "Administrator";
    }
}
