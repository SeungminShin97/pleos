# 개발 환경과 실행

문서 시작점은 [docs/README](README.md), 사용자 의도·결정은 [CONTEXT](CONTEXT.md), 상세 설계는 [DESIGN](DESIGN.md), 다음 작업은 [ROADMAP](ROADMAP.md)에 있습니다.

## 프로젝트 범위

Kotlin + Jetpack Compose 기반의 초기 Android 앱입니다. 현재는 홈 화면, 차량 상태 인터페이스, 주차 조건 판정, 모의 상태 전환만 있습니다. 프리셋 저장·적용·복귀, 음성, 알람은 아직 구현하지 않았습니다. 장소 검색과 내비게이션은 후속 범위입니다.

| 빌드 | 용도 | 차량 데이터·제어 |
| --- | --- | --- |
| `demoDebug` | 일반 Android 기기·에뮬레이터에서 화면 개발 | 데모 소스에만 포함된 Fake 상태. 실제 차량 연결·조작 없음 |
| `vehicleDebug` | Pleos/AAOS 연동 개발의 출발점 | 공식 Vehicle SDK 의존성 포함. 실제 adapter는 아직 미구현이며 항상 연결 안 됨 |

`demoDebug`에서 P·정지 / D·주행 버튼을 눌러 주차 조건 메시지를 확인할 수 있습니다. 조건이 충족돼도 아직 프리셋 기능은 활성화되지 않습니다. 차량 빌드에는 Fake 구현과 모의 상태 전환 버튼이 포함되지 않습니다.

## 고정한 버전

- Gradle Wrapper 8.12, Android Gradle Plugin 8.9.2
- Kotlin / Compose compiler plugin 2.2.21
- Compose BOM 2025.04.01
- compileSdk 35, targetSdk 34, minSdk 28, Build Tools 35.0.0
- Java/Kotlin 바이트코드 대상 17. JDK 17 권장, 로컬 빌드 환경은 검증 결과 문서 참고
- Pleos Vehicle SDK 2.3.2: `vehicleImplementation`에만 선언

targetSdk 34는 Pleos 개발 이미지 API 34를 기준으로 선택했습니다. 최종 앱 등록 요구 사항은 배포 전에 다시 확인합니다. 빌드 성공이 Pleos 서비스·차량 제어의 실행 검증을 뜻하지는 않습니다.

## Android Studio

이번 작업의 로컬 배치는 저장소 `F:\Projects\pleos`, SDK `F:\Projects\.japeace-toolchain\android-sdk`, Gradle 캐시 `F:\Projects\.japeace-toolchain\gradle-home`입니다. SDK 경로는 Git에서 제외한 `local.properties`에 지정했습니다. Android Studio에서도 Gradle user home을 위 F: 경로로 설정하면 C:에 새 캐시가 쌓이는 것을 줄일 수 있습니다. 이는 개인 PC 설정이며 저장소의 다른 사용자에게 강제하지 않습니다.

1. 저장소 루트의 `settings.gradle.kts`가 있는 폴더를 엽니다.
2. Gradle 8.12 / AGP 8.9.2를 지원하는 Android Studio와 JDK 17 이상을 사용합니다.
3. SDK Manager에서 Android SDK Platform 35와 Build Tools 35.0.0을 설치합니다.
4. SDK 경로는 Android Studio가 만드는 `local.properties` 또는 `ANDROID_HOME`으로 지정합니다. 이 파일은 Git에서 제외합니다.
5. Build Variants에서 `demoDebug`를 선택하고 일반 API 28 이상 Android 에뮬레이터나 기기에 실행합니다.
6. Pleos 통합 개발은 공식 이미지에서 `vehicleDebug`를 사용합니다. 현재 빌드는 차량 조작을 하지 않습니다.

Windows `local.properties` 예시:

```properties
sdk.dir=C:/Users/사용자명/AppData/Local/Android/Sdk
```

## CLI

Windows PowerShell:

```powershell
# 이 PC에서 캐시·SDK를 F:에 둘 때 설정합니다.
$env:GRADLE_USER_HOME = 'F:\Projects\.japeace-toolchain\gradle-home'
$env:ANDROID_HOME = 'F:\Projects\.japeace-toolchain\android-sdk'
.\gradlew.bat :app:assembleDemoDebug :app:assembleVehicleDebug
.\gradlew.bat :app:testDemoDebugUnitTest :app:testVehicleDebugUnitTest
.\gradlew.bat :app:lintDemoDebug :app:lintVehicleDebug
```

macOS / Linux:

```bash
./gradlew :app:assembleDemoDebug :app:assembleVehicleDebug
./gradlew :app:testDemoDebugUnitTest :app:testVehicleDebugUnitTest
./gradlew :app:lintDemoDebug :app:lintVehicleDebug
```

설치할 기기 또는 에뮬레이터가 연결되어 있으면 `:app:installDemoDebug` 또는 `:app:installVehicleDebug`를 사용합니다. 여러 기기가 연결된 경우 대상 기기를 먼저 지정합니다. 차량 빌드는 automotive 기기가 필요합니다.

로컬 빌드 결과:

- `app/build/outputs/apk/demo/debug/app-demo-debug.apk`
- `app/build/outputs/apk/vehicle/debug/app-vehicle-debug.apk`
- `app/build/reports/tests/`: 단위 테스트 HTML
- `app/build/reports/lint-results-*Debug.html`: Android Lint

초기 로컬 빌드·테스트의 결과와 미검증 범위는 [VERIFICATION.md](VERIFICATION.md)에 기록합니다.

위 APK는 개발용 debug 빌드입니다. 공모전 제출용 서명 release APK는 아직 없습니다. 빌드 출력과 SDK 바이너리는 소스 저장소에 커밋하지 않습니다. GitHub Actions는 빌드 후 debug APK와 보고서를 실행 결과의 artifact로 보관하도록 구성합니다.

## Pleos 연결의 다음 작업

1. 공식 SDK의 실제 시그니처·권한과 이미지의 Vehicle Service 버전을 기록합니다.
2. AAOS 표준 속도·기어 속성을 읽고 정상·오류·연결 해제 상태를 매핑합니다.
3. Vehicle SDK 연결과 Seat capability 조회를 구현합니다.
4. 좌석 위치 조회 → 이동 → 중단 → 재조회가 되는지 Virtual Console로 검증합니다.
5. 저장 위치 재현 방식이 확인되면 제어 인터페이스와 적용·복귀 유스케이스를 추가합니다.
6. 주행 제한과 수명주기·권한 처리를 연결한 뒤 차량 제어 UI를 활성화합니다.

현재 `ParkingPolicy`는 정상 데이터의 P·속도 0 여부만 검사합니다. 실제 adapter에서 데이터 유효성·재조회·연결 세대를 확인하고, 실행 시 재검사·capability·사용자 확인·중단 처리를 추가해야 합니다. 현재 판정만으로 좌석 제어를 허용하지 않습니다.

공식 의존성 설정: [Pleos Vehicle SDK 가져오기](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
