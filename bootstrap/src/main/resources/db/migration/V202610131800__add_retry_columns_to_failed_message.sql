-- failed_message에 엔티티가 쓰는 event_id, next_retry_time 컬럼과 스케줄러 조회 인덱스를 추가한다.
-- V1 baseline에 빠져 있어서, 새로 만든 DB에서는 DLQ 저장(event_id)과 재처리 조회(next_retry_time)가 실패한다.
-- 운영 테이블은 Flyway 이전 ddl-auto로 이미 컬럼이 있을 수 있으므로 없을 때만 추가한다. (MySQL은 ADD COLUMN IF NOT EXISTS 미지원)

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'failed_message' AND column_name = 'event_id');
SET @sql := IF(@col_exists = 0,
               'ALTER TABLE failed_message ADD COLUMN event_id VARCHAR(255) NULL AFTER id',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'failed_message' AND column_name = 'next_retry_time');
SET @sql := IF(@col_exists = 0,
               'ALTER TABLE failed_message ADD COLUMN next_retry_time DATETIME(6) NULL AFTER exception_message',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 스케줄러 조회(resolved = false AND dead = false AND next_retry_time <= now) 인덱스
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'failed_message' AND index_name = 'idx_failed_message_retry');
SET @sql := IF(@idx_exists = 0,
               'CREATE INDEX idx_failed_message_retry ON failed_message (resolved, dead, next_retry_time)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- isAlreadyRecorded(eventId) 조회 인덱스
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'failed_message' AND index_name = 'idx_failed_message_event_id');
SET @sql := IF(@idx_exists = 0,
               'CREATE INDEX idx_failed_message_event_id ON failed_message (event_id)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
