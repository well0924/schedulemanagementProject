-- 엔티티는 있지만 마이그레이션이 없던 테이블.
-- 로컬은 ddl-auto: update가 자동으로 만들어 줘서 드러나지 않았고, 운영(ddl-auto: none)에서는 생성되지 않았다.
-- 운영에 이미 수동으로 만들어진 테이블이 있을 수 있어 IF NOT EXISTS로 만든다.
-- (인덱스·제약도 CREATE TABLE 안에 둬서, 테이블이 이미 있으면 함께 건너뛴다)

-- 챗봇 대화 이력 (ChatHistory)
CREATE TABLE IF NOT EXISTS chat_history (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id          BIGINT      NOT NULL,
    user_message       TEXT        NOT NULL,
    assistant_response TEXT        NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    KEY idx_chat_history_member_created (member_id, created_at)
);

-- 웹 푸시 구독 (PushSubscription)
CREATE TABLE IF NOT EXISTS push_subscription (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id       BIGINT,
    endpoint        VARCHAR(500) NOT NULL,
    p256dh          VARCHAR(255) NOT NULL,
    auth            VARCHAR(255) NOT NULL,
    expiration_time DATETIME(6),
    user_agent      VARCHAR(255),
    active          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6),
    revoked_at      DATETIME(6),
    UNIQUE KEY uk_push_subscription_endpoint (endpoint),
    UNIQUE KEY uk_push_subscription_member_endpoint (member_id, endpoint)
);

-- Outbox 이벤트 (OutboxEventEntity, @Table 미지정 → 기본 이름 outbox_event_entity)
CREATE TABLE IF NOT EXISTS outbox_event_entity (
    id             VARCHAR(255) PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    event_type     VARCHAR(255) NOT NULL,
    payload        MEDIUMTEXT   NOT NULL,
    sent           BOOLEAN      NOT NULL DEFAULT FALSE,
    retry_count    INT          NOT NULL DEFAULT 0,
    created_at     DATETIME(6)  NOT NULL,
    sent_at        DATETIME(6),
    KEY idx_outbox_sent_retry_created (sent, retry_count, created_at)
);
