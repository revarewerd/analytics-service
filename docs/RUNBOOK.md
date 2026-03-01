# 📊 Analytics Service — Runbook

> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

---

## Запуск

### Локально (SBT)

```bash
cd services/analytics-service
export TIMESCALE_URL=jdbc:postgresql://localhost:5432/tracker_gps
export DATABASE_URL=jdbc:postgresql://localhost:5432/tracker_devices
export REDIS_HOST=localhost
export S3_ENDPOINT=http://localhost:9000
export S3_ACCESS_KEY=minioadmin
export S3_SECRET_KEY=minioadmin
sbt run
```

### Docker

```bash
docker build -t analytics-service services/analytics-service/
docker run -p 8095:8095 --env-file .env analytics-service
```

### Health check

```bash
curl http://localhost:8095/health
```

---

## Типичные ошибки

### 1. Slow report generation (>10s)

**Причина:** Continuous aggregates не обновлены или запрос по raw данным за большой период.

**Диагностика:**
```sql
-- Проверить статус обновления агрегатов
SELECT * FROM timescaledb_information.continuous_aggregate_stats;

-- Вручную обновить
CALL refresh_continuous_aggregate('daily_vehicle_stats', '2026-01-01', '2026-02-01');
```

### 2. Redis cache miss ratio высокий

**Причина:** Многообразие параметров запросов (разные периоды, фильтры).

**Диагностика:**
```bash
redis-cli INFO keyspace
redis-cli KEYS "report:*" | wc -l
```

### 3. Export task зависла (processing)

**Причина:** Ошибка при генерации файла или S3 upload.

**Диагностика:**
```bash
redis-cli HGETALL "export:$TASK_ID"
# Проверить status, error
```

**Решение:** Установить TTL на export задачи (24h auto-cleanup).

### 4. S3/MinIO недоступен

**Причина:** MinIO контейнер не запущен или неправильные credentials.

**Диагностика:**
```bash
curl http://localhost:9000/minio/health/live
```

---

## Мониторинг

### Prometheus метрики

```
as_reports_generated_total{type="mileage"}     — Счётчик сгенерированных отчётов
as_report_generation_seconds{type="mileage"}   — Время генерации
as_cache_hits_total{type="mileage"}            — Попадания в кеш
as_cache_misses_total{type="mileage"}          — Промахи кеша
as_exports_total{format="xlsx"}                — Экспортов
as_scheduled_runs_total{status="success"}      — Запланированные запуски
```

### Redis debug

```bash
# Кеш отчётов
redis-cli KEYS "report:*"

# Задачи экспорта
redis-cli KEYS "export:*"

# Агрегаты
redis-cli KEYS "agg:daily:*"
```
