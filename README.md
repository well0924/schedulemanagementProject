# 📅 일정 관리 프로젝트

> **개발 기간**: 2025.01 ~ 2025.06(1차)/2025.09(2차)
> 
> **주요 기술**: Spring Boot, Kafka, Redis, S3, WebClient, WebSocket, Next.js
> 
> **배포 환경**: AWS Lightsail + GitHub Actions
> 
> **모니터링**: Grafana,Prometheus,Loki

---

## ✅ 개발 완료 목록 (2025.09 기준)

MVP 수준의 기본 일정 기능, 실시간 알림, 파일 첨부, AI 일정 추천 및 운영 환경 구축까지 완료된 상태입니다.

🎯 완료 기능

- **일정 등록/수정/삭제 (반복 일정 포함: 매일/매주/매월/매년)**
- **일정 충돌 방지 (서버단 중복 필터링)**
- **일정 리마인드 알림 (예약 이벤트 발행 + DLQ 재처리)**
- **사용자 알림 구독 설정 (알림 채널별 토글 API + Consumer 필터링)**
- **AI 일정 추천 (OpenAI API + Fallback 처리)**
- **Kafka 기반 이벤트 전파 및 DLQ 처리+슬랙알림**
- **WebSocket 실시간 알림**
- **Presigned URL 기반 S3 파일 업로드 + 썸네일 비동기 생성**
- **회원가입 / 로그인 / JWT + Redis RefreshToken 관리**
- **Prometheus + Grafana + Loki 운영 모니터링**
- **GitHub Actions 기반 CI/CD 자동 배포**
- **Email / Push 알림 채널 연동 (FCM 등)**
- **모바일 반응형 UI 고도화**
- **반복 일정 고도화 (종료 조건, 예외 처리 등)**
- **일정 추천 고도화 (프롬프트 개선+WebClient 변경)**
- **WebSocket 재접속 시 미수신 알림 복원 처리**
- **일정추천 기능 및 kafka알림 EOS 성능 측정(Jmeter+Grafana+Prometheus)**
---

## 🔜 향후 계획

- 일정 통계 기능 (일간/주간/월간 기준 시각화)
- 일정 태그 기능 및 태그 기반 검색
- GraalVM 적용
- 프론트 Vercel 배포후 EndToEnd 테스트

---

## 📌 프로젝트 개요

사용자가 웹에서 개인 일정을 등록/관리할 수 있는 캘린더 기반 서비스입니다.  
**반복 일정, 일정 충돌 방지, AI 기반 추천 일정, 실시간 알림, 파일 첨부 기능**을 포함하고 있으며, 운영 환경을 고려한 **Kafka 기반 이벤트 아키텍처**, **WebSocket 실시간 알림**, **Presigned URL 기반 S3 파일 업로드** 등을 설계했습니다.

---

## ✨ 주요 기능

| 기능 | 설명                                        |
|------|-------------------------------------------|
| ✅ **일정 CRUD** | 일정 등록/수정/삭제 + 반복 일정(매일, 매주, 매월, 매년)       |
| ⚠️ **일정 충돌 방지** | 일정 등록 시 서버 단에서 중복 일정 자동 필터링               |
| 🤖 **일정 추천** | OpenAI API 사용. 비어 있는 시간대 기반 추천 |
| 📢 **실시간 알림** | Kafka 이벤트 발행 → WebSocket 실시간 수신 알림        |
| 📎 **파일 첨부** | Presigned URL로 직접 업로드 → 썸네일 생성 (`@Async`) |

---

🧪 품질 보증

TestContainers 기반 Kafka/Redis/MySQL 통합 테스트

Awaitility 기반 비동기 이벤트 흐름 검증 (Outbox → Kafka → Consumer → DB 저장)

DLQ/Retry 장애 시나리오 재현 및 재처리율 검증

JMeter 부하/일괄 테스트 자동화

---

## 🛠 사용 기술 스택

### Backend
- *Java 17, Spring Boot 3*
- *JPA, QueryDSL*
- *Kafka (이벤트 전파 및 DLQ 처리)*
- *Redis (세션/캐시, 일정 중복 처리)*
- *OpenFeign -> WebClient (AI 일정 추천 연동)*
- *AWS S3 (Presigned URL 기반 파일 업로드/다운로드)*
- *Flyway (DB 마이그레이션 버전 관리)*

### Frontend
- *Next.js 14* + *TypeScript*
- *Tailwind CSS*
- *FullCalendar*

### Infra / DevOps
- *AWS Lightsail, RDS, S3*
- *GitHub Actions (CI/CD 파이프라인)*
- *Docker, Docker Compose*
- *Prometheus + Loki + Promtail + Grafana(모니터링)*

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

**ERD 설계 원칙**: JPA 연관관계 최소화 및 DB 무결성 강제
저희 ERD는 JPA 연관관계를 사용하지 않고 데이터베이스의 외래 키(FK) 및 제약조건을 통해 데이터의 정합성을 보장하는 것을 목표로 합니다.

**ORM 연관관계 최소화**: 엔티티는 다른 엔티티의 외래 키를 단순히 Long 타입의 ID(scheduleId, attachId 등)로만 보관합니다. 이는 코드의 결합도를 낮추고, 흔히 발생하는 N+1 문제의 리스크를 근본적으로 줄입니다.

## 🧠 기술적 고민 및 해결 사례

| 주제                                                                   | 설명                                                                                                                                                                                                                                                                                                                                 |
|----------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 🖼️ [Presigned URL 리사이징 비동기 처리](https://codingweb.tistory.com/257)   | 서버 I/O 병목과 메모리 과부하 문제를 해결하기 위해, 클라이언트가 S3에 직접 업로드하고 서버는 @Async로 썸네일을 생성하는 구조로 개선했다. 측정 결과, Presigned URL은 직접 업로드 대비 약 2.5배 높은 처리량을 보였고, 서버 부하가 크게 감소했다.                                                                                                                                                                            
| 🧠 [OpenAI 일정추천 WebClient 전환](https://codingweb.tistory.com/287)     | 기존 OpenFeign 기반 Blocking 호출을 WebClient + Reactive 구조로 전환. Redis 캐싱과 CircuitBreaker + Fallback을 적용해 안정성을 강화했다. 성능 검증은 JMeter와 Grafana를 활용해 AWS LightSail 2GB 환경에서 진행하였다. 단일 사용자 기준 최초 호출은 약 4.46초가 소요되었으나, 캐시 Hit 시 평균 182ms로 단축되어 응답 속도가 약 95% 개선되었다.또한 부하 테스트에서는 동시 사용자 50명 환경에서 평균 응답 346ms(P95 556ms), 동시 사용자 100명 환경에서도 평균 응답 945ms(P95 1.8s)로, 에러율 0%를 유지하며 안정적으로 처리 가능함을 확인하였다. 이를 통해 반복 요청 최적화뿐 아니라, 실제 운영 환경에서도 일정 규모의 트래픽을 안정적으로 처리할 수 있는 구조임을 검증하였다.|
| 🗓️ [Schedule 충돌 검사 로직](https://codingweb.tistory.com/267)           | ScheduleType 분기 처리로 반복/하루 일정 충돌 탐지. Redis 기반 빠른 탐색은 추후 적용 예정                                                                                                                                                                                                                                                                       |
| 📨 [Kafka EOS 기반 복원력 아키텍처](https://codingweb.tistory.com/290) | Outbox 패턴과 DLQ/Retry를 통합하여 메시징 신뢰성을 확보. Outbox 테이블 + ShedLock Publisher로 유실 없는 발행, Consumer에서 eventId 기반 멱등 처리로 중복 방지. 장애 발생 시 DLQ 저장 → 지연큐 기반 재처리 → 최종 실패 시 Slack 알림까지 연계. JMeter(1만/3만/5만 건) 부하테스트에서 유실 0건, 중복 0건, 평균 응답 39ms, 에러율 0%를 검증. Grafana로 Kafka Consumer 처리량·Lag·DB 커넥션 풀 모니터링까지 완료해 운영 환경에서도 EOS 보장 가능성을 입증.|
| ⚙️ [운영 메모리 최적화 및 모니터링 서버 분리](https://codingweb.tistory.com/281)      | 초기 서비스 서버에서 Prometheus, Loki 등 모니터링 스택을 함께 구동하면서 전체 컨테이너 메모리 사용량이 1.67GB까지 증가. Grafana 기반 메트릭 분석 결과, JVM Heap, Metaspace, DirectMemory에서 과도한 리소스 사용 확인. 이를 해결하기 위해 Prometheus, Loki를 모니터링 전용 서버로 이관하고, Jib 기반 JVM 옵션(-Xmx512m, -XX:MaxMetaspaceSize=128m, G1GC) 튜닝을 병행. 결과적으로 컨테이너 메모리 사용량을 1.43GB까지 절감하고, 서비스 안정성과 로그 수집 효율이 개선됨. |
| 🛠️ [GitHub Actions Gradle 캐시 적용](https://codingweb.tistory.com/285) | GitHub Actions로 CI/CD를 구성한 이후, 매번 전체 Gradle 의존성을 다운로드하면서 빌드 시간이 길어지는 병목이 있었다. 이를 해결하기 위해 `actions/cache@v3`를 활용해 `~/.gradle/caches`, `~/.gradle/wrapper` 경로를 캐싱하고, `hashFiles` 기반으로 Gradle 파일 변경 시에만 캐시를 갱신하도록 설정했다. 캐시 히트 시 빌드 시간이 평균 1분 이상 단축되어 PR 리뷰 속도와 피드백 루프가 크게 향상되었다.                                                      |
---