# Daily Line

사용자의 일정과 대화 패턴을 바탕으로 AI 챗봇이 일정을 추천하는 개인화 일정 관리 서비스입니다.
기능 구현에 그치지 않고, 서버 2대 운영 환경에서 부하 테스트로 병목을 찾아 고치고 이벤트 유실·중복이 없는지 직접 검증했습니다.

90VU 스트레스 테스트에서 처리량을 84.3 TPS에서 **248.9 TPS**로, 에러율을 8.04%에서 **0%**로 개선했습니다.
490VU 혼합 시나리오 테스트에서는 Outbox 적체를 약 25,500건에서 약 200건으로 줄였고, 생성된 일정 34,300건이 요청 수와 정확히 맞았습니다.

기간: 2025.01 ~ 2026.10 (개인 프로젝트) / 프론트엔드: [schedulemanagement-front](https://github.com/well0924/schedulemanagement-front)

<details>
<summary>단계별 진행</summary>

- MVP 기본 구현: 2025.01 ~ 2025.03
- 1차 고도화 (메시지 무결성): 2025.04 ~ 2025.09
- 2차 고도화 (분산 환경 전환): 2026.04 ~ 2026.06
- 3차 고도화 (병목 재규명 및 성능 개선): 2026.09
- 4차 고도화 (Mixed-flow 부하 테스트 및 운영 점검): 2026.09 ~ 2026.10

</details>

---

## Tech Stack

| 구분 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot 3.2, Spring Data JPA, MySQL 8, Redis, Flyway |
| Messaging | Apache Kafka (KRaft, 브로커 3개), ShedLock, WebSocket(STOMP), Web Push |
| External | OpenAI API, Resilience4j, AWS S3 (PreSigned URL) |
| Infra | AWS EC2, RDS, Docker, Nginx, GitHub Actions, Jib |
| Observability | Prometheus, Grafana, Loki, Tempo, OpenTelemetry |
| Test | JUnit 5, TestContainers, WireMock, JMeter |

---

## 아키텍처

<img width="1171" height="831" alt="전체 시스템 구성" src="https://github.com/user-attachments/assets/236161e9-17bf-435a-a536-35222099ff5c" />

앱 서버 2대, 인프라 서버 1대(Nginx, Redis, Kafka 브로커 3개), 모니터링 서버로 구성했습니다.
AI 추천 부분은 헥사고날 구조로 분리해서, LLM을 바꾸더라도 일정 도메인 로직은 손대지 않도록 했습니다. ([헥사고날 설계](docs/architecture/hexagonal-design.md))

### 이벤트 처리 흐름

<img alt="이벤트 처리 흐름" src="docs/image/event-flow.png" />

일정을 저장할 때 Outbox와 리마인더도 같은 트랜잭션에 함께 저장합니다.
발행기는 1초마다 미발행 이벤트를 최대 200건씩 가져와 Kafka로 보내고, 컨슈머는 `(consumer, event_id)`로 중복을 걸러 낸 뒤 업무 데이터와 처리 기록을 한 트랜잭션에 저장합니다.
알림과 회원가입 이벤트는 처리에 실패하면 3번 재시도한 뒤 DLQ로 보내고, 재처리 스케줄러가 간격을 늘려 가며 최대 4번 다시 발행합니다. 그래도 실패하면 포기(dead)로 표시하고 Slack으로 알립니다. 챗봇 이벤트의 DLQ 재처리 연결은 남은 과제입니다.

단계별 그림과 재시도 표는 [이벤트 흐름 상세](docs/event/event-flow.md)에 있습니다.

---

## 설계 결정

| 결정 | 이유 | 검토한 대안 |
|---|---|---|
| 후속 처리를 Kafka로 비동기화 | 서버 재시작에도 이벤트가 남고, 서버 2대 어디서 발행해도 컨슈머 그룹이 나눠 처리 | `@Async`, ApplicationEvent: 메모리 안에서만 전달돼 장애 시 유실 |
| Outbox 패턴 | DB 저장과 Kafka 발행을 한 트랜잭션으로 묶을 수 없어, 이벤트를 DB에 먼저 저장 | 저장 후 바로 발행: 발행 실패 시 알림 유실 |
| `(consumer, event_id)` 멱등 처리 | Outbox는 최소 한 번 발행이라 중복이 올 수 있고, 한 이벤트를 여러 컨슈머 그룹이 처리 | `event_id`만 키로 사용: 먼저 처리한 그룹이 다른 그룹을 막음 |
| 부수 작업은 같은 트랜잭션 또는 Outbox로만 | 커밋 이후 리스너에서 DB에 쓰면 저장이 버려지거나 커넥션을 2개 씀 | `AFTER_COMMIT` + `REQUIRES_NEW`: 490VU에서 커넥션 풀 고갈 |
| OpenAI 호출에 서킷브레이커 | 외부 API 장애가 일정 서비스로 번지지 않게 격리, 첫 응답과 무응답 타임아웃 분리 | 타임아웃만 설정: 장애 중에도 계속 호출 |

자세한 내용은 [설계 결정 상세](docs/architecture/design-decisions.md)에 정리했습니다.

---

## 성능 결과

앱 서버 2대와 Kafka 브로커 3개로 구성한 운영 환경에서 JMeter로 측정했습니다.

스트레스 테스트 (일정 생성 API, think-time 없음)

| 조건 | 결과 | 핵심 원인 |
|---|---|---|
| 30VU, 50VU | 에러율 99.89% → 0.35% | 커넥션 풀 포화, 인증 필터 DB 조회 |
| 60VU | 에러율 16% → 0.05% | Gap Lock 데드락 |
| 90VU (리마인더 저장 수정 전 측정) | 84.3 → **248.9 TPS**, 에러율 8.04% → **0%** | 트랜잭션 점유 시간, 느린 쿼리 |

Mixed-flow 부하 테스트 (로그인 + 생성 70% / 수정 30%, think-time 1~5초)

| 조건 | 처리량 | 에러율 | p99 |
|---|---|---|---|
| 90VU | 23.9 TPS | 0.00% | 150ms |
| 490VU | 정상 구간 157~161 TPS | 0.00% | 469ms |

490VU에서 API는 약 157~161 req/s를 에러 없이 처리했지만, Outbox 발행기가 초당 60~70건밖에 처리하지 못해 적체가 약 25,500건까지 쌓였습니다. 배치 선점 방식으로 바꾼 뒤 약 200건으로 줄었습니다.

측정 상세: [성능 결과 요약](docs/performance/summary.md), [스트레스 테스트](docs/performance/distributed-stress-test.md), [Mixed-flow](docs/performance/mixed-flow-load-test.md)

---

## 트러블슈팅

| 문제 | 원인 | 해결 |
|---|---|---|
| 60VU 데드락 (에러율 16%) | 충돌 검사의 비관적 락이 Gap Lock 경합 | 비관적 락 제거, COUNT 사전 검사 + UNIQUE |
| 90VU 붕괴 | 처음엔 Outbox 락 경합으로 봤지만, 커넥션 대기가 적체보다 90초 먼저 늘어남 | 느린 쿼리(리마인더 DELETE, 충돌 검사 COUNT)와 트랜잭션 구조 개선 |
| 490VU 발행 적체 | 건마다 UPDATE해 초당 60~70건이 상한 | UPDATE 한 번으로 200건 배치 선점 |
| 490VU 커넥션 고갈 | 리마인더 `REQUIRES_NEW`가 요청당 커넥션 2개, 선점 UPDATE의 범위 잠금(추정) | 같은 트랜잭션에서 저장, 선점 트랜잭션만 `READ_COMMITTED` |
| DLQ 재처리가 한 번도 동작하지 않음 | 매퍼 필드 누락, 재시도 시각 누락, 재실패 유실 | 수정 후 Kafka·MySQL 통합 테스트로 확인 |

운영 점검 중 찾은 버그 12건을 포함한 전체 내용은 [트러블슈팅 상세](docs/troubleshooting/README.md)에 있습니다.

---

## 테스트와 관측성

재시도, DLQ, 재처리, 포기 처리까지의 실패 경로는 TestContainers로 Kafka, MySQL, Redis를 띄운 통합 테스트로 확인했습니다.
부하 테스트가 끝난 뒤에는 DB를 직접 조회해 대조했습니다. 490VU 기준으로 생성 건수 합계가 요청 수(34,300건)와 일치했고, 미발행 Outbox와 재처리 대상은 0건이었습니다.
병목은 Grafana에서 HikariCP 커넥션, Outbox 백로그, 컨슈머 지연을 보면서 찾았고, 지표만으로 확정하기 어려운 DB 문제는 slow query log로 확인했습니다.

자세한 내용: [관측성과 테스트](docs/testing/README.md)

---

## 한계점과 다음 단계

| 한계 | 다음 단계 |
|---|---|
| Kafka 브로커 3개와 Redis가 EC2 한 대에 있어 호스트 장애에 취약 | 브로커를 다른 호스트로 분산 |
| WebSocket 브로커가 서버마다 따로 있어, 다른 서버에 연결된 사용자는 실시간 알림을 못 받음 | Redis Pub/Sub으로 서버 간 전달 |
| 챗봇 이벤트의 DLQ를 받는 컨슈머가 없음 | 알림처럼 재처리 경로 연결 |
| 배치 선점 후 490VU 평균 응답이 65ms에서 804ms로 늘어남 | 원인을 나눠서 다시 측정 |

전체 목록은 [트러블슈팅 상세의 남은 한계](docs/troubleshooting/README.md#남은-한계)에 있습니다.

---

## 상세 문서

블로그
- 분산 환경 병목 트러블슈팅 [1편](https://codingweb.tistory.com/325), [2편](https://codingweb.tistory.com/326), [3편](https://codingweb.tistory.com/328), [4편](https://codingweb.tistory.com/353), [5편](https://codingweb.tistory.com/354), [6편](https://codingweb.tistory.com/355)
- [Mixed-flow 부하 테스트](https://codingweb.tistory.com/356), [Kafka 장애 주입 테스트](https://codingweb.tistory.com/327), [챗봇 고도화 설계](https://codingweb.tistory.com/324)

프로젝트 문서
- [아키텍처](docs/architecture/architecture-overview.md), [이벤트 흐름](docs/event/event-flow.md), [CI/CD](docs/architecture/ci-cd-pipeline.md), [ERD](docs/database/erd.md), [인증](docs/auth/auth-overview.md), [모니터링](docs/monitoring/monitoring.md)
