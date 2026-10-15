-- 활동 이력. 추가만 하고 수정·삭제하지 않는다. 업무 변경과 같은 트랜잭션에서 기록한다(로그인 실패만 별도 트랜잭션).
CREATE TABLE activity_logs (
    id                BIGSERIAL    PRIMARY KEY,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    category          VARCHAR(50)  NOT NULL,
    action            VARCHAR(100) NOT NULL,
    actor_id          BIGINT       REFERENCES members (id),
    actor_role        VARCHAR(20),
    subject_member_id BIGINT       REFERENCES members (id),
    target_type       VARCHAR(50),
    target_id         BIGINT,
    summary           VARCHAR(500) NOT NULL,
    changes           JSONB        NOT NULL DEFAULT '[]',
    CONSTRAINT ck_activity_logs_actor_role CHECK (actor_role IS NULL OR actor_role IN ('ADMIN', 'INSTRUCTOR', 'MEMBER'))
);

CREATE INDEX idx_activity_logs_occurred ON activity_logs (occurred_at);
CREATE INDEX idx_activity_logs_actor_occurred ON activity_logs (actor_id, occurred_at);
CREATE INDEX idx_activity_logs_subject_occurred ON activity_logs (subject_member_id, occurred_at);
CREATE INDEX idx_activity_logs_target ON activity_logs (target_type, target_id);
CREATE INDEX idx_activity_logs_category_action ON activity_logs (category, action);
