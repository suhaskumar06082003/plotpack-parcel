CREATE TABLE notification (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id      VARCHAR(36)  NOT NULL,
    kind          VARCHAR(16)  NOT NULL,
    token         VARCHAR(16)  NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    message       VARCHAR(400) NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    -- Consumer idempotency: the same Kafka event can never produce two SMS.
    CONSTRAINT uk_notification_event UNIQUE (event_id),
    INDEX idx_notification_phone (phone, id)
) ENGINE = InnoDB;
