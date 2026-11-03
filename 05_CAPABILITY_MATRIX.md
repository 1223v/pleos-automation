# 기능별 공식 API 계약과 실행 게이트

> **2026-10-28 업데이트:** Vehicle2.3.2/NaviHelper3.0.5 앱이 공식 API34 AVD에서 실행된다. Service 설치값은 **2.3.22**다. 앰비언트 합친 영역0~100, 열선·앞뒤 MOVE READ_WRITE를 [재조회](evidence/W03/avd-readonly-probe-2026-10-28.txt)했다. HVAC는 초기 timeout/unavailable 이후 host GPU Cold boot·활성 Console에서22→23°C 목표값 변경 후 조회를 통과했다. SDK 온도 범위15~30°C/0.5°C와 Console 카드16~30°C는 각각의 관찰로 구분한다. 밝기·색상 상태 변경도 통과했으나 시트 두 축 위치 변화는 미관찰이다. capability와 실제 쓰기·이동·정지를 [구현 검증](evidence/implementation/2026-10-28.md) 및 [제어 게이트](evidence/W05/control-gate.md)에 구분한다. 아래 미착수 표기는 10월25일 초기 이력이다.

확인일: **2026-10-25 KST**. 문서·공식 SDK 소스 확인과 앱 실행 검증은 다르다. 현재 코드·SDK 설치·계정 권한·AVD 실행은 미착수/미확인이다. **공조·조명·시트는 모두 필수 요구사항**이며 권한을 확보하지 못하면 완료 기준을 낮추지 않는다.

## 사용자 지정 두 페이지의 적용

[Playground 홈](https://pleos.ai/playground)은 개발 시작·환경·설계·연동·테스트·등록으로 이어지는 진입점이다. Vehicle/NaviHelper/TTS와 디자인·Gleo·Publish·Workspace 안내를 제공한다. 홈의 SDK 소개는 개별 계정 권한이나 전체 차량 지원의 증거가 아니다.

[설치 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)는 개발 도구와 공식 AVD의 구성 근거다. 이 프로젝트는 홈에서 연결되는 상세 API·정책·샘플·시험 문서까지 대조하고, 설치는 12의 Windows 절차, 구현은 13의 TODO, 실제 결과는 07의 시험표로 연결한다. 계정 생성·승인과 앱 등록은 별도 게이트로 남긴다.

## 1. 증거 수준과 SDK 접근성

| 증거 | 확인할 사실 | 현재 상태 |
|---|---|---|
| DOC | 공식 문서에 API와 정책이 있음 | 아래 출처로 확인 |
| SOURCE | 특정 버전 공식 소스에 선언·타입·권한이 있음 | Vehicle 2.3.2 sources.jar 확인 |
| ACCOUNT | 사용자 프로젝트에 실제 제공/승인됨 | 미확인 |
| RUNTIME | 설치 AVD에서 권한·capability·조회·쓰기 결과 확인 | 미실행 |
| RELEASE | 제품 release APK 전체 흐름 반복 | 미실행 |
| VEHICLE | 실제 차량에서 검증 | 이번 완료 범위 밖 |

[프로젝트 생성·SDK 신청](https://document.pleos.ai/docs/pleos-only/create-my-project)은 Vehicle State·NaviHelper를 별도 신청 없이 사용 가능, Vehicle Control·STT·TTS·FusedLocation을 준비 중/신청 불가로 안내한다. 이는 공개 안내 상태이며 사용자 계정의 실제 상태를 읽은 결과가 아니다. Manifest 선언, SDK의 normal 권한 annotation, 샘플 실행 가능성만으로 프로젝트 허가·실차 사용권을 추정하지 않는다.

초기 버전 기준은 Vehicle **2.3.2**, Emulator **2.1.0**이다. Vehicle Service는 소개 **2.3.21**, 샘플 **2.3.22** 표기가 달라 설치값을 따로 기록한다. 2026-10-19 릴리스 본문은 `VehicleSpeedListener` 삭제 및 Emulator의 기존 LLM SDK 종료를 알린다. [SDK 릴리스](https://document.pleos.ai/docs/pleos-only/release-note/sdk-261015), [Emulator 릴리스](https://document.pleos.ai/docs/pleos-only/release-note/emulator-261015)

## 2. 빌드·실행 버전의 구분

| 항목 | 조사 기준 | 해석 |
|---|---|---|
| JDK / Wrapper / AGP / Kotlin | 17 / 8.10.2 / 8.8.2 / 2.0.0 | 공식 샘플 재현 출발점, 실제 빌드 확인 필요 |
| compileSdk / targetSdk | 35 / 35 | 실행 이미지 API 수준과 구분 |
| 제품 minSdk | 28 이상 | 샘플 minSdk 23을 제품에 복사하지 않음 |
| AVD | Pleos 2.1.0, Android 14/API 34 | Android 휴대폰 기본 AVD로 대체 불가 |
| Windows 이미지 | x86_64 | Mac ARM64 이미지와 구분 |
| SDK Maven 좌표 | ai.pleos.playground:Vehicle:2.3.2 | 아래 공식 저장소 |

[공식 샘플 기준](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app), [환경 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator), [Maven 설정](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup).

```kotlin
// 공식 Maven 좌표 참고. 현재 Gradle 프로젝트는 없으며 설치하지 않았다.
maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
implementation("ai.pleos.playground:Vehicle:2.3.2")
```

## 3. 공식 소스 확인 방법과 식별자

아래 함수 계약은 공식 Maven의 [Vehicle-2.3.2-sources.jar](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2-sources.jar)를 메모리에서 열어 확인했다. 파일 설치·프로젝트 적용·컴파일은 하지 않았다.

- 크기: 145,042 bytes.
- SHA-256: `62b5dd2c4c0523fd13c2e64040cd775bc89a14ce54f35a37560d0eb4eb3fab40`.
- 확인 대상: `api/HVAC.kt`, `api/Light.kt`, `api/Seat.kt`, `constant/control/MoveControl.kt`; 공통 prefix는 `ai/pleos/playground/vehicle/`.
- 후속 구현 전 동일 버전이라도 해시·실제 resolved dependency를 재확인한다. 소스와 문서가 다르면 새 출처·차이·결정을 11·10 문서에 남긴다.
- 아래 표는 비동기 선언을 요약한 계약이다. callback 전체·overload·nullable 여부는 해당 버전 소스와 IDE에서 대조한다. 이름만 보고 동기 반환값이나 미존재 메서드를 만들지 않는다.

## 4. 차량 상태 — R02

| 신호 | 경로·타입 | 권한/단위 | 필수 확인 |
|---|---|---|---|
| 기어 | AAOS CarPropertyManager, GEAR_SELECTION, Integer | Car.PERMISSION_POWERTRAIN, enum | P/R/N/D 매핑·ONCHANGE·시작 전 snapshot |
| 속도 | AAOS CarPropertyManager, PERF_VEHICLE_SPEED, Float | Car.PERMISSION_SPEED, m/s | Dangerous 권한 요청, 양수/음수·유효시각·주기 |
| 플랫폼 주행 제한 | Car UX restrictions의 대상 API | 플랫폼/지역 정책 | listener 등록·해제, 편집 제한 |

속도는 후진 시 음수일 수 있다. `speed<=0`로 정차를 판단하지 않는다. P와 유효한 절대속도, 연결·freshness를 함께 확인한다. property config null/목록 누락은 권한 부족일 수도 있어 미지원과 구분한다. API 34에서 사용 가능한 구독 경로를 확인한다. [Pleos 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api), [FAQ](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq), [Android property 정의](https://developer.android.com/reference/android/car/VehiclePropertyIds), [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager)

## 5. 공조 목표 온도 — R06

`getHvacTemperature`는 설정한 **목표 온도**, `getHvacTemperatureCurrent`는 **현재 실내 온도**다. 설정 검증에 실내 온도가 목표까지 바뀌는 것을 요구하거나 두 값을 혼동하지 않는다.

| 함수/선언 요약 | 인자·완료 callback | 선언된 permission |
|---|---|---|
| checkHvacTemperatureCapabilities | onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Float>>, HvacTemperatureConfigArray?) → Unit; onFailed: (Exception) → Unit | CAR_CLIMATE |
| getHvacTemperature | area: SeatArea, unit: TemperatureUnit; onComplete(Float?); onFailed(Exception) | CAR_CLIMATE |
| setHvacTemperature | area: SeatArea, value: Float, unit: TemperatureUnit; onComplete(SeatArea, Float?); onFailed(Exception) | CONTROL_CAR_CLIMATE |
| getHvacTemperatureCurrent | area; onComplete(Float?) 또는 unit 인자를 받는 overload | CAR_CLIMATE |
| registerHvacTemperature / unregisterHvacTemperature | area와 동일 HvacTemperatureListener 또는 HvacTemperatureValueListener 인스턴스 | CAR_CLIMATE |

permission 문자열 prefix는 `pleos.car.permission.`이다. SOURCE에 normal로 선언돼 있으나 계정·배포·capability·실행 가용성을 대체하지 않는다.

온도 bounds는 섭씨다. capability callback의 세 번째 config는 nullable이며 차량별 온도 변환·증분을 제공한다. 이전 1개/2개 완료 인자 overload는 deprecated다. 최초 앱은 섭씨 표시를 기본으로 하고 실제 지원 범위·증분을 검증한다. 23°C는 예시값이다. 설정 전 영역·접근·값을 검사하고 요청 이후 목표 온도 이벤트/GET으로 결과를 확인한다. 공조 전원 의존성이 있다면 사전 조건을 표시하며 사용자 요청에 없는 전원 제어를 몰래 추가하지 않는다. [공식 HVAC API 목록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/entry)

## 6. 앰비언트 밝기·색상 — R07

| 함수 | 주요 계약 | 선언된 permission |
|---|---|---|
| checkAmbientLightBrightnessCapabilities | 완료: CapabilityConfig, Map<SeatArea, AreaBounds<Int>> | CAR_LIGHTS |
| getAmbientLightBrightness | area; 완료 Int? | CAR_LIGHTS |
| setAmbientLightBrightness | area, value: Int; 완료 (SeatArea, Int), 실패 Exception | CAR_LIGHTS |
| checkAmbientLightBrightnessColorCapabilities | 완료: CapabilityConfig, List<SeatArea> | CAR_LIGHTS |
| getAmbientLightBrightnessColor | area; 완료 Int? | CAR_LIGHTS |
| setAmbientLightBrightnessColor | area, value: Int; 완료 (SeatArea, Int), 실패 Exception | CAR_LIGHTS |

**Vehicle 2.3.2 소스의 위 두 쓰기 함수는 `CAR_LIGHTS`를 선언한다.** 관습적으로 CONTROL_CAR_LIGHTS를 요구한다고 바꾸지 않는다. 다른 조명 함수에 그대로 일반화하지도 않는다. 실제 서비스 권한 검사와 capability는 별도로 기록한다.

밝기는 영역별 Int bounds를 사용한다. Console의 0~100% UI를 모든 차량의 원시 단위로 일반화하지 않는다. 색은 32비트 ARGB이며 alpha 0xFF를 사용한다. Kotlin Int 부호와 무관하게 32비트 패턴을 보존한다. capability의 색상 영역 목록이 모든 색상 지원을 의미한다고 추정하지 말고 AVD 반영값을 확인한다. 밝기·색상 각각 GET/변경 이벤트를 요청 응답과 대조한다. [공식 Light API 목록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/light/entry)

## 7. 시트 앞뒤·등받이와 STOP — R08

| 목적 | 앞뒤 축 | 등받이 축 |
|---|---|---|
| 위치 capability | checkSeatForeAftPositionCapabilities | checkSeatBackrestAnglePositionCapabilities |
| 이동 capability | checkMoveSeatForeAftCapabilities | checkMoveSeatBackrestAngleCapabilities |
| 현재 위치 | getCurrentSeatForeAftPosition | getCurrentSeatBackrestAnglePosition |
| 방향 이동·정지 | moveSeatForeAft | moveSeatBackrestAngle |
| 위치 구독·해제 | register/unregisterSeatForeAftPosition | register/unregisterSeatBackrestAnglePosition |

위치 capability 완료는 `(CapabilityConfig, Map<SeatArea, AreaBounds<Int>>)`이고 이동 capability는 `(CapabilityConfig, List<SeatArea>)`다. 서로 다른 반환형을 하나로 가정하지 않는다. 위치 GET은 `Int?`, 구독은 축별 listener 인스턴스를 사용한다. 조회/capability/구독 권한은 `CAR_SEATS`, 이동·STOP은 `CONTROL_CAR_SEATS`다.

두 이동 함수는 `(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl?) -> Unit, onFailed: (Exception) -> Unit)` 형태다. `MoveControl` 소스는 **BACKWARD(-1), STOP(0), FORWARD(1)**이다. 별도 `stopSeat()`를 만들어 SDK 함수라고 쓰지 않고 동일 축의 이동 API에 STOP을 전달한다. [공식 Seat API 목록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/entry)

소스 주석의 “target angle” 표현을 각도 입력 계약으로 해석하지 않는다. 실제 인자형은 MoveControl이며 위치값 Int도 도·mm라는 근거가 없다. 영역별 방향과 위치 변화의 의미를 시험한다.

필수 게이트: 두 축 각각 위치/이동 capability, 실제 방향 이동, 위치 변화, 제한시간 STOP, 정지 관찰, 실패·취소 경로. 매 실행 확인·한 축씩 실행·범위 경계·늦은 callback 무효화가 필요하다. P단 해제는 이동 차단 사유지만 STOP 차단 사유가 아니다. 연결·권한 상실로 STOP이 불가능하면 성공 표시하지 않는다. 프로세스 사망 때 정지가 보장된다는 근거도 없다. 정책과 미확정 수치 결정은 [기술 설계](04_TECHNICAL_ARCHITECTURE.md)를 따른다.

## 8. 공통 실행 수명과 실패 구분

SDK 초기화 호출 반환만으로 연결 완료를 판단하지 않는다. 초기화→capability→첫 GET→구독/콜백→해제 증거를 확보한다. capability의 NONE/READ/WRITE/READ_WRITE, 영역, bounds, permission과 현재 가용성을 각각 기록한다. null·SecurityException·일시불가·timeout·미지원은 구분한다. [Capability](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/check-capability), [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission), [연결 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro)

SET ACK는 상태 반영 증거가 아니다. 공식 샘플도 읽은 값과 SET 결과를 분리한다. GET/이벤트·명령시각·관찰시각·영역을 대조한다. 소유한 동일 listener로 해제하고 연결 세대가 바뀌면 기존 capability·승인·지연 callback을 무효화한다. [샘플](https://github.com/Pleos-Playground/pleos-sample-vehicle-app)

샘플 저장소 README는 PublicApiPolicyTest의 이전 2.2.10/441개 기준과 2.3.2/442개 API의 불일치를 알려준다. 샘플 검사 실패를 제품 실패와 구분해 기록하고 임의 skip/기대값 완화로 숨기지 않는다. 후속 사용 시 실제 commit과 수정 여부를 다시 확인한다.

## 9. Virtual Console로 증명할 수 있는 것

Console의 P/R/N/D, 목표 온도, 밝기·색상, 탑승 상태 등은 **입력 재현** 경로다. 앱의 쓰기 권한을 증명하지 않는다. 앱 명령 후 검증할 때 Console에서 직접 같은 값을 바꿔 성공한 것처럼 만들지 않는다.

사용 가이드에는 Gear Selection과 Cruise Target Speed가 있지만 실제 `PERF_VEHICLE_SPEED` 입력 카드는 명시돼 있지 않다. 셋업 소개는 속도 시뮬레이션을 언급하므로 실제 AVD의 입력 방법을 별도로 확인한다. 크루즈 목표 속도로 실제 속도 시험을 대체하지 않는다. Seat Occupancy/Seat Temperature는 시트 위치 이동 시험을 대체하지 않는다. 시트 위치·이동 재현 역시 런타임 확인 과제다. [Console 사용](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide), [Console 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)

## 10. 선택 기능과 런타임 기록

Gleo는 우리 앱 기능의 승인된 음성 진입점이며 임의 외부 앱 제어권이 아니다. schema 3개 actionCalls, 지원 타입·필수인자 누락 가능성, assets/schema.json·승인 절차는 선택 도입 시 재확인한다. STT/TTS/위치의 API 존재와 신청 상태는 구분한다. NaviHelper는 기본 Navigation 연동이며 Melon·카카오내비 임의 제어 계약으로 해석하지 않는다. [Gleo schema](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability), [전달](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/deliver-schema)

| 기록 필드 | 현재 값 |
|---|---|
| 사용자 Workspace SDK 상태/승인 근거 | 미확인 |
| Windows/AVD/SDK/Service/샘플 commit | 런타임 미확인 |
| 속도 입력 경로·권한·주기 | 미실행 |
| 공조·조명·시트 영역별 read/write/관찰 결과 | 미실행 |
| 시트 두 축 제한값·STOP ACK·정지 관찰 | 미실행 |
| 제품 APK/소스 commit/해시 | 미생성 |

공식 문서 사실을 런타임 통과로 옮겨 적지 않는다. 각 필드의 실제 증거는 [검증](07_VALIDATION_AND_DEMO.md)·[TODO](13_IMPLEMENTATION_TODO.md)에 연결한다.
