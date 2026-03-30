# Multi-stage build para Java Spring Boot
# Etapa 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml ./
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Producción
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Usuario no root por seguridad (Best Practice)
RUN adduser -D -s /bin/sh springboot
USER springboot

COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "app.jar"]
