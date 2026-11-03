# Console 입력 → 실제 AAOS 속도·기어 → 편집 제한

2026-10-28 KST / W04,W09,W23 / R02,R04,R10 / T03,T04,T15,T32.

## 경로와 환경

공식 Pleos Connect2.1.0/API34, Vehicle SDK2.3.2, Service2.3.22, emulator-5556/user10. Cold boot·host GPU. 사용자가 연 `http://127.0.0.1:8088/` 탭을 내장 브라우저 도구로 직접 관찰하고 조작했다. 과거 Windows Chrome URL 식별 실패와 다른, URL이 식별된 탭 경로다. 세션 유지용 보조 프로세스를 실행하거나 브라우저 보호를 끄지 않았다.

Console Gear Selection 및 ACCEL/BRAKE → DDS actual gear/speed → AAOS GEAR_SELECTION/PERF_VEHICLE_SPEED(m/s) → VehiclePlatform.snapshot → CarUxRestrictionsManager → 편집 UI 순서로 확인했다. Cruise Target Speed·앱의 가짜 ExecutionContext·직접 VHAL 값 주입을 사용하지 않았다. `cmd car_service help`는 user 빌드 제약으로 [거부](car-service-user-build-limit.txt)돼 사용하지 않았다.

## 실행 결과

1. P/0km/h에서 루틴 이름 편집 창을 열고 `CONSOLE_READY` 확인.
2. Console D 클릭, ACCEL 약1.6초 입력. 화면 최고 관찰28km/h. 입력 해제 뒤 감속했으므로 다른 시각의 Console·스크린샷·SDK 숫자가 동일해야 한다고 가정하지 않는다.
3. 앱 계측에서 source=REAL, gear=D, speedMps=1.6666667461395264, uxRestricted=true 확인. 여기서 REAL은 앱의 실제 AAOS 입력 경로이며 실차 주행을 뜻하지 않는다.
4. 편집 창 닫힘, 내부 제한 UI, 디스크 초안 불변, 새 루틴 실행 없음 확인. [실제 AVD 화면](app-driving-restriction.png)에는 약15km/h와 Pleos 시스템의 ‘주행 중에는 사용할 수 없습니다’ 차단 화면도 표시됐다.
5. Console BRAKE 약1.2초 입력,0km/h 확인 후 P 클릭. 앱의 parkedAndStationary 및 편집 UI 복귀 확인.

[instrumentation 원본](console-driving-ui-test.txt): **1PASS /55.094초**. [주행 중 Console read-only 상태](console-driving-state.json). 시험은 차량 쓰기 루틴을 시작하지 않았다. 실행 중 시트 STOP·공조 중단·권한 상실·연결 단절을 검증한 것으로 확대하지 않는다.

## 함께 수정한 초안 저장

그래프와 입력 요청을 기존 drafts 테이블의 서로 다른 키에 Room 트랜잭션으로 저장한다. 스키마 변경 없이 기존 working 그래프를 읽는다. 편집 가능 시350ms debounce, 제한 시 즉시 저장한다. 저장 중/완료/실패를 구분하고 DB 완료 전 성공 안내를 하지 않는다. 제한 중 늦게 도착한 AI 제안도 표시하지 않으며 진행 중 확인 요청은 취소한다.

[입력 요청 복구 시험](../W23/typed-draft-recreate-test.txt)은 Activity 재생성 뒤 요청·그래프 일치, 실행 없음 **1PASS**다. 주행 전환 시험의 그래프 보존과 별도 결과다.
