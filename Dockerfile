# ============================================================
# Analytics Service — Multi-stage Docker build
# ============================================================

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Копируем sbt файлы для кэширования зависимостей
COPY project/build.properties project/
COPY project/plugins.sbt project/
COPY build.sbt .

# Скачиваем зависимости (кэшируется)
RUN sbt update

# Копируем исходники
COPY src/ src/

# Собираем fat JAR
RUN sbt assembly

# ============================================================
# Runtime
# ============================================================

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Создаём директорию для временных файлов экспорта
RUN mkdir -p /tmp/analytics-export && \
    addgroup -S analytics && \
    adduser -S analytics -G analytics && \
    chown analytics:analytics /tmp/analytics-export

COPY --from=builder /app/target/scala-3.4.0/analytics-service-assembly-*.jar app.jar

USER analytics

# Порт HTTP API
EXPOSE 8095

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8095/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
