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

    /**
     * A new sign-up for an email that was never verified replaces the earlier one, so whoever proves
     * they own the inbox also chose the password. Otherwise someone could sign up with another
     * person's email first and keep a password to their account.
     */
    public void replaceUnverifiedSignUp(String fullName, String phone, String passwordHash) {
        if (isVerified()) {
            throw new IllegalStateException("Only a sign-up that was never verified can be replaced");
        }
        setFullName(fullName);
        changePhone(phone);
        changePasswordHash(passwordHash);
    }

    /**
     * A member who signed up with Google: the email is already verified by Google, there is no
     * password yet, and the phone number is asked for afterwards.
     */
    public static Member signedUpWithGoogle(String fullName, String email, String googleSubject, String unusablePasswordHash) {
        Member member = new Member(fullName, email, null, unusablePasswordHash);
        member.removePassword(unusablePasswordHash);
        member.markVerified();
        member.linkGoogle(googleSubject);
        return member;
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
