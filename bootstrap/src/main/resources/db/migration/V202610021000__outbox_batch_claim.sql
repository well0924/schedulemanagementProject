-- Outbox 발행을 "건별 선점 + 건별 상태 갱신"에서 "배치 선점 + 일괄 상태 갱신"으로 바꾸기 위한 컬럼.
-- 490VU 측정에서 건마다 DB 왕복(선점 UPDATE, 발행 성공 UPDATE)이 생겨 발행 한계가 약 50 events/s에 머물렀다.
-- 운영 테이블은 Flyway 이전에 만들어져 인덱스 구성이 환경마다 다를 수 있으므로 있을 때만 건너뛴다.

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'outbox_event_entity' AND column_name = 'claim_id');
SET @sql := IF(@col_exists = 0,
               'ALTER TABLE outbox_event_entity ADD COLUMN claim_id VARCHAR(36) NULL, ADD COLUMN claimed_at DATETIME(6) NULL',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 선점 쿼리(미발행 + 생성순) 인덱스
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'outbox_event_entity' AND index_name = 'idx_outbox_sent_created');
SET @sql := IF(@idx_exists = 0,
               'CREATE INDEX idx_outbox_sent_created ON outbox_event_entity (sent, created_at)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 이번 배치가 선점한 행 조회(claim_id) 인덱스
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'outbox_event_entity' AND index_name = 'idx_outbox_claim_id');
SET @sql := IF(@idx_exists = 0,
               'CREATE INDEX idx_outbox_claim_id ON outbox_event_entity (claim_id)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
