ALTER TABLE teaching_sessions ADD COLUMN qr_started_at TIMESTAMP WITH TIME ZONE;

UPDATE teaching_sessions SET qr_started_at = date_trunc('second', CURRENT_TIMESTAMP)
WHERE status = 'ACTIVE';
