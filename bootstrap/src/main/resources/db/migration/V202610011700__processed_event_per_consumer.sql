-- 중복 처리 기록을 (consumer, event_id) 단위로 바꾼다.
-- 같은 chat-history 이벤트를 chat-history-save와 pattern-analysis 두 그룹이 각자 처리해야 하는데,
-- event_id만 유니크라서 먼저 처리한 그룹이 나머지 그룹을 "이미 처리됨"으로 건너뛰게 만들었다.
-- 환경마다 인덱스 이름이 다를 수 있어(로컬은 ddl-auto가 만든 이름) 있을 때만 지운다. (MySQL은 DROP INDEX IF EXISTS 미지원)

-- 1) consumer 컬럼 추가 (기존 기록은 'legacy')
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'processed_event' AND column_name = 'consumer');
SET @sql := IF(@col_exists = 0,
               'ALTER TABLE processed_event ADD COLUMN consumer VARCHAR(100) NOT NULL DEFAULT ''legacy'' AFTER id',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) event_id 단독 유니크 제거 (V4에서 만든 이름)
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'processed_event' AND index_name = 'uk_processed_event_event_id');
SET @sql := IF(@idx_exists > 0, 'ALTER TABLE processed_event DROP INDEX uk_processed_event_event_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 엔티티가 유니크로 선언했던 idx_event_id도 유니크라면 제거 (V4는 일반 인덱스로 만들었으므로 운영에서는 남는다)
SET @idx_unique := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'processed_event' AND index_name = 'idx_event_id' AND non_unique = 0);
SET @sql := IF(@idx_unique > 0, 'ALTER TABLE processed_event DROP INDEX idx_event_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) (consumer, event_id) 유니크 추가
SET @uk_exists := (SELECT COUNT(*) FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'processed_event' AND index_name = 'uk_processed_event_consumer_event');
SET @sql := IF(@uk_exists = 0,
               'ALTER TABLE processed_event ADD CONSTRAINT uk_processed_event_consumer_event UNIQUE (consumer, event_id)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
