package com.mycompany.gymbooking.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.util.Optional;
import java.util.Set;

/**
 * Checks phone numbers with Google's libphonenumber, which knows the numbering plan of every
 * country, and converts them to the international format they are stored in (E.164), e.g.
 * "079 123 4567" -> "+962791234567". Numbers typed without a country code are read as Jordanian.
 */
public final class PhoneNumbers {

    public static final String DEFAULT_REGION = "JO";

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();
    /** In some countries (e.g. the US) mobile and landline numbers look the same. */
    private static final Set<PhoneNumberType> MOBILE =
            Set.of(PhoneNumberType.MOBILE, PhoneNumberType.FIXED_LINE_OR_MOBILE);

    private PhoneNumbers() {
    }

    /**
     * The number in international format, or empty if it isn't a valid number. With
     * {@code mobileOnly}, landlines don't count, since a code can't be texted to them.
     */
    public static Optional<String> international(String input, boolean mobileOnly) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            PhoneNumber number = UTIL.parse(input.trim(), DEFAULT_REGION);
            if (!UTIL.isValidNumber(number) || (mobileOnly && !MOBILE.contains(UTIL.getNumberType(number)))) {
                return Optional.empty();
            }
            return Optional.of(UTIL.format(number, PhoneNumberFormat.E164));
        } catch (NumberParseException e) {
            return Optional.empty();
        }
    }

    /** For input that {@link ValidPhone} has already checked. */
    public static String toInternational(String input) {
        return international(input, false)
                .orElseThrow(() -> new IllegalArgumentException("Not a valid phone number: " + input));
    }
}
