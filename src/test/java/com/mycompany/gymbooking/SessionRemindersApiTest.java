package com.mycompany.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.gymbooking.service.SessionReminders;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reminder emails the day before a paid session. */
class SessionRemindersApiTest extends ApiTestBase {

    @Test
    @DisplayName("the member and the trainer get one reminder a day before, unless the session was paid within that day")
    void reminders() throws Exception {
        String member = newMember();
        String email = memberEmail(member);
        long booking = paidBooking(member, sara, "09:00");
        LocalDateTime startsAt = wednesday.atTime(LocalTime.of(9, 0));
        // Paid days before, whatever day the tests run on
        jdbc().update("update payments set paid_at = ? where booking_id = ?", startsAt.minusDays(3), booking);

        // Paid only three hours before it starts: the confirmation email is still fresh.
        String lateMember = newMember();
        String lateEmail = memberEmail(lateMember);
        long paidLate = paidBooking(lateMember, sara, "11:00");
        jdbc().update("update payments set paid_at = ? where booking_id = ?", wednesday.atTime(LocalTime.of(8, 0)), paidLate);

        SessionReminders reminders = backend.getBean(SessionReminders.class);
        assertEquals(0, reminders.sendDue(startsAt.minusHours(30)), "too early");
        assertEquals(0, mailbox.count(email, "Reminder"));

        assertEquals(1, reminders.sendDue(startsAt.minusHours(20)));
        assertEquals(1, mailbox.count(email, "Reminder: your session tomorrow at 09:00"));
        String reminder = mailbox.latestBody(email, "Reminder: your session");
        assertTrue(reminder.contains("Your session with Sara Haddad is tomorrow at 09:00."), reminder);
        assertTrue(reminder.contains("  Booking:   #" + booking), reminder);
        assertTrue(reminder.contains("Abdoun Branch"), reminder);
        assertEquals(1, mailbox.count("sara.trainer@gym.com", "Reminder: session with Test Member tomorrow at 09:00"));

        assertEquals(0, mailbox.count(lateEmail, "Reminder"), "paid within the last day");
        assertNotNull(jdbc().queryForObject("select reminder_checked_at from bookings where id = ?", LocalDateTime.class, paidLate),
                "and it isn't looked at again");

        assertEquals(0, reminders.sendDue(startsAt.minusHours(19)), "each booking gets one reminder");
        assertEquals(0, reminders.sendDue(startsAt.minusHours(2)));
        assertEquals(1, mailbox.count(email, "Reminder"));
    }
}
