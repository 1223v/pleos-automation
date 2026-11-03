# 2026-11-01 승인 계획 감사 후속 검증

상태: A01~A04/A09의 아래 로컬·표적 AVD 검증 완료. 전체 제품은 미완료. 모든 결과는 Windows x64 로컬 작업이며 실제 AI/유료 공급자 호출·시트 잠금 해제·배포는 하지 않았다. 날짜가 지난 로그는 감사 기준선으로 보존했다.

## 환경·범위

- 프로젝트 `C:/pleos-automation`, 브랜치 codex/consistency-tdd-security. 기존 미커밋/untracked 변경 보존.
- Corretto17.0.21.0.11, Gradle8.14.6, Node24.22.1. 로컬 도구/환경 변수만 사용하고 새 의존성은 추가하지 않음.
- 공식 PleosRoutine AVD, emulator-5556, Android14/API34, 사용자10.11월1일 장치 없음 확인 후 `-no-window -no-audio -no-snapshot -gpu host -port 5556`로 시작. 데이터 삭제/앱 제거 없음.
- Android Car 연결 관찰과 Pleos Vehicle2.3.2 자체 Binder 연결/대기 명령 큐를 구분. 제품 seatPolicy=null, 실제 교정/연결 재개 무효화는 미검증.

## RED → GREEN 증거

| 범위 | 실패 근거 | 수정 후 근거 |
|---|---|---|
| A01 시뮬레이션/경과/필터/편집 | core-red-2026-10-31.log: 새12개 중9 assertion 실패 | core-green-2026-10-31.log:83PASS |
| A02 HA/외부 응답 | server-red-2026-10-31.log: 새9개 중6실패 | server-green-2026-10-31.log:34PASS |
| A03 확인 뒤 취소/교정 변경 | app-red-2026-10-31.log:5개 중2실패 | resume-build-2026-11-01.log:app39PASS |
| A03 승인 소유 실행 Job | visit-and-approval-check.log:39개 중1실패 | resume-build-2026-11-01.log:app39PASS |
| A094,096 사전 한도 | visit-limit-red.log:3실패 / visit-limit-server-red.log:새2실패 | resume-build-2026-11-01.log:core89PASS / server-final-2026-11-01.log:38PASS |
| A09 저장 UI | visit-ui-red-2026-11-01.log:한도초과 저장 버튼 활성1실패 | editor-regression-2026-11-01.log:16PASS(한도·초안보관 포함) |
| A03 HTTP 전송 경계 | http-dispatch-red-2026-11-01.log:8개 중2실패(연결 변경/IO 준비 중 취소 후 POST) | http-green-timer-red-build-2026-11-01.log:app48PASS, HTTP9개 포함 |
| A04 남은 시간 전달 | timer-progress-red-2026-11-01.log:7개 중5실패 | timer-green-build-2026-11-01.log:core96/app48PASS |
| A04 실제 완료 알림 | timer-notification-red-2026-11-01.log:루틴 COMPLETED 뒤 완료 알림 없음1실패 | timer-notification-green-2026-11-01.log:1PASS |
| A04 늦은 음원 오류 | audio-late-failure-red-2026-11-01.log:FAILED 기대, COMPLETED 반환1실패 | audio-green-build-2026-11-01.log:단위시험·debug/test 빌드 통과 |

기존 기능에 보강한 회귀시험을 소급하여 모두 RED→GREEN이라고 표시하지 않는다. 새 API scaffold의 컴파일 성공 후 동작 assertion 실패와 기존 UI 실패를 근거로 삼는다. 실제30분·deep sleep·다른 앱 포커스 경쟁은 짧은/모의 시험과 별도다.

## 실행 명령

PowerShell 프로세스에 `JAVA_HOME=.tools/corretto-17.0.21.0.11`, `GRADLE_USER_HOME=.tools/gradle-home`, Node24.22.1 PATH를 적용했다. 실제 명령은 아래와 같고 표적 시험은 해당 `--tests` 또는 계측 class만 바꾸었다.

```powershell
& .\android\gradlew.bat -p android :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug --offline --max-workers=2 --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
# server/ 작업 폴더
npm.cmd test
# 프로젝트 작업 폴더 / 확인한 공식 AVD 사용자
& .\.tools\android-sdk\platform-tools\adb.exe -s emulator-5556 install -r android/app/build/outputs/apk/debug/app-debug.apk
& .\.tools\android-sdk\platform-tools\adb.exe -s emulator-5556 install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& .\.tools\android-sdk\platform-tools\adb.exe -s emulator-5556 shell am instrument --user 10 -w -r -e class com.pleos.routine.TimerNotificationTest com.pleos.routine.test/androidx.test.runner.AndroidJUnitRunner
```

## 최종 집계·아티팩트

- core98/app56/server38 실패0, Python5PASS. 서버 noEmit 타입검사 통과.
- `final-build-retest-2026-11-01.log`: debug/test/unsigned release·lint 포함137 tasks, BUILD SUCCESSFUL1m48s. 최초 final-build 로그의 constructor trailing-lambda 컴파일 오류를 명시적 onState 인자로 수정했다. 이 컴파일 실패를 TDD RED 증거로 세지 않는다.
- 이번 offline lint:0오류1경고(DataExtractionRules). 과거15경고 중14개 의존성 최신판 안내가 이번 보고에 없는 것을 의존성 교체로 해소했다고 해석하지 않는다. CarPropertyValue.status deprecation 컴파일 경고는 별도다.
- [APK3개 해시](apk-hashes-2026-11-01.json), [소스115개 해시](source-manifest-2026-11-01.json), [클래스별 시험 집계](test-summary-2026-11-01.json).
- `final-avd-2026-11-01.log`:21개 중13개 통과 후14번째 시험에서 DeadSystemException/System has crashed로 중단. 후속7개 미실행. [system_server watchdog 발췌](system-watchdog-excerpt-2026-11-01.txt)는 android.fg가 GNSS stopNmea Binder 응답을66초 기다린 상태임을 보여준다. 앱 오류 없이 항상 안정적이라는 증거로 해석하지 않는다. 시스템 자체 재시작 뒤 동일 APK의 재시험을 진행하며 최초 실패를 보존한다.
- `final-avd-retest-2026-11-01.log`: 동일 APK21PASS/실패0/건너뜀0,156.142초. 실제 Media3 로드 실패 및 통제된 focus client의 transient pause/resume·permanent loss2개 포함. `notification-denied-2026-11-01.log`:권한 거부 시 실제 완료·앱내 표시1PASS,6.347초. 원래 사용자10의 POST_NOTIFICATIONS 허용 상태를 finally에서 복원했다. `process-recovery-2026-11-01.log`:실제 PID19090 강제종료 후 새 PID20709에서 UNKNOWN/정의 snapshot/4개 event prefix/초안 보존,5초간 재실행 없음·음원 꺼짐1PASS. 단계별 모든 물리 명령 강제종료 시험을 대신하지 않는다. 이전 APK의 차량/30분 통과를 이번 APK에 재사용하지 않는다.

## 남은 범위

A05 실행 설명 API·이력 UI, A06 추가 연결, A07 Gleo 최종 응답, A08 잔여 실패행렬은 별도 미완료다. 실제 SDK 재연결, 시트 이동/STOP, 실제 AI/외부 계정, 최신 release 전체10회·최신 APK30분/실제 절전은 아직 통과하지 않았다.


## 검증 범위와 다음 작업

- AVD 합계: 최종 재시험21 + 알림 거부1 + 실제 프로세스 복구1 =23PASS. 최초 전체 계측 중단은 별도 실패 이력이며 통과 수에서 숨기지 않는다.
- 문서 검사:35개 Markdown,366개 로컬 링크,1개 JSON 예시 오류0(후속 문서 변경 시 재검사). Python 설치 도구5PASS, git diff --check 통과.
- 이번 제품 소스 변경에 새 dependency/permission은 없다. 제한된 비밀정보 서명 검사는 [별도 결과](secret-pattern-scan-2026-11-01.json)를 참조한다. 기존 OSV/npm audit는10월30일 동일 의존성 결과이며11월1일에 다시 조회한 것으로 표시하지 않는다.
- 다음 순서 A05: 실제/가상 이력의 정의 snapshot·revision·관찰값·시작 원인을 표시하고 설명 API/해당 조건 편집 연결을 구현한다. 원문 계획92행의 POST /v1/run-explanations는 아직 없으며 완료로 표시하지 않는다. A06~A08과 실제환경 게이트도 유지한다.
