# 서버·공유 계약 승인 계획 준수 감사

감사일: 2026-10-30 KST. 제품 코드 변경 없이 승인 원문·소스·시험·기존 증거를 대조했다. 아래 재현은 기존 빌드의 클래스에 가짜 fetch/provider를 주입하거나 로컬 HTTP만 사용했다. 실제 OpenAI, Google, Kakao, Home Assistant, what3words 요청·키 사용·의존성 설치는 없었다. 이 파일만 감사 결과로 추가했다.

기준: [승인 구현 계획](approved-implementation-plan.md), [승인 정합성 개선 계획](approved-consistency-plan.md). 초기 계획의 시트 반복마다 확인 요구는 후속 승인한 전체 실행 전 1회 확인 정책으로 대체된 것으로 읽었다. KST 예산도 명시적으로 승인된 변경이다.

## 판정

서버 기존 25개 시험은 이번에도 통과했다. 그러나 승인 계획의 설명 API·외부 연결 세부 기능은 일부 구현되지 않았고, 현재 보안 개선이 해결했다고 묶은 HA 사전 상태 차단은 조명에 적용되지 않는다. 외부 조회의 잘못된 응답을 VERIFIED로 기록하는 재현도 확인했다. 이 상태를 단순히 “계정 연결 후 실증만 남음”으로 분류하면 안 된다.

현재 README와 TODO는 전체 제품 완료를 주장하지 않고 W25~W27을 진행 중으로 유지한다. 실제 AI·외부 계정·시트·release 전체 10회의 미검증도 명시한다. 따라서 아래는 전체 완료 허위 주장에 대한 지적이 아니라 **원문에서 사라진 구현 범위와 남은 코드 결함**이다.

## 확인된 결함

### S1 — P2: HA 조명은 unavailable/unknown 사전 차단 없이 명령 전송

- 요구: 승인 정합성 계획 41행은 HA의 unavailable/unknown을 요청 전에 차단하고, 요청 후 미확인은 UNKNOWN으로 남기도록 한다. R18 / W27 / T43, 보안 보고서 항목 5와 연결된다.
- 코드: [connectors.ts](../../server/src/connectors.ts)의 현재 상태 조회와 usableState 검사는 `domain === 'climate'` 안에만 있다. [62행](../../server/src/connectors.ts)은 조명에 곧바로 POST한다.
- 재현: unavailable light에 `light.turn_on`을 요청하면 `POST /api/services/light/turn_on` 1회, 그 다음 `GET /api/states/light.allowed`가 일어난다. 최종 UNKNOWN은 맞지만, 계획이 요구한 **쓰기 전 차단**은 실패한다.
- 시험 빈틈: [security-regressions.test.ts:73](../../server/test/security-regressions.test.ts)은 unavailable climate만 시험한다. [server.test.ts:51](../../server/test/server.test.ts)의 light 시험은 쓰기 후 불일치만 본다. unknown/unavailable light 사전 상태에서 POST 0회를 검사하는 시험이 없다.
- 범위: 외부 연결은 기본 비활성이라 기본 설치에서 실서비스 쓰기는 발생하지 않는다. 설정 후 실제 light 동작에 영향을 준다.

### S2 — P2: 일정·장소·what3words 조회 결과의 의미 검증 없이 VERIFIED

- 요구: 승인 구현 계획의 연결 검증(143행), R18 / W27 / T43, 요청·응답·상태 확인을 구분하는 AGENTS 계약.
- 코드: [connectors.ts:32](../../server/src/connectors.ts), [38행](../../server/src/connectors.ts), [44행](../../server/src/connectors.ts)은 JSON 객체라는 것만 확인하고 items/documents/coordinates를 성공 값으로 반환한다.
- 재현: Google `items: "not-an-event-list"`, Kakao `documents: "not-a-place-list"`, what3words `{}`가 모두 VERIFIED다. 마지막 경우 `observedValue: null`인데 “좌표 조회 완료”로 기록한다.
- 영향: HTTP 성공과 유효 데이터 수신이 혼동된다. 외부 API 오류·형식 변화·잘못된 모의 응답이 루틴 성공으로 저장될 수 있다. 앱은 [RoutineRuntime.kt:157](../../android/app/src/main/java/com/pleos/routine/execution/RoutineRuntime.kt)에서 이 결과를 그대로 ActionResult로 역직렬화한다.
- 시험 빈틈: 현재 두 서버 시험 파일에는 일정/장소/what3words 정상 데이터 계약, 좌표 범위·유한성, 잘못된 배열/아이템, what3words 좌표 누락 시험이 없다. Google 시험은 최종 응답 크기 제한용이다.
- 권장 수정 범위: 제공자별 공식 응답 계약을 확인하고 최소 필수 데이터만 검증한다. 빈 정상 목록과 잘못된 목록은 구분한다. 새로운 커넥터 기능 추가와 분리해 수정 가능하다.

## 승인 원문 대비 구현 누락

| 항목 / 요구·작업·시험 | 승인 원문 | 현재 코드와 판정 |
|---|---|---|
| 실행 설명 API / R17 W26 T42 | 구현 계획 92행: `POST /v1/run-explanations`; 112행: 모델은 초안·수정안·설명 생성 | [http.ts:30](../../server/src/http.ts)은 routine-drafts/connector-actions만 허용한다. [PlannerClient.kt:24](../../android/app/src/main/java/com/pleos/routine/data/PlannerClient.kt)에도 설명 경로가 없다. 서버/src·공유 계약 전체 검색에서 관련 경로/DTO/구현 없음. 실제 키 없이도 구현·모의 시험할 수 있는 미구현 항목이다. |
| Google 오늘·다음 일정 / R18 W27 T43 | 구현 계획 41행 | [connectors.ts:28](../../server/src/connectors.ts)은 현재부터 정확히 24시간, 최대20개 고정이다. 오늘의 달력 날짜 범위/다음 일정 모드가 없다. 새벽 또는 밤에 실행하면 “오늘”과 결과가 다르고, 다음 일정이 24시간 이후면 조회되지 않는다. [카탈로그:494](../../contracts/connector-catalog.json)는 범위를 “앞으로24시간”으로 축소했다. 승인된 범위 변경 근거는 발견하지 못했다. |
| HA 상태 조회·장면 / R18 W27 T43 | 구현 계획 42행 | [connectors.ts:46](../../server/src/connectors.ts)은 쓰기 HA_CALL만 제공한다. [50~51행](../../server/src/connectors.ts)은 light/climate와 서비스3개만 허용하므로 scene 실행 불가. 독립 상태 조회 액션·API도 없다. climate 쓰기 전 GET은 사용자용 상태 조회 기능을 대신하지 않는다. 계정만 넣어서 열리는 기능이 아니다. |
| 카카오 주소→좌표 / R18 W27 T43 | 구현 계획 43행: 주소·장소를 목적지 좌표로 변환 | [connectors.ts:37](../../server/src/connectors.ts)은 keyword 검색만 있다. 주소 검색 모드/계약은 없다. 키워드 검색으로 주소 처리가 완전히 충족된다는 공식 또는 시험 근거도 없다. |
| 장소 결과 선택→목적지 사용 / R18 W27 T43 | 구현 계획 43~44행; 카탈로그 “검색 결과를 선택해 좌표로 사용” | 서버는 결과를 `observedValue` JSON 문자열로만 내보낸다([connectors.ts:6](../../server/src/connectors.ts)). 앱은 ActionResult로 기록하고, 이력 UI [MainActivity.kt:300](../../android/app/src/main/java/com/pleos/routine/MainActivity.kt)은 status/message만 표시한다. 결과 선택이나 NAVIGATE 좌표에 연결하는 코드 경로를 찾지 못했다. 별도 앱 감사와 함께 확정할 항목이다. |
| Android 표준 미디어 / R18 W27 T43 | 구현 계획 40행: 재생·일시정지·다음·지원 콘텐츠 탐색 공통 어댑터 | [RoutineRuntime.kt:156](../../android/app/src/main/java/com/pleos/routine/execution/RoutineRuntime.kt)은 MEDIA_CONTROL을 무조건 BLOCKED로 반환한다. Android manifest의 MediaBrowserService 조회 선언은 있으나 실제 클라이언트/탐색 구현 검색 결과 없음. 권한 미검증만이 아니라 어댑터 미구현이다. 앱 감사 담당과 중복 확인 필요. |
| 카카오내비 목적지·경유지 / R18 W27 T43 | 구현 계획 45행: 공식 Android 연동 구현, Pleos 호환성은 별도 검증 | 서버/계약/앱 main 소스에서 카카오내비 연동 클라이언트·액션이 없고 카탈로그의 market 항목뿐이다. 설치 앱 열기는 목적지·경유지 전달을 대신하지 못한다. 앱 감사 담당이 의존성·내비 코드까지 확인할 항목이다. |
| 기본 내비 경유지·경로 옵션 / R18,R20 W27,W29 T43,T45 | 구현 계획 28행 | 서버의 action 계약은 단일 latitude/longitude/text만 가진다. [workflow.schema.json](../../contracts/workflow.schema.json)에 경유지·경로 옵션 필드가 없다. 앱 내비 구현과 함께 보완할 미구현 범위다. |

이 누락은 원문 계획에 명시돼 있다. 현재 W27이 IN_PROGRESS인 점은 맞지만, TODO에는 각각의 구현·모의시험·계정시험 상태를 되살려야 한다. 설명 API와 오늘/다음·HA scene 등은 현재 압축 계획에서 항목 자체가 사라져 있다.

SmartThings·Spotify·Radioline·Make My Day는 원문에서도 등록/승인/파트너 접근 전 보류로 합의했다. 이들을 미구현이라는 이유만으로 무단 범위 축소로 판정하지 않았다. what3words의 유료 키와 별도 활성화 역시 승인된 차단이다.

## 시험 범위와 긍정 확인

- 25개 기존 서버 시험을 다시 실행해 25PASS/0FAIL을 확인했다. 시험은 가짜 외부 응답과 로컬 HTTP만 사용한다. “25PASS”는 위 누락 기능이나 모든 연결 분기의 증거가 아니다.
- [config.ts:78](../../server/src/config.ts)의 requireAi는 활성화·키·일/월 양의 예산을 모두 요구한다. [planner.ts:31](../../server/src/planner.ts) 이후에서 호출 전 검사, 영속 예약을 하고, 그 다음 provider를 호출한다. 비활성 기본값을 우회하지 않았다.
- [planner.ts:16](../../server/src/planner.ts)은 공식 OpenAI 원점·maxRetries0·timeout을 고정한다. [17행](../../server/src/planner.ts) 이후 strict structured output, store:false, reasoning low를 사용한다. 해당 SDK 경계 시험은 실제 모듈의 fetch를 가짜로 바꾼 것이므로 실제 OpenAI 평가가 아니다.
- 예산의 KST 일/월 경계·legacy UTC 중첩·중복 정산 거부·초과 비용 보존·영속 차단 구현과 시험은 승인 정합성 계획 39~40행에 대응한다. KST 전환은 무단 변경이 아니다.
- AI 응답/워크플로우는 AJV schema 후 의미 검사, 편집은 baseRevision과 복제본 최종 검증을 거친다. 앱이 최종 권한·실행 검증 주체라는 범위를 서버 README도 명시한다.
- 요청128KiB/최종 응답256KiB·인증·Origin·MIME·rate·공급자 원점에 시험이 있다. 이 결과를 TLS 실환경·외부 계정 권한 검증으로 확대하지 않는다.
- “동시성” 시험은 두 BudgetLedger 연결의 **순차 reserve** 충돌([security-regressions.test.ts:51](../../server/test/security-regressions.test.ts))이다. 실제 동시 Planner 요청이 BUSY로 차단되는 시험이나 다중 프로세스 경합 시험은 없다. 구현에는 busy gate가 있으므로 이 사실만으로 결함이라고 판정하지 않는다.
- “connector stream overflow and redirect failure” 시험([security-regressions.test.ts:134](../../server/test/security-regressions.test.ts))은 redirect 설정 문자열과 stream 초과를 확인한다. 실제 redirect 응답/timeout/OAuth 만료/429의 독립 분기 결과는 없다. 시험 이름보다 좁은 증거다.
- 서버/read-only 외부 연결의 정상·오류·경계·취소 행렬은 승인된 정합성 시험 범위를 아직 채우지 못했다. 위 S1/S2는 그 빈틈에서 재현된 결함이다.

보안 보고서는 npm audit0/OSV0의 한계, Kotlin KAPT 버전 잔여 위험, 실제 계정과 release 검증 미완료를 명시한다. 이번 감사는 새로운 공개 취약점 DB 조회나 SDK 공식 호환성 재평가를 수행하지 않았으므로 도구·의존성 전체에 추가 보안 보증을 하지 않는다.

## 실행한 재현 명령과 출력

실행 위치는 `C:\pleos-automation`. 기존 dist/src/connectors.js 수정 시각은 2026-10-30 09:19:26, src/connectors.ts는 2026-10-29 14:15:24였다. 소스와 해당 빌드의 분기를 대조했다. 제품 파일을 다시 빌드하거나 수정하지 않았다.

### S1 — 가짜 HA 응답

```powershell
@'
import {loadConfig} from './server/dist/src/config.js';
import {Connectors} from './server/dist/src/connectors.js';
const c = loadConfig({CONNECTORS_ENABLED:'true',HA_BASE_URL:'https://ha.test',HA_ACCESS_TOKEN:'fake-audit-only',HA_ALLOWED_ENTITIES:'light.allowed'});
const requests=[];
const fake=async(url,init)=>{requests.push({url,method:init.method??'GET'});return new Response(JSON.stringify(init.method==='POST'?[]:{entity_id:'light.allowed',state:'unavailable',attributes:{}}));};
const answer=await new Connectors(c,fake).execute({action:{type:'HA_CALL',targetId:'light.allowed',text:'light.turn_on'}});
console.log(JSON.stringify({case:'HA light initially unavailable',requests,answer}));
const w = new Connectors(loadConfig({CONNECTORS_ENABLED:'true',WHAT3WORDS_ENABLED:'true',WHAT3WORDS_API_KEY:'fake-audit-only'}),async()=>new Response('{}'));
console.log(JSON.stringify({case:'what3words missing coordinates',answer:await w.execute({action:{type:'WHAT3WORDS_RESOLVE',text:'a.b.c'}})}));
'@ | & .\.tools\node-v24.22.1-win-x64\node.exe --input-type=module
```

```jsonl
{"case":"HA light initially unavailable","requests":[{"url":"https://ha.test/api/services/light/turn_on","method":"POST"},{"url":"https://ha.test/api/states/light.allowed","method":"GET"}],"answer":{"result":{"status":"UNKNOWN","message":"서비스 응답은 받았으나 목표 상태 반영은 미확인","observedValue":null}}}
{"case":"what3words missing coordinates","answer":{"result":{"status":"VERIFIED","message":"세 단어 주소의 좌표 조회 완료","observedValue":null}}}
```

### S2 — 잘못된 외부 응답

```powershell
@'
import {loadConfig} from './server/dist/src/config.js';
import {Connectors} from './server/dist/src/connectors.js';
const common={CONNECTORS_ENABLED:'true'};
for (const [name,config,action,responses] of [
 ['calendar malformed items',{...common,GOOGLE_CLIENT_ID:'fake',GOOGLE_CLIENT_SECRET:'fake',GOOGLE_REFRESH_TOKEN:'fake'},{type:'CALENDAR_READ'},[{access_token:'fake'},{items:'not-an-event-list'}]],
 ['kakao malformed documents',{...common,KAKAO_REST_API_KEY:'fake'},{type:'PLACE_SEARCH',text:'test'},[{documents:'not-a-place-list'}]],
 ['what3words missing coordinates',{...common,WHAT3WORDS_ENABLED:'true',WHAT3WORDS_API_KEY:'fake'},{type:'WHAT3WORDS_RESOLVE',text:'a.b.c'},[{}]]
]) {
 let calls=0;
 const fake=async()=>new Response(JSON.stringify(responses[calls++]));
 console.log(JSON.stringify({case:name,answer:await new Connectors(loadConfig(config),fake).execute({action}),mockCalls:calls}));
}
'@ | & .\.tools\node-v24.22.1-win-x64\node.exe --input-type=module
```

```jsonl
{"case":"calendar malformed items","answer":{"result":{"status":"VERIFIED","message":"앞으로 24시간의 일정 읽기 완료","observedValue":"\"not-an-event-list\""}},"mockCalls":2}
{"case":"kakao malformed documents","answer":{"result":{"status":"VERIFIED","message":"장소 후보 조회 완료 · 목적지 선택 필요","observedValue":"\"not-a-place-list\""}},"mockCalls":1}
{"case":"what3words missing coordinates","answer":{"result":{"status":"VERIFIED","message":"세 단어 주소의 좌표 조회 완료","observedValue":null}},"mockCalls":1}
```

### 기존 시험 재실행

실행 위치 `C:\pleos-automation\server`:

```powershell
& 'C:\pleos-automation\.tools\node-v24.22.1-win-x64\node.exe' --test dist/test/server.test.js dist/test/security-regressions.test.js
```

```text
tests 25
suites 0
pass 25
fail 0
cancelled 0
skipped 0
todo 0
duration_ms 3482.9293
```

이 시험은 기존에 컴파일된 모의/로컬 HTTP 시험의 재실행이다. 이번 감사가 새 타입검사·빌드·실제 API 검증을 했다는 뜻이 아니다. 시험의 임시 SQLite 파일은 기존 테스트의 cleanup으로 제거되었다.
