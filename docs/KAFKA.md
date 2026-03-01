# 📊 Analytics Service — Kafka

> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

---

## Kafka топики

Analytics Service **не работает напрямую с Kafka**. Данные поступают из TimescaleDB (куда пишет History Writer из `gps-events`).

В будущем (PostMVP) возможно подписка на `gps-events` для real-time dashboard, но для MVP все данные читаются из БД.

---

## Связи с другими сервисами

```
History Writer ──(Kafka: gps-events)──> TimescaleDB (gps_positions)
                                              │
                                              ▼
                                     Analytics Service (читает)
```

Подробнее: [infra/kafka/TOPICS.md](../../../infra/kafka/TOPICS.md)
