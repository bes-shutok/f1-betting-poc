# ---------- build stage ----------
FROM gradle:8.6-jdk21 AS builder
WORKDIR /home/gradle/project

# Copy Gradle wrapper and root build files for multi-module build caching
COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts build.gradle.kts ./

# Copy only the modules we need to build user-betting (and its dependency 'common')
COPY common ./common
COPY user-betting ./user-betting

# Build only the user-betting module boot jar (skip tests for faster image build)
RUN ./gradlew :user-betting:bootJar -x test --no-daemon

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre-jammy
ARG APP_USER=app
ARG APP_HOME=/app

# create non-root user
RUN addgroup --system ${APP_USER} && adduser --system --ingroup ${APP_USER} ${APP_USER}

WORKDIR ${APP_HOME}
# copy jar
COPY --from=builder /home/gradle/project/user-betting/build/libs/*.jar app.jar

# expose port
EXPOSE 8080

# set environment defaults (can be overridden in compose)
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS=""

# use non-root user
USER ${APP_USER}

HEALTHCHECK --interval=10s --timeout=3s --retries=6 CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar /app/app.jar" ]
