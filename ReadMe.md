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
4. verify it works: http://localhost:8081/api/events?country=Belgium

## Run all containers
docker compose up


# Project structure
## event-service module
Responsible for: providing event metadata and historical results from the event sources

# API
## Event APIs
### GET /api/events
To be filtered by:
1. Session Type
2. Year
3. Country

It also returns the Driver Market of each event:
1. The full name of the Driver.
2. The ID Number of the Driver.
3. The odds when placing a bet for this driver to win the F1
   event.
   a. For simplicity, value can only be 2 , 3 or 4 .
   b. Always return a random integer between these 3
   values.

### GET /api/events/{eventId}/result
a. The System receives a request for a F1 Event that has been finished.
i. We get the ID of the event and the ID of the driver that won.

This API is to be called internally from the user-betting service


# Open API / swagger
After running the application
mvn spring-boot:run
One can see the API by this path
http://localhost:8080/swagger-ui/index.html

### Docker Compose support
This project contains a Docker Compose file named `docker-compose.yaml`.

