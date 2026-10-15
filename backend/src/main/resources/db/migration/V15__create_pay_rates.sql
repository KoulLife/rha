-- 강사별, 수업 유형별 수업당 단가. 출석 인원과 무관하다. 설정할 때 강사 단위로 통째로 교체한다.
CREATE TABLE pay_rates (
    instructor_id  BIGINT      NOT NULL REFERENCES members (id),
    lesson_type_id BIGINT      NOT NULL REFERENCES lesson_types (id),
    unit_price     BIGINT      NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (instructor_id, lesson_type_id),
    CONSTRAINT ck_pay_rates_price CHECK (unit_price >= 0)
);
