FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/event-producer-*.jar app.jar
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
