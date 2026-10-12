CREATE TABLE members (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255),
    name             VARCHAR(50)  NOT NULL,
    phone_number     VARCHAR(11)  NOT NULL,
    birth_date       DATE         NOT NULL,
    address          VARCHAR(200),
    role             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    resignation_date DATE,
    withdrawn_at     TIMESTAMPTZ,
    resigned_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_members_email UNIQUE (email),
    CONSTRAINT ck_members_role CHECK (role IN ('ADMIN', 'INSTRUCTOR', 'MEMBER')),
    CONSTRAINT ck_members_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'WITHDRAWN', 'RESIGNED'))
);

CREATE INDEX idx_members_role_status ON members (role, status);
CREATE INDEX idx_members_name ON members (name);
