package io.github.seungminshin97.japeace.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.seungminshin97.japeace.domain.rest.CaptureSource

@Composable
fun PresetStoragePanel(state: PresetStorageState, parked: Boolean, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("나의 휴식") }
    Card {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("내 휴식 설정", style = MaterialTheme.typography.titleLarge)
            Text("현재 운전석에서 읽을 수 있는 설정을 이 기기에 기록합니다. 자동 적용과 복귀는 아직 지원하지 않습니다.")
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 40) name = it },
                label = { Text("설정 이름") }, singleLine = true, enabled = !state.saving,
                modifier = Modifier.fillMaxWidth())
            Button(onClick = { onSave(name) }, enabled = parked && state.ready && !state.saving && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) {
                Text(if (state.saving) "설정을 읽고 저장하는 중" else "현재 운전석 설정 저장")
            }
            if (state.loading) Text("저장 기록을 불러오는 중입니다.")
            if (!parked) Text("P와 정지 상태에서 저장할 수 있습니다.")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.pendingSession?.let {
                Text("미해결 세션 기록이 있습니다. 원래 설정과 작업 기록을 보관하고 있으며 자동으로 다시 실행하지 않습니다.",
                    color = MaterialTheme.colorScheme.error)
            }
            if (state.ready) Text("저장된 설정 ${state.presets.size}개", style = MaterialTheme.typography.titleMedium)
            for (preset in if (state.ready) state.presets.take(5) else emptyList()) {
                val source = when (preset.capture.profile.source) {
                    CaptureSource.DEMO -> "데모 모의 값"
                    CaptureSource.PLEOS_EMULATOR -> "Pleos 에뮬레이터 조회 값"
                    CaptureSource.VEHICLE -> "차량 조회 값"
                }
                Text("${preset.name}, ${preset.capture.settings.size}개 항목\n$source, 자동 재현 미검증")
            }
            if (state.ready && state.presets.size > 5) Text("최근 저장한 5개를 표시합니다.")
        }
    }
}
