# F1 Betting POC

This repository contains a simple two-service proof of concept:
- event-service: reads F1 event metadata and historical results from OpenF1 and exposes read-only APIs.
- user-betting: manages users, balances, placing bets, and settling events against the event metadata.

Both services share common DTOs via the common module. All identifiers and money amounts are Long. Money is measured in whole EUR (no decimals).

# Getting Started
- Ensure Docker Desktop (or your Docker engine) is running.
- Java 21 is used in containers; for local runs you can use any JDK 17+ supported by Spring Boot 3.5.

## Run tests
- Linux/macOS: ./gradlew test
- Windows PowerShell: .\gradlew test

# Running locally
## # Open API / swagger
- user-betting: http://localhost:8080/swagger-ui/index.html
- event-service: http://localhost:8081/swagger-ui/index.html
- Using Gradle: ./gradlew :event-service:bootRun
- Swagger UI: http://localhost:8081/swagger-ui/index.html
- Example: http://localhost:8081/api/events?country=Belgium

## user-betting
- Using Gradle: ./gradlew :user-betting:bootRun
- Swagger UI: http://localhost:8080/swagger-ui/index.html

The user-betting service uses PostgreSQL via Flyway migrations. When running with Gradle it will use your configured datasource; when using Docker Compose it will connect to the db service automatically.

# Running with Docker Compose
From the repo root:
- Build and start all services: docker compose up --build
- Or start a single service: docker compose up --build event-service (or user-betting)

After startup:
- event-service: http://localhost:8081/swagger-ui/index.html
- user-betting: http://localhost:8080/swagger-ui/index.html
Note: all fields are expected in `snake_case` in contrast to what swagger suggest

Notes
- user-betting is configured (in docker-compose.yml) with EVENT_SERVICE_BASE_URL=http://event-service:8081 so it calls event-service over the Docker network (not localhost).
- Flyway runs automatically in user-betting with migrations under classpath:/migration; two users are seeded: alice and bob (both with 100 EUR).

# Project structure
## event-service module
Responsible for: providing event metadata and historical results from OpenF1.
- Port: 8081
- Caching: Caffeine
- Rate limiting: resilience4j
- Security: Swagger/OpenAPI are public; GET /api/events and GET /api/events/{id} are public. GET /api/events/{id}/winner is restricted to localhost only by default.

## user-betting module
Responsible for: user accounts, balances, placing bets, and settling events.
- Port: 8080
- Database: PostgreSQL (see docker-compose) with Flyway migrations
- Tables: users, historical_events, bets, event_outcomes (plus an optional payload cache)
- Money/IDs: all Long. Amounts are whole EUR.
- External dependency: calls event-service using the property event.service.base-url (overridden by EVENT_SERVICE_BASE_URL env var).

## common module
Shared domain DTOs used between services: EventDetails, Driver, EventResult.

# APIs
## Event APIs (event-service)
Base URL: http://localhost:8081

### GET /api/events
Query parameters (all optional):
- sessionType: string (e.g. RACE, QUALIFYING)
- country: string (e.g. Belgium)
- year: number (e.g. 2023)
- page: integer, default 0
- size: integer, default 2

Response (page envelope):
{
  "page": 0,
  "size": 2,
  "total": 2,
  "items": [
    {
      "session_key": 9134,
      "session_name": "Belgian Grand Prix",
      "country_name": "Belgium",
      "date_start": "2023-07-29T15:05:00Z",
      "date_end": "2023-07-29T16:35:00Z",
      "year": 2023,
      "session_type": "RACE",
      "drivers": [
        { "driver_number": 1, "full_name": "Max Verstappen", "team_name": "Red Bull", "odds": 2 },
        { "driver_number": 16, "full_name": "Charles Leclerc", "team_name": "Ferrari", "odds": 3 }
      ]
    }
  ]
}

Notes:
- Drivers include odds randomly assigned in {2,3,4} (POC only).
- Field names are snake_case on the wire.

### GET /api/events/{sessionKey}
Returns details for a single event with drivers and odds.
Example:
GET http://localhost:8081/api/events/9134
Response:
{
  "session_key": 9134,
  "session_name": "Practice 1",
  "country_name": "Belgium",
  "date_start": "2023-07-29T15:05:00Z",
  "date_end": "2023-07-29T16:35:00Z",
  "year": 2023,
  "session_type": "Practice",
  "drivers": [
    { "driver_number": 1, "full_name": "Max VERSTAPPEN", "team_name": "Red Bull Racing", "odds": 3 },
    { "driver_number": 2, "full_name": "Logan SARGEANT", "team_name": "Williams", "odds": 4 }
  ]
}

### GET /api/events/{sessionKey}/winner
Returns the event winner if available.
- 200 OK with EventResult when available
- 404 Not Found if not available

Security
- Restricted to localhost only by default (see SecurityConfig). This is intended for internal server-to-server calls or local testing.
- Caveat when running in Docker: requests originating from another container are not "localhost" and will be denied. For cross-container settlement, temporarily relax SecurityConfig for this endpoint or run settlement-triggering calls from the host.

## Betting APIs (user-betting)
Base URL: http://localhost:8080

### POST /api/bets
Places a single bet on a driver to win a specific event.

Request body:
{
  "user_id": 1,
  "event_id": 9134,
  "driver_id": 1,
  "amount_eur": 10
}

Validations
- amount_eur must be >= 1
- event_id and driver_id must exist in the event details fetched from event-service

Response body:
{
  "bet_id": 1,
  "event_id": 9134,
  "driver_id": 1,
  "amount_eur": 10,
  "odds": 3,
  "status": "PENDING"
}

### POST /api/events/{eventId}/settle
Locks the event, fetches the winner from event-service, updates bets and user balances atomically using proportional distribution, persists the outcome, and marks the event as SETTLED.

Path parameter
- eventId: Long

Settlement Logic
- Total pool = sum of all bets placed on the event
- Winning bets receive proportional share of total pool based on their contribution to winning bets
- Formula: (individualWinningBet / totalWinningBets) × totalPool
- Amounts are rounded down to whole EUR (any remainder is lost due to rounding)
- Losing bets are marked as LOST with no payout

Responses
- 200 OK on success
- 400 Bad Request if event is not open or winner unavailable

# Configuration
- user-betting property: event.service.base-url (default http://localhost:8081)
  - Overridable via env var EVENT_SERVICE_BASE_URL (used in docker-compose.yml)
- Flyway (user-betting): enabled, locations=classpath:/migration
  - Seeds users: alice (100 EUR), bob (100 EUR)

# Troubleshooting
- Swagger requires no auth for both services.
- If user-betting fails to start with "relation ... does not exist", ensure Flyway is enabled and migrations are applied (docker-compose handles this).
- If POST /api/bets in Docker fails with Connection refused to localhost:8081, ensure EVENT_SERVICE_BASE_URL points to http://event-service:8081 in the user-betting container (docker-compose already sets this).
- If settlement calls fail with 403 from event-service winner endpoint in Docker, see the localhost-only caveat above.

