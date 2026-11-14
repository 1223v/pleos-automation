# 개발 서버

## 2026-10-29 보안 기준

프로젝트 runtime은 **Node24.22.1**이다. 루트에서 `python scripts/bootstrap_node.py`로 공식 SHA-256을 확인한 `.tools/node-v24.22.1-win-x64`를 설치한다. PowerShell 세션에서 `$env:Path='C:\pleos-automation\.tools\node-v24.22.1-win-x64;'+$env:Path`를 설정하고 아래 명령을 실행한다. 전역 Node는 변경하지 않는다.

일/월 예산은 **Asia/Seoul** 자정 기준이다. 이전 UTC일만 기록된 행은 겹치는 한국 날짜/월에 보수적으로 집계한다. 실제 비용이 예약을 넘거나 사용량이 잘못되면 확인된 비용/예약을 유지하고 DB에 AI 호출 잠금을 저장한다. 재시작이나 날짜 변경으로 풀리지 않으며 `/v1/status`의 `budget.blocked`와 `timeZone`을 확인한다. 자동 해제 API는 없다. 실제 공급자 사용량·가격과 장부를 대조하기 전 잠금을 수동 해제하지 않는다.

공급자 원점은 공식 `https://api.openai.com/v1`로 고정한다. `OPENAI_BASE_URL`은 사용하지 않는다. 생성 요청에는 수정용 기준 그래프를 보내지 않으며, 최종 HTTP JSON 응답은 256KiB 이내다. HA의 unavailable/unknown은 적용 성공이 아니다. 실제 계정·키·예산 활성화는 이번 보안 시험에 포함하지 않는다.

Node 24/TypeScript. `npm ci`, `npm test`, `npm run build`, `npm start`로 실행합니다. `.env.example`을 참고해 `.env`를 만들고 무작위 32자 이상 SERVER_AUTH_TOKEN을 설정합니다. 실제 계정·키·예산은 자동으로 채우지 않습니다.

AI_ENABLED=false, 일/월 예산 0, CONNECTORS_ENABLED=false가 기본값입니다. 비활성 AI는 SDK 호출 전에 503을 반환합니다. 테스트는 가짜 provider/외부 응답과 로컬 HTTP만 사용합니다. 2026-10-30 Node24.22.1에서25개 시험·TypeScript 빌드와 npm audit0건을 확인했습니다. [검증 장부](../evidence/implementation/2026-10-29.md)

| 경로 | 동작 |
|---|---|
| GET `/v1/status` | 호출 가능 여부·모델·예약 예산 |
| GET `/v1/catalogue` | 공식 근거 연결 목록 |
| POST `/v1/routine-drafts` | 생성 또는 기준 버전 수정 패치 |
| POST `/v1/run-explanations` | 실행 당시 정의·단계 근거에 대한 읽기 전용 설명 |
| POST `/v1/connector-actions` | 허용한 일정·장소·스마트홈 동작 |

루틴 생성 중 AI가 좌석을 물으면 앱은 원문과 답을 함께 다시 보냅니다. 선택 필드 `clarification`은 같은 `requestText`, 대응하는 `questions`·`answers` 배열을 받습니다. 요청당 누적 질문은 5개, 문답 한 항목은 1,000자까지입니다. 생성 모드의 `needs_clarification`에 들어 있는 그래프는 읽기 전용이며, 시트 이동 카드의 빈 영역은 좌석을 정해야 한다는 뜻입니다. 정식 `draft`에는 유효한 영역이 필요합니다.

편집 모드는 기존대로 전체 루틴의 패치를 받을 수 있습니다. 특정 카드를 누른 요청은 `baseWorkflow`, `baseRevision`과 함께 `selectedNodeId`를 보냅니다. 이때 서버는 선택한 카드의 허용 설정, 그 다음에 추가하는 대기와 같은 영역·종류의 공조/열선/조명 설정, 원래 다음 카드로 돌아오는 연결만 허용합니다. 다른 카드나 시작 조건을 건드린 조작은 최종 그래프에서 원상 복귀하더라도 거부합니다. 이 검사는 AI 초안의 범위를 정하며 차량의 실제 권한·지원 영역은 앱에서 확인합니다.

생성·편집 요청의 선택 필드 `vehicleAreas`는 앱이 조회한 차량 SDK 영역 ID를 전달합니다. 각 항목은 `{type, areas}`이며 시트 이동에는 `axis`도 필요합니다. 공조 목표, 앰비언트 밝기·색상, 열선, 시트 앞뒤·등받이까지 6항목을 받을 수 있습니다. 항목당 ID는 최대 32개, ID당 최대 160자입니다. `+`로 이어진 복합 영역은 하나의 ID입니다. 서버는 새로 제안한 대상이 해당 항목의 목록과 일치하는지 검사합니다. 좌석이 하나만 보고되어도 운전자가 어느 좌석을 젖힐지 밝히지 않았다면 AI가 질문하도록 지시합니다. 선택되지 않은 기존 카드의 영역은 보존합니다. [Pleos SDK 영역 확인 절차](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app)

모든 경로는 Bearer 인증이 필요합니다. 브라우저 Origin은 허용하지 않습니다. 본문 128 KiB, 분당 60요청, AI 동시 실행 1건 제한입니다. 외부 인터페이스는 HTTPS가 필요합니다.

실행 설명은 `{requestId,locale,workflow,run,startCause,omittedEventCount}`를 받습니다. `workflow`는 당시 snapshot이고 `run`은 앱의 RunResult입니다. 이벤트가 많으면 처음32개/마지막96개를 보내며 원래 sequence와 생략 수를 유지합니다. 설명은 전송된 이벤트만 참조할 수 있습니다. 응답은 `{runId,summary,details:[{nodeId,explanation,eventSequences}],limitations}`이며 patch나 차량 명령을 포함하지 않습니다. 실행 불가로 차단된 초안도 설명할 수 있도록 snapshot은 구조 스키마와 참조를 검사하되 실행 자격을 부여하지 않습니다. 앱은 당시 snapshot의 의미 해시를 검증하며 서버는 해시를 재계산하거나 실행 기록을 인증했다고 주장하지 않습니다.

생성·수정·설명은 하나의 동시 호출 gate와 영속 예산 장부를 공유합니다. 누락 근거는 별도 한계로 표시하고 실제/가상 결과를 혼합하지 않습니다. 모델 텍스트의 사실성은 실제 한국어 평가 전 미검증이며, 앱의 원래 실행 상태·관찰값을 덮어쓰지 않습니다. 설명용 SDK도 `store:false`, 자동 재시도0회, 공식 API 원점 고정을 유지합니다.

SQLite 트랜잭션으로 호출 전에 비용 상한을 예약합니다. 자동 재시도는 끄고 불명확한 호출은 예약을 유지합니다. 동일 requestId는 중복 호출하지 않습니다. 입력은 1M 토큰당 $3, 출력 $10의 보수적인 **로컬 예산 통제값**입니다. 2026-10-27 공식 일반 입력 $2/cache write $2.50/출력 $10을 근거로 하며 청구서가 아닙니다. 가격·모델·지역 처리 변경은 재검증해야 합니다. store:false는 공급자의 모든 보관 정책을 없앤다는 뜻이 아닙니다.

Google은 읽기 전용 OAuth refresh token, Home Assistant는 HTTPS 원점·토큰·명시적 light/climate entity 목록, 카카오는 Local REST 키, what3words는 키와 별도 활성화가 필요합니다. Home Assistant 재조회가 목표 상태와 다르면 UNKNOWN입니다. 연결 응답의 내용은 AI 시스템 명령으로 사용하지 않습니다.

Kotlin core가 최종 그래프·안전 검증과 실행의 권위입니다. 서버는 schema·참조·순환·기본 범위·패치 버전을 검사하며 차량 권한이나 실행 승인을 부여하지 않습니다.

Windows Node 24.12.0의 한글 TEMP 경로에서 파일 DB 시험 프로세스가 비정상 종료되는 현상을 관찰해, 파일 시험 경로를 저장소의 `.data/tests`로 한정했습니다. 근본 원인은 확정하지 않았으며 운영 경로 변경 시 재검증해야 합니다.
