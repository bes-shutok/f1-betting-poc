-- V2__seed_test_data.sql
-- Basic seed data for integration tests

INSERT INTO users (username, balance_eur)
VALUES
    ('alice', 100),
    ('bob', 100);
