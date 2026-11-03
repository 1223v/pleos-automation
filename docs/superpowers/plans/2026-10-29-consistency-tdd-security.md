# Pleos 정합성·TDD·보안 구현 계획

## 2026-11-03 A05 완료 및 다음 작업

A05.1~4 로컬 구현·검증을 마쳤다.11월2일 서버50/core98/app64PASS, debug/test/unsigned release·offline lint0오류1경고, 전체 AVD26PASS(표적7개 포함) 및 별도 실제 프로세스 복구1PASS다. 독립 리뷰의 이력 편집 경합을 실제 Room 조회 지연으로 재현하고 동기 선택으로 수정했으며, 복구 journal 로딩 전 설명 차단도 RED→GREEN이다. 전체 회귀의 시험 선택자 충돌1건은 수정 뒤26개 모두 통과했다.11월3일 재개 시 소스126개/APK3개 해시 일치를 확인하고 남아 있던 문서·TODO를 정리했다. [A05 장부](../../../evidence/A05/verification-2026-11-02.md)·[리뷰 조치](../../../evidence/A05/review-resolution-2026-11-02.md)를 따른다. 다음 구현은 A06이며 A06~A08/실제 AI·시트·release10회 게이트는 별도로 유지한다.

## 2026-11-01 A05 상세 실행 순서

현재 A05 구현 전이다. [기존 TODO A05.1~A05.4](../../../13_IMPLEMENTATION_TODO.md)를 따른다. snapshot/원인·비파괴 migration → 엄격한 설명 API → 이력 사실 표시/조건 편집/설명 연결 → 통합 검증 순서다. API와 앱은 `{requestId,locale,workflow,run,startCause,omittedEventCount}`를 공유한다. run은 기존 RunResult이고 events는 최대128개이며 생략 건수를 함께 보낸다. 응답은 `{runId,summary,details:[{nodeId,explanation,eventSequences}],limitations}`이며 실제 상태 변경이나 patch를 포함하지 않는다. 모든 참조는 전송된 snapshot·event에 한정한다. 원래 전체 제품 완료 조건은 유지한다.

## 2026-11-01 실행 계획 갱신

A01/A02/A09의 로컬 결함 수정·검증, A03 승인 수명/HTTP 전송 경계, A04 타이머 표시·완료 알림/음원 오류 감시까지 진행했다. 현재 core98/app56/server38PASS, 최종 APK 빌드·lint 통과, 같은 APK AVD21PASS와 알림 거부1PASS다. 최초 AVD system_server GNSS watchdog 중단을 보존한다. 실제 프로세스 복구1PASS와 장부 정리를 마쳤다. 다음 구현 순서는 A05의 실행 설명 API/이력 표시다. A03 SDK 자체 연결 관찰·시트 교정·실제 AI·30분/절전·release10회는 별도 게이트다.

아래는 이번 재개 시작 시 파악한 중단 지점이다.

[TODO의 최신 재개 절](../../../13_IMPLEMENTATION_TODO.md)을 따른다. A01/A02 개별 RED→GREEN 이후, A03 승인 취소 시험1건의 최소 수정 후 재검증에서 멈췄다. 먼저 A01~A03/A09를 통합 검증하고 새 APK 증거와 문서를 갱신한 뒤 A04부터 이어간다. 4,096회 한도는 사용자가 추가 확정했다. 저장·시험 전 최악 경로를 검사하며 반복은 곱, 병렬은 합, 조건 분기는 최대값으로 계산한다. 실행 시 비상 한도와 편집 초안 자동 보관을 유지한다. 이전 APK의 통과를 새 코드에 재사용하지 않는다.

## 2026-10-30 원문 준수 감사로 재개방

아래 완료 체크는 실행한 범위의 이력이며 승인 원문 전체의 완료를 뜻하지 않는다. 대화의 원문 계획과 재대조하여 시작 트리거 시뮬레이션·HA 조명 사전 상태·시트 승인 연결 세대 계약 및 기능/시험 누락을 확인했다. [최신 TODO A01~A08](../../../13_IMPLEMENTATION_TODO.md)의 순서와 기존 R/W/T 연결을 따른다. 원문은 `evidence/plan-compliance-2026-10-30/approved-implementation-plan.md`, `approved-consistency-plan.md`에 보존했다. 결함은 RED→GREEN으로 수정하고, 코드 미구현을 실제 환경 시험 대기와 혼동하지 않는다.

## 2026-10-30 현재 상태와 재개 순서

최신 사용자 요청에 따라 소스·실행 로그와 대조해 갱신했다. 기준 증거는 [10월29~30일 장부](../../../evidence/implementation/2026-10-29.md)다. 단위시험·앱 계측 통과를 실제 차량 제어 통과로 올리지 않는다.

- [x] 서버25개·core71개·app25개 시험 및 strict 입력/KST 예산/트리거/실행 journal/시트 모의 안전 경계 수정.
- [x] Gleo 오래된 요청·저장 전 편집 유실·늦은 AI 응답을 실제 AVD에서 RED→GREEN 검증. 편집/migration/수명/연결 통합15PASS.
- [x] 실제 프로세스 종료 후 다른 PID에서 UNKNOWN, 정의 snapshot·event prefix4개 보존,5초간 재실행 없음 확인.
- [x] Node24.22.1·Guava33.7.2-android·Gradle8.14.6·로컬JDK17.0.21 패치. debug/test/release 빌드, lint0오류15경고, npm audit0, release Maven136개 OSV0.
- [x] **W05/R06,R07/T05~07,T11 — 최신 APK 차량 제어 실패 조사 완료.** Cold boot 후5개 probe 중 상태/capability1PASS, 공조 읽기·공조 power 준비·밝기·색상4FAIL. ACK 뒤 상태가 변하지 않아 UNKNOWN으로 남았지만 정상 제어 게이트는 미통과다. 공식8088 forward와 Console 부재를 확인하고 공식 절차로 복구했다. timeout이나 상태 확인 기준은 낮추지 않았다. 증거: `evidence/audit-2026-10-29/app/vehicle-regression.log`, `vehicle-observations.log`.
- [x] 공식 Console 연결 복구 후 동일 최신 APK5PASS. 읽기/공조22→23°C/밝기20→30%/색상 변경을 관찰했고 초기4FAIL과 재시험5PASS를 함께 기록했다.
- [x] APK3개·소스98개 해시/시험 요약·비밀정보 패턴 검사를 최종 산출물에 연결했다.
- [x] README·PRD·설계·검증계획·출처·Windows 인수인계·기존 TODO 갱신. 문서 링크/JSON/변경 공백 검사 통과.
- [x] 최종 보고 자료: 수정·실제 통과·실패/차단을 분리했다. Kotlin KAPT 관련 버전 잔여 위험, 실제 AI·시트 이동/STOP·최종 release10회 미완료를 유지한다.

진행 규칙: 위 미완료 항목을 순서대로 처리한다. 서로 독립적인 문서/증거 정리는 대기 중 진행할 수 있으나 미통과 선행 조건을 통과로 간주하지 않는다. 이미 승인된 로컬 조사·수정·시험은 재승인 없이 계속한다.

사용자가 2026-10-29 승인한 계획. 기존 R01~R20, W01~W29, T01~T45는 유지한다. 현재 진행 상황은 [기존 TODO](../../../13_IMPLEMENTATION_TODO.md), 실제 결과는 evidence/implementation에 기록한다.

## 확정 정책

- 시트 포함 전체 루틴의 어떤 부작용보다 먼저 전체 좌석·축·방향·검증 한도를 1회 확인하며 다음 실행에는 새 승인이 필요하다.
- 내비는 조건부 열선과 독립 실행한다. 일반 병렬 실행 실패 시 형제 취소와 물리 상태 자동 원복 금지는 유지한다.
- 타이머는 절전 시간을 포함하며 복귀 후 1초 이내 만료를 재평가한다. 깨우기 권한은 추가하지 않는다.
- AI 예산 날짜는 Asia/Seoul. 기존 UTC일만 있는 행은 겹치는 KST 기간에 보수적으로 반영한다.
- 실제 AI/외부 연결은 키·예산·활성화 전 무호출. 시트 제품 잠금은 실제 교정/STOP 증거 전 유지. 모든 모의 시험과 AVD 증거를 구분한다.

## 작업과 순서

1. 서버: 프로젝트용 Node 보안 패치, strict DTO/공식 공급자 원점, 예산 초과 영속 차단·KST 이관, HA 상태 검증·응답 제한, 기능별 node:test.
2. core: 활성 정의 동기화·결합조건·실제 시작 cooldown, 선택 경로 preflight·승인 run context·공통 시계·명시적 timeout, 기능별 JUnit.
3. 앱 저장/편집: Gleo 요청 최초 반영과 최신 초안 보존, 실행 snapshot/단계 journal, Room 1→2 비파괴 migration, JVM/계측 시험.
4. 앱 실행/차량: core/저장 인터페이스 연결, 전체 실행 전 승인, MOVE 직전 상태/범위 검사, STOP 독립 감시·연결 세대·불명 유지, 타이머/음원/외부입력 단위시험.
5. 독립 리뷰와 회귀: 표적 RED→GREEN 및 전체 시험, lint/build, 가능한 공식 AVD 시험, 출처/요구사항/보안 보고/해시 갱신.

## 검증 계약

기존 동작은 회귀시험으로 보강하고 결함/새 동작은 실패 원인을 먼저 관찰한 뒤 최소 수정한다. 과거 구현을 소급하여 TDD라고 주장하지 않는다. 기존 JUnit4/node:test/coroutines-test 1.9.0을 사용하며 새 런타임 프레임워크는 도입하지 않는다. 테스트 더블은 외부 IO 경계에서만 사용한다.

조건·트리거, 모든 그래프 노드, 편집/패치/시뮬레이터, 공조·조명·열선·두 시트 축/STOP, 저장/복구/Gleo, 타이머/음원/앱/내비, AI/서버/커넥터에 정상·오류·경계·취소의 해당 사례를 연결한다. 인증·타입·크기·동시성·날짜 경계·늦은 응답·migration을 포함한다.

실제 AI 평가·시트 두 축 이동/STOP·동일 release 10회는 단위시험으로 대체하지 않는다. 시험 미실행/차단/실패와 검증 완료를 분리한다.
