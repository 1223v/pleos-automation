# A05 실행 설명·이력 검증 장부

범위: R16,R17,R11 / W25,W26,W16 / T31,T41,T42. 승인 원문의 실행 설명 API, 실행 당시 이력과 조건 편집을 구현한다. 실제 OpenAI·외부 서비스 호출은 하지 않았다. 실제 차량 제어·시트 잠금·전체 제품 완료 기준은 이번 작업에서 변경하지 않는다.

판정: **A05.1~4 로컬 구현·단위/모의 HTTP·해당 공식 AVD 검증 완료**. 시험일11월2일, 문서 확정일11월3일. 재개 시 남아 있던 작업은 최종 통과 로그를 TODO·계획·문서에 반영하는 것이었다.

## 재개 및 구현

- 11월1일 A01~A04/A09 후 A05 미착수 상태에서 시작했다. 11월2일 재개 시 이력/서버 구현 및 첫 검증 뒤 독립 리뷰 I1 재현용 APK 빌드에서 멈춰 있었다. 당시 장치가 없어 기존 PleosRoutine AVD를 데이터 삭제 없이 기동했다.
- 실제 실행은 MANUAL/ACTUAL_TEST/AUTOMATIC 원인을 시작 journal에 저장한다. 시뮬레이션도 당시 전체 규칙·revision·의미 해시를 보존한다. Room2→3은 nullable startCause만 추가하고1→3을 연결한다. 과거 누락된 원인을 추정하지 않는다. 원본 schema2의 SHA-256은 이전 사본과 일치한다.
- 이력은 실제/가상·버전·원인·당시 시작 조건·설정값·관찰값·단계·경과·반복/분기를 표시한다. 당시 규칙이 없거나 metadata가 다르면 최신 규칙으로 대신 설명/편집하지 않는다. 현재 저장본과 당시 snapshot이 다르면 새 복사본으로 조건을 편집한다.
- POST /v1/run-explanations는 기존 인증·Origin 차단·128KiB 입력/256KiB 응답·AI 비활성·예산 예약·중복 ID·동시성 gate를 공유한다. strict 입력/출력과 run/node/event 참조를 검증하고 patch/명령을 허용하지 않는다. schema 검증만으로 자연어 설명의 사실성을 입증하지는 않으며 앱의 기록이 판정 기준이다.
- 최대128개(앞32/뒤96) 이벤트와 생략 건수를 전송한다. 원본은 Room에 남고, 크기 초과 시 설명 요청만 오류로 처리한다. 서버는 Kotlin 숫자 직렬화 차이 때문에 의미 해시를 재계산하지 않으며 앱에서 snapshot과 metadata를 검증한다.

## RED 및 수정 기록

| 근거 | 실행 결과/의미 |
|---|---|
| history-red.log | 신규 이력 시험5개 중4FAIL: snapshot 미보존·편집 연결·설명 근거 누락. stub 단계에서 과거 snapshot 거부1개는 이미 통과했으므로 RED라고 주장하지 않는다. |
| history-green-http-red.log | 이력5PASS, 신규 설명 HTTP 경로1FAIL(허용 목록 누락), 총15개 중1FAIL. |
| explanation-client-red.log | 설명 응답 시험2개 중1FAIL 후 strict parser·참조 검증 구현. |
| server-red.log | 기존 설명 route 부재로 표적9개 FAIL. |
| server-invalid-history-red.log | BLOCKED 이력의 불완전 규칙까지 실행 의미 검증으로 거부 → 설명 입력은 strict shape/참조 검증으로 분리. 실행 검증은 유지. |
| server-sdk-red.log / server-sdk-contract-red.log | 공식 SDK의 모의 HTTP 요청/strict schema 계약 보완 과정. 실제 API 호출 증거 아님. |
| ui-red.log | 공식 AVD3FAIL: 이력 이름/편집 연결 없음, 가상 snapshot 없음, 실제 원인 null. |
| ui-green.log | 5개 중4PASS/1FAIL. Room1→3·2→3 포함 migration 통과. 이력 복사 시험이 이전 자동 저장을 너무 일찍 읽은 동기화 결함: 복사본 title/nodes/id가 저장될 때까지 기다리도록 시험 수정. |
| history-race-red-2026-11-02.log | 독립 리뷰 I1 실제 재현: Room query executor를 막고 이력 열기 뒤 새 요청 입력, 조회 해제 후 최신 요청 소실.1FAIL/8.614s. |
| evidence-loading-red-2026-11-02.log | 복구 이벤트를 읽는 중 AI 설명 버튼이 활성인 문제1FAIL. 로딩과 빈 이력 분리 후 기록 준비 전 설명/편집 차단. |
| final-build-2026-11-02.log | nullable generic 선언의 공백 없는 `>=` 토큰으로 컴파일 실패. 시험 RED가 아닌 빌드 오류이며 수정 후 재빌드한다. |
| target-avd-2026-11-02.log | 7개 중6PASS/1FAIL. I1의 최신 요청은 UI에 유지됐으나 시험이 Compose 지연 저장 전에 DB를 읽었다. Compose clock 진행 및 실제 저장 완료 대기로 시험 동기화. 제품 코드는 이 재시험에서 변경하지 않았다. |
| full-avd-2026-11-02.log | 26개 중25PASS/1FAIL,182.133s. 원인 문구 전체 검색이 다른 시뮬레이션 카드까지 찾아2nodes 오류. 해당 이력 title+version+cause의 결합 선택자로 수정했으며 제품 코드는 동일하다. |

독립 리뷰 [review.md](review.md)의 I1은 화면에서 이미 수집한 저장본을 동기적으로 선택해 늦은 DB 응답 자체를 제거했다. 복구 이벤트 로딩/늦은 설명 응답은 row와 event 근거를 함께 묶는다. 중단 이력의 부분 journal을 조건 편집 문장에 전달하고 자동 시작 표시를 ‘자동 실행’으로 정리했다.

## 실제 명령과 최종 결과

환경: Windows x64, Corretto17.0.21.0.11, Gradle8.14.6, Node24.22.1, Room2.7.2, OpenAI SDK7.45.0. 공식 PleosRoutine AVD emulator-5556/API34/user10. Gradle은 프로젝트 `.tools/gradle-home`과 offline/max-workers2/in-process Kotlin을 사용한다.

```powershell
$env:JAVA_HOME='C:\pleos-automation\.tools\corretto-17.0.21.0.11'
$env:GRADLE_USER_HOME='C:\pleos-automation\.tools\gradle-home'
& .\android\gradlew.bat -p android :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug --offline --max-workers=2 --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
# server/에서 프로젝트 Node24 PATH를 사용
npm.cmd test
npm.cmd run typecheck
```

- 서버: [server-final-2026-11-02.log](server-final-2026-11-02.log) **50PASS/0FAIL/0SKIP**, typecheck PASS. 세부 범위 [server-report.md](server-report.md).
- Android: core98/app64 단위시험0FAIL/0SKIP. [최종 빌드](final-build-retest-2026-11-02.log)137 tasks/2m7s PASS, debug/test/unsigned release 및 offline lint0오류1경고(DataExtractionRules). CarPropertyValue.status deprecation·SDK XML 버전 경고는 별도 빌드 경고다.
- [표적 AVD 재시험](target-avd-retest-2026-11-02.log)7PASS/0FAIL/0SKIP,21.933s. Room1/2→3은 하나의 시험 메서드 안에서 두 경로를 실행했다. I1·복구 로딩·모의 HTTP 연결도 포함한다.
- [실제 프로세스 복구](process-recovery-2026-11-02.log)1PASS/6.374s. READY 후 앱만 force-stop, 새 PID978에서 UNKNOWN·snapshot·단계4개·초안 보존과5초간 재전송 없음/음원 종료를 확인했다. `python scripts/run_process_recovery.py --evidence-dir evidence/A05/process-recovery-2026-11-02` 실행. 제품 APK는 최종과 같고 계측 APK는 선택자 수정 전이며 [당시 해시](apk-hashes-before-selector-fix-2026-11-02.json)를 보존한다.
- [전체 AVD 회귀 재시험](full-avd-retest-2026-11-02.log) **26PASS/0FAIL/0SKIP**,184.483s. 표적7개를 포함하므로26+7로 합산하지 않는다. 시험 선택자만 수정한 [마지막 test APK 빌드](full-retest-build-2026-11-02.log)는53 tasks/34s PASS이며 제품 debug/release APK는 바뀌지 않았다.
- 로컬 HTTP fixture는18787 loopback·메모리 예산 DB·명시적인 모의 공급자를 사용한다. `.env`를 읽지 않고 외부 fetch를 차단한다. 테스트 앱의 임시 endpoint/token은 finally로 복원하며 이력 fixture도 삭제한다.

전체 AVD에서 실제 실행한 명령(별도 프로세스 복구와 차량 쓰기 probe는 이 목록에 포함하지 않음):

```powershell
& .\.tools\android-sdk\platform-tools\adb.exe -s emulator-5556 shell am instrument --user 10 -w -r -e a05Fixture true -e class com.pleos.routine.EditorFlowTest,com.pleos.routine.GleoRestoreTest,com.pleos.routine.ConnectionDraftTest,com.pleos.routine.ConnectionUiTest,com.pleos.routine.JournalMigrationTest,com.pleos.routine.RuntimeLifecycleTest,com.pleos.routine.ExecutionLimitUiTest,com.pleos.routine.TimerNotificationTest,com.pleos.routine.AudioPlatformTest,com.pleos.routine.RunHistoryUiTest,com.pleos.routine.RunExplanationUiTest com.pleos.routine.test/androidx.test.runner.AndroidJUnitRunner
```

## 최종 산출물과 재개 확인

- [소스126개 해시](source-manifest-2026-11-02.json), [APK3개 해시](apk-hashes-2026-11-02.json), [시험 요약](test-summary-2026-11-02.json), [실제 도구·AVD 버전](environment-2026-11-02.log)을 연결한다.11월3일 [재개 무결성 검사](resume-integrity-2026-11-03.json)에서 해시 불일치0건. 앱 코드를 수정하거나 제품 시험을 새로 실행하지 않았다.
- [로컬 HTTP 로그](local-http-2026-11-02.log)는 명시적 모의 공급자 호출4회이며 실제 OpenAI 호출은0회다. 보존한 [fixture](local-explanation-fixture.mjs)로 재현할 수 있다.
- [11월3일 정리 확인](cleanup-2026-11-03.json): 기존 AVD를 데이터 삭제 없이 기동하고 user10 설정을 읽어 시험 endpoint/token 잔존 없음, 시험 서버 프로세스0개·18787 수신 포트 없음·reverse 없음 확인. 설정 값은 출력하거나 저장하지 않았다.11월2일 [정리 로그](cleanup-2026-11-02.log)는 preferences를 읽지 못했으므로 복원 확인의 근거로 쓰지 않는다. 재개 때 `run-as <package> --user 10` 순서로 읽기 검증을 마쳤다.
- [제한된 비밀정보 패턴 검사](secret-pattern-scan-2026-11-02.json)는 텍스트118개/패턴4개 일치0건이다. 모든 비밀정보나 보안 취약점이 없다는 보증이 아니다. 상세 보안 경계와 잔여 위험은 [보안 보고서](../../security_best_practices_report.md)를 따른다.
- 11월3일 문서 정리 후 `python scripts/verify_docs.py`는 Markdown39개·로컬 링크422개·JSON 예시1개·오류0건이며 [검사 로그](docs-2026-11-03.log)에 남긴다. `git diff --check`도 [exit0](diff-check-2026-11-03.log)이다. 이번 재개에서 한 해시·문서·정리 확인을 새로운 앱 시험 통과로 계산하지 않는다.

## 남은 제품 게이트

실제 AI의 한국어 설명 정확도, 계정/외부 연결, Pleos SDK 자체 재연결, 시트 이동/STOP, 최신30분·실제 절전, 동일 release 전체 흐름10회는 이번 모의/로컬 시험으로 통과 처리하지 않는다. A06~A08도 별도 후속 범위다.
