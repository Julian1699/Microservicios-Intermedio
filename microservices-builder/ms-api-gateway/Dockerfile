# Contexto de build (obligatorio): carpeta microservices-builder (padre del monorepo).
#   docker build -f ms-api-gateway/Dockerfile .

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY ms-discovery-server/pom.xml ms-discovery-server/
COPY ms-api-gateway/pom.xml ms-api-gateway/
COPY ms-common-orders/pom.xml ms-common-orders/
COPY ms-common-stock/pom.xml ms-common-stock/
COPY ms-common-products/pom.xml ms-common-products/

RUN mvn -pl ms-api-gateway -am dependency:go-offline -B -q || true

COPY . .
RUN mvn -pl ms-api-gateway -am package -DskipTests -B -q

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/ms-api-gateway/target/ms-api-gateway-*.jar /app/app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
