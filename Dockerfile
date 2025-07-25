FROM maven:3.9.6-eclipse-temurin-22 AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ls -l && cat mvnw
COPY src src
RUN chmod +x mvnw
RUN mvn -B package -DskipTests

FROM eclipse-temurin:22-jdk-alpine
RUN apk add --no-cache ffmpeg
WORKDIR /deployments
COPY --from=build /build/target/quarkus-app/ ./

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/deployments/quarkus-run.jar"]