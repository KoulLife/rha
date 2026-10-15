-- 강사의 요일별 수업 가능 시간대. 한 요일에 여러 행이 가능하며 서로 겹치지 않는다. 현지 시각(Asia/Seoul).
-- 설정할 때 강사 단위로 통째로 교체한다.
CREATE TABLE instructor_availabilities (
    id            BIGSERIAL  PRIMARY KEY,
    instructor_id BIGINT     NOT NULL REFERENCES members (id),
    day_of_week   VARCHAR(3) NOT NULL,
    start_time    TIME       NOT NULL,
    end_time      TIME       NOT NULL,
    CONSTRAINT ck_instructor_availabilities_day CHECK (day_of_week IN ('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN')),
    CONSTRAINT ck_instructor_availabilities_time CHECK (start_time < end_time)
);

CREATE INDEX idx_instructor_availabilities_instructor_day ON instructor_availabilities (instructor_id, day_of_week);
