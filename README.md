# 📅 일정 관리 프로젝트

> **개발 기간**: 2025.01 ~ 2025.05  
> **주요 기술**: Spring Boot, Kafka, Redis, S3, OpenFeign, WebSocket, Next.js  
> **배포 환경**: AWS Lightsail + GitHub Actions

---

## 📌 프로젝트 개요

사용자가 웹에서 개인 일정을 등록/관리할 수 있는 캘린더 기반 서비스입니다.  
**반복 일정, 일정 충돌 방지, AI 기반 추천 일정, 실시간 알림, 파일 첨부 기능**을 포함하고 있으며, 운영 환경을 고려한 **Kafka 기반 이벤트 아키텍처**, **WebSocket 실시간 알림**, **Presigned URL 기반 S3 파일 업로드** 등을 설계했습니다.

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| ✅ **일정 CRUD** | 일정 등록/수정/삭제 + 반복 일정(매일, 매주, 매월, 매년) |
| ⚠️ **일정 충돌 방지** | 일정 등록 시 서버 단에서 중복 일정 자동 필터링 |
| 🤖 **일정 추천** | OpenAI API + OpenFeign 사용. 비어 있는 시간대 기반 추천 |
| 📢 **실시간 알림** | Kafka 이벤트 발행 → WebSocket 실시간 수신 알림 |
| 📎 **파일 첨부** | Presigned URL로 직접 업로드 → 썸네일 생성 (`@Async`) |

---

## 🛠 사용 기술 스택

### Backend
- *Java 17, Spring Boot 3*
- *JPA, QueryDSL*
- *Kafka (이벤트 전파 및 DLQ 처리)*
- *Redis (세션/캐시, 일정 중복 처리)*
- *OpenFeign (AI 일정 추천 연동)*
- *AWS S3 (Presigned URL 기반 파일 업로드/다운로드)*

### Frontend
- *Next.js 14* + *TypeScript*
- *Tailwind CSS*
- *FullCalendar*

### Infra / DevOps
- *AWS Lightsail, RDS, S3*
- *GitHub Actions (CI/CD 파이프라인)*
- *Docker, Docker Compose*
- Prometheus + Grafana (모니터링 구성 중)

---

## 🧱 아키텍처

![Image](https://github.com/user-attachments/assets/1fa64eeb-dfe8-4166-82b6-aceac0af3f76)

- 도메인 분리 기반 멀티모듈
- 각 도메인은 api / core / connector / infra로 계층 분리
- 이벤트 기반 후처리 (Kafka + Async + EventPublisher)


### 실행 방법 (로컬)
```
bash
git clone https://github.com/well0924/schedulemanagementProject.git
./gradlew bootRun

git clone https://github.com/well0924/schedulemanagement-front.git
cd my-app
npm install
npm run dev

```

## 🗂 ERD 및 모델 구조

![Image](https://github.com/user-attachments/assets/cac93df5-f180-47d6-bbbf-cff620bffc0a)


## 🧠 기술적 고민 및 해결 사례

| 주제                                                                 | 설명 |
|--------------------------------------------------------------------|------|
| 🖼️ [Presigned URL 리사이징 비동기 처리](https://codingweb.tistory.com/257) | 대용량 이미지 업로드 시 썸네일 자동 생성 + 서버 부하 분산 |
| 🧠 [OpenAI API Fallback 처리](https://codingweb.tistory.com/259)     | AI 일정 추천 실패 시 CircuitBreaker + Fallback 적용 |
| 🗓️ [Schedule 충돌 검사 로직](https://codingweb.tistory.com/267)         | ScheduleType 분기 처리로 반복/하루 일정 충돌 탐지. Redis 기반 빠른 탐색은 추후 적용 예정 |
| 🔁 [Kafka DLQ 재처리](https://codingweb.tistory.com/268)              | Kafka 소비 실패 메시지를 DLQ → DB 저장 및 재처리 스케줄러 구성 |
| 💾 [Outbox 패턴 기반 이벤트 발행](https://codingweb.tistory.com/272)                                        | Kafka 메시지 유실 방지를 위해 Outbox 테이블에 이벤트 저장 → 전용 Publisher에서 Kafka로 전송. 트랜잭션 일관성과 이벤트 발행 안정성 확보. ShedLock 기반 다중 인스턴스 동시성 제어 적용. |
---