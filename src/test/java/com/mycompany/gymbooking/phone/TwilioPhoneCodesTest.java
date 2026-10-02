package com.mycompany.gymbooking.phone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycompany.gymbooking.exception.ApiException;
import com.mycompany.gymbooking.phone.PhoneCodes.Check;
import com.mycompany.gymbooking.support.FakeTwilio;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** The requests sent to Twilio Verify, and how its answers are understood. */
class TwilioPhoneCodesTest {

    private static final String PHONE = "+962791234567";

    private FakeTwilio twilio;
    private TwilioPhoneCodes codes;

    @BeforeEach
    void start() throws Exception {
        twilio = FakeTwilio.start();
        codes = twilioPhoneCodes(FakeTwilio.AUTH_TOKEN, FakeTwilio.SERVICE_SID);
    }

    @AfterEach
    void stop() {
        twilio.close();
    }

    @Test
    @DisplayName("a code is sent by SMS to the number, with the account's credentials")
    void sendsCode() {
        codes.send(PHONE);

        FakeTwilio.Request request = twilio.requests().get(0);
        assertEquals("/v2/Services/VAtest/Verifications", request.path());
        assertEquals(Map.of("To", PHONE, "Channel", "sms"), request.form());
        String credentials = FakeTwilio.ACCOUNT_SID + ":" + FakeTwilio.AUTH_TOKEN;
        assertEquals("Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)),
                request.authorization());
    }

    @Test
    @DisplayName("Twilio's answer to a code: approved is correct, pending is wrong, anything else has expired")
    void checksCode() {
        assertEquals(Check.CORRECT, codes.check(PHONE, "123456"));
        assertEquals(Map.of("To", PHONE, "Code", "123456"), twilio.requests().get(0).form());
        assertEquals("/v2/Services/VAtest/VerificationCheck", twilio.requests().get(0).path());

        assertEquals(Check.WRONG, codes.check(PHONE, "000000"));
        twilio.answerNext(200, "{\"status\":\"max_attempts_reached\"}");
        assertEquals(Check.EXPIRED, codes.check(PHONE, "123456"));
        twilio.answerNext(404, "{\"code\":20404,\"message\":\"The requested resource was not found\"}");
        assertEquals(Check.EXPIRED, codes.check(PHONE, "123456"), "no code waiting: expired or already used");
        twilio.answerNext(429, "{\"code\":60202,\"message\":\"Max check attempts reached\"}");
        assertEquals(Check.EXPIRED, codes.check(PHONE, "123456"));
    }

    @Test
    @DisplayName("refused numbers, too many codes and Twilio being down are told apart")
    void errors() {
        twilio.answerNext(400, "{\"code\":60200,\"message\":\"Invalid parameter `To`\"}");
        assertEquals("PHONE_CANT_RECEIVE_SMS", code(() -> codes.send(PHONE)));
        twilio.answerNext(429, "{\"code\":60203,\"message\":\"Max send attempts reached\"}");
        assertEquals("TOO_MANY_CODES", code(() -> codes.send(PHONE)));
        twilio.answerNext(500, "{}");
        assertEquals("SMS_UNAVAILABLE", code(() -> codes.send(PHONE)));
        twilio.answerNext(503, "not json");
        assertEquals("SMS_UNAVAILABLE", code(() -> codes.check(PHONE, "123456")));

        twilio.close();
        assertEquals("SMS_UNAVAILABLE", code(() -> codes.send(PHONE)), "can't connect");
    }

    @Test
    @DisplayName("a missing auth token or Verify service stops the app at startup")
    void incompleteSettings() {
        assertThrows(IllegalStateException.class, () -> twilioPhoneCodes("", FakeTwilio.SERVICE_SID));
        assertThrows(IllegalStateException.class, () -> twilioPhoneCodes(FakeTwilio.AUTH_TOKEN, " "));
    }

    private TwilioPhoneCodes twilioPhoneCodes(String authToken, String serviceSid) {
        return new TwilioPhoneCodes(RestClient.builder(), new ObjectMapper(),
                FakeTwilio.ACCOUNT_SID, authToken, serviceSid, twilio.baseUrl());
    }

    private static String code(Runnable call) {
        return assertThrows(ApiException.class, call::run).getCode();
    }
}
