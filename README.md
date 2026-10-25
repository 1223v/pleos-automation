# Moment Routine — Windows Codex 개발 인수인계

기준일: **2026-10-25 KST**. 이 폴더는 Pleos 차량 루틴 앱의 요구사항·공식 근거·TODO·검증·제출 계획이다. **현재 작업은 Markdown 보완만 수행했다. 앱·서버·APK·Git 저장소는 없으며 SDK 설치·프로젝트 신청·결제·접수도 수행하지 않았다.** Windows 환경과 에뮬레이터 동작은 후속 개발에서 검증한다.

## 확정 목표

- 2026-11-13 18:00 KST까지 공식 Pleos Connect Emulator에서 검증된 완성 APK. 개발자 1명, 하루 5~7시간.
- 텍스트 AI 생성·검토·편집·저장 → 차량 상태·트리거 → 공조 목표 온도·앰비언트 밝기/색상·시트 앞뒤/등받이 제한 이동과 STOP → 타이머·자체 음원·이력.
- 시트는 매 실행 확인, 한 축씩 제한 이동. 요청 응답과 실제 상태·정지 확인을 구분한다.
- AI는 외부 API와 서버를 사용한다. 공급자·모델·호스팅·비용 한도는 후속 결정 작업이다.
- **공조·조명·시트 중 하나라도 미검증이면 전체 목표 미완료다.** 사용자 동의 없이 상태 읽기 전용 앱으로 완료 기준을 낮추지 않는다.

공개 신청 안내는 Vehicle Control을 준비 중으로 표시한다. 문서·SDK 소스 존재와 계정 권한·대상 환경 지원·상태 변경은 서로 다른 증거다. 10-28까지 3종 제어 접근성과 결과를 증명하지 못하면 11-13 완성 목표가 차단된 상태로 기록하고 독립 작업을 계속한다. [공식 신청 안내](https://document.pleos.ai/docs/pleos-only/create-my-project)

## Windows에서 시작하는 순서

1. 폴더 전체를 Windows 작업 폴더에 옮기고 [AGENTS.md](AGENTS.md)를 읽는다.
2. [제품 요구사항](03_PRODUCT_PRD.md), [Windows 인수인계](12_WINDOWS_HANDOFF.md)를 읽고 현재 파일·환경을 조사한다.
3. [세부 TODO](13_IMPLEMENTATION_TODO.md)에서 선행 조건을 충족한 작업을 고른다.
4. 해당 공식 문서와 설치 버전의 API를 확인한 뒤 구현한다. 계획값을 플랫폼 보장으로 사용하지 않는다.
5. [검증 계획](07_VALIDATION_AND_DEMO.md)에 요구사항·시험·실제 증거를 연결한다. 미실행은 미실행으로 남긴다.

시작·재개 프롬프트와 PowerShell 명령·실패 대응은 Windows 문서에 있다. 현재 Gradle 프로젝트는 없으므로 빌드 명령의 실행 조건부터 확인한다.

## 문서 지도

| 문서 | 역할 |
|---|---|
| [AGENTS.md](AGENTS.md) | 후속 Codex의 작업·근거·검증 규칙 |
| [01 사실 검증](01_FACT_CHECK.md) | 제품 주장과 문서·권한·실행 증거 구분 |
| [02 공모전](02_COMPETITION_GUIDE.md) | 일정·심사·제출·권리 규정 |
| [03 요구사항](03_PRODUCT_PRD.md) | R01~R13과 최종 범위 |
| [04 기술 설계](04_TECHNICAL_ARCHITECTURE.md) | 제안 구조·계약·시트/중지/실패 정책 |
| [05 기능과 API](05_CAPABILITY_MATRIX.md) | 버전·함수·권한·단위·영역·게이트 |
| [06 일정](06_DEVELOPMENT_ROADMAP.md) | 날짜·게이트·작업량·일정 위험 |
| [07 검증](07_VALIDATION_AND_DEMO.md) | 요구사항→작업→시험→증거 |
| [08 기획서 초안](08_APPLICATION_DRAFT.md) | 제출용 내용, 아직 구현 계획 |
| [09 제출 체크](09_SUBMISSION_CHECKLIST.md) | APK·증거·양식·링크·개인정보 |
| [10 결정과 위험](10_OPEN_QUESTIONS_AND_RISKS.md) | 확정 결정·미확인 조건·미발송 문의 |
| [11 공식 출처](11_SOURCES.md) | 출처 ID·범위·SDK 해시·불일치 |
| [12 Windows 인수인계](12_WINDOWS_HANDOFF.md) | 환경·명령·진단·전달·시작 프롬프트 |
| [13 구현 TODO](13_IMPLEMENTATION_TODO.md) | 의존·시간·산출물·시험·상태 |
| [공식 양식 메모](references/APPLICATION_FORM_NOTES.md) | 보관 DOCX의 항목과 해시 |

현재 구조와 향후 생성할 `android/`, `server/`, `evidence/`는 기술 설계에서 구분한다. 원본 [공식 양식](references/contest-application-consent.docx)은 변경하지 않는다.

## 현재 제약과 검증 상태

초기 조사 기준은 Vehicle SDK 2.3.2·Emulator 2.1.0이다. Service 버전은 소개와 샘플의 표기가 달라 설치값을 기록해야 한다. 시트는 각도·거리 설정으로 추정하지 않고 방향 이동과 STOP을 사용한다. 조명 권한은 함수별 선언을 따른다. 크루즈 목표 속도는 실제 속도 시험을 대체하지 못한다. 세부 근거와 해결 작업은 05·10·11·13 문서에 있다.

문서 정합성 검사는 앱 시험과 별개다. Windows 명령, Android 빌드, AI 호출, 차량 읽기·쓰기·정지, release 시연의 실제 시험 결과는 아직 없다.

## 이번 문서 보완의 검사 결과

- 기존 루트 문서 12개 보완, Markdown 3개 추가. 전체 Markdown 16개의 UTF-8·로컬 링크·표 열·코드 블록·JSON 예시를 검사했다.
- R01~R13, W01~W22, T01~T38의 정의·참조·대응과 작업 의존관계를 검사했다. 개발 출처 D01~D50은 공식 출처 색인에 연결했다.
- 작업량 합계 114~167시간, 19일 가용95~133시간, 일별 하한 배치123시간(작업114+예비9)을 검산했다. 11/06 중간 목표의 시간 부족도 일정에 공개했다.
- 공식 SDK 소스의 버전·145,042 bytes·SHA-256을 다시 확인했다. 파일에 SDK를 설치하거나 소스를 복제하지 않았다.
- 원본 DOCX와 양식 메모는 유지했다. 앱 코드·환경 설치·Windows/AVD 실행·AI API 호출은 수행하지 않았다.

이 검사는 문서 품질에 대한 결과다. 제품 개발 TODO는 모두 NOT_STARTED, 앱 시험은 모두 NOT_RUN으로 유지한다.

# pleos-automation
