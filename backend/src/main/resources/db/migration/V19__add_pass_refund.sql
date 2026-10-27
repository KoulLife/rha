-- 이용권 환불. 환불하면 상태가 REFUNDED가 되고, 이후 날짜에 따른 자동 상태 전환 대상에서 빠진다.
-- 환불 금액은 결제 합계에서 빼지 않고 따로 남긴다 (매출은 환불일 기준으로 차감한다).
ALTER TABLE passes ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE passes ADD COLUMN refunded_at DATE;
ALTER TABLE passes ADD COLUMN refund_memo VARCHAR(200);

ALTER TABLE passes DROP CONSTRAINT ck_passes_status;
ALTER TABLE passes ADD CONSTRAINT ck_passes_status
    CHECK (status IN ('UPCOMING', 'ACTIVE', 'PAUSED', 'EXPIRED', 'REFUNDED'));
ALTER TABLE passes ADD CONSTRAINT ck_passes_refunded_amount CHECK (refunded_amount >= 0);
