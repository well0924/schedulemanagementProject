# 📅 일정 관리 프로젝트

> **개발 기간**: 2025.01 ~ 2025.06(1차)/2025.09(2차)
> 
> **주요 기술**: Spring Boot, Kafka, Redis, S3, WebClient, WebSocket, Next.js
> 
> **배포 환경**: AWS Lightsail + GitHub Actions
> 
> **모니터링**: Grafana,Prometheus,Loki

---

## 📌 프로젝트 개요

웹 기반 개인 일정 관리 서비스로, **반복 일정 / 충돌 방지 / 실시간 알림 / 파일 첨부 / AI 일정 추천** 기능을 포함합니다.  
일정 생성 이후 발생하는 비동기 작업을 안정적으로 처리하기 위해 운영 환경을 고려한 구조로 설계했습니다.

---

## ✨ 주요 기능

| 기능 | 설명                                 |
|------|------------------------------------|
| ✅ **일정 CRUD** | 일정 등록/수정/삭제 + 반복 일정(매일, 매주, 매월, 매년) |
| ⚠️ **일정 충돌 방지** | 일정 등록 시 서버 단에서 중복 일정 자동 필터링        |
| 🤖 **일정 추천** | 비어 있는 시간대 기반 추천                    |
| 📢 **실시간 알림** | 이벤트 기반 실시간 수신 알림                   |
| 📎 **파일 첨부** | 썸네일 비동기 처리                    |

---

## 🧪 품질 보증

- TestContainers 기반 통합 테스트 환경 구성
- Outbox → Kafka → Consumer 비동기 흐름 검증
- DLQ 재처리 시나리오 및 부하 테스트(JMeter) 검증

---

## 🧱 아키텍처

### 전체 시스템 아키텍처
![Image](https://github.com/user-attachments/assets/c6b0a448-d7b6-4dc8-a47c-78546f60f4ba)

**요약**  
프로젝트는 **서비스 서버**와 **모니터링 서버**를 분리하여 운영 안정성을 높였습니다.  
Kafka 기반 비동기 이벤트 처리, Redis, S3, WebSocket을 중심으로 구성되며  
Promtail → Loki / Prometheus → Grafana를 통해 로그와 메트릭을 통합 시각화합니다.

| 구분 | 구성 요소 | 설명 |
|------|---------|------|
| **Frontend** | Next.js | 캘린더 UI, 일정 CRUD, WebSocket 실시간 수신 |
| **Backend** | Spring Boot, Kafka, Redis, MySQL, S3 | Outbox + DLQ 기반 복원력 아키텍처 |
| **Monitoring** | Prometheus, Loki, Grafana | 메트릭/로그 수집 및 대시보드 시각화 |

---
