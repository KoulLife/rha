CREATE TABLE absences (
    id            BIGSERIAL    PRIMARY KEY,
    instructor_id BIGINT       NOT NULL REFERENCES members (id),
    start_at      TIMESTAMPTZ  NOT NULL,
    end_at        TIMESTAMPTZ  NOT NULL,
    reason        VARCHAR(200),
    status        VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    reject_reason VARCHAR(200),
    processed_by  BIGINT       REFERENCES members (id),
    processed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_absences_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CANCELED')),
    CONSTRAINT ck_absences_period CHECK (start_at < end_at)
);

CREATE INDEX idx_absences_instructor_start ON absences (instructor_id, start_at);
CREATE INDEX idx_absences_status ON absences (status);
