-- Customer wallets. The balance always equals the sum of the wallet's ledger rows.
CREATE TABLE wallets
(
    id          UUID PRIMARY KEY,
    user_id     UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    balance_uzs NUMERIC(14, 0) NOT NULL DEFAULT 0 CHECK (balance_uzs >= 0),
    created_at  TIMESTAMPTZ    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    version     BIGINT         NOT NULL,
    CONSTRAINT ux_wallets_user UNIQUE (user_id)
);

CREATE TABLE wallet_transactions
(
    id                UUID PRIMARY KEY,
    wallet_id         UUID           NOT NULL REFERENCES wallets (id) ON DELETE CASCADE,
    user_id           UUID           NOT NULL,
    type              VARCHAR(20)    NOT NULL,
    -- positive = money in, negative = money out
    amount_uzs        NUMERIC(14, 0) NOT NULL CHECK (amount_uzs <> 0),
    balance_after_uzs NUMERIC(14, 0) NOT NULL,
    order_id          UUID,
    -- NULL when the system made it
    created_by        UUID,
    comment           VARCHAR(500),
    created_at        TIMESTAMPTZ    NOT NULL
);

CREATE INDEX ix_wallet_transactions_wallet ON wallet_transactions (wallet_id, created_at DESC);

-- Payments for orders and shipping differences, through Payme, Click or the wallet.
CREATE SEQUENCE payment_number_seq START 1000;

CREATE TABLE payment_transactions
(
    id                      UUID PRIMARY KEY,
    -- our numeric id, given to providers (Payme "transaction", Click merchant_prepare_id / merchant_confirm_id)
    number                  BIGINT         NOT NULL,
    purpose                 VARCHAR(30)    NOT NULL,
    order_id                UUID           NOT NULL REFERENCES orders (id),
    order_number            VARCHAR(20)    NOT NULL,
    customer_id             UUID           NOT NULL,
    provider                VARCHAR(20)    NOT NULL,
    -- Payme transaction id / Click click_trans_id
    provider_transaction_id VARCHAR(64),
    amount_uzs              NUMERIC(14, 0) NOT NULL,
    state                   VARCHAR(30)    NOT NULL,
    -- Payme "time": creation time in Payme Business, ms
    provider_time           BIGINT,
    create_time             TIMESTAMPTZ    NOT NULL,
    perform_time            TIMESTAMPTZ,
    cancel_time             TIMESTAMPTZ,
    -- Payme cancel reason code
    reason                  INT,
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    version                 BIGINT         NOT NULL,
    CONSTRAINT ux_payment_transactions_number UNIQUE (number),
    CONSTRAINT ux_payment_transactions_provider_id UNIQUE (provider, provider_transaction_id)
);

CREATE INDEX ix_payment_transactions_order ON payment_transactions (order_id, purpose, state);
CREATE INDEX ix_payment_transactions_provider_time ON payment_transactions (provider, provider_time);

-- Every raw provider call and our answer, for disputes and debugging.
CREATE TABLE payment_provider_logs
(
    id             UUID PRIMARY KEY,
    provider       VARCHAR(20)  NOT NULL,
    operation      VARCHAR(50)  NOT NULL,
    request        TEXT         NOT NULL,
    response       TEXT         NOT NULL,
    transaction_id UUID,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_payment_provider_logs_created ON payment_provider_logs (created_at DESC);
