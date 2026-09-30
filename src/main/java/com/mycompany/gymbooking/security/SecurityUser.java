package com.mycompany.gymbooking.security;

import com.mycompany.gymbooking.model.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * An ADAPTER between our User entity and Spring Security.
 *
 * Spring Security only understands the UserDetails interface.
 * Instead of making our User entity depend on Spring Security, we wrap it here.
 * This keeps the model package clean (low coupling).
 */
public class SecurityUser implements UserDetails {

    private final User user;

    public SecurityUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    /** "ROLE_MEMBER", "ROLE_TRAINER" or "ROLE_ADMIN": used to protect endpoints by role. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return user.isVerified();
    }
}
