package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.gymbooking.config.PhoneNumberUpgrade;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Phone numbers by country, and confirming them with a code by SMS before booking. */
class PhoneVerificationApiTest extends ApiTestBase {

    private static final String PHONE = "+962795551234";

    @Test
    @DisplayName("a new member's number is stored with its country code and confirmed by SMS before booking")
    void confirmBeforeBooking() throws Exception {
        String email = "phone" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        String member = signUp(email, "079 555 1234");
        Reply me = call("GET", "/api/users/me", member, null);
        assertEquals(PHONE, me.body().path("phone").asText());
        assertFalse(me.body().path("phoneVerified").asBoolean());

        Reply blocked = book(member, sara, "09:00");
        assertEquals("PHONE_NOT_VERIFIED", blocked.code());
        assertEquals(403, blocked.status());

        Reply sent = call("POST", "/api/users/me/phone/code", member, null);
        assertEquals(200, sent.status(), sent.body().toString());
        assertEquals(PHONE, sent.body().path("phone").asText());
        assertEquals(60, sent.body().path("resendAfterSeconds").asLong());
        assertEquals(10, sent.body().path("expiresInMinutes").asLong());
        assertEquals(1, sms.count(PHONE));
        Reply tooSoon = call("POST", "/api/users/me/phone/code", member, null);
        assertEquals("RESEND_TOO_SOON", tooSoon.code());
        assertEquals(1, sms.count(PHONE), "no second SMS within a minute");

        Reply wrong = confirm(member, wrongCode(sms.latestCode(PHONE)));
        assertEquals("INVALID_CODE", wrong.code());
        assertTrue(wrong.body().path("message").asText().endsWith("4 tries left."), wrong.body().toString());
        assertEquals("VALIDATION_FAILED", confirm(member, "12ab").code());

        Reply confirmed = confirm(member, sms.latestCode(PHONE));
        assertEquals(200, confirmed.status(), confirmed.body().toString());
        assertTrue(confirmed.body().path("phoneVerified").asBoolean());
        assertEquals(201, book(member, sara, "09:00").status());
        assertEquals("PHONE_ALREADY_VERIFIED", call("POST", "/api/users/me/phone/code", member, null).code());
    }

    @Test
    @DisplayName("a different number has to be confirmed again, a code only works for the number it went to, and the wait still applies")
    void changingNumber() throws Exception {
        String email = "change" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        String member = signUp(email, "0795551235");
        call("POST", "/api/users/me/phone/code", member, null);
        assertEquals(200, confirm(member, sms.latestCode("+962795551235")).status());

        Reply same = call("PUT", "/api/users/me/phone", member, Map.of("phone", "+962 79 555 1235"));
        assertTrue(same.body().path("phoneVerified").asBoolean(), "the same number, typed differently");

        Reply changed = call("PUT", "/api/users/me/phone", member, Map.of("phone", "0781234567"));
        assertEquals("+962781234567", changed.body().path("phone").asText());
        assertFalse(changed.body().path("phoneVerified").asBoolean());
        assertEquals("PHONE_NOT_VERIFIED", book(member, sara, "11:00").code());

        assertEquals("RESEND_TOO_SOON", call("POST", "/api/users/me/phone/code", member, null).code(),
                "changing the number doesn't skip the wait, or every change would be another paid SMS");
        allowNewCode(email);
        Reply sentToNewNumber = call("POST", "/api/users/me/phone/code", member, null);
        assertEquals(200, sentToNewNumber.status(), sentToNewNumber.body().toString());
        String codeForNewNumber = sms.latestCode("+962781234567");
        call("PUT", "/api/users/me/phone", member, Map.of("phone", "0771234567"));
        assertEquals("CODE_EXPIRED", confirm(member, codeForNewNumber).code(), "the code went to another number");
    }

    @Test
    @DisplayName("five wrong codes, an expired code and the daily limit all need a new code")
    void limits() throws Exception {
        String email = "limits" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        String member = signUp(email, "0795551236");
        assertEquals("CODE_EXPIRED", confirm(member, "123456").code(), "no code was sent yet");

        call("POST", "/api/users/me/phone/code", member, null);
        String code = sms.latestCode("+962795551236");
        for (int i = 0; i < 5; i++) {
            assertEquals("INVALID_CODE", confirm(member, wrongCode(code)).code());
        }
        assertEquals("TOO_MANY_ATTEMPTS", confirm(member, code).code(), "even the right code");

        allowNewCode(email);
        call("POST", "/api/users/me/phone/code", member, null);
        jdbc().update("update phone_verifications set expires_at = ? where user_id = (select id from users where email = ?)",
                LocalDateTime.now(AMMAN).minusMinutes(1), email);
        assertEquals("CODE_EXPIRED", confirm(member, sms.latestCode("+962795551236")).code());

        for (int sent = 2; sent < 5; sent++) {   // two codes so far today
            allowNewCode(email);
            assertEquals(200, call("POST", "/api/users/me/phone/code", member, null).status());
        }
        allowNewCode(email);
        Reply limit = call("POST", "/api/users/me/phone/code", member, null);
        assertEquals("TOO_MANY_CODES", limit.code());
        assertEquals(429, limit.status());
    }

    @Test
    @DisplayName("numbers are checked against their country's rules")
    void numbersByCountry() throws Exception {
        Reply notJordanian = call("POST", "/api/auth/signup", null, Map.of("fullName", "Test Member",
                "email", "bad" + MEMBER_NUMBER.incrementAndGet() + "@test.com", "phone", "0761234567", "password", "Secret1234"));
        assertEquals("VALIDATION_FAILED", notJordanian.code());
        assertEquals("Enter a valid mobile number for the selected country",
                notJordanian.body().path("fieldErrors").path("phone").asText());

        String member = signUp("saudi" + MEMBER_NUMBER.incrementAndGet() + "@test.com", "+966 50 123 4567");
        assertEquals("+966501234567", call("GET", "/api/users/me", member, null).body().path("phone").asText());
        Reply abroad = call("POST", "/api/users/me/phone/code", member, null);
        assertEquals("SMS_COUNTRY_NOT_SUPPORTED", abroad.code(), "codes are only texted to Jordan");
        assertEquals(0, sms.count("+966501234567"));
        assertEquals("VALIDATION_FAILED", call("PUT", "/api/users/me/phone", member, Map.of("phone", "06 461 2345")).code(),
                "a landline can't get the code");
    }

    @Test
    @DisplayName("the whole gym can send a limited number of SMS codes a day")
    void gymWideDailyLimit() throws Exception {
        String first = "daily" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        String member = signUp(first, "0795551237");
        assertEquals(200, call("POST", "/api/users/me/phone/code", member, null).status());
        Integer realCount = jdbc().queryForObject(
                "select sends_that_day from phone_verifications where user_id = (select id from users where email = ?)",
                Integer.class, first);
        // Pretend the gym already sent its 200 codes today.
        jdbc().update("update phone_verifications set sends_that_day = 200 where user_id = (select id from users where email = ?)", first);
        try {
            String other = signUp("daily" + MEMBER_NUMBER.incrementAndGet() + "@test.com", "0795551238");
            Reply limit = call("POST", "/api/users/me/phone/code", other, null);
            assertEquals("SMS_LIMIT_REACHED", limit.code());
            assertEquals(429, limit.status());
            assertEquals(0, sms.count("+962795551238"));
        } finally {
            jdbc().update("update phone_verifications set sends_that_day = ? where user_id = (select id from users where email = ?)",
                    realCount, first);
        }
    }

    @Test
    @DisplayName("numbers saved before the country picker are converted at startup")
    void oldNumbers() throws Exception {
        String old = "old" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        String broken = "broken" + MEMBER_NUMBER.incrementAndGet() + "@test.com";
        signUp(old, "0790000000");
        signUp(broken, "0790000000");
        jdbc().update("update users set phone = '0791112233' where email = ?", old);
        jdbc().update("update users set phone = '12345' where email = ?", broken);

        backend.getBean(PhoneNumberUpgrade.class).run();
        assertEquals("+962791112233", phoneOf(old));
        assertEquals("12345", phoneOf(broken), "not a valid number: left for the member to fix");
    }

    /** Signs up and verifies the email; returns the login token. */
    private static String signUp(String email, String phone) throws Exception {
        Reply signUp = call("POST", "/api/auth/signup", null, Map.of(
                "fullName", "Test Member", "email", email, "phone", phone, "password", "Secret1234"));
        assertEquals(201, signUp.status(), signUp.body().toString());
        Reply verified = call("POST", "/api/auth/verify", null,
                Map.of("email", email, "code", mailbox.latestVerificationCode(email), "password", "Secret1234"));
        assertEquals(200, verified.status(), verified.body().toString());
        return verified.body().path("token").asText();
    }

    private static Reply confirm(String member, String code) throws Exception {
        return call("POST", "/api/users/me/phone/confirm", member, Map.of("code", code));
    }

    /** Codes can be sent once a minute; tests move the last one back instead of waiting. */
    private static void allowNewCode(String email) {
        jdbc().update("update phone_verifications set sent_at = ? where user_id = (select id from users where email = ?)",
                LocalDateTime.now(AMMAN).minusMinutes(2), email);
    }

    private static String phoneOf(String email) {
        return jdbc().queryForObject("select phone from users where email = ?", String.class, email);
    }

    /** The right code with its last digit changed. */
    private static String wrongCode(String code) {
        char last = code.charAt(5);
        return code.substring(0, 5) + (last == '9' ? '0' : (char) (last + 1));
    }
}
