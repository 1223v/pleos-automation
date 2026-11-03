# A05 서버 구현·시험 인수인계

2026-11-01 KST. R16,R17,R11 / W25,W26,W16 / T31,T41,T42 중 서버 범위. 최초 구현 에이전트의 파일과 로그를 재개 후 대조했다. 서버 TS의 마지막 변경은16:59:58이며 최종 시험·typecheck 로그는17:00:02다. 재개 담당자는 서버 구현을 다시 쓰거나 동일 시험을 반복하지 않았다.

## 구현

- `POST /v1/run-explanations`를 기존 인증·Origin 차단·분당60요청·본문128KiB·응답256KiB 경계에 추가했다.
- 입력은 `{requestId,locale,workflow,run,startCause,omittedEventCount}`다. run은 기존 RunResult이고 이벤트는128개 이내다. 실행/루틴/노드/버전·단계 순번·출처·경과시간·생략 수의 일관성을 검사한다. 실제 VERIFIED/ACKNOWLEDGED와 SIMULATED를 혼합하지 않는다.
- 실행 불가로 BLOCKED된 초안도 설명할 수 있도록 `validateWorkflowShape`를 추출했다. 설명에는 스키마·참조 검증을 적용하고, 생성/수정의 기존 실행 의미 검증은 유지한다.
- 출력은 `{runId,summary,details:[{nodeId,explanation,eventSequences}],limitations}`다. 추가 명령·patch 필드, 다른 실행 ID, 존재하지 않거나 다른 노드에 속한 event 참조, 중복 참조를 거부한다. 생략/이벤트 부재 경고는 서버가 추가한다.
- `Planner.plan`과 `Planner.explain`이 하나의 busy gate와 영속 예산 예약/정산 경로를 공유한다. 비활성·예산 부족은 공급자 호출 전 차단하고 실패 예약/중복 ID/자동 재시도 금지를 유지한다.
- 공식 SDK7.45.0 Responses API에 별도의 strict 설명 스키마를 전달한다. 공식 API 원점, `store:false`, `maxRetries:0`,55초 timeout, tools 미제공을 유지한다. 새 의존성을 추가하지 않았다.

서버는 `semanticHash`의64자리hex 형식만 검사한다. Kotlin/JavaScript 숫자 직렬화 차이를 무시한 해시 재계산을 하지 않으며, 기록의 진위나 snapshot의 암호학적 일치를 인증했다고 주장하지 않는다. 앱은 전송 전 자신의 snapshot/revision/hash 일치를 검사한다. 자연어 사실성 자체는 실제 AI 한국어 평가 전 미검증이며 모델 출력이 원래 실행 상태를 바꾸지 않는다.

## 시험 증거

Windows x64 / Node24.22.1 / TypeScript7.0.2 / OpenAI SDK7.45.0 / AJV8.20.0. 기존 JUnit과 별개인 Node 단위·모의 공급자·실제 로컬 HTTP 시험이다.

| 증거 | 실제 결과 |
|---|---|
| [server-red.log](server-red.log) | 새 경로 미등록404로 표적9개 실패. 인증/비활성·응답·검증·예산 계약 기대값과 차이 확인 |
| [server-green.log](server-green.log) | 최초 구현 후 전체48PASS |
| [server-invalid-history-red.log](server-invalid-history-red.log) | BLOCKED 초안의 누락된 목적지를 실행 자격 검증하여400 반환, 기대200과 차이 확인 |
| [server-invalid-history-green.log](server-invalid-history-green.log) | shape 검증 분리 후 전체49PASS |
| [server-sdk-red.log](server-sdk-red.log) | 최초 SDK wire fixture 실행에서 AI_REQUEST_FAILED. 구체적인 계약 RED와 구분하는 중간 실패 기록 |
| [server-sdk-contract-red.log](server-sdk-contract-red.log) | wire 스키마의 문서상 지원되지 않은 uniqueItems 검출 후 실패 |
| [server-final-green.log](server-final-green.log) | uniqueItems를 서버 참조 검증으로 옮긴 뒤 전체50PASS, 실패/취소/skip0 |
| [server-typecheck.log](server-typecheck.log) | `npm run typecheck` PASS |

실행 명령은 프로젝트 Node 경로를 PATH에 추가한 뒤 `server/`에서 `npm test`(TypeScript build 후 `node --test dist/test/*.test.js`)와 `npm run typecheck`였다. 새 설명 시험은12개이며 기존38개가 함께 통과했다. 실제 OpenAI 및 외부 연결 서비스는 호출하지 않았다. SDK wire 시험도 global fetch를 시험 응답으로 대체했다.

## 앱 통합용 로컬 fixture — 작성 완료, 이 보고 시점 미실행

`.local/A05/explanation-server.mjs`는 production 범위 밖의 일회성 AVD fixture다. `node --check`가 통과했다. 고정된 주입 provider만 사용하고 global fetch도 거부하도록 설정했다. 환경파일을 읽지 않으며 provider 키는 실제 키가 아닌 명시적 fixture 문자열, 예산 장부는 메모리 DB다.

- 주소: `http://127.0.0.1:18787`
- 테스트 Bearer: `a05-local-fixture-token-000000000000` (테스트용 공개 문자열)
- 설명: `모의 설명: 실행 이력 근거 확인`
- details는 요청의 첫 이벤트 nodeId/sequence를 참조하며 이벤트가 없으면 빈 배열이다.
- 최초 READY와 호출 수/이벤트 수만 로그에 남기며 사용자 입력·토큰·관찰값 전체는 출력하지 않는다.

작성한 실행 예시(이 보고에서 실행하지 않음):

```powershell
$a05Fixture = Start-Process -FilePath 'C:\pleos-automation\.tools\node-v24.22.1-win-x64\node.exe' -ArgumentList 'C:\pleos-automation\.local\A05\explanation-server.mjs' -WorkingDirectory 'C:\pleos-automation' -WindowStyle Hidden -PassThru -RedirectStandardOutput 'C:\pleos-automation\.local\A05\fixture-out.log' -RedirectStandardError 'C:\pleos-automation\.local\A05\fixture-err.log'
& .\.tools\android-sdk\platform-tools\adb.exe -s emulator-5556 reverse tcp:18787 tcp:18787
# 시험 후 위에서 직접 시작한 프로세스만 종료한다.
Stop-Process -Id $a05Fixture.Id
& .\.tools\android-sdk\platform-tools\adb.exe -s emulator-5556 reverse --remove tcp:18787
```

응답의 limitations는 공급자 최대14개에 서버 경고 최대2개를 추가하므로 앱은 최종 응답16개까지 수용해야 한다. AI 설명과 원래 상태의 분리, 늦은 응답 취소, 실제/가상 표시, 이력 편집과 Room migration은 앱 담당 검증 범위다.

## 근거·리뷰 자료의 한계

11월1일 확인한 [공식 Structured Outputs 가이드](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses)의 strict 구조 및 지원 JSON Schema 부분집합과 [버전7.45.0 Responses 소스](https://raw.githubusercontent.com/openai/openai-node/v7.45.0/src/resources/responses/responses.ts)를 사용했다. 설치된 `server/node_modules/openai/src/resources/responses/responses.ts`의 ResponseFormatTextJSONSchemaConfig도 대조했다. API 계약 근거와 실제 서비스 응답 검증은 구분한다.

A05 직전 서버 전체 복사본은 확보되지 않았다.10월30일 baseline을 A05 직전 원본이라고 주장하거나 추정 diff를 원본으로 재구성하지 않았다. 현재 관련 파일·fixture의 SHA-256과 수정 시각은 [server-source-manifest.json](server-source-manifest.json)에 남겼다. Git commit/stage/push는 수행하지 않았다.
