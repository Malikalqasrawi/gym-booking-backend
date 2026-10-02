package com.mycompany.gymbooking.phone;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A valid phone number for its country (see {@link PhoneNumbers}). Empty values pass, so combine
 * with {@code @NotBlank} when the number is required.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPhoneValidator.class)
public @interface ValidPhone {

    /** False also accepts landlines, e.g. for a branch's phone. */
    boolean mobile() default true;

    String message() default "Enter a valid mobile number for the selected country";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
