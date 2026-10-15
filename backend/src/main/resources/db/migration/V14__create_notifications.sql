-- 인앱 알림. 실시간 전송은 없고 클라이언트가 주기적으로 조회한다. read_at이 null이면 읽지 않은 알림이다.
CREATE TABLE notifications (
    id           BIGSERIAL    PRIMARY KEY,
    recipient_id BIGINT       NOT NULL REFERENCES members (id),
    type         VARCHAR(50)  NOT NULL,
    title        VARCHAR(100) NOT NULL,
    message      VARCHAR(500) NOT NULL,
    related_type VARCHAR(50),
    related_id   BIGINT,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_id, created_at);
CREATE INDEX idx_notifications_recipient_read ON notifications (recipient_id, read_at);
