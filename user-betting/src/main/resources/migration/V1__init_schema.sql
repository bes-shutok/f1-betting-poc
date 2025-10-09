-- V1__init_schema.sql

-- Optional extension for case-insensitive columns
-- CREATE EXTENSION IF NOT EXISTS citext;

-- USERS
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        TEXT NOT NULL,
    balance_eur     BIGINT NOT NULL DEFAULT 100, -- starting gift: 100 EUR
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HISTORICAL EVENTS
CREATE TABLE historical_events (
    event_id        BIGINT PRIMARY KEY,
    event_name      TEXT,
    country         TEXT,
    year            INT,
    status          TEXT NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_events_status ON historical_events(status);

-- EVENT OUTCOMES
CREATE TABLE event_outcomes (
    event_id            BIGINT PRIMARY KEY REFERENCES historical_events(event_id),
    winning_driver_id   BIGINT NOT NULL,
    settled_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- BETS
CREATE TABLE bets (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    event_id        BIGINT NOT NULL REFERENCES historical_events(event_id),
    driver_id       BIGINT NOT NULL,
    driver_name     TEXT,
    amount_eur      BIGINT NOT NULL,
    odds            INT NOT NULL CHECK (odds IN (2,3,4)),
    status          TEXT NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_bets_event ON bets(event_id);
CREATE INDEX ix_bets_user ON bets(user_id);

-- EVENT PROVIDER PAYLOAD (optional cache of provider response)
CREATE TABLE event_provider_payloads (
    event_id        BIGINT PRIMARY KEY,
    provider_name   TEXT NOT NULL,
    raw_payload     JSONB,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payload_provider ON event_provider_payloads(provider_name);
