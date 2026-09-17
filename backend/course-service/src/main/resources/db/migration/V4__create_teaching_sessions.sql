CREATE TABLE teaching_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teaching_group_id UUID NOT NULL REFERENCES teaching_groups(id),
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED'
        CHECK (status IN ('CREATED', 'ACTIVE', 'CLOSED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_teaching_sessions_group_date ON teaching_sessions(teaching_group_id, session_date, start_time);
