> **"현대인은 해야 할 일을 아는 것보다, 언제 해야 할지 결정하는 과정에서 더 많은 에너지를 소비합니다.
Daily Line은 사용자의 행동 패턴과 빈 시간을 분석해 최적의 일정을 자동으로 추천함으로써, 결정 피로 없이 루틴을 유지할 수 있도록 돕는 개인화 일정 관리 서비스입니다."**
> 
> 본 프로젝트는 단순 CRUD 구현을 넘어, 제한된 자원 환경에서 **시스템 임계점(Limit)을 파악하고, 데이터 정합성 보장 및 외부 API 장애 격리**를 목표로 아키텍처를 고도화한 프로젝트입니다.

**제작 기간:** 2025.01 ~ 2026.10

- MVP 기본 구현: 2025.01 ~ 2025.03
- 1차 고도화 (메시지 무결성): 2025.04 ~ 2025.09
- 2차 고도화 (분산 환경 전환): 2026.04 ~ 2026.06
- 3차 고도화 (병목 재규명 및 성능 개선): 2026.09
- 4차 고도화 (Mixed-flow 부하 테스트 및 운영 점검): 2026.09 ~ 2026.10

- **프론트엔드:** [schedulemanagement-front](https://github.com/well0924/schedulemanagement-front)

---

## 🛠️ Tech Stack

- **Language & Framework:** Java 17, Spring Boot 3.2, Spring Data JPA
- **Database & Cache:** MySQL 8, Redis, Flyway
- **Message Broker:** Apache Kafka (KRaft mode, 3-Broker Cluster), ShedLock (Redis)
- **Realtime & External:** WebSocket(STOMP), Web Push(VAPID), OpenAI API + Resilience4j, AWS S3 (PreSigned URL)
- **Infra & CI/CD:** AWS EC2 (t3.micro 2GB), GitHub Actions(테스트 → Jib으로 Docker Hub push → 앱 서버 2대 순차 배포), Docker, Nginx
- **Observability:** OpenTelemetry, Prometheus, Grafana, Loki, Tempo (LGTM Stack)
- **Test:** JUnit 5, TestContainers, WireMock, JMeter

---

## 🧱 아키텍처

### 전체 시스템 구성

<img width="1171" height="831" alt="Image" src="https://github.com/user-attachments/assets/236161e9-17bf-435a-a536-35222099ff5c" />

| 구분 | 구성 요소                                | 설명                                |
|------|--------------------------------------|-----------------------------------|
| **Frontend** | Next.js (App Router)                 | 캘린더 UI, 일정 CRUD, WebSocket 실시간 수신 |
| **Backend** | Spring Boot, Kafka, Redis, MySQL, S3 | Outbox + DLQ 기반 복원력 아키텍처          |
| **Infra** | AWS EC2, Nginx, Docker Compose       | App 서버 2대 + 인프라 서버 1대(Nginx, Redis, Kafka 브로커 3개) |
| **Monitoring** | Prometheus, Loki, Grafana, Tempo     | 메트릭/로그 수집, 트레이싱 및 대시보드 시각화        |

### 헥사고날 아키텍처 (Ports & Adapters)

AI 추천 엔진(OpenAI API)은 외부 의존성이 크고 모델 스펙이 수시로 변경됩니다.
헥사고날 아키텍처를 도입하여 **외부 AI API 규격이 변경되거나 다른 LLM으로 교체되어도 핵심 일정 도메인 로직은 수정하지 않는 격리 환경**을 구성했습니다.

<img width="953" height="641" alt="Image" src="https://github.com/user-attachments/assets/abb12326-8951-471f-96c9-42677dc6726f" />

### 이벤트 로직

도메인 변경과 이벤트를 하나의 트랜잭션에 Outbox로 저장하고, 발행기가 Kafka로 전달합니다.  후속 처리(알림, 이메일, 챗봇 이력)는 컨슈머가 비동기로 맡습니다.

```
[저장] 하나의 트랜잭션
  일정 변경 / 회원가입 / 챗봇 응답 완료
    → 도메인 데이터 저장
    → Outbox 저장 (BEFORE_COMMIT)
    → 리마인더 알림 저장 (BEFORE_COMMIT, 일정과 함께 커밋)

[발행] OutboxEventPublisher, 1초 간격
  → UPDATE 한 번으로 최대 200건 선점 (claim_id, 30초 지나면 다른 실행이 회수)
  → 비동기 발행 (key = aggregate_id, acks=all, 멱등 프로듀서)
  → 성공 건 일괄 sent=true / 실패 건 선점 해제 (5회 초과 시 DLQ)

[소비] 토픽별 컨슈머 (수동 커밋)
  notification-events  → 알림 컨슈머 → WebSocket(STOMP) / Web Push
  member-signup-events → 가입 컨슈머 → 환영 이메일
  chat-history         → 이력 저장 컨슈머 → MySQL + Redis 대화 맥락
                       → 패턴 분석 컨슈머

  처리 순서: Redis 필터(처리 완료 표시) → (consumer, event_id) 멱등성 확인
            → [한 트랜잭션] 업무 저장 + processed_event 저장
            → 트랜잭션 종료 후 전달(WebSocket·Web Push·메일) → 오프셋 커밋

[실패] (알림·회원가입 이벤트. 챗봇 이벤트는 DLQ까지만)
  즉시 재시도 3회(2초 간격) → DLQ → failed_message 기록
  → 재처리 스케줄러(30초 주기)가 재시도 횟수별 토픽으로 재발행 → 원본 토픽 (최대 5회)
```
---
## 설계 과정 의사결정

일정 생성 한 건이 끝나면 알림 발송, 챗봇 이력 저장, 패턴 분석이 이어집니다.
이를 동기로 처리하면 응답 시간이 후속 작업 수만큼 늘고, 후속 처리 하나가 실패해도 일정 저장까지 롤백됩니다.

### 1. @Async / ApplicationEvent 대신 Kafka를 선택한 이유

| 요구사항 | @Async / ApplicationEvent | Kafka |
|---|---|---|
| 이벤트 보존 | 메모리 안에서만 전달, 서버 재시작·장애 시 유실 | 브로커에 저장, 오프셋 기준으로 실패 구간부터 재처리 |
| 서버 간 전달 | 같은 JVM 안에서만 동작 | App 2대 어느 쪽에서 발행해도 컨슈머 그룹이 나눠 처리 |
| 처리 속도 분리 | 발행 서버의 스레드 풀을 함께 사용 | 컨슈머 그룹마다 오프셋이 따로 있어 각자 속도로 처리 |

### 2. Outbox 패턴을 선택한 이유

```
[문제] DB 저장과 Kafka 발행은 하나의 트랜잭션으로 묶을 수 없다
  ① 일정 저장 (성공) → ② Kafka 발행 (실패) → 알림 유실

[Outbox] 이벤트를 같은 트랜잭션에 DB로 먼저 저장
  ① 일정 저장 + Outbox 저장 (하나의 트랜잭션)
  ② 발행기가 Outbox를 읽어 Kafka로 발행
  → ②가 실패해도 Outbox 행이 남아 있어 다시 발행
```

- **발행기:** UPDATE 한 번으로 최대 200건을 선점(`claim_id`)한 뒤 발행합니다. 성공 건은 일괄 `sent=true`로, 실패 건은 선점을 해제해 다음 주기에 다시 발행합니다.
- **ShedLock (Redis):** App 2대 중 한 대만 발행기를 실행합니다. 락이 겹치는 순간이나 발행 도중 서버가 죽는 경우는 선점 조건(`claim_id`, 30초)이 막고, 그래도 생길 수 있는 중복 전달은 컨슈머 멱등 처리가 막습니다.
- **Producer `acks=all` + `enable.idempotence=true`:** 브로커 장애에도 유실되지 않고, 재전송으로 인한 중복 기록을 막습니다.

### 3. 멱등성 + DLQ

Outbox는 "최소 한 번" 발행을 보장하므로 같은 이벤트가 두 번 올 수 있습니다. 중복은 컨슈머가 막습니다.

- **멱등성:** `processed_event`에 `(consumer, event_id)`를 기록합니다. 하나의 이벤트를 여러 컨슈머 그룹이 각자 처리하므로 키에 컨슈머를 포함했습니다.
- **`AckMode.MANUAL_IMMEDIATE`:** 처리와 기록이 끝난 뒤에만 오프셋을 커밋합니다.
- **`ErrorHandlingDeserializer`:** 역직렬화할 수 없는 메시지(Poison Pill)가 컨슈머를 멈추지 않게 합니다.
- **재시도 → DLQ → 재처리:** 컨슈머가 실패하면 `DefaultErrorHandler`가 2초 간격으로 3번 재시도하고, 그래도 실패하면 `DeadLetterPublishingRecoverer`로 DLQ에 보냅니다. DLQ 컨슈머가 `failed_message`에 기록하면, 30초 주기 스케줄러가 재시도 횟수별 토픽으로 다시 발행해 원본 토픽으로 돌려보냅니다(최대 5회). 대상은 알림·회원가입 이벤트이고, 챗봇 이벤트는 DLQ까지만 보냅니다.
- **저장 단위와 부수 작업:** 업무 데이터와 `processed_event`는 `TransactionTemplate` 하나로 함께 커밋하고, 저장 중 예외는 전파해 위 재시도 경로를 탑니다. 실시간 전달·메일·오프셋 커밋은 트랜잭션이 끝나 커넥션을 반납한 뒤에 합니다. 커밋 이후 콜백(`afterCommit`)은 커넥션이 묶인 채라, 메일 재시도가 커넥션을 붙잡거나 실패 기록 저장이 버려질 수 있어 쓰지 않았습니다.
- **순서:** 메시지 키는 대상 ID(일정·회원·채팅 회원)이고, 재처리 재발행도 같은 키로 보내 같은 파티션에서 순서대로 처리됩니다. 단, 발행 실패나 DLQ를 거친 메시지는 뒤 메시지보다 늦게 처리될 수 있습니다. 지금 컨슈머는 알림·이력처럼 기록을 쌓는 처리라 순서가 바뀌어도 데이터가 틀어지지 않으며, 상태를 덮어쓰는 컨슈머가 생기면 이벤트 버전으로 오래된 이벤트를 걸러야 합니다. 유실과 중복은 위 경로와 멱등 처리로 막습니다.

### 4. 외부 API 격리 — 챗봇 서킷브레이커

OpenAI 장애나 지연이 일정 서비스로 번지지 않도록 Resilience4j 서킷브레이커로 격리했습니다.

- **타임아웃:** 첫 응답 대기와 스트리밍 중 무응답 시간을 따로 제한합니다.
- **차단:** 실패율이 기준을 넘으면 회로를 열어, OpenAI를 호출하지 않고 즉시 대체 응답을 돌려줍니다.
- **대체 응답은 이력에 저장하지 않습니다:** 장애 중 안내 문구가 대화 맥락에 섞이지 않게 했습니다.

---

## 성능 결과

모든 테스트는 App 2대 + Kafka 3-Broker 운영 환경에서 JMeter로 진행했습니다.
먼저 스트레스 테스트로 시스템이 어디서 무너지는지 찾아 원인을 걷어냈고,
그다음 실제 사용 패턴에 가까운 Mixed-flow 부하 테스트로 검증했습니다.

### 스트레스 테스트 — 일정 생성 API, think-time 없음

| 단계 | 조건 | 결과 | 핵심 원인 |
|---|---|---|---|
| 1 | 30VU → 50VU | 에러율 99.89% → 0.35% | 커넥션 풀 포화, 인증 필터 DB 조회, nginx 부하 쏠림 |
| 2 | 60VU | 에러율 16% → 0.05% | Gap Lock 데드락, 트랜잭션 안의 외부 API 호출 |
| 3 | 90VU | 84.3 TPS, 에러율 8.04%, 1분 37초에 붕괴 | 커넥션 풀 고갈 → Tomcat·nginx 연쇄 포화 |
| 4~6 | 90VU 재검증 | **248.9 TPS, 에러율 0%, 붕괴 재현 안 됨** | 트랜잭션 점유 시간 단축, 쿼리 정리, 폴러 튜닝 |

같은 인프라 조건에서 처리량이 2.9배, 평균 응답이 1,225ms에서 277ms로 개선됐습니다.
→ 상세: [분산 환경 스트레스 테스트](docs/performance/distributed-stress-test.md)

### Mixed-flow 부하 테스트 로그인 + 생성 70% / 수정 30%, think-time 1~5초

| 조건 | 처리량 | 에러율 | p95 / p99 |
|---|---|---|---|
| 90VU | 23.9 TPS (이론 상한 29.6) | 0.00% | 75ms / 150ms |
| 490VU | 정상 구간 157~161 TPS (이론 상한 160) | 0.00% | 142ms / 469ms |
| 490VU, 배치 선점 후 | 97.5 req/s (전체) | 0.00% | 2,073ms / 2,938ms |

- think-time이 있으면 처리량은 `VU ÷ (응답 시간 + think-time)`을 넘을 수 없습니다(Little's Law). 9월 30일의 90VU·490VU 측정은 이 상한에 도달했습니다.
- 490VU에서 API는 안정적이었지만 **Outbox 발행기의 처리 한계(초당 약 60~70건)**가 드러났습니다. 백로그가 약 25,500건까지 쌓였고, 적체가 해소된 뒤 미발행 이벤트는 0건이었습니다. → 배치 선점으로 개선 (트러블슈팅 4번)
- 배치 선점 후 490VU 재측정: 적체 최대 **약 25,500건 → 약 200건**, 일정·리마인더 34,300건, 미발행·재처리 대상 0건. 이 측정부터 리마인더 INSERT가 포함됐습니다.
- 대신 DB 쓰기가 늘어(리마인더 INSERT, 컨슈머 처리량 약 2배) API 평균 응답이 65ms → 804ms로 늘었습니다. 원인 분리는 다음 과제입니다.

→ 상세: [Mixed-flow 부하 테스트](docs/performance/mixed-flow-load-test.md)

---

## 트러블슈팅

### 성능 측정 중

#### 1. Gap Lock 데드락 (60VU, 에러율 16%)
- **원인:** 일정 충돌 검사 쿼리에 `PESSIMISTIC_WRITE`를 걸어, 범위가 겹치는 트랜잭션끼리 Gap Lock 경합 발생
- **해결:** 비관적 락을 제거하고 COUNT 기반 사전 검사로 교체했습니다. 그 사이의 경쟁 조건은 `(member_id, start_time)` UNIQUE로 막았습니다.
- **판단:** Redis 분산락도 검토했지만, DB에서 생긴 문제를 애플리케이션 계층에서 우회하는 방식이라 택하지 않았습니다.

#### 2. 커넥션 풀 고갈 — 반쪽짜리 정답에서 근본 원인까지
- **증상 (30VU):** 트랜잭션이 길어 커넥션 점유 시간이 늘고, 풀이 고갈됨
- **1차 대응:** JwtFilter의 회원 조회를 Redis로 캐싱해 50VU까지 안정화
- **한계:** 커넥션을 쓰는 요청 수만 줄였을 뿐, 트랜잭션이 길다는 문제는 그대로였습니다.
- **근본 해결 (90VU 재검증):** 트랜잭션 안의 중복 조회를 제거하고, 리마인더 저장을 커밋 이후로 분리하고(이후 다시 같은 트랜잭션으로 되돌림, 5번 참고), 불필요한 DELETE와 누적된 테스트 데이터를 정리했습니다. → 평균 응답 1,463ms → 278ms

#### 3. 응답이 매번 정확히 10초에서 끊기는 현상
- **증상:** 최대 응답 시간이 매번 10초 근처에서 잘리고, 에러는 일부만 기록됨
- **원인:** 실제 지연이 아니라 nginx `proxy_read_timeout`(10초)에 걸려 504로 끊긴 것이었습니다. 처음엔 `proxy_next_upstream`의 다른 서버 재시도로 해석했지만, 재시도였다면 두 번째 서버까지 기다려 약 20초에서 끊겼어야 합니다. nginx는 `non_idempotent` 없이는 이미 보낸 POST를 다른 서버로 재시도하지 않습니다. 타임아웃을 늘려 실제 최악 응답(19.5초)을 확인했습니다.
- **배운 점:** 3단계에서 "99% 응답 10초 = 커넥션 점유 장기화"로 해석했던 진단이 틀렸다는 것을 이 현상으로 확인했습니다.

#### 4. 90VU 붕괴 → 원인 범위 축소 → 발행기 한계 → 배치 선점
- **3단계 진단:** Outbox 폴링 락 경합이 붕괴 원인이라고 판단
- **재검증:** HikariCP 대기 스레드가 Outbox 백로그보다 약 90초 먼저 증가했습니다. 관측 구간의 slow query log(`long_query_time=0.05`)에서는 폴러 UPDATE가 확인되지 않았고, 일정 생성 중 리마인더 DELETE와 충돌 검사 COUNT에서 긴 실행 시간이 확인됐습니다. → 이 쿼리와 트랜잭션 구조를 먼저 개선
- **조건부 UPDATE에 대한 정정:** 당시 "CAS"라고 부른 `UPDATE … SET retry_count = retry_count + 1 WHERE id = ? AND sent = false`는 재시도 횟수만 바꾸고 바로 커밋돼, 다른 실행도 같은 조건을 통과할 수 있었습니다. 단일 처리를 보장한 것은 이 쿼리가 아니라 **ShedLock(발행기 단일 실행)과 컨슈머 멱등 처리**였습니다.
- **발행기 한계 (490VU):** 건마다 조건부 UPDATE와 완료 UPDATE를 따로 실행해 한 주기에 DB 왕복이 약 400번 → 한 번 실행에 2~3초가 걸려 초당 약 60~70건이 상한
- **배치 선점:** UPDATE 한 번으로 200건에 `claim_id`와 선점 시각을 기록하고, 선점되지 않았거나 30초가 지난 행만 가져오도록 바꿨습니다. 이제 선점 자체가 다른 실행을 막고, 비동기 발행 후 일괄 완료 처리합니다.

#### 5. 배치 선점 후 재측정에서 드러난 커넥션 고갈 두 건
- **요청당 커넥션 2개 (시작 1~2분):** 리마인더 리스너의 `AFTER_COMMIT` + `REQUIRES_NEW`. Spring은 AFTER_COMMIT 리스너가 끝날 때까지 원래 커넥션을 반납하지 않아, 새 트랜잭션이 커넥션을 하나 더 요청했습니다. 490VU에서 풀(서버당 40개)이 바닥나 15초 타임아웃과 502 발생 → 리마인더를 같은 트랜잭션(`BEFORE_COMMIT`)에서 저장
- **선점 쿼리의 범위 잠금 (시작 약 4분):** 두 서버의 풀이 동시에 고갈됐지만 RDS의 CPU·디스크 크레딧은 여유가 있었습니다. `UPDATE … ORDER BY created_at LIMIT 200`이 REPEATABLE READ에서 미발행 구간 끝의 간격까지 잠가, 새 Outbox INSERT를 막는 것으로 추정 → 선점 트랜잭션만 `READ_COMMITTED`로 바꾸자 고갈이 사라짐
- **원칙:** 부수 작업은 "같은 트랜잭션" 아니면 "Outbox를 통한 비동기" 둘 중 하나로만 처리합니다. 커밋 이후 리스너에서 DB에 쓰지 않습니다.

#### 6. JVM Full GC 반복
G1GC 튜닝 + 모니터링 서버 분리로 서비스 서버와의 리소스 경쟁 제거.
<!-- TODO: t3.micro(1GB)에서의 증상 / 힙 768MB·G1GC 조치 / 490VU 결과(Old Gen 평평, GC 7ms 이하)로 보강 -->

### 운영 점검 중 찾은 버그

부하 테스트와 기능 점검을 운영 환경에서 직접 돌리면서, 로컬에서는 보이지 않던 버그를 찾아 고쳤습니다.

| 버그 | 원인 | 해결 |
|---|---|---|
| 리마인더 알림이 저장되지 않음 | 커밋 이후 리스너에서 기본 전파(REQUIRED)로 저장 → 이미 끝난 트랜잭션에 참여해 예외 없이 버려짐. 9월 11일 이후 부하 테스트에도 리마인더 INSERT가 빠져 있었음 | 같은 트랜잭션(`BEFORE_COMMIT`)에서 저장 (`REQUIRES_NEW`로 먼저 고쳤다가 고부하에서 커넥션 2개 문제가 생겨 다시 변경) |
| 챗봇 이력이 한쪽 컨슈머에만 반영 | 두 컨슈머 그룹이 `event_id`만으로 중복을 막아, 먼저 처리한 그룹이 다른 그룹을 막음 | 멱등성 키를 `(consumer, event_id)`로 변경 |
| 운영에서 테이블 3개 누락 | 엔티티만 있고 마이그레이션이 없었음. 로컬은 `ddl-auto: update`가 대신 만들어 줘서 드러나지 않음 | Flyway 마이그레이션 추가 |
| 알림 DLQ 스케줄러가 회원가입 메시지까지 재처리 | 메시지 타입 필터 없음 | 타입 필터 추가 |
| 챗봇 서킷브레이커 설정이 적용되지 않음 | 코드의 인스턴스 이름과 설정 파일 이름 불일치, 타임아웃 단위 오류 | 이름 통일, 단위 수정, 대체 응답은 이력에서 제외 |
| WebSocket 구독에 인증이 없음 | 다른 회원의 알림 채널도 구독 가능 | 연결할 때 토큰 검증, 본인 채널만 구독 허용 |
| 리마인더 중복 발송 | 서버 2대가 같은 리마인더를 동시에 발송 | ShedLock + 행 단위 선점 UPDATE |
| 같은 아이디로 회원이 2명 생성 | `member.user_id`에 UNIQUE 없음 → 로그인 실패 | UNIQUE 제약 추가 |
| 컨슈머 실패 메시지가 재시도·DLQ 없이 사라짐 | 알림·회원가입 컨슈머가 일반 예외를 삼켰고, 알림 컨슈머는 처리 시작 시점에 Redis "processing" 키를 잡아 재시도를 버렸음. 처리 기록이 `REQUIRES_NEW`로 따로 커밋돼 챗봇 컨슈머는 실패해도 "처리됨"이 먼저 남음 | 업무 저장과 처리 기록을 한 트랜잭션으로, 실패는 예외로 전파, 완료 후에만 Redis 표시. 실제 Kafka 통합 테스트로 수정 전 코드는 DLQ에 도달하지 않고 수정 후엔 재시도 3회 → DLQ, 롤백되는 것을 확인 |
| 재처리 메시지가 원래와 다른 파티션으로 감 | 재처리 재발행을 키 없이 보냈고, 리마인더는 일정 ID가 아니라 알림 행 ID를 키로 씀 | 재발행에 대상 ID 키 추가, 리마인더 키를 일정 ID로 통일 |
| CI가 배포 이미지를 만들지 않음 | `jibDockerBuild`라 이미지가 러너 안에서만 만들어지고 사라져, 배포가 Docker Hub의 옛 이미지를 받음. 이미지에 비밀값도 들어 있었음 | main push 때 `jib`으로 push, 비밀값은 서버 `.env`로만 주입, 서버 2대 순차 배포 |

---

## 📈 관측성 & 테스트

### 관측성

- **메트릭 (Prometheus + Grafana):** HikariCP 커넥션, JVM 힙·GC, Outbox 백로그, Kafka 컨슈머 지연, 서킷브레이커 상태를 대시보드로 확인합니다. 부하 테스트의 병목은 모두 이 지표로 찾았습니다.
- **로그 (Loki):** MDC로 요청·이벤트 ID를 남겨, Kafka 컨슈머와 스케줄러 로그를 요청 단위로 추적합니다.
- **트레이싱 (OpenTelemetry + Tempo):** 요청 흐름(TraceID)별로 어느 구간에서 시간이 걸렸는지 확인합니다.
- **slow query log:** 지표만으로 확정하기 어려운 DB 병목을 검증할 때 사용했습니다(리마인더 DELETE·충돌 검사 COUNT의 긴 실행 시간 확인). 기준 시간보다 짧은 쿼리의 반복 비용은 이 로그로 알 수 없어, 커넥션 대기 지표와 함께 판단했습니다..

### 테스트

- **통합 테스트 (TestContainers):** Kafka / Redis / MySQL을 컨테이너로 띄워 실제와 같은 조건에서 검증합니다.
- **외부 API 테스트 (WireMock):** OpenAI 지연·오류를 재현해 서킷브레이커와 타임아웃 동작을 검증합니다.
- **동시성·정합성 테스트:** 배치 선점, 리마인더 선점, 컨슈머별 멱등성, WebSocket 구독 권한 등 운영 점검에서 고친 버그마다 재현 테스트를 추가했습니다.
- **컨슈머 실패 경로 통합 테스트:** 실제 알림 컨슈머를 Kafka·MySQL·Redis 컨테이너로 돌려, 저장 실패 시 재시도 3회 → DLQ, 업무 데이터·처리 기록 롤백, 일시 장애 후 정확히 1건 처리, 전달 시점에 트랜잭션이 끝나 있음을 확인합니다. 수정 전 코드에서는 이 테스트가 실패합니다. (CI 러너의 Kafka 컨테이너 기동이 느려 CI에서는 제외, 로컬에서 `-Pit`로 실행)
- **부하 테스트 (JMeter):** 스트레스 테스트(30~90VU)와 Mixed-flow 부하 테스트(90·490VU). 테스트가 끝난 뒤 DB를 직접 대조했습니다. 490VU 기준으로 계정별 생성 건수 합계가 JMeter 생성 요청 수(34,300건)와 일치했고, 미발행 Outbox와 재처리 대상(`failed_message`)은 0건이었습니다.

---

## ⚠️ 한계점과 다음 단계

| 한계 | 영향 | 다음 단계 |
|---|---|---|
| 소셜 로그인이 nginx `ip_hash`에 의존 | OAuth2 인증 요청을 서버 세션에 저장해, 같은 서버로 돌아와야만 로그인이 완료됨 | 서명된 쿠키 기반 저장소로 바꿔 서버 간 상태 공유 제거 |
| 실시간 알림이 서버별로 분리 | WebSocket 브로커가 서버마다 따로 있어, 다른 서버에 연결된 사용자에게는 실시간 알림이 가지 않음(알림 내역은 저장됨) | Redis Pub/Sub 또는 Kafka 브로드캐스트로 서버 간 전달 |
| 인프라 서버 단일 호스트 | Kafka 브로커 3개, Redis, Nginx가 EC2 한 대에 있음. 브로커 프로세스 장애에는 복제로 대응하지만, 호스트 장애 시에는 이벤트 발행·캐시·라우팅이 함께 멈춤 (앱 서버 2대 이중화는 애플리케이션 계층에만 해당) | 브로커를 서로 다른 호스트·가용 영역으로 분산 |
| Redis 단일 장애점 | 캐시, ShedLock, 중복 사전 검사가 모두 Redis에 의존 | Redis Sentinel 또는 장애 시 동작 정의 |
| `TIMESTAMP` 2038년 한계 | 2038-01-19 이후 시각의 일정을 저장할 수 없음 (490VU 테스트 중 발견) | `DATETIME`으로 전환하는 마이그레이션 |
| 챗봇 이벤트 DLQ 미처리 | 챗봇 컨슈머 실패는 `chat-history.DLQ`, 발행 실패는 `chat-events.DLQ`로 가지만 둘 다 받는 컨슈머가 없어 재처리되지 않음 | DLQ 토픽 이름을 통일하고 알림처럼 `failed_message` → 재처리 연결 |
| DB 쓰기 증가로 API 응답 시간 증가 | 배치 선점 후 490VU에서 평균 응답 65ms → 804ms (리마인더 INSERT 추가, 컨슈머 처리량 약 2배) | 원인 분리 측정, 리마인더 생성을 Outbox로 이동, 컨슈머 동시성 조정 |

---

## 📚 상세 문서

**블로그**
- 분산 환경 병목 트러블슈팅: [1편](https://codingweb.tistory.com/325), [2편](https://codingweb.tistory.com/326), [3편](https://codingweb.tistory.com/328), [4편](https://codingweb.tistory.com/353), [5편](https://codingweb.tistory.com/354), [6편](https://codingweb.tistory.com/355)
- Mixed-flow 부하 테스트: [블로그](https://codingweb.tistory.com/356)
- Kafka 장애 주입 테스트 (ISR): [블로그](https://codingweb.tistory.com/327)
- 챗봇 고도화 설계: [블로그](https://codingweb.tistory.com/324)

**프로젝트 문서**
- 성능 측정: [분산 환경 스트레스 테스트](docs/performance/distributed-stress-test.md), [Mixed-flow 부하 테스트](docs/performance/mixed-flow-load-test.md), [단일 인스턴스 시절 테스트](docs/performance)
- 아키텍처: [전체 구성](docs/architecture/architecture-overview.md), [헥사고날 설계](docs/architecture/hexagonal-design.md), [CI/CD](docs/architecture/ci-cd-pipeline.md)
- 데이터 모델: [ERD와 설계 원칙](docs/database/erd.md)
- 인증: [인증 구조](docs/auth/auth-overview.md), [JWT](docs/auth/jwt-authentication.md)
- 모니터링: [모니터링](docs/monitoring/monitoring.md), [로깅](docs/monitoring/logging.md)
