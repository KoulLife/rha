-- 강사 정산. 날짜는 센터 현지 날짜(Asia/Seoul)이다. 지급은 시스템 밖에서 이뤄지고 지급했다는 사실만 기록한다.
CREATE TABLE settlements (
    id                    BIGSERIAL    PRIMARY KEY,
    instructor_id         BIGINT       NOT NULL REFERENCES members (id),
    period_start          DATE         NOT NULL,
    period_end            DATE         NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'CALCULATED',
    total_amount          BIGINT       NOT NULL,
    settled_lesson_count  INT          NOT NULL,
    excluded_lesson_count INT          NOT NULL,
    review_requested_at   TIMESTAMPTZ,
    confirmed_at          TIMESTAMPTZ,
    paid_at               DATE,
    payment_memo          VARCHAR(200),
    payment_recorded_at   TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_settlements_status CHECK (status IN ('CALCULATED', 'REVIEW_REQUESTED', 'CONFIRMED', 'PAID')),
    CONSTRAINT ck_settlements_period CHECK (period_start <= period_end)
);

CREATE INDEX idx_settlements_instructor_period ON settlements (instructor_id, period_start);
CREATE INDEX idx_settlements_status ON settlements (status);

-- 수업별 정산 내역. 한 수업은 하나의 정산에만 포함되며, 이 행이 있으면 출결을 정정할 수 없다.
CREATE TABLE settlement_items (
    id              BIGSERIAL   PRIMARY KEY,
    settlement_id   BIGINT      NOT NULL REFERENCES settlements (id),
    lesson_id       BIGINT      NOT NULL REFERENCES lessons (id),
    attended_count  INT         NOT NULL,
    no_show_count   INT         NOT NULL,
    unit_price      BIGINT,
    amount          BIGINT      NOT NULL,
    settled         BOOLEAN     NOT NULL,
    excluded_reason VARCHAR(30),
    CONSTRAINT uk_settlement_items_lesson UNIQUE (lesson_id),
    CONSTRAINT ck_settlement_items_reason CHECK (excluded_reason IS NULL OR excluded_reason IN ('NO_RESERVATION'))
);

CREATE INDEX idx_settlement_items_settlement ON settlement_items (settlement_id);
