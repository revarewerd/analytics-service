# 📊 Analytics Service — Data Model

> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

---

## TimescaleDB — Continuous Aggregates

Сервис читает данные из continuous aggregates TimescaleDB, которые строятся поверх таблицы `gps_positions`.

### daily_vehicle_stats

```sql
CREATE MATERIALIZED VIEW daily_vehicle_stats
WITH (timescaledb.continuous) AS
SELECT
  time_bucket('1 day', timestamp) AS day,
  device_id,
  MAX(odometer) - MIN(odometer) AS mileage_meters,
  AVG(speed) AS avg_speed,
  MAX(speed) AS max_speed,
  COUNT(*) AS point_count,
  MIN(timestamp) AS first_point,
  MAX(timestamp) AS last_point,
  FIRST(latitude, timestamp) AS start_lat,
  FIRST(longitude, timestamp) AS start_lon,
  LAST(latitude, timestamp) AS end_lat,
  LAST(longitude, timestamp) AS end_lon
FROM gps_positions
GROUP BY time_bucket('1 day', timestamp), device_id
WITH NO DATA;
```

### hourly_fuel_stats

```sql
CREATE MATERIALIZED VIEW hourly_fuel_stats
WITH (timescaledb.continuous) AS
SELECT
  time_bucket('1 hour', timestamp) AS hour,
  device_id,
  FIRST(fuel_level, timestamp) AS start_level,
  LAST(fuel_level, timestamp) AS end_level,
  MIN(fuel_level) AS min_level,
  MAX(fuel_level) AS max_level,
  MAX(odometer) - MIN(odometer) AS mileage_meters
FROM gps_positions
WHERE fuel_level IS NOT NULL
GROUP BY time_bucket('1 hour', timestamp), device_id
WITH NO DATA;
```

### daily_motion_stats

```sql
CREATE MATERIALIZED VIEW daily_motion_stats
WITH (timescaledb.continuous) AS
SELECT
  time_bucket('1 day', timestamp) AS day,
  device_id,
  SUM(CASE WHEN speed > 3 THEN 1 ELSE 0 END) * 
    EXTRACT(EPOCH FROM '10 seconds'::interval) AS moving_seconds,
  SUM(CASE WHEN speed <= 3 THEN 1 ELSE 0 END) * 
    EXTRACT(EPOCH FROM '10 seconds'::interval) AS idle_seconds,
  SUM(CASE WHEN ignition = true THEN 1 ELSE 0 END) * 
    EXTRACT(EPOCH FROM '10 seconds'::interval) AS engine_seconds
FROM gps_positions
GROUP BY time_bucket('1 day', timestamp), device_id
WITH NO DATA;
```

---

## PostgreSQL — Таблицы сервиса

### report_templates

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | BIGSERIAL PK | |
| name | VARCHAR(100) | Название шаблона |
| organization_id | BIGINT | FK → organizations |
| report_type | VARCHAR(50) | mileage, fuel, geozones и т.д. |
| config | JSONB | Настройки: columns, groupBy, includeTrips |
| default_filters | JSONB | Фильтры по умолчанию |
| created_at | TIMESTAMPTZ | |
| created_by | BIGINT | FK → users |

### scheduled_reports

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | BIGSERIAL PK | |
| name | VARCHAR(100) | Название |
| organization_id | BIGINT NOT NULL | FK → organizations |
| template_id | BIGINT | FK → report_templates |
| schedule | VARCHAR(50) | Cron: "0 8 * * 1" |
| timezone | VARCHAR(50) | Default: Europe/Moscow |
| report_type | VARCHAR(50) | Тип отчёта |
| vehicle_ids | BIGINT[] | Список ТС |
| group_ids | BIGINT[] | Список групп |
| period_type | VARCHAR(20) | yesterday, last_week, last_month |
| delivery_channels | VARCHAR(20)[] | email, telegram |
| recipients | JSONB | {"emails": [...], "telegramChatIds": [...]} |
| export_format | VARCHAR(10) | xlsx, pdf, csv |
| enabled | BOOLEAN | Default: true |
| last_run_at | TIMESTAMPTZ | |
| next_run_at | TIMESTAMPTZ | |
| created_at | TIMESTAMPTZ | |

### report_history

| Колонка | Тип | Описание |
|---------|-----|----------|
| id | BIGSERIAL PK | |
| organization_id | BIGINT NOT NULL | |
| user_id | BIGINT | FK → users (null для scheduled) |
| scheduled_id | BIGINT | FK → scheduled_reports |
| report_type | VARCHAR(50) | |
| parameters | JSONB | Параметры запроса |
| status | VARCHAR(20) | pending, processing, completed, failed |
| file_url | VARCHAR(500) | S3 URL |
| file_size | BIGINT | Размер файла |
| error_message | TEXT | |
| created_at | TIMESTAMPTZ | Партиционирование TimescaleDB |
| completed_at | TIMESTAMPTZ | |
| expires_at | TIMESTAMPTZ | Когда удалить файл |

---

## Redis ключи

| Ключ | Тип | TTL | Описание |
|------|-----|-----|----------|
| `report:{type}:{hash}` | STRING (JSON) | 1h (история) / 5m (сегодня) | Кеш готового отчёта |
| `agg:daily:{vehicleId}:{date}` | HASH | 7 дней | Агрегированные данные за день |
| `export:{taskId}` | HASH | 24 часа | Статус задачи экспорта |

### Примеры

```bash
# Кеш отчёта
GET report:mileage:a1b2c3d4
# → JSON отчёта

# Агрегат за день
HGETALL agg:daily:123:2026-01-25
# → mileage=150.5, fuel=45.2, max_speed=120

# Статус экспорта
HGETALL export:abc-def-123
# → status=completed, progress=100, file_url=..., error=null
```
