package com.mycompany.gymbooking.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * A gym member: signs up in the app, books sessions and pays for them.
 * Stored in the "users" table with user_type = 'MEMBER'.
 */
@Entity
@DiscriminatorValue("MEMBER")
public class Member extends User {

    protected Member() {
    }

    public Member(String fullName, String email, String phone, String passwordHash) {
        super(fullName, email, phone, passwordHash);
    }

    @Override
    public Role getRole() {
        return Role.MEMBER;
    }

    @Override
    public String getDisplayTitle() {
        return "Member";
    }
}
