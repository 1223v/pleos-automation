# 공식 출처와 조사 이력

## 2026-11-15 계획서 대조·음성 입력·시트 확인

| 공식 문서·버전 | 확인한 범위 |
|---|---|
| [공지: 개발 범위 및 기술 문의 안내](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008) | 공개 문서의 SDK 범위에서만 개발하고, 지원되지 않는 기능은 기획서에 향후 계획으로 적는다. 참가팀은 Gleo Interface(App Schema) 검토·승인을 신청할 수 없다. 음성 인식은 SpeechToText SDK를 쓰며 차량 마이크 음성을 외부 인식 서비스로 보내지 않는다. 2차 PT는 Pleos Connect 에뮬레이터 시연이다. |
| [공지: 비공개 GitHub 저장소 제출](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000007) · [제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission) | 마감 11월 15일 23:59. 접수 메일에 저장소 주소와 최종 커밋 해시. 비공개 저장소는 11월 16일까지 공개 전환, 11월 23일까지 유지. README에 앱 소개·실행법·시험 환경·에뮬레이터 버전과 화면 규격·APK 위치. `apk/팀명_앱이름_release.apk`. |
| [SpeechToText 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro) · [SDK 가져오기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk) · [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions) · [초기화](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/initialization-and-release) · [리스너](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/result-listeners) · [시작·중지](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/start-and-stop) · [앱 등록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/app-registration) · [언어](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/set-locale) · [오류](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/error-code) | `ai.pleos.playground:SpeechToText:3.3.1`, 권한 `pleos.car.permission.STT_SERVICE`. ON_DEVICE는 네트워크·앱 등록이 필요 없다. ko-KR 지원. `ResultListener` 7개 콜백과 `ErrorType` 12종. 공식 Maven의 AAR(SHA-256 `35109ad1…37bf`)을 `javap`로 읽어 공개 서명이 문서와 같음을 확인했다. 에뮬레이터 caas 4.2.10이 SDK 최소 4.1.29를 넘는다. |
| [Virtual Console](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide) (한·영) · [Seat API 목록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/entry) | Seat API에는 `moveSeatForeAft`·`moveSeatBackrestAngle`이 있으나 Console의 시트 카드는 Seat Occupancy와 Seat Temperature뿐이다. 시트 위치·등받이·외기 온도·실제 속도 카드는 없다. 10월 28일 실측(MOVE·STOP 응답, 위치 0 유지)과 함께 에뮬레이터에서 시트 이동을 입증할 수단이 없다고 판단했다. |

## 2026-11-12 겨울 차박 구현·접수 재확인

| 공식 문서·버전 | 확인한 범위 |
|---|---|
| [접수·제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission) · [심사·시상](https://appdevchallenge.auto-hmg.io/guide/judging-awards) · [공식 DOCX](https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx) | 1차 접수는 11월 15일까지 작성·서명한 양식을 이메일로 제출한다. 시연 영상과 소스·APK를 포함한 GitHub는 선택 가산점 자료다. 공식 DOCX 응답의 SHA-256은 보관본과 같은 `07be893d58b0e96ab7788f0d189d6829f0e59a2cee4bc18cb90ec37eeda2f5fd`였다. |
| [SDK 버전과 변경 사항](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note) · [Virtual Console](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide) · [주행 안전 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) | Vehicle 2.3.2의 영역 값 비교, 콘솔의 좌석 점유·열선과 운전석 공조 목표 온도, CarUxRestrictions에 따른 편집 제한을 대조했다. 시트 앞뒤·등받이 이동은 별도 SDK 계측 대상이다. |
| [Gleo Callback Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback) | asking·success·fail 및 7초 시간 초과를 확인했다. 운전자의 편집 확인과 물리 실행 결과는 서로 다른 사건이다. 로컬 응답 시험으로 Gleo 서비스 등록·수신을 입증할 수 없다. |
| [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses) · [Responses create](https://developers.openai.com/api/reference/resources/responses/methods/create) | 프로젝트 Node SDK 7.45.0과 strict JSON Schema를 대조했다. 초안/질문/수정안의 의미와 선택한 노드의 범위는 앱과 서버가 별도로 검사한다. |

**2026-11-05 KST 갱신.** 이번 공모전의 접수 요건, 비교 대회 수상작, 판매 차량의 기존 기능을 아래에 정리했다. C/P/D01~D50은 별도 표시가 없으면 2026-10-25 초기 조사 기록이다. D51 이후와 O/E 표에는 후속 구현 근거를 누적했다. 제품의 현재 검증 범위는 [A05 검증 기록](evidence/A05/verification-2026-11-02.md)과 [구현 TODO](13_IMPLEMENTATION_TODO.md)를 따른다.

공모전은 브라우저 직접 탐색, 제품·SDK는 공식 사이트 검색과 본문 열람으로 조사했다. 검색 스니펫만의 정보는 확정 근거로 쓰지 않는다. 문서(DOC), 배포 소스(SOURCE), 계정 권한(ACCOUNT), 실제 실행(RUNTIME)을 구분하며, 아래 URL이 공개됐다는 사실은 계정 사용 가능성이나 실제 차량 제어 성공의 증거가 아니다.

## 2026-11-05 접수 요건 재확인

| 공식 근거 | 지원서에 적용할 내용 |
|---|---|
| [공식 접수양식](https://appdevchallenge.auto-hmg.io/downloads/contest-application-consent.docx) · [보관 원본](references/contest-application-consent.docx) | 당일 내려받은 55,208바이트, 동의서 `1020 ver.`, SHA-256 `07be893d58b0e96ab7788f0d189d6829f0e59a2cee4bc18cb90ec37eeda2f5fd`가 보관 원본과 일치했다. 소개 자료 분량 제한은 없고, 심사 소개 부분은 익명으로 작성한다. 마감은 11월 15일 23:59 KST다. |
| [심사·시상](https://appdevchallenge.auto-hmg.io/guide/judging-awards) · [제출 방법](https://appdevchallenge.auto-hmg.io/guide/submission) | 1차 필수는 접수양식이며 차량특화성·창의성·기술 활용성·사업성을 평가한다. 세부 배점은 비공개다. 시연 영상·소스와 실행 가능한 release APK를 담은 GitHub는 선택 가산점 자료다. 숏폼은 별도 특별상이다. 각 참가자의 동의·서명·날짜를 갖춰 이메일 접수하며, 팀은 전원 CC한다. |
| [일정](https://appdevchallenge.auto-hmg.io/guide/schedule) · [출품 유의사항](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000006) · [FAQ](https://appdevchallenge.auto-hmg.io/faq) | 1차 20팀 결과 발표는 11월 23일이다. 이들이 12월 3~4일 PT에서 에뮬레이터 또는 기기로 시연한 결과로 순위가 결정된다. 최종 10팀 선정 발표는 12월 18일, 현장 전시·순위 발표는 2027년 2월 예정이다. 11월 5일 현재 이번 외부 공모전은 접수 중이다. |
| [현대차·기아 모집 발표](https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-kia-vehicle-app-development-contest) · [설명회 공지](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000005) | 10월 21일 주최 측은 8월 사내 해커톤에 이어 대상을 외부로 확대한다고 발표했다. 설명회 공지에는 10월 28일 14:00가 명시됐다. |

## 수상작과 겨울 차박 지원서의 비교 근거

아래 날짜는 주최 측 발표일이다. 전체 명단과 선별 소개를 구분했다. 오른쪽 열은 겨울 차박을 준비하는 운전자에게 이 앱의 가치를 설명하기 위한 작성 판단이다. 대회별 배점이나 다른 작품의 사용 실적을 우리 작품에 적용하지 않는다.

| 발표일 · 대회 | 확인된 수상작과 사용 장면 | 이번 지원서에 적용할 점 |
|---|---|---|
| 2026-08-24 · 현대차·기아 사내 Pleos 24H 코드 레이스 | 대상 **테일싱크**는 주행 데이터와 아동 반응을 반영해 목적지 도착에 맞춰 동화를 끝낸다. 주최 측은 고객 가치와 독창성을 높이 평가했다. 우수상은 아기 꿀잠 드라이브·마중·액시트다. [수상 4팀 전체](https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-kia-24h-code-race-hackathon) | 목적지에서 P단에 놓은 뒤에야 사용자가 저장한 차박 준비를 시작할 수 있는 장면으로 차량 상태의 가치를 설명한다. |
| 2026-10-28 · 현대모비스 사내 모빌리티 애플리케이션 해커톤 | AI 차량 진단앱 **모비딕**이 최종 우승했다. 모비몬과 카 드 메이커도 우수 앱으로 소개됐다. [우승 및 일부 우수작](https://www.hyundaimotorgroup.co.kr/ko/news/hyundai-mobis-mobility-app-development-contest) | 같은 그룹의 AAOS 기반 인포링크 사례다. 운전자의 자연어 요청을 검토할 수 있는 규칙으로 바꾸는 일에 AI의 역할을 한정해 표현한다. |
| 2023-09-06 · LG유플러스·현대모비스·오비고 차량용 앱 서비스 공모전 | 대상 **워치마일**은 GPS가 닿기 어려운 실내 주차장에서 영상으로 점유 현황을 파악해 길을 안내한다. 우수상은 닥터차·스루 운영사다. [수상 기업 3곳 전체](https://news.lguplus.com/13557) | 동일한 차박 준비를 수동 설정할 때와 저장한 루틴을 다시 쓸 때의 조작 수·시간을 비교하는 사용자 시험을 설계한다. |
| 2026-10-14 · Eclipse SDV Hackathon Chapter Four | 혁신 트랙 1위 **Guardian Loop**는 고온 실내·차량 아동 방치에 대응하도록 감지·판단·구동을 분리했다. [공식 트랙별 결과](https://eclipsesdv.org/blogs/from-student-dreams-to-open-source-reality-why-the-eclipse-sdv-hackathon-2026-was-different/) · [우승팀 원본 발표자료](https://github.com/Eclipse-SDV-Hackathon-Chapter-Four/Bare-Metal-Mafia/blob/main/pitch/Bare-Metal-Mafia_Pitch.pptx) | 원본 5장은 모의 입력과 실제 하드웨어를, 12장은 완료 및 후속 개발을 구분한다. 우리 시연도 가상 시험·에뮬레이터 관찰·시트 미검증을 화면에서 구분한다. |
| 2025-10-09 · Eclipse SDV Hackathon Chapter Three | **CarMate**가 1위다. 장거리 단독 운전자에게 차량 맥락을 반영한 음성 AI 동반자를 제공한다. [결선 7팀·수상 1~3위·코드](https://eclipsesdv.org/ko/blogs/meet-the-2025-sdv-hackathon-finalists-and-winners-and-explore-their-code/) | 운전자가 차박 규칙을 검토·수정·시험한 뒤 다음 방문 때 다시 쓰는 과정에서 차별점을 찾는다. |
| 2024-11-21 · Google Gemini API Developer Competition | **Gaze Link**가 Android 앱 부문을 수상했다. 돌봄 제공자의 질문과 ALS 환자가 시선 추적으로 입력한 단어로 문장 답변을 생성한다. 종합 수상작 **Jayu**는 화면을 읽고 여러 앱의 UI와 상호작용한다. [전 부문 수상작·시연](https://developers.googleblog.com/en/announcing-the-winners-of-the-gemini-api-developer-competition/) | 차박 요청 원문과 그 요청을 옮긴 조건·동작 카드를 나란히 제시해 AI가 맡은 일을 보여준다. |
| 2024-06-06 · Apple Design Awards | 포용성 앱 부문 수상작 **Oko**는 보행 신호 상태를 진동·소리로 알려준다. 개발팀은 현장 사용자의 선호를 확인하고 화면을 세로로 재설계했다. [수상·결선 전체](https://developer.apple.com/design/awards/2024/) · [Apple의 팀 인터뷰](https://developer.apple.com/news/?id=58c4urmu) | 차박 운전자가 시트 실행을 보류한 이유를 이해했는지 묻고, 실제 피드백과 이후 수정 사항을 기록한다. |
| 2024-07-01 · Google for Developers Solution Challenge | **Spoon Share**는 나눔 음식의 위치 정보를 단체·봉사자가 검토한 뒤 지도에서 찾을 수 있게 해 Top 3에 선정됐다. 같은 Top 3의 **ATTI**는 경도 치매 당사자의 기억과 일상 관리에 음성 도우미를 사용한다. [Top 3·인기상 전체](https://blog.google/innovation-and-ai/technology/developers-tools/meet-the-students-that-are-changing-the-world-through-technology/) | 시트가 들어간 루틴에서 누가 무엇을 언제 확인하는지, 취소하면 어떻게 되는지 사용 흐름에 명시한다. |

## 판매 차량의 유사 기능

| 발표일 · 제품 | 제조사가 설명한 기능 | 차별화의 범위 |
|---|---|---|
| 2023-04-25 · Mercedes-Benz 2024 E-Class MBUX | 중앙 화면에서 조건과 여러 동작을 조합한 루틴을 작성·편집하고 이름을 붙여 저장한다. 예시로 낮은 실내 온도에 따라 시트 열선과 앰비언트 조명을 켠다. [Mercedes-Benz USA 발표](https://media.mbusa.com/news/a-bridge-between-tradition-and-digitalization-the-new-e-class) | 조건부 차량 루틴에는 양산차 선례가 있다. Pleos에서 자신의 차박 흐름을 직접 고치고 시험한 뒤 실행 기록에서 결과를 확인하는 장면을 비교한다. |
| 2026-09-29 · Pleos Connect Gleo AI | 라디오·공조·무드등을 한 발화로 요청하면 차량 상태와 안전 조건을 고려해 순차 수행하는 사례가 소개됐다. 이동 습관을 기억해 선제 제안하는 기능은 인터뷰에서 향후 방향으로 다뤘다. [현대자동차그룹 개발자 인터뷰](https://www.hyundaimotorgroup.com/ko/story/42dot-gleo-ai-developer-present-and-future) | Gleo의 복합 음성 명령과 이 앱이 제공하는 사용자 편집·저장·가상 시험·실행 이력을 구분한다. Gleo 등록과 실제 음성 호출은 별도 검증 대상이다. |

## 공모전 초기 출처 색인

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

아래는 2026-10-25 KST 추가 확인 당시의 자료다. DOC는 문서 확인, SOURCE는 배포 아티팩트/공식 코드 확인을 뜻한다. 당시 ACCOUNT·RUNTIME·RELEASE 증거는 없었다. 후속 구현·설치 결과는 검증 장부에서 별도로 기록한다. 최신 웹 문서를 읽은 사실이 특정 설치 버전의 모든 API가 호환됨을 보장하지 않는다.

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
| D46 | [App signing](https://developer.android.com/studio/publish/app-signing), [apksigner](https://developer.android.com/tools/apksigner), [JDK 17 keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html) | 2026-11-15 확인. JDK 17로 제출 키 생성, Build Tools 35.0.0으로 APK 서명과 검증. [설치 결과](09_SUBMISSION_CHECKLIST.md#apk-검증-기록) |
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

초기에는 AI 공급자·서버/저장/네트워크 라이브러리가 미선정이었다. 10월 27일 승인된 구현에서는 OpenAI 및 아래 의존성을 사용한다. 실제 AI 호출은 키·일/월 예산·명시적 활성화 설정 전까지 보류하며, 문서 조사와 모의 시험은 실제 호출 평가를 대체하지 않는다.

## 2026-10-28 구현 근거 보완 — Pleos·Android

다음 표의 확인일은 모두 **2026-10-28 KST**다. 버전은 저장소 빌드 선언 또는 버전별 공식 문서의 값이며 ‘최신 권장 버전’이라는 뜻이 아니다. W03~05/W10~14는 차량·수명, W23~29는 시각 편집·Gleo·연결 확장에 연결된다.

| ID | 공식 자료 / 적용 버전 | 확인 범위와 적용 경로 |
|---|---|---|
| D51 | [Vehicle 의존성](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup) / 2.3.2 | DOC: 공식 Maven 저장소와 `ai.pleos.playground:Vehicle:2.3.2`. D35 배포 소스와 함께 `android/app/build.gradle.kts`, `vehicle/VehiclePlatform.kt`의 타입 계약 기준. capability와 실제 SET 결과는 별도 검증 |
| D52 | [NaviHelper 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro), [설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk) / 3.0.5 | DOC: 초기화·리스너·목적지 요청·이벤트·정리 순서. 실제 이벤트는 Navigation 앱 상태/구현에 의존. `execution/RoutineRuntime.kt` |
| D53 | [Gleo App Schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability) / 웹 현행 | DOC: 최대 3 function, 대문자와 밑줄 이름, activity 또는 broadcast 진입, 영어 설명. `contracts/gleo-app-schema.json`, Gleo 진입점의 생성/수정/호출 계약 |
| D54 | [Gleo callback](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback), [예제](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples) / 웹 현행 | DOC: functionName 일치, success/asking/fail, 영어 message, 7초 timeout. 응답 broadcast action과 packageName/response extras 확인. 사용자 확인이 남으면 asking이며 물리 실행 성공으로 응답하지 않음 |
| D55 | [Gleo 스키마 전달](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema) / 웹 현행 | DOC: APK의 **assets/schema.json** 이름 필수, function별 manifest action filter, 스키마 검토·승인 후 통합 활성화. 소스 초안 존재만으로 등록 완료를 주장하지 않음 |
| D56 | [AGP 8.8 릴리스](https://developer.android.com/build/releases/agp-8-8-0-release-notes) / 8.8.2 | DOC: AGP 8.8의 최대 API35, Gradle8.10.2, Build Tools35.0.0, JDK17 기준. 루트 Android 빌드와 Wrapper |
| D57 | [Compose BOM 매핑](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [드래그](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/drag-composables) / 선언 BOM2024.04.01 | DOC: BOM과 개별 모듈 버전 구분, pointer 입력 기반 이동. `ui/FlowCanvas.kt`. 웹 최신 매핑을 선언한 과거 BOM의 모든 resolved 버전으로 간주하지 않음 |
| D58 | [Room 릴리스](https://developer.android.com/jetpack/androidx/releases/room#2.7.2) / 2.7.2 | DOC: 2.7.2 배포·수정 이력, 2.7 계열 Kotlin2.0 요구와 KSP. `data/RoutineDatabase.kt`, schema export |
| D59 | [Media3 릴리스](https://developer.android.com/jetpack/androidx/releases/media3#1.5.1) / 1.5.1 | DOC: ExoPlayer·MediaSession 모듈과 해당 버전 이력. 자체 음원 재생·세션 소유권에 적용; 외부 앱 제어 권한이 생기는 것은 아님 |
| D60 | [FGS 유형](https://developer.android.com/develop/background-work/services/fgs/service-types) / API34+ | DOC: 유형별 manifest·권한, specialUse의 사용 설명, mediaPlayback 구분. `RoutineService`와 알림·자동 감시. 선언만으로 스토어 심사나 장시간 실행을 보장하지 않음 |
| D61 | [Package visibility](https://developer.android.com/training/package-visibility/declaring) / target30+ | DOC: 패키지 탐색 필터와 queries. 설치 앱 또는 미디어 서비스 발견과 실제 명령 지원을 구분 |
| D62 | [Activity](https://developer.android.com/jetpack/androidx/releases/activity#1.10.1), [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle#2.9.0), [Core](https://developer.android.com/jetpack/androidx/releases/core#1.16.0) / 선언 1.10.1·2.9.0·1.16.0 | DOC: AndroidX 공식 릴리스 이력. UI·수명·권한 요청에 사용. 이 행의 버전은 앱 Gradle 선언값 |
| D63 | [Coroutines 1.9.0](https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.9.0), [Serialization 1.7.3](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.7.3) | DOC/SOURCE: 공식 릴리스 태그. 엔진 취소·병렬·JSON 경계에 사용. serialization1.7.3 릴리스의 기본 빌드 Kotlin은2.0.20이며 프로젝트 플러그인2.0.0과 동일한 버전이라고 기록하지 않음 |
| D64 | [Gradle Windows worker 이슈](https://github.com/gradle/gradle/issues/30304) / Gradle8.10.2 환경 참고 | SOURCE/이슈: 비ASCII 경로 관련 진단 참고. 로컬 ASCII GRADLE_USER_HOME 보정의 실제 결과는 검증 장부에 따로 기록하며 모든 Windows 실패의 원인으로 일반화하지 않음 |

Gleo 예제 본문은 개별 `putExtra` 전달을 설명하지만 코드에는 `ACTION_PARAM` JSON 문자열을 사용한다. 두 표현의 차이를 수신 파서에서 제한된 형태로 처리하되, 등록된 Gleo 런타임의 실제 입력·callback 시험은 별도다. 스키마 파일 이름과 내부 action 이름을 혼동하지 않는다.

로컬에 내려받은 배포 소스를 10월28일 SHA-256으로 다시 식별했다. Vehicle2.3.2는 D35의 `62b5dd2c4c0523fd13c2e64040cd775bc89a14ce54f35a37560d0eb4eb3fab40`과 일치하며, NaviHelper3.0.5 sources.jar는 `915feb7246215e6999f4695ba6b51cf31e4c6a5d8a8c1b36f16a3ba353569201`이다. 파일 위치는 Git 제외 `android/platform/cache/`이며 해시 확인은 설치 AVD의 동작 결과를 뜻하지 않는다.

## 2026-10-28 구현 근거 보완 — AI·서버

| ID | 공식 자료 / 적용 버전 | 확인 범위와 구현 판단 |
|---|---|---|
| O01 | [GPT-6.1 Sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol) / `gpt-6.1-sol` | DOC: 표준 100만 토큰당 입력$2, 캐시입력$0.10, 캐시쓰기$2.50, 출력$10. 272K 초과 입력은 별도 할증. `server/src/config.ts`, `budget.ts`; 모델 변경 전 가격/평가 재확인 |
| O02 | [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses) / Responses API | DOC: strict JSON Schema·required·additionalProperties:false, 지원 스키마 부분집합. `planner-response.schema.json`, `server/src/planner.ts`; 형식 적합성이 의미·권한·실행 승인을 보장하지 않음 |
| O03 | [데이터 제어](https://developers.openai.com/api/docs/guides/your-data) / 확인일 정책 | DOC: 기본 API 학습 사용과 보존 정책 구분, Responses의 store 설정·abuse monitoring·별도 자격 조건. `store:false`를 무보존/ZDR 계정 승인으로 표현하지 않음 |
| O04 | [공식 SDK](https://developers.openai.com/api/docs/libraries) / 설치 `openai`7.45.0 | DOC+로컬 배포 메타데이터: 공식 TypeScript SDK, 설치 package.json의 버전·Node>=22 계약, `server/package-lock.json` 고정. GitHub v7.45.0 태그 URL은 웹 도구로 열리지 않아 태그 원문 확인 완료로 기록하지 않음 |
| D65 | [Node SQLite](https://nodejs.org/download/release/v24.12.0/docs/api/sqlite.html) / Node24.12.0 | DOC: DatabaseSync·prepare/exec·파일/메모리 DB, 이 버전 안정성은 Active development. `server/src/budget.ts` 영속 예산 예약. 비ASCII 경로 장애의 원인은 이 문서로 확정하지 않음 |
| D66 | [Ajv strict mode](https://ajv.js.org/strict-mode.html) / 설치8.20.0 | DOC+로컬 배포 메타데이터: strict schema 컴파일과 알 수 없는 키워드 처리. `server/src/contracts.ts`; 그래프 의미 검사와 별도 |

서버 선언/lock 기준 TypeScript7.0.2, @types/node24.19.2, Ajv8.20.0, OpenAI SDK7.45.0이다. 위 공식 문서는 API 계약의 근거이며 패키지 버전 전체의 실행 호환성 증거는 빌드·시험 장부에서 확인한다. 로컬 예산 장부는 캐시쓰기까지 고려해 입력 100만 토큰당 **$3**으로 보수적으로 예약하며 실제 청구서를 재현하는 회계 기능이 아니다. 실제 호출·계정·결제는 이 출처 조사에서 실행하지 않았다.

## 2026-10-28 구현 근거 보완 — 연결 카탈로그

모든 행은 확인일 **2026-10-28 KST**, 연결 대상 `contracts/connector-catalog.json` 및 `server/src/connectors.ts`, 작업 W27/R18/T43에 해당한다. 카탈로그는 확인한 공개 표면의 목록으로, 세계 모든 서비스 또는 모든 마켓 앱의 완전 목록이 아니다.

| ID | 공식 자료 / API | 확인 범위·제약 |
|---|---|---|
| E01 | [Calendar events.list](https://developers.google.com/workspace/calendar/api/v3/reference/events/list) / v3 | DOC: OAuth 읽기 scope, timeMin/timeMax·응답 목록. 계정 연결 및 실제 일정 읽기는 미검증 |
| E02 | [Home Assistant REST](https://contributors.home-assistant.io/core/internals/rest-api/) / 2026-10-16 문서 | DOC: Bearer 인증, services action과 states 조회 구분. 상태 POST는 장치 제어가 아니므로 서비스 호출 뒤 상태 재조회 사용. 서버 관리자가 허용한 entity만 실행하는 제한은 제품 정책 |
| E03 | [Kakao Local REST](https://developers.kakao.com/docs/ko/local/dev-guide) / v2 Local | DOC: 키워드 장소 검색·REST API 키·좌표 반환. 카카오내비 앱의 임의 조작 권한을 뜻하지 않음 |
| E04 | [what3words](https://developer.what3words.com/public-api/docs) / API v3 | DOC: 세 단어 주소↔WGS84 좌표, HTTPS GET·API 키. 사용 요금·계정 가용성 별도 확인; 명시 활성화 전 호출 차단 |
| E05 | [Spotify Developer Policy](https://developer.spotify.com/policy) / 확인일 정책 | DOC: voice-enabled SDA·음성 제어 어시스턴트 금지 항목 확인. 마켓 설치 가능성과 본 제품의 음성 자동화 허용을 구분하고 직접 음성 제어는 차단 |
| E06 | [SmartThings API Access App Setup](https://developer.smartthings.com/docs/service-integrations/app-setup) / preview | DOC: 일부 기능 coming soon, 계정·공개HTTPS·유료 Developer/Business plan 선행 조건. 계정 생성·가입·결제·등록은 수행하지 않음 |
| E07 | [Radioline business](https://business.radioline.co/) / 제휴 API | DOC: Automotive 앱과 API 접근 요청 경로. 공개 기능 소개를 사용 가능한 인증정보로 간주하지 않음 |
| E08 | [Make My Day API](https://www.makemydayapp.com/api.php) / REST/JSON 소개 | DOC: 경로·EV 충전 계획 API, live API 접근 문의 필요. 직접 연결 계약과 런타임은 미확인 |

P02/P03 공식 발표 본문을 10월28일 다시 확인했다. 신규 발표는 국내 마켓 총18종을 설명하지만 두 발표에서 이름을 확인한14종만 카탈로그에 열거했다. 마켓 입점은 외부 앱의 Intent·MediaSession·API 자동화 지원을 입증하지 않는다. Vehicle 카탈로그의20 getter에는 deprecated Gear도 포함되어 공식 샘플 UI의19 도메인과 수가 다르며, 제품의 기어 판정 경로는 AAOS property다.

## 2026-10-28 표적 시험에서 확인한 버전 차이

| ID | 근거 / 버전 | 확인 결과와 적용 |
|---|---|---|
| D67 | [CoroutineScope 1.9.0 소스](https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.9.0/kotlinx-coroutines-core/common/src/CoroutineScope.kt), [NonCancellable 1.9.0 소스](https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.9.0/kotlinx-coroutines-core/common/src/NonCancellable.kt) | UNIT: 구조화 병렬의 형제 취소 후 정리, 취소 중 STOP 대기. IO에서 취소된 Main으로 돌아올 때 cancellation이 다시 발생하므로 이미 저장한 terminal 실행 이력을 덮어쓰지 않음 |
| D68 | [AAOS VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds), Vehicle SDK2.3.2 `HvacTemperatureConfigArray.kt` | DOC+SRC: 공조 전원 상태에 따른 property UNAVAILABLE 가능성, 목표 온도와 실내 현재 온도 구분, 설정 간격은 °C float로 반환됨. query 실패에서 쓰기 전 BLOCKED, 쓰기 후 timeout UNKNOWN |
| D69 | [setAmbientLightBrightness](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/set-ambient-light-brightness), 2.3.2 `Light.kt`/`LightImpl.kt`/`PleosPermission.kt` | DOC+SRC+AVD: 인터페이스 CAR_LIGHTS와 구현 CONTROL_CAR_LIGHTS 불일치. 실제 SecurityException 재현 후 두 권한 선언. 색상 fixed-FF 계약과 달리 AVD 조회값은 RGB24(0xDB854B). 색상 비교에서 생략된 alpha만 FF로 정규화하며 부분 alpha는 임의 보정하지 않음 |
| D70 | Vehicle SDK2.3.2 `BaseServiceConnector.kt:118~134`, `disconnectService:84~96`, `Vehicle.release:323` (D51 배포 소스) | SRC: 연결 준비 전·DeadObjectException의 호출을 queue에 저장하고 재연결 후 소비. 공개 release는 연결 해제와 queue clear로 연결되지만 MOVE와 STOP 사이의 경쟁·실패 안전성은 입증되지 않음. production seatPolicy 잠금 유지 |
| D71 | [Virtual Console 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator), [AAOS 에뮬레이터](https://developer.android.com/training/cars/testing/emulator) | DOC+AVD: 8088 포워딩, 실제 Console가 제공하는 HTML·catalog·state를 확인. `emulator_active=false`/DDS 준비 false, 공조 준비 요청은 emulator_inactive 응답. 실제 속도는 cruise target과 다른 경로이나 SDK 반영 미검증. 로컬 HTTP 시험 경로를 제품 API로 사용하지 않음 |

확인일은 모두 **2026-10-28 KST**다. SDK 소스는 Git 제외 로컬 cache의 공식 배포 archive이며 위 발견과 시험 결과는 [10월28일 검증 장부](evidence/implementation/2026-10-28.md)에 연결한다. 브라우저용 세션 보조 프로세스는 자동 승인 검토에서 거부되어 실행되지 않았다.

| ID | 공식 근거 / 버전 | 확인 범위 |
|---|---|---|
| D72 | [ActivityScenario 시험 가이드](https://developer.android.com/guide/components/activities/testing), [SystemClock Android14 소스](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/android/os/SystemClock.java) | 2026-10-28 DOC+SRC: CREATED로 Activity stop 시험, elapsedRealtime은 deep sleep 포함 단조 시각. SystemClock 웹 reference는 timeout이어서 AVD OS에 맞는 공식 AOSP 소스를 사용했다. 30분 시험은 실제 elapsedRealtime으로 별도 측정하고 가짜 시간·시뮬레이션을 사용하지 않는다. 이 시험은 차량 전원 suspend 검증을 대신하지 않는다. |
| D73 | [Package visibility](https://developer.android.com/training/package-visibility/declaring), [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager) / API28~35 | 2026-10-28 DOC: queries의 MAIN/LAUNCHER intent와 queryIntentActivities로 공개 실행 진입점을 검색. 설치 앱 목록은 해당 Android 사용자/앱 가시성 범위이며 기능 자동화 권한·MediaSession 연결·실제 화면 전환 성공을 의미하지 않는다. 패키지명을 추측하지 않으며 QUERY_ALL_PACKAGES를 추가하지 않는다. |
| D74 | [SharedPreferences.Editor](https://developer.android.com/reference/android/content/SharedPreferences.Editor#commit()) / API1~35 | 2026-10-28 DOC: commit의 동기 디스크 저장 결과를 IO dispatcher에서 확인. Gleo의 asking 응답은 IO 전에 보내며 저장·실행 성공을 뜻하지 않는다. 생성·수정 요청만 로컬 보관, CONTROL 미보관, 요청 ID가 바뀐 뒤 과거 요청의 지우기가 새 요청을 삭제하지 않도록 비교한다. |
| D75 | [공식 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator), [이미지 피드](https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml), [Emulator 릴리스](https://document.pleos.ai/docs/pleos-only/release-note/emulator-261015), [SDK 릴리스](https://document.pleos.ai/docs/pleos-only/release-note/sdk-261015) | 2026-10-28 재확인: 공개 릴리스는 Emulator2.1.0(10월19일), Vehicle2.3.2/NaviHelper3.0.5. 피드 revision2의 SHA-1/크기는 설치 이미지와 일치. Cold boot 필수, quick boot에서는 VHAL 사용 불가라고 명시. 실제 실행은 -no-snapshot으로 full boot였으므로 현재 실패를 Quick boot로 단정하지 않음. |
| D76 | [Android GPU 가속](https://developer.android.com/studio/run/emulator-acceleration), [CLI 옵션](https://developer.android.com/studio/run/emulator-commandline), 설치37.2.12의 `-help-gpu`/`-help-no-snapshot` | 2026-10-28 DOC+CLI: host는 호스트 GPU 드라이버, software는 소프트웨어 렌더러. -no-snapshot은 로드·저장을 막아 full boot. 현재 Windows GPU는 nvidia-smi에서 RTX3060 12288MiB/610.88. WMI AdapterRAM은32비트 값이므로 실제VRAM으로 판정하지 않음. GPU 선택만으로 DDS/공조 정상화를 보장하지 않는다. |
| D77 | [Console 기능표](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide) | 2026-10-28 재확인: 목표 온도16~30°C/0.5°C, 좌석 점유·열선/통풍 제공. 공개 카드 표에는 시트 앞뒤/등받이 위치 이동 조작이 없다. 이는 SDK API 부재의 증거가 아니며 실제 이동 지원은 별도 확인한다. |
| D78 | [Compose UI test](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/package-summary), [Instrumentation.sendStatus](https://developer.android.com/reference/android/app/Instrumentation#sendStatus(int,%20android.os.Bundle)) | 2026-10-28 DOC: hasText는 편집 가능한 텍스트도 검색하므로 목록 행 시험에서 hasSetTextAction을 제외한다. sendStatus의 stream 결과를 호스트에 전달해 프로세스 종료 시험의 READY 이후에만 force-stop한다. Logcat 순환이나 수동 도구 호출 지연을 동기화 수단으로 사용하지 않는다. |
| D79 | [Room Transaction](https://developer.android.com/reference/androidx/room/Transaction), [Compose effects](https://developer.android.com/develop/ui/compose/side-effects), [Android14 CarShellCommand](https://android.googlesource.com/platform/packages/services/Car/+/refs/heads/android14-dev/service/src/com/android/car/CarShellCommand.java) | 2026-10-28 DOC+SRC: Room2.7.2 트랜잭션으로 그래프·요청을 함께 저장. LaunchedEffect 키 변경 시 기존 작업 취소·재시작을 반영하고 DB 완료 전 저장 성공을 표시하지 않는다. 공식 이미지의 cmd car_service help 실행은 requires non-user build로 거부됐으며 injected event 명령의 존재와 해당 이미지 접근 권한을 구분한다. |
| D80 | [Node 보안 공지](https://nodejs.org/en/blog/vulnerability/october-2026-security-releases), [24.22.1 릴리스](https://nodejs.org/en/blog/release/v24.22.1), [공식 해시](https://nodejs.org/dist/v24.22.1/SHASUMS256.txt) | 2026-10-29 DOC+BIN: 기존24.12.0을 프로젝트 로컬24.22.1 Windows x64로 패치. bootstrap은 SHA-256 및 ZIP 경로를 검사. 호스트 전역 설치는 변경하지 않음. |
| D81 | [OpenAI client 버전 소스](https://github.com/openai/openai-node/blob/v7.45.0/src/client.ts) / 7.45.0 | 2026-10-29 SRC: 환경 OPENAI_BASE_URL보다 명시적 baseURL 우선. 서버에서 공식 원점 고정, maxRetries=0, timeout55초. 모의 fetch만 사용했으며 실제 계정 호출 증거가 아님. |
| D82 | [HA Entity](https://contributors.home-assistant.io/core/entities/entity-class/), [REST API](https://contributors.home-assistant.io/core/internals/rest-api/) | 2026-10-29 DOC: unknown/unavailable은 유효한 반영 상태가 아니다. climate 현재 entity/range 확인과 변경 후 재조회 계약에 적용. 실제 HA 계정 시험은 미실행. |
| D83 | [SystemClock](https://developer.android.com/reference/android/os/SystemClock), [CarPropertyValue](https://developer.android.com/reference/android/car/hardware/CarPropertyValue) / API34~35 | 2026-10-29 DOC: elapsedRealtime은 deep sleep 포함. Car property timestamp의 경과시계 nanoseconds를 보존하고 continuous speed freshness와 ONCHANGE gear 조회를 구분. 1초 재평가는 제품 정책이며 Android의 깨우기 보장이 아님. |
| D84 | [Room migration](https://developer.android.com/training/data-storage/room/migrating-db-versions), [ActivityScenario](https://developer.android.com/reference/androidx/test/core/app/ActivityScenario) / Room2.7.2, test-core1.6.1 | 2026-10-29~30 DOC+AVD: destructive fallback 없이1→2 migration 등록. 실제 legacy SQLite fixture/재열기, snapshot·event 보존, Activity recreate 시험. |
| D85 | [coroutines Android dispatcher](https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/ui/kotlinx-coroutines-android/src/HandlerDispatcher.kt), [Timeout](https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/kotlinx-coroutines-core/common/src/Timeout.kt) / 1.9.0 | 2026-10-29 SRC: 단순 delay를 절전 포함 시계로 간주하지 않는다. 경과시계 주입 및 단계별 deadline 확인. 외부 timeout과 전체 실행 한도, 사용자 취소를 구분한다. |
| D86 | [Activity task](https://developer.android.com/guide/components/activities/tasks-and-back-stack), [Compose lifecycle](https://developer.android.com/develop/ui/compose/lifecycle), [Instrumentation](https://developer.android.com/reference/android/app/Instrumentation#callActivityOnNewIntent(android.app.Activity,%20android.content.Intent)) | 2026-10-30 DOC+AVD: singleTask의 onNewIntent 전달과 composition identity를 구분. 새 entry로 전체 composition을 폐기하지 않는다. 동일 UI task의 입력/Intent callback으로 저장 전 입력 손실2건 RED→GREEN 확인. |
| D87 | [OSV querybatch](https://google.github.io/osv.dev/post-v1-querybatch/), [Guava 보안 권고](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94), [33.7.2 릴리스](https://github.com/google/guava/releases/tag/v33.7.2) | 2026-10-29~30 DOC+DEP: resolved Maven 좌표를 조회해33.3.1-android의 CVE-2026-102554 확인. 기존 전이 의존성에33.7.2-android constraint 적용. Java 원시 역직렬화 경로 악용 여부와 패키지 취약 여부는 별개. |
| D88 | [Gradle daemon 권고](https://github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64), [worker 권고](https://github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8), [SSL 저장소 권고](https://github.com/gradle/gradle/security/advisories/GHSA-j5m7-59rp-24f5), [8.14.6](https://docs.gradle.org/8.14.6/release-notes.html), [SHA-256](https://services.gradle.org/distributions/gradle-8.14.6-bin.zip.sha256) | 2026-10-30 DOC+BIN: 8.10.2가 영향 범위. 앞의2건은 동일 호스트 접근, 마지막은 저장소 교란·후순위 저장소 제어가 전제. 8.14.6 고정 해시로 패치. 프로젝트 코드 취약점과 빌드 도구 취약점을 구분. |
| D89 | [Corretto17.0.21.0.11](https://github.com/corretto/corretto-17/releases/tag/17.0.21.0.11), [changelog](https://github.com/corretto/corretto-17/blob/17.0.21.0.11/CHANGELOG.md), [공식 해시](https://corretto.aws/downloads/latest_sha256/amazon-corretto-17-x64-windows-jdk.zip) | 2026-10-30 DOC+BIN: Windows x64 ZIP을 공식 SHA-256으로 검증해 .tools에 설치. JDK17.0.21+11-LTS 실행 확인. 최신 해시 endpoint를 설치 시 동적으로 신뢰하지 않고 확인한 버전·해시를 bootstrap에 고정. |
| D90 | [JetBrains 보안 공지](https://www.jetbrains.com/privacy-security/issues-fixed/), [KAPT 수정 소스](https://github.com/JetBrains/kotlin/commit/bf51df665b458fda7c3eaf436c4d88dc119d7ec6), [배포 권고](https://github.com/advisories/GHSA-r937-wjx7-w2jp) | 2026-10-30 SRC: CVE-2026-53914는 Kotlin2.4.20 미만 공지이나 연결된 수정은 KAPT incremental cache 역직렬화다. 현재 프로젝트는 KSP2이며 kapt plugin/task 설정 없음. 이 경로의 도달성 미확인과 취약 버전 존재를 구분하고 빌드 캐시를 신뢰 경계로 남긴다. Kotlin/Compose/KSP 일괄 교체를 임의로 완료 처리하지 않는다. |
| D91 | [Console 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator), [제어 사용법](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide) / Emulator2.1.0 | 2026-10-30 DOC+AVD: 공식8088 forward 및 브라우저 연결 절차 재확인. 준비 없는 최초 probe1PASS/4FAIL 후 동일 APK/동일 판정으로5PASS. SDK 목표 온도·밝기·색상 관찰과 Console 최종 표시 확인. 초기 실패를 앱 코드 수정으로 해결했다고 표현하지 않음. |


## 2026-10-31~11-01 감사 수정의 공식 근거

| ID | 공식 근거 / 버전 | 확인 범위 |
|---|---|---|
| D92 | [Job.kt](https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.9.0/kotlinx-coroutines-core/common/src/Job.kt), [Timeout.kt](https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.9.0/kotlinx-coroutines-core/common/src/Timeout.kt) / coroutines1.9.0 | 10월31일~11월1일 SRC: 협력 취소·Job.isActive와 timeout 경계를 확인. 실행을 소유한 Job이 취소/종료된 경우 승인 증표를 재사용하지 않으며 확인 뒤에도 ensureActive 검사. 실제 시트 교정 증거를 뜻하지 않는다. |
| D93 | [HA REST](https://contributors.home-assistant.io/core/internals/rest-api/), [Google Calendar events.list](https://developers.google.com/workspace/calendar/api/v3/reference/events/list), [Kakao Local](https://developers.kakao.com/docs/ko/local/dev-guide), [what3words API](https://developer.what3words.com/public-api/docs) / 공개 REST, Calendar v3, W3W v3 | 10월31일 DOC: HA 조명/공조 사전 entity 상태와 변경 후 조회, 빈 일정 목록과 잘못된 items 구분, 장소 x/y 문자열 좌표와 W3W lat/lng 숫자 좌표 계약. 실제 계정 호출 없이 IO 모의 시험으로 검증. |
| D94 | [Compose UI test](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/package-summary), [ActivityScenario](https://developer.android.com/reference/androidx/test/core/app/ActivityScenario) / 설치 Compose BOM2024.04.01, test-core1.6.1 | 11월1일 DOC+AVD: assertIsNotEnabled/performScrollTo 및 수동 Activity 수명으로 초안을 보존하는 저장 차단 회귀시험. 문서 최신 페이지를 설치 버전 자체의 증거로 혼동하지 않으며 설치된 API로 실제 컴파일/실행 확인. |

4,096회 노드 방문 한도와 최악 경로 계산은 사용자가 확정한 제품 계약이다. 외부 SDK의 제한으로 출처를 붙이지 않는다. Pleos Vehicle2.3.2 자체 Binder 재연결/대기 명령 큐는 D70의 버전 소스 계약이며 Android Car 연결 세대 관찰과 별개다. 공개 SDK 연결 observer 부재와 실제 재연결 무효화 미검증은 A03의 잠금 해제 전 조건으로 유지한다.


| ID | 공식 근거 / 버전 | 확인 범위 |
|---|---|---|
| D95 | [URLConnection Java17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLConnection.html), [coroutines Builders 1.9.0](https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.9.0/kotlinx-coroutines-core/common/src/Builders.common.kt) | 11월1일 DOC+SRC: 요청 객체/헤더 준비와 실제 IO 구분. IO 진입 뒤 첫 outputStream/responseCode 접근 직전에 승인과 취소를 검사하며 재전송하지 않는다. |
| D96 | [Android 알림](https://developer.android.com/develop/ui/compose/notifications/create-notification), [Media3 Player1.5.1](https://raw.githubusercontent.com/androidx/media/1.5.1/libraries/common/src/main/java/androidx/media3/common/Player.java), [AudioFocusManager1.5.1](https://raw.githubusercontent.com/androidx/media/1.5.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/AudioFocusManager.java) | 11월1일 DOC+SRC: API33+ 알림 권한·채널, Media3 단일 application thread/listener, transient focus suppression·permanent focus loss 계약. Media3가 일시정지/복귀를 관리하며 앱은 강제 재생하지 않는다. TIMER 완료 기록 뒤 알림을 보내며 권한 거부를 물리 실행 실패로 바꾸지 않는다. |
| D97 | [Room migration](https://developer.android.com/training/data-storage/room/migrating-db-versions), 설치 Room2.7.2·기존 schema1/2 | 11월1일 DOC+설치 버전 컴파일: 명시적인2→3 컬럼 추가와1→3 연결 migration, 이전 원인 null 보존. 문서의 최신 Room API를 설치 버전 계약으로 대체하지 않는다. 데이터 보존은 별도 계측시험으로 확인한다. |
| D98 | [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses), [openai-node7.45.0 Responses](https://raw.githubusercontent.com/openai/openai-node/v7.45.0/src/resources/responses/responses.ts) | 11월1일 DOC+SRC: 설명 전용 strict JSON Schema·Responses format, store:false·재시도0·기존 예산 예약. 설치 SDK 소스 및 차단된 모의 transport로 요청 계약 검증. 실제 AI 품질/계정 사용 가능 증거는 아니다. |
