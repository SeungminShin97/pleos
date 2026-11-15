package io.github.seungminshin97.japeace.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestBatteryWarningTest {
    @Test
    fun warnsAtOrBelowThreshold() {
        assertTrue(isRestBatteryLow(0, "20"))
        assertTrue(isRestBatteryLow(20, "20"))
        assertFalse(isRestBatteryLow(21, "20"))
    }

    @Test
    fun ignoresMissingReadingsAndInvalidThresholds() {
        assertFalse(isRestBatteryLow(null, "20"))
        assertFalse(isRestBatteryLow(-1, "20"))
        assertFalse(isRestBatteryLow(10, ""))
        assertFalse(isRestBatteryLow(10, "100"))
    }
}
