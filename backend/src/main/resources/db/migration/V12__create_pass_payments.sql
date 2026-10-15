-- 오프라인 결제의 기록. 이용권 결제 합계(환불로 체크된 기록 포함)로 미수금을 계산한다. 날짜는 센터 현지 날짜이다.
CREATE TABLE pass_payments (
    id          BIGSERIAL    PRIMARY KEY,
    pass_id     BIGINT       NOT NULL REFERENCES passes (id),
    method      VARCHAR(20)  NOT NULL,
    amount      BIGINT       NOT NULL,
    paid_at     DATE         NOT NULL,
    memo        VARCHAR(200),
    refunded    BOOLEAN      NOT NULL DEFAULT FALSE,
    refunded_at DATE,
    refund_memo VARCHAR(200),
    recorded_by BIGINT       NOT NULL REFERENCES members (id),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_pass_payments_method CHECK (method IN ('CARD', 'CASH', 'TRANSFER', 'OTHER')),
    CONSTRAINT ck_pass_payments_amount CHECK (amount >= 1)
);

CREATE INDEX idx_pass_payments_pass ON pass_payments (pass_id);
CREATE INDEX idx_pass_payments_paid_at ON pass_payments (paid_at);
CREATE INDEX idx_pass_payments_method ON pass_payments (method);
