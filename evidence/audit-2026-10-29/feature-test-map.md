# 기능별 시험 연결

## 2026-11-02 A05 — 11월3일 기록 확정

[A05 최종 장부](../A05/verification-2026-11-02.md)를 우선한다. core98/app64/server50, 공식 AVD26개와 별도 프로세스 복구1개가 통과했다. 아래 표적7개는 전체 AVD26개 안에 포함된다. 실제 AI 자연어 정확도와 실제 차량 제어는 이 시험으로 입증하지 않는다.

| 감사 ID / 요구·시험 | 시험 코드 | 범위 |
|---|---|---|
| A05.1 / R11,R17 / T31,T42 | [RunHistoryTest](../../android/app/src/test/java/com/pleos/routine/data/RunHistoryTest.kt), [JournalMigrationTest](../../android/app/src/androidTest/java/com/pleos/routine/JournalMigrationTest.kt), [RuntimeRecoveryTest](../../android/app/src/androidTest/java/com/pleos/routine/RuntimeRecoveryTest.kt) | 당시 정의·원인·revision/hash, 과거/불일치 snapshot 거부, Room1/2→3 보존, 실제 프로세스 종료 후 UNKNOWN·부분 journal·재전송 없음 |
| A05.2 / R16,R17 / T41,T42 | [run-explanations](../../server/test/run-explanations.test.ts) | strict 요청/응답·run/node/event 근거, BLOCKED 이력, 인증·비활성·중복 예산·공유 동시성, 공식 SDK의 모의 HTTP 출력 계약 |
| A05.3 / R11,R17 / T31,T42 | [RunHistoryUiTest](../../android/app/src/androidTest/java/com/pleos/routine/RunHistoryUiTest.kt) | 가상 snapshot 저장·원인 표시, 과거 조건 복사/최신 저장본 보존, 수동 원인, 지연 조회 후 새 요청 보존 |
| A05.4 / R16,R17 / T41,T42 | [RunExplanationTest](../../android/app/src/test/java/com/pleos/routine/data/RunExplanationTest.kt), [PlannerHttpTransportTest](../../android/app/src/test/java/com/pleos/routine/data/PlannerHttpTransportTest.kt), [RunExplanationUiTest](../../android/app/src/androidTest/java/com/pleos/routine/RunExplanationUiTest.kt) | 엄격한 설명 파싱·잘못된 참조/patch 거부·경로 허용, 복구 이벤트 준비 전 요청 차단, 로컬 HTTP 모의 설명 표시 후 UNKNOWN 기록 불변 |

## 2026-11-01 추가 검증

[현재 검증 장부](../plan-compliance-2026-10-30/verification-2026-11-01.md)를 우선한다. 아래10월29~30일 기록은 과거 APK 기준이다.

| 감사 ID / 요구·시험 | 추가 시험 | 범위 |
|---|---|---|
| A01 / R05,R15,R17 / T16,T40,T42 | [PlanComplianceTest](../../android/core/src/test/kotlin/com/pleos/routine/core/PlanComplianceTest.kt) | 시작 자격, 부분 경과, 확인 뒤 필터,64노드 편집,문구 |
| A02 / R18 / T43 | [connector-results](../../server/test/connector-results.test.ts) | HA 사전 entity, Calendar/Kakao/W3W 잘못된 결과 |
| A03 / R08,R10 / T22,T24 | [SeatApprovalSessionTest](../../android/app/src/test/java/com/pleos/routine/execution/SeatApprovalSessionTest.kt), [PlannerHttpTransportTest](../../android/app/src/test/java/com/pleos/routine/data/PlannerHttpTransportTest.kt) | 실행/정의/교정/세대/취소, HTTP 직전 재검사. SDK 자체 Binder reconnect 미검증 |
| A09 / R04,R15,R17 / T14,T15,T40,T42 | [ExecutionLimitTest](../../android/core/src/test/kotlin/com/pleos/routine/core/ExecutionLimitTest.kt), [server limit](../../server/test/execution-limit.test.ts), [ExecutionLimitUiTest](../../android/app/src/androidTest/java/com/pleos/routine/ExecutionLimitUiTest.kt) |4,096/4,097·반복·병렬·IF·overflow·실행 전 IO0·저장차단/초안보관 |
| A04 / R09 / T28,T29 | [TimerProgressTest](../../android/core/src/test/kotlin/com/pleos/routine/core/TimerProgressTest.kt), [TimerNotificationTest](../../android/app/src/androidTest/java/com/pleos/routine/TimerNotificationTest.kt), [권한 거부](../../android/app/src/androidTest/java/com/pleos/routine/TimerNotificationDeniedTest.kt) | 경과시계·반복/병렬 식별·durable 완료 뒤 알림·WAIT/취소 제외·실제 권한거부 fallback |
| A04 / R09,R10 / T29,T27 | [OwnedAudioTest](../../android/app/src/test/java/com/pleos/routine/execution/OwnedAudioTest.kt), [ActiveResourceFailureTest](../../android/core/src/test/kotlin/com/pleos/routine/core/ActiveResourceFailureTest.kt), [AudioPlatformTest](../../android/app/src/androidTest/java/com/pleos/routine/AudioPlatformTest.kt) | 앱 소유 자원 정리·늦은 실패·실제 Media3 asset실패/통제focus. 다른 앱 실제 경쟁과 구분 |

## 2026-10-29~30 기록

2026-10-29~30 정합성 개선. 기존 구현의 회귀시험 추가와 결함의 RED→GREEN을 구분한다. 소급하여 모든 기존 기능을 TDD로 개발했다고 주장하지 않는다. 아래 UNIT은 실제 차량·공급자 시험을 대체하지 않는다.

| 요구/기능 | 실행한 시험 코드 | 경계와 관찰 |
|---|---|---|
| R02,R05 조건·활성화·cooldown | [SemanticRegressionTest](../../android/core/src/test/kotlin/com/pleos/routine/core/SemanticRegressionTest.kt), [ClockAndActivationTest](../../android/core/src/test/kotlin/com/pleos/routine/core/ClockAndActivationTest.kt) | 결합 조건 전체 진입, 재활성화 때 과거 이벤트 제외, 실제 시작 시 cooldown 소비 |
| R04,R14 편집·패치·Undo/Redo·복제 | [FeatureCoverageTest](../../android/core/src/test/kotlin/com/pleos/routine/core/FeatureCoverageTest.kt), [RuleDescriptionsTest](../../android/core/src/test/kotlin/com/pleos/routine/core/RuleDescriptionsTest.kt), [EditorFlowTest](../../android/app/src/androidTest/java/com/pleos/routine/EditorFlowTest.kt) | 잘못된 패치 원자적 거부, 의미 해시, 연결 유지, 실제 생성→시뮬레이션→저장 |
| R15,R17 그래프·시뮬레이터 | [WorkflowTest](../../android/core/src/test/kotlin/com/pleos/routine/core/WorkflowTest.kt), [FeatureCoverageTest](../../android/core/src/test/kotlin/com/pleos/routine/core/FeatureCoverageTest.kt), [ExecutionSafetyTest](../../android/core/src/test/kotlin/com/pleos/routine/core/ExecutionSafetyTest.kt) | 모든15개 ActionType, IF/WAIT/TIMER/REPEAT/PARALLEL/JOIN, 64노드·4분기·20반복·7200초 경계, 구조 범위 침범·자원 충돌·가상/실제 분리 |
| R20 조건부 열선·독립 내비 | [SemanticRegressionTest](../../android/core/src/test/kotlin/com/pleos/routine/core/SemanticRegressionTest.kt) | 미선택 열선 capability 검사가 내비를 막지 않음. 일반 병렬 실패의 형제 취소 유지 |
| R08,R10 시트 전체 실행 승인 | [SeatRunAuthorizationTest](../../android/app/src/test/java/com/pleos/routine/execution/SeatRunAuthorizationTest.kt), [JournalContractTest](../../android/core/src/test/kotlin/com/pleos/routine/core/JournalContractTest.kt) | 모든 좌석/축 계획을 실행마다1회 확인, 미교정/주행/취소 시 첫 부작용도 없음 |
| R08,R10 MOVE/STOP | [SeatMotionCoordinatorTest](../../android/app/src/test/java/com/pleos/routine/vehicle/SeatMotionCoordinatorTest.kt) | 양방향, 끝점/역방향 관찰, 최종 상태 변경, 지연 MOVE/STOP ACK, 느린 조회, 취소, 연결 변경, 이전 journal 보존. fake port UNIT이며 제품 seatPolicy=null 유지 |
| R11,R19 실행 이력·복구 | [JournalContractTest](../../android/core/src/test/kotlin/com/pleos/routine/core/JournalContractTest.kt), [JournalMigrationTest](../../android/app/src/androidTest/java/com/pleos/routine/JournalMigrationTest.kt), [RuntimeRecoveryTest](../../android/app/src/androidTest/java/com/pleos/routine/RuntimeRecoveryTest.kt) | runId/순번/관찰값, 저장 실패 시 병렬 후속 쓰기 차단, 비파괴 migration, 정의 snapshot·단계 보존, 프로세스 재시작 UNKNOWN·재실행 금지 |
| R09 타이머·자체 음원 | [ClockAndActivationTest](../../android/core/src/test/kotlin/com/pleos/routine/core/ClockAndActivationTest.kt), [RuntimeLifecycleTest](../../android/app/src/androidTest/java/com/pleos/routine/RuntimeLifecycleTest.kt) | 가짜 절전 경과시계의1초 재평가, AVD 화면 이탈/음원·타이머/취소 이력. 실제 deep sleep 또는 새 APK30분 시험과 구분 |
| R16 Gleo 초안 | [EditorRestoreTest](../../android/app/src/test/java/com/pleos/routine/data/EditorRestoreTest.kt), [GleoRestoreTest](../../android/app/src/androidTest/java/com/pleos/routine/GleoRestoreTest.kt), [ConnectionDraftTest](../../android/app/src/androidTest/java/com/pleos/routine/ConnectionDraftTest.kt) | 요청 최초1회 반영, 최신 수정 보존, 재생성, 저장 전 새Intent, CONTROL 미보관·미실행 |
| R18 앱·연결 선택 | [ConnectionUiTest](../../android/app/src/androidTest/java/com/pleos/routine/ConnectionUiTest.kt), [ConnectionDraftTest](../../android/app/src/androidTest/java/com/pleos/routine/ConnectionDraftTest.kt), [ConnectionPolicyTest](../../android/app/src/test/java/com/pleos/routine/data/ConnectionPolicyTest.kt) | 실제 설치 앱의 해석 가능한 target, 연결 화면, HTTPS 원점과 debug loopback 제한 |
| R03,R16,R18 서버·AI·커넥터 | [server.test.ts](../../server/test/server.test.ts), [security-regressions.test.ts](../../server/test/security-regressions.test.ts) | 비활성 무호출, DTO/graph/patch, KST 일·월/UTC이관·재시작 잠금, 공식 SDK 원점, HA 상태/범위, 응답 한도, 인증·Origin·MIME·본문·rate/stream |
| R01,R13 로컬 설치 안전 | [test_bootstrap_node.py](../../scripts/test_bootstrap_node.py) | SHA 불일치·경로 탈출·절대경로·심볼릭링크 거부, Node/JDK 지정 root만 추출 |

최신 APK의 공조·조명은 Console 연결 복구 후5개 probe 재시험에서 통과했다. 최초1PASS/4FAIL과 재시험5PASS를 모두 [이번 장부](../implementation/2026-10-29.md)에 남겼다. [10월28일 장부](../implementation/2026-10-28.md)의30분 타이머는 별도 APK 증거다. 시트 이동/STOP, Gleo 서비스 등록/callback, 외부 계정 연결, 실제 AI 평가, release 전체10회는 각각 별도 게이트이며 이번 모의 시험으로 대체하지 않는다.
