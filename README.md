# 자비스 (자.peace)

**주차한 내 차에서 쉴 때, 저장해 둔 나만의 휴식 환경을 준비하고 이전 설정으로 돌아오도록 돕는 Pleos Connect 앱.**

장거리 운전 중 졸음쉼터·휴게소에서 짧게 쉬는 사람과 점심시간에 자차에서 혼자 쉬는 직장인을 위한 앱입니다. 사용자가 직접 맞춘 자세·환경을 기억하는 것이 핵심입니다.

> **현재 상태: 차량 상태 읽기와 운전석 관측 설정 저장**
>
> 홈, 주차 조건 판정, 데모 상태 전환, AAOS 기어와 속도 읽기 및 운전석 관측 설정의 로컬 저장을 구현했습니다. Room 세션 기록과 재시작 복구 기반도 추가했습니다. 실제 좌석 이동과 자동 재현은 미검증이며 적용과 복귀, 음성 및 알람은 아직 구현하지 않았습니다. [W04 저장 검증](docs/STORAGE_VALIDATION.md)

## 개발 문서

**[docs/README.md에서 시작하세요.](docs/README.md)** 원래 대화 없이도 개발을 이어갈 수 있도록 제품 의도, 요구사항·변경 결정, 상세 설계, 구현 상태와 다음 작업을 모두 docs에 정리했습니다.

| 문서 | 내용 |
| --- | --- |
| [대화 맥락·요구사항](docs/CONTEXT.md) | 사용자가 직접 요청한 내용, 현재 설계 선택, 미확정 항목 |
| [상세 설계](docs/DESIGN.md) | 화면·차량 제어·음성·알람·데이터·오류 처리·수용 기준 |
| [개발 환경](docs/DEVELOPMENT.md) | 도구 버전, SDK·캐시 경로, 실행 방법 |
| [검증 기록](docs/VERIFICATION.md) | 실제 검증 결과와 미검증 범위 |
| [개발 인수인계·다음 작업](docs/ROADMAP.md) | 현재 코드 지도·개발 순서·API 검증 질문·완료 조건 |
| [시장·공모전·기술 근거](docs/REFERENCES.md) | 통계·기사·제출 안내·제공 참고 목록·공식 SDK 출처 |

## 빌드·실행

Android Studio에서 저장소 루트를 열고 SDK Platform 35·Build Tools 35.0.0, Gradle 8.12·AGP 8.9.2를 지원하는 환경과 JDK 17 이상을 준비합니다.

```bash
git clone https://github.com/SeungminShin97/pleos.git
cd pleos
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDemoDebug :app:assembleVehicleDebug
.\gradlew.bat :app:testDemoDebugUnitTest :app:testVehicleDebugUnitTest
.\gradlew.bat :app:lintDemoDebug :app:lintVehicleDebug
```

macOS/Linux에서는 `./gradlew`를 사용합니다. SDK 지정과 F: 캐시 설정은 [DEVELOPMENT](docs/DEVELOPMENT.md)를 확인합니다.

| 변형 | 실행 환경·동작 |
| --- | --- |
| `demoDebug` | 일반 API 28 이상 Android 기기·에뮬레이터. P·정지/D·주행 모의 상태 전환. 실제 차량 제어 없음 |
| `vehicleDebug` | Pleos/AAOS용. AAOS 기어와 속도 읽기, 권한 및 연결 오류 처리. 차량 조작은 비활성 |

APK는 빌드 후 아래 로컬 경로에 생성됩니다.

- `app/build/outputs/apk/demo/debug/app-demo-debug.apk`
- `app/build/outputs/apk/vehicle/debug/app-vehicle-debug.apk`

공모전 제출용 서명 release APK는 아직 없습니다. [GitHub Actions](https://github.com/SeungminShin97/pleos/actions)에서 생성한 debug APK·보고서는 실행 결과의 artifact로 보관합니다.

## 확인된 검증과 다음 단계

W04에서 실제 운전석 11개 관측값 저장과 앱 재시작 후 유지를 확인했습니다. 두 변형의 DB 시나리오 각 8개와 별도 프로세스 종료 복구를 검증했습니다. 단위 테스트 20개와 두 변형 빌드 및 Lint도 통과했습니다. 자동 적용과 복귀는 보류합니다. [저장 및 복구 검증 기록](docs/STORAGE_VALIDATION.md)

W03에서 좌석 11개 축의 capability와 30개 위치값을 조회했습니다. 이동과 STOP 콜백은 왔지만 현재 이미지에서 위치 변화가 관측되지 않아 자동 좌석 적용과 복귀는 계속 보류합니다. [좌석 SDK 검증 기록](docs/SDK_VALIDATION.md)

기존 코드의 두 APK 빌드, 테스트와 Lint는 [Linux CI 재실행](https://github.com/SeungminShin97/pleos/actions/runs/42371489410/attempts/2)에서도 통과했습니다. 이후 로컬 Pleos v2.1.0에서 두 APK 설치와 홈 화면, 읽기 전용 AAOS 연동을 검증했습니다. 이번 변경을 CI에 올려 실행한 것은 아닙니다. 상세 결과는 [LOCAL_VALIDATION](docs/LOCAL_VALIDATION.md)을 확인합니다.

다음 단계는 **좌석 위치 조회, 이동, 중단과 저장 위치 재현 방식** 검증입니다. 단계별 완료 조건은 [ROADMAP](docs/ROADMAP.md)에 있습니다.

초기 로컬 환경은 `F:\Projects\pleos`입니다. 이번 로컬 검증은 F:가 없는 환경의 `C:\resume\pleos`에서 진행했습니다. SDK와 캐시 경로는 [LOCAL_VALIDATION](docs/LOCAL_VALIDATION.md)을 확인합니다.
