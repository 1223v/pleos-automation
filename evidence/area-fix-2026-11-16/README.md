# 휴식 예시 영역 별칭 차단 수정 (2026-11-16)

## 증상
- 휴식 예시 "잠깐 쉬어가기"의 실제 실행이 공조 단계에서 `BLOCKED`로 끝났다.
- 메시지: `SET_HVAC_TARGET: 읽기·쓰기 권한과 DRIVER 영역을 확인하세요. (READ_WRITE)` ([run1](run1-blocked-driver-alias.png))

## 원인
- `WorkflowExamples.rest()`와 `commute()`의 차량 단계가 영역을 지정하지 않았다. 그래서 `ActionSpec.area`의 기본값 `"DRIVER"`가 들어갔다.
- `DRIVER`는 SDK의 `SeatArea`가 아니다. 대상 AVD는 다음 영역만 반환했다.
  - 공조: `ROW_1_LEFT`, `ROW_1_RIGHT`
  - 앰비언트: `ROW_1_LEFT+ROW_1_RIGHT+ROW_2_LEFT+ROW_2_CENTER+ROW_2_RIGHT`
- `VehiclePlatform.preflight`가 의도대로 이 실행을 차단했다.
- 검토 단계(`WorkflowValidator`)는 영역을 차량 지원 목록과 대조하지 않았다. 그래서 문제가 저장 뒤 실행할 때에야 드러났다.

## 변경
- `core/Examples.kt`: 휴식 예시는 공조·시트 단계에 `ROW_1_LEFT`, 앰비언트 단계에 실내 전체 영역을 명시했다. 출근 예시의 열선 단계에는 `ROW_1_LEFT`를 명시했다.
- `app/vehicle/EditableVehicleAreas.kt`: `vehicleAreaIssues()`를 추가했다. 차량 제어 단계를 두 경우에 "확인할 항목"으로 표시한다.
  - `DRIVER` 별칭이 들어 있을 때
  - 차량이 보고한 READ_WRITE capability의 영역 목록에 없을 때

  capability가 아직 조회되지 않았거나 쓰기 권한이 없으면 판단을 preflight에 맡긴다.
- `MainActivity.kt`: 검토 항목에 `vehicleAreaIssues`를 합쳤다. 이 항목이 있으면 저장과 실제 기능 테스트가 비활성화된다.

## 시험 (실행함)
- Red: `:core:test --tests ExampleAreasTest`에서 2개 실패. `:app:testDebugUnitTest --tests EditableVehicleAreasTest`는 미구현 참조로 컴파일 실패. 로그: [unit-red.log](unit-red.log)
- Green: `:core:test :app:testDebugUnitTest :app:assembleDebug` 성공. core 108개, app 106개, 실패 0. 로그: [unit-green.log](unit-green.log)
- debug APK SHA-256: `61975a000963f79e77acb2b03cdd863657883b518e17fd55cd1c4c1e9bdfa830`

## 공식 에뮬레이터 (emulator-5556, API 34, user 10)
- run2 (00:42:39 KST, Console 탭 숨김):
  - 공조 단계가 REQUESTED → ACKNOWLEDGED → `UNKNOWN`으로 끝났다.
  - logcat상 `HVAC_TEMPERATURE_SET=23.0` 반영은 약 48초 뒤, Console 페이지를 다시 활성화한 시점에 일어났다.
  - 앱은 성공으로 기록하지 않았다. ([run2](run2-unknown-console-hidden.png))
- run3 (00:44:07 KST, Console 탭 표시):
  - 공조 `ROW_1_LEFT` 23°C, 앰비언트 밝기 30%, 앰비언트 색상 `#FF4B85DB`, 자체 음원이 모두 `VERIFIED`였다.
  - 시트 2단계는 사용자 동의로 `SKIPPED`(명령 미전송)였다.
  - 30분 타이머는 진행 중이었다. ([run3](run3-running-verified.png))

## 남은 위험
- 별칭 `DRIVER`가 들어간 채로 이미 저장된 루틴은 그대로 남아 있다. 열면 이제 "확인할 항목"으로 표시되지만, 자동으로 고치지는 않는다.
- 실행 이력의 색상 관찰값이 부호 있는 정수(`-1.1827749E7`)로 표시된다. 판정은 정상이고 표시만 개선이 필요하다.
- Console 탭이 백그라운드에 있으면 쓰기 반영이 늦어진다. 에뮬레이터 시험 중에는 Console을 화면에 띄워 둬야 한다.
- 이번 결과는 debug APK 1회 실행이다. release 10회 반복 기준은 충족하지 않았다.
