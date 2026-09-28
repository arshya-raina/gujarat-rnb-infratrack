# Runs the prebuilt jar and web app. Build context: the project root.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY backend/infratrack.jar backend/infratrack.jar
COPY backend/lib backend/lib
COPY frontend/dist frontend/dist
EXPOSE 8080
VOLUME /app/data
CMD ["java", "-jar", "backend/infratrack.jar"]
