# 백그라운드 음성과 시트 메모리 연동

2026-11-14 기준. 대상은 Pleos Connect용 서드파티 앱입니다. 공식 API 계약, 로컬 SDK 공개 메서드, Pleos 에뮬레이터 관측을 근거로 판단했습니다.

## 백그라운드 음성

차량 알람만 선택한 최신 APK에서 “자비스에서 15분 휴식 설정을 준비해 줘”를 합성 음성으로 입력했습니다. 15분 확인창에서 설정을 눌러 예약 시각 표시와 Android AlarmManager의 `RestAlarmReceiver` 등록을 확인했습니다. 시험 알람을 취소하고 휴대폰 알람 옵션을 원래 상태로 복원했습니다.

최신 차량 APK를 실행해 쉴 곳 탭을 열어 둔 상태에서 “자비스에서 휴식 설정을 열어 줘”를 합성 음성으로 입력했습니다. Gleo가 `OPEN_REST_SETTINGS`를 전달했고, 자비스가 휴식 설정 탭으로 이동해 저장 버튼과 기존 설정의 11개 항목 표시를 보여줬습니다.

| 원하는 동작 | 확인된 경로와 제약 | 상태 |
| --- | --- | --- |
| 앱에서 음성 버튼 누르기 | 현재 코드는 `ACTION_RECOGNIZE_SPEECH`를 실행합니다. Pleos 에뮬레이터 Android 14의 차량 사용자 10에서 해당 인텐트를 조회하고 실행했으며, 처리할 Activity가 없어 실패했습니다. 시스템 RecognitionService는 별도로 등록되어 있었습니다. | 현재 버튼의 인텐트 경로 실패 |
| 앱에서 시작한 인식을 홈으로 나간 뒤 계속하기 | Pleos STT의 차량 마이크 세션으로 시험할 수 있습니다. 공개 문서에는 Activity가 내려간 뒤 인식 세션의 수명을 보장하는 설명이 없습니다. | 미검증 |
| 앱을 내린 상태에서 새 인식 시작하기 | Pleos 에뮬레이터에서 자비스 화면 없이 계측 러너로 `request()`를 호출했습니다. SDK 초기화는 성공했지만 합성 음성 입력 후 30초 동안 결과가 없었습니다. 같은 음성은 자비스 화면에서 인식됐습니다. | 이 시험 구성에서 백그라운드 인식 미확인 |
| 앱이 열려 있지 않아도 차량 비서로 명령하기 | Gleo가 시스템 음성 진입을 맡고 앱에 Intent를 전달합니다. 앱의 `assets/schema.json`, 등록한 기능의 Intent 처리, 배포 시 스키마 검토가 필요합니다. | Pleos Connect 2.1.0에서 음성으로 `OPEN_REST_SETTINGS` 실행 확인. 실차와 배포는 미검증 |
| 앱 자체 호출어를 상시 듣거나 부팅 후 자동 대기하기 | Android에서 호출어 수신을 위해 시스템이 유지하는 서비스는 선택된 `VoiceInteractionService`입니다. 이 에뮬레이터의 선택 서비스는 `ai.umos.ambientai/.service.AiAssistantService`였습니다. 일반 Pleos 앱이 시스템 비서 역할을 얻거나 개인 호출어를 등록할 수 있다는 공개 계약은 확인하지 못했습니다. | 일반 앱 기능으로 보류 |

현재 음성 버튼 위치는 [RestToolsPanel.kt](../app/src/main/java/io/github/seungminshin97/japeace/presentation/RestToolsPanel.kt#L168)입니다. 공식 근거는 [Pleos STT 시작과 종료](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop), [Gleo 스키마 전달](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema), [Android VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService)입니다.

- **Pleos STT 서비스.** 3.3.1 공개 SDK는 `pleos.car.permission.STT_SERVICE`를 사용합니다. 에뮬레이터에 해당 권한과 서비스 패키지가 등록돼 있었습니다. 첫 시험은 서버 등록이 필요 없는 `ON_DEVICE`에서 초기화, 리스너 등록, `request()`, `stop()`, `release()` 순으로 진행합니다. `sendAudio()`는 앱이 가진 PCM을 넣는 경로이므로 엔진 확인에는 쓸 수 있지만 차량 마이크의 백그라운드 접근을 검증할 수 없습니다. [STT 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro), [권한](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/SpeechToText/declaring-permissions), [PCM 입력](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/SpeechToText/sending-audio-data)
- **앱이 직접 마이크를 사용하는 경우.** Android 14 이상을 대상으로 하는 앱은 `microphone` 유형의 포그라운드 서비스와 `FOREGROUND_SERVICE_MICROPHONE`, 런타임 `RECORD_AUDIO`가 필요합니다. 일반적으로 앱이 보일 때 서비스를 시작해야 하며, 허용된 예외를 제외하면 백그라운드나 부팅 직후의 신규 시작이 제한됩니다. Android `SpeechRecognizer`도 연속 인식 용도로 설계되지 않았습니다. 차량에서 직접 녹음을 채택하려면 Pleos 하드웨어 접근 정책과 배포 권한을 별도로 확인합니다. [마이크 서비스 유형](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone), [백그라운드 시작 제한](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer), [Pleos 하드웨어 접근 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture)

### 음성 검증 순서

Pleos Connect 2.1.0에서 “자비스에서 15분 휴식 설정을 준비해 줘”를 한국어 합성 음성으로 입력했습니다. Gleo의 `PREPARE_REST_ALARM` 호출에 `{"minutes":15}`가 포함됐고, 자비스의 차량 알람 확인창에 15분이 표시됐습니다. 설정 버튼을 누른 뒤 Android AlarmManager의 활성 예약을 확인했습니다. 자비스에서 시험 알람을 취소했고 활성 예약이 사라졌습니다. 이전의 “15분 뒤에 휴식 알람을 맞춰 줘”는 기본 리마인더로 분류됐습니다.

차량 빌드의 표시 이름을 스키마의 호출 이름과 같은 ‘자비스’로 맞추자 “자비스 앱을 열어 줘”로 앱이 열렸습니다. “자비스에서 휴식 설정을 열어 줘”는 Gleo가 전용 기능을 선택했지만 이 이미지가 패키지명 접두사를 붙여 보낸 Intent를 기존 필터가 처리하지 못했습니다. 차량 매니페스트에 `${applicationId}.OPEN_REST_SETTINGS`를 추가한 뒤 앱을 강제 종료하고 같은 음성을 다시 주입했습니다. Gleo의 `sendActionCall`과 Android의 해당 액션 `START` 성공, 자비스 `MainActivity`의 화면 진입까지 확인했습니다. Pleos Connect 2.1.0 개발 이미지에서 검증한 결과입니다.

같은 PCM 주입 경로에서 “캘린더 앱을 실행해 줘”라고 하자 Gleo가 캘린더를 열었고 `com.android.calendar/.AllInOneActivity`가 화면에 올라왔습니다. “자피스 앱을 실행해 줘”는 “잡피스”로 인식됐으며 앱 찾기에 실패했습니다. 캘린더 시험으로 합성 음성 입력부터 Gleo의 기본 앱 실행까지 확인했습니다. 자비스의 설치 표시 이름 `자.peace`와 스키마의 호출 이름 `자비스`가 다른 점은 기본 앱 이름 검색에서 별도로 시험할 수 있습니다.

스피커 재생 시험에서는 `RESULT_TIMEOUT`이 발생했고 에뮬레이터 마이크에 소리가 들어갔는지 확인하지 못했습니다. 이후 같은 AVD를 로컬 gRPC로 다시 실행해 `injectAudio`로 16 kHz 모노 한국어 합성 PCM을 주입했습니다. Gleo가 두 발화를 모두 문자로 인식했습니다. “자비스 앱을 열어 줘”는 기본 앱 실행 기능으로 분류됐으나 설치 앱 이름을 찾지 못했습니다. “자피스에서 휴식 설정을 열어 줘”는 `unsupportedFeature`와 `FEATURE_PROHIBITED_TO_HELP`를 반환했습니다. 두 시험에서 사용자 앱 액션의 실행은 관측되지 않았습니다. Gleo 로그의 도구 목록에는 `io.github.seungminshin97.japeace.OPEN_REST_SETTINGS`가 있었습니다. 이 오류만으로 스키마 검토 상태와 기능 선택 정책 중 어느 쪽이 원인인지 확정할 수 없습니다. [Android Emulator 오디오 입력](https://developer.android.com/studio/run/emulator-console)

차량 빌드에 Gleo용 [스키마](../app/src/vehicle/assets/schema.json)와 [액션 필터](../app/src/vehicle/AndroidManifest.xml)를 추가했습니다. `OPEN_REST_SETTINGS`는 기존 앱 화면을 엽니다. Pleos Connect 2.1.0의 사용자 10에서 ADB로 액션을 보내 앱이 종료 상태에서 열린 것을 확인했습니다. APK 설치 후 시스템 Gleo 로그에 앱 패키지와 `setAppCapability | converted | functionCount=1`에 해당하는 기록이 남아 스키마를 읽고 기능 하나로 변환한 것까지 확인했습니다. 시스템 음성 세션을 열자 인식 오디오 전송 로그가 나왔고 세션은 `RESULT_TIMEOUT`으로 종료됐습니다. 이 시도에는 의도적으로 발화를 입력하지 않았으므로 실제 음성 호출 결과는 미검증입니다. [Gleo 구현 예제](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples), [스키마 전달](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema)

1. **앱이 보일 때** 한국어 시험 문장으로 인식 시작과 최종 결과를 확인합니다. 권한, 서비스 연결, 마이크 입력, 엔진 오류를 구분합니다.
2. **홈으로 이동한 뒤** 같은 세션에 새 시험 문장을 입력하고 최종 결과를 확인합니다. 별도 실행에서 화면을 내린 뒤 처음으로 `request()`를 호출해 새 세션 시작 여부를 확인합니다. 화면 전환과 콜백의 시각, 부분 및 최종 인식 여부, 오류 코드, 정리 시점을 기록합니다. 초기화 성공이나 시작 콜백만으로 통과시키지 않습니다.
3. **Gleo 별도 검증.** 앱 화면을 한 번도 열지 않은 상태에서 스키마 등록과 무해한 시험 명령의 Intent 수신을 확인합니다. 일반 프로세스 종료, 사용자 강제 종료, 차량 절전 및 재시작은 각각 관측합니다. 실제 기능은 터치와 같은 유스케이스를 사용하며 기존 주차 조건 및 사용자 확인을 적용합니다. [Gleo 구현 예제](https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/examples)
4. **실차 재검증.** 차량 마이크, 다른 음성 기능과의 동시 사용, 화면 전환, 차량 절전 및 복귀를 대상 차종과 소프트웨어 버전에서 확인합니다. 에뮬레이터 통과 여부를 실차 지원으로 표시하지 않습니다.

## 현대차 시트 메모리 동기화

차량 메모리 슬롯 동기화는 제품 범위에서 제외했습니다. 아래 내용은 기존 조사 기록입니다. 자비스가 현재 좌석 위치를 읽어 자체 휴식 설정으로 저장하는 기능은 유지합니다.

현대차 사용자 매뉴얼은 차량 기능을 **운전석 자세 메모리 시스템**이라고 부릅니다. 사양에 따라 운전석 외에 미러나 HUD도 함께 저장되며, 일부 차종은 인포테인먼트 운전자 프로필별 저장을 지원합니다. 매뉴얼에서 확인한 것은 차량 내 기능입니다. 외부 앱이 프로필과 메모리 슬롯의 매핑이나 저장 데이터를 읽는 공개 API는 확인하지 못했습니다. [아이오닉 6 매뉴얼](https://ownersmanual.hyundai.com/full_webhelp/CE1/2025/ko_KR/convenience_device_erivers_seat_memory_system.html), [코나 매뉴얼](https://ownersmanual.hyundai.com/full_pdf/SX2/2024/ko_KR)

| 경로 | 확인 결과 |
| --- | --- |
| Pleos Vehicle SDK 2.3.2 | `checkSeatMemoryCapabilities`는 지원 영역과 슬롯 범위를 알려줍니다. `selectSeatMemory`는 지정 슬롯을 불러오는 명령입니다. 공개 `Seat` 메서드에서 슬롯의 저장값, 선택된 슬롯, 슬롯 변경 이벤트, 슬롯 저장, 슬롯 적용 완료를 제공하는 API를 찾지 못했습니다. 현재 위치는 지원되는 축별 API로 읽을 수 있습니다. [슬롯 선택 API](https://document.pleos.ai/api-reference/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/select-seat-memory), [SDK 검증](SDK_VALIDATION.md) |
| Pleos 에뮬레이터 | 운전석 `ROW_1_LEFT`, 슬롯 1부터 2, `WRITE`를 관측했습니다. Pleos Connect 2.1.0에서 두 슬롯을 각각 요청한 뒤 11개 좌석 축을 세 번 재조회했습니다. 모든 값이 그대로였고 관측 중 실패 콜백도 없었습니다. 슬롯 선택 효과와 물리 버튼 또는 프로필과의 매핑은 미검증입니다. [실행 기록](SDK_VALIDATION.md) |
| Android Automotive 표준 속성 | `SEAT_MEMORY_SELECT`와 `SEAT_MEMORY_SET`은 각각 선택과 현재 위치 저장을 위한 쓰기 전용 정수 속성입니다. 둘 다 쓰기에 서명 또는 특권 권한이 필요합니다. `SET`은 슬롯 데이터 조회를 제공하지 않습니다. 표준 속성의 슬롯 번호 규칙과 Pleos에서 관측한 범위를 서로 대입하지 않습니다. [Android VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds#SEAT_MEMORY_SELECT), [차종별 지원 및 접근성](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability) |
| 클라우드 API와 OEM 지원 | 공개 Pleos Vehicle Data API의 상태 조회에서 메모리 슬롯이나 운전자 프로필 설정을 읽거나 쓰는 기능을 찾지 못했습니다. 전체 동기화가 필요하면 기술 지원에 대상 차종의 슬롯 값, 슬롯과 프로필의 매핑, 변경 알림, 적용 완료 신호, 배포 권한 제공 여부를 문의해야 합니다. [Vehicle Data API](https://document.pleos.ai/en/api-reference/vehicle-data-api/intro), [Pleos SDK 권한 정책](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/) |

### 구현 가능한 범위와 검증 조건

2025 아이오닉 5 한국어 매뉴얼은 운전석 도어의 1번 또는 2번 버튼을 길게 눌러 저장하고 짧게 눌러 불러오는 절차를 명시합니다. 이 조작은 해당 사양의 차량 기능입니다. 공개 Pleos Seat API에서 물리 버튼 누름 이벤트나 버튼 저장 동작을 호출하는 메서드는 확인하지 못했습니다. `selectSeatMemory`는 슬롯 선택 요청이며, 실제 차량에서 어느 물리 버튼과 대응하는지는 검증이 필요합니다. [현대차 버튼 사용 설명](https://ownersmanual.hyundai.com/full_webhelp/NE1/2025/ko_KR/id23C5G040JAX.html)

- 조사 당시 공개 계약에는 좌석을 움직이지 않고 저장값을 읽거나, 변경을 감지해 자동 갱신하거나, 앱의 임의 위치를 차량 슬롯에 쓰는 인터페이스가 확인되지 않았습니다.
- **사용자 확인 후 현재 좌석값 저장**을 우선 검토합니다. 사용자가 정차 상태에서 차량 버튼으로 슬롯을 불러오고 이동이 끝났음을 확인하면, 앱에서 지원되는 운전석 축의 현재값을 저장합니다. 화면에는 ‘현재 좌석 저장’으로 표시합니다. 출처는 사용자가 선택한 번호로만 기록할 수 있으며 슬롯 자체를 조회했다는 의미를 부여하지 않습니다. 결측값은 임의 값으로 채우지 않습니다.
- **앱에서 슬롯 불러오기**는 차종별 지원 영역 및 슬롯 범위, 권한, P와 정지 상태를 확인한 뒤 사용자가 명시적으로 요청하는 흐름으로만 검토합니다. 앱이 자동으로 슬롯을 순회하지 않습니다. 위치가 서로 다른 두 슬롯과 독립적으로 기록한 기준값을 사용해 선택 전후 축 값을 비교하고, 수동 조작 중단과 실패 응답도 확인합니다. 오류 콜백이 없거나 일정 시간 좌석값이 같았다는 사실만으로 선택 성공 또는 적용 완료로 판정하지 않습니다.
- **검증 한계.** 에뮬레이터가 슬롯별 위치 변화를 재현하지 못하면 서비스 계약 검증까지만 기록하고, 물리 버튼과 SDK 슬롯 번호의 대응 및 실제 좌석 동작은 대상 실차에서 확인합니다. 이 문서 갱신에서는 앱 구현이나 새 실차 시험을 수행하지 않았습니다.

Virtual Console 공식 카드 목록에서 좌석 탑승 상태 조작은 확인했지만 좌석 위치나 메모리 슬롯을 준비하는 카드는 찾지 못했습니다. 현재 이미지의 일반 ADB 셸에서는 VHAL 진단 도움말과 메모리 속성 조회 모두 `FAILED_TRANSACTION`을 반환했습니다. AAOS 차량 서비스 진단에는 운전석 선택 속성 `0x15400b80` 기록이 있었습니다. 이 기록은 좌석값 저장이나 복원의 증거로 사용하지 않습니다. [Virtual Console 카드 목록](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide), [AOSP VHAL 진단](https://source.android.com/docs/automotive/vhal/vhal_debug)

재시작한 Pleos 이미지에서도 `ro.debuggable=0`을 확인했습니다. AOSP VHAL 문서의 루트 셸을 전제로 한 진단 절차와 현재 일반 ADB 셸의 권한 조건이 다릅니다.

공식 Maven 메타데이터에 공개된 Vehicle SDK 버전은 2.0.3과 2.3.2이며 최신은 2.3.2였습니다. 두 AAR의 공개 `Seat` 메서드를 비교한 결과, 2.0.3은 메모리 선택만 제공했고 2.3.2는 선택과 지원 범위 조회를 제공합니다. 두 버전 모두 슬롯값 조회와 저장 API는 확인되지 않았습니다. 에뮬레이터의 Vehicle Service는 공식 샘플 기준과 같은 2.3.22였습니다. [공식 Maven 메타데이터](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/maven-metadata.xml), [Vehicle SDK 샘플](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk)

차량용 검증 APK의 `memory-config`를 실행해 앱 권한으로 보이는 `CarPropertyManager` 속성 목록도 검사했습니다. `SEAT_MEMORY_SELECT`와 `SEAT_MEMORY_SET` 모두 목록에 나타나지 않았습니다. Pleos SDK를 통한 슬롯 선택 요청은 앞선 시험에서 차량 서비스에 도달했습니다. 표준 속성이 앱에 보이지 않는 이유가 실제 구현 부재인지, 권한 필터링인지는 이 시험으로 구분할 수 없습니다. 같은 이미지에서 `cmd car_service help`는 사용자 빌드에서 사용할 수 없다는 응답을 반환했습니다.

### 슬롯 복원 검증에 필요한 환경

운전석 메모리 버튼이 있는 Pleos 실차나, 슬롯에 서로 다른 좌석값을 준비할 수 있는 OEM 테스트 이미지가 필요합니다. 실차에서는 시험용 운전자 설정을 사용하고 차량 매뉴얼에 따라 P 상태에서 1번과 2번을 서로 구분되는 위치로 저장합니다. 물리 버튼으로 각각 불러온 뒤 지원되는 좌석 축을 읽어 두 기준값을 남깁니다. 그다음 각 슬롯을 SDK로 선택해 관측 위치가 대응하는 기준값에 도달하는지 비교합니다. 물리 버튼과 SDK 슬롯의 대응, 실패 콜백, 관측한 축을 기록합니다. 슬롯에 저장된 원본 데이터를 직접 가져오는 검증에는 OEM이 공개한 조회 인터페이스가 별도로 필요합니다.
앱의 음성 명령 버튼으로 인식을 시작한 뒤에는 저장한 호출어를 명령 앞에 붙일 수 있습니다. 예를 들어 호출어가 `별빛`이면 `별빛, 알람 20분`을 해석합니다. 호출어 없는 기존 명령도 지원합니다. 설정은 앱 내 명령에만 적용하며 Gleo 스키마의 호출 이름은 `자비스`입니다. 명령 해석은 단위 테스트로 확인했으며 앱 내 실제 발화 인식은 별도 검증이 필요합니다.
앱 내 음성 버튼은 이제 Android SpeechRecognizer를 직접 사용합니다. Pleos 에뮬레이터에서 마이크 권한 요청과 Gleo `AmbRecognitionService.onStartListening` 진입을 확인했습니다. 발화를 입력하지 않은 세션은 제한 시간에 종료됐습니다. 개인 호출어 발화의 인식 결과는 아직 확인하지 않았습니다.
앱의 음성 명령 버튼으로 `AmbRecognitionService` 세션을 열고, 에뮬레이터에 16 kHz 모노 합성 음성 “알람 취소”를 주입했습니다. 주입은 완료됐지만 서비스에서 앱으로 인식 결과가 돌아오지 않았고 15초 후 세션이 취소됐습니다. 시스템 Gleo 세션에서 성공한 PCM 입력을 앱의 Android SpeechRecognizer 경로에 그대로 적용할 수 있는지는 확인되지 않았습니다. 다음 검증 경로는 Pleos SpeechToText SDK의 `request()`와 `sendAudio()`입니다. [Pleos 음성 인식 시작과 종료](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop)
차량 빌드의 음성 버튼은 Pleos SpeechToText 3.3.1의 ON_DEVICE를 사용합니다. 공식 Maven 의존성으로 빌드했고, Pleos 에뮬레이터에서 초기화와 `request()` 이후 안내 문구를 확인했습니다. “알람 취소” PCM을 에뮬레이터 마이크에 주입했지만 최종 결과가 돌아오지 않아 15초에 종료됐습니다. SDK에 PCM을 직접 전달하는 `sendAudio()` 시험은 남아 있습니다. [Pleos STT 이벤트](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners)
Pleos 온디바이스 STT 계측 시험에서 16 kHz, 16비트, 모노 PCM “알람 취소”를 `sendAudio()`로 직접 전달했습니다. 언어 코드만 전달한 첫 시험에서 엔진이 `ko-`를 거절해, 계측과 차량 앱의 로캘을 `ko-KR`로 지정했습니다. 재시험에서 최종 결과 “알람 취소”를 받았습니다. 차량 마이크를 통한 인식과 개인 호출어 실행은 이 시험 범위에 포함되지 않습니다. [Pleos PCM 입력 가이드](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/sending-audio-data)
차량 앱을 `ko-KR`로 빌드한 뒤 Pleos 에뮬레이터에서 음성 명령 버튼을 눌러 “알람 취소” 합성 음성을 마이크 경로에 주입했습니다. 온디바이스 엔진 로그에 최종 결과 “알람 취소”가 남았고 앱에는 “타이머를 취소했습니다.”가 표시됐습니다. 이 시험에서는 개인 호출어를 사용하지 않았습니다.
개인 호출어 재시험 중 Pleos 에뮬레이터 연결이 끊겨 기존 AVD를 다시 부팅했습니다. 디버그 앱 설정에 시험용 호출어를 넣어 입력란에 표시되는 것과 `ko-KR` 음성 세션 시작을 확인했습니다. 호출어를 포함한 합성 발화에 대해서는 최종 인식 결과와 명령 실행을 확인하지 못했습니다. 시험용 호출어 설정은 복원했습니다.
개인 호출어 `자비스`를 지정한 계측 시험에서 “자비스, 알람 취소” PCM을 Pleos 온디바이스 SDK에 전달했습니다. 최종 결과 `자비스 알람 취소`를 앱의 명령 해석기에 넣었을 때 `CancelAlarm`으로 해석됐습니다. 첫 실행에서는 최종 결과가 15초 제한을 지난 뒤 생성되어 계측 대기 시간을 30초로 조정했습니다. 실제 음성 버튼으로 같은 발화를 인식하고 알람을 취소하는 흐름은 추가 검증이 필요합니다.
차량 앱은 인식 시작 후 15초 안에 중간 인식 문구를 받으면 처리 중 안내를 표시하고, 시작 시점 기준 최대 30초까지 최종 결과를 기다립니다. Pleos 에뮬레이터에서 입력이 없는 경우 15초 제한으로 종료되는 것을 확인했습니다. 중간 인식 이후 대기 연장을 포함한 개인 호출어의 실제 실행은 미검증입니다.
Pleos 에뮬레이터에서 10분 차량 알람을 예약한 뒤, 앱의 음성 명령 버튼을 눌러 “자비스 알람 취소”를 마이크 경로에 합성 음성으로 입력했습니다. 최종 인식 문구, 앱의 취소 안내, 저장된 알람 마감 값 제거를 확인했습니다. 앞선 시험에서는 호출어 뒤에 쉼이 있는 음성이 “자비스”까지만 인식됐습니다. 시험 후 호출어 설정을 원래대로 복원했습니다.
앱 내 음성 명령에서 등록한 호출어만 최종 결과로 들어오면 인식 세션을 한 번 더 엽니다. 호출어를 반복해서 말해도 추가 인식을 무한히 열지 않습니다. 호출어 단독 판단은 단위 테스트로 확인했고, 두 발화로 나눠 말하는 에뮬레이터 시험은 남아 있습니다.
Pleos 에뮬레이터에서 음성 명령 버튼을 한 번 누른 뒤 “자비스”와 “알람 취소”를 별도 합성 음성으로 차례로 입력했습니다. 첫 발화 이후 SDK 로그에서 두 번째 인식 요청이 시작된 것을 확인했고, 두 번째 발화 이후 앱에 “타이머를 취소했습니다.”가 표시됐습니다. 테스트 호출어 설정은 원래대로 복원했습니다.
