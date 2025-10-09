# Read Me First

# Getting Started
Make sure the docker service is up. E.g. in Windows make sure Docker Desktop is running.

## Running tests
To run tests for all modules use following command
./gradlew test


# Running Event service locally
## as a service
./gradlew :event-service:bootRun

## with Docker
1. Start docker service
2. Build the Spring Boot JAR from root ``./gradlew :event-service:bootJar``
3. Run the container `docker-compose up --build event-service`
4. verify it works with: http://localhost:8081/api/events?country=Belgium and http://localhost:8081/api/events/9134/winner

## Run all containers
docker compose up


# Project structure
## event-service module
Responsible for: providing event metadata and historical results from the event sources.
- Runs on port 8081 by default.
- Integrates with the public OpenF1 API, with simple caching and rate limiting.
- Exposes read-only REST APIs used by user-betting.

## user-betting module
Responsible for: user accounts, balances, placing bets, and settling events.
- Runs on port 8080 by default.
- Persists data in Postgres (managed via Flyway migrations).
- Maintains a historical_events table used to coordinate/lock event lifecycle during bets and settlement.
- All identifiers and money amounts are Long. Money is represented in EUR as whole numbers (no decimals).

## common module
Shared domain DTOs used between services: EventDetails, Driver, EventResult.

# API
## Event APIs (event-service)
Base URL: http://localhost:8081

### GET /api/events
Query parameters (all optional):
- sessionType: string (e.g. RACE, QUALIFYING)
- country: string (e.g. Belgium)
- year: number (e.g. 2023)
- page: integer, default 0
- size: integer, default 2

Response: a page envelope
{
  "page": 0,
  "size": 2,
  "total": 2,
  "items": [
    {
      "sessionKey": 9134,
      "sessionName": "Belgian Grand Prix",
      "countryName": "Belgium",
      "dateStart": "2023-07-29T15:05:00Z",
      "dateEnd": "2023-07-29T16:35:00Z",
      "year": 2023,
      "sessionType": "RACE",
      "drivers": [
        { "driverNumber": 1, "fullName": "Max Verstappen", "teamName": "Red Bull", "odds": 2 },
        { "driverNumber": 16, "fullName": "Charles Leclerc", "teamName": "Ferrari", "odds": 3 }
      ]
    }
  ]
}

Notes:
- For each event, the driver market is included with odds randomly assigned in {2,3,4} for this proof-of-concept.

### GET /api/events/{sessionKey}/winner
Returns the event winner if available.
- 200 OK with body EventResult when data is available
- 404 Not Found otherwise

Security:
- This endpoint is restricted to localhost only (see SecurityConfig). Intended for internal calls from user-betting.

Example:
GET http://localhost:8081/api/events/9134/winner
Response:
{
  "sessionKey": 9134,
  "finished": true,
  "winnerDriverNumber": 1,
  "providerFetchedAt": "2023-07-29T16:40:12Z"
}

## Betting APIs (user-betting)
Base URL: http://localhost:8080

### POST /api/bets
Places a single bet on a driver to win a specific event.

Request body:
{
  "userId": 123,
  "eventId": 9134,
  "driverId": 1,
  "amountEur": 25
}

Validations:
- amountEur must be >= 1
- eventId and driverId must correspond to an existing event/driver in event-service (verified before DB transaction)

Response body:
{
  "betId": 987,
  "eventId": 9134,
  "driverId": 1,
  "amountCents": 25,
  "odds": 3,
  "status": "PENDING"
}
Notes:
- Field names reflect the current implementation; amounts are whole-EUR Longs in this POC.

### POST /api/events/{eventId}/settle
Triggers settlement for the event. The service locks the event, computes outcomes, updates user balances, and records the result.

Path parameter:
- eventId: Long

Request body:
{
  "winningDriverId": 1
}

Responses:
- 200 OK on successful settlement
- 400/409 if event is not open for settlement or invalid

# Open API / swagger
- user-betting: http://localhost:8080/swagger-ui/index.html
- event-service: Swagger UI is not enabled by default in this module.

### Docker Compose support
This project contains a Docker Compose file named `docker-compose.yaml`.
- To run both services together: docker compose up
- After startup:
  - event-service: http://localhost:8081/api/events?country=Belgium
  - user-betting Swagger: http://localhost:8080/swagger-ui/index.html

