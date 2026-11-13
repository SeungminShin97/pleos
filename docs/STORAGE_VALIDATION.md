# W04 설정과 세션 영속 저장

검증일: 2026-11-08, Asia/Seoul. 변경은 로컬 작업 상태이며 commit 또는 push하지 않았습니다.

## 구현 결과와 범위

**현재 운전석 설정을 조회해 이름과 함께 저장하고, 앱 재시작 뒤 목록을 다시 표시합니다. 프리셋, 원래 값, 세션과 항목별 작업 계획을 Room에 영속 저장하는 기반을 추가했습니다. 차량 제어, 자동 적용과 복귀는 이번 단계에 포함하지 않았습니다.**

[W03](SDK_VALIDATION.md)의 실제 좌석 이동과 재현 미검증 상태는 유지합니다. 저장 성공을 자동 재현 가능으로 표시하지 않습니다. W04의 세션 API는 데이터 기록용이며, 실제 제어를 연결하는 W05 coordinator는 아직 없습니다.

## 저장 구조와 변경 이유

설계의 Room 방향을 채택해 `androidx.room` 2.8.5, Kotlin 2.2.21에 맞는 KSP 2.2.21-2.0.4를 고정했습니다. SQLite의 트랜잭션, 외래 키와 unique index를 사용해 프로세스나 repository 인스턴스가 달라도 기록의 일관성을 유지합니다. 가벼운 독립 사용자 설정은 이번 단계에 없으므로 DataStore는 아직 추가하지 않았습니다. 활성 세션과 원본을 별도 저장소에 나눠 기록하지 않습니다.

| 소스 | 구현 |
| --- | --- |
| `app/src/main/java/io/github/seungminshin97/japeace/domain/rest/RestModels.kt` | 프리셋, 출처, 관측값, 세션과 작업 상태, 저장 및 읽기 계약 |
| `app/src/main/java/io/github/seungminshin97/japeace/data/rest/RestDatabase.kt` | Room entity, DAO, DB v1, 원자적 조회, 외래 키와 활성 슬롯 제약 |
| `app/src/main/java/io/github/seungminshin97/japeace/data/rest/RoomRestRepository.kt` | 프리셋 저장, 세션과 원본 및 계획의 트랜잭션, 요청 중복 처리와 재시작 복구 |
| `app/src/vehicle/java/io/github/seungminshin97/japeace/integration/vehicle/PleosSettingsReader.kt` | AAOS P와 속도 0을 확인하고 Pleos SDK capability 및 현재 운전석 값을 조회. 차량 쓰기 호출 없음 |
| `app/src/demo/java/io/github/seungminshin97/japeace/data/vehicle/FakeSettingsReader.kt` | 명확히 DEMO로 표시한 4개 모의 설정, 실제 차량 연결 없음 |
| `app/src/main/java/io/github/seungminshin97/japeace/presentation/HomeViewModel.kt` | 저장 요청 중복 차단, 저장 상태와 오류, 미해결 세션 관측. DB 오류 시 데이터 삭제 없이 중단 |
| `app/src/main/java/io/github/seungminshin97/japeace/presentation/PresetStoragePanel.kt` | 이름 입력, P와 정지에서 저장, 최근 기록 목록, 출처와 자동 재현 미검증 안내 |
| `app/src/androidTest/java/io/github/seungminshin97/japeace/validation/StorageValidationInstrumentation.kt` | 실제 SQLite 기반 8개 시나리오 및 별도 프로세스 종료 복구 검증 |
| `app/schemas/io.github.seungminshin97.japeace.data.rest.RestDatabase/1.json` | Room이 내보낸 최초 스키마, 이후 migration 검토의 기준 |

DB 파일은 각 앱의 내부 `databases/rest.db`입니다. demo와 vehicle은 applicationId가 달라 데이터 디렉터리가 분리됩니다. 기존 Manifest의 `allowBackup=false`와 데이터 추출 규칙의 database 제외를 유지했습니다. 네트워크나 서버 저장은 추가하지 않았습니다.

### 테이블

| 테이블 | 보관 내용 |
| --- | --- |
| `presets` | UUID, 이름, revision 1, schemaVersion 1, 생성 시각, 출처와 매핑 버전 |
| `preset_settings` | 설정 키와 영역, raw 값, min/max, 단위 문자열, 조회 완료 시각, 재현 검증 여부 |
| `sessions` | 요청 ID, 프리셋 ID와 revision, 상태, 시각, 활성 슬롯, 원본의 출처 |
| `before_snapshots` | 변경 대상 항목의 원래 값과 관측 정보. 수정 API 없이 보존 |
| `operations` | 항목별 순서, 원래 값과 목표 값, PLANNED / DISPATCHED / CONFIRMED / FAILED / UNKNOWN, 확인 값과 오류 |

`sessions.requestId`와 `sessions.activeSlot`은 unique index입니다. repository는 미해결 세션의 activeSlot을 1, 명시적으로 종료한 세션을 NULL로 저장해 활성 세션을 하나로 제한합니다. 외래 키로 세션에 연결된 프리셋과 작업 기록의 관계를 유지합니다.

### 세션 기록과 복구

1. `prepareSession`은 출처와 매핑 버전, 선택한 키와 영역, 단위와 현재 지원 범위를 확인합니다.
2. 원본 스냅샷, 세션과 전체 작업 계획을 한 트랜잭션으로 저장합니다. 작업 삽입 실패 시 원본과 세션도 롤백합니다.
3. 같은 요청 ID를 다시 받으면 기존 세션을 반환하며 새 관측값으로 원본을 덮어쓰지 않습니다. 다른 요청으로 미해결 세션을 추가할 수 없습니다.
4. 작업 상태는 기대하는 이전 상태와 세션 ID가 일치해야 변경됩니다. 늦은 콜백이나 다른 세션의 요청은 기존 상태를 완료 처리하지 못합니다. 목표값이 확인되지 않은 CONFIRMED 기록도 거부합니다.
5. 앱 재시작에서는 활성 세션을 RECOVERY_REQUIRED로 표시하고, DISPATCHED 작업은 UNKNOWN으로 바꿉니다. 원래 값은 보존합니다. 어떤 차량 명령도 자동으로 실행하지 않습니다.
6. 모든 작업이 CONFIRMED인 경우에만 완료 처리할 수 있습니다. 별도의 명시적 포기 API는 활성 슬롯을 해제하지만 원본과 이력을 삭제하지 않습니다. 사용자 확인 화면은 W05 이후에 연결할 부분입니다.

이 API는 기록의 일관성을 보장하는 저장 계층입니다. PLANNED나 CONFIRMED를 DB에 기록할 수 있다는 사실이 해당 차량 제어가 지원되거나 물리적으로 검증됐다는 뜻은 아닙니다. 테스트의 작업 상태 전이는 가상 데이터이며 실제 차량 요청을 보낸 결과가 아닙니다.

## 현재 설정 저장

vehicle에서는 앞좌석 왼쪽 영역 1의 capability를 조회한 뒤 읽기 가능한 항목만 현재 값으로 저장합니다. 조회 중 오류나 빈 값이 발생하면 불완전한 프리셋을 저장하지 않습니다. 실제 주차 상태는 조회 전후로 다시 확인합니다. 기존 화면의 주차 조건도 저장 요청 전후에 검사합니다.

이 이미지에서는 W03에서 확인한 11개 항목이 저장됐습니다. 물리 단위가 미확정이므로 단위는 `RAW_UNKNOWN`, 자동 재현 검증은 false로 보존합니다. 각 항목의 시각은 SDK 조회 완료 후 앱에서 기록한 시각이며 VHAL 측정 시각으로 표현하지 않습니다. 여러 항목의 조회가 차량 전체에서 동시에 이루어진 원자적 측정이라고 보장하지 않습니다.

출처는 DEMO, PLEOS_EMULATOR, VEHICLE을 구분합니다. 빌드 fingerprint 기반의 profile key는 차량의 검증된 고유 식별자가 아니며 `identityVerified=false`로 저장합니다. VIN은 수집하지 않았습니다. 같은 기종의 다른 차량에서 자동 적용할 수 있다고 판단하지 않습니다.

UI는 이름을 1~40자로 받고, 저장된 기록 중 최근 5개를 표시합니다. DB의 최대 프리셋 수를 5개로 제한한 것은 아닙니다. 수정과 삭제 UI, 대상 좌석 및 항목 선택 UI는 아직 없습니다. 현재 화면의 대상은 운전석이며, 이번 저장은 11개 관측값을 기록한 것입니다.

## 실제 검증

### DB 시나리오

아래 8개를 로컬 Pleos에서 vehicle과 demo 각각 실행해 모두 통과했습니다. 기존 20개 도메인 단위 테스트와 별도의 instrumentation 검증입니다. 테스트마다 고유한 `rest-validation-*` DB를 사용하고 정리하며 사용자의 `rest.db`를 지우지 않습니다.

| 시나리오 | 확인한 결과 |
| --- | --- |
| DB 닫기 및 다시 열기 | 프리셋 이름, 값과 출처, 미검증 메타데이터 유지 |
| 같은 요청 중복 및 다른 요청 충돌 | 원본 값 40 유지, 다른 활성 세션 차단 |
| 작업 INSERT 강제 실패 | SQLite trigger로 실패를 주입한 후 세션, 스냅샷과 작업 행 모두 0. 프리셋은 유지 |
| 두 DB 인스턴스의 동시 요청 | 정확히 하나만 세션 생성, 원본 행이 서로 섞이지 않음 |
| 전송 중 기록 복구 | 다시 연 DB에서 DISPATCHED를 UNKNOWN으로 변경, 원본 유지, 복구 재호출에도 결과 유지 |
| 오래되거나 잘못된 완료 처리 | 잘못된 세션 ID, 기대 상태 불일치와 확인 값 불일치 거부, 미완료 세션 종료 거부 |
| 다른 출처, 단위, 범위 및 누락 값 | 부분 세션 생성 없이 거부. 중복 관측값과 빈 이름도 거부 |
| 명시적 포기 기록 | 활성 슬롯은 해제하되 원본과 이력은 유지, 다음 세션 생성 가능 |

### 실제 프로세스 종료

별도 fixture DB에 원래 전후 값 40과 DISPATCHED 상태를 커밋하고 DB를 명시적으로 닫지 않은 채 instrumentation을 끝냈습니다. 이후 대상 앱을 `am force-stop`하고 새로운 instrumentation 프로세스에서 같은 DB를 열었습니다.

새 프로세스는 DISPATCHED와 원래 값 40을 읽었고, 복구 후 RECOVERY_REQUIRED와 UNKNOWN으로 바뀌면서 원본이 유지됐습니다. 이 테스트에도 차량 API는 사용하지 않았습니다. OS 재부팅이나 전원 손실까지 검증한 결과는 아닙니다.

### 사용자 화면

- 실제 vehicle 앱의 저장 버튼으로 Pleos 운전석 11개 관측값 저장 성공.
- 앱을 force-stop하고 재실행한 뒤 저장된 설정 1개와 11개 항목, 에뮬레이터 출처 표시 유지.
- D, 속도 0에서 저장 버튼 비활성 및 P 정지 안내, 기존 기록 수 유지.
- demo 앱은 vehicle의 기록이 없는 초기 목록에서 시작. 모의 항목 4개를 저장한 뒤 DEMO 출처 표시 확인. 두 앱의 기록이 섞이지 않음.
- 자동 적용과 이전 설정 복귀 버튼은 계속 비활성.

### 빌드와 정적 검사

두 debug 앱과 두 instrumentation APK 빌드, 기존 단위 테스트 총 20개, 두 변형 Android Lint를 통과했습니다. Room v1 스키마를 생성해 저장소에 포함했습니다. 기본 테스트 runner는 저장 검증으로 변경하고, W03 좌석 probe는 vehicle 테스트 Manifest의 별도 instrumentation으로 유지했습니다.

vehicleRelease의 병합 Manifest에는 W04에 필요한 좌석 읽기 권한 CAR_SEATS가 있습니다. 좌석 제어 권한 CONTROL_CAR_SEATS는 debug 검증용에만 남아 있습니다. demo에는 실제 차량 읽기 권한이나 SDK adapter를 추가하지 않았습니다. release APK를 빌드하거나 실차에서 실행한 것은 아닙니다.

## 증거와 재현

로컬 증거: `C:\resume\.tools\pleos-w04-evidence`.

- `final-build.log`, `process-test-build.log`: 빌드와 검사 결과.
- `storage-integration.log`, `storage-demo-integration.log`: 각 변형의 8개 DB 시나리오.
- `process-stage.log`, `process-recovered.log`: 서로 다른 프로세스 사이의 전송 중 기록 복구.
- `home-saved.xml`, `home-after-force-stop.xml`: 실제 관측값 저장과 앱 재실행 후 유지.
- `home-drive-blocked.xml`: D 상태 저장 비활성.
- `demo-before-save.xml`, `demo-saved.xml`: demo 데이터 분리 및 모의 저장.

```powershell
$env:ANDROID_HOME = 'C:\resume\.tools\pleos-sdk'
$env:GRADLE_USER_HOME = 'C:\Users\rnsdl\.gradle'
.\gradlew.bat :app:assembleVehicleDebug :app:assembleVehicleDebugAndroidTest
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb -s emulator-5554 install --user 10 -r app/build/outputs/apk/vehicle/debug/app-vehicle-debug.apk
& $adb -s emulator-5554 install --user 10 -r app/build/outputs/apk/androidTest/vehicle/debug/app-vehicle-debug-androidTest.apk
& $adb -s emulator-5554 shell am instrument --user 10 -w io.github.seungminshin97.japeace.test/io.github.seungminshin97.japeace.validation.StorageValidationInstrumentation
# 실제 프로세스 경계를 확인하는 별도 fixture
& $adb -s emulator-5554 shell am instrument --user 10 -w -e storageMode stage-recovery io.github.seungminshin97.japeace.test/io.github.seungminshin97.japeace.validation.StorageValidationInstrumentation
& $adb -s emulator-5554 shell am force-stop --user 10 io.github.seungminshin97.japeace
& $adb -s emulator-5554 shell am instrument --user 10 -w -e storageMode verify-recovery io.github.seungminshin97.japeace.test/io.github.seungminshin97.japeace.validation.StorageValidationInstrumentation
```

W03의 이동 probe는 별도 이름으로 명시적으로 호출해야 하며 기본 저장 테스트에는 포함되지 않습니다.

## 한계와 다음 단계

현재 DB는 최초 버전 1입니다. 이전 버전에서의 migration 경로는 아직 없고, destructive fallback은 설정하지 않았습니다. 이후 스키마 변경에서는 migration과 기존 데이터 보존 검증을 추가해야 합니다.

실차, 전원 손실, 장기 보관 정리 정책, 프리셋 편집과 삭제 UI, 복구 기록의 사용자 해결 화면, 알람 기록과 DataStore 설정, 실제 제어와 작업 상태의 연결은 미구현 또는 미검증입니다. W05에서 저장 계층을 사용하는 적용 및 복귀 유스케이스를 만들 수 있으나, 좌석 제어 활성화는 W03의 위치 변화 및 중단과 재현 검증을 통과하기 전까지 보류합니다.

공식 근거: [Room 안내](https://developer.android.com/training/data-storage/room), [Room 2.x 릴리스](https://developer.android.com/jetpack/androidx/releases/room), [Kotlin 2.2.21용 KSP](https://github.com/google/ksp/releases/tag/2.2.21-2.0.4).
