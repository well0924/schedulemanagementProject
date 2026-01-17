### 로깅 설계


**설계 목적**

요청 단위 추적과 비동기 흐름 가시성 확보

비동기 이벤트 기반 아키텍처에서는 단순한 에러 로그만으로 장애 원인을 추적하기 어렵다고 판단했습니다.

본 프로젝트는 API 요청부터 Kafka 이벤트 처리, DLQ 재처리 흐름까지 하나의 요청 단위로 추적 가능한  
운영 중심 로깅 구조를 목표로 설계했습니다.
---
#### 로깅 전략

<img width="921" height="61" alt="Image" src="https://github.com/user-attachments/assets/c9cbfbd9-23cd-4f6e-839a-8f8da5daa663" />

- 모든 요청에 `requestId`를 부여하여 MDC에 저장
- API → Kafka Producer → Consumer → DLQ 재처리까지  
  동일한 `requestId`로 로그 상관 추적
- JSON 기반 구조화 로그로 Loki에서 효율적인 검색 및 필터링 가능

---

#### MDC 로그 포

```json
{
  "timestamp": "%d{yyyy-MM-dd'T'HH:mm:ss.SSSZ}",
  "level": "%level",
  "logger": "%logger",
  "message": "%message",
  "requestId": "%X{requestId}",
  "email": "%X{email}"
}
```

requestId: 요청 단위 흐름 추적

email: 장애 발생 시 사용자 영향 범위 식별

---

#### 비동기 처리 환경에서의 로그 추적 

Kafka Consumer 및 비동기 실행 환경에서도 MDC 컨텍스트가 유지되도록 설계

이를 통해 비동기 이벤트 처리 과정에서도 요청 단위 로그 추적 가능

----
#### 로그 기반 분석 사례

Kafka 이벤트 처리 실패 발생 시 동일한 requestId를 기준으로 API 요청 로그 → Consumer 로그 → DLQ 로그를 순차적으로 추적하여 장애 원인을 빠르게 식별