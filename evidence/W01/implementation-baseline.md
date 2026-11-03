# 구현 기준선

- 날짜: 2026-10-27 KST
- 승인: 사용자의 `Implement the proposed plan.` 요청.
- 시작 상태: Git clean, Markdown 16개와 DOCX 2개, 앱/서버 소스 없음.
- 호스트: Windows x64, Java 17.0.7, Node 24.12.0, npm 11.7.0.
- 기존 SDK: C:/AndroidSdk, platform 32/33. Pleos AVD 없음.
- 병렬 소유권: core/계약, server/연결 목록, vehicle/공식 플랫폼 경계, root/UI·저장·서비스·빌드·문서.
- 실제 OpenAI 호출: 사용자 요청에 따라 보류. 개발용 fixture는 실제 AI로 표시하지 않음.
- 앱 코드 작성과 공식 이미지 다운로드는 승인 범위. 계정 신청/유료 가입/외부 발송은 수행하지 않음.
- 최초 기준: Vehicle 2.3.2, NaviHelper 3.0.5, Emulator 2.1.0/API34. 실제 설치·권한·상태 변경은 별도 증거 필요.

## 공통 구현 계약

공통 JSON schemaVersion=2. Kotlin core가 모델·validator·실행·시뮬레이션을 소유하고 server는 같은 JSON Schema로 생성 응답을 검사한다. 앱은 실행 가능 여부·동의·capability를 결정한다. 실차 제어를 mock 성공값으로 대체하지 않는다.
