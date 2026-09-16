ALTER TABLE students ADD COLUMN oidc_issuer VARCHAR(255);
ALTER TABLE students ADD COLUMN oidc_subject VARCHAR(255);
ALTER TABLE students ADD CONSTRAINT uq_student_identity UNIQUE (oidc_issuer, oidc_subject);
