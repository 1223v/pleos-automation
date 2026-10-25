# Pleos와 사용자 정의 루틴에 대한 사실 검증

검증 기준: **2026-10-25 KST**. 사용자가 제공한 설명을 공식 상품 발표와 개발 문서로 대조했다. 발표 시점의 확인, 현재 문서상 제공 상태, 실제 계정 권한과 실차 검증을 구분한다. 이번 작업에서는 계정 로그인·SDK 실행·실차 시험을 하지 않았다.

## 판단

**플랫폼 설명은 대체로 맞지만, ‘개발자가 지금 공조·시트·여러 앱을 자유롭게 자동 제어할 수 있다’는 결론은 근거가 부족하다.** 사용자 확정 목표는 공조·조명·시트를 모두 포함한 에뮬레이터 APK다. 목표는 유지하며 실제 쓰기·상태 반영·시트 정지 증거를 선행 조건으로 확인한다. 제어가 막힌 상태를 축소 MVP 완료로 처리하지 않는다.

| 인용된 주장 | 판정 | 정확한 해석 |
|---|---|---|
| Gleo가 한 번의 음성으로 공조·조명·라디오 실행 | 확인 | 2026-04-30 공식 발표의 복합명령 예시와 일치 [P1] |
| 외부 앱 설치와 여러 앱 화면 사용 | 확인 | App Market 다운로드, 두 화면 영역의 서로 다른 앱 실행. 앱 내부 기능의 임의 제어를 뜻하지 않음 [P1] |
| 멜론·카카오내비 포함 18종 | 시점 한정 확인 | 2026-09-09 공식 기사 기준 국내 18종. 10-25 실시간 총개수는 확인하지 않음 [P2] |
| 갤럭시처럼 조건과 동작을 저장하는 사용자용 루틴 | 공식 지원 미확인 | 공식 검색·상품/개발자료에서 확인하지 못함. 존재하지 않는다고 증명한 것은 아님 [P3] |
| Gleo의 앱 실행은 모두 미래 기능 | 최신 자료와 불일치 | 9-29 인터뷰는 일부 프리로드 앱 음성 연동과 현재 앱 실행 구조를 설명. 일반 서드파티 전체 제어는 별개 [P3] |
| 테슬라보다 앱 확장성이 우수 | 비교 근거 부족 | 현대차가 개방성과 서드파티 생태계를 강조하는 것은 확인. 동일 조건 경쟁 비교는 하지 않음 [P4] |
| 공조·시트·조명 제어 API가 있다 | 문서상 확인 | 메서드는 존재하나 일반 개발자 쓰기 승인과 실차 실행을 보장하지 않음 [D1][D2] |
| 도어·창문·핸들 열선 제어도 있다 | 도메인 설명 확인 | Vehicle 소개에 조회/제어 영역으로 명시. 모든 차종·앱에서 사용 가능한지는 별도 문제 [D5] |
| 주행 모드·주행거리·브레이크·EV 상태 조회 | 도메인 설명 확인 | DrivingMode·Odometer·Brake·EvBattery가 명시됨. 개별 값과 권한은 대상 환경에서 검증 [D5] |
| 상태 변화 Callback이 있다 | 확인 | 구독/해제 및 실패 통지가 문서화됨. 상시 백그라운드 권한을 뜻하지 않음 [D3] |
| 개발자가 모든 SDK를 지금 사용 가능 | 부정확 | 신청 안내는 State/NaviHelper 사용 가능, Control/STT/TTS/위치는 준비 중으로 구분 [D1] |
| 클라우드 Vehicle Data/Fleet로 인카 루틴 제어 가능 | 혼동 주의 | 인카 SDK와 다른 데이터/차량관리 서비스. Vehicle Data는 문서의 2026-08 기준 유럽 제공 [P5][P6] |
| ADAS·위치 SDK가 모두 공개돼 바로 사용 가능 | 확인 부족 | ADAS는 현재 SDK 개요 목록에 없음. 위치 API 문서와 신청 상태는 서로 다름 [D2][D4] |
| 실차 없이 Emulator/Devbox로 개발 가능 | Emulator 확인, Devbox 접근 미확인 | 공식 에뮬레이터 개발·검증 절차는 존재. Devbox의 현재 제공 대상·대여 조건은 이번 조사에서 확인하지 않음 [D5] |

## Gleo와 출품작의 관계

Gleo가 여러 명령을 해석·실행하는 기능은 이미 있다. 2026-09-29 공식 인터뷰는 앱 연동 현황과 함께 반복 습관 기억·선제 제안을 향후 방향으로 설명한다. 따라서 ‘AI로 여러 기능을 켠다’만으로 차별성을 주장하지 않는다. [Gleo 개발자 인터뷰][P3]

이번 작품의 차별화 가설은 **사용자가 자연어로 만든 조건·동작을 명시적인 규칙으로 저장하고, 검토·수정·활성화한 뒤 반복 실행과 실패 이유를 관리하는 것**이다. 이는 조사에서 도출한 제품 제안이며, 시장 최초 또는 특허 신규성을 확인한 주장이 아니다.

## API 문서와 실제 이용 가능성의 차이

네 단계를 별도로 확인해야 한다.

1. **문서:** API와 버전이 명시돼 있는가.
2. **프로젝트:** 계정/프로젝트가 해당 SDK를 사용할 수 있는가.
3. **배포:** 선언한 권한과 앱 기능이 사전 협의·심사를 통과하는가.
4. **차량:** 해당 차종·지역·좌석 영역에서 지원하고 실제 상태가 바뀌는가.

예를 들어 FusedLocation 1.1.3 상세 API와 릴리스가 존재해도 프로젝트 신청 가이드는 준비 중으로 안내한다. 문서 업데이트 차이, 특정 파트너 제공, 에뮬레이터 한정 여부는 공개 자료만으로 결론낼 수 없다. **문서에 있으니 사용 가능, 또는 준비 중이니 영구 불가능이라는 두 단정 모두 피한다.** [프로젝트 신청][D1], [위치 API][D4]

## 원래 낮잠 예시의 현실적인 분해

| 요청 | 기본 출품 범위 | 확장 조건 |
|---|---|---|
| P단 + 13~15시 + 낮잠 선택 | 목표 환경에서 기어/속도 읽기 검증 후 적용 | 차량 신호 접근 불가 시 실행 차단 |
| 공조 23°C | 필수 목표 온도 설정 | 쓰기 권한·영역·범위·단위와 목표 온도 반영 확인 |
| 앰비언트 밝기·색상 | 둘 다 필수 | 함수별 권한·capability와 각각의 상태 반영 확인 |
| 시트 뒤로 | 앞뒤·등받이 제한 이동과 STOP 모두 필수 | 축·방향 확인, 매 실행 동의, 이동·정지 관찰; 각도/거리 추정 금지 |
| 멜론 수면 음악 | MVP는 권리 확보한 앱 자체 음원 | 공식 Melon 연동 계약/API 확인 |
| 30분 알림 | 앱이 활성화된 휴식 세션에서 로컬 타이머 | 백그라운드·시동 OFF 보장은 별도 검증 |
| 이후 동일 조건에서 재실행 | 활성 세션에서 저장 규칙 평가 | 시스템 상주/부팅 복원은 정책·수명 검증 후 확대 |

기술별 구체적 근거는 [기능 가능 범위](05_CAPABILITY_MATRIX.md), 설계는 [제품 기획](03_PRODUCT_PRD.md)에 있다. 공식 SDK 2.3.2 소스에서 MoveControl.STOP, 목표 온도와 실내 온도의 별도 조회, 조명 쓰기의 함수별 권한 선언을 추가 확인했다. 메서드 존재는 대상 AVD 실행 성공을 뜻하지 않는다. 출처·아티팩트 해시는 [공식 출처](11_SOURCES.md)의 D35에 기록한다.

## 검증 범위와 남은 한계

공모전은 브라우저로 개요·일정·제출·개발·심사·규정·숏폼·FAQ·공지 3건·약관·개인정보를 읽었다. 상품 기능은 현대차그룹/현대차/Pleos 공식 사이트에서 Pleos/플레오스와 routine/routines/루틴/자동화를 조합해 조회하고 해당 발표를 확인했다. 일반 사용자 메뉴를 직접 조작한 시험이나 국내 모든 출시 차종 검증은 아니다. 자료 변경 시 [출처 목록](11_SOURCES.md)을 기준으로 재확인한다.

[P1]: https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-group-redefines-in-vehicle-experience-with-pleos-connect-next-generation-infotainment-system
[P2]: https://www.hyundaimotorgroup.com/ko/news/42dot-pleos-connect-app-market-new-apps
[P3]: https://www.hyundaimotorgroup.com/ko/story/42dot-gleo-ai-developer-present-and-future
[P4]: https://www.hyundai.com/worldwide/en/newsroom/detail/0000000921
[P5]: https://document.pleos.ai/en/api-reference/vehicle-data-api/intro
[P6]: https://document.pleos.ai/en/api-reference/fleet-api/intro
[D1]: https://document.pleos.ai/docs/pleos-only/create-my-project
[D2]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/
[D3]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/callback
[D4]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro
[D5]: https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
