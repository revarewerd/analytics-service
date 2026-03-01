# 📊 Analytics Service — Документация

> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

---

## 📑 Содержание

| Файл | Описание |
|------|----------|
| [README.md](README.md) | Обзор, запуск, переменные окружения |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Внутренняя архитектура, диаграммы, ZIO Layer |
| [API.md](API.md) | REST endpoints, примеры curl |
| [DATA_MODEL.md](DATA_MODEL.md) | PostgreSQL/TimescaleDB схемы, Redis ключи |
| [KAFKA.md](KAFKA.md) | — (сервис не работает с Kafka) |
| [DECISIONS.md](DECISIONS.md) | ADR — архитектурные решения |
| [RUNBOOK.md](RUNBOOK.md) | Запуск, дебаг, типичные ошибки |

---

## 🔗 Связанная инфраструктура

| Ресурс | Ссылка |
|--------|--------|
| Общая архитектура Block 2 | [docs/ARCHITECTURE_BLOCK2.md](../../../docs/ARCHITECTURE_BLOCK2.md) |
| TimescaleDB схемы | [infra/databases/timescaledb-init.sql](../../../infra/databases/timescaledb-init.sql) |
| Redis ключи (общие) | [infra/redis/](../../../infra/redis/) |
| Notification Service | [services/notification-service/docs/](../../notification-service/docs/) |
