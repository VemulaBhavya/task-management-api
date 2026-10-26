FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/task-management-api.jar ./app.jar
EXPOSE 7070
ENTRYPOINT ["java", "-jar", "/app/app.jar"]