## 아키텍처

### 전체 구성

<img width="1171" height="831" alt="전체 시스템 구성" src="https://github.com/user-attachments/assets/236161e9-17bf-435a-a536-35222099ff5c" />

| 구분 | 구성 요소 | 설명 |
|------|-----------|------|
| **Frontend** | Next.js (App Router) | 캘린더 UI, 일정 CRUD, WebSocket 실시간 수신 |
| **App 서버 2대** | Spring Boot (EC2) | API, Outbox 발행기, Kafka 컨슈머, 스케줄러. 발행기·스케줄러는 ShedLock으로 한 대만 실행 |
| **인프라 서버 1대** | Nginx, Redis, Kafka 브로커 3개 (KRaft) | 앱 서버 2대로 요청 분산, 캐시·ShedLock, 이벤트 브로커 |
| **데이터** | MySQL (RDS), S3 | 업무 데이터와 Outbox, 첨부파일 (PreSigned URL) |
| **모니터링 서버** | Prometheus, Grafana, Loki, Tempo | 메트릭·로그·트레이스 수집과 대시보드 |

### 설계 의도 및 핵심 판단

- 일정 생성 이후 알림, 챗봇 이력 같은 후속 작업이 사용자 요청 경로(API)에 영향을 주지 않도록 Kafka 기반 비동기 이벤트 구조를 선택했습니다. → [이벤트 흐름](../event/event-flow.md)
- 앱 서버를 2대로 나눠, 한 대를 배포하거나 장애가 나도 다른 한 대가 요청을 받도록 했습니다. → [CI/CD](ci-cd-pipeline.md)
- 로그·메트릭 수집이 서비스 서버의 자원을 쓰지 않도록 모니터링 서버를 따로 두었습니다. → [모니터링](../monitoring/monitoring.md)

### 알려진 한계

- Kafka 브로커 3개와 Redis, Nginx가 인프라 서버 한 대에 있어, 브로커 프로세스 장애에는 복제로 대응하지만 호스트 장애에는 함께 멈춥니다.
- WebSocket 브로커가 서버마다 메모리에 따로 있어(`enableSimpleBroker`), 다른 서버에 연결된 사용자에게는 실시간 알림이 가지 않습니다. 알림 내역은 DB에 저장됩니다.

전체 목록은 [트러블슈팅 - 남은 한계](../troubleshooting/README.md#남은-한계)에 있습니다.
