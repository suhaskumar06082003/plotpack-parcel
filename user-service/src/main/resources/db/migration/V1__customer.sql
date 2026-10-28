CREATE TABLE customer (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_customer_phone UNIQUE (phone)
) ENGINE = InnoDB;
