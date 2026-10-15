CREATE TABLE lessons (
    id             BIGSERIAL    PRIMARY KEY,
    title          VARCHAR(100) NOT NULL,
    lesson_type_id BIGINT       NOT NULL REFERENCES lesson_types (id),
    instructor_id  BIGINT       NOT NULL REFERENCES members (id),
    room_id        BIGINT       NOT NULL REFERENCES rooms (id),
    start_at       TIMESTAMPTZ  NOT NULL,
    end_at         TIMESTAMPTZ  NOT NULL,
    capacity       INT          NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    publish_at     TIMESTAMPTZ,
    canceled_at    TIMESTAMPTZ,
    cancel_reason  VARCHAR(200),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_lessons_status CHECK (status IN ('CREATED', 'SCHEDULED', 'PUBLISHED', 'UNPUBLISHED', 'CANCELED')),
    CONSTRAINT ck_lessons_time CHECK (start_at < end_at)
);

CREATE INDEX idx_lessons_start_at ON lessons (start_at);
CREATE INDEX idx_lessons_instructor_start ON lessons (instructor_id, start_at);
CREATE INDEX idx_lessons_room_start ON lessons (room_id, start_at);
CREATE INDEX idx_lessons_status_publish ON lessons (status, publish_at);
