package io.github.seungminshin97.japeace.presentation

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestAlarmTimeTest {
    private val now = ZonedDateTime.parse("2026-11-14T23:58:30+09:00[Asia/Seoul]")

    @Test fun remainingSecondsArePreserved() {
        assertEquals(30_000L, restAlarmDelayMillis("23:59", now))
    }

    @Test fun earlierClockTimeRollsOverToTomorrow() {
        assertEquals(90_000L, restAlarmDelayMillis("00:00", now))
    }

    @Test fun phoneCanReceiveNextMorningTime() {
        val target = restAlarmClockTarget("07:30", now)
        assertEquals(7, target?.let { alarmTime(it, now.toInstant(), now.zone)?.hour })
        val sameMinute = restAlarmClockTarget("23:58", now)
        assertNull(sameMinute?.let { alarmTime(it, now.toInstant(), now.zone) })
    }

    @Test fun invalidClockTimeIsRejected() {
        assertNull(restAlarmDelayMillis("24:00", now))
        assertNull(restAlarmDelayMillis("12:60", now))
        assertNull(restAlarmDelayMillis("9:00", now))
    }
}
