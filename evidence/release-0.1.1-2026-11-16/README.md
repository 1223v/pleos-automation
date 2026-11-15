# release 0.1.1 설치·필수 흐름 10회 (2026-11-16)

대상 APK는 [apk/forky_forky_release.apk](../../apk/forky_forky_release.apk)다.
- 버전 0.1.1(2), SHA-256 `8c924c9d47bad7172db343949dba08c802492d3ae4ef705e1149ed54e8eb5214`
- 소스: `e54640e`, 버전 변경: `aff3cae`
- 0.1.0과 같은 서명 키

## 판정 요약

| 범위 | 결과 |
|---|---|
| T35 설치 | **PASS**: 업그레이드 설치, fresh install, 콜드 스타트 |
| 실행 가능 항목 10회 | **10/10 PASS**: 공조·밝기·색상 반영, 시트 미실행 기록, 음원, 타이머, 이력, 시작 방식 |
| T36 원래 기준(07 문서) | **BLOCKED (미통과)**: 실제 AI 초안, 시트 두 축 이동·STOP이 매 회 미실행 |

[07 검증 계획](../../07_VALIDATION_AND_DEMO.md#release-반복-검증과-합격-기준)의 T36은 매 회 다음 두 가지를 요구한다.
- 실제 AI 초안 생성. 준비된 응답 재생은 인정하지 않는다.
- 시트 앞뒤·등받이 두 축의 이동과 STOP 확인

두 가지 모두 이번에 실행하지 않았으므로 T36 전체 합격은 아니다. 기준을 낮추지 않고 아래에 따로 남긴다.

## 환경

- **AVD:** ForkyRelease AVD(`.local/forky-submission/avd`, `emulator-5580`)
  - Pleos Connect v2.1.0, API 34, user 10, 2560×1440
  - `-no-snapshot -gpu host`, 창 표시, 오디오 켬
- **작업용 AVD를 쓰지 않은 이유:** 작업용 AVD(`emulator-5556`)에는 debug 서명 앱과 사용자 데이터가 있다. 지우지 않으려고 종료만 했다.
- **Console:** `adb -s emulator-5580 forward tcp:8088 tcp:8088`
  - Console 백엔드는 브라우저 세션 heartbeat가 있을 때만 활성화된다(`availability.source = web-activation`). 연결 전에는 기어 값도 비어 있었다.
  - 그래서 드라이버가 Console 페이지와 같은 heartbeat(`POST /browser/session`, 1초)를 보냈다. 브라우저 Console 탭도 함께 열어 두었다.
- **권한:** fresh install 뒤 앱의 "연결 설정 → 속도·알림 권한"으로 시스템 권한 창에서 허용했다(속도는 위치 권한 그룹). 차량 제어 권한은 설치 시 허용 상태였다.

## T35 설치 ([release-install.txt](release-install.txt))

- 0.1.0 위 `install -r`: `Success`, 0.1.1(2)로 업그레이드
- 제거 후 fresh install: `Success`
- 콜드 스타트: `Status: ok`, `LaunchState: COLD`, 1,024ms, MainActivity resumed. [첫 화면](first-screen.png)

## 반복 절차 ([run_rounds.py](run_rounds.py))

회차마다 다음 순서로 진행했다.

1. **기준값 설정:** Console API로 값을 넣는다(`PUT /vehicle/gear|hvac|body`). 운전석 목표 온도, 앰비언트 밝기, 색상을 목표값과 다른 값으로 5가지 순환한다. 그 뒤 `GET /vehicle/state`로 전 상태를 기록한다.
2. **검토:** 앱에서 "루틴 만들기 → 01 생성 → 휴식 예시"를 연다. "확인할 항목"이 0건인지 본다.
3. **짝수 회차만, 시작 조건 변경:**
   - "조건 바꾸기 → 상태 조건이 새로 충족될 때 → 기어 · P"로 바꾼다.
   - 04 테스트에서 시뮬레이션을 돌린다(COMPLETED, 가상 1800초).
4. **저장:** 이름을 `Rest Rnn`으로 바꿔 비활성으로 저장한다.
5. **시작:**
   - 홀수 회차: 내 루틴의 "지금 실행"
   - 짝수 회차: "활성화"를 확인한 뒤 Console에서 기어 D로 5초 두고 P로 되돌린다.
6. **시트 단계 제외 확인:** 확인 창의 문구를 기록하고 동의한다.
7. **반영 확인:** Console 상태를 목표값(23°C, 30%, `#4b85db`)이 될 때까지 0.5초 간격으로 읽는다. 실행 중 화면을 캡처하고, 시스템 오디오 재생 목록(`dumpsys audio`, 앱 uid 1010178)을 기록한다.
8. **중지:** "실행 중지"를 누른다. 그 뒤 음원 배너, 시스템 오디오 재생 목록, 타이머 배너가 사라졌는지 본다.
9. **이력:** 실행 이력에서 최신 기록을 펼쳐 단계별 상태를 수집한다. 짝수 회차는 끝에 루틴을 "끄기"로 비활성화한다.

## 결과 ([rounds/summary.md](rounds/summary.md), 회차별 `rounds/Rnn.json`·`-running.png`·`-history.png`)

| 회차 | 시작 | 기록된 시작 원인 | Console 전→후 (공조 / 밝기 / 색상) | 앱 판정 | 시트 앞뒤/등받이 | 음원·타이머 | 실행 가능 항목 |
|---|---|---|---|---|---|---|---|
| R01 | 직접 | 직접 실행 | 20.0→23.0°C / 0→30% / #00e5ff→#4b85db | VERIFIED ×3 | SKIPPED/SKIPPED | VERIFIED, PENDING→CANCELLED | PASS |
| R02 | 조건 진입 | 자동 실행 · 기어 = P에 새로 진입 | 21.0→23.0 / 70→30 / #aa00ff→#4b85db | VERIFIED ×3 | SKIPPED/SKIPPED | VERIFIED, PENDING→CANCELLED | PASS |
| R03 | 직접 | 직접 실행 | 18.5→23.0 / 50→30 / #ffffff→#4b85db | VERIFIED ×3 | SKIPPED/SKIPPED | VERIFIED, PENDING→CANCELLED | PASS |
| R04 | 조건 진입 | 자동 실행 | 25.0→23.0 / 10→30 / #2979ff→#4b85db | VERIFIED ×3 | SKIPPED/SKIPPED | VERIFIED, PENDING→CANCELLED | PASS |
| R05 | 직접 | 직접 실행 | 16.0→23.0 / 100→30 / #00e5ff→#4b85db | VERIFIED ×3 | SKIPPED/SKIPPED | VERIFIED, PENDING→CANCELLED | PASS |
| R06 | 조건 진입 | 자동 실행 | 20.0→23.0 / 0→30 / #00e5ff→#4b85db | VERIFIED ×3 | SKIPPED/SKIPPED | VERIFIED, PENDING→CANCELLED | PASS |
| R07 | 직접 | 직접 실행 | 21.0→23.0 / 70→30 / #aa00ff→#4b85db | VERIFIED ×3 | SKIPPED/SKIPPED | VERIFIED, PENDING→CANCELLED | PASS |
| R08 | 조건 진입 | 자동 실행 | 18.5→23.0 / 50→30 / #ffffff→#4b85db | VERIFIED ×3 | SKIPPED/SKIPPED | VERIFIED, PENDING→CANCELLED | PASS |
| R09 | 직접 | 직접 실행 | 25.0→23.0 / 10→30 / #2979ff→#4b85db | VERIFIED ×3 | SKIPPED/SKIPPED | VERIFIED, PENDING→CANCELLED | PASS |
| R10 | 조건 진입 | 자동 실행 | 16.0→23.0 / 100→30 / #00e5ff→#4b85db | VERIFIED ×3 | SKIPPED/SKIPPED | VERIFIED, PENDING→CANCELLED | PASS |

- **차량값 반영:** 동의 후 Console 상태에 목표값이 반영되기까지 0.0~0.6초 걸렸다(0.5초 간격 조회). 회차 사이에 logcat의 `HVAC_TEMPERATURE_SET`·`INTERIOR_LIGHTS_AMBIENT_BRIGHTNESS` 변경 이벤트도 남겼다.
- **앱 관찰값:**
  - 공조 관찰값은 23.0, 밝기 관찰값은 30.0이다.
  - 색상 관찰값은 `-1.1827749E7`로 표시된다. `0xFF4B85DB`를 부호 있는 정수로 보여 준 것이며, 판정은 맞고 표시만 개선이 필요하다.
- **시트:**
  - 매 회 "시트 단계 제외 실행 확인"을 받았다. 시트 2단계는 "차량 명령 미전송"으로 `SKIPPED` 처리됐다.
  - Console의 운전석 slide·recline 위치값은 전후 모두 0으로 그대로였다.
- **음원:**
  - 실행 중에는 시스템 오디오 재생 목록에 앱 uid의 `AudioTrack state:started`(USAGE_MEDIA)가 있었다.
  - 중지 후에는 목록에서 사라졌고, 앱 배너도 사라졌다.
- **타이머·종료:** "30분 휴식: 남은 179x초"가 표시됐다. 중지 후 기록은 `#16 CANCELLED · 실행이 중지되었습니다`로 끝났다.
- **허용되지 않은 실행:** 0건. 시험 후 실행 이력 전체([history-list.txt](rounds/history-list.txt))는 16건이다.
  - 실제 실행 11건: R01 첫 시도 포함
  - 시뮬레이션 5건
  - 자동 실행은 짝수 회차마다 정확히 1건이다. 기어를 P로 되돌린 시각에 시작했고, 기어 D 상태에서 시작된 실행은 없다.
- **R01 재실행:** 처음 R01(01:10)은 시스템 오디오 확인을 넣기 전 스크립트로 돌렸다. 같은 절차를 보강된 스크립트로 01:29에 다시 돌렸다. 첫 기록은 `rounds/first-harness-R01*`에 그대로 남겼다. 두 실행 모두 차량값 3개가 VERIFIED였다.

## 미실행·차단 (T36 미통과 사유)

| 항목 | 상태 | 이유 |
|---|---|---|
| 실제 AI 초안 (매 회 새 문장) | BLOCKED | 사용자 결정으로 OpenAI 실제 호출이 꺼져 있다(키·예산 미설정). 이번에는 내장 휴식 예시를 썼다 |
| 시트 앞뒤·등받이 이동과 STOP | BLOCKED | 앱이 이동·STOP 교정 증거가 없으면 시트 명령을 보내지 않는다(정책). 매 회 동의를 받아 SKIPPED로 기록했다 |
| 타이머 30분 완료 | 이번 미실행 | 매 회 중지로 종료했다. 실제 30분 완료는 별도 시험(T28, [W14](../W14/long-timer-persisted-result.txt))으로 했으며, 0.1.1에서는 다시 하지 않았다 |
| 음성 입력, Gleo | 범위 밖 | T36 항목이 아니다 |

## 새로 확인한 점

1. **시트 이동 실증 가능성:** 이 AVD의 Console 카탈로그(`GET /vehicle/catalog`)에 숨김 기능이 있다.
   - 기능: `seat-fore-aft-move`·`seat-backrest-move`(Negative/Stop/Positive)와 위치값(0~100) 기능, 각각 `SEAT_FORE_AFT_MOVE`와 `SEAT_BACKREST_ANGLE_1_MOVE`에 대응한다.
   - 액추에이터 시뮬레이션(`tick_ms` 100, `step_pct` 5)이 있다.
   - "이 AVD에서는 시트 이동·정지를 재현할 수 없다"는 기존 판단을 다시 확인해야 한다. 시트 교정 시험(이동→STOP→위치 정지 확인)의 후보 경로다.
2. **좌석 점유를 읽지 않음:** 앱은 좌석 점유(`SEAT_OCCUPANCY`)를 SDK에서 읽지 않는다.
   - `ExecutionContext.seatOccupied`는 시뮬레이션에서만 채워진다.
   - 그래서 출근 예시의 "좌석이 비었다가 탑승할 때" 트리거는 실제 실행에서 참이 될 수 없다. 이번 조건 진입은 기어로 대신했다.
3. **Console 연결 조건:** Console 백엔드는 브라우저 세션이 3초 안에 heartbeat를 보내야 활성 상태를 유지한다. 탭이 백그라운드로 밀려 heartbeat가 늦어지면 앱의 쓰기 반영이 늦어지고, 그 결과 실행이 결과 불명(UNKNOWN)으로 기록된다([area-fix 기록](../area-fix-2026-11-16/README.md)의 run2).
