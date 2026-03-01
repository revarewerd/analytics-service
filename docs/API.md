# 📊 Analytics Service — REST API

> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

---

## Base URL

```
http://localhost:8095
```

---

## Health

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/health` | Health check |

---

## Отчёты

### GET `/api/v1/reports/mileage` — Отчёт по пробегу

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `orgId` | long | да | ID организации |
| `vehicleIds` | long[] | нет | ID транспорта (через запятую) |
| `groupIds` | long[] | нет | ID групп |
| `from` | ISO datetime | да | Начало периода |
| `to` | ISO datetime | да | Конец периода |
| `groupBy` | string | нет | `day` / `week` / `month` (default: `day`) |
| `includeTrips` | bool | нет | Включить детализацию поездок (default: false) |

```bash
curl "http://localhost:8095/api/v1/reports/mileage?orgId=1&vehicleIds=123&from=2026-01-01T00:00:00Z&to=2026-01-31T23:59:59Z&groupBy=day"
```

### GET `/api/v1/reports/fuel` — Отчёт по топливу

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `orgId` | long | да | ID организации |
| `vehicleIds` | long[] | нет | ID транспорта |
| `from` | ISO datetime | да | Начало периода |
| `to` | ISO datetime | да | Конец периода |
| `includeEvents` | bool | нет | Включить заправки/сливы (default: true) |

### GET `/api/v1/reports/geozones` — Отчёт по геозонам

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `orgId` | long | да | ID организации |
| `vehicleIds` | long[] | нет | ID транспорта |
| `geozoneIds` | long[] | нет | ID геозон |
| `from` | ISO datetime | да | Начало периода |
| `to` | ISO datetime | да | Конец периода |

### GET `/api/v1/reports/idle` — Отчёт по простою

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `orgId` | long | да | ID организации |
| `vehicleIds` | long[] | нет | ID транспорта |
| `from` | ISO datetime | да | Начало периода |
| `to` | ISO datetime | да | Конец периода |

### GET `/api/v1/reports/speed-violations` — Превышения скорости

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `orgId` | long | да | ID организации |
| `vehicleIds` | long[] | нет | ID транспорта |
| `from` | ISO datetime | да | Начало периода |
| `to` | ISO datetime | да | Конец периода |
| `speedLimit` | int | нет | Лимит скорости (default: 90) |

### GET `/api/v1/reports/summary` — Сводный отчёт

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `orgId` | long | да | ID организации |
| `from` | ISO datetime | да | Начало периода |
| `to` | ISO datetime | да | Конец периода |

---

## Экспорт

### POST `/api/v1/reports/export` — Запустить экспорт

```bash
curl -X POST http://localhost:8095/api/v1/reports/export \
  -H "Content-Type: application/json" \
  -d '{
    "orgId": 1,
    "reportType": "mileage",
    "format": "xlsx",
    "parameters": {
      "vehicleIds": [123],
      "from": "2026-01-01T00:00:00Z",
      "to": "2026-01-31T23:59:59Z"
    }
  }'
# {"taskId":"uuid","status":"pending"}
```

### GET `/api/v1/reports/export/{taskId}` — Статус экспорта

```bash
curl http://localhost:8095/api/v1/reports/export/abc123
# {"status":"completed","progress":100,"downloadUrl":"...","expiresAt":"..."}
```

### GET `/api/v1/reports/export/{taskId}/download` — Скачать файл

---

## Расписания

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/v1/scheduled?orgId=...` | Список расписаний |
| POST | `/api/v1/scheduled?orgId=...` | Создать расписание |
| PUT | `/api/v1/scheduled/{id}?orgId=...` | Обновить |
| DELETE | `/api/v1/scheduled/{id}?orgId=...` | Удалить |
| POST | `/api/v1/scheduled/{id}/run?orgId=...` | Запустить вручную |

### Пример создания расписания

```bash
curl -X POST "http://localhost:8095/api/v1/scheduled?orgId=1" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Еженедельный пробег",
    "reportType": "mileage",
    "schedule": "0 8 * * 1",
    "periodType": "last_week",
    "vehicleIds": [],
    "exportFormat": "xlsx",
    "deliveryChannels": ["email"],
    "recipients": {"emails": ["admin@company.com"]}
  }'
```

---

## История

### GET `/api/v1/reports/history?orgId=...&from=...&to=...&status=...`

Параметры: `orgId` (обяз.), `from`, `to`, `status` (`pending`/`completed`/`failed`), `limit` (default: 50).
