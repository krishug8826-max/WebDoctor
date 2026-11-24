# Stage 1: Build Spring Boot application
FROM maven:3-eclipse-temurin-21 AS java-build

WORKDIR /workspace

COPY backend/pom.xml ./pom.xml
COPY backend/src ./src

RUN mvn -B -DskipTests package


# Stage 2: Production runtime
FROM node:26.13.1-slim

ENV NODE_ENV=production \
    SPRING_PROFILES_ACTIVE=prod \
    CHROME_PATH=/usr/bin/chromium

# Install Java 21 and Chromium
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-21-jre-headless \
        chromium \
        ca-certificates \
        fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Spring Boot executable JAR
COPY --from=java-build --chown=node:node /workspace/target/backend-0.0.1-SNAPSHOT.jar ./app.jar

# Install Lighthouse dependencies
COPY --chown=node:node backend/package.json backend/package-lock.json ./

RUN npm ci --omit=dev && npm cache clean --force

# Copy Lighthouse runner
COPY --chown=node:node backend/scripts ./scripts

USER node

EXPOSE 8081

CMD ["java", "-jar", "/app/app.jar"]