-- =====================================================================
-- 일정관리 프로젝트 스키마 (2026-10-02 기준, MySQL 8)
-- Flyway 마이그레이션(V1 ~ V202610021000) + 엔티티 정의를 합친 최종 형태.
-- ERDCloud 등 ERD 도구 임포트용. 실제 DB 생성은 Flyway 마이그레이션을 사용한다.
--
-- 표기
--   * DB 외래키는 3개뿐이다 (일정→회원, 일정→카테고리, 첨부→일정).
--   * 나머지 테이블은 ID만 보유하는 논리적 관계다(ID 기반 약결합).
--     ERD에 선으로 보이게 하려면 맨 아래 "논리적 관계" 블록의 주석을 풀어 임포트한다.
--     (그 블록은 그림용이며 실제 DB에는 없는 제약이다)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 도메인
-- ---------------------------------------------------------------------

CREATE TABLE member (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 번호',
    user_id         VARCHAR(255) NOT NULL COMMENT '로그인 아이디 (소셜 회원은 social_타임스탬프)',
    password        VARCHAR(255)          COMMENT '비밀번호 해시 (소셜 회원은 NULL)',
    user_phone      VARCHAR(50)           COMMENT '전화번호',
    user_email      VARCHAR(255)          COMMENT '이메일',
    user_name       VARCHAR(255)          COMMENT '이름',
    roles           VARCHAR(50)           COMMENT '권한 (ROLE_USER / ROLE_ADMIN)',
    is_deleted_user BOOLEAN      DEFAULT FALSE COMMENT '탈퇴 여부',
    created_by      VARCHAR(255)          COMMENT '생성자',
    updated_by      VARCHAR(255)          COMMENT '수정자',
    created_time    TIMESTAMP             COMMENT '생성 시각',
    updated_time    TIMESTAMP             COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_member_user_id UNIQUE (user_id)
) COMMENT '회원';

CREATE TABLE category (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '카테고리 번호',
    name                VARCHAR(255)          COMMENT '카테고리 이름',
    parent_id           BIGINT                COMMENT '상위 카테고리 번호',
    depth               BIGINT       DEFAULT 0 COMMENT '깊이',
    is_deleted_category BOOLEAN      DEFAULT FALSE COMMENT '삭제 여부',
    created_by          VARCHAR(255)          COMMENT '생성자 (수정·삭제 권한 판단)',
    updated_by          VARCHAR(255)          COMMENT '수정자',
    created_time        TIMESTAMP             COMMENT '생성 시각',
    updated_time        TIMESTAMP             COMMENT '수정 시각',
    PRIMARY KEY (id)
) COMMENT '카테고리 (회원 공용)';

CREATE TABLE schedules (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '일정 번호',
    member_id            BIGINT                COMMENT '회원 번호',
    category_id          BIGINT                COMMENT '카테고리 번호',
    contents             VARCHAR(500)          COMMENT '내용',
    schedule_month       INT                   COMMENT '월',
    schedule_day         INT                   COMMENT '일',
    start_time           TIMESTAMP             COMMENT '시작 시각 (TIMESTAMP: 2038-01-19까지만 저장 가능)',
    end_time             TIMESTAMP             COMMENT '종료 시각',
    is_all_day           BOOLEAN      DEFAULT FALSE COMMENT '종일 여부',
    schedule_type        VARCHAR(50)           COMMENT '일정 유형 (SINGLE_DAY 등)',
    progress_status      VARCHAR(50)           COMMENT '진행 상태',
    repeat_type          VARCHAR(50)           COMMENT '반복 유형 (NONE / DAILY / ...)',
    repeat_count         INT                   COMMENT '반복 횟수',
    repeat_interval      INT                   COMMENT '반복 간격',
    repeat_group_id      VARCHAR(255)          COMMENT '반복 일정 묶음 ID',
    is_deleted_scheduled BOOLEAN      DEFAULT FALSE COMMENT '삭제 여부',
    created_by           VARCHAR(255)          COMMENT '생성자',
    updated_by           VARCHAR(255)          COMMENT '수정자',
    created_time         TIMESTAMP             COMMENT '생성 시각',
    updated_time         TIMESTAMP             COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_member_starttime UNIQUE (member_id, start_time),
    INDEX idx_schedules_user_time (member_id, start_time, end_time),
    INDEX idx_sched_group_user_start (repeat_group_id, member_id, start_time),
    INDEX idx_sched_user_status (member_id, progress_status),
    CONSTRAINT fk_schedules_member   FOREIGN KEY (member_id)   REFERENCES member (id),
    CONSTRAINT fk_schedules_category FOREIGN KEY (category_id) REFERENCES category (id)
) COMMENT '일정';

CREATE TABLE attach (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '첨부파일 번호',
    scheduled_id        BIGINT                COMMENT '일정 번호',
    origin_file_name    VARCHAR(255)          COMMENT '원본 파일명',
    stored_file_name    VARCHAR(255)          COMMENT '저장 파일명 (S3 키)',
    file_path           VARCHAR(500)          COMMENT '파일 경로',
    thumbnail_file_path VARCHAR(500)          COMMENT '썸네일 경로',
    file_size           BIGINT                COMMENT '파일 크기',
    is_deleted_attach   BOOLEAN      DEFAULT FALSE COMMENT '삭제 여부',
    created_by          VARCHAR(255)          COMMENT '생성자 (삭제 권한 판단)',
    updated_by          VARCHAR(255)          COMMENT '수정자',
    created_time        TIMESTAMP             COMMENT '생성 시각',
    updated_time        TIMESTAMP             COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_attach_schedule FOREIGN KEY (scheduled_id) REFERENCES schedules (id)
) COMMENT '첨부파일';

-- ---------------------------------------------------------------------
-- 알림 · 챗봇 (회원·일정 ID만 보유)
-- ---------------------------------------------------------------------

CREATE TABLE notification (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '알림 번호',
    user_id           BIGINT                COMMENT '회원 번호',
    schedule_id       BIGINT                COMMENT '일정 번호',
    notification_type VARCHAR(50)           COMMENT '알림 유형 (SCHEDULE_CREATED / SCHEDULE_REMINDER ...)',
    message           VARCHAR(500)          COMMENT '메시지',
    scheduled_at      TIMESTAMP             COMMENT '리마인드 발송 예정 시각',
    is_read           BOOLEAN               COMMENT '읽음 여부',
    is_sent           BOOLEAN               COMMENT '발송 여부',
    is_reminder_sent  BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '리마인드 발송 선점 플래그 (UPDATE ... WHERE false)',
    created_by        VARCHAR(255)          COMMENT '생성자',
    updated_by        VARCHAR(255)          COMMENT '수정자',
    created_time      TIMESTAMP             COMMENT '생성 시각',
    updated_time      TIMESTAMP             COMMENT '수정 시각',
    PRIMARY KEY (id),
    INDEX idx_notification_user_id (user_id),
    INDEX idx_notification_scheduledAt_isSent (scheduled_at, is_sent),
    INDEX idx_notification_isRead (is_read),
    INDEX idx_notification_schedule_id_type (schedule_id, notification_type)
) COMMENT '알림';

CREATE TABLE notification_setting (
    id                       BIGINT  NOT NULL AUTO_INCREMENT COMMENT '설정 번호',
    user_id                  BIGINT  NOT NULL COMMENT '회원 번호',
    schedule_created_enabled BOOLEAN DEFAULT TRUE  COMMENT '일정 생성 알림',
    schedule_updated_enabled BOOLEAN DEFAULT TRUE  COMMENT '일정 수정 알림',
    schedule_deleted_enabled BOOLEAN DEFAULT TRUE  COMMENT '일정 삭제 알림',
    schedule_remind_enabled  BOOLEAN DEFAULT TRUE  COMMENT '리마인드 알림',
    web_enabled              BOOLEAN DEFAULT TRUE  COMMENT '웹 알림 채널',
    email_enabled            BOOLEAN DEFAULT FALSE COMMENT '이메일 채널',
    push_enabled             BOOLEAN DEFAULT FALSE COMMENT '웹 푸시 채널',
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_setting_user UNIQUE (user_id)
) COMMENT '알림 설정 (회원당 1행)';

CREATE TABLE push_subscription (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '구독 번호',
    member_id       BIGINT                COMMENT '회원 번호',
    endpoint        VARCHAR(500) NOT NULL COMMENT '브라우저 푸시 엔드포인트 (FCM 등)',
    p256dh          VARCHAR(255) NOT NULL COMMENT '암호화 공개키',
    auth            VARCHAR(255) NOT NULL COMMENT '인증 비밀값',
    user_agent      VARCHAR(255)          COMMENT '브라우저 정보',
    active          BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '활성 여부',
    expiration_time DATETIME(6)           COMMENT '만료 시각',
    created_at      DATETIME(6)           COMMENT '구독 시각',
    revoked_at      DATETIME(6)           COMMENT '해제 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_push_subscription_endpoint UNIQUE (endpoint),
    CONSTRAINT uk_push_subscription_member_endpoint UNIQUE (member_id, endpoint)
) COMMENT '웹 푸시 구독 (기기별)';

CREATE TABLE chat_history (
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '이력 번호',
    member_id          BIGINT      NOT NULL COMMENT '회원 번호',
    user_message       TEXT        NOT NULL COMMENT '사용자 질문',
    assistant_response TEXT        NOT NULL COMMENT '챗봇 답변',
    created_at         DATETIME(6) NOT NULL COMMENT '대화 시각',
    PRIMARY KEY (id),
    INDEX idx_chat_history_member_created (member_id, created_at)
) COMMENT '챗봇 대화 이력 (최근 맥락은 Redis chat_history:{회원번호})';

-- ---------------------------------------------------------------------
-- 이벤트 처리 인프라 (도메인과 독립)
-- ---------------------------------------------------------------------

CREATE TABLE outbox_event_entity (
    id             VARCHAR(255) NOT NULL COMMENT '이벤트 ID (UUID)',
    aggregate_type VARCHAR(255) NOT NULL COMMENT '집합 유형 (SCHEDULE / MEMBER / CHAT) → 토픽 결정',
    aggregate_id   VARCHAR(255) NOT NULL COMMENT '집합 ID (Kafka 메시지 키)',
    event_type     VARCHAR(255) NOT NULL COMMENT '이벤트 유형',
    payload        MEDIUMTEXT   NOT NULL COMMENT '직렬화된 이벤트 JSON',
    sent           BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '발행 여부',
    retry_count    INT          NOT NULL DEFAULT 0 COMMENT '발행 시도 횟수 (5 초과 시 DLQ)',
    created_at     DATETIME(6)  NOT NULL COMMENT '생성 시각',
    sent_at        DATETIME(6)           COMMENT '발행 시각',
    claim_id       VARCHAR(36)           COMMENT '배치 선점 ID',
    claimed_at     DATETIME(6)           COMMENT '선점 시각 (30초 지나면 다시 선점)',
    PRIMARY KEY (id),
    INDEX idx_outbox_sent_created (sent, created_at),
    INDEX idx_outbox_claim_id (claim_id)
) COMMENT 'Outbox (도메인과 같은 트랜잭션에 저장, 발행기가 Kafka로 전달)';

CREATE TABLE processed_event (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '번호',
    consumer     VARCHAR(100) NOT NULL DEFAULT 'legacy' COMMENT '처리한 컨슈머 그룹',
    event_id     VARCHAR(255) NOT NULL COMMENT '이벤트 ID',
    processed_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '처리 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_processed_event_consumer_event UNIQUE (consumer, event_id),
    INDEX idx_event_id (event_id)
) COMMENT '컨슈머 멱등성 기록 (컨슈머별 1회 처리)';

CREATE TABLE failed_message (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '번호',
    event_id          VARCHAR(255)           COMMENT '이벤트 ID',
    topic             VARCHAR(255)           COMMENT '원래 토픽',
    message_type      VARCHAR(255)           COMMENT '메시지 유형 (NOTIFICATION / WEB_PUSH / MEMBER_SIGNUP)',
    payload           VARCHAR(2000)          COMMENT '메시지 본문',
    retry_count       INT                    COMMENT '재시도 횟수',
    resolved          BOOLEAN       DEFAULT FALSE COMMENT '처리 완료 여부',
    dead              BOOLEAN       DEFAULT FALSE COMMENT '재시도 포기 여부',
    exception_message VARCHAR(2000)          COMMENT '마지막 실패 사유',
    next_retry_time   DATETIME(6)            COMMENT '다음 재시도 시각',
    last_tried_at     TIMESTAMP              COMMENT '마지막 시도 시각',
    resolved_at       TIMESTAMP              COMMENT '처리 완료 시각',
    created_at        TIMESTAMP              COMMENT '생성 시각',
    PRIMARY KEY (id),
    UNIQUE KEY uk_failed_message_payload (payload(255)),
    INDEX idx_failed_message_retry (resolved, dead, next_retry_time)
) COMMENT 'DLQ로 들어온 메시지와 재처리 상태';

CREATE TABLE failed_thumbnail (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '번호',
    stored_file_name VARCHAR(255)          COMMENT '원본 저장 파일명',
    reason           VARCHAR(500)          COMMENT '실패 사유',
    retry_count      INT                   COMMENT '재시도 횟수',
    resolved         BOOLEAN      DEFAULT FALSE COMMENT '처리 완료 여부',
    last_tried_at    TIMESTAMP             COMMENT '마지막 시도 시각',
    PRIMARY KEY (id),
    INDEX idx_resolved_retry (resolved, retry_count)
) COMMENT '썸네일 생성 실패 기록';

CREATE TABLE fail_email_entity (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '번호',
    to_email   VARCHAR(255)          COMMENT '받는 사람',
    subject    VARCHAR(255)          COMMENT '제목',
    content    TEXT                  COMMENT '본문',
    resolved   BOOLEAN      DEFAULT FALSE COMMENT '재발송 완료 여부',
    created_at TIMESTAMP             COMMENT '생성 시각',
    PRIMARY KEY (id)
) COMMENT '이메일 발송 실패 기록';

-- ---------------------------------------------------------------------
-- 논리적 관계 (선택) — ERD 그림에 선을 그리기 위한 블록. 실제 DB에는 없는 제약이다.
-- 사용하려면 아래 주석을 풀어 함께 임포트하고, ERD 도구에서 이 관계들을 "비식별·점선"으로 표시한다.
-- ---------------------------------------------------------------------
-- ALTER TABLE category             ADD CONSTRAINT lr_category_parent      FOREIGN KEY (parent_id)   REFERENCES category (id);
-- ALTER TABLE notification         ADD CONSTRAINT lr_notification_member  FOREIGN KEY (user_id)     REFERENCES member (id);
-- ALTER TABLE notification         ADD CONSTRAINT lr_notification_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id);
-- ALTER TABLE notification_setting ADD CONSTRAINT lr_setting_member       FOREIGN KEY (user_id)     REFERENCES member (id);
-- ALTER TABLE push_subscription    ADD CONSTRAINT lr_push_member          FOREIGN KEY (member_id)   REFERENCES member (id);
-- ALTER TABLE chat_history         ADD CONSTRAINT lr_chat_member          FOREIGN KEY (member_id)   REFERENCES member (id);
