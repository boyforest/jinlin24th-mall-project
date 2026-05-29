FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
ENV JAVA_OPTS=""
COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 7878
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
