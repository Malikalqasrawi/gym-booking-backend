package com.mycompany.gymbooking.phone;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidPhoneValidator implements ConstraintValidator<ValidPhone, String> {

    private boolean mobileOnly;

    @Override
    public void initialize(ValidPhone annotation) {
        mobileOnly = annotation.mobile();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || PhoneNumbers.international(value, mobileOnly).isPresent();
    }
}
