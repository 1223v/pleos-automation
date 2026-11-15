# 기획서 그림 4장 교체 기록 (Astryx)

```text
요구사항 ID / 작업 ID / 시험 ID·분기: R12/R13 · W22 · 기획서 그림 교체(W30 디자인 기준 준비)
날짜(KST) / 담당 / 판정(PASS·FAIL·BLOCKED·NOT_RUN): 2026-11-15 18:55 / 개발 에이전트 / PASS(문서 검수)
검증 수준(DOC·UNIT·API·AVD·DEVICE): DOC. 앱 동작 시험이 아니다
APK 버전·SHA-256 / 소스 커밋 또는 소스 묶음 해시: 그림 3의 화면은 제출 APK(소스 f27504a, SHA-256 9f94aaae…3ec7)를 11월 15일 공식 에뮬레이터에서 찍은 release-first-screen.png
Windows·JDK·Gradle / AVD·OS·SDK·Vehicle Service 버전: Windows 11 x64, Node 24.22.1, Chrome headless, Word(Microsoft 365) / 그림 3·4는 Pleos Connect Emulator v2.1.0 화면
입력 출처(실제 SDK·Console→SDK·모의 어댑터·실제 AI): 그림 1·2는 사용 시나리오 도식(contracts/fixtures/winter-camping.json 그래프와 조명 수정 시나리오), 그림 3·4는 실제 에뮬레이터 화면 원본 픽셀
권한 / capability / 대상 영역·축 / 단위·허용 범위: 해당 없음
적용 정책 버전(속도·freshness·이동/STOP 확인 한도): 해당 없음
실행 ID·동작 ID / 사용자 확인 ID: 해당 없음
재현 단계 / 기대 결과: design/ASTRYX_NOTES.md의 명령 순서. 그림 4장 교체, 그 밖의 파트는 바이트 동일, 7쪽 유지
요청값·요청 시각 / 응답 종류·시각: 해당 없음
변경 전/후 조회 또는 이벤트값·시각: 아래 표
STOP 요청·응답·정지 확인 또는 결과 불명 근거: 해당 없음
실제 결과 / 화면·로그·영상 경로: 이 폴더의 fig1~4.png, figures.sha256. 완성 DOCX·PDF는 개인정보가 있어 .local/application-figures-2026-11-15/ (Git 제외)
실패 원인 / 수정 작업 / 재시험 결과: 첫 Word 렌더 스크립트(Temp 보관본)가 Close/Quit에 [ref] 인자를 주지 않아 숨은 Word가 남았다. 이 세션이 띄운 창 없는 인스턴스만 종료하고 design/figures/render-word.ps1로 고쳐 재실행, 정상 종료 확인
남은 미검증 범위 / 재개 조건: 인쇄본 육안 확인과 메일 제출은 제출자가 한다. 3번째 팀원의 동의 체크·서명·날짜는 비어 있다(내용 변경 안 함)
```

## 바뀐 것

| 위치 | 이전 | 이후 |
|---|---|---|
| 그림 1 (2. 기획 배경) | Pillow로 그린 아이콘 없는 박스 3쌍, 16.35×4.81cm | n8n형 노드 흐름: 주차 후 시작 → 외기 5°C 이하 —충족→ 공조 23°C / —미충족→ 건너뜀 → 운전석 등받이(실행마다 확인) → 조명 30%(제안값) → 완료. 16.35×6.44cm |
| 그림 2 (4. 주요 사용 시나리오) | 박스 3단 + 수정 전후 박스 | 선택 → AI 수정 요청 → 적용 3단계 카드와 수정 전/후 노드 흐름, 추가 노드 배지. 16.35×7.80cm |
| 그림 3 (7. 참고자료) | 10월 28일 화면 일부를 문서 안에서 자른 것(글자 판독 어려움) | 제출 APK 11월 15일 실제 화면의 흐름 부분 원본 픽셀 + 출처 칩. 16.35×4.18cm |
| 그림 4 (7. 참고자료) | 주행 제한 화면 crop | 같은 원본의 같은 영역 + "주행 중(D · 15 km/h)" 칩. 6.10×3.55cm |
| 그림 3 캡션 | "실제 편집 화면 발췌" | "제출 APK 실제 화면 발췌" (화면 출처가 바뀌어 한 구절만 수정) |
| 대체 문구 4개 | 이전 그림 설명 | 새 그림 내용 설명 |

## 검수 결과

- `python design/figures/verify_docx.py …` → `PASS: 4 media + drawings + 1 caption changed; other parts byte-identical; 7 pages; pixel-identical pages [1, 4, 6, 7]; alt text has no contact data.`
- 기준 수정본 SHA-256 `205eb414de83a784bfe40c21095c83ab542954ea783c4a42bfbd8a4ce5ea66f5`, 결과 DOCX SHA-256 `e82aafd4708941ca2fa69538e3f6abde601a90916b555f898c9b26f9299357c3`.
- Word 렌더 7쪽. 2·3·5쪽을 1.6배 렌더로 육안 확인: 그림이 답변 칸 안에 들어가고, 노드 제목·포트 라벨·배지가 읽힌다. 그림 안 글자는 인쇄 기준 노드 제목 약 3mm, 보조 설명 약 2.4mm.
- 그림 안에 이름·연락처·계정·프로필 표시가 없다. 그림 3·4는 상태바(프로필 표시 포함)를 잘라냈다.

## 출처·라이선스

- 디자인 토큰: `@astryxdesign/theme-neutral` 0.7.16 (MIT, Meta). 아이콘: `lucide-react` 1.73.0 (ISC). 글꼴: Figtree(OFL, Google Fonts), Noto Sans KR(OFL, Windows 설치본).
- 그림 3·4의 화면: 공식 Pleos Connect Emulator에서 이 앱과 Pleos 시스템이 표시한 화면.
