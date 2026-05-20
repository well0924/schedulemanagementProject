> **"사용자의 일정 결정 피로를 줄이는 루틴 추천 및 시간 관리 서비스"**
> 본 프로젝트는 단순 CRUD 구현을 넘어, 제한된 자원 환경에서 **시스템 임계점(Limit)을 파악하고, 데이터 정합성 보장 및 외부 API 장애 격리**를 목표로 아키텍처를 고도화한 프로젝트입니다.

**개발 기간:** 2025.01 ~ 2025.06 (1차: MVP) / 2025.09 ~ 2026.04 (2차: 분산 환경 고도화)

**배포 환경:** AWS Lightsail + GitHub Actions

**모니터링:** Grafana · Prometheus · Loki
 
---

## 🛠️ Tech Stack

- **Language & Framework:** Java 17, Spring Boot, Spring Data JPA
- **Database & Cache:** MySQL 8.0, Redis
- **Message Broker:** Apache Kafka (KRaft mode, 3-Broker Cluster)
- **Infra & CI/CD:** AWS EC2 (t3.micro 2GB), GitHub Actions, Docker, Nginx
- **Observability:** Prometheus, Grafana, Loki (LGTM Stack)
- **Test:** JMeter, TestContainers
---

## 🧱 아키텍처

### 전체 시스템 구성

![DailyLine Architecture](https://github.com/user-attachments/assets/38113ebf-4474-4016-8d27-271ab60da0a3)

| 구분 | 구성 요소 | 설명 |
|------|-----------|------|
| **Frontend** | Next.js (App Router) | 캘린더 UI, 일정 CRUD, WebSocket 실시간 수신 |
| **Backend** | Spring Boot, Kafka, Redis, MySQL, S3 | Outbox + DLQ 기반 복원력 아키텍처 |
| **Infra** | AWS Lightsail, Nginx, Docker Compose | App 2대 + Kafka 3-Broker 분산 환경 |
| **Monitoring** | Prometheus, Loki, Grafana | 메트릭/로그 수집 및 대시보드 시각화 |

### 헥사고날 아키텍처 (Ports & Adapters)

AI 추천 엔진(OpenAI API)은 외부 의존성이 크고 모델 스펙이 수시로 변경됩니다.
헥사고날 아키텍처를 도입하여 **외부 AI API 규격이 변경되거나 다른 LLM으로 교체되어도 핵심 일정 도메인 로직은 수정하지 않는 격리 환경**을 구성했습니다.

```
[외부 세계]                              [도메인 핵심]
─────────────────────────────────────────────────────
OpenAI / Gemini / Claude  ─→  Outbound Port(AI추천Port)  ─→  일정 도메인 (불변)
REST Controller / WebSocket  ─→  Inbound Port(UseCase)  ─→  일정 도메인 (불변)
─────────────────────────────────────────────────────
```
 
---

## 🚀 인프라 임계점 극복 — 에러율 16.35% → 0.05% 달성

현업의 대규모 인프라 장애 메커니즘을 제한된 비용 내에서 모사하기 위해, 의도적으로 **초소형 인프라(EC2 2GB Sandbox)**를 구축하여 시스템 임계치(60VU)까지 트래픽을 밀어 넣었습니다.
스펙업 없이 순수 아키텍처 튜닝만으로 **Throughput 약 320배 개선** 및 핵심 도메인 에러율 0%를 달성한 과정입니다.

### 6단계 병목 격파 과정

| 차수 | 조치 내역 | 에러율 | 조치 트리거 및 인과관계 |
|:-----|:----------|:------:|:----------------------|
| **1차** | 최초 분산 환경 테스트 (App 2대, Kafka 3대) | **16.35%** | 동시 요청 시 대량 에러 발생 및 병목 지점 탐색 시작 |
| **2~3차** | **DB 데드락(Gap Lock) 해결:** Lock Ordering + Unique Index 설계 개선 | **8.1%** | **[트리거]** 동일 사용자의 동시 요청 시 무작위 Gap Lock 경합 관측<br>→ 인덱스 정렬 및 유니크 제약으로 에러율 절반 반감 |
| **4차** | **트랜잭션 내 외부 I/O 격리:** 외부 API 호출 `@Async` 분리 + 재처리 테이블 구축 | **2.4%** | **[트리거]** 외부 API 응답 지연 시 HikariCP 커넥션 풀이 반환되지 않고 스레드가 묶이는 지표 확인<br>→ 트랜잭션 범위를 좁히고 외부 I/O 격리하여 커넥션 고갈 해소 |
| **5~6차** | **Redis Pre-check 도입:** 불필요한 트랜잭션 인입 차단 + 쿼리 튜닝 | **0.05%** | **[트리거]** 중복 동시 요청이 DB 레이어까지 인입되어 자원 낭비 유발<br>→ 캐시 계층에서 1차 차단하여 **핵심 도메인 에러 0%** 및 Throughput 320배 개선 |

> 에러율 0.05% 해설: 발생한 소수의 에러는 외부 OpenAI API 타임아웃 예외 상황에서 발생한 **의도된 가용성 에러**입니다. 핵심 일정 도메인(생성·조회·알림)의 에러율은 **0%** 입니다.
>
> 상세 트러블슈팅 기록: [블로그](https://codingweb.tistory.com/326)

### 최종 부하 테스트 결과 (60VU 실측)

| 지표 | 결과 | 의미 |
|------|------|------|
| 에러율 | 0.05% | 7,000건 이상 요청 중 사실상 모든 요청 정상 처리 |
| 처리량 | 42.5 req/sec | 60VU 환경에서 안정적인 처리량 확보 |
| DLQ Retry Count | 0건 | 재처리 큐로 넘어간 건 단 한 건도 없음 |
| Outbox Publish Latency | 평균 0.05~0.1ms | 이벤트 발행 로직이 메인 비즈니스 로직에 부하를 주지 않음 |
| HikariCP Active Connections | 25~30개 (max 60) | 풀의 절반 이하로 운용, 추가 트래픽 대응 여력 확보 |
| Kafka Consumer Lag | 순간 발생 후 즉시 0 수렴 | 컨슈머 처리 속도가 프로듀서 속도를 충분히 감당 |
 
---

## ⚡ Kafka + Outbox Pattern — 핵심 설계

일정 생성 API 하나가 완료되면 **알림 발송 / 챗봇 데이터 파이프라인 / 패턴 분석**이 동시에 발생합니다.
이를 동기 방식으로 처리하면 응답 지연이 선형으로 증가하고, 후속 처리 실패 시 전체 롤백 문제가 생깁니다.

### @Async / ApplicationEvent 대신 Kafka를 선택한 이유

| 요구사항 | @Async / ApplicationEvent | Kafka |
|---------|--------------------------|-------|
| **이벤트 재처리(Replay)** | 처리 실패 시 이벤트 유실, 복구 불가 | 오프셋 기반으로 실패 구간부터 재처리 가능 |
| **멀티 컨슈머 독립 구독** | 단일 리스너 구조 | 알림 Consumer / 분석 Consumer가 동일 이벤트를 독립 구독 |
| **파이프라인 확장** | 신규 처리 추가 시 기존 코드 수정 필요 | 신규 Consumer 추가만으로 기능 확장, 기존 로직 무영향 |

### 데이터 유실 제로(At-Least-Once) 및 멱등성 보장

```
[기존 방식의 문제]
① DB 저장 (성공)
② Kafka 발행 (실패) → 알림 유실, 복구 불가
 
[Outbox Pattern 적용]
① DB 저장 + Outbox 테이블 저장 (하나의 트랜잭션)
② ShedLock Polling → Kafka 발행
→ ②가 실패해도 Outbox 레코드가 남아 있어 재시도 가능
```

- **Producer (`acks=all`, `enable.idempotence=true`):** 브로커 장애 시 데이터 유실 방지 + 네트워크 재시도 시 중복 발행 차단
- **Consumer (`ErrorHandlingDeserializer` + DLQ):** Poison Pill로 인한 무한 루프 방지, 실패 메시지를 `.DLQ` 토픽으로 격리 후 `DeadLetterPublishingRecoverer`로 사후 재처리
- **`AckMode.MANUAL_IMMEDIATE`:** `eventId` 기반 멱등성 검증 성공 시점에만 오프셋 커밋하여 확실한 At-Least-Once 보장
- **ShedLock:** 다중 서버 환경에서 Outbox Polling 중복 실행 방지 (추가 인프라 없이 MySQL 재활용)
---

## 🔧 주요 트러블슈팅

### 1. Gap Lock 데드락
동일 사용자의 동시 요청 시 Gap Lock 경합 확인 → Lock Ordering + Unique Index + COUNT 쿼리 교체로 해결.
Redis 분산락은 DB 계층의 문제를 애플리케이션 계층에서 우회하는 오버엔지니어링으로 판단, DB 계층에서 직접 해결했습니다.

### 2. HikariCP 커넥션 고갈
`@Transactional` 내부에서 외부 OpenAI API를 동기 호출하여 커넥션이 점유된 채 대기하는 문제 발견.
`@Async`로 외부 I/O를 트랜잭션 범위 밖으로 분리하여 커넥션 즉시 반납하도록 개선.

### 3. S3 고아 객체 누적
PreSigned URL 방식은 사용자가 업로드 후 일정 저장을 취소하면 S3에 미등록 파일이 남는 문제가 있습니다.
임시 테이블에 파일 키와 발급 시각을 기록하고, Spring 스케줄러가 주기적으로 만료된 임시 파일을 자동 삭제합니다.
멀티스레드 환경 추적성을 위해 `MDC(job, requestId)`를 활용해 로그 가시성을 확보하고, 루프 내 `try-catch` 격리로 개별 실패가 전체 스케줄러에 영향을 주지 않도록 설계했습니다.

### 4. Kafka 이벤트 중복 처리
`eventId` 기반 멱등 처리 + ShedLock으로 다중 서버 환경에서 Outbox Polling 중복 실행 방지.

### 5. JVM Full GC 반복
G1GC 튜닝 + 모니터링 서버 분리로 서비스 서버와의 리소스 경쟁 제거.
 
---

## 🗂️ 기술 스택 선택 근거

| 기술 | 도입 이유 |
|------|-----------|
| **Kafka** | 이벤트 재처리(Replay) + 멀티 컨슈머 독립 구독 + 추천 파이프라인 확장 (`@Async`로는 불가) |
| **Outbox Pattern** | DB 저장과 이벤트 발행을 단일 트랜잭션으로 묶어 이벤트 유실 원천 차단 |
| **ShedLock** | 다중 서버 환경에서 Outbox Polling 중복 실행 방지 (추가 인프라 없이 MySQL 재활용) |
| **Redis** | AI 추천 결과 캐싱(OpenAI 재호출 비용 절감) + JWT Refresh Token TTL 관리 + 중복 이벤트 Pre-check |
| **WebSocket + Web Push** | 브라우저 활성/비활성 상태 모두 커버하는 이중 채널로 알림 도달률 극대화 |
| **Hexagonal Architecture** | AI API 교체·장애에도 일정 도메인 로직 수정 없이 Adapter만 교체 가능 |
| **S3 PreSigned URL** | 파일 업로드 트래픽을 서버가 중계하지 않아 서버 I/O 부하 절감 |
 
---

## 📈 Observability & Monitoring

- **Prometheus & Grafana:** HikariCP Connection Count, JVM Memory, CPU Usage 실시간 모니터링 및 Alertmanager 연동
- **Loki & Promtail:** Distributed Tracing 환경 구축, 사용자 요청 흐름(TraceID)별 병목 구간 실시간 추적 및 MDC 파일명 매핑 로그 가시성 확보
---

## 🧪 품질 보증

- TestContainers 기반 Kafka / Redis / MySQL 통합 테스트
- JMeter 60VU 부하 테스트 (7,000건+)
    - 메시지 유실: 0건
    - 중복 처리율: 0% (eventId 기반 멱등 처리)
- DLQ → 재처리 후 최종 정상 처리율: 100%
---

## 📚 상세 문서

- 아키텍처 설계: `docs/architecture`
- 이벤트 처리 및 재처리 전략: `docs/event`
- 성능 테스트 및 모니터링: `docs/performance`
- 트러블슈팅 블로그: [https://codingweb.tistory.com/326](https://codingweb.tistory.com/326)
---

## 🔗 관련 저장소

- **프론트엔드:** [schedulemanagement-front](https://github.com/well0924/schedulemanagement-front)