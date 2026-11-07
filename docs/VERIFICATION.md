# 초기 프로젝트 검증 기록

검증일: 2026-11-07 (Asia/Seoul)

## 환경

- Windows, Eclipse Adoptium JDK 21.0.8+9
- 저장소: `F:\Projects\pleos`
- Android SDK: `F:\Projects\.japeace-toolchain\android-sdk`
- Gradle 캐시: `F:\Projects\.japeace-toolchain\gradle-home`
- Gradle 8.12 / AGP 8.9.2 / Kotlin 2.2.21
- SDK Platform 35 / Build Tools 35.0.0
- Pleos Vehicle SDK 2.3.2 의존성 다운로드 및 차량 APK 패키징 확인

## 실행과 결과

```powershell
.\gradlew.bat :app:assembleDemoDebug :app:assembleVehicleDebug `
  :app:testDemoDebugUnitTest :app:testVehicleDebugUnitTest `
  :app:lintDemoDebug :app:lintVehicleDebug
```

캐시·SDK·임시 파일은 F: 경로로 지정하고 실행했습니다.

| 항목 | 결과 |
| --- | --- |
| 데모 debug APK | 생성 성공 |
| 차량 debug APK | 생성 성공 |
| 주차 정책 단위 테스트 · demo | 5개, 실패 0, 오류 0 |
| 주차 정책 단위 테스트 · vehicle | 5개, 실패 0, 오류 0 |
| Android Lint · 두 debug 변형 | 오류 없이 통과. 사용 버전·targetSdk의 업데이트 권고 경고는 남음 |
| XML·버전 카탈로그 TOML | 파싱 성공 |
| 차량 APK 내 FakeVehicleGateway | 포함되지 않음. 데모 APK에만 포함됨 |
| 차량 APK Manifest | applicationId·minSdk 28·targetSdk 34·automotive 필수 확인 |

Gradle 결과: `BUILD SUCCESSFUL`, 101개 작업 실행.

초기 Lint에서 지적한 데이터 이전 설정을 보완해 Android 12 이상에서는 클라우드 백업·기기 간 전송 제외 규칙을 명시했습니다. Manifest·리소스 변경 후 두 APK 빌드와 Lint를 재실행해 성공을 확인했습니다(91개 작업, 44개 실행·47개 최신 상태). 주차 정책 코드는 변경하지 않았습니다.

테스트는 정상 P·정지, 연결 해제, 유효하지 않거나 누락된 값, NaN·무한대, 정지 상태의 D/R/N, P이지만 속도가 0이 아닌 경우를 검사합니다. 실제 차량 제어 요청을 보내는 통합 테스트는 아닙니다.

첫 C: 빌드는 공간 부족으로 중단됐습니다. 저장소와 이 작업의 SDK·Gradle 캐시를 F:로 옮기고 재실행한 결과가 위 기록입니다.

## 미검증 범위

- APK의 실제 기기·에뮬레이터 설치·실행 및 화면 검증
- Pleos Vehicle Service 연결과 차량 속성 읽기·쓰기
- 좌석 이동·정지·저장 위치 재현 및 복귀
- 프리셋 영속 저장, 음성, 알람, 휴대폰 연동
- 서명 release APK 및 앱 마켓 심사 요건
- 클라우드 Pleos 에뮬레이터 실행

GitHub Actions에는 같은 빌드·테스트·Lint 명령을 구성했습니다. 이 로컬 기록을 원격 CI 실행 성공 기록으로 간주하지 않습니다.
