# Use the official OpenJDK 23 image
FROM openjdk:23

# Create a directory for the application
WORKDIR /usr/app

# Copy the built JAR file to the container
COPY target/spring-steam-backend-0.0.1-SNAPSHOT.jar /usr/app/

# Expose the port your application listens on
EXPOSE 9000

# Run the JAR file
ENTRYPOINT ["java", "-jar", "spring-steam-backend-0.0.1-SNAPSHOT.jar"]
