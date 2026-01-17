### 3️⃣ 로그 수집 및 모니터링

![Image](https://github.com/user-attachments/assets/75019754-6f6f-4c59-886d-c1985ccf9a8b)

**설계 목적**

운영 환경에서 발생하는 장애를 재현이 아니라 관측으로 대응하기 위해 로그와 메트릭을 분리 수집하고, 단일 대시보드에서 상관 분석이 가능하도록 구성했습니다.

**구성 요약**
- Promtail이 Spring Boot / Kafka / Redis 로그를 Loki로 전송
- Prometheus가 JVM, Redis, Kafka Exporter에서 메트릭 수집
- Grafana가 Loki + Prometheus 데이터를 통합 시각화

**관측 지표**
- JVM Heap / GC 시간 / Thread / 요청 응답속도
- Kafka 처리량, Consumer Lag, DLQ 재처리율
- Redis 메모리 사용량, Connection 수, Latency

**MDC 로그 포맷**
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

### 관측 기반 개선 사례

- Kafka Consumer Lag 증가를 Grafana 대시보드로 조기에 감지하여 컨슈머 처리량 병목을 사전에 확인
- JVM Heap 사용량과 GC 시간을 함께 관측하여 Full GC 빈도 증가 원인을 분석하고 G1GC 튜닝으로 안정화
- DLQ 재처리율을 지표로 관리하여 장애 발생 후 복구 성공 여부를 수치로 확인