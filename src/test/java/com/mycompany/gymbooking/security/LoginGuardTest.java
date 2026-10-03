package com.mycompany.gymbooking.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.gymbooking.model.Member;
import com.mycompany.gymbooking.model.User;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginGuardTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0);
    private static final String ATTACKER = "203.0.113.7";
    private static final String OWNER = "198.51.100.20";

    private final LoginGuard guard = new LoginGuard(5, 30, 15);

    private static Member member(long id) {
        Member member = new Member("Test Member", "member" + id + "@test.com", "+962790000000", "hash");
        try {
            Field field = User.class.getDeclaredField("id");   // set by the database normally
            field.setAccessible(true);
            field.set(member, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return member;
    }

    @Test
    @DisplayName("wrong passwords lock the account for the address they came from, not for the owner elsewhere")
    void lockIsPerAddress() {
        Member member = member(1);
        for (int i = 1; i < 5; i++) {
            assertFalse(guard.recordWrong(member, ATTACKER, NOW));
        }
        assertTrue(guard.recordWrong(member, ATTACKER, NOW), "the 5th wrong attempt locks");
        assertEquals(Optional.of(NOW.plusMinutes(15)), guard.lockedUntil(member, ATTACKER, NOW));
        assertEquals(Optional.empty(), guard.lockedUntil(member, OWNER, NOW), "the owner can still log in");
        assertEquals(Optional.empty(), guard.lockedUntil(member(2), ATTACKER, NOW), "other accounts aren't affected");
        assertEquals(Optional.empty(), guard.lockedUntil(member, ATTACKER, NOW.plusMinutes(15)), "the lock ends");
    }

    @Test
    @DisplayName("many wrong attempts from many addresses lock the account everywhere")
    void accountWideBackstop() {
        Member member = member(3);
        boolean locked = false;
        for (int i = 0; i < 30; i++) {
            // 4 attempts per address: never enough for a per-address lock
            locked = guard.recordWrong(member, "203.0.113." + (i / 4), NOW);
        }
        assertTrue(locked, "the 30th wrong attempt in a row locks the account");
        assertTrue(guard.lockedUntil(member, OWNER, NOW).isPresent());
    }

    @Test
    @DisplayName("a correct login clears the count, old wrong attempts stop counting, and a password reset lifts every lock")
    void countsReset() {
        Member member = member(4);
        for (int i = 0; i < 4; i++) {
            guard.recordWrong(member, ATTACKER, NOW);
        }
        guard.recordSuccess(member, ATTACKER);
        assertFalse(guard.recordWrong(member, ATTACKER, NOW), "counting starts again after a correct login");

        for (int i = 0; i < 3; i++) {
            guard.recordWrong(member, OWNER, NOW);
        }
        assertFalse(guard.recordWrong(member, OWNER, NOW.plusMinutes(16)), "attempts from more than 15 minutes ago don't count");

        for (int i = 0; i < 5; i++) {
            guard.recordWrong(member, ATTACKER, NOW.plusMinutes(20));
        }
        assertTrue(guard.lockedUntil(member, ATTACKER, NOW.plusMinutes(20)).isPresent());
        guard.clearAll(member);
        assertEquals(Optional.empty(), guard.lockedUntil(member, ATTACKER, NOW.plusMinutes(20)));
    }
}
