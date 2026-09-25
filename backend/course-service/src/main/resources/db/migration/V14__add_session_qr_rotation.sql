ALTER TABLE teaching_sessions
    ADD COLUMN qr_rotation_seconds INTEGER NOT NULL DEFAULT 30;

ALTER TABLE teaching_sessions
    ADD CONSTRAINT chk_teaching_sessions_qr_rotation
        CHECK (qr_rotation_seconds BETWEEN 5 AND 300);
