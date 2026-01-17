## 아키텍처

### 전체 아키텍처

<img width="1414" height="779" alt="Image" src="https://github.com/user-attachments/assets/de12e00a-69ea-4c19-b601-f484127046a9" />

- 프로젝트는 **서비스 서버**와 **모니터링 서버**를 분리하여 운영 안정성을 높였습니다.
- Kafka 기반 비동기 이벤트 처리, Redis 기반 캐싱/락 관리, S3 업로드, WebSocket 실시간 알림을 중심으로 구성되며  
  Promtail → Loki / Prometheus → Grafana를 통해 로그와 메트릭을 통합 시각화합니다.

| 구분 | 구성 요소 | 설명 |
|------|-----------|------|
| **Frontend** | Next.js | 캘린더 UI, 일정 CRUD, WebSocket 실시간 수신 |
| **Backend** | Spring Boot, Kafka, Redis, MySQL, S3 | Outbox + DLQ 기반 복원력 아키텍처 |
| **Monitoring** | Prometheus, Loki, Grafana | 메트릭/로그 수집 및 대시보드 시각화 |


### 설계 의도 및 핵심 판단

- 일정 생성 이후 알림, 파일 처리와 같은 후속 작업이 사용자 요청 경로(API)에 영향을 주지 않도록
  *Kafka 기반 비동기 이벤트 구조*를 선택했습니다.
- 장애 발생 시에도 핵심 기능(일정 CRUD)이 정상 동작하도록 Outbox + DLQ 기반 복원력 아키텍처를 적용했습니다.
- 로그 및 메트릭 수집으로 인한 리소스 경쟁을 방지하기 위해 서비스 서버와 모니터링 서버를 물리적으로 분리했습니다.
