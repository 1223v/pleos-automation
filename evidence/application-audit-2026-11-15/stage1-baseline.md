# 1단계 — 기준선 재현과 11월 12일 중단 지점 마무리

2026-11-15 KST. Codex 작업은 11월 12일 01:55에 멈춰 있었다. 마지막 기록은 에뮬레이터 표적 시험 2건 실패였고 재시험이 없었다([당시 로그](../winter-camping/avd-target-first.log)).

## 환경

Windows x64, Corretto 17.0.21.0.11, Gradle 8.14.6, Node 24.22.1. 공식 Pleos Connect v2.1.0(API 34) `emulator-5556`, 사용자 10, 화면 2560×1440·160dpi. Vehicle SDK 2.3.2, NaviHelper 3.0.5. 기동 옵션은 `-no-window -no-audio -no-snapshot -gpu host -port 5556`. AI 응답은 모두 로컬 모의값이며 실제 OpenAI 호출은 0회다.

## 결과

| 수준 | 실행 | 결과 |
|---|---|---|
| UNIT·모의 | 서버 `npm run typecheck`, `npm test` | 61건 통과 ([로그](server-baseline.log)) |
| UNIT | `:core:test :app:testDebugUnitTest` | core 102건, app 77건 통과 ([빌드 로그](android-baseline.log)) |
| 빌드 | debug, androidTest, release(서명 없음), lintDebug | 성공. APK 해시는 [baseline-apk-hashes.txt](baseline-apk-hashes.txt) |
| AVD | `WinterCampingEditorTest`, `GleoCallbackTest` 재실행 | 1건 통과, 1건 실패 ([로그](avd-target-retest-1.log)) → 시험 수정 후 2건 통과 ([로그](avd-target-retest-2.log)) |
| AVD | 전체 회귀 13개 클래스 28건 | 28건 통과. 단일 실행은 시스템 중단으로 두 번 끊겨 클래스별로 나눠 완료 |

## Gleo 응답 시험 실패의 원인

앱은 응답을 정상 전송했다. 에뮬레이터의 Gleo 수신 컴포넌트(`ai.umos.ambientai`)가 `APP_ACTION_RESPONSE`를 받아 내용을 해석한 로그를 확인했다([발췌](gleo-platform-receiver-excerpt.txt)). Gleo가 시작한 요청이 아니어서 플랫폼은 응답을 거절(Reject)했다. 이것은 Gleo 음성 호출 연동의 검증이 아니다.

실패는 시험 쪽 문제였다. 시험의 대역 수신기를 `RECEIVER_NOT_EXPORTED`로 등록했는데, Android 14는 암시적 브로드캐스트를 비공개 동적 수신기에 전달하지 않는다. 외부 앱인 Gleo를 대신하는 수신기이므로 `RECEIVER_EXPORTED`로 고쳤다. 제품 코드는 바꾸지 않았다.

## 전체 회귀 중 시스템 중단

같은 debug APK(`f238dd49…`)에서 전체 28건을 한 번에 돌리면 16건 통과 후 `INSTRUMENTATION_ABORTED: System has crashed`로 끊겼다([1회차](avd-full-regression-1.log), [2회차](avd-full-regression-2.log)). 두 번 모두 system_server watchdog이 GNSS HAL 호출(`GnssNative.native_stop_*`)에서 67초 멈춤을 보고했다([발췌](system-watchdog-excerpt-2026-11-15.txt)). 11월 1일 기록과 같은 형태다. 당시 호스트 여유 메모리는 0.9GB였다. 앱 쪽 원인은 찾지 못했다.

끊긴 뒤 남은 클래스를 하나씩 실행해 모두 통과했다: TimerNotificationTest 3, AudioPlatformTest 2, RunHistoryUiTest 4, RunExplanationUiTest 1, WinterCampingEditorTest 1, GleoCallbackTest 1 (`avd-part-*.log`). 앞의 16건과 합쳐 28건이다. 단일 실행 28건 통과 기록은 없다.

## 이번에 하지 않은 것

차량 쓰기 재계측(공조·조명), 시트, 실제 OpenAI, 서명 release 설치는 이 단계에 포함하지 않았다.
