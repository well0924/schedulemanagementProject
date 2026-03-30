# 📅 일정 관리 프로젝트 Dayline

> **개발 기간**
> 
> 2025.01 ~ 2025.06 (1차:MVP 기능 구현)
> 
> 2025.09 (2차: 고도화)
> 
> **주요 기술**: Spring Boot, Kafka, Redis, S3, WebClient, WebSocket, Next.js
> 
> **배포 환경**: AWS Lightsail + GitHub Actions
> 
> **모니터링**: Grafana,Prometheus,Loki

---

## 📌 프로젝트 개요

DailyLine은 웹 기반 개인 일정 관리 서비스로, **반복 일정 / 충돌 방지 / 실시간 알림 / 파일 첨부 / AI 일정 추천** 기능을 포함합니다.  
일정 생성 이후 발생하는 비동기 작업을 안정적으로 처리하기 위해 운영 환경을 고려한 구조로 설계했습니다.

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

## 🧪 품질 보증

### 정상 테스트
| 항목 | 결과 |
|------|------|
| 평균 응답속도 | 74ms |
| P95 지연시간 | 57ms |
| 에러율 | 0% |
| 메시지 유실/중복 | 0건 |

### 부하 테스트 (JMeter)
| 시나리오 | 평균 응답시간 | P95 | 에러율 |
|---------|------------|-----|-------|
| 50VU (2차) | 187ms | 376ms | 0% |
| 100VU | 1,155ms | 2,187ms | 0% |

- 50VU: DB 커넥션 풀 포화 발견 → maxPoolSize 20→50 개선 후 에러율 0% 달성
- 100VU: Outbox 초기 지연 8~9s 스파이크 발생 → Kafka 배압으로 빠르게 안정화

### 일괄 처리 테스트
| 시나리오 | 평균 응답시간 | 에러율 | 유실 | 중복 |
|---------|------------|-------|-----|-----|
| 3만건 | 39ms | 0% | 0건 | 0건 |
| 5만건 | 39ms | 0% | 0건 | 0건 |

- eventId 기반 멱등 처리로 대량 요청에서도 중복 0건, 유실 0건 검증 완료
- DLQ → 재처리 후 최종 정상 처리율 100%

### 통합 테스트
- TestContainers 기반 Kafka / Redis / MySQL 통합 테스트
- 강제 예외 주입 + Awaitility 기반 DLQ 재처리 흐름 검증
---

## 🧱 아키텍처

### 전체 시스템 아키텍처
<img width="1414" height="779" alt="Image" src="https://github.com/user-attachments/assets/de12e00a-69ea-4c19-b601-f484127046a9" />

**요약**  

본 프로젝트는 모니터링 서버를 서비스 서버와 분리하여 로그/메트릭 수집으로 인한 리소스 경쟁을 제거하고
장애 상황에서도 서비스 영향도를 최소화했습니다.

Kafka 기반 비동기 이벤트 처리, Redis, S3, WebSocket을 중심으로 구성되며 Promtail → Loki / Prometheus → Grafana를 통해 로그와 메트릭을 통합 시각화합니다.

| 구분 | 구성 요소 | 설명 |
|------|---------|------|
| **Frontend** | Next.js | 캘린더 UI, 일정 CRUD, WebSocket 실시간 수신 |
| **Backend** | Spring Boot, Kafka, Redis, MySQL, S3 | Outbox + DLQ 기반 복원력 아키텍처 |
| **Monitoring** | Prometheus, Loki, Grafana | 메트릭/로그 수집 및 대시보드 시각화 |

---

## 🔧 주요 트러블슈팅

- Kafka 이벤트 중복 처리 문제  
  → eventId 기반 멱등 처리 + ShedLock으로 중복 실행 방지
  📝 [왜 Kafka를 선택했는가?](https://codingweb.tistory.com/260) | [DLQ + 재처리](https://codingweb.tistory.com/268) | [Outbox 패턴](https://codingweb.tistory.com/272) | [EOS 보장](https://codingweb.tistory.com/296)


- 파일 업로드 정합성 문제  
  → Presigned URL 비동기 업로드  
  → 임시 경로 + Lifecycle Rule로 고아 객체 방지
📝 [multipart 문제점과 Presigned URL 도입 배경](https://codingweb.tistory.com/254) | [Presigned URL 적용](https://codingweb.tistory.com/257) | [Lifecycle Rule 적용 및 고도화](https://codingweb.tistory.com/284)


- 일정 추천 성능 문제  
  → OpenFeign(동기) → WebClient(비동기) 전환으로 논블로킹 처리
  → Redis 캐싱 + CircuitBreaker Fallback으로 외부 API 장애 대응
  📝 [OpenFeign으로 일정추천 구현](https://codingweb.tistory.com/259) | [WebClient 전환 및 고도화](https://codingweb.tistory.com/287)


- JVM Full GC 반복
  → G1GC 튜닝 + 모니터링 서버 분리로 GC Pause 0.1s → 0.01s 이하 개선
  📝 [모니터링 환경 구축](https://codingweb.tistory.com/280) | [JVM 튜닝 + 모니터링 서버 분리](https://codingweb.tistory.com/281)