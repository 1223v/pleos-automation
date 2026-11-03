# W05 제어 검증 게이트 — 2026-10-28

판정: **미통과**. R06 공조·R08 시트가 필수이며 선택 기능으로 내리지 않는다. 현재 production `seatPolicy`는 null이다.

## 대상과 관찰

Windows x64, 공식 Pleos Connect v2.1.0 Android14/API34, serial `emulator-5556`, 사용자10. Vehicle SDK2.3.2 / Vehicle Service2.3.22. 실제 AAOS 조회는 P, 0.0m/s, UX 제한 false였다. Virtual Console 크루즈 목표값이나 가짜 센서 입력을 사용하지 않았다.

| 제어 | 요청·관찰 | 판정 |
|---|---|---|
| 공조 목표 온도 | 초기 목표값 unavailable. host GPU Cold boot·활성 Console에서 앞줄 왼쪽22→23°C 요청 후 SDK23°C 관찰 | 개별 정상 경로 PASS. 초기 capability timeout·오류 경로는 미완료 |
| 앰비언트 밝기 | 반환된 합친 좌석 영역, 범위0~100. 70→20→30 및30→20→30을 실제 조회 | 개별 정상 경로 PASS |
| 앰비언트 색상 | 같은 영역. #FFDB854B→#FF4B85DB 관찰. RGB24 반환의 생략된 alpha만 fixed-FF로 정규화 | 개별 정상 경로 PASS |
| 시트 앞뒤 | ROW_1_LEFT, FORWARD/BACKWARD 각각 ACK. 이동 전·중·STOP 후 raw position 모두0 | FAIL. MOVE ACK는 이동 증거가 아님 |
| 시트 등받이 | 같은 영역·두 방향, MOVE/STOP ACK. 위치 모두0 | FAIL. 움직이는 시트의 STOP 실증 아님 |

[공조·밝기 원본](permission-retest-observations.txt), [색상·앞뒤 원본](../implementation/avd-final-observations-2026-10-28.txt), [등받이 원본](seat-observations.txt), [SDK 읽기 probe](../W03/avd-readonly-probe-2026-10-28.txt).

후속 [host GPU 공조1PASS](host-gpu-hvac-test.txt) / [22→23°C 관찰](host-gpu-hvac-observations.txt), [같은 환경의 시트2FAIL](host-gpu-seat-test.txt) / [위치 관찰](host-gpu-seat-observations.txt)을 우선한다. 공식 Console 활성 상태만으로는 공조가 통과하지 않았고 Cold boot 후 통과했다. GPU와 재부팅의 효과를 분리한 인과 실험은 아니므로 단일 원인을 확정하지 않는다.

## 실험 상한과 제품 정책의 구분

시트 교정 probe는 공식 AVD 식별과 `seatCalibration=emulator-only` 인자를 모두 요구한다. raw 위치의 단위는 미확정으로 mm/각도를 붙이지 않는다. 한 축씩 FORWARD/BACKWARD를 요청하며 예정250ms, 최대300ms 이내 STOP 요청 여부를 검사한다. 관찰된 STOP 요청 간격은 앞뒤279/257ms, 등받이275/293ms였다. 이 값은 에뮬레이터 계측 상한으로, 제품의 안전 이동시간이 아니다.

각 STOP ACK 후 raw 위치3회가0으로 같았지만 앞선 이동이 관찰되지 않았다. 따라서 움직이는 시트의 정지 성공으로 해석하지 않는다. 늦은 응답·STOP 불명·복구 기록 불명에서 새 MOVE를 막는다.

## SDK 재연결에서 남은 계약

공식 2.3.2 `BaseServiceConnector.kt`는 연결 준비 전 또는 DeadObjectException의 호출을 queue에 보관하고 연결 후 소비한다. 앱에 재시도 루프가 없다는 사실만으로 SDK의 과거 MOVE/쓰기 재전송이 없다고 주장할 수 없다. `Vehicle.release()`가 queue clear·unbind로 이어지는 것은 확인했으나, 진행 중 전달과 STOP 사이의 경쟁·재연결 실패는 미검증이다. UI의 차량 요청 실패 안내도 SDK 처리 결과 불명으로 표시한다.

공개 취소·연결·STOP 경로의 검증 없이 private reflection, 임의 권한, 임의 위치 단위로 이 게이트를 우회하지 않는다. 근거는 [D68~71](../../11_SOURCES.md)이다.

## 다음 확인 조건

1. 초기 Console HTTP 준비 요청은 `emulator_inactive`였다. 이후 활성 연결과 SDK 공조22→23°C 변경 관찰을 확보했다. 초기 연결 timeout·실제 속도 입력 연결 검증은 남아 있다.
2. 동일 AVD에서 두 시트 축의 실제 위치 변화 경로를 확보하고, 한 축씩 제한 이동과 STOP 후 정지를 관찰한다.
3. 주행 전환·취소·연결 단절·늦은 callback·프로세스 종료 뒤 재연결 시험으로 새 MOVE 차단과 결과 불명 처리를 검증한다.
4. 근거 있는 영역·방향·시간·정지 판정 정책을 고정한 뒤에만 제품 시트 이동을 활성화한다.

Console 세션 보조 프로세스는 자동 승인 검토가 `blocked by policy`로 거부하여 실행되지 않았다. Windows Computer Use도 현재 브라우저 URL을 확인하지 못해 종료되어 추가 UI 조작을 수행하지 않았다. 이번 기록은 해당 두 제한을 우회했다는 뜻이 아니다.
