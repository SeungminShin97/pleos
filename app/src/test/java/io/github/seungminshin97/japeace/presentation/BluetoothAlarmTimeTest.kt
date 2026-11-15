package io.github.seungminshin97.japeace.presentation

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothAlarmTimeTest {
    @Test fun rejectsTargetWhenClockWouldRingOnEarlierDayAfterSpringTransition() {
        val zone = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-03-08T05:30:00Z")
        assertNull(alarmTime(Instant.parse("2026-03-09T04:45:00Z"), now, zone))
        val validTarget = Instant.parse("2026-03-09T04:15:00Z")
        assertEquals(validTarget, alarmTime(validTarget, now, zone)?.toInstant())
    }

    @Test fun rejectsSecondPrecisionTheClockCannotSchedule() {
        val now = Instant.parse("2026-02-11T10:00:00Z")
        assertNull(alarmTime(now.plusSeconds(61), now, ZoneId.of("UTC")))
    }

    @Test fun receiverAcceptsClockAlarmMoreThanTwoHoursAway() {
        val now = Instant.parse("2026-02-11T10:00:00Z")
        val evening = now.plusSeconds(8 * 3600)
        assertEquals(evening, receivedAlarmTarget(evening.epochSecond, now))
        assertNull(receivedAlarmTarget(now.plusSeconds(24 * 3600).epochSecond, now))
        assertNull(receivedAlarmTarget(Long.MAX_VALUE, now))
    }

    @Test fun midnightUsesPhoneLocalTime() {
        val now = Instant.parse("2026-11-14T14:50:30Z")
        val target = checkNotNull(alarmTarget(20, now))
        val local = checkNotNull(alarmTime(target, now, ZoneId.of("Asia/Seoul")))
        assertEquals(15, local.dayOfMonth)
        assertEquals(0, local.hour)
        assertEquals(11, local.minute)
    }

    @Test fun durationNeverRoundsDownAndKeepsExactMinute() {
        val now = Instant.parse("2026-11-14T14:50:00Z")
        assertEquals(now.plusSeconds(1200), alarmTarget(20, now))
        assertEquals(now.plusSeconds(1260), alarmTarget(20, now.plusNanos(1)))
        assertEquals(now.plusSeconds(1260), alarmTarget(20, now.plusSeconds(59)))
    }

    @Test fun expiredAndAmbiguousTimesAreRejected() {
        val now = Instant.parse("2026-11-01T05:00:00Z")
        assertNull(alarmTime(now, now))
        assertNull(alarmTime(now.plusSeconds(1800), now, ZoneId.of("America/New_York")))
        assertNull(alarmTarget(121, now))
    }
}
