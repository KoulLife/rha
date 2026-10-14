CREATE TABLE member_permissions (
    member_id       BIGINT       NOT NULL REFERENCES members (id),
    permission_code VARCHAR(100) NOT NULL,
    granted_by      BIGINT       NOT NULL REFERENCES members (id),
    created_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (member_id, permission_code)
);
