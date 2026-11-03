# Android 원문 승인 계획 준수 감사

감사일: 2026-10-30 KST. 대상: `android/app` 제품 코드·JVM/계측 시험·저장된 증거. 제품 파일 수정, Gradle 실행, 에뮬레이터 변경, 외부 호출은 하지 않았다. 이 파일은 부모 감사 작업의 요청으로 작성한 보고서다. 아래의 “확인”은 소스·기존 시험·로그 대조이며 새 실행 통과를 뜻하지 않는다.

기준은 복원한 [원문 구현 계획](approved-implementation-plan.md)과 [원문 정합성 계획](approved-consistency-plan.md), PRD/설계/TODO/검증계획이다. 축약된 `docs/superpowers/plans/2026-10-29-consistency-tdd-security.md`만으로 완료 여부를 판단하지 않았다. 기존 구현은 Git 미추적 상태이므로 HEAD와의 diff를 구현 전체의 변경 이력으로 취급하지 않았다.

## 판정

“계획을 모두 구현했고 나머지는 실제 환경 검증뿐”이라는 설명은 정확하지 않다. 승인 연계·완료 알림·미디어 및 일부 내비 연결·이력 UI 등은 제품 코드에도 빠져 있다. 반면 시트 실제 이동/STOP, 실제 Gleo 등록, 좌석 점유 유효성, 주행 중 내비/미디어, 실제 AI, 동일 release 10회는 현재 문서가 명시적으로 미완료/차단으로 남긴 항목이다. 이 미검증 게이트를 몰래 삭제한 정황과 코드 누락은 구분해야 한다. 문서와 코드만으로 누락의 의도나 별도 구두 승인을 단정하지 않는다.

시트 전체 실행 전 1회 승인, 열선과 독립 내비, 절전 포함 타이머, KST 예산은 원문에서 승인된 변경이므로 이탈로 분류하지 않았다.

## 확인된 누락·이탈

### A1. P1 — 시트 승인이 연결 세대·규칙 revision/hash와 결합되지 않는다

- 요구: 원문 정합성 계획 54~58행은 공통 runId, revision·의미 해시·시트 계획·연결 세대에 승인을 연결하고 재연결/정의 변경 등에 무효화하도록 명시한다. R08/R10, W13/W18, T22/T24/T31에 해당한다.
- 코드: [RoutineRuntime.kt](../../android/app/src/main/java/com/pleos/routine/execution/RoutineRuntime.kt) 27행의 `Approval`은 제목/문구/Boolean deferred뿐이다. 118~119행은 `authorizeRun`이 받은 runId를 사용하지 않는다. [SeatRunAuthorization.kt](../../android/app/src/main/java/com/pleos/routine/execution/SeatRunAuthorization.kt) 6~14행은 현재 시트 목록과 최초 상태를 확인한 뒤 Boolean으로 승인하며 유효한 승인 증표를 보관·검사하지 않는다.
- 연결 세대는 [VehiclePlatform.kt](../../android/app/src/main/java/com/pleos/routine/vehicle/VehiclePlatform.kt) 75행에서 증가한다. 하지만 승인과 연결하지 않는다. [SeatMotionCoordinator.kt](../../android/app/src/main/java/com/pleos/routine/vehicle/SeatMotionCoordinator.kt) 36행은 MOVE를 시작할 때의 새 세대를 잡으므로 승인 이후 재연결을 감지할 수 없다.
- 반례: 교정 가능한 환경에서 `TIMER/WAIT → MOVE_SEAT_LIMITED` 루틴을 승인한 뒤 대기 중 연결이 끊겼다가 다시 P/0/정상 UX로 돌아오면, 기존 승인으로 새 세대의 MOVE를 시작할 수 있다. Runtime 97행의 안전 guard는 `vehicleEffectsStarted`가 참일 때만 작동하고, 이는 132행의 차량 ACTION에서 처음 설정된다. 승인 완료 뒤 대기 중 재연결을 취소하는 경로가 없다.
- 제한: 현재 제품 `seatPolicy=null`(VehiclePlatform 52행) 때문에 실제 MOVE는 잠겨 있다. 이는 잠금 해제 전 고쳐야 하는 잠재 안전 결함이다. 확인 창이 열린 동안 30초 timeout은 Runtime 84행에 이미 있다. UI가 연결 단절에 따른 UX 제한을 받아 미완료 확인을 취소할 수도 있으므로 “어떤 재연결 중에도 확인 창이 유지된다”거나 “만료 처리 자체가 없다”는 주장은 하지 않는다.
- 시험: [SeatRunAuthorizationTest.kt](../../android/app/src/test/java/com/pleos/routine/execution/SeatRunAuthorizationTest.kt) 14~28행의 3개 시험은 횟수/미교정/초기 주행/거절/시트 없음만 다룬다. 승인 후 재연결·revision/hash 변화·대기 중 조건 상실은 시험하지 않는다. MOVE 도중 세대 변경 시험은 승인 유효성 시험을 대체하지 않는다.

### A2. P2 — TIMER 완료 알림과 실행 중 타이머 표시가 없다

- 요구: 원문 구현 계획 102행은 WAIT와 달리 TIMER에 표시·완료 알림을 요구한다. 원문 정합성 계획 28행도 절전 복귀 시 종료·알림 처리를 명시한다. R09, W14, T28/T44.
- 코드: Runtime 98~100행의 event observer는 Room 저장과 `events` 갱신만 수행한다. RoutineService(같은 파일) 188~195행은 “실행 및 새 이벤트 감시”라는 일반 ongoing notification만 만든다. TIMER 완료 시 별도 notification을 올리거나 기존 notification을 완료 상태로 바꾸는 경로가 없다. MainActivity 266행은 실행 중 마지막 8개 사건 문자열만 표시하며 남은 시간/마감 표시를 제공하지 않는다.
- 반례: 앱을 다른 화면으로 옮겨 30분 TIMER가 끝나도 실행 중지용 일반 알림만 남는다. 기록에는 “타이머 완료”가 생기지만 사용자가 완료 알림을 받지 않는다.
- 시험: [LongTimerTest.kt](../../android/app/src/androidTest/java/com/pleos/routine/LongTimerTest.kt) 71~80행은 완료 상태·1800초·음원 해제·Room만 확인한다. notification이나 실행 중 표시를 검증하지 않는다. elapsed clock 및 1초 만료 재평가 자체는 구현된 별도 항목이다.

### A3. P2 — 표준 미디어 공통 어댑터가 미구현이다

- 요구: 원문 구현 계획 40행은 Android 표준 미디어 연결의 공통 어댑터(재생·일시정지·다음 곡·지원 탐색)를 구현하고 앱별 권한/명령을 검증하도록 한다. R18, W27, T43.
- 코드: Runtime 156행의 `MEDIA_CONTROL`은 항상 BLOCKED를 반환한다. 제품 소스에 `MediaController`, `MediaBrowser`, 외부 `SessionToken` 연결/명령 지원 조회가 없다. 자체 음원의 `ExoPlayer`/`MediaSession`은 다른 앱을 제어하는 클라이언트 어댑터가 아니다. Manifest의 MediaBrowserService query 선언만 있다.
- UI: [Editors.kt](../../android/app/src/main/java/com/pleos/routine/ui/Editors.kt) 57행은 대상 ID만 입력받으며 재생/일시정지/다음 곡 선택도 없다.
- 판정: 외부 계정이나 AVD 지원이 미확인인 것과 별개로 공통 연결 구현이 없다. 실제 주행 허용/앱별 검증 보류는 유지해야 하나 이를 “통합 검증만 남음”으로 설명할 수 없다.

### A4. P2 — 승인된 내비 확장의 일부가 빠져 있고 장소 검색 결과를 목적지로 사용할 UI가 없다

- 요구: 원문 구현 계획 28행은 기본 내비의 경유지·경로 옵션, 45행은 카카오내비 Android 연동과 목적지/경유지 전달, 43~44행은 장소/세 단어 주소를 목적지 좌표로 변환하는 연결을 포함한다. R18/R20, W27/W29, T43/T45.
- 기본 내비: Runtime 167~182행은 단일 목적지 좌표의 NaviHelper `requestRoute`와 `cancelRoute`만 구현한다. Editors 54행은 이름/위도/경도만 받는다. 경유지/경로 옵션을 편집하거나 전달하는 경로가 없다.
- 카카오내비: `android/app/src/main`에서 Kakao SDK/NaviClient/목적지 deep link 어댑터를 찾지 못했다. 일반 OPEN_APP은 앱을 여는 기능이고 목적지/경유지 전달 구현을 대신하지 않는다.
- 부모 요청에 따른 서버 교차 확인: [connectors.ts](../../server/src/connectors.ts) 5~6행은 모든 결과를 `observedValue` JSON 문자열로 감싼다. 34~44행의 PLACE_SEARCH/WHAT3WORDS_RESOLVE는 후보/좌표를 반환한다. Runtime 157~159행은 이를 ActionResult로 반환할 뿐이다. MainActivity 295~300행의 이력은 `observedValue`를 표시하지 않고, Editors 54~55행은 검색과 NAVIGATE 수동 좌표 입력을 분리한다. 검색 결과 목록에서 선택해 NAVIGATE 좌표를 채우는 호출 경로가 없다.
- 결과: 서버 조회가 성공해도 사용자는 앱에서 후보/좌표를 검토·선택해 길 안내에 연결할 수 없다. 장소 검색 결과를 자동 실행에 직결하자는 뜻이 아니라, 검토·선택 UI와 명시적 목적지 반영이 빠졌다는 판정이다.

### A5. P2 — Gleo는 접수 응답만 보내며 최종 결과 응답과 연결되지 않는다

- 요구: 원문 구현 계획 110행은 Gleo 세 진입점을 검토/확인 흐름에 연결하고 긴 작업의 접수와 최종 완료를 구분한다. R16, W25, T41.
- 코드: [MainActivity.kt](../../android/app/src/main/java/com/pleos/routine/MainActivity.kt) 76~84행은 `status=asking` broadcast를 1회 보낸 후 pending 초안을 저장하고 앱을 연다. 이것이 제품 소스의 유일한 `APP_ACTION_RESPONSE` 전송이다. 사용자 검토 완료/거절/실행 결과와 원래 Gleo 요청을 연결해 최종 응답을 보내는 코드가 없다.
- 시험: RuntimeLifecycleTest 48~52행은 schema 파일 존재·동일성만 확인한다. GleoRestoreTest는 로컬 Intent/초안 보존이며 실제 Gleo 서비스의 등록/응답 수신 시험은 아니다.
- 판정: 실제 Gleo 등록/callback은 문서가 명시적으로 남긴 게이트다. 그 외에 최종 응답 경로의 코드 구현도 별도 미완료로 표시해야 한다. `asking` 자체를 잘못된 프로토콜이라고 단정한 것은 아니다.

### A6. P2 — R11 이력의 시작 원인·버전·관찰값·조건 편집 연결이 빠졌다

- 요구: PRD 52/95행은 원인·영역·규칙 버전과 동작별 결과를 보여 주도록 한다. 원문 구현 계획 66행은 미실행 이유에서 해당 조건 편집으로 연결한다. R11/R14/R17, W16/W23/W26, T15/T31/T39/T42.
- 코드: MainActivity 295~300행은 routineId/생성 시각/상태와 nodeId/status/message만 표시한다. 이미 저장된 revision과 event observedValue도 읽어 보여 주지 않는다. snapshot에서 해당 동작의 영역/요청값을 해석하지 않고, 조건 편집으로 이동할 수 없다.
- 시작 원인은 UI만의 문제가 아니다. [RoutineDatabase.kt](../../android/app/src/main/java/com/pleos/routine/data/RoutineDatabase.kt) 11행의 RunRecord에 실행 진입 원인이 없고, [RunJournalRepository.kt](../../android/app/src/main/java/com/pleos/routine/data/RunJournalRepository.kt) 10~13행도 이를 받지 않는다. Runtime 51/71/76/86행은 수동·자동을 동일한 launchRun(w)로 보내므로 동일한 조건 루틴을 “지금 실행”한 것과 조건 진입 실행한 것을 이후 구분할 수 없다.
- 구현된 부분: definition/revision/hash snapshot, 순번 event, 관찰값 보존, 비파괴 migration, UNKNOWN 복구는 존재한다. 이 저장 계층의 장점이 사용자 이력 기능 전체의 완성을 뜻하지 않는다.

## 원문 계획 → 코드/시험 대응표

| 요구/원문 항목 | 코드 및 실제 시험 범위 | 판정 |
|---|---|---|
| R02 실제 기어/속도/연결 유효성 | VehiclePlatform 87~104: AAOS 실제 property·speed source timestamp·온도·UX 조회. PlatformProbeTest, 과거 Console→속도 증거 | 구현/일부 AVD 있음. 음수/NaN/stale/권한 철회/연결 재개 adapter 전체 실패행렬은 앱 시험에 없음 |
| R06/R07 목표 온도·밝기·색상 | VehiclePlatform 128~196: typed SDK·권한·영역·범위·전송 직전 상태·조회 확인. VehicleWriteProbeTest 34~68 | 최신 같은 APK 정상 probe 통과 기록 존재. 초기 실패도 보존. 실패 경로 전체 통과를 뜻하지 않음 |
| R08 시트 2축 MOVE/STOP | VehiclePlatform 199~250, SeatMotionCoordinator. 12개 fake-port 시험은 방향/끝점/취소/연결/지연 ACK 등 | 모의 경계 구현·시험 있음. 실제 교정은 명시 차단. 승인 연계는 A1 누락 |
| R09 타이머·자체 음원 | Runtime 137~147/164~166, LifecycleTest·LongTimerTest | 자체 재생·종료·cancel 및 과거 APK 1800초 증거 있음. A2 알림 누락, 실제 focus 경쟁·deep sleep·새 APK 1800초는 미검증 |
| R10 제한/취소 | Runtime 79/97, app preflight 121~127, MainActivity 182~183 | 일부 구현. 승인 후 대기 중 연결 변경 취소/승인 무효화 누락. 주행 허용 미디어/내비는 근거 전 차단 유지 |
| R11/R19 journal·Room·복구 | Repository, Database MIGRATION_1_2, JournalMigrationTest, RuntimeRecoveryTest | snapshot/순번/기존 행·event 보존 구현. 실프로세스 종료 1개 시점 시험 존재. A6 이력 기능 부족, 단계별 강제종료 전부는 아님 |
| R16 Gleo/초안 | MainActivity 65~87/135~181, PendingRequestStore, EditorRestore, GleoRestoreTest 5개 | 복원/중복/새 요청·늦은 AI 경합 시험 있음. 실제 Gleo 등록과 A5 최종 응답은 별도 미완료 |
| R18 설치 앱 | InstalledApps 11~21, Runtime 148~154, ConnectionDraftTest 14~23 | 실제 설치 목록·해석 가능한 target만 시험. 선택 후 삭제/비활성화 및 startActivity 실패는 미시험 |
| R18 외부 미디어 | Runtime 156 | A3 공통 adapter 미구현. 표준 권한/unsupported command/denial 시험 없음 |
| R18/R20 내비/장소 | Runtime 167~182, Editors 54~55, connector 조회 | 단일 목적지/취소 code 있음. A4 경유지/옵션/카카오내비/검색 선택 누락. 실제 내비 callback 오류·취소·늦은 이벤트 시험 없음 |
| R12 전체 release | latest evidence/implementation/2026-10-29.md 83~89 | 서명/fresh install/동일 release10회 미완료가 명시됨. 완료로 왜곡하지 않음 |

## 원문 정합성 계획이 요구했으나 빠진 시험

원문 73~84행의 “반드시 검증할 사례”와 앱 소스 전체의 `@Test` 및 저장된 실행 목록을 대조했다. 아래는 전체 미검증을 한 개 숫자로 덮지 않고 별도 TODO로 남길 대상이다.

1. 승인: 대기 중 timeout, 승인 후 연결 세대 변경, revision/hash/시트 계획 변화, 취소 후 늦은 확인, 승인 후 조건 상실. 현재 3개 authorization 시험만으로 충족하지 않는다.
2. 공조/조명/열선의 adapter 정상/오류/경계: 권한 철회, area mismatch callback, ACK 후 다른 상태/무변화/조회 timeout, 연결 세대 변경. JVM app 시험은 EditorRestore/ConnectionPolicy/SeatAuthorization/SeatMotion뿐이며 열선 실측 probe도 없다. 기존 실제 차량 probe의 정상 결과와 일부 환경 실패는 전체 행렬을 대체하지 않는다.
3. 음원: 실제/모의 포커스 상실과 재획득·음원 로드 실패·소유 자원만 정리. `setAudioAttributes(..., true)` 사용 자체는 시험이 아니다. LifecycleTest는 숨김 뒤 취소, LongTimerTest는 정상 연속 재생만 다룬다.
4. 앱 연결: 목록 조회 후 target 삭제·비활성화, resolve 후 시작 실패. ConnectionDraftTest는 초기 목록의 `getLaunchIntentForPackage != null`만 검사한다.
5. 내비: 목적지 불일치 이벤트, 오류/시간 초과, 취소 ACK, 늦은 callback과 다음 실행 혼선. 제품 navigation 함수에 대응하는 앱 시험이 없다.
6. 강제종료: 원문은 단계별 종료를 요구하나 실제 RuntimeRecoveryTest는 로컬 음원→타이머 대기 중 READY 한 지점이다. JournalMigrationTest는 직접 삽입한 ACK event를 DB 재개로 확인하는 저장 시험이며 실제 전송/ACK/관찰 각 지점의 강제종료가 아니다.
7. TIMER: 종료 알림·남은 시간 표시·절전 복귀 시 사용자에게 보이는 완료. 현재 시간/이력/음원 정리 assertion만 있다.

차단된 실제 시트/AI/Gleo 시험에 안전하지 않은 대체를 요구하는 것이 아니다. 승인 범위 안에서 순수 로직·가짜 IO 경계 시험을 보완하고 실제 미검증은 별도 상태로 유지해야 한다.

## 기록의 정합성

- 최신 README와 TODO는 전체 제품 미완료를 분명히 하므로 “제품 전체를 완료로 허위 표시했다”는 결론은 아니다.
- 다만 TODO 16행의 “정합성·TDD·보안 개선 — 로컬 검증 완료” 및 축약 계획의 체크 완료만으로 원문 계획의 A1/필수 시험까지 완료됐다고 읽히면 과장이다. 원문 필수 세부 항목을 TODO에 다시 연결해야 한다.
- evidence/implementation/2026-10-29.md 85~88행에 시트/실제 AI/Gleo/외부 계정/점유/주행 허용/실제 deep sleep/최신 APK 30분/release 게이트가 남아 있다. 이들은 이미 공개된 미완료로 분류했다.
- 초기 일괄 “매 이동 확인”은 승인된 “전체 실행 전 1회”로 바뀌었는데 Editors 53행에는 아직 “매 이동 확인” 문구가 남아 있다. 기능 정책 자체의 무단 변경이 아니라 UI 문구 정합성 결함이다.

## 후속 수정 우선순위

1. A1 승인 증표와 실행 상태·연결 세대 무효화, 승인 뒤 조건 재검증을 먼저 설계/실패 시험으로 고정한다. 제품 시트 잠금은 유지한다.
2. A2 완료 알림/표시와 A6 이력 사용성을 구현하고 notification·복구·원인/관찰값 시험을 추가한다.
3. A3/A4/A5의 코드 미구현과 실제 서비스 미검증을 각각 TODO에 분리한다. 기존 승인 범위의 공식 adapter 구현과 모의 시험을 수행하되 계정/유료 호출/제출 승인으로 확대하지 않는다.
4. 원문 필수 시험 목록을 기능별 정상·오류·경계·취소로 복원하고 실제 실행한 증거만 연결한다.
