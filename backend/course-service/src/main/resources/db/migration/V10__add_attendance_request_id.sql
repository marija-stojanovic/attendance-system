ALTER TABLE attendances ADD COLUMN request_id UUID;
ALTER TABLE attendances ADD CONSTRAINT uq_attendance_request_id UNIQUE (request_id);
