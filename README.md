# Pleos AI Routine Builder

운전자가 원하는 상황을 말하거나 적으면 AI가 차량용 루틴 초안을 만들고, 운전자가 화면에서 고치고 가상으로 시험한 뒤 저장하는 Pleos Connect 앱입니다. **2026-11-15 기준 개발 진행 중이며 전체 제품 검증 완료 상태가 아닙니다.** OpenAI 실제 호출은 사용자 결정에 따라 꺼 두었고, 서버 응답은 로컬 모의값으로 시험합니다.

## 시험 환경 (공모전 제출 요건)

| 항목 | 값 |
|---|---|
| 에뮬레이터 | 공식 Pleos Connect Emulator **v2.1.0** (Android 14 / API 34, x86_64 시스템 이미지, Vehicle Service 2.3.22) |
| 화면 규격 | **2560 × 1440, 160dpi**, 가로. 앱은 Pleos 홈의 오른쪽 영역에 표시됩니다 |
| Android Emulator | 37.2.12, Windows x64, `-gpu host`, 사용자 10 |
| SDK | Pleos Vehicle 2.3.2, NaviHelper 3.0.5, SpeechToText 3.3.1 · minSdk 28, targetSdk 35 |
| 차량 상태 입력 | Virtual Console(`adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`). 공조·조명 쓰기 결과 확인에 Console 연결이 필요합니다 |
| AI 서버 | 선택. 없으면 직접 편집·가상 시험·저장은 되고 AI 초안 만들기만 안내 문구를 냅니다 |

## 2026-11-15 변경

- **말로 입력**: 요청·노드 수정 요청·보충 질문 답변 입력란에 Pleos SpeechToText(ON_DEVICE, ko-KR)를 붙였습니다. 들은 문장은 입력란에만 채우며 AI나 차량으로 자동 전송하지 않습니다. 에뮬레이터에서 서비스 연결·요청·준비 신호와 중지를 확인했고, 에뮬레이터를 `-no-audio`로 띄워 실제 목소리 인식은 확인하지 않았습니다. 공모전 공지상 참가팀은 Gleo 앱 스키마 승인을 신청할 수 없어 Gleo 연동은 진입점 코드만 둡니다.
- **시트 단계 제외 실행**: 에뮬레이터에서 시트 이동·정지를 입증할 수 없어 시트는 움직이지 않습니다. 시트가 든 루틴을 실행하면 "시트 단계 제외 실행 확인"을 묻고, 동의한 그 실행에서만 시트 단계를 ‘미실행’으로 기록하고 나머지를 실제로 실행합니다. 공식 에뮬레이터에서 거절 시 변화 없음, 동의 시 조명 20→50% 재조회 반영과 시트 미실행 기록을 확인했습니다.
- **겨울 차박**: 좌석 보충 질문, 선택한 카드 주변만 고치는 AI 수정 제안, 이름 확인 후 비활성 저장, 시작 화면의 "겨울 차박 예시".
- 검증: 서버 61, core 106, app 95 단위시험, lint 오류 0, 같은 debug APK의 에뮬레이터 계측 28건과 별도 계측 3건. [1단계 기록](evidence/application-audit-2026-11-15/stage1-baseline.md) · [계측 결과](evidence/application-audit-2026-11-15/avd-regression-final.txt)

[제출용 APK 다운로드](apk/forky_forky_release.apk) · 버전 0.1.0. 2026-11-15 공식 Pleos 에뮬레이터에서 새로 설치하고 첫 화면 실행을 확인했습니다. [서명 및 설치 기록](09_SUBMISSION_CHECKLIST.md#apk-검증-기록)

## 현재 구현

- A05 실행 설명 API, 실제/가상 이력의 당시 규칙·버전·원인·설정/관찰값 표시, 조건 편집 연결. 과거 규칙은 최신 저장본과 다르면 복사본으로 열며 AI 설명은 원래 기록을 바꾸지 않습니다. [A05 검증 장부](evidence/A05/verification-2026-11-02.md)

- Compose: 생성·확인·수정·테스트, 노드 이동/확대, 카드·연결 편집, Undo/Redo, 복제, Room 초안·루틴·실행 이력.
- Kotlin core: 조건·대기·타이머·구조화 반복·병렬·합류. 64노드·4병렬·20반복·2시간·최악 경로4,096회 방문 한도, 충돌 검사와 순수 시뮬레이션.
- Vehicle 2.3.2 타입 어댑터, 제한 이동·STOP 경로, NaviHelper 3.0.5, 자체 음원. 실제 관찰과 요청 응답을 구분합니다.
- Node 24 서버: OpenAI strict 출력·최소 패치, 인증·SQLite 예산 예약, 일정·장소·스마트홈 어댑터. 제공자 키는 서버에만 보관합니다.
- 공식 출처를 첨부한 연결 카탈로그 43항목. SDK 도메인·마켓 입점·직접 연결·추가 계약 필요를 구분합니다. 모든 서비스의 완전 목록을 뜻하지 않습니다.
- Gleo 생성·수정·호출 진입점 3개. 외부 요청은 사용자 확인 없이 물리 실행을 승인하지 못합니다.
- 시작 조건·노드·분기 연결의 변경 전후 비교. 기기에서 조회한 실행 가능 앱 선택, Gleo 생성·수정 요청의 로컬 보관·검토 재개.
- 입력 요청·작업 그래프의 원자적 초안 저장/복원, 실제 UX 제한 전환 시 편집·확인 창 닫기와 저장 결과 표시.
- 전체 실행 전 시트 계획1회 승인, 선택된 동작만 지원 검사, 절전 포함 타이머, 순번 있는 실행 단계 journal·Room1→2→3 비파괴 migration, KST 예산·영속 차단.
- 새 Gleo 요청/화면 재생성에서도 최신 편집을 보존하며, 새 요청을 반영하면 이전 요청의 늦은 AI 제안을 취소합니다.

## 로컬 실행

JDK 17, Node 24, Python 3, Android SDK가 필요합니다. 검증 버전은 프로젝트 로컬 Corretto17.0.21.0.11, Node24.22.1, Gradle8.14.6입니다. SDK/AVD와 도구는 Git 제외 `.tools/`에 설치했습니다.

```powershell
python scripts/generate_contracts.py
python scripts/verify_docs.py
python scripts/bootstrap_node.py
python scripts/bootstrap_java.py
$env:JAVA_HOME='C:\pleos-automation\.tools\corretto-17.0.21.0.11'
$env:Path='C:\pleos-automation\.tools\node-v24.22.1-win-x64;'+$env:Path
$env:GRADLE_USER_HOME='C:\pleos-automation\.tools\gradle-home'
& .\android\gradlew.bat -p android :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --max-workers=2 --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
& .\.tools\android-sdk\platform-tools\adb.exe -s emulator-5556 install -r android/app/build/outputs/apk/debug/app-debug.apk
```

공식 AVD 사용자 10의 앱 목록에서 **Pleos AI 루틴**을 여세요. **연결 설정 → 속도·알림 권한**에서 필요한 권한을 요청합니다. 부팅 직후에는 Pleos 홈 초기화가 끝난 뒤 실행하세요. [실제 앱 영역 화면](evidence/W23/avd-first-flow.png)

서버는 `server/`에서 `npm ci`, `npm test`, `npm run build`, `npm start` 순서입니다. `.env.example`을 `.env`로 복사하고 무작위 32자 이상 SERVER_AUTH_TOKEN을 로컬에서 설정하세요. AI/외부 연결은 기본 비활성·예산 0입니다. [서버 안내](server/README.md)

앱 debug 기본 서버 주소는 `http://127.0.0.1:8787`입니다. `adb -s emulator-5556 reverse tcp:8787 tcp:8787`가 필요하며 release는 HTTPS만 사용합니다. 키·토큰·서명키를 Git에 넣지 마세요.

## 검증과 남은 조건

11월2일 A05 실행 설명·이력 개선의 로컬 검증을 마쳤고11월3일 문서와 TODO를 정리했다. [A05 최종 장부](evidence/A05/verification-2026-11-02.md)가 아래 과거 기록보다 우선한다. core98·앱64·서버50, 전체 AVD26개(표적7개 포함)와 별도 실제 프로세스 복구1개가 통과했다. debug/test/unsigned release 빌드, offline lint0오류1경고이며 소스126개/APK3개 해시도 재개 시 일치했다. 로컬 HTTP의 모의 설명 응답을 검증했고 실제 AI는 호출하지 않았다. A06~A08의 기능/시험과 아래 실제 환경 검증은 남아 있다.

11월1일에는 승인 원문 감사 후 A01/A02/A03/A09를 보완하고 A04 타이머·음원을 검증했다. [당시 장부](evidence/plan-compliance-2026-10-30/verification-2026-11-01.md)와 [원문 대조 감사](evidence/plan-compliance-2026-10-30/report.md)에 core98·앱56·서버38, 확장 AVD21개·알림 권한 거부1개·실제 프로세스 복구1개 및 최초 시스템 watchdog 중단을 보존한다. 타이머 완료 알림, 남은 시간 표시, 시작 후 음원 오류 감시와4,096회 초과 저장·시험 차단을 추가했다.

아래 수치와 차량 증거는 **10월30일 APK의 과거 검증**이다.

[최신 구현 검증 기록](evidence/implementation/2026-10-29.md), [기능별 시험](evidence/audit-2026-10-29/feature-test-map.md), [보안 보고서](security_best_practices_report.md), [TODO](13_IMPLEMENTATION_TODO.md)를 참조하세요. core71개·앱25개·서버25개 단위/모의 시험과 debug·unsigned release 빌드가 통과했습니다. 공식 AVD의 편집·migration·수명/복구 시험 결과는 최신 장부에 별도로 기록합니다. 10월28일 APK의 실제 공조·조명·30분 타이머 증거와 최신 APK 결과를 혼합하지 않습니다.

최신 APK의 앱 통합15개·프로세스 복구1개와 차량 재시험5개가 통과했습니다. 최초 차량 시험은 Console 준비 부재에서1PASS/4FAIL이었고, 공식 연결 복구 후 같은 APK에서 공조·조명 상태 반영을 확인했습니다. 실패 이력도 장부에 보존합니다.

실제 OpenAI 한국어 평가, 실제 목소리 음성 인식, Gleo 등록·callback(공모전 기간 신청 불가), 공조의 초기 연결·오류 경로, 시트 이동·STOP, 좌석 점유 유효성, 주행 허용 내비/미디어, 외부 계정, release 전체 흐름10회 검증이 남아 있습니다. 공조·조명·시트 중 하나라도 미검증이면 전체 완료로 표시하지 않습니다. npm audit/OSV 일치0건은 모든 취약점이 없다는 뜻이 아니며 Kotlin KAPT 관련 버전 잔여 위험·lint 경고를 보안 보고서에 남겼습니다.

## 구조와 문서

`android/core/`는 순수 규칙 엔진, `android/app/`은 UI·Room·차량·서비스, `server/`는 AI와 외부 연결, `contracts/`는 JSON Schema와 카탈로그, `scripts/`는 설치·생성, `evidence/`는 실제 결과입니다. `.tools/`는 Git 제외 로컬 SDK·AVD·공식 샘플입니다.

[작업 규칙](AGENTS.md) · [사실](01_FACT_CHECK.md) · [공모전](02_COMPETITION_GUIDE.md) · [요구사항](03_PRODUCT_PRD.md) · [설계](04_TECHNICAL_ARCHITECTURE.md) · [기능/API](05_CAPABILITY_MATRIX.md) · [일정](06_DEVELOPMENT_ROADMAP.md) · [검증](07_VALIDATION_AND_DEMO.md) · [신청 초안](08_APPLICATION_DRAFT.md) · [제출](09_SUBMISSION_CHECKLIST.md) · [위험](10_OPEN_QUESTIONS_AND_RISKS.md) · [출처](11_SOURCES.md) · [Windows 인수인계](12_WINDOWS_HANDOFF.md) · [TODO](13_IMPLEMENTATION_TODO.md)

10월25일 문서는 초기 계획의 근거 기록입니다. 최신 승인으로 바뀐 범위와 현재 산출물은 각 문서의10월27~30일 추가 내용과 TODO를 우선합니다. 원본 DOCX는 변경하지 않았습니다.
