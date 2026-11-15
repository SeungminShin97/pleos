package io.github.seungminshin97.japeace.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seungminshin97.japeace.domain.rest.CaptureSource
import io.github.seungminshin97.japeace.domain.rest.OperationState
import io.github.seungminshin97.japeace.domain.rest.SessionState
import io.github.seungminshin97.japeace.domain.rest.StoredSession

private enum class SessionAction { RESTORE, ABANDON }

@Composable
fun PresetStoragePanel(
    state: PresetStorageState,
    parked: Boolean,
    canExecute: Boolean,
    onSave: (String) -> Unit,
    onApply: (String) -> Unit,
    onRestore: (String) -> Unit,
    onAbandon: (String) -> Unit,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("나의 휴식") }
    val available = state.ready && !state.saving && !state.executing
    val pending = state.pendingSession
    val selected = state.presets.find { it.id == selectedId }
    var confirmation by rememberSaveable(pending?.id) { mutableStateOf<SessionAction?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("휴식 설정", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = name, onValueChange = { if (it.length <= 40) name = it },
            label = { Text("설정 이름") }, singleLine = true, enabled = available,
            modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSave(name) }, enabled = available && parked && name.isNotBlank()) {
            Text("저장")
        }
        if (state.loading) Text("저장 기록을 불러오는 중입니다.")
        if (state.ready && state.presets.isEmpty()) Text("저장된 휴식 설정이 없습니다.")
        for (preset in state.presets) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canExecute && preset.capture.profile.source == CaptureSource.DEMO) {
                    RadioButton(selected = selectedId == preset.id, onClick = { onSelect(preset.id) },
                        enabled = available)
                }
                Text("${preset.name} (${preset.capture.settings.size}개 항목)")
            }
        }
        if (canExecute) {
            Button(
                onClick = { selected?.let { onApply(it.id) } },
                enabled = available && parked && pending == null && selected?.capture?.profile?.source == CaptureSource.DEMO,
            ) { Text("설정 적용") }
            if (pending?.state in setOf(SessionState.APPLIED, SessionState.RECOVERY_REQUIRED)) {
                Button(onClick = { confirmation = SessionAction.RESTORE }, enabled = available && parked) {
                    Text("이전 설정으로 복귀")
                }
                if (pending?.state == SessionState.RECOVERY_REQUIRED) {
                    TextButton(onClick = { confirmation = SessionAction.ABANDON }, enabled = available) {
                        Text("수동 처리 후 세션 종료")
                    }
                }
            }
        }
        if (state.executing) Text("작업 중")
        state.message?.let { Text(it) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        (state.lastResult ?: pending)?.let { SessionResults(it) }
    }
    val action = confirmation
    if (action != null && pending != null) {
        val restoring = action == SessionAction.RESTORE
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (restoring) "이전 설정으로 복귀할까요?" else "세션을 종료할까요?") },
            text = { Text(if (restoring) "적용이 확인된 항목을 저장해 둔 이전 값으로 복귀합니다."
                else "설정을 변경하지 않고 세션을 종료합니다.") },
            confirmButton = {
                TextButton(enabled = available && (!restoring || parked), onClick = {
                    confirmation = null
                    if (restoring) onRestore(pending.id) else onAbandon(pending.id)
                }) { Text(if (restoring) "복귀 확인" else "종료 확인") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun SessionResults(session: StoredSession) {
    HorizontalDivider()
    Text(when (session.state) {
        SessionState.PLANNED -> "적용 작업"
        SessionState.APPLIED -> "적용됨"
        SessionState.RECOVERY_REQUIRED -> "복구 필요"
        SessionState.COMPLETED -> "복귀 완료"
        SessionState.ABANDONED -> "세션 종료"
    }, style = MaterialTheme.typography.titleMedium)
    ResultRow("항목", "이전 → 목표", "결과")
    session.operations.forEach { operation ->
        val phase = if (operation.sequence < session.before.settings.size) "적용" else "복귀"
        ResultRow("$phase ${operation.key} (${operation.areaId})", "${operation.beforeRaw} → ${operation.targetRaw}",
            when (operation.state) {
                OperationState.PLANNED -> "대기"
                OperationState.DISPATCHED -> "확인 중"
                OperationState.CONFIRMED -> "확인됨"
                OperationState.FAILED -> "실패"
                OperationState.UNKNOWN -> "확인 불가"
            })
    }
}

@Composable
private fun ResultRow(item: String, values: String, status: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(item, Modifier.weight(2f))
        Text(values, Modifier.weight(1f))
        Text(status, Modifier.weight(1f))
    }
}
