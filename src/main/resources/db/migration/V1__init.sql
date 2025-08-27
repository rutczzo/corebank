CREATE TYPE currency AS ENUM ('KRW');

CREATE TABLE customers (
    id              UUID PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    customer_id     UUID NOT NULL REFERENCES customers(id),
    account_number  VARCHAR(32) NOT NULL UNIQUE,
    currency        currency NOT NULL DEFAULT 'KRW',
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                    CHECK ( status IN ('ACTIVE', 'FROZEN', 'CLOSED') ),
    is_house        BOOLEAN NOT NULL DEFAULT FALSE,
    balance         NUMERIC(19, 0) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_customer ON accounts(customer_id);

CREATE TABLE transfers (
    id              UUID PRIMARY KEY,
    type            VARCHAR(20) NOT NULL
                    CHECK ( type IN ('DEPOSIT', 'WITHDRAW', 'ACCOUNT_TRANSFER') ),
    from_account    UUID REFERENCES accounts(id),
    to_account      UUID REFERENCES accounts(id),
    amount          NUMERIC(19, 0) NOT NULL CHECK ( amount > 0 ),
    currency        currency NOT NULL,
    status          VARCHAR(16) NOT NULL
                    CHECK ( status IN ('PENDING', 'SUCCEEDED', 'FAILED') ),
    idempotency_key VARCHAR(64) UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transfers_created ON transfers(created_at);

CREATE TABLE journal_entries (
    id              BIGSERIAL PRIMARY KEY,
    transfer_id     UUID NOT NULL REFERENCES transfers(id),
    account_id      UUID NOT NULL REFERENCES accounts(id),
    direction       CHAR(1) NOT NULL CHECK (direction IN ('D', 'C')),
    amount          NUMERIC(19, 0) NOT NULL CHECK ( amount > 0 ),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_journal_account_created ON journal_entries(account_id, created_at DESC);
CREATE INDEX idx_journal_transfer ON journal_entries(transfer_id);