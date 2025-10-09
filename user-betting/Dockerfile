# ---------- build stage ----------
FROM gradle:8.6-jdk21 AS builder
WORKDIR /home/gradle/project
# copy gradle wrapper and build files first for caching
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts settings.gradle.kts ./
# copy sources
COPY src src
# Build the boot jar (no daemon)
RUN ./gradlew bootJar -x test --no-daemon

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-jammy
ARG APP_USER=app
ARG APP_HOME=/app

# create non-root user
RUN addgroup --system ${APP_USER} && adduser --system --ingroup ${APP_USER} ${APP_USER}

WORKDIR ${APP_HOME}
# copy jar
COPY --from=builder /home/gradle/project/build/libs/*.jar app.jar

# expose port
EXPOSE 8080

# set environment defaults (can be overridden in compose)
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS=""

# use non-root user
USER ${APP_USER}

HEALTHCHECK --interval=10s --timeout=3s --retries=6 CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar /app/app.jar" ]
