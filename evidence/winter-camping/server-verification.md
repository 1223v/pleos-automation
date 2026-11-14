# 겨울 차박 플래너 서버 검증 · 2026-11-12

| 요구 | 실행한 시험 | 결과 |
|---|---|---|
| R03/R04/R16 · W08/W25 | 실제 로컬 HTTP 서버에서 첫 한국어 요청 → 좌석 질문과 읽기 전용 미리 보기 → 좌석 답변을 반영한 초안 → 조명 선택 수정의 세 요청. 공유 [겨울 차박 시험 데이터](../../contracts/fixtures/winter-camping.json) 사용 | 모의 공급자 응답으로 완료. 답변 전 빈 시트 영역이 있는 그래프는 일반 초안 검사를 통과하지 않음 |
| R14/R16/R17 · W23/W25/W26 | 조명 30% 뒤에 600초 대기와 같은 영역 20% 추가. 공조·시트·트리거 변경, 다른 분기 진입, 임시 연결 변경, ID 충돌, 오래된 버전, 연결되지 않은 카드 거부 | 서버 수락·거부 검증 통과. 실제 가상 실행은 Android core 별도 시험 |
| R06~R08 · W05/W13 | 선택 필드 `vehicleAreas`, SDK 복합 조명 영역 원문, 시트 축별 영역, 다른 카드의 기존 영역 보존, 미보고 대상·잘못된 축·과다 항목·차량 상태 추가 전송 거부 | 모의 입력 검증 통과. 영역 일치는 시트 작동이나 차량 쓰기 권한의 근거가 아님 |

Windows, Node 24.22.1, TypeScript 7.0.2, OpenAI Node SDK 7.45.0으로 실행했다. [최초 표적 시험](server-red.log) 6건이 기존 구현에서 실패했고 [영역 추가 전 시험](server-areas-red.log)은 표적 4건 중 3건 실패했다. 최종 `npm run typecheck`와 `npm test`의 TypeScript 빌드, 서버 전체 **61건**이 통과했다. 이 중 [겨울 차박 시험](../../server/test/planner-context.test.ts)은 11건이다. [최종 시험 로그](server-tests.log)

인증을 거친 로컬 HTTP 및 고정 응답을 사용했다. 실제 OpenAI의 한국어 해석 정확도는 별도 시험으로 남는다. 출력 형식은 기존 strict JSON Schema를 사용하며 생성·편집 모드, 질문/초안 구분, 선택한 노드의 수정 범위는 플래너가 검증한다. [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses) · [Pleos 기능·영역 확인 안내](https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk-sample-app)
