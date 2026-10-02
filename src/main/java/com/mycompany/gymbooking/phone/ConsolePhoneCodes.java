package com.mycompany.gymbooking.phone;

import com.mycompany.gymbooking.security.Sha256;
import com.mycompany.gymbooking.service.VerificationCodeGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Development stand-in for SMS, used while the Twilio keys aren't set: the code is written to the
 * log instead of being texted. Codes are kept in memory, so a restart means asking for a new one.
 */
@Component
@ConditionalOnExpression("'${app.notifications.mode:console}' != 'test' and '${app.sms.twilio.account-sid:}' == ''")
public class ConsolePhoneCodes implements PhoneCodes {

    private static final Logger log = LoggerFactory.getLogger(ConsolePhoneCodes.class);

    private record Sent(String codeHash, Instant expiresAt) {
    }

    private final Map<String, Sent> latest = new ConcurrentHashMap<>();
    private final VerificationCodeGenerator codes;
    private final Clock clock;
    private final long validMinutes;

    public ConsolePhoneCodes(VerificationCodeGenerator codes,
                             Clock clock,
                             @Value("${app.phone.code-expiration-minutes}") long validMinutes) {
        this.codes = codes;
        this.clock = clock;
        this.validMinutes = validMinutes;
        log.info("SMS codes are written to the log. Add the Twilio keys to local.properties to text them.");
    }

    @Override
    public void send(String phone) {
        String code = codes.generate();
        latest.put(phone, new Sent(Sha256.hex(code), clock.instant().plusSeconds(validMinutes * 60)));
        log.info("""

                ===================== SMS (console mode) =====================
                To:   {}

                Your Gym Booking code is {}. It expires in {} minutes.
                ==============================================================
                """, phone, code, validMinutes);
    }

    @Override
    public Check check(String phone, String code) {
        Sent sent = latest.get(phone);
        if (sent == null || clock.instant().isAfter(sent.expiresAt())) {
            return Check.EXPIRED;
        }
        if (!sent.codeHash().equals(Sha256.hex(code))) {
            return Check.WRONG;
        }
        latest.remove(phone);   // each code works once
        return Check.CORRECT;
    }
}
