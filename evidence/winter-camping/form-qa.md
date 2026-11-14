# 겨울 차박 지원서 검수

2026-11-12 KST. [제출용 작업 문서](../../contest-application-consent.docx)와 [공식 배포 보관본](../../references/contest-application-consent.docx)을 대조했다.

| 비교본 | SHA-256 |
|---|---|
| 공식 양식 | `07be893d58b0e96ab7788f0d189d6829f0e59a2cee4bc18cb90ec37eeda2f5fd` |
| 11월 12일 개정 직전 | `565d4abffda0b21efa28e082acb7feeacf950080c5c04ab9fcbd22c7abf0d013` |
| 이번 개정본 | `29a97b96a0e2dfaa20ff78ad6bc95240bfdbf55dc6ffdc253ee2bc8aac34f3f2` |

- 심사 자료에서 좌석 질문, 확인 전 초안, 선택 노드와 인접 단계의 수정안, 이름 확인 후 비활성 저장을 갱신했다. 실제 OpenAI 호출과 Gleo 서비스 등록·음성 호출, 시트 두 축의 이동·정지, 동일 서명 release APK 10회는 후속 검증으로 기재했다.
- OOXML 182개 문단 중 답변 7개만 바뀌었다. 본문 밖 패키지 항목 24개와 삽입 이미지 4개의 바이트가 개정 직전과 일치했다. 공식 양식과 대조해 앱명 세 곳, 운영란, 개인정보 동의 문구, 빈 서명란·동의 선택란을 확인했다. 심사 답변에는 연락처와 로컬 파일 경로가 없다.
- Windows 번들에 LibreOffice 실행 파일이 없어 `render_docx.py`가 `FileNotFoundError: LibreOffice soffice.exe was not found on PATH`로 종료됐다. Microsoft Word에서 읽기 전용으로 열어 다시 페이지를 매기고 PDF로 내보낸 뒤 번들 pypdfium2로 7쪽을 각각 1,221 × 1,726 픽셀로 렌더링했다.
- 1쪽 운영란, 2쪽 공조에만 이어진 외기 조건선, 3쪽 밝기 수정 전후와 저장 질문, 4쪽 구현·검증 계획, 5쪽 에뮬레이터 화면·결과 표, 6~7쪽 동의·서명을 열어 보았다. 줄 잘림, 표 분할, 겹침을 발견하지 못했다. 개정 전후 1·5·6·7쪽 픽셀이 같았다. 마지막 좌석 질문 문구 수정 후 다시 렌더링했고 2쪽 외 여섯 쪽 픽셀 일치와 2쪽 문구·도식을 확인했다.

검수용 PDF와 페이지 이미지는 Git 제외 디렉터리 `.local/winter-camping/form-qa/`에 있다. 참가자 기입·본인 서명·제출은 남아 있다.
