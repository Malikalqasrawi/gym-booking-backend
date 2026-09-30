package com.mycompany.gymbooking.model;

/**
 * Used so members can choose a female or male trainer (a common preference in Jordan):
 *   GET /api/branches/1/trainers?gender=FEMALE
 */
public enum Gender {
    MALE,
    FEMALE
}
