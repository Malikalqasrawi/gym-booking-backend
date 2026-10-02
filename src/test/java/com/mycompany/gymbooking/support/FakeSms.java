package com.mycompany.gymbooking.support;

import com.mycompany.gymbooking.phone.PhoneCodes;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records SMS codes instead of texting them, so tests can read them like a phone would. */
public final class FakeSms implements PhoneCodes {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, String> latest = new ConcurrentHashMap<>();
    private final List<String> sentTo = new CopyOnWriteArrayList<>();

    @Override
    public void send(String phone) {
        latest.put(phone, String.format("%06d", random.nextInt(1_000_000)));
        sentTo.add(phone);
    }

    @Override
    public Check check(String phone, String code) {
        String expected = latest.get(phone);
        if (expected == null) {
            return Check.EXPIRED;
        }
        if (!expected.equals(code)) {
            return Check.WRONG;
        }
        latest.remove(phone);
        return Check.CORRECT;
    }

    /** The code the phone received last. */
    public String latestCode(String phone) {
        String code = latest.get(phone);
        if (code == null) {
            throw new AssertionError("No code is waiting for " + phone);
        }
        return code;
    }

    public long count(String phone) {
        return sentTo.stream().filter(phone::equals).count();
    }
}
