# 시트 이동·STOP 검증 (2026-11-16)

R08 / W05·W13 / T08·T09·T19. 이번 작업은 원인 확인과 기록이며 제품 코드는 바꾸지 않았다. `VehiclePlatform.seatPolicy`는 계속 `null`이고, 앱의 시트 이동 잠금도 유지한다.

## 결론

| 경로 | 결과 | 근거 |
|---|---|---|
| 앱 SDK MOVE/STOP → 차량 시스템(VHAL) | **동작** | MOVE·STOP 응답(ACK)을 받았다. `SEAT_FORE_AFT_MOVE`·`SEAT_BACKREST_ANGLE_1_MOVE` 변경 이벤트(1/-1/0)가 시스템 `VEHICLE_CONTROL`에 기록됐다 |
| VHAL → Console 시트 모델 | **동작** | 앱이 FORWARD를 보내면 Console 위치가 +, BACKWARD면 −로 움직였다. STOP 뒤에는 그 위치에서 멈춰 유지됐다. 두 축, 두 방향, 2회 모두 재현했다 |
| Console 시트 위치 → VHAL → 앱 SDK 위치 조회 | **동작 안 함** | `getCurrentSeat*Position`은 28/28회 모두 0을 돌려줬다. `SEAT_*_POS` 변경 이벤트는 0건이었다. Console이 위치를 30·50으로 직접 넣어도 SDK는 0을 읽었다 |

**판정**
- 앱이 보낸 이동·정지 명령이 에뮬레이터의 시트 모델을 실제로 움직이고 멈춘다는 것은 처음으로 확인했다. 10월 28일 기록의 "위치 0 유지, 이동 미관찰"과 다른 결과다.
- 다만 앱은 SDK로 위치를 되읽어 이동과 정지를 확인해야 한다. 이 이미지는 그 피드백을 주지 않으므로 앱 안의 정지 확인은 통과할 수 없다(결과 불명).
- 규칙("정지 확인 실패는 결과 불명이며 성공으로 기록하지 않는다", "가짜 위치값으로 시험을 통과시키지 않는다")에 따라 시트 잠금을 풀지 않는다. R08은 계속 **미통과**다.

## 환경

- 작업용 AVD `emulator-5556`(PleosRoutine): Pleos Connect v2.1.0, API 34, user 10, `-no-snapshot -gpu host`, 창 표시
- 기어 P, 0 m/s, UX 제한 false
- 같은 소스(`b4f7b33`)의 debug 0.1.1 APK와 시험 APK를 `install -r`로 설치했다. 사용자 데이터는 유지했다.
  - debug SHA-256 `e2f9b60f…5412`
  - test SHA-256 `f51dc6d3…1342`
  - [빌드 로그](build.log)
- Console: `adb -s emulator-5556 forward tcp:8088 tcp:8088`, 브라우저 Console 탭을 열어 둠. 감시 스크립트가 Console 페이지와 같은 heartbeat를 보냄.

## 절차

1. **A단계 (사전 조사, 앱 없음):** ForkyRelease AVD의 Console에서 숨김 카탈로그 기능 `seat-fore-aft-move`로 `PUT /vehicle/seat?kind=move&control=slide&area=driver&value=1`을 보냈다.
   - 위치가 0→10→…→60으로 0.2초마다 10씩 증가했다(액추에이터 `tick_ms` 100, `step_pct` 5).
   - `value=0`을 보내자 60에서 멈췄다.
   - 끝난 뒤 위치를 0으로 되돌렸다.
   - 이 단계는 Console 모델이 존재하는지 확인한 것일 뿐이며 시험 통과 근거로 쓰지 않는다.
2. **B단계 (검증):**
   - Console 위치 publish로 운전석 앞뒤·등받이 기준 위치를 정했다(1회차 50, 2회차 30). 0에서 시작하면 한쪽 방향 이동을 관찰할 수 없기 때문이다.
   - 기존 계측 [SeatCalibrationProbeTest](../../android/app/src/androidTest/java/com/pleos/routine/SeatCalibrationProbeTest.kt)를 그대로 실행했다. ROW_1_LEFT, 축마다 FORWARD와 BACKWARD 각각 250ms 뒤 STOP, 최대 300ms다.
   - 동시에 [console_seat_watch.py](console_seat_watch.py)로 Console 시트 상태를 50ms 간격으로 기록했다.

```powershell
& .\.tools\android-sdk\platform-tools\adb.exe -s emulator-5556 shell am instrument --user 10 -w -r -e class com.pleos.routine.SeatCalibrationProbeTest -e seatCalibration emulator-only com.pleos.routine.test/androidx.test.runner.AndroidJUnitRunner
```

## 관찰 (1회차: 기준 50, [시험](probe-run1.txt) · [앱 기록](probe-run1-observations.txt) · [Console](console-watch-run1.txt) · [logcat](logcat-run1-full.txt))

| 축·명령 | 앱 SDK (MOVE ACK / STOP 전송 / 위치) | Console (move 값 / 위치) |
|---|---|---|
| 앞뒤 FORWARD | ACK ok, STOP 252ms, 위치 전·중·후 0 | 1 → 위치 50→55→60 → STOP(0) 후 60 유지 |
| 앞뒤 BACKWARD | ACK ok, STOP 251ms, 위치 0 | −1 → 60→55→50 → 0 후 50 유지 |
| 등받이 FORWARD | ACK ok, STOP 252ms, 위치 0 | 1 → 50→55→60 → 0 후 60 유지 |
| 등받이 BACKWARD | ACK ok, STOP 253ms, 위치 0 | −1 → 60→55→50 → 0 후 50 유지 |

- **2회차**(기준 30, [시험](probe-run2.txt) · [앱 기록](probe-run2-observations.txt) · [Console](console-watch-run2.txt) · [logcat](logcat-run2-full.txt)): 같은 양상이 반복됐다. 30→40→30 이동 후 정지했고, 앱 위치는 0이었다.
- **시험 결과:** 두 회차 모두 `Tests run: 2, Failures: 2`였다. 실패 사유는 "Both directions must show raw position changes; ACK alone is insufficient"로, 앱이 위치 변화를 관찰하지 못한 것이다. 시험 코드가 의도한 대로 판정했다.
- **움직인 시간:** Console이 move≠0을 본 시간은 약 190~300ms였다(50ms 간격 조회와 HTTP 왕복 오차 포함). 위치는 매번 두 틱(+10/−10) 움직였다. 앱의 MOVE→STOP 전송 간격 약 250ms와 맞는다.
- **방향 대응:** FORWARD는 위치 증가, BACKWARD는 감소다. 이것은 Console 원시 위치(0~100)의 방향일 뿐이며 거리나 각도가 아니다.

## 다음 확인 (선택지)

1. **현행 유지 (권장 기본값):** 시트 잠금과 실행별 "시트 단계 제외 실행"을 유지한다. R08은 "명령 경로 확인, 위치 피드백 없음"으로 남긴다.
2. **Pleos에 문의:** Connect v2.1.0 이미지에서 Console 시트 위치(`SEAT_FORE_AFT_POS`, `SEAT_BACKREST_ANGLE_1_POS`)가 VHAL로 전달되지 않는지, 그리고 숨김 기능의 지원 여부를 묻는다. 외부 발송이므로 사용자가 결정한다.
3. **정지 확인 기준 변경 검토:** 위치 대신 SDK로 `SEAT_*_MOVE` 값을 되읽어 STOP(0)을 확인하는 방안이다. 하지만 이는 "차량이 정지 명령 상태를 보고함"이지 "시트가 멈춘 것을 관찰함"이 아니므로 안전 계약을 바꾸는 일이다. 사용자 결정 없이 구현하지 않는다.
