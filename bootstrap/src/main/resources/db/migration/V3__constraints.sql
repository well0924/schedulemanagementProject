-- V3__constraints.sql
-- schedules 외래키
ALTER TABLE schedules
    ADD CONSTRAINT fk_schedules_member
        FOREIGN KEY (member_id) REFERENCES member(id);

ALTER TABLE schedules
    ADD CONSTRAINT fk_schedules_category
        FOREIGN KEY (category_id) REFERENCES category(id);

-- attach 외래키
ALTER TABLE attach
    ADD CONSTRAINT fk_attach_schedule
        FOREIGN KEY (scheduled_id) REFERENCES schedules(id);
