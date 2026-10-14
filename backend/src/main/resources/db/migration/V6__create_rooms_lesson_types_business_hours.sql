CREATE TABLE rooms (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL,
    capacity   INT          NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

-- 이름은 삭제되지 않은 룸 사이에서만 유일하다. 삭제된 룸의 이름은 다시 쓸 수 있다.
CREATE UNIQUE INDEX uk_rooms_name_active ON rooms (name) WHERE deleted_at IS NULL;

CREATE TABLE lesson_types (
    id         BIGSERIAL    PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    capacity   INT          NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_lesson_types_code UNIQUE (code)
);

-- 요일별 1행, 항상 7행. 설정한 적이 없으면 모든 요일이 휴무다. 현지 시각(Asia/Seoul).
CREATE TABLE business_hours (
    day_of_week VARCHAR(3) PRIMARY KEY,
    closed      BOOLEAN    NOT NULL DEFAULT TRUE,
    open_time   TIME,
    close_time  TIME,
    CONSTRAINT ck_business_hours_day CHECK (day_of_week IN ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')),
    CONSTRAINT ck_business_hours_times CHECK (
        (closed AND open_time IS NULL AND close_time IS NULL)
        OR (NOT closed AND open_time IS NOT NULL AND close_time IS NOT NULL AND open_time < close_time))
);

INSERT INTO business_hours (day_of_week, closed) VALUES
    ('MON', TRUE), ('TUE', TRUE), ('WED', TRUE), ('THU', TRUE), ('FRI', TRUE), ('SAT', TRUE), ('SUN', TRUE);
