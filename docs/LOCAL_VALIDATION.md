# 로컬 Pleos 실행 검증

W04의 실제 설정 저장, 프로세스 재시작과 DB 일관성 검증은 [STORAGE_VALIDATION](STORAGE_VALIDATION.md)에 기록합니다.

W03 좌석 capability와 이동 시험은 [SDK_VALIDATION](SDK_VALIDATION.md)에 별도로 기록합니다. 실제 위치 변화와 저장 위치 재현은 아직 입증하지 못했습니다.

검증일: 2026-11-08, Asia/Seoul. W01 실행 환경과 첫 화면 확인 기록입니다.

## W01 결과

- Windows 11 Home, Intel i5-1135G7, RAM 약 16GB, Intel Iris Xe Graphics.
- Android Emulator 37.2.13.0, WHPX 진단: `installed and usable`.
- 공식 Pleos Connect v2.1.0 x86_64 이미지, Android 14 / API 34.
- 이미지 SHA1: `b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa`, 공식 배포 XML 값과 일치.
- 이미지 fingerprint: `Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0/release_connect_v2.1.0:user/test-keys`.
- Car API 34.0, Platform 34.1, CarBuiltin Platform minor 1.
- AVD `pleos-w01`, RAM 4096MB, 공식 기기 프로필 2560x1440, `-gpu host`, `-no-snapshot` Cold Boot.
- `sys.boot_completed=1`, ADB `emulator-5554`, 활성 사용자 10.
- `android.hardware.automotive.vehicle.IVehicle/default`, `car_service` 등록 확인.
- `adb forward tcp:8088 tcp:8088` 후 Virtual Console HTTP 200, 브라우저에서 `DDS ONLINE`, P와 0 km/h 표시 확인.

첫 검증에는 코드 커밋 `11b94f2886ff24e79b778c87b449dcd6827dd1a8`의 CI 재실행 APK를 사용했습니다.
[Actions attempt 2](https://github.com/SeungminShin97/pleos/actions/runs/42371489410/attempts/2)의 두 APK와 테스트 보고서를 내려받았습니다. 체크아웃 `b642b6060c5189db0f29ef73f792c4421a25c3f1`과의 차이는 문서뿐입니다.

| 검증 | 결과 |
| --- | --- |
| 차량 APK 설치와 첫 화면 | 사용자 10에 설치, 미연결 안내와 비활성 제어 버튼 확인 |
| 데모 APK 설치와 첫 화면 | P, 0 km/h, 모의 데이터 안내 확인 |
| 데모 D 버튼 | D, 36 km/h, P 상태에서만 적용 가능하다는 안내 확인 |
| 차량 SDK 실제 읽기와 쓰기 | W01의 초기 APK에는 미구현, 검증하지 않음 |

최초 앱 실행의 `am start -W`는 timeout을 반환했고 캡처가 검었습니다. 부팅 완료 후 다시 확인한 결과 Activity가 정상 실행되고 홈 화면이 표시됐습니다. 설치 시 AAOS 사용자 0과 활성 사용자 10을 구분해야 했습니다. 화면 전환 초기에는 앱이 이전 위치에 남거나 검은 영역이 보였고, 이후 데모 캡처에서는 Pleos 전체 셸과 앱이 정상 표시됐습니다. GPU 공식 최소 사양 미달 환경이므로 이 결과로 모든 그래픽 기능과 성능을 보장하지 않습니다.

CPU 조회의 `VirtualizationFirmwareEnabled=False`만으로 실행 불가라고 판단하지 않습니다. 이 PC에서는 HypervisorPresent와 에뮬레이터의 WHPX 실진단 및 실제 부팅으로 가속 사용을 확인했습니다.

## 로컬 경로와 재현

F: 드라이브는 이 실행 환경에 없어 이번 사용자 요청에 따른 로컬 검증 파일은 C: 작업 공간에 두었습니다. 기존 문서의 F: 환경을 삭제하거나 변경하지 않았습니다.

- 저장소: `C:\resume\pleos`
- SDK: `C:\resume\.tools\pleos-sdk`
- Android 설정: `C:\resume\.tools\pleos-android-home`
- AVD: `C:\resume\.tools\pleos-avd\pleos-w01.avd`
- W01 캡처와 로그: `C:\resume\.tools\pleos-w01-evidence`

```powershell
$env:ANDROID_HOME = 'C:\resume\.tools\pleos-sdk'
$env:ANDROID_USER_HOME = 'C:\resume\.tools\pleos-android-home'
$env:ANDROID_AVD_HOME = 'C:\resume\.tools\pleos-avd'
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd pleos-w01 -no-snapshot -gpu host -memory 4096
& "$env:ANDROID_HOME\platform-tools\adb.exe" -s emulator-5554 forward tcp:8088 tcp:8088
# 별도 프로그램 형태의 콘솔 창
& 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe' --app=http://127.0.0.1:8088/
```

이미 같은 AVD가 실행 중이면 중복 실행하지 않습니다. 포트 포워딩은 로컬 테스트용입니다. 원격으로 공개하지 않았습니다.

공식 근거: [에뮬레이터 설치](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [GPU와 Cold Boot 설정](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator), [Virtual Console 연결](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator).

## W02 구현과 검증

이 절은 W01 이후의 로컬 변경 결과입니다. GitHub에 push하거나 이번 변경의 CI를 실행한 것은 아닙니다.

### 구현과 변경 이유

- `app/src/vehicle/java/io/github/seungminshin97/japeace/integration/vehicle/PleosVehicleGateway.kt`: 항상 미연결이던 placeholder를 AAOS `Car` 및 `CarPropertyManager` 읽기 전용 adapter로 변경했습니다. 속성과 IPC는 별도 HandlerThread에서 처리합니다. 기어는 `GEAR_SELECTION`(289408000), 속도는 `PERF_VEHICLE_SPEED`(291504647), area 0, 속도 단위 m/s를 사용합니다. 현재 변속단인 `CURRENT_GEAR` 대신 사용자가 선택한 기어를 사용합니다.
- 이벤트의 `STATUS_AVAILABLE`을 확인하고, 1초 간격으로 두 속성을 다시 조회합니다. 변경 이벤트가 없다는 이유만으로 기어 값을 오래된 것으로 판정하지 않으며, 반대로 과거 이벤트만으로 현재 조회 성공을 보장하지 않습니다. 이 주기는 설정값이며 실측 처리시간이나 성능 수치가 아닙니다.
- `domain/vehicle/VehicleReadings.kt`: 속성별 VHAL 타임스탬프로 순서가 뒤집힌 이벤트를 제외하고, 미지원 기어와 NaN 및 무한대 속도를 무효화합니다. 오류 시 값을 지우고 재조회로 회복합니다.
- `VehicleState.kt`: 연결 중, 권한 필요, 연결 해제, 값 미확인, 미지원 및 조회 실패 원인을 구분합니다. `ParkingPolicy`의 P와 정상 속도 0 조건은 유지합니다.
- 앱이 화면에서 내려가면 구독과 연결을 해제합니다. 새 연결은 빈 관측 상태에서 시작하며, 연결 세대와 구독 세대가 다른 콜백은 반영하지 않습니다. 서비스 끊김 시 이전 값을 즉시 지우고, 활성 세대에서만 지연 재연결을 시도합니다.
- `MainActivity.kt`, `HomeViewModel.kt`, 빌드별 `DependencyFactory.kt`: application context로 ViewModel 수명에 맞는 의존성을 구성하고 시작, 정지와 해제를 연결했습니다. 회전 등 Activity 재생성이 기존 ViewModel의 adapter와 어긋나지 않도록 factory는 지연 생성합니다.
- `app/src/vehicle/AndroidManifest.xml`: `CAR_SPEED`는 런타임 dangerous 권한, `CAR_POWERTRAIN`은 normal 권한으로 선언합니다. 차량 속도 권한은 처음에 요청하고 거절 시 설정에서 허용하도록 안내합니다. 데모에는 차량 권한과 adapter를 넣지 않습니다.
- `app/build.gradle.kts`: SDK 35의 `optional/android.car.jar`는 vehicle의 compileOnly에만 추가합니다. APK에 Android Car 구현을 복사하지 않습니다. Pleos SDK 2.3.2 의존성은 유지하며 이번 단계에서는 좌석 API를 호출하지 않습니다.
- Pleos v2.1.0은 `android.car-module.jar`를 bootclasspath로 제공합니다. `android.car` 공유 라이브러리를 필수로 요구하면 `INSTALL_FAILED_MISSING_SHARED_LIBRARY`가 발생해 optional로 선언했습니다. 이미지의 bootclasspath와 설치 성공으로 확인한 호환성 변경입니다. API 29 미만의 차량 빌드는 연결을 시도하지 않고 미지원으로 처리하며, 실제 실행 검증 대상은 API 34입니다.
- `presentation/HomeScreen.kt`: 실제 AAOS 조회와 모의 데이터를 구분하고 연결 및 권한 오류를 표시합니다. 저장, 적용과 복귀 버튼은 계속 비활성입니다.

### 검증 결과

| 항목 | 결과와 근거 |
| --- | --- |
| 두 debug APK 빌드 | 성공, JDK 21, Gradle 8.12, SDK 35, Build Tools 35.0.0 |
| 단위 테스트 | demo 10개, vehicle 10개, 총 20개 실패 0, 오류 0. 각 변형에 기존 정책 5개와 신규 관측 상태 회귀 테스트 5개 |
| Android Lint | 두 변형 오류 없이 통과, 기존 버전 및 SDK 업데이트 권고 경고는 남음 |
| 권한 거절 | 테스트 기기의 속도 권한을 거절 상태로 지정해 실행. 권한 필요 안내, 기어와 속도 미확인, 조건 차단 확인 |
| 권한 허용 | 테스트 기기 사용자 10에 속도 권한을 허용한 뒤 재실행, 실제 AAOS P와 0 km/h 확인 |
| D, 정지 | Virtual Console에서 D로 전환, 앱 D/0과 P 조건 차단 확인 |
| 실제 속도 변경 | Console 가속 후 디버그 로그에서 `gear=DRIVE`, `speedMps=2.777778`, `5.277778`과 감속 후 `0.0` 등 실제 이벤트 수신 확인 |
| 시스템 주행 제한 | Pleos가 앱 위에 주행 중 사용 불가 화면을 표시하는 캡처 확인. 이를 우회하도록 앱을 distraction optimized로 등록하지 않음 |
| P 복원 | 브레이크와 P 전환 후 앱에서 새 P와 정지 상태 확인 |
| 서비스 끊김 | 로컬 에뮬레이터의 `com.android.car` 사용자 0 프로세스를 `am crash`로 재시작. READ_FAILED 또는 DISCONNECTED, 기어와 속도 null, valid false 로그 확인 |
| 재연결 | 재연결 보완 후 같은 앱 PID에서 DISCONNECTED, DATA_UNAVAILABLE, 정상 P/0 순서의 로그 확인 |
| 오류와 지연 이벤트 | 단위 테스트로 속성 무효화, 늦은 P/0 이벤트가 최신 D/주행 상태를 덮어쓰지 않음, 미지원 기어 및 비정상 속도, 새 연결에 이전 값을 사용하지 않음을 검증 |

UI 덤프는 움직이는 화면이 idle 상태가 되기를 기다리거나 실패할 수 있어 단독 속도 검증 수단으로 사용하지 않았습니다. 짧은 가속 후 덤프를 기다리는 동안 감속해 0이 된 결과와 실제 이벤트 수신을 구분했습니다. `VehicleReadings` 로그는 debuggable 빌드에서 값 변경 시에만 남깁니다. release에는 해당 값 로그를 남기지 않습니다.

서비스 재시작 초기 시도에서는 재연결이 늦어져 활성 연결 세대에서만 재시도하도록 보완했습니다. 최종 로그는 동일 PID에서 값이 사라지고 새 조회 이후 회복되는 것을 보여 줍니다. 수신 단절이나 실제 차량 물리 동작 전체를 보장하는 검증은 아닙니다.

검증 파일은 `C:\resume\.tools\pleos-w01-evidence`에 있습니다. `w02-complete-build.log`, `w02-readings.log`, `w02-service-restart-final.log`, `w02-denied.xml`, `w02-drive.xml`, `w02-final.png`, `w02-moving.png`를 확인합니다. 단위 테스트 XML과 Lint는 저장소의 `app/build/test-results` 및 `app/build/reports`에 생성됩니다.

### 미검증 및 다음 단계

실제 차량, API 34 이외의 AAOS 이미지, 물리적인 서비스 오류나 VHAL unavailable 이벤트의 실차 재현, 좌석 조회 및 제어, 쓰기 전 실행 조건 재검사와 안정화 정책은 미검증입니다. 이번 동작을 좌석 제어 허가로 사용하지 않습니다. 다음 단계 W03에서 공식 좌석 capability, 영역, 단위, 이동 및 중단 API를 검증합니다.

API 근거: [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds), [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager), [Car 수명주기](https://developer.android.com/reference/android/car/Car).
