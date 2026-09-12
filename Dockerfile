# --- Stage 1: build the jar ---
# Uses a full JDK image so the project's own Maven wrapper (mvnw) can
# download Maven itself and compile the app.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only what's needed to resolve dependencies first, so Docker can
# cache this layer and skip re-downloading dependencies when only
# source code changes (not pom.xml).
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Now copy the actual source and build.
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# --- Stage 2: run it ---
# A much smaller image containing only a JRE (no compiler, no Maven,
# no source code) — this is what actually ships/runs.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/linkforge-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
