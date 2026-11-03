# 정합성·보안 검토 보고서

## 2026-11-02 A05 변경 검토

설명 API도 기존 인증·Origin 차단·크기 제한·중복 ID·공유 동시성·영속 예산 정책을 사용한다. 공급자 입력의 라벨/관찰값/메시지는 신뢰하지 않는 데이터이며 출력은 strict schema와 기록된 run/node/event 참조를 통과해야 한다. 추가 patch/명령을 거부하며 AI 설명으로 차량 동작·승인·원래 이력을 변경하지 않는다. 실제 모델 설명의 사실성은 스키마 검증만으로 보장하지 않는다.

[독립 리뷰](evidence/A05/review.md)에서 확인한 오래된 이력 조회의 편집 덮어쓰기 경합을 AVD에서 재현했다. 동기적인 snapshot 선택으로 조회 대기를 제거하고, 복구 이벤트를 읽기 전 설명 요청을 차단한다. Room1/2→3은 기존 이력을 보존하며 누락된 원인을 추정하지 않는다. [수정·시험 장부](evidence/A05/verification-2026-11-02.md)에서 RED, 컴파일 오류, 재시험을 구분한다. 새 의존성·권한·실제 외부 API 호출은 없다. 아래 의존성 잔여 위험과 실제 환경 게이트는 유지한다.

## 2026-11-01 감사 후속 보완

이 절과 [새 검증 장부](evidence/plan-compliance-2026-10-30/verification-2026-11-01.md)가 아래10월30일 완료 표현보다 우선한다. 계획 원문 대조에서 추가 결함을 확인하여 보완했다.

- HA 조명 사전 상태 누락과 잘못된 Calendar/Kakao/W3W 결과의 성공 오판정을 차단했다. 모의 IO 시험이며 실제 계정 검증은 아니다.
- 시트 승인에 실행 ID/정의 버전/의미 해시/전체 계획/교정 키/연결 세대/실행 Job을 연결했다. 연결 또는 취소가 HTTP 준비 중 바뀌어도 최종 전송 직전 재검사한다. STOP은 승인 gate와 분리한다.
- Android Car callback이 Pleos SDK 자체 Binder 재연결을 대표하지 않는다. SDK 공개 observer·대기 명령 큐의 재연결 안전성이 미검증이므로 제품 시트 잠금을 유지한다.
- 사용자 확정4,096회 최악 방문 경로를 앱·서버에서 저장/시험 전에 검사하고 중첩 산술을 포화시킨다. 작업 초안은 보관하며 실행 중 제한도 유지한다.
- 알림 실패를 물리 실행 실패로 바꾸지 않는다. 앱 소유 음원의 늦은 오류를 사용자 취소/전체 성공과 구분하고 후속 동작을 중단한다. 외부 앱의 음원을 정리하거나 강제로 포커스를 재획득하지 않는다.

새 의존성·권한·외부 실제 API 호출은 추가하지 않았다. 기존 의존성 취약점·Kotlin 빌드 캐시 잔여 위험·실제 환경 미검증은 계속 유효하다. 이번 단위시험 통과는 과거 침해 여부나 전체 취약점 부재의 보증이 아니다.


검토일: 2026-10-29~30 KST. 범위는 현재 앱·core·Node 서버·계약·빌드 설정과 승인된 개선 계획이다. `security-best-practices`의 지원 언어인 TypeScript/Node를 검토하고, Android는 공식 SDK/플랫폼 계약 및 저장소 코드로 별도 검토했다. 이 보고서는 소스·모의 시험·공식 AVD 결과를 구분하며 침해 사고가 없었다는 보증이나 실차 안전 인증이 아니다.

공급자 원점·입력 검증·예산 차단·이력 저장·STOP 경계를 보완했다. Node/Guava/Gradle/JDK의 확인된 구버전은 프로젝트 범위에서 패치했다. 실제 AI/외부 계정 호출은 수행하지 않았다. 시트 제품 잠금과 최종 release 검증 조건은 유지한다. 상세 명령·시험 결과는 [검증 장부](evidence/implementation/2026-10-29.md), 기능별 범위는 [시험 연결표](evidence/audit-2026-10-29/feature-test-map.md)에 기록한다.

## 높음 — 수정 및 회귀시험

1. **비용 초과 뒤 다음 호출 가능성** — 재시작·날짜 변경 후 예산 불일치가 가려지면 추가 유료 호출이 가능하다. [budget.ts](server/src/budget.ts) 58~76행에서 실제 초과액/기존 예약을 보존하고 전역 차단을 SQLite에 영속화한다. 중복 정산·동시 예약·재시작·KST 일/월·UTC legacy 중첩 시험을 추가했다. 자동 잠금 해제 API는 없다. 실제 청구서 대조는 키·예산 설정 후의 운영 작업이다.

2. **공급자 원점 변경 및 느슨한 입력** — 프로세스 환경의 대체 원점이나 타입 변환이 의도하지 않은 요청으로 이어질 수 있었다. [planner.ts](server/src/planner.ts) 16행에서 공식 OpenAI 원점을 명시하며, 31행 이후 strict mode/locale/base 필드를 검증한다. SDK 실제 모듈의 fetch를 테스트 경계에서 대체하여 키가 공식 원점 외로 전송되지 않는지를 확인했다. 임의 URL/코드/실행 승인은 모델 출력 계약에 없다.

3. **실행 단계 저장 실패 후 후속 부작용** — [Engine.kt](android/core/src/main/kotlin/com/pleos/routine/core/Engine.kt) 24~40행과 [RunJournalRepository.kt](android/app/src/main/java/com/pleos/routine/data/RunJournalRepository.kt) 9행 이후를 수정했다. 실행 정의·revision·hash와 순번 있는 이벤트를 기다려 저장한다. observer 오류를 어댑터가 삼켜도 실행 job을 취소해 병렬 형제의 후속 쓰기를 차단한다. UNKNOWN에는 실제 저장된 prefix만 보존한다. ACK는 VERIFIED와 구분한다.

4. **시트 이동/정지의 늦은 응답과 경계 검사** — [SeatMotionCoordinator.kt](android/app/src/main/java/com/pleos/routine/vehicle/SeatMotionCoordinator.kt) 30행 이후에서 durable journal 뒤 최종 상태/세대/범위를 재검사하고, MOVE 응답을 기다리지 않는 독립 STOP 감시를 둔다. 방향별 끝점·역방향 변화·늦은 STOP ACK·기한 내 최초 안정 관찰을 검사한다. 순서를 입증하지 못하면 journal을 유지한다. UNIT 모의 IO 시험으로 검증했으며 **실제 AVD 교정 부재로 [VehiclePlatform.kt](android/app/src/main/java/com/pleos/routine/vehicle/VehiclePlatform.kt) 52행의 seatPolicy=null은 그대로다.** 이 수정은 실제 움직이는 시트의 안전성 입증이 아니다.

## 보통 — 수정 및 경계 보강

5. **서버/앱 크기 제한 불일치와 HA 오판정** — [http.ts](server/src/http.ts) 16행은 직렬화 후 최종 응답256KiB를 제한한다. 요청128KiB·정확한 MIME·인증·Origin·분당60요청을 시험했다. [connectors.ts](server/src/connectors.ts) 55~72행은 HA의 현재 entity/range와 unknown/unavailable을 검사한다. 과거 target 온도만 맞아도 상태가 unavailable이면 성공이 아니다. 실제 HA 계정은 시험하지 않았다.

6. **저장 주소의 재검증 부족** — [ConnectionPolicy.kt](android/app/src/main/java/com/pleos/routine/data/ConnectionPolicy.kt)는 release HTTPS 원점, debug의 명시적 localhost/127.0.0.1/10.0.2.2 HTTP만 허용한다. [PlannerClient.kt](android/app/src/main/java/com/pleos/routine/data/PlannerClient.kt)는 요청 때도 검증하며 redirect를 따라가지 않는다. 서버도 비-loopback bind에 TLS를 요구한다. 로컬 개발 HTTP를 외부 배포의 허용 정책으로 확대하지 않는다.

7. **Gleo 오래된 Intent/저장 전 편집 유실** — [EditorRestore.kt](android/app/src/main/java/com/pleos/routine/data/EditorRestore.kt)와 [MainActivity.kt](android/app/src/main/java/com/pleos/routine/MainActivity.kt)를 수정했다. 보관 요청·원본 요청 ID를 기준으로 최초 적용을 구분하고, 새 Intent에서 composition 전체를 폐기하지 않는다. 최신 입력·그래프·원본 ID를 같은 트랜잭션에 보존한다. 실제 AVD에서 저장 전2건 실패를 먼저 관찰한 뒤 수정했다. 새 요청 뒤 이전 AI 응답이 잘못 표시되는 경합도 로컬 HTTP로 재현하고, 새 요청 반영 시 이전 제안 작업을 취소하여 최종 AVD에서 검증했다. 외부 CONTROL은 검토 화면만 열며 실행 승인을 부여하지 않는다.

## 의존성과 개발 도구

| ID | 대상·확인 범위 | 조치·근거 |
|---|---|---|
| 8 | Node24.12.0 | 프로젝트 로컬24.22.1 Windows x64로 교체. 공식 해시 검증, Python 추출 안전 시험. [Node 공지](https://nodejs.org/en/blog/vulnerability/october-2026-security-releases) |
| 9 | Guava33.3.1-android / CVE-2026-102554 | 기존 전이 의존성에33.7.2-android constraint. 앱/core의 ObjectInputStream·getSerializableExtra 경로는 찾지 못했지만 취약 패키지는 패치. [공식 권고](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94) |
| 10 | Gradle8.10.2 | 8.14.6으로 패치, 공식 배포 SHA-256 고정. daemon/worker 채널2건은 같은 호스트 접근이 전제이며 원격 네트워크 노출 취약점으로 표현하지 않는다. 저장소 TLS 실패 fallback1건도 수정 버전 적용. [공식 릴리스](https://docs.gradle.org/8.14.6/release-notes.html) |
| 11 | 빌드 JDK17.0.7 | 프로젝트 로컬Corretto17.0.21.0.11 설치 및17.0.21+11 실행 검증. 전역 JDK는 변경하지 않음. [공식 릴리스](https://github.com/corretto/corretto-17/releases/tag/17.0.21.0.11) |

`npm audit`는 알려진 일치0건이다. 패치 후 releaseRuntimeClasspath136개를 공개 OSV에 조회했으며 일치0건·추가 페이지 없음이다. [원시 조회 결과](evidence/audit-2026-10-29/server/android-osv-patched.json)는 공개 패키지 좌표만 포함한다. 사설 Pleos 패키지의 미등록 결함까지 없다는 뜻은 아니다.

## 남은 제약과 별도 확인

12. **Kotlin 빌드 캐시 권고 — 버전 잔여 위험**: Kotlin2.0.0은 CVE-2026-53914의 공지 버전 범위에 남는다. 연결된 [공식 수정](https://github.com/JetBrains/kotlin/commit/bf51df665b458fda7c3eaf436c4d88dc119d7ec6)은 KAPT incremental cache의 Java 역직렬화 제한이다. 현재 빌드는 KSP2이며 kapt 설정이 없고 원격 build cache를 설정하지 않았다. 현재 경로에서 악용 가능하다고 입증하지 않았으며, 반대로 Kotlin 전체가 패치되었다고도 표시하지 않는다. KAPT/공유 캐시를 도입하기 전에 Kotlin·Compose·KSP 호환 버전으로 별도 이관하고 다시 검증해야 한다.

13. **운영·실제 플랫폼 검증**: 실제 공급자 키/예산·Gleo 서비스 등록·외부 계정·시트 두 축 이동/STOP·최종 동일 release10회는 미완료다. SSL·인증서·계정별 권한·오디오 포커스 경쟁·실제 deep sleep 등 운영 조건은 해당 환경에서 검증해야 한다. 키·예산·시트 잠금을 임의로 해제하지 않았다.

14. **분석의 한계**: [비밀정보 패턴 검사](evidence/audit-2026-10-29/secret-pattern-scan.json)는 제한된 key/private-key 서명에 일치가 없음을 뜻한다. 모든 개인정보·비밀의 부재, 과거 침해 여부, 호스트/에뮬레이터 OS 전체의 보안 상태를 보증하지 않는다. release 빌드는 서명 없는 로컬 산출물이며 마켓 제출/운영 배포를 수행하지 않았다. lint15경고(의존성 최신 버전 안내14, data extraction 설정1)는 검증 장부에 남긴다.

이 작업에서 추가한 실제 동작 수정은 실패 재현 후 수정했고, 기존 기능은 회귀시험을 보강했다. 최종 빌드·AVD 재검증 결과는 장부가 기준이며 모의 시험 수를 실제 제어 성공 수로 바꾸지 않는다.
