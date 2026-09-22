ALTER TABLE local_users ADD COLUMN display_name VARCHAR(200);
UPDATE local_users SET display_name = username;
ALTER TABLE local_users ALTER COLUMN display_name SET NOT NULL;
