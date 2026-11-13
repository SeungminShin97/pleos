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
- `app/src/androidTestVehicle/java/io/github/seungminshin97/japeace/validation/SeatValidationInstrumentation.kt`: 별도 instrumentation APK의 검증 코드. 기본 동작은 읽기 전용 inventory입니다. 좌석 명령은 명시적인 `operation=motion`에서만 호출합니다.
- `app/src/vehicleDebug/AndroidManifest.xml`: 테스트 대상 debug 앱에만 `pleos.car.permission.CAR_SEATS`, `pleos.car.permission.CONTROL_CAR_SEATS`를 선언합니다. 이미지에서 두 권한 모두 protectionLevel normal인 것을 확인했습니다.
- `app/build.gradle.kts`: vehicle 테스트 runner와 SDK Car API용 androidTest compileOnly 경로를 지정합니다. 라이브러리 구현을 복사하거나 임의의 SDK setter를 추가하지 않았습니다.

검증 APK는 알려진 `Pleos/crp_avd/` fingerprint, `crp_avd_x86_64` device, debuggable 앱에서만 실행됩니다. 이동 전에는 AAOS의 정상 P와 유한한 속도 0을 직접 재조회합니다. 명령 후에는 성공 콜백과 실제 위치 재조회 결과를 별도로 기록하며, 이동 호출의 예외나 timeout 경로에서도 STOP을 시도합니다. 이 제한된 실험 코드는 제품용 제어기나 실차 안전 검증을 대신하지 않습니다.

## 확인한 공개 API

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

## 이동과 중단 시험

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
