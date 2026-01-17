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

- TestContainers 기반 Kafka / Redis / MySQL 통합 테스트
- JMeter 부하 테스트 (10,000건)
    - 메시지 유실: 0건
    - 중복 처리율: 0% (eventId 기준)
- DLQ → 재처리 후 최종 정상 처리율: 100%

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
  → eventId 기반 멱등 처리  
  → DLQ 재처리 시 ShedLock으로 중복 실행 방지


- 파일 업로드 정합성 문제  
  → Presigned URL 비동기 업로드  
  → 임시 경로 + Lifecycle Rule로 고아 객체 방지


- 일정 추천 성능 문제  
  → WebClient 비동기 호출  
  → 추천 결과 캐싱으로 요청 수 감소


- JVM Full GC 반복  
  → G1GC 튜닝  
  → 모니터링 서버 분리로 리소스 경쟁 제거

## 📚 상세 문서

- 아키텍처 설계: docs/architecture
- 이벤트 처리 및 재처리 전략: docs/event
- 성능 테스트 및 모니터링: docs/performance