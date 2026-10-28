-- Ground-floor grid: 4 racks (A-D) x 3 rows x 5 columns = 60 bins, 4 packages each.
CREATE TABLE bin (
    id            BIGINT      PRIMARY KEY,
    code          VARCHAR(16) NOT NULL,
    rack          VARCHAR(4)  NOT NULL,
    row_no        INT         NOT NULL,
    col_no        INT         NOT NULL,
    capacity      INT         NOT NULL,
    distance_rank INT         NOT NULL,
    CONSTRAINT uk_bin_code UNIQUE (code)
) ENGINE = InnoDB;

INSERT INTO bin (id, code, rack, row_no, col_no, capacity, distance_rank)
WITH RECURSIVE
    rws AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM rws WHERE n < 3),
    cls AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM cls WHERE n < 5),
    racks AS (SELECT 'A' AS rk, 0 AS ri UNION ALL SELECT 'B', 1 UNION ALL SELECT 'C', 2 UNION ALL SELECT 'D', 3)
SELECT racks.ri * 15 + (rws.n - 1) * 5 + cls.n,
       CONCAT(racks.rk, '-', rws.n, '-', LPAD(cls.n, 2, '0')),
       racks.rk, rws.n, cls.n, 4,
       racks.ri * 15 + (rws.n - 1) * 5 + cls.n
FROM racks CROSS JOIN rws CROSS JOIN cls;

CREATE TABLE bin_allocation (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    bin_id        BIGINT       NOT NULL,
    bin_code      VARCHAR(16)  NOT NULL,
    customer_id   BIGINT       NOT NULL,
    capacity      INT          NOT NULL,
    package_count INT          NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    -- = bin_id while ACTIVE, NULL once RELEASED => at most ONE active allocation per bin.
    active_slot   BIGINT       NULL,
    allocated_at  TIMESTAMP(6) NOT NULL,
    released_at   TIMESTAMP(6) NULL,
    CONSTRAINT uk_active_alloc_slot UNIQUE (active_slot),
    CONSTRAINT ck_alloc_count CHECK (package_count >= 0 AND package_count <= capacity),
    CONSTRAINT fk_alloc_bin FOREIGN KEY (bin_id) REFERENCES bin (id),
    INDEX idx_alloc_customer (customer_id, status)
) ENGINE = InnoDB;

CREATE TABLE parcel (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    token           VARCHAR(16)    NOT NULL,
    customer_id     BIGINT         NOT NULL,
    customer_name   VARCHAR(120)   NOT NULL,
    customer_phone  VARCHAR(20)    NOT NULL,
    allocation_id   BIGINT         NOT NULL,
    bin_id          BIGINT         NOT NULL,
    bin_code        VARCHAR(16)    NOT NULL,
    floor           INT            NOT NULL,
    description     VARCHAR(200)   NOT NULL,
    amount          DECIMAL(12, 2) NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    idempotency_key VARCHAR(64)    NOT NULL,
    request_hash    VARCHAR(64)    NOT NULL,
    created_at      TIMESTAMP(6)   NOT NULL,
    delivered_at    TIMESTAMP(6)   NULL,
    -- Source of truth for API idempotency (Redis is only the fast path).
    CONSTRAINT uk_parcel_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_parcel_allocation FOREIGN KEY (allocation_id) REFERENCES bin_allocation (id),
    INDEX idx_parcel_token (token, status),
    INDEX idx_parcel_customer (customer_id, status)
) ENGINE = InnoDB;

-- Transactional outbox: events are committed with the data, then relayed to Kafka.
CREATE TABLE outbox_event (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id   VARCHAR(36)  NOT NULL,
    topic      VARCHAR(255) NOT NULL,
    msg_key    VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload    TEXT         NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    attempts   INT          NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    sent_at    TIMESTAMP(6) NULL,
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id),
    INDEX idx_outbox_status (status, id)
) ENGINE = InnoDB;
