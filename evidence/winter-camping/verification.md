# 겨울 차박 지원서와 제품 검증

2026-11-12 KST. 11월 6일 중단 시점에 있던 것은 발화 예제, 시험 코드와 [컴파일 실패 기록](android-red.log)이었다. 11월 2일의 A05 통과 수치는 당시 APK의 결과다. 이번 회차에는 [공유 그래프](../../contracts/fixtures/winter-camping.json)와 다음 경로로 지원서의 주장을 점검했다.

| 요구 | 확인한 동작과 근거 |
|---|---|
| R03/R04/R16 · 생성/좌석 보충 질문 | 처음 요청, 읽기 전용 부분 그래프, 보충 질문·답변을 전달한다. 완성되지 않은 좌석은 빈 영역으로 남긴다. 앱은 쓰기 가능한 기능별 영역 ID와 시트 축을 보낼 수 있고 서버는 이름을 임의로 나눌 수 없는 복합 영역을 보존한다. [서버 시험](server-verification.md) |
| R14/R16/R17 · 조명을 짚어 변경 | 앱과 서버가 선택한 카드와 원래 다음 카드 사이의 추가만 허용한다. 공조·시트·트리거까지 바꾸는 제안과 기반 버전이 다른 제안은 거부한다. 기존 전체 루틴 편집은 별도로 제공한다. [코어 회귀](../../android/core/src/test/kotlin/com/pleos/routine/core/WinterCampingWorkflowTest.kt) · [서버 검사](../../server/src/selected-edit.ts) |
| R04/R17 · 조건과 시간 가상 시험 | 3°C와 경계 5°C에서만 공조 목표 23°C를 선택했다. 8°C에서도 시트와 조명 계획을 거치며, 세 경우 모두 0초에 조명 30%, 600초에 20%를 시뮬레이션했다. 외기가 미상이면 동작 전에 차단했다. 가상 시간이므로 600초를 실제로 기다린 계측이 아니다. |
| R04/R17 · 저장/재열기 | 이름 확인·계속 편집이 가능하고, 확정하면 루틴을 비활성으로 저장한다. 실제 실행과 시트 승인은 별도 진입점이다. [편집기](../../android/app/src/main/java/com/pleos/routine/MainActivity.kt) |

## 환경과 시험 방법

Windows x64, 프로젝트 로컬 Corretto 17.0.21.0.11, Gradle 8.14.6, Node 24.22.1, Vehicle SDK 2.3.2. 공식 Pleos Connect 2.1.0/API 34의 `emulator-5556` 사용자 10에서 시험한다. 콘솔 8088 포워드 후 P단, 0 km/h, DDS ONLINE을 확인했다. 에뮬레이터와 단위시험의 AI 응답은 로컬 모의값이다.

```powershell
& ./android/gradlew.bat -p android --offline --no-daemon --max-workers=2 '-Pkotlin.compiler.execution.strategy=in-process' :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug
```

## 남은 제품 인수 조건

- 실제 한국어 OpenAI 생성·질문·수정의 품질 평가에는 서버 키, 예산 및 호출 활성화 설정이 필요하다. Gleo 음성 등록·플랫폼 수신은 등록된 서비스에서 별도로 확인해야 한다.
- 시트 앞뒤와 등받이의 이동·STOP 관찰 후에만 제품의 제한 이동 정책을 설정할 수 있다. 그 전에는 시트를 포함한 겨울 차박 루틴 전체의 실제 실행을 잠근다. [시트 검증 조건](../W05/seat-reproduction.md)
- 동일 서명 release APK 필수 흐름 10회, 사용자 5~8명 조사 및 20명 파일럿은 별도로 수행해야 한다. 월 활성 12명·4주차 재사용률 40%는 측정 목표다.
