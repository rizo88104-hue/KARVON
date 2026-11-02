# karvon-api: build with Maven, run on a JRE 21 with the Spring Boot layered jar, as a non-root user.
# Tests run in CI (they need Docker for Testcontainers), not in this build.

# ---- build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
# Dependencies first: this layer is reused until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/karvon-api-*.jar extract --layers --launcher --destination /app

# ---- run
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S karvon && adduser -S -G karvon -H -s /sbin/nologin karvon
WORKDIR /app
# One layer per Boot layer: a code change only replaces the small "application" layer.
COPY --from=build --chown=karvon:karvon /app/dependencies/ ./
COPY --from=build --chown=karvon:karvon /app/spring-boot-loader/ ./
COPY --from=build --chown=karvon:karvon /app/snapshot-dependencies/ ./
COPY --from=build --chown=karvon:karvon /app/application/ ./
USER karvon
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
