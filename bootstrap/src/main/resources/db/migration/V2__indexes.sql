-- 인덱스들
CREATE INDEX idx_schedules_user_time ON schedules(member_id, start_time, end_time);
CREATE INDEX idx_sched_group_user_start ON schedules(repeat_group_id, member_id, start_time);
CREATE INDEX idx_sched_user_status ON schedules(member_id, progress_status);

CREATE INDEX idx_notification_user_id ON notification(user_id);
CREATE INDEX idx_notification_scheduledAt_isSent ON notification(scheduled_at, is_sent);
CREATE INDEX idx_notification_isRead ON notification(is_read);

CREATE INDEX idx_resolved_retry ON failed_thumbnail(resolved, retry_count);
