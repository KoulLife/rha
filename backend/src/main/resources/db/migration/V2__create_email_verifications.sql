CREATE TABLE email_verifications (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                   VARCHAR(255) NOT NULL,
    code_hash               VARCHAR(255) NOT NULL,
    code_expires_at         TIMESTAMPTZ  NOT NULL,
    verified_at             TIMESTAMPTZ,
    verification_token_hash VARCHAR(255),
    token_expires_at        TIMESTAMPTZ,
    used_at                 TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_email_verifications_token_hash UNIQUE (verification_token_hash)
);

CREATE INDEX idx_email_verifications_email ON email_verifications (email);
