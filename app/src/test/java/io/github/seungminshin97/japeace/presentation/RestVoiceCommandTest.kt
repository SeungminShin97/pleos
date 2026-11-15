package io.github.seungminshin97.japeace.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class RestVoiceCommandTest {
    @Test fun standaloneInvocationCanRequestOneFollowUp() {
        assertTrue(isRestInvocation(" 자비스, ", "자비스"))
        assertFalse(isRestInvocation("자비스 알람 취소", "자비스"))
        assertFalse(isRestInvocation("", ""))
    }
    @Test fun optionalInvocationRequiresAWordBoundary() {
        assertEquals(RestVoiceCommand.Alarm(20), parseRestVoice("별빛, 알람 20분", "별빛"))
        assertEquals(RestVoiceCommand.CancelAlarm, parseRestVoice("별빛 알람 취소", "별빛"))
        assertEquals(RestVoiceCommand.Apply, parseRestVoice("프리셋 적용", "별빛"))
        assertNull(parseRestVoice("별빛알람 취소", "별빛"))
        assertNull(parseRestVoice("별빛", "별빛"))
    }

    @Test fun exactCommandsResolve() {
        assertEquals(RestVoiceCommand.Apply, parseRestVoice("프리셋 적용!"))
        assertEquals(RestVoiceCommand.Restore, parseRestVoice("원래대로 해 줘"))
        assertEquals(RestVoiceCommand.CancelAlarm, parseRestVoice("알람 취소"))
        assertEquals(RestVoiceCommand.Alarm(20), parseRestVoice("20분 알람 설정해 줘"))
    }

    @Test fun invalidOrAmbiguousCommandsAreIgnored() {
        for (words in listOf("알람 0분", "알람 121분", "프리셋 적용하지 마", "알람 2분 3분", "")) {
            assertNull(words, parseRestVoice(words))
        }
    }
}
