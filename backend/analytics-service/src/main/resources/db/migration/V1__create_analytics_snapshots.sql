CREATE TABLE analytics_snapshots (
    offering_id UUID PRIMARY KEY,
    source_data JSONB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
