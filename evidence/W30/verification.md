# W30 Astryx 디자인 앱 UI 개편 검증

```text
요구사항 ID / 작업 ID / 시험 ID·분기: R14/R04 · W30 · T39(편집 의미 유지)/T32(주행 제한 화면) 회귀
날짜(KST) / 담당 / 판정(PASS·FAIL·BLOCKED·NOT_RUN): 2026-11-15 19:40 / 개발 에이전트 / PASS(아래 범위)
검증 수준(DOC·UNIT·API·AVD·DEVICE): UNIT + AVD
APK 버전·SHA-256 / 소스 커밋 또는 소스 묶음 해시: debug 0.1.0 app-debug.apk 1ea3f358f296d818baa12033da2e74c570e121ecf09d6033eebe7f7d6b88fa47, app-debug-androidTest.apk f51dc6d3163389900989de2bdef63490a1cf91ba1975f78360a24c061a511342. 기준 커밋 337d9a8 위의 미커밋 작업 트리. 제출 release APK(f27504a)는 바꾸지 않았다
Windows·JDK·Gradle / AVD·OS·SDK·Vehicle Service 버전: Windows 11 x64, Corretto 17.0.21.0.11, Gradle 8.14.6 / emulator-5556, Pleos/crp_avd/crp_avd_x86_64:14/RELEASE.release_connect_v2.1.0, 사용자 10, 2560×1440·160dpi, `-no-window -no-audio -no-snapshot -gpu host`
입력 출처(실제 SDK·Console→SDK·모의 어댑터·실제 AI): 실제 SDK(공식 AVD), Virtual Console(8088), 로컬 HTTP 모의 설명 fixture(18787, 외부 호출 차단). 실제 AI 호출 0회
권한 / capability / 대상 영역·축 / 단위·허용 범위: 동작 코드 변경 없음. 시트 단계 제외 실행 시험만 가상 차량 조명에 씀
적용 정책 버전(속도·freshness·이동/STOP 확인 한도): 변경 없음
실행 ID·동작 ID / 사용자 확인 ID: 계측 로그 참조
재현 단계 / 기대 결과: README 빌드 명령 → install -r → 아래 계측 → capture(밝은/어두운). 시험이 찾는 문구·testTag 유지, 기존 계측 전체 통과
요청값·요청 시각 / 응답 종류·시각: SeatExcludedRunTest 로그 참조
변경 전/후 조회 또는 이벤트값·시각: 같은 로그
STOP 요청·응답·정지 확인 또는 결과 불명 근거: 해당 없음(시트 명령 미전송 유지)
실제 결과 / 화면·로그·영상 경로: 이 폴더의 light-*.png, dark-*.png, overview-*.png, avd-*.log, avd-regression.txt
실패 원인 / 수정 작업 / 재시험 결과: 아래 "실패와 재시험"
남은 미검증 범위 / 재개 조건: 아래 "남은 범위"
```

## 바뀐 것

- 테마: `ui/theme/RoutineTheme.kt`, 생성 파일 `ui/theme/AstryxTokens.kt`(neutral 0.7.16 토큰). 기기 밝은/어두운 설정을 따른다. `res/values(-night)/styles.xml`의 창·상태바 색도 맞췄다.
- 아이콘: `res/drawable/ic_lucide_*.xml` 63개(생성, Lucide ISC). `ui/NodeVisuals.kt`가 노드 종류·동작·시작 조건 → 아이콘·분류색·한국어 이름을 정한다. 캔버스와 노드 삽입 창에서 `ACTION`/`IF` 같은 enum 원문을 없앴다.
- 캔버스(`ui/FlowCanvas.kt`): 팬/줌과 함께 움직이는 점 격자, 분류 색 정사각형 노드(시작 D형·종료 원형), 입출력 연결점, 포트 라벨(충족/미충족·반복/다음·분기 N), 화살표 베지어, 선택 링, 시험 실행 경로 초록 링, "전체 보기"(처음 열 때 전체 흐름 맞춤). 선택 링은 노드 밖에 그려 연결점 위치가 바뀌지 않는다. 애니메이션 없음.
- 화면(`MainActivity.kt` 외): 헤더 상태 칩, 아이콘 탭·단계 표시, 상태 배너, 예시 카드, 편집 화면은 캔버스 + 노드 추가 도구 막대(03 수정) + 오른쪽 속성 패널(선택 노드·확인할 항목·단계 도구·시작 조건). 1100dp 미만은 세로 배치. 내 루틴 카드에 노드 아이콘 미리보기 줄, 실행 이력에 상태 아이콘, 대화상자 아이콘.
- 동작·안전 로직(실행 확인, 시트 단계 제외, 주행 제한, 저장·초안)은 바꾸지 않았다.

## 결과

| 항목 | 결과 |
|---|---|
| 단위시험 | core 106, app 103(새 `NodeVisualsTest` 8건 포함) 실패 0 |
| lint | 오류 0. 경고 15 = 기존 DataExtractionRules 1 + 온라인 의존성 최신 버전 안내 GradleDependency 14(이번 변경과 무관). 처음 실행 때 나온 미사용 아이콘 6건과 Modifier 인자 1건은 제거 |
| 계측(인자 없음) | 13클래스 28건 중 27건 실행 통과. `RunExplanationUiTest` 1건과 `SeatExcludedRunTest`·`VoicePlatformTest`는 명시 인자가 없어 건너뜀(상태 코드 -4, runner는 OK로 표시). 건너뜀은 통과로 세지 않음 |
| 계측(인자 있음) | `RunExplanationUiTest`(-e a05Fixture true, 로컬 fixture) OK, `VoicePlatformTest`(-e voiceProbe emulator-only) OK, `SeatExcludedRunTest`(-e vehicleWrites emulator-only) Console 연결 후 OK |
| 화면 | 밝은 8장(생성·확인·수정 선택·테스트·내 루틴·실행 이력·연결 둘러보기·연결 설정), 어두운 4장. Pleos 홈 오른쪽 영역에서 잘림·겹침 없음, 예시 흐름 80% 확대로 전체 표시 |

실행 통과 합계: 27(인자 없음) + 3(인자 있음) = 30건. 건너뜀은 모두 인자를 준 재실행으로 대체했다.

## 실패와 재시험

- `SeatExcludedRunTest`(vehicleWrites) 첫 실행: 조명 쓰기 요청 응답은 왔지만 상태 반영을 확인하지 못해 `UNKNOWN`으로 기록되어 실패(`avd-SeatExcludedRunTest-writes.log`). 원인: 에뮬레이터를 새로 띄운 뒤 Virtual Console(8088)을 연결하지 않았다. README·인수인계 문서에 적힌 기존 조건이다. `adb forward tcp:8088 tcp:8088` 후 Console을 열고 재실행해 통과(`avd-SeatExcludedRunTest-writes-console.log`). 앱은 반영을 확인하지 못한 쓰기를 성공으로 바꾸지 않았다.

## 남은 범위

- 제출 release APK는 이전 UI다. 새 UI의 release 빌드·서명·교체와 동일 release 10회 흐름은 사용자 요청 시 진행한다.
- 실제 차량, 주행 중 UX 제한 화면의 새 디자인 캡처(Console로 D단 전환), 분할 화면(1100dp 미만) 실기 캡처는 하지 않았다.
- 시트 이동·STOP, 실제 AI, 실제 목소리 인식, Gleo 연동은 기존 남은 게이트 그대로다.
