ALTER TABLE attendances
    ADD COLUMN ip_address VARCHAR(45) NOT NULL DEFAULT 'unknown',
    ADD COLUMN user_agent VARCHAR(512) NOT NULL DEFAULT 'unknown';

ALTER TABLE attendances ALTER COLUMN ip_address DROP DEFAULT;
ALTER TABLE attendances ALTER COLUMN user_agent DROP DEFAULT;
