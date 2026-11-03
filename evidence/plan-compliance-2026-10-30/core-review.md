# Core 계획 준수 감사 — 2026-10-30 KST

범위: `android/core` 전체 구현·시험, workflow 모델/공유 schema/fixture, 앱의 core 호출부. 승인 원문은 [최초 구현 계획](approved-implementation-plan.md), [정합성 개선 계획](approved-consistency-plan.md)이다. 제품 소스는 변경하지 않았다. Gradle·전체 시험은 실행하지 않았다. 아래 재현은 이미 존재하는 compiled core classes를 JShell로 호출한 별도 로컬 진단이며 공식 AVD 증거가 아니다. 코드 읽기로 재현 원인도 대조했다.

## 확인된 누락·불일치

### C01 / P2 / R17·W26·T42 — 시뮬레이션이 시작 조건을 검사하지 않는다

- 승인 원문 `approved-implementation-plan.md:65`: 가상 시간·온도·탑승 상태로 실행 경로를 보여주기로 했다. `:63`은 시작 조건과 전체 조건·특정 동작 조건을 구분한다.
- `android/core/src/main/kotlin/com/pleos/routine/core/Engine.kt:163-169`: `WorkflowSimulator.simulate`는 engine을 바로 실행한다. `Engine.kt:130-135`는 `trigger.filters`만 평가하고 본문을 시작한다. `trigger.condition`, `SEAT_OCCUPIED`의 점유값, `TIME`의 시각·요일을 평가하지 않는다.
- 실제 트리거는 `Conditions.kt:49-67`에서 별도로 평가되므로 시뮬레이터와 자동 실행 의미가 다르다.
- 재현: 평일 08시·12°C·점유 false에서 출근 fixture는 `COMPLETED [navigation]`. `CONDITION_ENTER outside <= 5`를 12°C로 시험하면 조건 자체는 `FALSE`인데 `COMPLETED`, TIMER를 실행한다.
- 앱 `MainActivity.kt:253-255`는 이 결과를 “이 상황으로 테스트”, “가상 경과”로 표시한다. 수동 실행을 선택한다는 별도 입력은 이 경로에 없다.
- **수정 시 구별 필요:** 실제 수동 시작이 트리거 이벤트를 무조건 요구하도록 바꾸는 것은 다른 의미 변경이다. 시뮬레이션에서 자동 조건 적격성과 수동 실행을 명시적으로 구분해야 한다. 정적인 점유 true가 실제 false→true 이벤트 발생을 증명한다는 주장도 피해야 한다.
- 누락 시험: 점유 false/UNKNOWN, CONDITION_ENTER false/UNKNOWN, TIME 시각·요일 불일치, 해당 경우 실제 IO 0건 및 미실행 이유. 기존 `WorkflowTest.kt:19-29`는 온도에 따른 본문 IF만 검사한다.

### C02 / P2 / R17·W26·T40/T42 — 중간에 차단된 시뮬레이션의 총 경과시간이 0으로 돌아간다

- `Engine.kt:28`에서 총 elapsed=0으로 시작하고 `:135`에서 `walk`가 정상 반환한 경우에만 갱신한다. `:157-158`은 예외/차단 때도 그 값을 반환한다.
- 재현: WAIT 7200초 → TIMER 1초. WAIT 완료 event는 7,200,000ms, TIMER는 제한 초과로 BLOCKED인데 `RunResult.elapsedMillis=0`이다. UI `MainActivity.kt:255`는 “가상 경과 0초”를 표시한다.
- `FeatureCoverageTest.kt:297-306`에 바로 이 경로가 있지만 상태·노드만 검사하고 총 경과시간은 검사하지 않아 결함을 놓친다. 병렬의 한 분기가 먼저 진행한 뒤 다른 분기에서 차단되는 경우도 같은 결과 요약 문제를 검사해야 한다.
- 이는 실제 타이머의 elapsedRealtime 동작이 틀렸다는 주장이 아니라 **실패/차단 시뮬레이션의 결과 요약 결함**이다.

### C03 / P2 / R14·R15·W23/W24·T39/T40 — 편집기의 실제 상한이 승인한 64노드와 다르다

- 승인 원문 `approved-implementation-plan.md:99` 및 `Validation.kt:10`: 64노드 허용.
- `GraphEditing.kt:8`은 삽입할 fragment 크기와 관계없이 기존 노드 수 `<60`을 요구한다. 60개짜리 유효 그래프에서 단일 WAIT를 추가해 61개로 만드는 것도 거부한다. 모든 삽입 경로에서 64개까지 편집하는 것이 불가능하다.
- 재현: 기본 2노드에 WAIT 58개 삽입 → 60노드, validator 오류 0. 다음 WAIT 삽입은 `IllegalArgumentException: Failed requirement.`.
- `FeatureCoverageTest.kt:236-247`의 64개 시험은 리스트를 직접 만들어 engine에 전달하므로 실제 편집 경로의 축소된 한도를 검사하지 않는다. fragment의 결과 노드 수를 기준으로 각 종류의 경계 시험이 필요하다.

### C04 / P3 / R08·R14·W23 — 사용자 설명에 폐기한 시트 확인 정책이 남아 있다

- 확정 원문 `approved-consistency-plan.md:25`: 전체 실행 전에 한 번 확인, 다음 실행에 재확인.
- `RuleDescriptions.kt:34`는 `MOVE_SEAT_LIMITED` 설명을 여전히 “매 이동 확인”이라고 한다. 실행 정책 자체와 분리된 사용자 설명 불일치다.
- `RuleDescriptionsTest.kt`에는 새 정책 문구를 검증하는 시험이 없다.

## 추가 추적·시험 공백

- **조건·트리거 시험:** `approved-consistency-plan.md:75`는 센서 갱신 순서와 동시 후보를 반드시 검증하도록 한다. `SemanticRegressionTest.kt:18-28`는 기본 기어 조건이 이미 true인 상태에서 점유 필터가 마지막으로 true가 되는 한 순서만 검사한다. 기어/속도 양쪽 순서와 UNKNOWN 끼어들기 조합은 없다. core test tree에는 `TriggerType.TIME` 시험 자체가 없다. 시각 트리거의 초기 해당 분·지나간 이벤트·요일·필터 변화 정책은 조건 TIME_WINDOW 시험으로 대체되지 않는다. 동시 후보 우선순위/충돌은 앱 소유이므로 앱 감사와 합쳐 판단해야 한다.
- **미실행 설명:** `approved-implementation-plan.md:66`은 미실행 이유에서 해당 조건 편집으로 이어지도록 한다. `Engine.kt:71-76`은 방문한 IF의 FALSE 이유만 기록하며 선택하지 않은 노드의 `SKIPPED`/차단 조건 연결은 기록하지 않는다. 재현에서 warm 출근의 heat event=0, SKIPPED event=0이다. UI가 이를 별도로 유도하지 않는다면 설명·편집 연결이 미완성이다. 최종 UI 판정은 앱 감사에 넘겼다.
- **추가 실행 한도:** `Engine.kt:64`에 4096 node-visit 상한이 있다. 승인 원문과 현재 README의 64노드/4분기/20반복/2시간 설명에는 없다. 방어 상한 자체를 제거하라는 결론은 아니지만, 중첩 반복에서 각 블록이 20회 이하·총 시간이 2시간 이하라도 이를 초과할 수 있으므로 제품 계약·사전 검증·오류 설명·경계 시험에 연결해야 한다. 현재 `rg` 기준 4096 상한 전용 시험이 없다.
- **전체 승인 후 조건 재검사:** `Engine.kt:130-135`는 filter 검사 → 사용자 대기 가능한 authorizeRun → walk 순서이고, 대기 이후 filter를 다시 검사하지 않는다. `RoutineRuntime.kt:118-120`, `SeatRunAuthorization.kt:6-15`에서도 시작 조건 재검사를 발견하지 못했다. 시작 조건이 확인 대기 중 해제되는 사례는 앱 감사자가 판단하도록 전달했다. 물리 MOVE 직전 주차 검사와 사용자 지정 trigger predicate 재검사는 별개다.
- **공유 계약의 시험 범위:** `WorkflowTest.kt:9-15`는 단일 commute fixture decode/validate/simulate 계약을 확인한다. 모든 ActionType 정상/오류 계약은 `FeatureCoverageTest.kt:36-132`에서 core 검증·모의 실행만 확인한다. 이는 실제 외부 연결·차량 SDK 검증이 아니다. `feature-test-map.md`의 UNIT 구분은 이 제한을 대체로 정확히 표시한다.

## 승인 항목 → 실제 코드·시험 대조

| 승인 항목 / ID | 코드 | 시험·확인 범위 | 판정 |
|---|---|---|---|
| 결합 CONDITION_ENTER / R05 W12 | `Conditions.kt:56-67` | `SemanticRegressionTest.conditionEnterFiresWhenLastFilterBecomesTrue` | 구현 있음. 센서 순서 조합 시험 부족 |
| 활성 집합 변경·재활성화 시 과거 이벤트 제외 | `Conditions.kt:41-46` | `ClockAndActivationTest.disabledEventsAreNeverReplayedOnActivation` | 해당 모의 경로 있음 |
| 실제 시작 cooldown·새 edge / R05 | `Conditions.kt:46,66`, 앱 `RoutineRuntime.kt:89` | `ExecutionSafetyTest.kt:140-155`, discardedCandidate 시험 | 300초 정확한 경계와 재시작 초기 true 검사 있음. 실제 시작 호출 시점/거절 정책은 앱 범위 |
| 선택된 동작 직전 preflight / R20 W29 | `Engine.kt:77-87` | `SemanticRegressionTest.kt:42-86` | 미선택 열선 preflight 회피, cold heat 실패 전에 NAV 완료 있음 |
| 독립 NAV 후 조건부 heat | `Examples.kt:5-13` | `WorkflowTest.kt:19-29`, SemanticRegressionTest | 확정 원문 방식과 일치. 실제 주행 허용 NAV는 별도 미검증 게이트 |
| 일반 병렬 실패 시 형제 취소 | `Engine.kt:109-113` | `ExecutionSafetyTest.kt:111-137` | 취소 이후 cleanup 순서 시험 있음 |
| 구조화 IF/WAIT/TIMER/REPEAT/PARALLEL/JOIN | `Validation.kt:32-109`, `Engine.kt:68-114` | `WorkflowTest`, `ExecutionSafetyTest`, `FeatureCoverageTest` | 구조·중첩·합류·자원 충돌 모의 시험 있음 |
| 64/4/20/2시간 상한 | `Validation.kt:10,41,45,60`, `Engine.kt:64,91` | `FeatureCoverageTest.kt:236-306` | 엔진 경계 시험 있음. 실제 편집 C03, partial 시간 C02, 추가4096 한도는 별개 |
| 원자 패치·기준 revision·Undo/Redo·의미 해시 | `Workflow.kt:97-182` | `FeatureCoverageTest.kt:344-448` | 정상·실패 원본 보존·redo 폐기·의미 변화 시험 있음 |
| 전체 run 승인 전에 부작용 없음 | `Engine.kt:134` | `JournalContractTest.kt:20-24` | engine hook 순서만 입증. run/revision/hash/연결 세대 승인 결합은 앱 구현 별도 감사 |
| 절전 포함 공통 시계·1초 재평가 | `Engine.kt:90-102,121-123`; 앱 `RoutineRuntime.kt:98` elapsedRealtime 주입 | `ClockAndActivationTest.kt:15-24`, `JournalContractTest.kt:37-47` | 가짜 절전·timeout 분류 시험 있음. 실제 deep sleep 증거를 대체하지 않음 |
| 순번 journal·관찰값·실패 시 후속 부작용 차단 | `Engine.kt:30-39,80-84,145-158` | `JournalContractTest.kt:15-69` | 요청 전 저장 실패·순번·취소 terminal·병렬 형제 차단 있음. Room/프로세스 복구는 앱 범위 |
| 실제/가상 입력 분리 / R17 | `Engine.kt:42-48,163-169`, `ExecutionContract.kt:13-15` | `WorkflowTest.simulationCannotReachRealAdapter` 및 FeatureCoverage의 SIMULATED != VERIFIED | 전용 simulator는 fake adapter만 보유. 시작 조건 시뮬레이션은 C01 |

## 재현 명령과 실제 출력

스크립트: [core-readonly-repro.ps1](core-readonly-repro.ps1). 원본 출력: [core-readonly-repro.log](core-readonly-repro.log).

```powershell
& ./evidence/plan-compliance-2026-10-30/core-readonly-repro.ps1 |
    Tee-Object -FilePath evidence/plan-compliance-2026-10-30/core-readonly-repro.log
```

실제 출력에서 JShell prompt만 제외한 부분:

```text
OCCUPIED_FALSE=COMPLETED [navigation]
WARM_HEAT_EVENTS=0 SKIPPED_EVENTS=0
FALSE_CONDITION_TRIGGER=FALSE RESULT=COMPLETED SIMULATED_NODES=[timer]
PARTIAL_ELAPSED=0 MAX_EVENT_ELAPSED=7200000 STATUS=BLOCKED
EDITOR_NODES=60 VALIDATION_ERRORS=0
EDITOR_ADD_61=REJECTED Failed requirement.
```

미확인 제품 게이트와의 구별: 실제 AI 평가, 시트 두 축 이동/STOP 교정, 실제 점유·주행 내비/미디어, 동일 release 전체10회는 이미 문서에 남아 있는 미완료이다. 이 감사가 새로 찾은 C01~C04 및 시험 공백을 이 기존 게이트와 섞지 않는다. 기존 core71PASS는 과거 시험 결과이며 이번 감사에서 새로 재실행했다고 주장하지 않는다.
