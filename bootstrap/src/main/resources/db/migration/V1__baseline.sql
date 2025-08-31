-- ===================== MEMBER =====================
CREATE TABLE IF NOT EXISTS member (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id VARCHAR(255) NOT NULL,
 password VARCHAR(255),
 user_phone VARCHAR(50),
 user_email VARCHAR(255),
 user_name VARCHAR(255),
 roles VARCHAR(50),
 is_deleted_user BOOLEAN DEFAULT FALSE,
 created_by VARCHAR(255),
 updated_by VARCHAR(255),
 created_time TIMESTAMP,
 updated_time TIMESTAMP
);

-- ===================== CATEGORY =====================
CREATE TABLE IF NOT EXISTS category (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(255),
 parent_id BIGINT,
 depth BIGINT DEFAULT 0,
 is_deleted_category BOOLEAN DEFAULT FALSE,
 created_time TIMESTAMP,
 updated_time TIMESTAMP,
 created_by VARCHAR(255),
 updated_by VARCHAR(255)
);

-- ===================== SCHEDULES =====================
CREATE TABLE IF NOT EXISTS schedules (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 contents VARCHAR(500),
 schedule_month INT,
 schedule_day INT,
 is_deleted_scheduled BOOLEAN DEFAULT FALSE,
 member_id BIGINT,
 category_id BIGINT,
 progress_status VARCHAR(50),
 repeat_type VARCHAR(50),
 repeat_count INT,
 repeat_group_id VARCHAR(255),
 repeat_interval INT,
 start_time TIMESTAMP,
 end_time TIMESTAMP,
 is_all_day BOOLEAN DEFAULT FALSE,
 schedule_type VARCHAR(50),
 created_time TIMESTAMP,
 updated_time TIMESTAMP,
 created_by VARCHAR(255),
 updated_by VARCHAR(255)
);

-- ===================== ATTACH =====================
CREATE TABLE IF NOT EXISTS attach (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 thumbnail_file_path VARCHAR(500),
 origin_file_name VARCHAR(255),
 stored_file_name VARCHAR(255),
 file_size BIGINT,
 file_path VARCHAR(500),
 scheduled_id BIGINT,
 is_deleted_attach BOOLEAN DEFAULT FALSE,
 created_time TIMESTAMP,
 updated_time TIMESTAMP,
 created_by VARCHAR(255),
 updated_by VARCHAR(255)
);

-- ===================== NOTIFICATION =====================
CREATE TABLE IF NOT EXISTS notification (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 message VARCHAR(500),
 is_read BOOLEAN,
 is_sent BOOLEAN,
 schedule_id BIGINT,
 user_id BIGINT,
 notification_type VARCHAR(50),
 scheduled_at TIMESTAMP,
 created_time TIMESTAMP,
 updated_time TIMESTAMP,
 created_by VARCHAR(255),
 updated_by VARCHAR(255)
);

-- ===================== NOTIFICATION_SETTING =====================
CREATE TABLE IF NOT EXISTS notification_setting (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL UNIQUE,
 schedule_created_enabled BOOLEAN DEFAULT TRUE,
 schedule_updated_enabled BOOLEAN DEFAULT TRUE,
 schedule_deleted_enabled BOOLEAN DEFAULT TRUE,
 schedule_remind_enabled BOOLEAN DEFAULT TRUE,
 web_enabled BOOLEAN DEFAULT TRUE,
 email_enabled BOOLEAN DEFAULT FALSE,
 push_enabled BOOLEAN DEFAULT FALSE
);

-- ===================== FAIL_EMAIL_ENTITY =====================
CREATE TABLE IF NOT EXISTS fail_email_entity (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 to_email VARCHAR(255),
 subject VARCHAR(255),
 content TEXT,
 resolved BOOLEAN DEFAULT FALSE,
 created_at TIMESTAMP
);

-- ===================== FAILED_MESSAGE =====================
CREATE TABLE IF NOT EXISTS failed_message (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 topic VARCHAR(255),
 message_type VARCHAR(255),
 payload VARCHAR(2000) UNIQUE,
 retry_count INT,
 resolved BOOLEAN DEFAULT FALSE,
 dead BOOLEAN DEFAULT FALSE,
 exception_message VARCHAR(2000),
 last_tried_at TIMESTAMP,
 resolved_at TIMESTAMP,
 created_at TIMESTAMP
);

-- ===================== FAILED_THUMBNAIL =====================
CREATE TABLE IF NOT EXISTS failed_thumbnail (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 stored_file_name VARCHAR(255),
 reason VARCHAR(500),
 retry_count INT,
 resolved BOOLEAN DEFAULT FALSE,
 last_tried_at TIMESTAMP
);
