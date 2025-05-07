# 일정 관리 프로젝트 


## 📌 프로젝트 개요

사용자가 일정 등록/수정/삭제 및 반복 설정을 할 수 있고,  
AI 기반 일정 추천, 실시간 알림, 채팅 기능을 제공하는 웹 캘린더 서비스입니다.

- 기간: 2025.01 ~ 2025.05
- 주요 기술: Spring Boot, Next.js, MySQL, Kafka, WebSocket, OpenFeign, Redis, S3
- 주요 기능:
    - 일정 CRUD 및 반복 일정 등록
    - 실시간 일정 알림 (WebSocket + Kafka)
    - 일정 충돌 방지 및 추천 일정 생성 (OpenAI API)
    - Presigned URL 기반 파일 첨부

## 🛠 사용 기술 스택

### Backend
- Java 17, Spring Boot 3
- JPA + QueryDSL
- Kafka (이벤트 기반 알림)
- Redis (캐시 관리)
- Amazon S3 (파일 업로드/다운로드)
- OpenFeign (AI 일정 추천 API 호출)

### Frontend
- Next.js 14 + TypeScript
- Tailwind CSS
- FullCalendar

### Infra / DevOps
- AWS Lightsail, RDS, S3
- GitHub Actions (CI/CD)
- Docker, Docker Compose
- Prometheus + Grafana (모니터링 예정)

## ✨ 주요 기능

- **일정 등록**
    - 반복 설정 (매일/매월/매년), 첨부파일 포함
- **일정 추천**
    - 사용자의 비어 있는 시간대 기반 추천 (OpenAI 사용)
- **알림 기능**
    - Kafka 이벤트 발행 → WebSocket 실시간 수신
- **파일 첨부**
    - S3 Presigned URL 기반 업로드 / 다운로드

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
 
![일정관리 (1)](https://github.com/user-attachments/assets/19cb4ace-786d-414c-a971-238dd38195fb)


### 7. 🔹 기술적 고민 + 트러블슈팅 + 개선 방향

### 🧠 기술적 고민 & 해결 과정
