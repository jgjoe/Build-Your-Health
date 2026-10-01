# Build stage: Maven Wrapper on JDK 17.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
COPY src/ src/
# Tests need Docker (Testcontainers); they run in CI, the image only packages the app.
RUN sh mvnw -B -DskipTests package

# Run stage: JRE only.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
