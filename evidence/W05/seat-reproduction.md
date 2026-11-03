# 시트 이동 미관찰 재현 기록

2026-10-28 KST / W05,R08,T08~09. 외부 문의를 발송하지 않은 로컬 재현 자료다.

## 환경

- Windows x64, NVIDIA RTX3060 12GB/driver610.88.
- Android Emulator37.2.12, 공식 Pleos Connect2.1.0/API34/revision2.
- Vehicle SDK2.3.2, Vehicle Service2.3.22, 사용자10.
- `-avd PleosRoutine -no-window -no-audio -no-snapshot -gpu host -port 5556`.
- 실제 AAOS 입력 P/0m/s, UX 제한false. Console 활성·DDS ready.
- 제품의 `seatPolicy=null`을 유지하고 별도 emulator-only 계측만 수행.

## 재현

```powershell
& .\.tools\android-sdk\platform-tools\adb.exe -s emulator-5556 shell am instrument --user 10 -w -r -e class com.pleos.routine.SeatCalibrationProbeTest -e seatCalibration emulator-only com.pleos.routine.test/androidx.test.runner.AndroidJUnitRunner
```

코드: [SeatCalibrationProbeTest](../../android/app/src/androidTest/java/com/pleos/routine/SeatCalibrationProbeTest.kt). 해당 장치가 공식 AVD이고 실제 정차 상태인 경우에만 진행한다. SDK가 반환한 capability/영역/위치 범위를 확인하고 ROW_1_LEFT의 각 축에서 FORWARD/BACKWARD를 별도로 보낸다. 예정250ms, 최대300ms 이내 STOP 전송을 검사한다. 이 시간은 계측 상한이며 제품 또는 실차 안전 한도로 확정한 값이 아니다.

## 실제 결과

- 앞뒤: 이번 회차는 capability 조회2초 timeout으로 MOVE 전에 종료. 이전 별도 회차에서는 MOVE/STOP ACK를 받았으나 위치0 유지.
- 등받이: MOVE/STOP ACK, raw position 범위0..100. 두 방향 모두 전·중·후 위치0.
- host GPU 회차의 STOP 요청은256/251ms 간격으로 관찰됐다. 이동이 선행 확인되지 않았으므로 정지된 시트의 위치 안정만으로 움직이는 시트의 STOP을 인증하지 않는다.
- 같은 APK·부팅 세션에서 공조22→23°C 변경은 실제 SDK 조회로 확인됐으므로 전체 차량 서비스가 항상 끊겨 있다는 설명과 구분한다.

[시험 원본](host-gpu-seat-test.txt), [이동/정지 관찰](host-gpu-seat-observations.txt), [대조 공조 관찰](host-gpu-hvac-observations.txt), [전체 제어 게이트](control-gate.md).

## 미확정 사항과 다음 확인

공식 Console 공개 기능표는 점유·열선/통풍을 설명하지만 앞뒤·등받이 이동 카드는 명시하지 않는다. API 공개·READ_WRITE capability만으로 에뮬레이터 물리 모델의 이동 지원을 추정하지 않는다. 해당 이미지에서 두 축의 위치를 변화시키는 공식 경로와 관찰 주기를 확인해야 한다. 위치값의 단위, SDK 연결 준비 시점, MOVE의 큐잉/취소 계약도 별도 확인 대상이다. 확인 전 임의 VHAL 주입·private API·가짜 위치값으로 시험을 통과시키지 않는다.

후속으로 내장 브라우저에서 Emulation Controls의 **48개 기능/7개 도메인**을 직접 펼쳐 확인했다. Seat Occupancy와 Seat Temperature는 있었고 앞뒤/등받이 위치 이동 항목은 없었다. 이 UI 관찰은 공개 문서와 일치하지만 SDK 내부 또는 향후 이미지에서의 지원까지 부정하지 않는다.
