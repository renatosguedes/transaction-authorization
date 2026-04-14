CREATE TABLE authorization_results (
    id             BIGSERIAL    PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    account_id     VARCHAR(255) NOT NULL,
    status         VARCHAR(10)  NOT NULL,
    reason         VARCHAR(255),
    processed_at   TIMESTAMPTZ  NOT NULL
);
