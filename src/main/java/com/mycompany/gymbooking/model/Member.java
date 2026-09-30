package com.mycompany.gymbooking.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

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
