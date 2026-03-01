# 📊 Analytics Service

> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

---

## Обзор

Analytics Service — сервис генерации отчётов и аналитики на основе GPS-данных. Использует TimescaleDB continuous aggregates для эффективных расчётов, Redis для кеширования готовых отчётов, S3/MinIO для хранения экспортированных файлов.

| Параметр | Значение |
|----------|----------|
| **Порт** | 8095 |
| **Package** | `com.wayrecall.tracker.analytics` |
| **Вход** | TimescaleDB (gps_positions), PostgreSQL (events, devices) |
| **Выход** | JSON отчёты, Excel, PDF, CSV |
| **Кеш** | Redis (готовые отчёты, задачи экспорта) |
| **Хранилище файлов** | S3 / MinIO |

---

## Типы отчётов

| Тип | Описание |
|-----|----------|
| **Mileage** | Пробег — суточный, месячный, по периоду |
| **Fuel** | Расход топлива, заправки, сливы |
| **Geozones** | Время в зонах, визиты |
| **Idle** | Простои, стоянки, моточасы |
| **SpeedViolations** | Превышения скорости |
| **Summary** | Сводный по организации / группам |

---

## Быстрый старт

### SBT

```bash
cd services/analytics-service
sbt run
```

### Docker

```bash
docker build -t analytics-service .
docker run -p 8095:8095 \
  -e TIMESCALE_URL=jdbc:postgresql://localhost:5432/tracker_gps \
  -e DATABASE_URL=jdbc:postgresql://localhost:5432/tracker_devices \
  -e REDIS_HOST=localhost \
  -e S3_ENDPOINT=http://localhost:9000 \
  analytics-service
```

---

## Переменные окружения

| Переменная | По умолчанию | Описание |
|------------|-------------|----------|
| `HTTP_PORT` | `8095` | Порт REST API |
| `TIMESCALE_URL` | `jdbc:postgresql://localhost:5432/tracker_gps` | TimescaleDB URL |
| `TIMESCALE_USER` | `postgres` | TimescaleDB пользователь |
| `TIMESCALE_PASSWORD` | `postgres` | TimescaleDB пароль |
| `TIMESCALE_MAX_POOL` | `30` | Макс. пул соединений TimescaleDB |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/tracker_devices` | PostgreSQL URL |
| `DATABASE_USER` | `postgres` | PostgreSQL пользователь |
| `DATABASE_PASSWORD` | `postgres` | PostgreSQL пароль |
| `DATABASE_MAX_POOL` | `10` | Макс. пул соединений PostgreSQL |
| `REDIS_HOST` | `localhost` | Redis хост |
| `REDIS_PORT` | `6379` | Redis порт |
| `S3_ENDPOINT` | `http://localhost:9000` | S3/MinIO endpoint |
| `S3_ACCESS_KEY` | `` | S3 access key |
| `S3_SECRET_KEY` | `` | S3 secret key |
| `S3_BUCKET` | `tracker-reports` | S3 bucket для файлов |
| `CACHE_HISTORICAL_TTL` | `3600` | TTL кеша для исторических данных (сек) |
| `CACHE_REALTIME_TTL` | `300` | TTL кеша для текущего дня (сек) |
| `EXPORT_MAX_CONCURRENT` | `10` | Макс. параллельных экспортов |
| `SCHEDULER_ENABLED` | `true` | Включить запланированные отчёты |

---

## Health Check

```bash
curl http://localhost:8095/health
# {"status":"ok","service":"analytics-service"}
```

---

## Связанные документы

- [ARCHITECTURE.md](ARCHITECTURE.md) — внутренняя архитектура
- [API.md](API.md) — REST endpoints
- [DATA_MODEL.md](DATA_MODEL.md) — схемы БД и Redis ключи
- [RUNBOOK.md](RUNBOOK.md) — запуск и дебаг
