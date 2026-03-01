# 📊 Analytics Service — Архитектура

> Тег: `АКТУАЛЬНО` | Обновлён: `2026-03-01` | Версия: `1.0`

---

## Обзор

Сервис аналитики строит отчёты на основе GPS-данных из TimescaleDB и metadata из PostgreSQL. Архитектура разделена на слои: генераторы отчётов, алгоритмы, query engine, кеш и экспорт.

---

## Диаграмма потока данных

```mermaid
flowchart TB
    subgraph Clients["Клиенты"]
        WebUI[Web UI]
        ExtAPI[External API]
        Sched[Scheduler]
    end

    subgraph AS["Analytics Service :8095"]
        REST[REST API]
        
        subgraph Generators["Report Generators"]
            MG[MileageGenerator]
            FG[FuelGenerator]
            GG[GeozoneGenerator]
            IG[IdleGenerator]
            SG[SpeedGenerator]
            SUM[SummaryGenerator]
        end
        
        subgraph Processing["Processing"]
            QE[QueryEngine]
            Alg[Algorithms]
            Cache[ReportCache]
        end
        
        subgraph Export["Export"]
            ES[ExportService]
            Excel[ExcelExporter]
            PDF[PdfExporter]
            CSV[CsvExporter]
        end
    end

    subgraph Storage["Хранилища"]
        TS[(TimescaleDB)]
        PG[(PostgreSQL)]
        Redis[(Redis)]
        S3[(S3 / MinIO)]
    end

    WebUI & ExtAPI --> REST
    Sched --> REST
    
    REST --> Generators
    Generators --> QE & Alg
    QE --> TS & PG
    Generators --> Cache
    Cache <--> Redis
    
    REST --> ES
    ES --> Excel & PDF & CSV
    ES --> S3
```

---

## Последовательность обработки запроса

```mermaid
sequenceDiagram
    participant C as Client
    participant API as REST API
    participant Cache as ReportCache
    participant Gen as Generator
    participant QE as QueryEngine
    participant TS as TimescaleDB
    participant Redis as Redis

    C->>API: GET /reports/mileage?orgId=1&vehicleIds=123&from=...&to=...
    API->>Cache: get(cacheKey)
    
    alt Cache Hit
        Cache->>Redis: GET report:mileage:hash
        Redis-->>Cache: JSON report
        Cache-->>API: CachedReport
        API-->>C: 200 JSON
    else Cache Miss
        API->>Gen: generate(params)
        Gen->>QE: getMileageData(vehicleId, from, to)
        QE->>TS: SELECT FROM daily_vehicle_stats
        TS-->>QE: Aggregated rows
        QE-->>Gen: MileageData
        Gen->>Gen: Calculate totals, detect trips
        Gen-->>API: MileageReport
        API->>Cache: set(cacheKey, report, ttl)
        Cache->>Redis: SETEX report:mileage:hash
        API-->>C: 200 JSON
    end
```

---

## Компоненты

| Компонент | Пакет | Назначение |
|-----------|-------|------------|
| **ReportApi** | `api` | HTTP маршруты для отчётов |
| **ExportApi** | `api` | HTTP маршруты для экспорта |
| **ScheduledApi** | `api` | CRUD расписаний, ручной запуск |
| **MileageGenerator** | `generator` | Генерация отчёта по пробегу |
| **FuelGenerator** | `generator` | Генерация отчёта по топливу |
| **GeozoneGenerator** | `generator` | Генерация отчёта по геозонам |
| **IdleGenerator** | `generator` | Генерация отчёта по простою |
| **SpeedGenerator** | `generator` | Генерация отчёта по скорости |
| **SummaryGenerator** | `generator` | Сводный отчёт по организации |
| **QueryEngine** | `query` | Запросы к TimescaleDB/PostgreSQL |
| **MileageCalculator** | `algorithm` | Расчёт пробега (Haversine) |
| **TripDetector** | `algorithm` | Детектирование поездок |
| **FuelEventDetector** | `algorithm` | Детектирование заправок/сливов |
| **ReportCache** | `cache` | Redis кеширование отчётов |
| **ExportService** | `export` | Фоновый экспорт + S3 upload |
| **ExcelExporter** | `export` | Генерация .xlsx (Apache POI) |
| **PdfExporter** | `export` | Генерация .pdf (OpenPDF) |
| **CsvExporter** | `export` | Генерация .csv |
| **ReportScheduler** | `scheduler` | Запланированные отчёты (cron) |
| **ScheduledReportRepo** | `repository` | CRUD расписаний в PostgreSQL |
| **ReportHistoryRepo** | `repository` | История генераций |

---

## ZIO Layer граф

```
AppConfig.allLayers
  ├── TimescaleTransactor.live
  │     └── QueryEngine.live
  │           └── MileageGenerator.live
  │           └── FuelGenerator.live
  │           └── GeozoneGenerator.live
  │           └── IdleGenerator.live
  │           └── SpeedGenerator.live
  │           └── SummaryGenerator.live
  ├── PostgresTransactor.live
  │     └── ScheduledReportRepository.live
  │     └── ReportHistoryRepository.live
  ├── Redis
  │     └── ReportCache.live
  ├── S3Client
  │     └── ExportService.live
  │           └── ExcelExporter
  │           └── PdfExporter
  │           └── CsvExporter
  └── Server (port 8095)
```
