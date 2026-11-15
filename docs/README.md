# 개발 문서 시작점

## 제출 시연

1. Pleos 에뮬레이터에서 `쉴 곳`을 열고 졸음쉼터를 목적지로 설정합니다. 다른 쉼터를 선택해 경유지로 추가한 뒤 결과를 확인합니다.
2. Gleo에 “자비스에서 영동선 강릉 방면 쉴 곳을 검색해 줘”라고 요청합니다. 자비스의 검색어와 결과 목록이 갱신됩니다.
3. 차량과 휴대폰을 페어링하고 양쪽 알람 옵션을 켭니다. 휴대폰에서 차량 요청을 기다린 뒤, 차량에서 알람을 보내고 휴대폰 기본 시계에 등록합니다.
4. 데모 APK에서 휴식 설정을 저장한 뒤 적용하고, 이전 설정으로 복귀합니다. 차량용 앱의 저장 위치 자동 적용과 주행 중 동승석 조정은 [SDK 검증 한계](SDK_VALIDATION.md)에 기록했습니다.

최신 데모 APK에서 4개 항목 저장, D 상태의 적용 버튼 비활성화, P 정지 상태의 적용 및 복귀 확인을 다시 검증했습니다.

로컬 빌드 결과는 [차량용 APK](../app/build/outputs/apk/vehicle/debug/app-vehicle-debug.apk)와 [데모용 APK](../app/build/outputs/apk/demo/debug/app-demo-debug.apk)입니다. 차량 화면의 기본 조작은 [HTML 미리보기](jarvis-preview.html)에서 확인할 수 있습니다.

차량용 Release 빌드는 서명 전 APK를 생성합니다. 별도 시험용 사본에 디버그 서명을 적용해 Pleos에서 실행한 결과 기존 휴식 설정 11개 항목과 쉴 곳 목록 468곳을 읽었습니다. 시험 후 차량용 디버그 APK를 다시 설치했습니다.

## 화면에서 연결할 기능

| 기능 | 현재 연결 지점 |
| --- | --- |
| 휴식 설정 저장, 적용, 복귀 | `HomeViewModel`의 `savePreset`, `applyPreset`, `restoreSession`. 저장은 차량용과 데모에서 제공하고, 적용과 복귀는 데모에서 제공 |
| 차량 알람 | `VehicleRestAlarm`의 `requestScheduleAfter`, `scheduledTimeMillis`, `cancel`. 예약 API는 현재 차량 상태와 권한을 검사하고 결과를 반환 |
| 휴대폰 알람과 타이머 | `sendBluetoothAlarm`, `receiveBluetoothAlarm`으로 시각을 전달하고 `requestPhoneAlarm`, `requestPhoneTimer`로 기본 시계에 요청 |
| 쉴 곳 목록 | `RestPlaceCatalog`의 로드와 갱신, `searchRestPlaces`와 `rankRestPlaces` |
| 기본 내비 | `RestNavigationClient`의 `refreshRoute`, `setDestination`, `addWaypoint`와 요청 확인 상태를 담은 `state` |
| 동승석과 공조 | `PleosRestControls.kt`의 `adjustPassengerSeat`와 `readRestClimatePower`. 좌석 이동과 공조 제어는 Pleos 디버그 에뮬레이터에서 사용 |
| 전기차 배터리 감시 | `RestBatteryMonitor`의 `requestStart`, `stop`, `running`과 `RestBatteryThreshold`의 `read`, `save`, `observe` |
| 음악 | `RestMusicPlayer`의 `play`, `stop`, `openMusicApp`와 재생 상태를 담은 `state` |
| 앱 내 음성 | 차량용 `recognizeRestSpeech`로 한 번 인식한 문장을 받고 `parseRestVoice`로 명령 해석 |
| Gleo | `OPEN_REST_SETTINGS`, `PREPARE_REST_ALARM`, `OPEN_REST_PLACES` 앱 액션 |

휴대폰 알람 옵션을 켜면 음성 알람 요청은 예약 시간을 입력하고, 전송과 확인은 기존 알람 버튼에서 처리합니다. Pleos 에뮬레이터에서 Gleo와 같은 15분 앱 액션을 전달해 차량 및 휴대폰 설정이 유지되는 것을 확인했습니다.

저장 기능과 세션 복구 기반은 [W04 저장 검증](STORAGE_VALIDATION.md)에 있습니다. 자동 적용과 복귀는 데모에서 검증했으며, 차량 빌드에서 실제 위치 재현은 아직 확인하지 못했습니다.

좌석 이동과 정지는 Virtual Console에서 확인했습니다. 현재 Pleos 이미지의 SDK 위치 조회는 움직인 위치를 반영하지 않아 자동 재현은 미검증입니다. [좌석 SDK 검증 기록](SDK_VALIDATION.md)

백그라운드 음성, Gleo 앱 호출, 현대차 시트 메모리 가져오기는 [연동 조사와 검증 계획](VOICE_AND_SEAT_MEMORY.md)을 확인합니다.

갱신: 2026-11-08 (Asia/Seoul). 원래 대화를 읽지 않은 개발자·새 세션도 이 폴더에서 이어 작업할 수 있도록 정리한 문서입니다.

## 읽는 순서

| 순서 | 문서 | 알 수 있는 것 |
| --- | --- | --- |
| 1 | [CONTEXT.md](CONTEXT.md) | 사용자의 제품 의도·요구사항, 변경 결정, 제안과 확정의 구분 |
| 2 | [DESIGN.md](DESIGN.md) | 상세 사용자 흐름·화면·상태·제어·데이터·오류 처리·전체 MVP 수용 기준 |
| 3 | [DEVELOPMENT.md](DEVELOPMENT.md) | 소스 구조와 고정 버전, SDK·캐시 경로, 빌드·설치 명령 |
| 4 | [VERIFICATION.md](VERIFICATION.md) | 실제 통과한 검증, 근거 커밋·CI 링크, 아직 미검증인 범위 |
| 5 | [ROADMAP.md](ROADMAP.md) | 다음 작업의 순서·관련 파일·완료 조건·미확정 사항 |
| 참고 | [REFERENCES.md](REFERENCES.md) | 시장 조사, 공모전·제출, 제공 앱·아이디어 사례, 공식 SDK 출처 |

상세 설계는 루트 README에서 [DESIGN.md](DESIGN.md)로 이관했습니다. 루트 README는 소개와 실행 요약·문서 링크만 제공합니다. 이 폴더에서 제품·개발 맥락을 읽기 위해 대화 기록이나 루트 README를 별도로 참조할 필요는 없습니다. 실제 구현 시 소스 코드와 연결한 공식 API 문서는 함께 확인합니다.

## 현재 출발점

- 앱: **자비스 (자.peace)**. 주차한 개인 차량에서 짧게 쉬는 사람을 위한 앱.
- 우선 흐름: 직접 맞춘 자세·환경 저장 → P·정지 확인 → 설정 적용 → 휴식 → 사용자 요청으로 이전 설정 복귀.
- 쉴 곳 검색에서 공공데이터의 졸음쉼터와 휴게소를 조회합니다. Pleos 기본 내비의 목적지 설정과 경유지 추가를 에뮬레이터에서 검증했습니다. 외부 내비와 숙소 검색은 보류했습니다.
- Pleos와 Android 휴대폰 에뮬레이터에서 차량과 기본 시계 알람을 같은 시각으로 예약하고 양쪽 오디오 시작을 확인했습니다. Gleo 음성 검색은 자비스가 종료된 상태에서도 동작했습니다.
- 전기차 휴식의 배터리와 공조, 음악, 좌석 위치 조회 및 제어 시험은 [SDK 검증 기록](SDK_VALIDATION.md)에 있습니다. 현대차 시트 메모리 슬롯 동기화는 범위에서 제외했습니다.
- 로컬 실행 환경과 최신 검증은 [LOCAL_VALIDATION](LOCAL_VALIDATION.md)을 확인합니다. 초기 문서의 미연결 placeholder 설명과 미검증 목록은 당시 기준입니다.
- 다음 핵심 작업: 좌석 위치 조회, 이동, 중단과 저장 위치 재현 방식 검증.
- 로컬 저장소: `F:\Projects\pleos`. SDK·Gradle 캐시도 F: 사용. 세부 경로는 DEVELOPMENT에 기록.
- 2026-11-07 기준 로컬·Linux CI에서 두 debug APK와 주차 정책 테스트 10개, Lint 오류 없이 통과. 실제 차량 기능 검증은 아님.

## 문서 해석과 갱신

사용자 요구는 CONTEXT, 구현 설계는 DESIGN, 실행 상태의 증거는 코드와 VERIFICATION을 기준으로 구분합니다. 문서 안의 '제안', '후속', '미검증'을 완료되거나 사용자에게 확정받은 기능으로 해석하지 않습니다. 최신 사용자 요청이 기존 문서와 다르면 그 요청을 반영하고 관련 문서를 함께 갱신합니다.

새 기능을 구현하면 실제 변경·검증 결과를 VERIFICATION에, 다음 작업을 ROADMAP에 기록합니다. SDK 함수명·단위·권한·오류 의미를 새로 확인하면 DESIGN과 검증 기록에 근거를 남깁니다. 단순 호출 성공, 실제 값 변화, 물리적 동작 확인을 구분합니다.

## 새 세션에 전달할 문장

> docs/README.md부터 읽고 CONTEXT, DESIGN, DEVELOPMENT, VERIFICATION, ROADMAP을 확인한 뒤 현재 코드와 Git 상태를 점검하세요. 기본 내비, 알람, Gleo의 에뮬레이터 검증은 SDK_VALIDATION과 ROADMAP을 확인하세요. 좌석 제어와 차량 전원 상태는 에뮬레이터에서 확인한 수준을 기록하세요.
