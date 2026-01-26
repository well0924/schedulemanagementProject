-- notification 테이블에 리마인드 발송 여부 컬럼 추가
ALTER TABLE notification
    ADD COLUMN is_reminder_sent BOOLEAN DEFAULT FALSE NOT NULL;