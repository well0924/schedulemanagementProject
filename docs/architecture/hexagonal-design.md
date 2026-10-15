### 코드 레벨 헥사고날 아키텍처

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



### 설계 원칙 및 의존성 규칙

- Core Domain은 외부 기술(DB, Kafka, Redis, WebClient)에 직접 의존하지 않는다.
- 모든 외부 연동은 Outbound Port를 통해서만 수행되며, 기술 교체 시 Core 비즈니스 로직은 영향을 받지 않는다.
- Controller 및 Kafka Consumer는 비즈니스 로직을 직접 호출하지 않고 Inbound Port를 통해 요청을 전달한다.

## AI 추천 엔진 격리

AI 추천 엔진(OpenAI API)은 외부 의존성이 크고 모델 스펙이 수시로 변경됩니다. 외부 AI API 규격이 바뀌거나 다른 LLM으로 교체되어도 핵심 일정 도메인 로직은 수정하지 않도록 포트와 어댑터로 분리했습니다.

<img width="953" height="641" alt="AI 추천 엔진 헥사고날 구조" src="https://github.com/user-attachments/assets/abb12326-8951-471f-96c9-42677dc6726f" />
