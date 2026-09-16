UPDATE students
SET oidc_issuer = NULL, oidc_subject = NULL
WHERE oidc_issuer LIKE '%/realms/attendance-system';

CREATE TABLE local_users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(30) NOT NULL CHECK (role IN ('EMPLOYEE'))
);
