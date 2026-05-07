# 📅 일정 관리 프로젝트 Dayline

> **개발 기간**
> 
> 2025.01 ~ 2025.06 (1차:MVP 기능 구현)
> 
> 2025.06~09 (2차 고도화)
> 
> 2026.04.10~ (3차: 분산 서버 전환 + 부하 테스트)
> 
> **주요 기술**: Spring Boot, Kafka, Redis, S3, WebClient, WebSocket, Next.js
> 
> **배포 환경**: AWS EC2 (분산 2대) + Nginx 로드밸런서 + GitHub Actions
> 
> **모니터링**: Grafana,Prometheus,Loki

---

## 📌 프로젝트 개요

DailyLine은 웹 기반 개인 일정 관리 서비스로, **반복 일정 / 충돌 방지 / 실시간 알림 / 파일 첨부 / AI 일정 추천** 기능을 포함합니다.  
일정 생성 이후 발생하는 비동기 작업을 안정적으로 처리하기 위해 운영 환경을 고려한 구조로 설계했습니다.

일정 생성 이후 발생하는 비동기 작업을 안정적으로 처리하기 위해 운영 환경을 고려한 구조로 설계하였으며,
이후 단일 서버의 한계(SPOF, 자원 병목)를 확인하고 분산 서버 구조로 전환 후 JMeter 부하 테스트를 통해 안정성을 정량적으로 검증했습니다

---

## ✨ 주요 기능

| 기능 | 설명                                 |
|------|------------------------------------|
| ✅ **일정 CRUD** | 일정 등록/수정/삭제 + 반복 일정(매일, 매주, 매월, 매년) |
| ⚠️ **일정 충돌 방지** | 일정 등록 시 서버 단에서 중복 일정 자동 필터링        |
| 🤖 **일정 추천** | 일정 공백 분석 + WebClient 기반 외부 API 연동                    |
| 📢 **실시간 알림** | Kafka 이벤트 기반 + WebSocket 실시간 푸시                  |
| 📎 **파일 첨부** | Presigned URL 업로드 + 썸네일 비동기 처리                   |

---

## 🧱 아키텍처

### 전체 시스템 아키텍처

<img width="933" height="831" alt="Image" src="https://github.com/user-attachments/assets/55776b95-6313-44bd-9dbb-17b666c74d08" />

**요약**  

| 구분 | 구성 요소 | 설명 |
| --- | --- | --- |
| **Frontend** | Next.js | 캘린더 UI, 일정 CRUD, WebSocket 실시간 수신 |
| **Load Balancer** | Nginx (least_conn) | 서비스 서버 2대 간 트래픽 분산, SPOF 제거 |
| **Backend** | Spring Boot × 2 (AWS EC2 2GB) | Outbox + DLQ 기반 복원력 아키텍처 |
| **Message Broker** | Kafka (KRaft 3-Broker) (AWS EC2 4GB) | 비동기 이벤트 처리, 메시지 내구성 보장 |
| **Cache** | Redis | JWT 인증 캐싱, 이벤트 멱등 처리, 추천 결과 캐싱 |
| **Storage** | AWS S3 | Presigned URL 파일 업로드 |
| **Monitoring** | Prometheus, Loki, Grafana (별도 EC2) | 메트릭/로그 수집 및 대시보드 시각화 |

모니터링 서버를 서비스 서버와 분리하여 로그/메트릭 수집으로 인한 리소스 경쟁을 제거하고,
장애 상황에서도 서비스 영향도를 최소화했습니다.

---

## 🔥 분산 서버 전환 및 부하 테스트

### 전환 배경

단일 서버 환경의 다음 한계를 확인하고 분산 구조로 전환을 결정했습니다.

- **SPOF**: 서비스 서버 장애 시 전체 시스템 중단
- **자원 한계**: 단일 노드의 CPU/RAM으로 실 서비스 수준 트래픽 처리 불가
- **확장성 부재**: 스케일 아웃 시 로드밸런싱 성능 변화 미검증

### 분산 인프라 구성

```
[JMeter] → [Nginx LB] → [App Server 1 (EC2 2GB)]
                      → [App Server 2 (EC2 2GB)]
                              ↓
                    [Kafka KRaft 3-Broker (EC2 4GB)]
                              ↓
                         [AWS RDS MySQL]
                         [Redis Cache]
```

### JMeter 부하 테스트 결과 

### 단일 서버 정상 테스트
| 항목 | 결과 |
|------|------|
| 평균 응답속도 | 74ms |
| P95 지연시간 | 57ms |
| 에러율 | 0% |
| 메시지 유실/중복 | 0건 |

### 단일 서버 부하 테스트 (JMeter)
| 시나리오 | 평균 응답시간 | P95 | 에러율 |
|---------|------------|-----|-------|
| 50VU (2차) | 187ms | 376ms | 0% |
| 100VU | 1,155ms | 2,187ms | 0% |

- 50VU: DB 커넥션 풀 포화 발견 → maxPoolSize 20→50 개선 후 에러율 0% 달성
- 100VU: Outbox 초기 지연 8~9s 스파이크 발생 → Kafka 배압으로 빠르게 안정화

### 단일 서버 일괄 처리 테스트
| 시나리오 | 평균 응답시간 | 에러율 | 유실 | 중복 |
|---------|------------|-------|-----|-----|
| 3만건 | 39ms | 0% | 0건 | 0건 |
| 5만건 | 39ms | 0% | 0건 | 0건 |

- eventId 기반 멱등 처리로 대량 요청에서도 중복 0건, 유실 0건 검증 완료
- DLQ → 재처리 후 최종 정상 처리율 100%

### 분산 서버 부하 테스트 

| 구간 | 최종 시도 | 에러율 | 처리량(TPS) | 주요 해결 이슈 |
| --- | --- | --- | --- | --- |
| **30VU** | 6차 | **0.18%** | - | Nginx 설정 튜닝, JWT 인증 Redis 캐싱 |
| **50VU** | 8차 | **0.35%** | 30/sec | RDS 커넥션 풀 확장, HikariCP 튜닝 |
| **60VU** | 6차 | **0.05%** | 42.5/sec | DB 데드락 해결, @Async 분리, Redis Pre-check |

- **DLQ Retry Count: 0** — 7,000건+ 처리 중 재처리 큐로 넘어간 건 없음
- **Kafka Consumer Lag**: 순간 급등 후 즉시 0 수렴 (자가 회복 확인)
- **HikariCP**: maximum-pool-size 60 기준 절반 이하(25~30개)로 안정 운용
  📝 **상세 트러블슈팅 과정**
- [분산 서버 전환 후 부하 테스트로 병목 구간 찾기 1 (30VU ~ 50VU)](https://codingweb.tistory.com/325)
- [분산 서버 전환 후 부하 테스트로 병목 구간 찾기 2 (60VU)](https://codingweb.tistory.com/326)
---

## 🔧 주요 트러블슈팅

### 분산 서버 환경

| 문제 | 원인 | 해결 |
| --- | --- | --- |
| **DB 데드락** | Gap Lock 경합 (동일 memberId 동시 요청) | COUNT 쿼리 교체 + Lock Ordering + JDBC Batch Insert |
| **@Transactional 내 외부 API 호출** | 트랜잭션 범위 내 외부 I/O → DB 커넥션 장시간 점유 | @Async 분리로 트랜잭션 범위 축소 |
| **502 Bad Gateway** | Nginx proxy_read_timeout 초과 + HikariCP 고갈 | timeout 상향 + Redis Pre-check로 DB 진입 전 중복 차단 |
| **Nginx 로드밸런싱 불균형** | keepalive로 특정 서버에 연결 고착 | least_conn 알고리즘 + keepalive 제거 |
| **JWT 인증 필터 병목** | 모든 요청마다 DB 조회 → 커넥션 선점 | Redis 캐싱으로 DB 조회 제거 |
| **OS 레벨 연결 한계** | worker_connections 1024 기본값 초과 | worker_connections 4096, epoll, multi_accept 적용 |

### 단일 서버 환경

| 문제 | 해결 |
| --- | --- |
| **Kafka 이벤트 중복 처리** | eventId 기반 멱등 처리 + DLQ 재처리 시 ShedLock으로 중복 실행 방지 |
| **파일 업로드 정합성** | Presigned URL 비동기 업로드 + 임시 경로 + Lifecycle Rule로 고아 객체 방지 |
| **JVM Full GC 반복** | G1GC 튜닝 + 모니터링 서버 분리로 리소스 경쟁 제거 |
 
---

## 🧪 품질 보증

- TestContainers 기반 Kafka / Redis / MySQL 통합 테스트
- JMeter 부하 테스트 (단일 서버 10,000건 / 분산 서버 7,000건+)
  - 메시지 유실: **0건**
  - 중복 처리율: **0%** (eventId 기준)
- DLQ → 재처리 후 최종 정상 처리율: **100%**
---

## 📚 상세 문서 및 블로그

| 주제 | 링크 |
| --- | --- |
| 아키텍처 설계 | docs/architecture |
| 이벤트 처리 및 재처리 전략 | docs/event |
| 성능 테스트 및 모니터링 | docs/performance |
| AWS EC2 기반 분산 인프라 구축기 1 | [블로그](https://codingweb.tistory.com/318) |
| AWS EC2 기반 분산 인프라 구축기 2 | [블로그](https://codingweb.tistory.com/319) |
| Kafka KRaft 3-Broker 클러스터 전환 | [블로그](https://codingweb.tistory.com/320) |
| Docker + Nginx 로드밸런싱 구성과 SPOF 검증 | [블로그](https://codingweb.tistory.com/317) |
| 부하 테스트 트러블슈팅 1 (30VU ~ 50VU) | [블로그](https://codingweb.tistory.com/325) |
| 부하 테스트 트러블슈팅 2 (60VU) | [블로그](https://codingweb.tistory.com/326) |
 