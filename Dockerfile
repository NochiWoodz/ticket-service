# ---------- Etapa 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# Se copian primero las dependencias para aprovechar la cache de capas de Docker
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Las pruebas ya se ejecutan en el job "build-test" del pipeline
RUN mvn -B -q package -DskipTests

# ---------- Etapa 2: runtime ----------
FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.title="ticket-service" \
      org.opencontainers.image.description="Sistema de Gestion de Reservas de Eventos con Tickets (EFS + S3)"

# Usuario sin privilegios (UID 1000). El directorio del EFS en el host debe pertenecer a 1000:1000
RUN groupadd -g 1000 app && useradd -u 1000 -g app -m -s /usr/sbin/nologin app \
    && mkdir -p /data/efs && chown -R app:app /data
WORKDIR /app
COPY --from=build --chown=app:app /build/target/ticket-service.jar app.jar

USER app
ENV EFS_BASE_PATH=/data/efs \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
VOLUME ["/data/efs"]
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
