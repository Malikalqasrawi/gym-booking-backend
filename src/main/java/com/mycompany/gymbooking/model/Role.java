package com.mycompany.gymbooking.model;

/**
 * The three kinds of users in the app.
 * An enum is a fixed list of allowed values, so a role can never be a typo like "memebr".
 */
public enum Role {
    MEMBER,
    TRAINER,
    ADMIN
}
