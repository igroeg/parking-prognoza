FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/parking-prognoza-*.jar app.jar
ENV TZ=Europe/Sofia
EXPOSE 8080
VOLUME /app/data
ENTRYPOINT ["java", "-jar", "app.jar"]
