# AI 루틴 앱 기술 설계와 구현 계약

기준: **2026-10-25 KST**. 아래 디렉터리·타입·API는 제품 내부의 제안 설계다. 실제 앱·서버 코드는 아직 없다. Pleos SDK의 정확한 함수·단위·권한은 [기능 표](05_CAPABILITY_MATRIX.md)를 따른다. 요구사항 ID는 [PRD](03_PRODUCT_PRD.md), 실행 순서는 [TODO](13_IMPLEMENTATION_TODO.md)가 기준이다.

## 현재 구조와 향후 구조

현재: 루트 Markdown 문서와 `references/`의 공식 양식·메모. Android 프로젝트·서버·빌드 설정·Git 이력은 없다.

아래는 후속 개발 시 생성할 제안 구조이며 지금 만들어진 파일이 아니다. 처음에는 Android 단일 app 모듈 안의 패키지로 책임을 나누고, 근거 없이 멀티모듈·범용 플러그인 프레임워크를 추가하지 않는다.

```text
android/                  # 향후 Gradle 프로젝트, Wrapper 포함
  app/src/main/.../
    composer/             # 입력, 초안·질문, 편집
    domain/               # 규칙·검증·트리거·실행 상태 (플랫폼 분리)
    data/                 # 로컬 규칙·이력 저장
    vehicle/              # AAOS/Pleos 상태·capability·제어
    execution/            # 직렬 실행, STOP, 타이머·음원
    ui/                   # 루틴·확인·실행·이력·연결 화면
  app/src/test/.../        # 가짜 시계·어댑터로 순수 로직 시험
  app/src/androidTest/.../# 저장·수명·UI 계측 시험
server/                   # 향후 생성 서버; 공급자·런타임 결정 후 생성
  contracts/              # API 및 JSON 스키마의 단일 정의
  tests/                  # 계약·오류·AI 평가
evidence/                # 향후 실제 증거; 파일명은 07·13의 규칙 준수
```

코드 경로는 소스 분석 후 확정하고 TODO에 기록한다. Kotlin·공식 샘플의 Compose 구성을 UI 출발 후보로 삼되 저장·네트워크·직렬화 라이브러리 및 서버 런타임은 사용자의 새 의존성 승인 규칙과 공식 호환성 검토를 거쳐 결정한다. 결정 전 임의 버전을 구현 필수값으로 쓰지 않는다. [공식 샘플](https://github.com/Pleos-Playground/pleos-sample-vehicle-app)

## 데이터 흐름과 책임

```mermaid
flowchart TD
    U[텍스트 요청과 보충 답변] --> P[생성 서버와 선택한 AI]
    P --> V[로컬 구조·지원 기능 검증]
    V --> D[검토·수정·저장]
    D --> R[규칙 저장소]
    S[AAOS/Pleos 상태와 capability] --> E[트리거·현재 상태 검사]
    R --> E
    E --> C[시트 포함 시 이번 실행 확인]
    C --> X[직렬 실행과 STOP 우선 처리]
    X --> A[공조·조명·시트·타이머·음원]
    A --> J[응답·관찰 상태·결과 기록]
    J --> UI[진행 화면과 이력]
```

| 경계 | 입력 → 출력 | 책임과 제한 |
|---|---|---|
| PlannerClient/서버 | PlannerRequest → PlannerResponse | 생성만 수행. 차량 SDK·셸·임의 도구 호출 없음 |
| RoutineValidator | 초안+제품 정책+현재 지원 정보 → 검증 결과 | 타입·enum·값·영역·지원 여부 검증, 추가 필드/코드/URL 거부 |
| RoutineStore | 승인한 규칙 → 버전 있는 StoredRoutine | 편집 시 새 버전. AI가 id·enabled·실행 승인을 부여하지 않음 |
| VehicleStateAdapter | AAOS/Pleos 읽기 → VehicleSnapshot/변경 이벤트 | 값·단위·상태·관찰시각·데이터 출처 포함 |
| CapabilityRegistry | 연결+권한+API 조회 → 기능별 CapabilitySnapshot | 기능/영역/접근/범위 구분, 연결·권한 변경 시 무효화 |
| RuleEngine | 규칙+상태+시계+트리거 → Blocked 또는 실행 후보 | 외부 쓰기 없이 조건·중복·충돌 판단 |
| VehicleControlAdapter | 검증된 typed command → 응답/관찰 결과 | 공조·조명·시트별 정확한 SDK 호출, 매 호출 영역·범위 확인 |
| ActionExecutor | 승인된 실행 계획 → 동작별 결과 | 단일 루틴·단일 시트 축, STOP 우선, 지연 응답 처리 |
| ExecutionJournal | run/action 이벤트 → 로컬 이력 | 요청/응답/관찰/중단 원인, 버전·상관 ID 기록 |

위 명칭은 내부 설계이며 SDK에 같은 이름의 클래스가 있다고 가정하지 않는다. API 34 실행 환경에서 존재하는 AAOS API를 확인하고, 최신 문서에만 있는 구독 메서드를 버전 검사 없이 호출하지 않는다. [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager)

## 생성 서버 계약

내부 제안 경로: `POST /v1/routine-drafts`. 서버는 공급자 SDK를 감추는 `RoutinePlanner.generate()` 경계를 가진다. 공급자·런타임·인증·호스팅·요금 한도는 별도 결정 작업이며 아직 미정이다.

- 요청: `requestId`, `locale`, `requestText`, `capabilitySummary`, 선택적인 이전 초안·보충 답변. 차량 VIN·정확한 위치·속도 이력·오디오 원본은 전송하지 않는다.
- 응답: `schemaVersion`, `status`, `draft`, `questions`, `unsupportedRequests`. `status`는 `draft`, `needs_clarification`, `unsupported`만 허용한다.
- `draft`: 생성할 제목·트리거·시간 조건·typed actions. `needs_clarification`에서는 질문에 답하기 전 저장 불가. `unsupported`에서는 실행 가능한 전체 초안으로 표시하지 않는다.
- 혼합 요청: 가능한 초안과 지원 불가 항목을 함께 표시한다. 일부만 저장하는 경우 사용자가 명시적으로 선택한 새 규칙으로 저장하고 원문과 빠진 항목을 보존한다.
- 모델 거부·JSON 오류·타임아웃·요금 한도·인증 실패는 공급자별 오류를 앱의 오류 코드로 매핑한다. 자동 재시도 때문에 중복 초안이나 무제한 비용이 생기지 않도록 상한을 결정·시험한다. 생성 요청 재시도는 차량 실행 재시도와 분리한다.
- 실 API 경로와 테스트 fixture를 UI·로그에서 구분한다. 네트워크 실패 때 템플릿을 AI 생성 성공으로 보여주지 않는다. 저장된 규칙 실행은 네트워크에 의존하지 않는다.

HTTP 인증·요청 크기/시간·동시성·보존기간·비용 상한은 공급자 선정 기록에 수치와 공식 근거를 남긴 후 서버 구현을 연다. 외부에 인증 없는 생성 서버를 공개하는 것을 기본값으로 삼지 않는다. 키는 서버에서만 관리한다.

## 내부 루틴 모델

다음은 생성 응답 안의 `draft` 예시다. 모든 숫자·색·영역은 희망값 예시이며 capability 검증 전 실행 가능한 값이 아니다. 실제 승인·실행 권한 필드는 모델 출력에 포함하지 않는다.

```json
{
  "title": "점심 휴식",
  "trigger": {"type": "MANUAL"},
  "conditions": {
    "timeWindow": {"from": "13:00", "to": "15:00", "zone": "Asia/Seoul"},
    "requireParkedAndStationary": true
  },
  "actions": [
    {"type": "SET_HVAC_TARGET", "area": "DRIVER", "celsius": 23.0},
    {"type": "SET_AMBIENT_BRIGHTNESS", "area": "DRIVER", "requestedPercent": 20},
    {"type": "SET_AMBIENT_COLOR", "area": "DRIVER", "argbHex": "#FF0000FF"},
    {"type": "MOVE_SEAT_LIMITED", "area": "DRIVER", "axis": "FORE_AFT", "direction": "BACKWARD"},
    {"type": "MOVE_SEAT_LIMITED", "area": "DRIVER", "axis": "BACKREST", "direction": "BACKWARD"},
    {"type": "PLAY_LOCAL_AUDIO", "assetId": "licensed-rest-01"},
    {"type": "START_TIMER", "durationSeconds": 1800}
  ]
}
```

`DRIVER`는 운전석을 뜻하는 제품 내부 라벨이며 SDK의 고정 숫자가 아니다. 대상 AVD의 좌석 정의·지원 영역을 확인해서 매핑한다.

제품 검증기 규칙:

- 시간 구간은 같은 날짜의 `[from,to)`와 명시적 시간대. 자정 횡단·중첩 OR·임의 스크립트는 첫 버전에서 거부하고 설명한다.
- 위 7개 동작이 한 루틴에 들어갈 수 있도록 **최대 7개**로 둔다. 같은 기능·영역 또는 같은 시트 축의 중복 동작은 저장 전 해결한다. 기존 최대 5개 제안은 폐기한다.
- 타이머 60~3600초, 음원은 앱의 허가된 assetId만. 공조·밝기·좌석 범위는 차량 capability에서 얻는다.
- 밝기의 percent는 제품 요청값이다. SDK 원시값을 항상 0~100으로 간주하지 않는다. 대상 AVD에서 단위·스케일을 검증한 변환만 허용하며 미확정이면 저장/실행을 차단한다.
- 색상은 불투명 ARGB 32비트로 변환한다. 지원 색 범위와 반영값을 확인한다. 임의 문자열·URL·float를 색상 SDK 값으로 넘기지 않는다.
- 시트는 축·방향만 초안에 담는다. 이동시간·속도·목표각도·거리·승인 토큰을 AI가 지정할 수 없다. 검증된 앱 실행 정책을 적용한다.
- `requireParkedAndStationary=false`는 실행 허용으로 해석하지 않는다. 사용자에게 제품 제한을 설명하고 새 초안을 확인받는다.

저장 시 앱이 `schemaVersion=1`, `routineId`, `version`, `enabled=false`, `scope=ACTIVE_APP_SESSION`과 원문/승인 기록을 부여한다. 앱 시작마다 실행 세션은 비활성으로 복구하고 사용자가 다시 활성화한다. 저장된 시트 동의는 다음 실행의 동의로 재사용하지 않는다.

## 상태·지원 정보와 미확정 정책값

VehicleSnapshot은 gear/speed의 `value`, `unit`, SDK 상태, 수신 단조시각, 원본 시각(있으면), 연결 세대, 입력 출처를 가진다. property 목록에서 사라졌다는 이유만으로 미지원이라고 단정하지 않는다. 권한 부족·일시 불가·UNKNOWN·ERROR를 구분한다.

초기 시험 후보는 P단과 `abs(speed)<0.1m/s`, 속도 freshness 2초다. 이는 기존 제품 가설이지 공식 보장이나 안전 인증 수치가 아니다. 상태 이벤트 주기와 정차 노이즈를 계측하고 아래 정책표를 확정하기 전 차량 쓰기 게이트를 열지 않는다. 기어 ONCHANGE의 오랜 무변화를 오류로만 판단하지 말고 실행 직전 snapshot 재조회로 유효성을 확인한다.

| 미확정 정책 | 결정에 필요한 증거 | 확정 전 처리 |
|---|---|---|
| 속도 임계·freshness | 실제 AAOS 주기·시각·정차/움직임 값 | 쓰기 실행 차단 |
| 시트 축별 제한 이동시간 | 한 축 최소 이동·위치 관찰·STOP 응답/정지 관찰 시간 | 제품 이동 비활성 |
| 상태 반영·STOP 확인 timeout | 응답 지연과 위치 안정화 관찰, 반복 시험 | 성공 추정 금지 |
| 시트 경계·방향 매핑 | 영역별 bounds와 방향 이동 후 위치 변화 | 방향/단위 추정 금지 |
| 승인 대기 만료·재검사 시점 | UX 과제, 대기 중 상태/규칙 변경 | 확인 시 전 조건 재검사 |

최초 측정은 [검증 계획](07_VALIDATION_AND_DEMO.md)의 W05 에뮬레이터 전용 스파이크 절차로 수행한다. 제품 한도 확정 전에는 제품 자동 이동을 열지 않되, 별도 기록한 임시 실험 상한과 수동 STOP 경로로 계측하는 단계는 구분한다. 측정 결과와 선택 이유는 버전 있는 로컬 정책과 결정 기록으로 남긴다. 상한을 크게 늘려 실패 시험을 통과시키지 않는다. 실제 속도 주입 및 시트 위치 이벤트가 AVD에서 재현되지 않으면 해당 검증을 미실행/차단으로 남긴다.

## 트리거·동시성·실행 순서

- MANUAL은 명시적 시작, CONDITION_ENTER는 동일 활성 세션의 false→true 전환 1회다. 초기 상태가 true이면 자동 실행하지 않는다. 불명 상태에서 true가 된 것을 정상 false→true로 오인하지 않는다.
- CONDITION_ENTER의 재실행 cooldown은 기존 설계의 300초를 제품 기본값으로 유지한다. 공식 플랫폼 제한이 아니다. 실행 시작 단조시각부터 계산하며, 시간 경과만으로 재실행하지 않고 새로운 false→true 전환을 요구한다. MANUAL은 이 시간 자동화 규칙과 별개지만 활성 run과 충돌하면 차단한다.
- 세션 비활성 중 놓친 트리거는 재생하지 않는다. 시트 확인 거절/만료 시 다음 실제 false→true 전환까지 재무장하지 않는다. 수동 시작은 별도로 가능하다.
- 동시에 한 루틴만 실행한다. 다른 트리거는 자동 큐에 쌓아 나중에 제어하지 않고 충돌로 차단·기록한다.
- 승인 토큰은 `routineId/version/runId/seatPlan/connectionGeneration`에 묶고 일회용으로 소비한다. 변경·새 연결·취소 시 무효다.
- 기본 순서: 전체 preflight → 시트 포함 시 사용자 확인 → 전체 preflight 재검사 → 공조 → 조명 밝기 → 색상 → 시트 앞뒤 이동/STOP 확인 → 등받이 이동/STOP 확인 → 음원 → 타이머 시작.
- 각 동작 직전 상태를 재검사한다. 시트 하나가 결과 불명이라면 다음 시트 축과 나머지 동작을 시작하지 않는다. STOP은 같은 직렬 작업의 끝까지 기다리지 않는 우선 경로로 보낸다.
- 타이머 시작은 즉시 등록하고 큐를 점유하지 않는다. 30분 경과는 단조시계 기준이다. 물리 제어값의 자동 원복은 없으며 시간 종료 시 앱 소유 음원과 세션만 정리한다.

## 실행 상태와 취소 계약

규칙 편집 상태와 실행 상태를 분리한다. 실행은 `PREFLIGHT → AWAITING_CONFIRMATION(해당 시) → RUNNING → COMPLETED/CANCELLED/BLOCKED/FAILED/UNKNOWN`이다. 동작은 `PENDING → REQUESTED → ACKNOWLEDGED → VERIFIED` 또는 `FAILED/UNKNOWN/CANCELLED`로 기록한다. 단순 ACK를 VERIFIED로 올리지 않는다.

공조·조명은 명령 전 상태, 상관 ID, 요청값, ACK, 요청 이후 관찰값/재조회 결과를 기록한다. 무관한 오래된 이벤트로 성공 처리하지 않는다. 요청값과 일치하는 기존 상태라면 실제 변경과 이미 충족된 no-op을 구분한다.

시트는 위치 변화의 관찰과 STOP 후 검증된 안정화 관찰을 모두 요구한다. SDK가 별도 모터 정지 센서를 제공한다고 가정하지 않는다. 제한된 관찰로 확인한 정지와 실차 물리 안전 보장은 다르다.

중지 순서:

1. run 취소 표시, 대기 명령·확인 토큰 무효화, 새 동작 금지.
2. 이동 중이거나 결과 불명인 소유 시트 축에 `MoveControl.STOP` 시도. P단 이탈·움직임 조건은 **새 이동**을 막으며 STOP을 막는 동일 조건문으로 사용하지 않는다. 연결·권한이 사라져 전달할 수 없으면 그 실패를 기록한다.
3. 소유 음원·타이머 정리, STOP ACK 및 위치 관찰 확인. 성공 증거가 없으면 UNKNOWN과 사용자 안내를 유지한다.
4. 이전 run의 늦은 콜백은 이력에만 연결하고 취소를 되돌리거나 다음 동작을 시작하지 않는다.

Foreground 세션의 종료/비활성 전환은 취소 사유다. 화면 재생성 때 executor를 중복 생성하지 않는다. 정상 정리 시 STOP 처리·구독 해제 뒤 Vehicle.release를 수행한다. 강제 종료·프로세스 죽음에서는 앱이 STOP을 보낼 수 있다고 보장하지 않는다. 재시작 시 실행을 자동 재개하지 않고, 미확인 시트 이동 기록은 새 제어를 막는다. 연결 회복 시 소유 축의 STOP과 결과 확인을 복구 절차로 수행하며 원래 이동을 재전송하지 않는다.

## 부분 실패·로그·배포 경계

앞서 바꾼 공조/조명은 뒤 동작이 실패해도 무조건 원복하지 않는다. 변경 확인된 값과 남은 상태를 사용자에게 알린다. 앱이 시작한 음원만 중지하고 다른 앱 미디어는 제어하지 않는다. 오디오 포커스 상실은 소유 세션의 중단 정책으로 처리한다.

키·토큰·VIN·정확한 위치·요청 원문을 기본 진단 로그에 기록하지 않는다. 이력에는 규칙/실행/동작 ID, 입력 출처, SDK/정책 버전, 영역, 요청·관찰값, 시각, 오류 분류, STOP 결과를 둔다. 시연 데이터는 비식별 예제만 사용한다.

모의 adapter는 test/debug에 격리하고 시뮬레이션 표시를 붙인다. release에는 앱 내부 가짜 상태로 차량 쓰기 게이트를 통과하는 경로를 넣지 않는다. Virtual Console→공식 서비스→SDK 입력은 공식 에뮬레이터 시험으로 표시한다. Gleo·음성·상시 서비스·정확한 알람·부팅/시동 OFF 자동 실행은 이번 필수 구현에 포함하지 않는다.

구현 전후에는 [검증 계획](07_VALIDATION_AND_DEMO.md)의 요구사항별 시험을 수행한다. 문서의 제안 인터페이스를 실제로 구현했다는 주장은 코드·시험·해시 증거가 생긴 뒤에만 추가한다.
