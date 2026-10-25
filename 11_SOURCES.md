# 공식 출처와 조사 이력

모든 자료 확인 기준: **2026-10-25 KST**. 공모전은 브라우저 직접 탐색, 제품·SDK는 공식 사이트 검색과 본문 열람으로 확인했다. SDK 신청 페이지는 브라우저로 추가 교차 확인했다. 이번 보완에서 공식 SDK sources.jar와 Windows 이미지 XML을 메모리에서 읽었으며 SDK 설치는 하지 않았다. 검색 스니펫만의 정보는 확정 근거로 쓰지 않았다. 아래는 원문 복제가 아닌 출처 색인이다.

## 공모전

| ID | 공식 자료 | 확인 목적 |
|---|---|---|
| C01 | [홈](https://appdevchallenge.auto-hmg.io/) | 모집·전체 구조 |
| C02 | [참가 가이드](https://appdevchallenge.auto-hmg.io/guide) | 연결 문서 목록 |
| C03 | [개요](https://appdevchallenge.auto-hmg.io/guide/overview) | 대상·주제 |
| C04 | [일정](https://appdevchallenge.auto-hmg.io/guide/schedule) | 공식 KST 일정·PT 장소 |
| C05 | [제출](https://appdevchallenge.auto-hmg.io/guide/submission) | 이메일·자료·GitHub |
| C06 | [개발 환경](https://appdevchallenge.auto-hmg.io/guide/development) | SDK 연결과 개발 조건 |
| C07 | [심사·시상](https://appdevchallenge.auto-hmg.io/guide/judging-awards) | 평가·상금·입점 조건 |
| C08 | [참가 규정](https://appdevchallenge.auto-hmg.io/guide/rules) | 수정·기한·의무 |
| C09 | [숏폼](https://appdevchallenge.auto-hmg.io/guide/shortform) | 길이·채널·태그 |
| C10 | [FAQ](https://appdevchallenge.auto-hmg.io/faq) | 제출·SDK·순위 문답 |
| C11 | [공지 목록](https://appdevchallenge.auto-hmg.io/notice) | 확인 당시 3건 |
| C12 | [모집 공고](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000001) | 모집 요건 교차 확인 |
| C13 | [설명회 공지](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000005) | 상세 시각·링크 미공개 확인 |
| C14 | [출품 유의사항](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006) | 20팀 PT·10팀 행사·IP 조건 |
| C15 | [문의 안내](https://appdevchallenge.auto-hmg.io/guide/help) | 공식 이메일 |
| C16 | [이용약관](https://appdevchallenge.auto-hmg.io/terms) | 권리·독립 개발·OSS |
| C17 | [개인정보 처리방침](https://appdevchallenge.auto-hmg.io/privacy) | 2026-10-21 시행, 수집·보관 |
| C18 | [접수양식 DOCX](https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx) | 마감 시각·항목·비식별·서명 |

C18의 원본은 [references](references/APPLICATION_FORM_NOTES.md)에 해시와 함께 보관했다. 나머지 페이지 전체를 복사·미러링하지 않고 필요한 사실과 출처만 요약했다. 공모전 전체 메뉴의 공개 안내를 읽었으며 로그인·가입·지원 발송은 하지 않았다.

## 제품 발표와 인터뷰

| ID | 발표일 | 공식 자료 | 확인 범위 |
|---|---|---|---|
| P01 | 2025-03-28 | [Pleos 브랜드](https://www.hyundai.com/worldwide/en/newsroom/detail/0000000921) | 개방형 생태계 방향 |
| P02 | 2026-04-30 | [Pleos Connect 발표](https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-group-redefines-in-vehicle-experience-with-pleos-connect-next-generation-infotainment-system) | 복합명령·앱·분할 화면 |
| P03 | 2026-09-09 | [App Market 신규 앱](https://www.hyundaimotorgroup.com/ko/news/42dot-pleos-connect-app-market-new-apps) | 9-7 발표 내용을 소개, 국내 18종 |
| P04 | 2026-09-29 | [Gleo 개발자 인터뷰](https://www.hyundaimotorgroup.com/ko/story/42dot-gleo-ai-developer-present-and-future) | 현재 앱 연동·향후 개인화 구분 |

‘사용자 정의 루틴 공식 지원 미확인’은 이 자료들과 공식 사이트 한정 검색에 대한 조사 결과다. 전체 차량 펌웨어 메뉴의 부재를 입증한 것은 아니다. Tesla 대비 비교 우위를 입증하는 독립 검증도 수행하지 않았다.

## 개발 문서

| ID | 자료 | 확인 범위 |
|---|---|---|
| D01 | [프로젝트 생성·SDK 신청](https://document.pleos.ai/docs/pleos-only/create-my-project) | 준비 중/사용 가능 상태 |
| D02 | [SDK 개요](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/) | 모듈 목록·권한 협의 |
| D03 | [SDK 릴리스](https://document.pleos.ai/docs/pleos-only/release-note/sdk-261015) | 본문 2026-10-19, SDK 버전 |
| D04 | [Emulator 릴리스](https://document.pleos.ai/docs/pleos-only/release-note/emulator-261015) | 2.1.0, LLM SDK·CRN 종료 |
| D05 | [환경 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator) | API·Gradle·AVD |
| D06 | [Vehicle 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup) | Maven 좌표 |
| D07 | [Vehicle 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) | Service 버전 |
| D08 | [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission) | 조회와 제어 구분 |
| D09 | [Capability](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability) | 접근·영역·범위 |
| D10 | [Callback](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/callback) | 구독·오류 |
| D11 | [HVAC](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/entry) | 공조 API 목록 |
| D12 | [Seat](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/entry) | 시트 API 목록 |
| D13 | [Light](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/entry) | 조명 API 목록 |
| D14 | [API 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api) | AAOS와 Pleos 역할 |
| D15 | [API 가용성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability) | 실제 지원 조회 |
| D16 | [개발 FAQ](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq) | 속도 CarPropertyManager 경로 |
| D17 | [Android VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds) | 기어·속도 정의·권한·단위 |
| D18 | [Gleo 개념](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts) | 앱 기능 호출 통합 |
| D19 | [Gleo Schema 전달](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema) | assets와 승인 |
| D20 | [Gleo App Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability) | 3 actionCalls·지원 타입·필수 인자 한계 |
| D21 | [STT 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro) | 음성→텍스트 |
| D22 | [TTS 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro) | 음성 출력 모드 |
| D23 | [위치 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro) | 1.1.3·영역 이벤트 |
| D24 | [위치 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions) | FUSED_LOCATION·정책 구분 |
| D25 | [Virtual Console](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide) | 테스트 입력 |
| D26 | [Vehicle Sample App](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app) | 요청과 결과 확인 구분 |
| D27 | [앱 동작 모델](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model) | 수명·백그라운드 최소화 |
| D28 | [주행 제한 개발](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide) | UX restriction listener |
| D29 | [지역별 주행 제한](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy) | 주차/주행 조건 |
| D30 | [앱 심사](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability) | 승인 권한·기능 검증 |
| D31 | [카테고리](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category) | 분류 후보 |
| D32 | [Vehicle Data API](https://document.pleos.ai/en/api-reference/vehicle-data-api/intro) | 클라우드 데이터·지역 제한 |
| D33 | [Fleet API](https://document.pleos.ai/en/api-reference/fleet-api/intro) | 차량관리·Webhook |

## 불일치 처리 기록

| 항목 | 관찰 | 계획에 반영한 판단 |
|---|---|---|
| SDK 공개 상태 | 상세 API·10월 릴리스와 D01 준비 중 표기 병존 | 제공 상태를 보장하지 않고 실제 계정/운영사 확인 게이트 |
| Vehicle Service | 소개 2.3.21, 샘플 2.3.22 | SDK 버전과 별도로 설치 Service 기록 |
| 마감 시각 | 웹 일정은 날짜, DOCX는 23:59 | DOCX 근거 표시, 제출 직전 재확인 |
| 제출 경로 | 이메일 접수와 ‘제출 페이지’ 문구 혼재 | 이메일+양식 링크로 준비, 운영사 확인 |
| GitHub와 비식별 | 저장소 제출 요구와 계정정보 비노출 요구 병존 | 소유자/커밋 포함 적용 방식 문의 |
| 오래된 검색 결과 | 일부 이전 Playground URL은 404, SDK 스니펫과 현행 본문 차이 | 스니펫만으로 상태 확정하지 않음 |

## 다음 갱신 시점

10-28 설명회, SDK 프로젝트 상태 확인, 1차 제출 직전, 1차 결과 후 PT 공지, 마켓 배포 준비 때 재확인한다. 이는 문서에 남긴 체크포인트이며 자동 모니터링을 설정한 것은 아니다.

## Windows 인수인계·SDK 소스 추가 근거

아래는 2026-10-25 KST 추가 확인 자료다. DOC는 문서 확인, SOURCE는 배포 아티팩트/공식 코드 확인을 뜻한다. ACCOUNT·RUNTIME·RELEASE 증거는 아직 없다. 최신 웹 문서를 읽은 사실이 특정 설치 버전의 모든 API가 호환됨을 보장하지 않는다.

| ID | 공식 자료 | 확인 수준·범위 |
|---|---|---|
| D34 | [공식 Vehicle Sample 저장소](https://github.com/Pleos-Playground/pleos-sample-vehicle-app) | DOC/SOURCE: 구조·SET ACK와 관찰값 분리·listener 수명·기존 API 개수 시험 불일치. clone/build 미실행 |
| D35 | [Vehicle 2.3.2 sources.jar](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2-sources.jar) | SOURCE: HVAC·Light·Seat·MoveControl 선언, 함수별 권한·callback·단위 |
| D36 | [Windows/Linux 이미지 XML](https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml) | SOURCE: API34·x86_64·Pleos2.1.0 패키지; 이미지 다운로드/설치 미실행 |
| D37 | [Android Studio 설치 요구사항](https://developer.android.com/studio/install) | DOC: Windows x64·가상화·메모리, Windows ARM 지원 제한; 사용자 PC 미조사 |
| D38 | [Emulator 가속](https://developer.android.com/studio/run/emulator-acceleration) | DOC: WHPX 권장·accel-check·재시작·AEHD/HAXM 차이 |
| D39 | [Android 환경변수](https://developer.android.com/tools/variables) | DOC: ANDROID_HOME·deprecated ANDROID_SDK_ROOT·ADB 대상 |
| D40 | [Virtual Console 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator) | DOC: forward tcp:8088과 127.0.0.1 접속, 입력 재현 |
| D41 | [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager) | DOC: 권한/미지원/가용성 구분·구독 API; API34 실제 사용 가능 경로는 확인 과제 |
| D42 | [Compose 시작](https://developer.android.com/develop/ui/compose/setup) | DOC: UI 구성 참고 진입점. 제품 의존성/버전 결정·설치 아님 |
| D43 | [Activity lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle) | DOC: 화면/프로세스 수명 참고; STOP 보장으로 해석 금지 |
| D44 | [Audio focus](https://developer.android.com/media/optimize/audio-focus) | DOC: 자체 음원의 포커스/중단 구현 참고, 대상 OS·target별 확인 필요 |
| D45 | [Android testing](https://developer.android.com/training/testing) | DOC: 로컬·계측·UI 시험 구성 참고 |
| D46 | [App signing](https://developer.android.com/studio/publish/app-signing) | DOC: release 서명·키 관리 참고; 서명·키 생성 미실행 |
| D47 | [Gradle Wrapper](https://docs.gradle.org/8.10.2/userguide/gradle_wrapper.html) | DOC: 프로젝트 Wrapper·Windows 실행 재현 기준 |
| D48 | [Network security configuration](https://developer.android.com/privacy-and-security/security-config) | DOC: 개발/제품 네트워크 설정 분리 참고; 서버 구현 미착수 |
| D49 | [SystemClock](https://developer.android.com/reference/android/os/SystemClock) | DOC: elapsedRealtime 등 경과시간 시계; 타이머의 벽시계 변경 분리 참고 |
| D50 | [Pleos Playground — 사용자 지정 출발점](https://pleos.ai/playground) | DOC: 개발 여정·Vehicle/NaviHelper/TTS·디자인·Publish·Gleo·Workspace 진입점; 계정 사용 가능성 확인 아님 |

D47의 버전별 페이지는 웹 도구에서 열리지 않아 공식 URL을 HTTP로 직접 읽어 응답 200과 Windows `gradlew.bat` 안내를 확인했다. 도구로 페이지 접근을 확인한 것과 Windows 명령 실행 검증은 다르다.

### D35 재현 식별자

- 좌표: `ai.pleos.playground:Vehicle:2.3.2`, 분류자 `sources`, 확장자 `jar`.
- 크기: **145,042 bytes**.
- SHA-256: `62b5dd2c4c0523fd13c2e64040cd775bc89a14ce54f35a37560d0eb4eb3fab40`.
- 읽은 주요 경로: `ai/pleos/playground/vehicle/api/HVAC.kt`, `Light.kt`, `Seat.kt`, `constant/control/MoveControl.kt` 및 capability 타입.
- 온도 목표/실내 구분, 최신 3인자 capability callback, 조명 쓰기의 CAR_LIGHTS 선언, 시트 방향·STOP과 Int 위치를 [기능 표](05_CAPABILITY_MATRIX.md)에 요약했다. 파일 전체 복제·SDK 설치·실행은 하지 않았다.
- 후속 구현자는 소스 hash·resolved dependency·공식 샘플 commit을 기록한다. 다른 hash면 위 계약을 그대로 확정하지 않고 차이를 조사한다.

### 추가 불일치 처리

| 관찰 | 설계/개발에 반영 |
|---|---|
| Control 신청 준비 중과 SDK 쓰기 API 공존 | 실제 프로젝트 제공 상태·기능별 capability·상태 반영은 독립 게이트 |
| 조명 쓰기의 CAR_LIGHTS 선언 | 일반 CONTROL 명명 추정 금지, 함수/버전별 검증 |
| 시트 이동 주석과 MoveControl 인자 | 각도·거리 API로 오인 금지, 실제 방향·위치 의미 계측 |
| Console 소개의 속도 언급 vs 카드 목록 | PERF_VEHICLE_SPEED 입력 경로 미확정; Cruise Target Speed 대체 금지 |
| 시트 탑승/온도 카드 vs 위치 이동 | 앞뒤/등받이·STOP은 AVD에서 별도 재현 |
| 샘플의 기존 API 개수 시험 실패 안내 | 실제 commit에서 재현·분류, skip으로 통과 처리 금지 |

## 다음 개발자가 출처를 갱신하는 방법

1. 작업 TODO와 요구사항 ID로 확인할 API·오류·단위·수명 항목을 먼저 적는다.
2. 설치하려는 정확한 버전의 공식 문서·소스·릴리스·샘플을 확인한다. 검색 결과 요약만으로 계약을 확정하지 않는다.
3. URL·확인일·버전·찾은 사실·미확인 부분·해당 코드 경로를 추가한다. 계정 화면의 비밀정보는 가린다.
4. 문서에 없는 동작은 격리된 시험으로 확인하고 공식 사실과 실험 결과를 분리한다.
5. 실제 성공 여부는 07 검증 기록으로 연결한다. 링크를 추가한 것만으로 구현 TODO를 완료하지 않는다.

AI 공급자·서버/저장/네트워크 라이브러리는 아직 선택하지 않았다. 선택 작업에서 공식 API·호환성·비용/보존 정책 근거를 추가하며 현재 후보를 실제 사용 중이라고 기록하지 않는다.
