## Kafka eos 검증

### 테스트 목적

Outbox + DLQ + Retry 기반 Kafka 이벤트 처리 구조에 eventId 기반 멱등 처리(EOS) 적용 후, 중복 처리 0% 유실 0건
대량 이벤트 환경 안정성을 성능 테스트로 검증한 문서이다.

### 테스트 시나리오

- 정상 호출 테스트:JMeter로 10회 반복 호출
- 부하 테스트: 동시 접속 50만명,100만명
- 일괄 테스트: 3만건, 5만건

### kafka 성능 테스트 요약

50 VU: 평균 200ms 이하, 에러율 0%, 전 구성 요소 안정

100 VU: 평균 1s 내외 지연 발생하나 서비스 안정성 유지

Kafka + Outbox + DLQ 기반 비동기 구조는 부하 상황에서도 유실·중복 없는 처리(EOS 관점)

일시적 스파이크 이후 빠른 회복 특성을 보임

병목은 메시징이 아닌 HTTP + DB 리소스 경합 구간에 집중됨


### 테스트 환경

서버 환경
- AWS Lightsail 2GB VM
- JVM Heap: 512MB ~ 1GB
- HikariCP max pool size: 20

API
- Endpoint: /api/schedule/
- 인증: JWT 헤더 포함


측정 도구
- JMeter: 요청 부하 및 응답 시간 측정
- Grafana (Prometheus 연동): JVM Heap, GC, DB 커넥션 풀 상태 모니터링

일정생성에 필요한 요청값

```
import org.apache.commons.lang3.RandomUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// 날짜 범위 (2025-09-19 ~ 2025-12-30)
def startDate = LocalDate.of(2025, 9, 19)
def endDate   = LocalDate.of(2025, 12, 30)
def daysBetween = ChronoUnit.DAYS.between(startDate, endDate)
def randomOffset = RandomUtils.nextInt(0, (int)daysBetween + 1)
def date = startDate.plusDays(randomOffset)

// userId (1~50 랜덤)
def userId = RandomUtils.nextInt(1, 51)

// userId별 시간대 기본 배정 (0~23시)
def baseHour = userId % 24

// 같은 시간대라도 분 단위 랜덤 오프셋 추가 → 충돌 최소화
def offsetMin = RandomUtils.nextInt(0, 60)

// 시작/종료 시간 (1시간 일정)
def startTime = LocalDateTime.of(date, LocalTime.of(baseHour, offsetMin))
def endTime   = startTime.plusHours(1)

// 포맷
def formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

// JMeter 변수 저장
vars.put("startTime", startTime.format(formatter))
vars.put("endTime", endTime.format(formatter))
vars.put("scheduleDays", String.valueOf(date.getDayOfMonth()))
vars.put("contents", "테스트-" + System.nanoTime())
vars.put("userId", String.valueOf(userId))

```

### 단일 호출(10회)

Outbox 이벤트 발행 처리량

<img width="1280" height="658" alt="Image" src="https://github.com/user-attachments/assets/d6617dd3-a84e-40f0-a3e8-b45e329a4d3b" />

Outbox publish worst-case latency

<img width="1280" height="668" alt="Image" src="https://github.com/user-attachments/assets/4e896c4b-ddd6-4127-b50f-d82fa9c0c3cd" />

Outbox publish 평균 latency

<img width="1280" height="655" alt="Image" src="https://github.com/user-attachments/assets/bc114b9d-d6d2-4e24-a45c-e4dda07e2499" />

측정 결과 요약

- 이벤트 발행 처리량: 약 0.33 msg/s
→ 스케줄러 기반 Outbox Publisher가 일정한 처리량으로 안정적으로 동작함을 확인


- 평균 발행 지연시간: 약 5ms ~ 35ms
→ 정상 구간에서는 수 ms 수준으로 유지되며, 일시적인 부하 구간에서 평균 latency가 상승


- 최대 발행 지연시간(worst-case): 최대 약 280ms
→ 단일 실행에서 발생한 이상치로, 지속적인 성능 저하가 아닌 일시적 피크로 확인됨

kafka consumer 평균 처리

<img width="1280" height="644" alt="Image" src="https://github.com/user-attachments/assets/9f224edb-fe3c-48c0-a318-28e6c011fe06" />

kafka consumer 최대 처리

<img width="1280" height="605" alt="Image" src="https://github.com/user-attachments/assets/115c74b3-5400-4074-846a-2644bdaabfa9" />


측정 결과 요약

- 평균 처리 시간: 약 40ms ~ 55ms
→ 알림 이벤트 1건을 처리하는 데 소요되는 평균 시간으로, 10회 반복 테스트 전반에서 비교적 일정한 수준을 유지함


- 최대 처리 시간(worst-case): 최대 약 200ms
→ 단일 처리 구간에서 발생한 최대 지연으로, 지속적인 성능 저하가 아닌 일시적인 피크 구간으로 확인됨

-----

### 부하 테스트(50VU,100VU)


##### 50 VU 부하 테스트 – 1차 (실패 케이스)

<img width="1280" height="341" alt="Image" src="https://github.com/user-attachments/assets/477a2803-ebad-40b5-8f2c-8e43dbadd405" />

<img width="1280" height="521" alt="Image" src="https://github.com/user-attachments/assets/62a5d3b5-d5a7-4b31-9cae-8c248ea732f5" />

Kafka / Outbox / DLQ
- Kafka Consumer 처리량: 피크 약 30 msg/s, backlog 없이 정상 소화
- Consumer Latency: 평균 ~0.2s, max 지연 없음
- Outbox Publish Rate: 0.3 ~ 0.35 msg/s 유지
- Outbox 평균 지연: 부하 구간에서 2~3초까지 상승
  - DB 처리 지연의 영향으로 판단
- DLQ Retry: 거의 0 → 재처리 흐름 정상
- EOS 관점: 메시지 유실/중복 없음

JVM / GC

- Heap 사용량: ~200MB, Old Gen 증가 없음
- GC Pause: 최대 6ms

결론: JVM 튜닝 상태 양호, 병목 아님

DB (HikariCP)
- Active Connections: maxPoolSize=20 전부 사용
- Idle Connections: 0
- Pending Threads: 일시적으로 증가 후 회복
결론: DB Connection Pool 포화

HTTP API
- Request Rate: 최대 ~120 req/s
- Average Latency: 0.4 ~ 0.6s
- Error Rate: 피크 시 5xx 다수 발생

원인 분석
> - DB Connection Pool 포화로 트랜잭션 지연 발생 
> 
> - 일정 충돌 Validation 실패가 5xx로 분류되어 에러율 과대 집계


##### 1차 개선 사항
><img width="597" height="147" alt="Image" src="https://github.com/user-attachments/assets/d738e739-d5d1-4442-a77b-f6c42391425f" />
>
>HikariCP maximumPoolSize: 20 → 50
> 
>JMeter 테스트 데이터 개선
> 
> userId 범위 확대
> 시간 슬롯 제한
> 
> JSR PreProcessor로 일정 충돌 최소화
> 
> Validation 실패 응답을 4xx(409) 로 수정
>→ 모니터링 지표 왜곡 제거
>

##### 50 VU 부하 테스트 – 2차 (개선 후)

<img width="1280" height="383" alt="Image" src="https://github.com/user-attachments/assets/c1441c82-ad12-423b-8f19-7d951962f40c" />

<img width="1280" height="813" alt="Image" src="https://github.com/user-attachments/assets/f0fbad52-5334-4efc-bbc4-669b6333be45" />

<img width="1280" height="519" alt="Image" src="https://github.com/user-attachments/assets/23fb4753-fd45-4a18-941b-056b0da3aa18" />

<img width="1280" height="349" alt="Image" src="https://github.com/user-attachments/assets/32c7e08f-ce6e-4219-8852-3e88ca9a8b09" />

JMeter 결과
- 샘플 수: 3,801
- 평균 응답 시간: 187ms
- P90 / P95 / P99: 317ms / 376ms / 606ms
- 최대 응답 시간: 1,036ms
- 에러율: 0%
- Throughput: 21.2 req/s

Kafka / Outbox
- Consumer 처리량: ~30 msg/s
- Consumer 평균 지연: 0.02 ~ 0.08s
- Outbox Publish Rate: ~0.3 msg/s
- Outbox 평균 지연: 초기 1.2s → 0.1s 이내 수렴
- DLQ Retry: 0

JVM / GC
- Heap Usage: ~150MB (안정적)
- GC Pause: 10~17ms

DB (HikariCP)
- HikariCP Active: 피크 시 ~40, 이후 20 이하 유지
- Idle Connections: 10개 이상 유지
- Pending Threads: 0

HTTP API
- Request Rate: 250 ~ 300 req/s
- 평균 응답 시간: 120 ~ 180ms
- 5xx 에러율: 0%

**결론 (50 VU)**

- 평균 응답 160~180ms, 에러율 0%
- Kafka, DB, GC 모두 안정적
- Outbox Publisher는 초기 warm-up 이후 안정화
- DB Connection Pool 병목 해소 확인

##### 100 VU 부하 테스트

<img width="1280" height="491" alt="Image" src="https://github.com/user-attachments/assets/edbf8410-2132-4ed0-9e1d-bf728be9f9dc" />

<img width="1280" height="812" alt="Image" src="https://github.com/user-attachments/assets/39a0fe42-883b-4bdd-98b1-7a8bcadd5b96" />

<img width="1280" height="346" alt="Image" src="https://github.com/user-attachments/assets/aa2022b9-bc20-4db8-8ca7-85c5416fec48" />

<img width="1280" height="517" alt="Image" src="https://github.com/user-attachments/assets/e1278cb2-8d3e-4b7b-b144-c379c63690da" />

JMeter 결과
- 평균 응답 시간: ~1,155ms
- Median: 1,069ms
- P95: 2,187ms
- 최대 응답 시간: 4,454ms
- Throughput: ~14.6 req/s
- 에러율: 0%

JVM / DB (HikariCP)
- Heap: ~150MB, 안정
- GC Pause: < 10ms
- Active/Idle이 pool size 50 근접
- Pending Threads: 0

결론: DB 풀은 포화에 근접했으나 대기열 병목은 발생하지 않음

Kafka / Outbox
- Consumer 처리량: 30 ~ 40 msg/s까지 상승 후 정상 회복
- Outbox 평균 지연: 초기 8~9초 스파이크 이후 1초 미만으로 빠르게 수렴

결론: Outbox → Kafka publish 구간은 일시적 스파이크만 발생

HTTP

- 평균 응답 시간: 0.8 ~ 1.0s
- 에러율: 0%

**결론(100VU)**
- 동시 사용자 100명 환경에서도 서비스 지속 가능
- 병목은 HTTP 처리 및 DB 부하 증가에 따른 자연스러운 지연
- Kafka Consumer 및 메시징 파이프라인은 병목 없이 동작 
- 장애 전파, 메시지 유실, 중복 처리 없음

-----

### 일괄 테스트 (3만건, 5만건)

#### 30,000건 일괄 요청 테스트

<img width="1280" height="359" alt="Image" src="https://github.com/user-attachments/assets/21ffcbef-dca1-47fc-9e97-d177ae87f618" />

<img width="1280" height="698" alt="Image" src="https://github.com/user-attachments/assets/aec057c7-2718-443b-9958-b77e58c634ed" />

<img width="1280" height="343" alt="Image" src="https://github.com/user-attachments/assets/38483c64-eb46-41ad-9c68-51044b1a87a1" />

<img width="1280" height="509" alt="Image" src="https://github.com/user-attachments/assets/7f864e86-0258-4e6c-81a6-44cbd4598a51" />

JMeter 결과

- 평균 응답 시간: 39ms
- Median: 36ms
- P95: 59ms
- 최대 응답 시간: ~1,037ms
- Throughput: ~24.4 req/s
- 에러율: 0%(일정 충돌에 따른 409 응답은 정상 처리로 분류)

JVM / DB (HikariCP)

- Heap 사용량: ~150MB, 안정적
- GC Pause: 10ms
- Active / Idle 커넥션 정상 범위 유지
- Pending Threads: 0

Kafka / Outbox
- Kafka Consumer 처리량: 초기 20~25 msg/s, 이후 안정화
- Outbox Publish Rate: ~0.3 msg/s (스케줄러 기반)
- Outbox 평균 지연 : 초기 스파이크 이후 0.05 ~ 0.1s 수준으로 수렴

HTTP
- 평균 응답 시간: ~38~39ms
- 5xx 에러율: 0%

**해석 (3만건)**
- 전체 요청 중 13,884건은 정상 저장, 나머지는 비즈니스 규칙(일정 충돌)에 따라 409로 정상 거절
- DB 저장 결과 기준으로 중복 처리 및 유실 없음
- eventId 기반 멱등 처리와 Outbox 흐름을 통해 EOS 보장이 정상적으로 검증됨
- JVM, DB, Kafka 모두 병목 없이 안정적으로 동작

----

#### 50,000건 일괄 요청 테스트 

<img width="1280" height="402" alt="Image" src="https://github.com/user-attachments/assets/36b8e1f2-d472-46c0-b389-d3d6f0784f1b" />

<img width="1280" height="699" alt="Image" src="https://github.com/user-attachments/assets/f5a12491-cbe2-482c-98df-30a7bd76a55d" />

<img width="1280" height="523" alt="Image" src="https://github.com/user-attachments/assets/0efd6e36-d737-4fb4-a2ae-3dc758c36d94" />

<img width="1280" height="340" alt="Image" src="https://github.com/user-attachments/assets/275f8f1b-7852-48d5-b9d7-13a00bff26c3" />

JMeter 결과
- 평균 응답 시간: 39ms
- Median: 38ms
- P95: 59ms
- 최대 응답 시간: ~2,044ms
- Throughput: ~23.9 req/s
- 에러율: 0% (409 응답은 정상 처리)

JVM / DB (HikariCP)
- Heap 사용량: 100~150MB, 안정
- GC: 병목 없음
- Active / Idle 커넥션 정상
- Pending Threads: 0

Kafka / Outbox
- Kafka Consumer 처리량: 초기 20~25 msg/s까지 상승 이후 점진적으로 안정화
- Outbox Publish Rate: ~0.3 msg/s 지속 유지
- Outbox 평균 지연: 초기 스파이크 발생 이후 0.1s 미만으로 안정화
- DLQ Retry: 일부 재시도 발생 대부분 빠르게 정상 처리로 회복

HTTP
- 평균 응답 시간: 20~50ms
- 5xx 에러율: 0%

**해석 (5만건)**
- 총 요청 중 약 17,000건 정상 저장
- DB 저장 기준: 중복 처리 0건 ,메시지 유실 0건
- 대량 요청 상황에서도 Kafka Consumer 및 Outbox Publisher는 병목 없이 동작
- 평균 응답 시간과 에러율 모두 안정적으로 유지됨