CREATE TABLE IF NOT EXISTS processed_event (
   id BIGINT AUTO_INCREMENT PRIMARY KEY,
   event_id VARCHAR(255) NOT NULL,
   processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
   UNIQUE KEY uk_processed_event_event_id (event_id)
);

CREATE INDEX idx_event_id ON processed_event(event_id);