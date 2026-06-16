FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/event-consumer-1.0.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
