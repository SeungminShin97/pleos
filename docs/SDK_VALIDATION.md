# W03 좌석 SDK 기술 검증

W04에서 좌석 읽기와 설정 저장을 추가했습니다. 현재 vehicle 빌드는 좌석 읽기 권한을 포함하며, 좌석 제어 권한은 debug 검증용에만 있습니다. 아래 W03 시험의 당시 권한 및 미검증 범위와 구분하며 [STORAGE_VALIDATION](STORAGE_VALIDATION.md)을 함께 확인합니다. 실제 좌석 이동과 자동 재현은 여전히 미검증입니다.

검증일: 2026-11-08, Asia/Seoul.

## 결론과 단계 상태

**좌석 capability와 현재 위치 조회, 방향 및 STOP 명령의 콜백은 확인했습니다. 실제 위치 변화와 저장 위치 재현은 현재 에뮬레이터에서 입증하지 못했습니다. W03의 자동 이동 및 중단, 위치 재현 완료 조건은 아직 충족하지 않았습니다.**

실제 차량의 좌석을 움직인 검증이 아닙니다. 이번 결과로 자동 적용이나 복귀 버튼을 활성화하지 않습니다. W04의 저장 구조는 별도로 개발할 수 있지만, W05와 W06에서 자동 좌석 이동을 제공하려면 위치 변화와 중단, 재현을 검증할 환경이 먼저 필요합니다.

## 환경과 재현 코드

- 체크아웃 HEAD: `b642b6060c5189db0f29ef73f792c4421a25c3f1`, W02 및 이번 W03 로컬 변경 포함. 이번 변경은 commit 또는 push하지 않았습니다.
- Pleos Connect v2.1.0, Android 14 / API 34, x86_64 Cold Boot, WHPX. 상세 환경은 [LOCAL_VALIDATION](LOCAL_VALIDATION.md).
- Vehicle SDK `2.3.2`, 설치된 `ai.pleos.playground.service.vehicle`는 `versionName=2.3.22`, `versionCode=74`. 공식 SDK 소개 문서는 Vehicle Service 2.3.21 기준이므로 실제 설치 버전과 구분합니다.
- `app/src/androidTestVehicle/java/io/github/seungminshin97/japeace/validation/SeatValidationInstrumentation.kt`: 별도 instrumentation APK의 검증 코드. 기본 동작은 읽기 전용 inventory입니다. 좌석 명령은 명시적인 `operation=motion` 또는 `operation=memory`에서 호출합니다.
- `app/src/vehicleDebug/AndroidManifest.xml`: 테스트 대상 debug 앱에만 `pleos.car.permission.CAR_SEATS`, `pleos.car.permission.CONTROL_CAR_SEATS`를 선언합니다. 이미지에서 두 권한 모두 protectionLevel normal인 것을 확인했습니다.
- `app/build.gradle.kts`: vehicle 테스트 runner와 SDK Car API용 androidTest compileOnly 경로를 지정합니다. 라이브러리 구현을 복사하거나 임의의 SDK setter를 추가하지 않았습니다.

검증 APK는 알려진 `Pleos/crp_avd/` fingerprint, `crp_avd_x86_64` device, debuggable 앱에서만 실행됩니다. 이동 전에는 AAOS의 정상 P와 유한한 속도 0을 직접 재조회합니다. 명령 후에는 성공 콜백과 실제 위치 재조회 결과를 별도로 기록하며, 이동 호출의 예외나 timeout 경로에서도 STOP을 시도합니다. 이 제한된 실험 코드는 제품용 제어기나 실차 안전 검증을 대신하지 않습니다.

## 확인한 공개 API

표준 Android Automotive 좌석 위치 속성을 `position-config` 계측으로 확인했습니다. Pleos Connect 2.1.0의 Vehicle Debug 앱에 표준 좌석 제어 권한을 선언했으나 시스템이 부여하지 않았습니다. 앱에 보이는 속성 목록에는 전후, 높이, 등받이 위치 속성이 모두 없었습니다. 이 계측은 권한과 속성 목록만 읽었으며 활성 차량 앱 권한으로 표준 위치 쓰기를 사용할 수 없음을 보여 줍니다. [Android Automotive 차량 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds)

| 목적 | API 및 의미 |
| --- | --- |
| 연결 | `Vehicle(context)`, `initialize()`, `getSeat()`, `release()` |
| 위치 지원 범위 | `checkSeatForeAftPositionCapabilities(onComplete(config, areaMap), onFailed)` 등 축별 API |
| 이동 지원 영역 | `checkMoveSeatForeAftCapabilities(onComplete(config, areas), onFailed)` 등 축별 API |
| 위치 읽기 | `getCurrentSeatForeAftPosition(area, onComplete(Int?), onFailed)` 등 축별 API |
| 방향 명령 | `moveSeatForeAft(area, MoveControl, onComplete(area, MoveControl?), onFailed)` 등 축별 API |
| 중단 명령 | 같은 move API에 `MoveControl.STOP` 전달 |
| 위치 이벤트 | `registerSeatForeAftPosition` 및 `unregisterSeatForeAftPosition` |
| 차량 메모리 슬롯 | `checkSeatMemoryCapabilities`, `selectSeatMemory(area, slot, onFailed)` |

`MoveControl`의 공개 값은 `FORWARD`, `BACKWARD`, `STOP`입니다. 숫자 목표 위치를 받는 함수가 아닙니다. `Seat`의 공개 메서드 목록에서 임의의 raw 위치를 지정하는 setter나 현재 자세를 새 메모리 슬롯에 저장하는 API는 확인하지 못했습니다. 메모리 슬롯 선택은 앱이 읽은 임의 위치를 저장하고 재현하는 기능과 동일하지 않습니다. 이번에는 슬롯 선택을 실행하지 않았습니다.

위치 콜백은 nullable입니다. null은 정상 위치로 사용하지 않습니다. 공식 오류 처리 문서의 `VehicleException` 타입은 이 2.3.2 AAR에서 그 이름으로 컴파일되지 않아, 검증 코드는 예외의 실제 클래스명과 message를 기록합니다. 특정 오류 코드를 임의로 매핑하지 않았습니다.

## capability와 현재 값

아래 값은 2026-11-08 이 이미지에서 조회한 raw 정수값입니다. **0..100을 퍼센트, 각도를 도, 거리를 mm로 단정하지 않습니다. 물리 단위는 미확정입니다.** 영역 숫자는 `SeatValue.id`로 확인했습니다.

- 앞좌석: `ROW_1_LEFT=1`, `ROW_1_RIGHT=4`.
- 뒷좌석: `ROW_2_LEFT=16`, `ROW_2_RIGHT=64`.

| 축 | 지원 영역 | 위치 범위 raw | 처음 읽은 값 |
| --- | --- | --- | --- |
| 전후 foreAft | 1, 4, 16, 64 | 0..100 | 모두 0 |
| 등받이 backrest | 1, 4, 16, 64 | 0..100 | 모두 0 |
| 높이 height | 1, 4, 16, 64 | 0..100 | 모두 0 |
| 기울기 tilt | 1, 4, 16, 64 | 0..100 | 모두 0 |
| 깊이 depth | 1, 4 | -10..10 | 모두 0 |
| 안전벨트 높이 beltHeight | 1, 4 | 0..10 | 모두 10 |
| 헤드레스트 각도 headrestAngle | 1, 4 | -10..10 | 모두 0 |
| 헤드레스트 전후 headrestForeAft | 1, 4 | -10..10 | 모두 0 |
| 헤드레스트 높이 headrestHeight | 1, 4 | -10..10 | 모두 0 |
| 요추 전후 lumbarForeAft | 1, 4 | -10..10 | 모두 0 |
| 요추 측면 lumbarSide | 1, 4 | -10..10 | 모두 0 |

11개 축의 위치 capability와 이동 capability 모두 조회됐습니다. 위치값은 총 30개를 조회했습니다. 위치 및 이동 capability의 accessType은 READ_WRITE였습니다. 이것은 서비스가 보고한 지원 정보이며 실제 이동 성공 증거가 아닙니다.

메모리 capability는 앞좌석 왼쪽 영역 1, 슬롯 1..2, accessType WRITE였습니다. 슬롯의 현재 저장 자세, 슬롯을 갱신하는 방법과 이동 중단 의미는 이번 시험에서 확인하지 않았습니다.

후속 조사에서 Vehicle SDK 2.3.2의 공개 API와 Android Automotive 표준 속성 모두 슬롯에 저장된 값의 조회를 제공하지 않는다는 점을 확인했습니다. 후속 시험에서 테스트 러너를 지정해 Pleos Connect 2.1.0의 슬롯 1번과 2번을 각각 요청했습니다. P와 속도 0을 확인한 뒤 운전석 11개 축을 요청 전과 요청 후 세 차례 비교했습니다. 양쪽 슬롯 모두 위치 변화와 실패 콜백이 관측되지 않았습니다. 재현 시 Gradle에 `-PvalidationRunner=io.github.seungminshin97.japeace.validation.SeatValidationInstrumentation`을 지정하고 instrumentation에 `-e operation memory -e slot 1` 또는 `-e slot 2`를 전달합니다. 현재 위치 가져오기 조건과 실차 검증 계획은 [시트 메모리 연동 조사](VOICE_AND_SEAT_MEMORY.md#현대차-시트-메모리-동기화)에 정리했습니다.

## 이동과 중단 시험

Virtual Console에서 가상 운전석의 전후 40, 등받이 45, 높이 50, 기울기 55, 요추 60을 게시하고 각 콘솔 값을 확인했습니다. 같은 상태의 SDK `inventory`에서 대응 위치 다섯 개는 모두 0으로 조회됐습니다. 시험 후 콘솔의 다섯 위치를 원래 값인 0으로 복원했습니다. 이 이미지에서는 전후 외의 위치 축에서도 Virtual Console 값과 앱의 SDK 조회가 일치하지 않았습니다.

운전석 전후 위치 40에서 SDK의 앞으로 400ms 및 뒤로 400ms 명령을 차례로 실행했습니다. Virtual Console에서 40, 60, 40을 관측했고 두 방향 모두 STOP 뒤 이동 방향은 0이었습니다. SDK 위치 조회는 양쪽 시험 모두 0이었으므로 이 결과는 시험자가 콘솔로 확인한 단일 축의 왕복 이동입니다. 계측을 마친 뒤 초기 콘솔 값 0을 복원했습니다.

Virtual Console에서 가상 운전석 전후 위치를 40으로 설정한 뒤 P 상태에서 SDK로 400ms 앞으로 이동과 정지를 요청했습니다. 두 명령이 응답했고 콘솔은 위치 60 및 이동 방향 0을 표시했습니다. SDK 위치 변경 콜백은 초기값 0만 전달했고, SDK 조회는 이동과 정지 전후 모두 0이었습니다. 시험 후 콘솔 위치를 원래 값인 0으로 복원했습니다. 이 이미지에서는 SDK 조회나 콜백으로 가상 위치를 읽어 저장 위치 재현을 확인할 수 없었습니다.

| 시험 | 요청과 관측 | 판단 |
| --- | --- | --- |
| D, 속도 0에서 전후 이동 요청 | P 확인에서 차단, `commandSent` 기록 없음 | 검증 코드의 주행 차단 확인, SDK 자체 거절 시험은 아님 |
| 전후 FORWARD, 400ms | FORWARD와 STOP 콜백, 위치 0 → 0 → 0 | 실제 이동 미관측 |
| 전후 BACKWARD, 400ms | BACKWARD와 STOP 콜백, 위치 0 → 0 → 0 | 반대 방향도 실제 이동 미관측 |
| 전후 BACKWARD, 2000ms | 초기 위치 이벤트 raw 0, BACKWARD와 STOP 콜백, 재조회 0 → 0 → 0 | 관측 시간을 늘려도 위치 변화 미관측 |
| 깊이 FORWARD, 2000ms | 범위 -10..10의 안쪽 값 0에서 FORWARD와 STOP 콜백, 재조회 계속 0 | 전후 축의 경계값만으로 설명할 수 없음 |
| 등받이 BACKWARD, 2000ms | BACKWARD와 STOP 콜백, 재조회 계속 0 | 다른 주요 축에서도 실제 이동 미관측 |

기간은 실험 코드에 준 설정값이며 성능 측정값이 아닙니다. 재조회는 STOP 직후와 500ms 뒤에 수행했습니다. STOP 뒤 값이 같다는 사실만으로 움직이던 좌석이 물리적으로 멈췄다고 판정하지 않습니다. 이동 자체가 관측되지 않았으므로 실제 중단 효과는 미검증입니다.

각 motion 로그의 `finished.success=true`는 검증 절차가 예외 없이 끝났다는 뜻입니다. 좌석 이동 성공은 별도 `motionObservation.changed`로 구분하며 이번에는 모두 false입니다. 원위치 재현은 `replayNotAttempted`로 기록됐습니다. 검증 코드의 제한된 재현 실험 분기는 실행되지 않았으므로 검증된 제어 알고리즘으로 사용하지 않습니다.

Virtual Console의 48개 기능 목록에는 Seat Occupancy가 있었지만 좌석 위치를 직접 바꾸는 카드는 보이지 않았습니다. VHAL 서비스 진단의 `dumpsys ... --help`도 FAILED_TRANSACTION을 반환했습니다. 에뮬레이터의 좌석 동역학 지원 누락, 서비스 내부 처리와 앱 요청 반영 경로 가운데 어느 것이 원인인지는 확정하지 않았습니다. 시스템 제한을 변경하거나 SDK 외부 setter로 결과를 만들어내지 않았습니다.

## 빌드 및 기존 기능 회귀 검증

두 debug 앱, vehicle instrumentation APK 빌드와 두 변형의 단위 테스트 및 Lint를 실행했습니다. 기존 정책과 관측 상태 단위 테스트는 demo 10개, vehicle 10개로 총 20개, 실패와 오류 0입니다. 신규 instrumentation 절차는 이 단위 테스트 개수에 포함하지 않습니다. vehicleRelease의 병합 Manifest에 좌석 읽기 및 제어 권한이 없는 것도 확인했습니다. release APK나 실차 기능을 검증한 것은 아닙니다.

## 실행 방법과 증거

SDK 경로는 [LOCAL_VALIDATION](LOCAL_VALIDATION.md)을 참고합니다. 기존 AVD가 실행 중이며 P와 정지 상태여야 합니다. 대상 앱에는 W02의 `CAR_SPEED` 권한을 허용해야 합니다.

```powershell
$env:ANDROID_HOME = 'C:\resume\.tools\pleos-sdk'
$env:GRADLE_USER_HOME = 'C:\Users\rnsdl\.gradle'
.\gradlew.bat :app:assembleVehicleDebug :app:assembleVehicleDebugAndroidTest
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb -s emulator-5554 install --user 10 -r app/build/outputs/apk/vehicle/debug/app-vehicle-debug.apk
& $adb -s emulator-5554 install --user 10 -r app/build/outputs/apk/androidTest/vehicle/debug/app-vehicle-debug-androidTest.apk
& $adb -s emulator-5554 shell am instrument --user 10 -w -e operation inventory io.github.seungminshin97.japeace.test/io.github.seungminshin97.japeace.validation.SeatValidationInstrumentation
# 아래는 알려진 Pleos 에뮬레이터에서만 실행하는 명시적 이동 실험입니다.
& $adb -s emulator-5554 shell am instrument --user 10 -w -e operation motion -e axis foreAft -e direction BACKWARD -e pulseMillis 2000 io.github.seungminshin97.japeace.test/io.github.seungminshin97.japeace.validation.SeatValidationInstrumentation
```

로컬 증거 디렉터리: `C:\resume\.tools\pleos-w03-evidence`.

- `inventory.log`: 지원 축, 영역, 범위와 실제 읽은 값.
- `blocked-in-drive.log`: D 상태의 사전 차단.
- `motion-foreAft-left.log`, `motion-foreAft-left-backward.log`: 양방향 400ms 시험.
- `motion-foreAft-2000ms.log`, `motion-depth-2000ms.log`, `motion-backrest-2000ms.log`: 2초 시험과 STOP 및 재조회.
- `seat-public-api.txt`: 설치한 SDK의 공개 Seat 선언 목록.
- `final-build.log`: 빌드와 회귀 검사 결과.

미검증: 나머지 8개 축과 다른 좌석의 이동, 실제 움직임 중 중단, 저장한 임의 위치 재현, SDK 자체의 주행 거절 및 권한 오류, 실차 timeout과 지연 명령, 물리 단위, 여러 축의 이동 순서와 충돌, 메모리 슬롯 선택과 저장.

## 근거와 후속 조치

공식 문서: [SDK 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), [좌석 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission), [도메인 연결](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/obtain-domain-object), [Seat 공개 API](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/entry), [전후 이동](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/move-seat-fore-aft), [MoveControl](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.constant.control/move-control/entry).

다음 기술 검증에서는 좌석 위치 변화가 지원되는 이미지나 검증 장비에서 같은 probe를 실행해 명령 이후 변화, STOP 효과, 목표 위치 재현을 확인해야 합니다. 그 전에는 앱의 자동 좌석 적용과 복귀를 제공하지 않습니다. W04에서는 관측한 설정을 저장하되 자동 재현 지원 여부를 별도로 보관하는 구조까지 개발할 수 있습니다.
# 카카오맵 연결

휴식 장소 검색을 열면 Pleos 기본 내비에 현재 위치를 요청합니다. 위치 응답이 있으면 현재 위치와 선택한 장소를 카카오맵 자동차 길찾기에 전달합니다. 출발지 정보가 없으면 장소 위치를 표시하는 요청을 보냅니다.

- Pleos 에뮬레이터에서 태인 졸음쉼터를 선택하고 카카오맵 버튼을 눌렀을 때, 카카오맵을 열 수 없다는 안내가 표시되는 것을 확인했습니다.
- 현재 위치 자동 조회를 추가한 뒤, Pleos 에뮬레이터에서 경로 정렬 버튼을 누르지 않고 태인 졸음쉼터를 선택해 카카오맵 길찾기 버튼이 표시되는 것을 확인했습니다.
- Pleos의 현재 경로 조회에 성공하면 카카오맵으로 출발지, 기존 경유지, 선택한 쉼터, 기존 목적지를 전달할 수 있습니다. 쉼터는 마지막 경유지로 추가하며, 카카오맵의 최대 5개 경유지 제한을 적용합니다.
- Pleos 에뮬레이터에서 현재 경로 기준으로 정렬한 뒤 횡성(강릉) 휴게소를 선택했습니다. 카카오맵 경유지 추가 버튼이 표시됐고, 버튼을 누르면 카카오맵을 열 수 없다는 안내가 표시됐습니다.
- Pleos 기본 내비에서는 태인을 목적지로 설정한 뒤 전주와 삼례를 순서대로 추가했습니다. 자비스에서 각 요청의 경유지 추가 확인 문구를 확인했습니다. 내비가 경유지 좌표를 `0.0, 0.0`으로 반환하면 요청한 순서의 경유지 이름으로 확인합니다.
- 같은 경로에서 현재 경로 기준 정렬을 시험했습니다. 내비는 전주와 삼례의 기본 좌표를 `0.0, 0.0`으로 반환했고 표시 좌표도 없었습니다. 자비스는 두 경유지가 정렬에서 제외됐다고 표시하고, 확인한 현재 위치와 태인 목적지 사이 직선 거리로 검색 결과를 정렬했습니다.
- 에뮬레이터에 카카오맵이 없어 외부 앱의 수신과 길찾기 화면은 미검증입니다. 카카오내비 SDK에는 별도의 네이티브 앱 키와 앱 등록이 필요합니다.
- 공식 문서 [카카오맵 URL Scheme](https://apis.map.kakao.com/android_v2/docs/api-guide/urlscheme/), [카카오내비 Android SDK](https://developers.kakao.com/docs/en/kakaonavi/android)

## TMAP 목적지 연결

빌드 환경에 `TMAP_APP_KEY` 또는 Gradle 속성 `tmapAppKey`로 발급받은 TMAP 연동 앱 키를 설정하면 휴식 장소 상세에 티맵 길찾기 버튼이 나타납니다. 버튼은 공식 HTTPS 연결 주소에 장소 이름, 경도, 위도를 전달합니다. 이 키는 APK 리소스와 연결 URL에 포함되므로 모바일 앱에서 공개 가능한 연동 키를 사용합니다.

공식 샘플 [TMAP Invoke 길안내](https://tmapapi.tmapmobility.com/webv2/sample/webSample61.html)

Pleos 에뮬레이터에서 테스트용 문자열을 설정한 APK를 설치하고 태인 졸음쉼터 상세에 티맵 길찾기 버튼이 표시되는 것을 확인했습니다. 실제 발급 키로 인증한 결과와 TMAP 앱의 경로 화면은 미검증입니다.

## 주변 숙소 검색

Gleo 스키마에 주변 숙소 검색의 `query=숙소` 전달 규칙을 추가했습니다. 직접 Intent를 전달하는 시험에 이어, Pleos Connect 2.1.0 에뮬레이터에서 홈 화면으로 나간 뒤 “자비스에서 주변 숙소를 찾아 줘”를 16 kHz 모노 합성 음성으로 입력했습니다. Gleo 로그에서 발화 인식, `OPEN_REST_PLACES` 선택, `{"query":"숙소"}` 전달을 확인했고, 자비스 화면에 주변 숙소 검색 버튼이 표시됐습니다.

쉴 곳 검색에서 주변 숙소 검색을 누르면 Pleos가 제공한 현재 좌표와 검색어 `숙소`를 카카오맵에 전달합니다. 위치를 확인하지 못하면 검색 요청을 보내지 않습니다. Pleos 에뮬레이터에서 버튼을 누른 뒤 카카오맵을 열 수 없다는 안내를 확인했습니다. 카카오맵의 실제 검색 결과는 미검증입니다. [카카오맵 검색 규격](https://apis.map.kakao.com/android_v2/docs/api-guide/urlscheme/#검색하기)

## 외부 음악 앱 열기

차량 빌드에서 Android Automotive의 `MEDIA_TEMPLATE`으로 기본 미디어 센터를 요청합니다. Pleos에서 자비스의 음악 앱 열기를 누르면 내장 미디어 앱 `ai.umos.mediaapp`에 요청이 전달되고 자비스의 재생은 중지됐습니다. Pleos 미디어 앱은 화면을 띄우는 중 `NullPointerException`으로 종료됐습니다. 시스템 미디어 앱의 자체 실행 액션으로도 화면 진입을 확인하지 못했습니다. [Android Automotive 미디어 앱 연동](https://developer.android.com/training/cars/media/automotive-os)

사용자가 음악 앱 열기를 누를 때 Android 음악 앱 카테고리로 실행을 요청합니다. Pleos 에뮬레이터에서 버튼과 음악 앱을 열 수 없다는 안내를 확인했습니다. 기본 음악 패키지의 런처 Activity 조회도 결과가 없었습니다. 외부 앱 실행 성공은 미검증입니다.

앱 내 휴식 음악은 직접 합성한 12초 음원을 반복 재생합니다. 재생 전 미디어 오디오 포커스를 요청하고, 포커스를 잃거나 앱 화면이 일시 정지되면 재생을 종료합니다. Pleos 에뮬레이터에서 Android 오디오 진단의 MediaPlayer 재생 상태와 캘린더 앱 전환 후 중지를 확인했습니다. [Android 오디오 포커스](https://developer.android.com/media/optimize/audio-focus)

1분 차량 휴식 알람을 예약하고 음악을 재생했습니다. 알람이 전달되자 앱 화면의 버튼이 재생 상태에서 중지 상태로 돌아왔고 알림 ID 701이 등록됐습니다. 실제 알람 소리와 통화 및 내비 음성에 의한 중단은 미검증입니다.
## 전기차 휴식 정보

현재 Virtual Console 카탈로그의 차량 전원 상태 입력으로 P 상태에서 On, Off, On을 순서대로 게시했습니다. Off 직후와 약 10초 뒤에 콘솔은 공조 켜짐을 표시했고 Android는 계속 `Awake`였습니다. 시험 전 콘솔에 전원 초기값이 없어서 On을 기준값으로 설정했고 시험 후 On으로 돌렸습니다. 이번 입력은 가상 차량 신호만 바꿨으며, Android 종료나 차박 모드 진입은 관측되지 않았습니다.

Pleos의 Virtual Console 세션이 활성화된 상태에서 자비스로 공조 켜짐을 조회했습니다. 화면을 끈 후 약 15초, 45초, 90초에 Android 전원 상태는 `Asleep`이었고 가상 차량의 `hvac.power_on`은 모두 `true`였습니다. 시험 후 화면을 다시 켰습니다. 이 결과는 화면 절전 중의 가상 공조 상태이며, 차량 전원 차단이나 장시간 차박 모드는 별도입니다.

배터리 잔량 계산은 Android Automotive의 현재 사용 가능 용량(`EV_CURRENT_BATTERY_CAPACITY`)을 우선 사용합니다. 해당 값이 없거나 유효하지 않으면 정격 용량을 사용하며, 앱은 화면에 계산 기준을 표시합니다. Vehicle APK 빌드와 Vehicle 및 Demo 단위 테스트를 통과했습니다. 최신 APK를 설치한 Pleos 에뮬레이터에서 현재 용량 기준 약 100%가 표시됐습니다. Virtual Console에서 남은 에너지를 15 kWh로 낮추자 약 19%와 저잔량 경고가 표시됐고, 원래 값으로 되돌리자 약 100%로 복귀하고 경고가 사라졌습니다. 정격 용량으로 전환하는 경로는 이 이미지에서 검증하지 못했습니다. [Android 차량 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds#EV_CURRENT_BATTERY_CAPACITY)

Virtual Console 세션이 활성화된 상태에서 자비스로 공조를 켠 다음 캘린더로 전환했습니다. 콘솔에서 전환 후 10초, 20초, 30초에 공조 켜짐을 관측했고, 시험이 끝난 뒤 원래의 꺼짐 상태로 복원했습니다. 이 시험은 앱 화면을 벗어난 뒤의 공조 상태를 확인했으며, 차량 절전이나 장시간 차박은 시험하지 않았습니다.

공조 제어 버튼의 주차 조건도 확인했습니다. 정지한 Pleos 에뮬레이터에서 Virtual Console로 기어를 P → D → P로 바꾸고, 공조 켜기와 끄기가 각각 활성 → 비활성 → 활성으로 바뀌는 것을 버튼 상태로 검증했습니다. 시험 종료 시 기어는 P입니다.

Virtual Console 세션을 활성화한 상태에서 자비스의 공조 켜기 → 끄기를 연속 실행했습니다. 켜기 후 앱 재조회와 콘솔 `hvac.power_on`이 모두 켜짐을, 끄기 후 모두 꺼짐을 표시했습니다. 콘솔 세션이 비활성인 앞선 시험에서는 켜기 응답 이후 실제 값 변경이 관측되지 않았습니다. 이 결과는 Pleos 에뮬레이터의 공조 전원 전환을 검증하며 장시간 공조 유지는 별도입니다.

공조 제어 요청은 구역마다 한 번만 보내고, 응답 뒤 최대 6초간 상태를 다시 읽습니다. Pleos 에뮬레이터에서 재시험했을 때 켜짐은 재조회 시간 안에 확인되지 않았고, 이어서 끄기를 요청했을 때 꺼짐이 조회됐습니다.

Pleos 에뮬레이터 디버그 빌드에 주차 조건을 확인하는 공조 켜기와 끄기를 추가했습니다. Pleos HVAC의 제어 응답을 받은 후 구역별 상태를 다시 읽습니다. 에뮬레이터에서는 켜기 요청 응답 이후에도 기대한 상태를 확인하지 못했습니다. 실제 제어 성공으로 표시하지 않습니다. [Pleos Vehicle SDK 권한](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk)

후속 시험에서 Pleos Virtual Console의 공조 전원을 켜고 끈 뒤 자비스에 각각 공조 켜짐과 꺼짐이 표시되는 것을 확인했습니다. 시험 후 기존 공조 꺼짐 상태로 복원했습니다. 앱에서 공조 전원을 바꾸는 기능과 장시간 유지는 남아 있습니다.

공조 상태 확인은 Pleos HVAC의 `checkHvacPowerCapabilities`로 지원 구역을 확인한 뒤 각 구역의 `getHvacPowerState`를 읽습니다. Pleos 에뮬레이터에서 공조 꺼짐 표시를 확인했습니다. 공조가 켜진 상태, 장시간 유지, 전원 제어는 미검증입니다. [Pleos HVAC API](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/entry)

배터리 감시는 P 및 정지 상태에서 시작할 수 있습니다. Pleos 에뮬레이터에서 감시 중 가상 기어를 D로 바꾸자 감시가 중지되는 것을 확인하고 P로 복원했습니다.

Pleos Vehicle SDK 2.3.2 공개 도메인 목록과 SDK 패키지에서 차박 또는 유틸리티 모드의 전원 유지 API를 확인하지 못했습니다. Android 공개 CarPowerManager는 전원 정책 조회와 변경 수신을 제공합니다. 공조 제어는 Pleos HVAC 도메인에서 별도로 제공하며 장시간 차량 전원 유지의 검증이 필요합니다. [Pleos Vehicle SDK](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/Vehicle/intro), [Android CarPowerManager](https://developer.android.com/reference/android/car/hardware/power/CarPowerManager)

배터리 잔량이 저장된 기준 이하가 되면, 앱 알림이 허용된 경우 별도의 배터리 경고 알림을 등록합니다. Pleos 에뮬레이터의 Battery Energy를 15 kWh로 낮춰 알림 ID 702가 등록되고, 원래 값으로 되돌린 뒤 해당 알림이 해제되는 것을 확인했습니다.

배터리 감시 시작을 누르면 차량 빌드의 포그라운드 서비스가 화면 전환 이후에도 차량 배터리 값을 조회합니다. Pleos 에뮬레이터에서 감시를 시작하고 캘린더로 전환한 뒤 Battery Energy를 15 kWh로 낮춰 알림 ID 702를 확인했습니다. 에너지를 원래 값으로 복원하고 자비스에서 감시를 중지했습니다. 차량 전원 종료나 강제 종료 이후 감시 재개는 지원 범위 밖입니다. [Android 포그라운드 서비스 유형](https://developer.android.com/develop/background-work/services/fgs/service-types)

배터리 경고 기준의 기본값은 20%입니다. 1~99% 사이의 값을 저장하면 앱을 다시 열어도 적용됩니다. Pleos 에뮬레이터에서 35%를 저장하고 앱 프로세스를 종료한 뒤 재실행해 복원을 확인했습니다. 화면과 감시 서비스가 같은 설정 API를 사용하도록 변경한 뒤, 41%를 저장하고 강제 종료 후 다시 실행해 41%가 표시되는 것을 확인했습니다. 테스트 전 값인 34%로 복원했습니다. 같은 설치본에서 감시 시작 시 포그라운드 서비스와 알림 ID 703이 등록되고, 감시 중지 시 서비스가 제거되고 시작 버튼이 다시 나타나는 것을 확인했습니다. 당시 앱이 배터리 잔량을 읽지 못해 감시 중 기준 변경에 따른 알림 ID 702 전환은 미검증입니다. 경고 판단의 경계값은 단위 테스트로 검증했습니다.

Virtual Console의 Battery Energy를 25 kWh로 바꾸자 약 32%가 표시되고 저장된 35% 기준의 경고가 나타났습니다. 원래 에너지 값으로 복원하자 100%로 돌아오며 경고가 사라졌습니다. 별도의 Battery SOC 카드를 35%로 바꿨을 때는 Battery Energy 값과 앱 표시가 유지됐습니다. 두 시뮬레이션 값을 각각 원래 상태로 복원했습니다. [Pleos Virtual Console 가이드](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)

차량 배터리의 남은 에너지를 정격 사용 가능 용량으로 나눈 추정치, 충전 케이블 연결 여부, 차량이 제공하는 충전 상태를 각각 표시합니다. 에너지 읽기 권한 또는 속성이 없으면 해당 정보는 미확인으로 표시합니다. Pleos 에뮬레이터 사용자 10에서 배터리 약 100%, 케이블 연결 안 됨, 목표 충전량 도달을 확인했습니다. 충전 중으로 바뀌는 경우와 충전 오류는 미검증입니다. [Android 차량 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds)
## 동승석 전후 조정 재시험

Virtual Console 세션을 켜고 조수석과 뒷좌석 좌우의 전후 위치를 각각 40으로 설정했습니다. D, 10 km/h에서 `passenger-drive`를 실행하자 세 좌석의 요청이 모두 `50001`로 실패했고, 콘솔에서도 세 위치가 모두 40으로 유지됐습니다. 시험 후 P, 0 km/h와 원래 좌석 위치로 복원했습니다.

실행 중인 Virtual Console의 `/vehicle/catalog`에는 UI에서 숨겨진 `seat-fore-aft-position` 항목이 있습니다. 에뮬레이터에서 `PUT /vehicle/seat?kind=position&control=slide&area=passenger&value=40`으로 조수석 위치를 준비하고 P, 0 km/h에서 자비스의 앞으로 조정을 실행했습니다. 콘솔의 가상 위치는 40에서 55로 바뀌었지만, 같은 요청의 Pleos SDK 조회는 0에서 0으로 유지됐습니다. 시험 후 콘솔 위치를 0으로 복원했습니다. 가상 좌석 이동은 관측했으며, 저장 위치의 적용과 복귀를 확인하려면 앱에서 변화한 위치를 읽을 수 있어야 합니다.

차량용 휴식 설정의 저장 목록도 재시작 전후에 비교했습니다. Vehicle Debug 앱을 강제 종료하고 다시 실행한 뒤 기존 `나의 휴식` 설정과 11개 항목 표시가 그대로 돌아왔습니다.

같은 APK의 뒷좌석 왼쪽과 오른쪽도 P, 0 km/h에서 시험했습니다. 좌석을 바꾸면 이전 결과가 지워졌고, 각 요청은 선택한 좌석으로 전달됐습니다. 두 좌석 모두 이동과 정지 응답 뒤 위치가 0에서 0으로 조회됐으며 해당 좌석의 위치 변화 없음이 표시됐습니다.

좌석 위치와 차량 상태를 확인한 뒤 이동하도록 바꾼 Vehicle Debug APK로 재시험했습니다. Pleos 에뮬레이터를 재시작해 APK를 설치하고 P, 0 km/h 상태에서 조수석 앞으로 조정을 눌렀습니다. 이동과 정지 응답 이후 위치가 0에서 0으로 조회됐고, 화면에 위치 변화 없음이 표시됐습니다.

휴대폰 에뮬레이터의 Demo APK에서는 뒷좌석 왼쪽을 50에서 55로 조정한 후 휴식 설정 페이지를 다녀왔습니다. 동승석으로 돌아왔을 때 선택된 좌석과 값 55가 유지됐고, 조수석은 50으로 유지됐습니다. 이 시험은 좌석별 모의 위치와 페이지 상태를 검증합니다.

Pleos 공식 지역별 정책에서 중앙 인포테인먼트의 Seat 기능은 북미, 유럽 및 기타 지역 모두 주행 중 제한 항목입니다. Distraction Optimization을 선언하더라도 좌석 항목 자체의 제한이 해제된다는 근거는 없습니다. 별도 동승자 디스플레이에서 좌석 기능이 허용되는 공개 배포 절차는 이번 문서 조사에서 확인하지 못했습니다. [Pleos 지역별 주행 제한 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)

D 기어 시험의 시스템 로그를 다시 확인했습니다. Pleos 홈의 `TaskViewContainer`는 D 전환 직후 자비스 MainActivity의 전체 화면 가용성을 `false`로, P 복귀 후 `true`로 기록했습니다. 같은 구간에서 주행 UX 제한도 활성화됐다가 해제됐습니다. 별도의 앱 설치 출처 경고도 기록됐지만, 그 경고만으로 표시 제한의 원인을 확정할 수는 없습니다. 기존 화면에서 D 상태의 새 좌석 SDK 요청을 입증하지 못했습니다. 주행 중 사용할 화면에는 별도 Activity와 Pleos의 주행 UX 검증이 필요합니다. [Android Automotive 주행 중 앱 표시 가이드](https://source.android.com/docs/automotive/driver_distraction/guidelines?hl=en)

좌석 선택 시 이전 결과를 지우고, 에뮬레이터 디버그 화면의 새 요청과 응답을 `PassengerSeat` 태그로 기록하도록 변경했습니다. 최신 APK로 D 상태 재시험을 시도했으나 당시 화면에서 뒤로 버튼이 확인되지 않아 새 명령을 보내지 못했습니다. 가상 기어는 P로 복원했습니다.

Virtual Console에서 D로 바꿨을 때 앱 로그에 `gear=DRIVE, speedMps=0.0`이 기록됐습니다. 이 상태에서 조정 버튼을 누르는 시험을 시도했으나 새 SDK 요청과 이전 화면 결과를 구분할 수 있는 증거를 확보하지 못했습니다. 시험 후 가상 기어를 P로 복원했습니다.

최신 APK에서 같은 좌석 세 자리를 반대 방향으로도 시험했습니다. Virtual Console 세션을 활성화한 상태에서 자비스의 뒤로 버튼을 각각 눌렀으며, 조수석과 뒷좌석 좌우 모두 이동·정지 응답 후 위치가 `0 → 0`으로 조회됐습니다. 어느 방향에서도 위치 변화가 관측되지 않았으므로 이 결과를 좌석 이동 성공으로 판단할 수 없습니다.

공식 Virtual Console 가이드의 좌석 관련 카드에는 착석 여부와 열선·통풍이 있으나 전후 위치 설정 항목은 확인되지 않았습니다. 이 카드 목록으로는 좌석을 중간 위치에 두고 다시 이동하는 시험을 구성할 수 없습니다. 로컬 콘솔 카탈로그 재조회도 두 포트에서 응답 시간 초과가 발생했습니다. `0 → 0`만으로 초기 위치가 이동 한계였는지, 에뮬레이터가 전후 이동을 반영하지 않았는지 구분할 수 없습니다. [Pleos Virtual Console 가이드](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)

Pleos Virtual Console 세션을 활성화하고 자비스에서 조수석과 뒷좌석 좌우의 앞으로 조정을 요청했습니다. 앱은 이동 및 정지 응답을 받은 뒤 위치를 다시 읽었습니다. 이 시험에서 위치 변화는 확인하지 못했습니다.
## 차량 알람 예약 시각 표시

최신 Vehicle Debug APK로 1분 차량 알람을 예약한 뒤 화면을 껐습니다. 예약 시각에 재생 서비스가 활성화됐고, 시스템 전원 상태가 `Asleep`인 동안 `USAGE_ALARM` 플레이어가 시작 상태로 출력 장치 17에 연결됐습니다. 시험 후 화면과 휴대폰 알람 옵션을 원래 상태로 돌렸습니다.

예약 알람의 소리를 직접 재생하는 서비스를 추가했습니다. Pleos에서 1분 알람을 예약하고 홈 키를 누른 뒤, 예약 시각에 재생 서비스 활성화와 `USAGE_ALARM` 플레이어 시작 및 출력 장치 17 할당을 확인했습니다. 플레이어는 약 30초 후 해제됐고 서비스 목록에서도 제거됐습니다. 외부 스피커의 음향은 확인하지 않았습니다. [Android 정확한 알람과 서비스 시작](https://developer.android.com/develop/background-work/services/alarms), [알람 서비스 유형](https://developer.android.com/about/versions/14/changes/fgs-types-required)

별도 1분 알람에서 Pleos 알림 패널의 알람 끄기를 눌렀습니다. Android 오디오 기록에서 플레이어 3983은 07:38:00.891에 시작해 07:38:07.782에 해제됐고, 앱의 재생 서비스도 즉시 목록에서 제거됐습니다.

Pleos에서 1분 알람을 예약했습니다. 예약 만료 시 알림 ID 701이 게시됐고, 알림 채널에 기본 알람 소리와 `USAGE_ALARM`이 설정되어 있었습니다. 같은 시점에 Android 오디오 진단의 플레이어 시작 이벤트는 확인되지 않았습니다. 비교 시험에서 자비스의 알람 소리 미리 듣기는 `USAGE_ALARM` 플레이어의 시작과 출력 장치 할당을 기록했습니다. 예약 알람의 소리 재생은 검증이 남아 있습니다.

차량 알람 화면의 예약 시각 표시를 Pleos 에뮬레이터에서 확인했습니다. 20분 알람 예약 후 휴식 설정 페이지를 다녀와도 같은 시각이 표시됐고, 시험 알람을 취소하자 표시가 사라졌습니다.

## 휴대폰 지정 시각 알람

차량과 휴대폰 옵션을 함께 켜고 1분 타이머를 예약했습니다. 같은 예약 시각인 차량 00:01 PST와 휴대폰 08:01 GMT에 Pleos 자비스와 Google 시계가 모두 `USAGE_ALARM` 오디오 플레이어를 시작했습니다. Android 오디오 로그의 시작 시각은 각각 08:01:00.914와 08:01:03.181이고, 두 쪽 모두 출력 장치 할당이 기록됐습니다. 차량은 자동 종료됐고 휴대폰은 알림에서 중지했습니다. 이 결과는 에뮬레이터 시스템의 재생 로그를 기준으로 합니다.

차량과 휴대폰 옵션을 모두 켜면 한 번의 확인으로 차량 알람을 예약하고 같은 시점의 알람 요청을 블루투스로 보냅니다. Pleos와 Android 15 에뮬레이터에서 20분으로 시험해 차량 오후 11:48 예약과 휴대폰 오전 7:48 활성 예약을 확인했습니다. 두 기기의 시간대는 8시간 차이였습니다. 휴대폰에서는 수신 후 기본 시계 등록을 직접 확인했으며, 시험 알람은 양쪽에서 제거했습니다. 두 기기의 실제 소리는 이 시험에서 확인하지 않았습니다.

최신 Vehicle·Demo APK로 20분 타이머 전송도 확인했습니다. 차량 확인창의 22:50은 GMT 휴대폰에 06:50으로 전달됐습니다. 기본 시계의 06:50 알람이 활성화된 것을 확인하고 시험 알람을 껐습니다.

Pleos와 Android 휴대폰 에뮬레이터에서 최신 APK를 실행했습니다. 차량 현지 시각 22시대에 다음 날 06:17을 선택해 페어링된 휴대폰으로 전송했습니다. 차량의 시간대는 America/Los_Angeles이고 휴대폰은 GMT여서 휴대폰에서 같은 시점인 14:17로 표시됐습니다. 휴대폰에서 시계 앱에 알람 설정을 누른 뒤 기본 시계의 활성 알람 목록에 2:17 PM이 추가된 것을 확인했습니다.
