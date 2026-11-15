package io.github.seungminshin97.japeace.presentation

internal sealed interface RestVoiceCommand {
    data object Apply : RestVoiceCommand
    data object Restore : RestVoiceCommand
    data object CancelAlarm : RestVoiceCommand
    data class Alarm(val minutes: Int) : RestVoiceCommand
}

private val alarmRequest = Regex("(?:알람)?([0-9]{1,3})분(?:알람)?(?:설정해줘)?")

internal fun isRestInvocation(text: String, invocation: String): Boolean =
    invocation.isNotBlank() && text.trim().trimEnd('.', '!', '?', ',').trimEnd() == invocation.trim()

internal fun parseRestVoice(text: String, invocation: String = ""): RestVoiceCommand? {
    val spoken = text.trim().trimEnd('.', '!', '?')
    val prefix = invocation.trim()
    val command = if (prefix.isNotEmpty() && spoken.startsWith(prefix) &&
        spoken.getOrNull(prefix.length)?.let { it.isWhitespace() || it == ',' } == true
    ) spoken.drop(prefix.length).trimStart { it.isWhitespace() || it == ',' }
    else spoken
    val words = command.filterNot(Char::isWhitespace)
    return when (words) {
        "프리셋적용", "프리셋적용해줘", "휴식설정적용", "휴식설정적용해줘", "휴식시작" -> RestVoiceCommand.Apply
        "복귀", "복귀해줘", "원래대로해줘" -> RestVoiceCommand.Restore
        "알람취소", "알람취소해줘", "타이머취소" -> RestVoiceCommand.CancelAlarm
        else -> alarmRequest.matchEntire(words)?.groupValues?.get(1)?.toInt()
            ?.takeIf { it in 1..120 }?.let { RestVoiceCommand.Alarm(it) }
    }
}
