FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Look for the JAR in the current directory instead of build/libs/
COPY *.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
