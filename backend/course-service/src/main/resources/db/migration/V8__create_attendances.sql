CREATE TABLE attendances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teaching_session_id UUID NOT NULL REFERENCES teaching_sessions(id),
    student_id UUID NOT NULL REFERENCES students(id),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_attendance_session_student UNIQUE (teaching_session_id, student_id)
);

CREATE INDEX idx_attendances_session_time ON attendances(teaching_session_id, recorded_at);
CREATE INDEX idx_attendances_student ON attendances(student_id);
