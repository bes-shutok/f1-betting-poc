-- V1__init_schema.sql

-- Optional extension for case-insensitive columns
-- CREATE EXTENSION IF NOT EXISTS citext;

-- ENUM types
DO $$
BEGIN
    CREATE TYPE bet_status AS ENUM ('PENDING', 'WON', 'LOST', 'CANCELLED');
EXCEPTION WHEN duplicate_object THEN null; END$$;

DO $$
BEGIN
    CREATE TYPE event_status AS ENUM ('OPEN', 'LOCKED', 'SETTLING', 'SETTLED');
EXCEPTION WHEN duplicate_object THEN null; END$$;

-- USERS
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        TEXT NOT NULL,
    balance_cents   BIGINT NOT NULL DEFAULT 10000, -- starting gift: 100 EUR
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HISTORICAL EVENTS
CREATE TABLE historical_events (
    event_id        TEXT PRIMARY KEY,
    event_name      TEXT,
    country         TEXT,
    year            INT,
    status          event_status NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_events_status ON historical_events(status);

-- EVENT OUTCOMES
CREATE TABLE event_outcomes (
    event_id            TEXT PRIMARY KEY REFERENCES historical_events(event_id),
    winning_driver_id   TEXT NOT NULL,
    settled_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- BETS
CREATE TABLE bets (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    event_id        TEXT NOT NULL REFERENCES historical_events(event_id),
    driver_id       TEXT NOT NULL,
    driver_name     TEXT,
    amount_cents    BIGINT NOT NULL,
    odds            INT NOT NULL CHECK (odds IN (2,3,4)),
    status          bet_status NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_bets_event ON bets(event_id);
CREATE INDEX ix_bets_user ON bets(user_id);

-- EVENT PROVIDER PAYLOAD (optional cache of provider response)
CREATE TABLE event_provider_payloads (
    event_id        TEXT PRIMARY KEY,
    provider_name   TEXT NOT NULL,
    raw_payload     JSONB,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payload_provider ON event_provider_payloads(provider_name);
