## 🧱 아키텍처

### 1.전체 시스템 아키텍처
![Image](https://github.com/user-attachments/assets/c6b0a448-d7b6-4dc8-a47c-78546f60f4ba)

**요약**  
프로젝트는 **서비스 서버**와 **모니터링 서버**를 분리하여 운영 안정성을 높였습니다.  
Kafka 기반 비동기 이벤트 처리, Redis 기반 캐싱/락 관리, S3 업로드, WebSocket 실시간 알림을 중심으로 구성되며  
Promtail → Loki / Prometheus → Grafana를 통해 로그와 메트릭을 통합 시각화합니다.

| 구분 | 구성 요소 | 설명 |
|------|------------|------|
| **Frontend** | Next.js (Vercel 배포 예정) | 캘린더 UI, 일정 CRUD, WebSocket 실시간 수신 |
| **Backend** | Spring Boot, Kafka, Redis, MySQL, S3 | Outbox + DLQ 기반 복원력 아키텍처 |
| **Monitoring** | Prometheus, Loki, Grafana | 메트릭/로그 수집 및 대시보드 시각화 |

### 2.코드 레벨 헥사고날 아키텍처

![Image](https://github.com/user-attachments/assets/f5a60741-5e24-48f4-9aef-a310b0eacbaf)

**구조 개요**
- 도메인 분리 기반 멀티모듈 설계
- 각 도메인은 `api → inconnector → core → outconnector → infra` 계층으로 구성
- Core는 비즈니스 로직만 담당하며, 외부 의존성은 Port & Adapter 구조로 분리

| 계층 | 역할 | 예시 |
|------|------|------|
| **API Layer** | Controller, Kafka Consumer | 사용자 진입점 |
| **Inbound Port** | InConnector | 요청 전달 |
| **Core Domain** | Service, Model | 비즈니스 로직 |
| **Outbound Port** | OutConnector | DB/Kafka/WebClient 호출 |
| **Infrastructure** | 기술 계층 | Redis, Kafka, S3, RDS 등 |

---

### 3.CI/CD 파이프라인
![Image](https://github.com/user-attachments/assets/fcf8cd41-2fbf-44a4-b7ae-1402b5fd85d5)

**구성 요약**
- GitHub Actions 기반 자동 배포 파이프라인
- Jib으로 Docker 이미지 빌드 후 Lightsail 서버로 SSH 배포
- Gradle 캐시(`actions/cache@v3`) 적용으로 빌드 시간 약 1분 단축

**배포 흐름**
1. main 브랜치 merge 시 자동 트리거
2. Gradle 빌드 및 테스트
3. Jib 기반 Docker 이미지 생성
4. Docker Hub 푸시 → Lightsail SSH 접속
5. `docker-compose up -d` 자동 실행

---