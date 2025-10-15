# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an F1 betting proof-of-concept with two Spring Boot microservices:
- **event-service** (port 8081): Provides F1 event metadata and historical results from OpenF1 API
- **user-betting** (port 8080): Manages users, balances, placing bets, and settling events

Both services share common DTOs via the `common` module. All identifiers and money amounts are Long, with money measured in whole EUR.

## Development Commands

### Testing
- Linux/macOS: `./gradlew test`
- Windows PowerShell: `.\gradlew test`
- Run tests for specific module: `./gradlew :event-service:test` or `./gradlew :user-betting:test`

### Running Services Locally
- **event-service**: `./gradlew :event-service:bootRun` (http://localhost:8081)
- **user-betting**: `./gradlew :user-betting:bootRun` (http://localhost:8080)

### Docker Development
- Build and start all services: `docker compose up --build`
- Start single service: `docker compose up --build event-service` or `docker compose up --build user-betting`

### API Documentation
- user-betting: http://localhost:8080/swagger-ui/index.html
- event-service: http://localhost:8081/swagger-ui/index.html

## Architecture

### Service Communication
- `user-betting` calls `event-service` via RestTemplate using `event.service.base-url` property
- In Docker, `EVENT_SERVICE_BASE_URL=http://event-service:8081` enables inter-container communication
- The `/api/events/{id}/winner` endpoint in `event-service` is restricted to localhost by default

### Data Model
- **user-betting**: PostgreSQL database with Flyway migrations (tables: users, historical_events, bets, event_outcomes)
- **event-service**: No database, caches data from OpenF1 API using Caffeine with resilience4j rate limiting
- **common module**: Shared DTOs - EventDetails, Driver, EventResult

### Key Business Logic
- **BettingService**: Handles bet placement with user balance validation and event settlement
- **EventService**: Provides paginated event data with filtering (sessionType, country, year)
- Event settlement locks events, fetches winners, updates balances atomically

### Security Configuration
- Swagger/OpenAPI endpoints are public
- `event-service` `/api/events/{id}/winner` endpoint is localhost-only for internal server-to-server calls
- No authentication implemented in this POC

### Testing Approach
- Unit tests use Mockito with LENIENT strictness
- Integration tests test full API endpoints
- Tests use Faker for realistic test data generation
- Testcontainers configuration available for database testing

## Configuration Notes
- Java 21 toolchain configured for containers, JDK 17+ works locally
- Flyway migrations seed two users: alice and bob (both with 100 EUR)
- All API fields use snake_case despite Java camelCase naming
- Event status transitions: OPEN → LOCKED → SETTLED