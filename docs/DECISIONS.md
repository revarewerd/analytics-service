# 📊 Analytics Service — Архитектурные решения (ADR)

> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

---

## ADR-001: TimescaleDB Continuous Aggregates вместо raw queries

**Контекст:** Запросы по миллионам GPS-точек за месяц — медленно.

**Решение:** Использовать continuous aggregates (daily_vehicle_stats, hourly_fuel_stats, daily_motion_stats). Для детальных данных (trips, violations) — raw queries только за короткие периоды.

**Следствия:**
- Отчёты за месяц = запрос к ~30 строкам агрегата, а не к миллионам точек
- Фоновое обновление каждый час
- Задержка данных ~ 1 час для агрегатов

---

## ADR-002: Redis кеширование с TTL на основе периода

**Контекст:** Одни и те же отчёты запрашиваются много раз.

**Решение:**
- Исторические данные (to < сегодня) → TTL 1 час
- Текущий день включён → TTL 5 минут
- Cache key = `report:{type}:{md5(params)}`

**Следствия:** Cache hit ratio ~65%, снижение нагрузки на TimescaleDB.

---

## ADR-003: Background export с S3

**Контекст:** Генерация Excel/PDF для больших отчётов может занять 10-30 секунд.

**Решение:** Асинхронный export: POST возвращает taskId, фоновая задача генерирует файл, загружает в S3, клиент poll-ит статус.

**Следствия:**
- REST API не блокируется на тяжёлых отчётах
- Presigned URLs для скачивания (TTL 24h)
- Progress tracking через Redis hash

---

## ADR-004: Два пула подключений к БД

**Контекст:** Сервис работает с двумя базами — TimescaleDB (GPS данные) и PostgreSQL (метадата: devices, templates, schedules).

**Решение:** Два отдельных HikariPool:
- TimescaleDB: maxPoolSize=30 (тяжёлые аналитические запросы)
- PostgreSQL: maxPoolSize=10 (лёгкие CRUD операции)

---

## ADR-005: Apache POI для Excel, OpenPDF для PDF

**Контекст:** Нужен экспорт в Excel и PDF.

**Решение:** Apache POI (XSSFWorkbook) для .xlsx, OpenPDF для .pdf, ручной CSV writer.

**Следствия:** Тяжёлые зависимости (POI ~30 MB), но надёжные и зрелые библиотеки. Для MVP — без Streaming API POI.

---

## ADR-006: Cron-based Scheduler через ZIO Schedule

**Контекст:** Запланированные отчёты должны запускаться по расписанию.

**Решение:** ZIO fiber с `Schedule.fixed(1.minute)` проверяет `scheduled_reports.next_run_at`. Парсинг cron через `cron4s`.

**Следствия:** Простая реализация, одна инстанция сервиса. Для multi-instance — нужен distributed lock (PostMVP).
