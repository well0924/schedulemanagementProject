### 로깅 설계

**설계 목적**

비동기 이벤트 구조에서는 에러 로그 한 줄만으로 원인을 찾기 어렵습니다.
HTTP 요청은 요청 단위로, Kafka 이벤트는 이벤트 단위로 로그를 묶어, 한 이벤트가 컨슈머 → 재시도 → DLQ → 재처리를 거치는 과정을 따라갈 수 있게 했습니다.

---

#### MDC 키

| 키 | 넣는 곳 | 의미 |
|---|---|---|
| `requestId` | `MDCFilter` (HTTP 요청마다), `KafkaMDCUtil` (메시지 처리마다), 스케줄러 | 한 번의 요청 또는 한 번의 메시지 처리 |
| `eventId` | `KafkaMDCUtil` (컨슈머, DLQ·재처리 스케줄러) | 이벤트 ID. 재시도와 재처리를 거쳐도 그대로 유지 |
| `traceId`, `spanId` | Micrometer Tracing | Tempo 트레이스와 로그 연결 |

---

#### 로그 형식

콘솔은 사람이 읽는 형식, 파일은 Loki가 수집하는 JSON 형식으로 남깁니다 (`logback-spring.xml`, 7일 보관).

```json
{
  "timestamp": "...",
  "level": "%level",
  "thread": "%thread",
  "logger": "%logger",
  "message": "%message",
  "eventId": "%X{eventId}",
  "requestId": "%X{requestId}",
  "traceId": "%X{traceId:-}",
  "spanId": "%X{spanId:-}",
  "stack_trace": "..."
}
```

---

#### 비동기 흐름 추적

- **같은 이벤트는 `eventId`로 묶습니다.** 컨슈머 처리, 재시도, DLQ 저장, 재처리 스케줄러 로그에 모두 같은 `eventId`가 남습니다.
- **`requestId`는 처리마다 새로 만듭니다.** Kafka 메시지를 처리할 때마다 새 `requestId`를 붙이므로, HTTP 요청의 `requestId`가 컨슈머까지 이어지지는 않습니다. Outbox 발행기가 별도 스레드에서 발행하기 때문입니다.
- **MDC 정리:** 스레드를 재사용하므로 처리가 끝나면 `finally`에서 MDC를 비웁니다.

#### 로그 기반 분석 예시

알림이 오지 않았다는 문의가 오면 Loki에서 해당 `eventId`로 검색해 컨슈머 처리 로그 → 재시도 로그 → DLQ 저장 로그 → 재처리 스케줄러 로그 순서로 어디서 멈췄는지 확인합니다.
