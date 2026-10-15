-- 이용권. 날짜는 센터 현지 날짜(Asia/Seoul)이다. 상태는 날짜에 따라 서버가 전환한다.
CREATE TABLE passes (
    id              BIGSERIAL    PRIMARY KEY,
    pass_product_id BIGINT       NOT NULL REFERENCES pass_products (id),
    member_id       BIGINT       NOT NULL REFERENCES members (id),
    instructor_id   BIGINT       NOT NULL REFERENCES members (id),
    price           BIGINT       NOT NULL,
    total_count     INT          NOT NULL,
    remaining_count INT          NOT NULL,
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_passes_status CHECK (status IN ('UPCOMING', 'ACTIVE', 'PAUSED', 'EXPIRED')),
    CONSTRAINT ck_passes_price CHECK (price >= 0),
    CONSTRAINT ck_passes_counts CHECK (total_count >= 1 AND remaining_count >= 0 AND remaining_count <= total_count),
    CONSTRAINT ck_passes_period CHECK (end_date >= start_date)
);

CREATE INDEX idx_passes_member_end ON passes (member_id, end_date);
CREATE INDEX idx_passes_instructor ON passes (instructor_id);
CREATE INDEX idx_passes_product ON passes (pass_product_id);
CREATE INDEX idx_passes_status ON passes (status);
CREATE INDEX idx_passes_created_at ON passes (created_at);

-- 일시정지. 등록하면 정지 일수만큼 passes.end_date를 즉시 연장하고, 조기 해제나 취소하면 사용하지 않은 일수만큼 줄인다.
CREATE TABLE pass_pauses (
    id            BIGSERIAL    PRIMARY KEY,
    pass_id       BIGINT       NOT NULL REFERENCES passes (id),
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    reason        VARCHAR(200),
    terminated_at TIMESTAMPTZ,
    created_by    BIGINT       NOT NULL REFERENCES members (id),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_pass_pauses_status CHECK (status IN ('SCHEDULED', 'ACTIVE', 'ENDED', 'CANCELED'))
);

CREATE INDEX idx_pass_pauses_pass_start ON pass_pauses (pass_id, start_date);
