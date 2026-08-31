FROM maven:3.9.16-eclipse-temurin-21-noble AS build

ARG MODULE
WORKDIR /workspace
COPY pom.xml ./
COPY services ./services
RUN mvn -B -ntp -pl "services/${MODULE}" -am clean package -DskipTests \
    && cp "services/${MODULE}"/target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-noble

RUN useradd --system --uid 10001 --create-home appuser
USER appuser
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
