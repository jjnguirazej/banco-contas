-- Esquema inicial
-- As regras de negócio críticas são garantidas também pela base de dados
-- (UNIQUE e CHECK), como última linha de defesa caso a aplicação falhe.

CREATE TABLE customers (
    id          BIGSERIAL    PRIMARY KEY,
    full_name   VARCHAR(150) NOT NULL,
    nuit        VARCHAR(9)   NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_customers_nuit UNIQUE (nuit),
    CONSTRAINT ck_customers_nuit CHECK (nuit ~ '^[0-9]{9}$')
);

CREATE TABLE app_users (
    id             BIGSERIAL    PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    customer_id    BIGINT       REFERENCES customers (id),
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_users_username UNIQUE (username),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'CLIENT')),
    -- Um cliente tem de estar ligado a um titular; um administrador não.
    CONSTRAINT ck_app_users_customer CHECK (
        (role = 'CLIENT' AND customer_id IS NOT NULL) OR
        (role = 'ADMIN'  AND customer_id IS NULL)
    )
);

CREATE SEQUENCE account_number_seq START WITH 1;

CREATE TABLE accounts (
    id              BIGSERIAL     PRIMARY KEY,
    account_number  VARCHAR(20)   NOT NULL,
    customer_id     BIGINT        NOT NULL REFERENCES customers (id),
    account_type    VARCHAR(10)   NOT NULL,
    balance         NUMERIC(19,2) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_accounts_number  UNIQUE (account_number),
    CONSTRAINT ck_accounts_type    CHECK (account_type IN ('ORDEM', 'POUPANCA')),
    CONSTRAINT ck_accounts_balance CHECK (balance >= 0)
);
CREATE INDEX idx_accounts_customer ON accounts (customer_id);

CREATE TABLE transfers (
    id                 BIGSERIAL     PRIMARY KEY,
    reference          UUID          NOT NULL,
    idempotency_key    VARCHAR(100),
    source_account_id  BIGINT        NOT NULL REFERENCES accounts (id),
    target_account_id  BIGINT        NOT NULL REFERENCES accounts (id),
    amount             NUMERIC(19,2) NOT NULL,
    description        VARCHAR(200)  NOT NULL,
    created_by         VARCHAR(50)   NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfers_reference   UNIQUE (reference),
    CONSTRAINT uq_transfers_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_transfers_amount      CHECK (amount > 0),
    CONSTRAINT ck_transfers_accounts    CHECK (source_account_id <> target_account_id)
);

-- Livro-razão: cada linha é imutável e guarda o saldo resultante.
CREATE TABLE movements (
    id             BIGSERIAL     PRIMARY KEY,
    account_id     BIGINT        NOT NULL REFERENCES accounts (id),
    transfer_id    BIGINT        REFERENCES transfers (id),
    movement_type  VARCHAR(30)   NOT NULL,
    amount         NUMERIC(19,2) NOT NULL,
    balance_after  NUMERIC(19,2) NOT NULL,
    description    VARCHAR(200)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_movements_type CHECK (movement_type IN
        ('SALDO_INICIAL', 'TRANSFERENCIA_DEBITO', 'TRANSFERENCIA_CREDITO')),
    CONSTRAINT ck_movements_amount  CHECK (amount > 0),
    CONSTRAINT ck_movements_balance CHECK (balance_after >= 0)
);
CREATE INDEX idx_movements_account_date ON movements (account_id, created_at DESC, id DESC);
