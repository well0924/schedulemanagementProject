# 📅 일정 관리 프로젝트

> **개발 기간**: 2025.01 ~ 2025.06  
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

![Image](https://github.com/user-attachments/assets/c6b0a448-d7b6-4dc8-a47c-78546f60f4ba)

- 도메인 분리 기반 멀티모듈
- 각 도메인은 api / core / connector / infra로 계층 분리
- Kafka 기반 이벤트 전파 및 후처리 (Outbox 패턴, DLQ 처리, RetryScheduler)
- Redis를 이용한 분산락 및 스케줄 중복 방지
- WebSocket을 통한 실시간 알림 전송
- Presigned URL을 활용한 S3 직접 업로드 및 비동기 썸네일 생성

🔧 CI/CD 및 운영 모니터링

CI/CD

- GitHub Actions를 활용해 main 브랜치 머지 시 자동 배포
- Jib 기반 Docker 이미지 빌드 → Lightsail 서버에 SSH로 배포
- docker-compose.prod.yml로 서비스 컨테이너 구동
- nginx를 활용한 포트 기반 라우팅 구성

로그 수집 및 모니터링

- Promtail을 통해 Spring Boot / Kafka / Redis 로그를 Loki로 전송
- Loki + Grafana를 활용해 requestId, email, 알림 수신자 기준 로그 추적
- Prometheus Exporter (Kafka, Redis, Node, JVM) 기반 메트릭 수집
- Grafana에서 JVM Heap, GC 시간, Kafka 처리량, DLQ 발생률, WebSocket 지연 시간 등을 시각화
- MDC 로깅(`requestId`, `receiverId`) 기반 로그 트레이싱 구성

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

![Image](https://github.com/user-attachments/assets/0d985e10-7b5a-4a5b-bc31-e7be84251119)


## 🧠 기술적 고민 및 해결 사례

| 주제                                                                 | 설명                                                                                                                                                                                                                                                                                                                                 |
|--------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 🖼️ [Presigned URL 리사이징 비동기 처리](https://codingweb.tistory.com/257) | 초기에는 이미지 파일을 서버가 직접 받아 저장하면서 I/O 병목과 메모리 사용량 급증 문제가 발생했다. 이를 해결하기 위해 Presigned URL 방식을 도입해, 클라이언트가 S3에 직접 업로드하고, 서버는 업로드 완료 후 `@Async` 비동기 방식으로 썸네일을 생성하도록 구조를 개선했다. 이로 인해 업로드 속도와 서버 안정성이 모두 향상되었다.                                                                                                                               
|
| 🧠 [OpenAI API Fallback 처리](https://codingweb.tistory.com/259)     | AI 일정 추천 실패 시 CircuitBreaker + Fallback 적용                                                                                                                                                                                                                                                                                         |
| 🗓️ [Schedule 충돌 검사 로직](https://codingweb.tistory.com/267)         | ScheduleType 분기 처리로 반복/하루 일정 충돌 탐지. Redis 기반 빠른 탐색은 추후 적용 예정                                                                                                                                                                                                                                                                       |
| 🔁 [Kafka DLQ 재처리](https://codingweb.tistory.com/268)              | Kafka 소비 실패 메시지를 DLQ → DB 저장 및 재처리 스케줄러 구성                                                                                                                                                                                                                                                                                         |
| 💾 [Outbox 패턴 기반 이벤트 발행](https://codingweb.tistory.com/272)        | Kafka 메시지 유실 방지를 위해 Outbox 테이블에 이벤트 저장 → 전용 Publisher에서 Kafka로 전송. 트랜잭션 일관성과 이벤트 발행 안정성 확보. ShedLock 기반 다중 인스턴스 동시성 제어 적용.                                                                                                                                                                                                         |
| ⚙️ [운영 메모리 최적화 및 모니터링 서버 분리](https://codingweb.tistory.com/281)    | 초기 서비스 서버에서 Prometheus, Loki 등 모니터링 스택을 함께 구동하면서 전체 컨테이너 메모리 사용량이 1.67GB까지 증가. Grafana 기반 메트릭 분석 결과, JVM Heap, Metaspace, DirectMemory에서 과도한 리소스 사용 확인. 이를 해결하기 위해 Prometheus, Loki를 모니터링 전용 서버로 이관하고, Jib 기반 JVM 옵션(-Xmx512m, -XX:MaxMetaspaceSize=128m, G1GC) 튜닝을 병행. 결과적으로 컨테이너 메모리 사용량을 1.43GB까지 절감하고, 서비스 안정성과 로그 수집 효율이 개선됨. |
| 🛠️ [GitHub Actions Gradle 캐시 적용](https://codingweb.tistory.com/285)                                | GitHub Actions로 CI/CD를 구성한 이후, 매번 전체 Gradle 의존성을 다운로드하면서 빌드 시간이 길어지는 병목이 있었다. 이를 해결하기 위해 `actions/cache@v3`를 활용해 `~/.gradle/caches`, `~/.gradle/wrapper` 경로를 캐싱하고, `hashFiles` 기반으로 Gradle 파일 변경 시에만 캐시를 갱신하도록 설정했다. 캐시 히트 시 빌드 시간이 평균 1분 이상 단축되어 PR 리뷰 속도와 피드백 루프가 크게 향상되었다.                                                                                                                                                                                                                                                                                                                                   |
---