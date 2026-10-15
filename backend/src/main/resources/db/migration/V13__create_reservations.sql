-- 수업 예약. 예약하면 이용권 횟수를 1회 차감하고, 취소하면 복구한다.
CREATE TABLE reservations (
    id                      BIGSERIAL    PRIMARY KEY,
    lesson_id               BIGINT       NOT NULL REFERENCES lessons (id),
    member_id               BIGINT       NOT NULL REFERENCES members (id),
    pass_id                 BIGINT       NOT NULL REFERENCES passes (id),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'RESERVED',
    attendance_confirmed_at TIMESTAMPTZ,
    canceled_by             VARCHAR(20),
    canceled_at             TIMESTAMPTZ,
    cancel_reason           VARCHAR(200),
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_reservations_status CHECK (status IN ('RESERVED', 'ATTENDED', 'NO_SHOW', 'CANCELED')),
    CONSTRAINT ck_reservations_canceled_by CHECK (canceled_by IS NULL OR canceled_by IN ('MEMBER', 'ADMIN', 'LESSON_CANCELED'))
);

-- 같은 회원이 같은 수업을 중복 예약할 수 없다. 취소한 예약은 제외한다.
CREATE UNIQUE INDEX uk_reservations_lesson_member_active ON reservations (lesson_id, member_id)
    WHERE status <> 'CANCELED';

CREATE INDEX idx_reservations_lesson ON reservations (lesson_id);
CREATE INDEX idx_reservations_member_created ON reservations (member_id, created_at);
CREATE INDEX idx_reservations_pass ON reservations (pass_id);
