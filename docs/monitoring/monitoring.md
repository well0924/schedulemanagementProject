### 모니터링

**설계 목적**

장애를 재현이 아니라 관측으로 대응하기 위해 메트릭·로그·트레이스를 따로 수집하고, Grafana 한 곳에서 함께 보도록 구성했습니다.
수집 도구가 서비스 자원을 쓰지 않도록 **모니터링 서버를 앱 서버와 분리**했습니다.

**구성**

| 종류 | 수집 | 저장·조회 |
|---|---|---|
| 메트릭 | Spring Boot Actuator(`/actuator/prometheus`), Kafka·Redis·Node Exporter | Prometheus → Grafana |
| 로그 | Promtail이 앱의 JSON 로그 파일을 수집 | Loki → Grafana |
| 트레이스 | Micrometer Tracing + OTLP (샘플링 100%) | Tempo → Grafana |

**부하 테스트에서 실제로 본 지표**

| 지표 | 쓰임 |
|---|---|
| HikariCP 활성·대기 커넥션 | 90VU 붕괴 원인 좁히기: 커넥션 대기가 Outbox 적체보다 약 90초 먼저 늘어난 것을 확인 |
| `outbox.pending.count` | 미발행 Outbox 백로그. 490VU에서 약 25,500건 → 배치 선점 후 약 200건 |
| `outbox.publish.duration` / `.count` | 발행기 한 주기 실행 시간과 처리량 |
| `kafka.consumer.*.duration` / `.count` | 컨슈머별 처리 시간과 건수 (알림, 회원가입, 챗봇 이력) |
| `kafka.dlq.retry.duration`, `kafka.actual.retry.count` | DLQ 재처리 스케줄러 실행 시간과 실제 재발행 횟수 |
| JVM 힙·GC | Full GC 반복 확인과 G1GC 튜닝 후 비교 |
| Kafka Consumer Lag | 컨슈머가 발행 속도를 따라가는지 확인 |

지표만으로 확정하기 어려운 DB 병목은 slow query log(`long_query_time=0.05`)로 검증했습니다. 리마인더 DELETE와 충돌 검사 COUNT의 긴 실행 시간을 이 로그로 확인했습니다.

**로그 형식**

로그 필드와 추적 방법은 [로깅](logging.md)에 정리했습니다.

**참고**

리포지토리의 `promethus/prometheus.yml`, `promtail/promtail-config.yml`, `docker-compose.monitoring.yml`은 서버 2대 전환 전의 단일 서버 기준 설정입니다. 지금 모니터링 서버의 설정은 서버에서 직접 관리하고 있어 이 파일들과 다릅니다.
