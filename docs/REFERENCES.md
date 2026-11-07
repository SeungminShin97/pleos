# 시장 조사·공모전·기술 참고 자료

정리일: 2026-11-08. 대화에서 사용한 근거와 제공받은 참고 목록을 보관합니다. 이 문서 작성 자체는 외부 자료를 새로 전수 검증한 작업이 아닙니다. 기존에 확인한 자료의 기준일과 사용자가 제공한 메모를 구분합니다.

## 시장 조사에 사용하기로 한 근거

| 자료 | 대화에서 확인·활용한 내용 | 사용할 때의 한계 |
| --- | --- | --- |
| [공공데이터포털: 한국도로공사 졸음쉼터 설치현황](https://www.data.go.kr/data/15043710/fileData.do) | 2026-08-01 기준 한국도로공사 관리 대상 253개소 | 전국 모든 고속도로·일반도로의 총수로 쓰지 않음. 장소 검색용 좌표·접근성 데이터가 완성됐다는 뜻도 아님 |
| [아주대학교 석사논문, 2024](https://dcoll.ajou.ac.kr/dcollection/srch/srchDetail/000000033973) | 조현제, 「졸음쉼터 이용률이 졸음 관련 교통사고에 미치는 영향에 관한 연구」. 2021~2022년 9개 노선·138개 쉼터 분석. 연구 보고값: 일평균 이용 차량 470대·이용률 2.01% | 보고된 평균과 표본 범위를 유지. 2.01%는 교통량 대비 진입 차량 비율이며 수면 이용률·점유율이 아님. 253×470 같은 전국 시장 추정에 사용하지 않음 |
| [서울경제, 2025-06-18: “점심시간엔 혼자 쉬고 싶어요”··· 차·영화관 ‘나만의 힐링공간’ 찾는 MZ직장인들](https://v.daum.net/v/20250618105251898?s=print_news) | 사용자가 이 기사와 졸음쉼터 자료를 현재 시장 조사 근거로 선택. 점심시간 개인 공간으로 차량을 쓰는 직장인 사례와 당시 차량 휴식 서비스 사례 | 정성 근거. 전체 직장인의 차량 수면 비율·현재 서비스 운영·앱 유료 수요를 입증하는 통계로 쓰지 않음 |
| [국토교통부: 2026 교통안전 연차보고서 안내](https://www.molit.go.kr/USR/policyData/m_34681/dtl.jsp?id=4909) | 대화의 보조 확인 자료. 보고서 본문 178쪽(PDF 192쪽)의 졸음쉼터 관련 자료 참고 | 설치 실적과 향후 계획 개소를 단순 합산하지 않음 |

사용자가 전달한 '점심시간에 방해받지 않으려고 자차에서 쉰다는 사람들 이야기를 들었다'는 내용은 제품 가설의 출발점입니다. 인터뷰·수요 통계로 검증된 결과로 표현하지 않습니다. 앱이 졸음 사고를 줄인다는 정량 효과도 아직 입증하지 않았습니다.

추가로 조사할 항목은 차에서 쉬는 빈도, 준비에 드는 시간, 반복 조정 항목, 알람 수단, 복귀 불편, 프리셋 재사용 의향입니다. 사용자 수·시장 규모·유료화는 아직 확정하지 않았습니다.

## 현재 개발 공모전과 제출

공식 진입점:

- [현대자동차그룹 차량용 앱 개발 공모전](https://appdevchallenge.auto-hmg.io/)
- [지원·제출 방법과 접수양식 다운로드](https://appdevchallenge.auto-hmg.io/guide/submission)

2026-11-07 공식 제출 안내를 확인한 기록으로는 접수양식 자체가 기획서이며, 전원 서명 동의가 필요합니다. 1차 접수는 2026-11-15까지 이메일 제출입니다. 영상·GitHub·APK는 선택 제출이며 2차 PT에서는 실제 동작 앱이 필요합니다. 제출 직전에 공식 안내를 다시 확인합니다.

제출 이메일: `appdevchallenge@hyundai-partners.com`

기본 제목 형식: `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`

GitHub 제출 시 소스와 실행 가능한 서명 release APK를 준비합니다. 공식 안내의 기본 위치·명명은 `apk/팀명_앱이름_release.apk`이며, 용량 초과 시 GitHub Release를 활용할 수 있습니다. 현재 생성한 debug APK를 제출용 release라고 부르지 않습니다. 참가자 서명·연락처·동의서 같은 개인정보는 공개 저장소에 넣지 않습니다.

### 사용자가 제공한 설명회 메모

아래는 대화 시작 때 사용자가 제공한 요약입니다. 공식 최신 공지와 다르면 공식 안내를 우선 확인합니다.

| 항목 | 제공된 내용 |
| --- | --- |
| 방향 | 스마트폰 앱의 단순 이식보다 차량 상태·주행/정차·운전자/동승자에 특화된 경험 |
| 개발 플랫폼 | AAOS 기반 Pleos Connect, Java/Kotlin·Android Studio |
| 주요 심사 관점 | 차량 특화성·창의성·기술 활용성·사업성. 이 개발 공모전의 비중은 제공되지 않음 |
| 참가 | 개인 또는 최대 5인 팀, 대학생·일반인 부문 |
| 1차 접수·발표 | 11/15 마감, 11/23 결과 |
| 2차 PT | 12/3~4, 실제 동작 앱 시연 필요 |
| 최종 발표·시상 | 12/18 발표, 2027년 2월 시상·전시 |
| 제출 | 기획서·참가자 전원 서명 동의서 필수, 영상·GitHub·APK 등 선택 |
| AI 활용 | 기획·개발 전 과정 활용 가능하되 구조와 로직을 설명할 수 있어야 함 |
| 출품·검증 | 복수 작품 가능, 실차 테스트 지원 없음, 에뮬레이터·Virtual Console 활용 |
| SDK 활용 | API 수보다 사용자 문제에 필요한 기능 활용이 중요 |
| 개발 권한 안내 | 개발 단계에는 별도 SDK 승인 없이 권한 선언으로 사용 가능하다는 설명. Gleo 스키마 승인·서버 STT 등록·배포 권한까지 보장하는 뜻으로 확대 해석하지 않음 |
| 외부 연결 | 외부 API·네트워크 사용 가능하다는 설명. 각 제공자의 키·정책·요금은 별도 확인 |

현재 앱의 핵심 심사 설명은 '차량 상태에 따라 실행을 제한하고 개인 환경을 적용·복귀하는 차량 특화 경험'입니다. SDK를 많이 호출하는 것 자체를 목표로 삼지 않습니다.

## 제공된 차량 API 범위 메모

사용자가 제공한 범위는 다음과 같습니다. 이 목록은 **항목마다 읽기·쓰기를 모두 보장한다는 구현 계약이 아닙니다.** 실제 함수·권한·차종·좌석별 지원은 SDK 참조와 capability로 확인합니다.

| 묶음 | 제공된 항목 |
| --- | --- |
| 주행·안전 | 브레이크 상태, 주행 모드·크루즈, 경고등·안전 상태, 방향 지시등, 조향각 |
| 차량 기본 정보 | VIN 등 차량 식별 정보, 표시 단위 |
| 에너지·운행 | 충전·SOC·전비, 연료량·연비, 누적·트립 거리와 시간 |
| 실내 환경 | 냉난방·풍량·온도, 실내외 조명·앰비언트 |
| 탑승·편의 | 좌석 위치·열선·통풍, 핸들 열선, 미러 열선 |
| 개폐·기타 | 도어·글로브박스 상태/제어, 창문·블라인드·Smart Glass, 와이퍼 상태/스위치 |
| 그 외 | 타이어 저압 상태, ETCS 음성 안내 음량 |

자.peace의 초기 검증 대상은 속도·기어·좌석·필요한 공조·조명입니다. VIN·도어 개폐·브레이크 제어 등 나머지 항목을 제품 범위로 자동 추가하지 않습니다.

## 제공된 Pleos App Market 목록 — 배경 자료

사용자 메모는 2026년 9월 발표 전체 18종 중 이름을 확인한 15종이라고 설명합니다. 다음은 **사용자 제공 목록을 보관한 것이며 현재 마켓 전체 목록을 이번에 재검증한 결과가 아닙니다.**

| 분류 | 앱 |
| --- | --- |
| 지도·내비 | 네이버 오토, 네이버 지도, 카카오내비, what3words, Make My Day – Route Planner |
| 음악·오디오 | Spotify, 멜론, 지니뮤직, essential;, SBS 고릴라, Radioline, RaiDIO Auto(래디오오토), Pickle(픽클) |
| 영상·웹 | YouTube, 네이버 웨일 |

앱이 마켓에 있다는 사실만으로 자.peace에서 해당 앱의 재생·길 안내를 제어할 API가 있다고 간주하지 않습니다. B를 재개하더라도 현재 검증한 내비 방향은 NaviHelper→Pleos Navigation이며, 임의의 타사 내비 선택은 별도 확인 대상입니다.

## 제공된 아이디어 공모전 수상작 — 별도 행사 참고

대화에서 제공된 메모: 접수 2026-06-26~07-05, 약 3,600건, 우수작 16개 중 작품명 공개 15개, 심사 창의성 50%·실현 가능성 30%·공감도 20%. 이 비중을 현재 **앱 개발 공모전**의 심사 비중으로 사용하지 않습니다. 아래 목록은 사용자 제공 참고 자료이며 이번 작업에서 개별 수상 정보를 재검증하지 않았습니다.

| 작품 | 제공된 아이디어 요지 |
| --- | --- |
| 창밖동화 · 대상 | 위치·경로 기반 AI 동화·퀴즈와 조명·창문을 연계한 어린이 체험 |
| car톡 | 음성으로 주변 차량과 연결해 위험 상황 전달 |
| 도로 보안관 | 위법 차량 영상·사진 기록과 신고 |
| 구덩이조심 | 차량 진동으로 도로 파손 감지·공유 |
| 마! 어데고! | 친구 차량의 위치·목적지 공유 |
| 로드시그널 | 이동 경로가 비슷한 운전자 연결 |
| 길찾기 대모험 | 내비와 연동한 어린이 모험 게임 |
| 아그들아, 여행가자! | 차량 카메라 기반 여행 영상 촬영·편집 |
| 로컬미션 | 관광지 미션과 제휴 혜택 |
| 카-케스트라 비트파티 | AI·V2V 기반 차량 간 음악 이벤트 |
| 차마고치 | 주유·충전·기어 등을 활용한 차량 관리 육성 게임 |
| 차계부 | 유지비 자동 기록·관리 |
| 여기서 타! | 동승자의 최적 픽업 장소 추천 |
| (현)대신 웨이팅 | 도착 예정 시간에 맞춘 식당 대기 등록 |
| 캐시 드라이브 | 지역 음성 광고 청취와 보상 |

이 목록은 기존 아이디어의 방향을 비교하기 위한 배경입니다. 자.peace는 개인 휴식 환경 저장·적용·복귀를 중심으로 하며, 독창성·경쟁 우위나 특허 여부를 이 목록만으로 입증하지 않습니다.

## 기술 공식 출처와 확인 수준

| 주제 | 출처 | 현재 확인 |
| --- | --- | --- |
| Vehicle 의존성 | [SDK 가져오기](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup) | 공식 Maven·Vehicle 2.3.2 의존성 다운로드 및 차량 APK 패키징 |
| Vehicle 기능 | [소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)·[권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission) | 도메인·읽기·제어·capability·비동기 구조 문서 확인. 실제 서비스 연동은 미구현 |
| 표준 정보 우선 | [Pleos API 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api)·[AAOS 차량 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds) | 속도·기어는 AAOS 표준 경로 우선 검토 |
| STT | [소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)·[시작/종료](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop) | SDK 세션 API 문서 확인. 상시 개인 호출어 보장은 아님 |
| Gleo | [예제](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples)·[스키마 전달](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema) | 스키마·Intent·검토/승인 경로 확인. 개발 이미지 호출은 미검증 |
| 내비 | [소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)·[목적지](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations) | 기존 Navigation 앱에 요청 전달. 검색 제공자와 구분. 현재 B 보류 |
| 마이크 | [Android microphone FGS](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone) | 직접 마이크 접근의 권한·백그라운드 시작 제약 참고 |
| 알람·전원 | [Android 알람](https://developer.android.com/develop/background-work/services/alarms/schedule)·[AAOS 전원](https://source.android.com/docs/automotive/power) | 앱 수명과 OS 예약, 차량 전원 종료를 구분 |
| Pleos 실행 환경 | [설치](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)·[GPU/Cold Boot](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator) | Windows/Linux 이미지, GPU·RAM·Cold Boot 등의 문서 조건. 실제 부팅은 미검증 |
| 클라우드 일반 구성 | [Google Android Emulator cloud 예제](https://github.com/google/android-emulator-container-scripts/blob/master/cloud-init/README.MD) | KVM 등 조건을 갖춘 일반 Android 실행 구성의 참고. Pleos 클라우드 성공 기록이 아님 |

정확한 좌석 제어 함수의 확인 목록과 미확정 사항은 DESIGN 4절과 ROADMAP Q01~Q11에 있습니다. 출처가 바뀌면 최신 공식 문서를 확인하고, 앱의 실제 지원 여부는 검증 기록으로 남깁니다.
