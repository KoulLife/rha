CREATE TABLE pass_products (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    price         BIGINT       NOT NULL,
    total_count   INT          NOT NULL,
    validity_days INT          NOT NULL,
    description   VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_pass_products_price CHECK (price >= 0),
    CONSTRAINT ck_pass_products_count CHECK (total_count >= 1),
    CONSTRAINT ck_pass_products_validity CHECK (validity_days >= 1)
);

-- 단일 행(id = 1). 설정한 적이 없으면 취소 마감 24시간, 미수 예약 허용이다.
CREATE TABLE reservation_policies (
    id                       SMALLINT    PRIMARY KEY,
    cancel_deadline_hours    INT         NOT NULL,
    allow_unpaid_reservation BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reservation_policies_single CHECK (id = 1),
    CONSTRAINT ck_reservation_policies_deadline CHECK (cancel_deadline_hours >= 0)
);

INSERT INTO reservation_policies (id, cancel_deadline_hours, allow_unpaid_reservation, updated_at)
VALUES (1, 24, TRUE, now());
