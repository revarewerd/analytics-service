package com.wayrecall.tracker.analytics.domain

import zio.json.*
import java.time.{Instant, LocalDate, Duration as JDuration}
import java.util.UUID

// ============================================================
// Перечисления
// ============================================================

/** Типы отчётов */
enum ReportType:
  case Mileage, Fuel, Geozones, Idle, SpeedViolations, Summary

object ReportType:
  given JsonEncoder[ReportType] = JsonEncoder[String].contramap(_.toString.toLowerCase)
  given JsonDecoder[ReportType] = JsonDecoder[String].mapOrFail { s =>
    ReportType.values.find(_.toString.equalsIgnoreCase(s))
      .toRight(s"Неизвестный тип отчёта: $s")
  }

/** Форматы экспорта */
enum ExportFormat:
  case Xlsx, Pdf, Csv

object ExportFormat:
  given JsonEncoder[ExportFormat] = JsonEncoder[String].contramap(_.toString.toLowerCase)
  given JsonDecoder[ExportFormat] = JsonDecoder[String].mapOrFail { s =>
    ExportFormat.values.find(_.toString.equalsIgnoreCase(s))
      .toRight(s"Неизвестный формат: $s")
  }

/** Статус задачи экспорта */
enum ExportStatus:
  case Pending, Processing, Completed, Failed

object ExportStatus:
  given JsonEncoder[ExportStatus] = JsonEncoder[String].contramap(_.toString.toLowerCase)
  given JsonDecoder[ExportStatus] = JsonDecoder[String].mapOrFail { s =>
    ExportStatus.values.find(_.toString.equalsIgnoreCase(s))
      .toRight(s"Неизвестный статус: $s")
  }

/** Тип периода для запланированных отчётов */
enum PeriodType:
  case Yesterday, LastWeek, LastMonth, Custom

object PeriodType:
  given JsonEncoder[PeriodType] = JsonEncoder[String].contramap {
    case PeriodType.Yesterday => "yesterday"
    case PeriodType.LastWeek  => "last_week"
    case PeriodType.LastMonth => "last_month"
    case PeriodType.Custom    => "custom"
  }
  given JsonDecoder[PeriodType] = JsonDecoder[String].mapOrFail {
    case "yesterday"  => Right(PeriodType.Yesterday)
    case "last_week"  => Right(PeriodType.LastWeek)
    case "last_month" => Right(PeriodType.LastMonth)
    case "custom"     => Right(PeriodType.Custom)
    case other        => Left(s"Неизвестный тип периода: $other")
  }

/** Статус записи истории */
enum ReportHistoryStatus:
  case Pending, Processing, Completed, Failed

object ReportHistoryStatus:
  given JsonEncoder[ReportHistoryStatus] = JsonEncoder[String].contramap(_.toString.toLowerCase)
  given JsonDecoder[ReportHistoryStatus] = JsonDecoder[String].mapOrFail { s =>
    ReportHistoryStatus.values.find(_.toString.equalsIgnoreCase(s))
      .toRight(s"Неизвестный статус истории: $s")
  }

// ============================================================
// Базовые типы
// ============================================================

/** Диапазон дат для запроса */
final case class DateRange(
    from: Instant,
    to: Instant
) derives JsonCodec

/** GPS координаты */
final case class Coordinates(
    latitude: Double,
    longitude: Double
) derives JsonCodec

/** GPS точка с timestamp */
final case class GpsPoint(
    timestamp: Instant,
    latitude: Double,
    longitude: Double,
    speed: Double,
    odometer: Option[Double],
    fuelLevel: Option[Double],
    ignition: Option[Boolean]
) derives JsonCodec

// ============================================================
// Параметры запроса отчёта
// ============================================================

/** Общие параметры запроса отчёта */
final case class ReportParams(
    organizationId: Long,
    vehicleIds: List[Long],
    groupIds: List[Long],
    from: Instant,
    to: Instant,
    reportType: ReportType,
    // Доп. параметры для конкретных типов отчётов
    groupBy: Option[String],           // day, week, month — для mileage
    includeTrips: Option[Boolean],     // детализация поездок — для mileage
    includeEvents: Option[Boolean],    // заправки/сливы — для fuel
    geozoneIds: Option[List[Long]],    // фильтр по геозонам
    speedLimit: Option[Int]            // лимит скорости для speed violations
) derives JsonCodec

// ============================================================
// Отчёт по пробегу (Mileage)
// ============================================================

/** Данные за один день (mileage) */
final case class DailyMileage(
    date: LocalDate,
    mileageKm: Double,
    avgSpeed: Double,
    maxSpeed: Double,
    engineHours: Double,
    pointCount: Long
) derives JsonCodec

/** Одна поездка */
final case class Trip(
    startTime: Instant,
    endTime: Instant,
    startCoords: Coordinates,
    endCoords: Coordinates,
    distanceKm: Double,
    maxSpeed: Double,
    avgSpeed: Double,
    durationMinutes: Double
) derives JsonCodec

/** Полный отчёт по пробегу */
final case class MileageReport(
    vehicleId: Long,
    period: DateRange,
    totalMileageKm: Double,
    totalEngineHours: Double,
    averageSpeed: Double,
    maxSpeed: Double,
    dailyData: List[DailyMileage],
    trips: List[Trip]
) derives JsonCodec

// ============================================================
// Отчёт по топливу (Fuel)
// ============================================================

/** Событие: заправка или слив */
final case class FuelEvent(
    eventType: String,     // "refuel" или "drain"
    timestamp: Instant,
    coords: Coordinates,
    volumeLiters: Double,
    levelBefore: Double,
    levelAfter: Double
) derives JsonCodec

/** Потребление за день */
final case class DailyFuel(
    date: LocalDate,
    consumedLiters: Double,
    mileageKm: Double,
    avgConsumptionPer100km: Double
) derives JsonCodec

/** Полный отчёт по топливу */
final case class FuelReport(
    vehicleId: Long,
    period: DateRange,
    totalConsumedLiters: Double,
    totalRefueledLiters: Double,
    totalDrainedLiters: Double,
    avgConsumptionPer100km: Double,
    refuels: List[FuelEvent],
    drains: List[FuelEvent],
    dailyConsumption: List[DailyFuel]
) derives JsonCodec

// ============================================================
// Отчёт по геозонам (Geozones)
// ============================================================

/** Статистика по одной геозоне */
final case class GeozoneStats(
    geozoneId: Long,
    geozoneName: String,
    totalVisits: Int,
    totalTimeMinutes: Double,
    avgVisitMinutes: Double
) derives JsonCodec

/** Один визит в геозону */
final case class GeozoneVisit(
    geozoneId: Long,
    geozoneName: String,
    enterTime: Instant,
    exitTime: Option[Instant],
    durationMinutes: Double,
    enterCoords: Coordinates,
    exitCoords: Option[Coordinates]
) derives JsonCodec

/** Полный отчёт по геозонам */
final case class GeozoneReport(
    vehicleId: Long,
    period: DateRange,
    geozones: List[GeozoneStats],
    visits: List[GeozoneVisit]
) derives JsonCodec

// ============================================================
// Отчёт по простою (Idle)
// ============================================================

/** Одна стоянка */
final case class Parking(
    startTime: Instant,
    endTime: Instant,
    coords: Coordinates,
    durationMinutes: Double,
    engineOn: Boolean
) derives JsonCodec

/** Полный отчёт по простою */
final case class IdleReport(
    vehicleId: Long,
    period: DateRange,
    totalIdleMinutes: Double,
    totalIdleWithEngineMinutes: Double,
    totalParkings: Int,
    parkings: List[Parking]
) derives JsonCodec

// ============================================================
// Отчёт по превышениям скорости (SpeedViolations)
// ============================================================

/** Одно превышение скорости */
final case class SpeedViolation(
    timestamp: Instant,
    coords: Coordinates,
    actualSpeed: Double,
    speedLimit: Double,
    overspeedKmh: Double,
    durationSeconds: Long
) derives JsonCodec

/** Полный отчёт по превышениям */
final case class SpeedViolationsReport(
    vehicleId: Long,
    period: DateRange,
    speedLimit: Double,
    totalViolations: Int,
    violations: List[SpeedViolation]
) derives JsonCodec

// ============================================================
// Сводный отчёт (Summary)
// ============================================================

/** Статистика по одному ТС в сводном отчёте */
final case class VehicleSummary(
    vehicleId: Long,
    vehicleName: String,
    mileageKm: Double,
    fuelConsumedLiters: Double,
    engineHours: Double,
    maxSpeed: Double,
    speedViolations: Int,
    tripCount: Int,
    idleMinutes: Double
) derives JsonCodec

/** Сводный отчёт по организации */
final case class SummaryReport(
    organizationId: Long,
    period: DateRange,
    totalVehicles: Int,
    totalMileageKm: Double,
    totalFuelConsumedLiters: Double,
    totalEngineHours: Double,
    vehicles: List[VehicleSummary]
) derives JsonCodec

// ============================================================
// Экспорт
// ============================================================

/** Запрос на экспорт */
final case class ExportRequest(
    organizationId: Long,
    reportType: ReportType,
    format: ExportFormat,
    parameters: ReportParams
) derives JsonCodec

/** Задача экспорта (трекинг в Redis) */
final case class ExportTask(
    taskId: String,
    organizationId: Long,
    reportType: ReportType,
    format: ExportFormat,
    status: ExportStatus,
    progress: Int,
    fileUrl: Option[String],
    error: Option[String],
    createdAt: Instant,
    completedAt: Option[Instant]
) derives JsonCodec

/** Ответ при создании задачи экспорта */
final case class ExportTaskCreated(
    taskId: String,
    status: ExportStatus
) derives JsonCodec

// ============================================================
// Шаблоны и расписания
// ============================================================

/** Шаблон отчёта */
final case class ReportTemplate(
    id: Long,
    name: String,
    organizationId: Long,
    reportType: ReportType,
    config: String,            // JSON строка
    defaultFilters: String,    // JSON строка
    createdAt: Instant,
    createdBy: Long
) derives JsonCodec

/** Расписание отчёта */
final case class ScheduledReport(
    id: Long,
    name: String,
    organizationId: Long,
    templateId: Option[Long],
    schedule: String,          // cron выражение: "0 8 * * 1"
    timezone: String,
    reportType: ReportType,
    vehicleIds: List[Long],
    groupIds: List[Long],
    periodType: PeriodType,
    deliveryChannels: List[String],
    recipients: String,        // JSON: {"emails": [...], "telegramChatIds": [...]}
    exportFormat: ExportFormat,
    enabled: Boolean,
    lastRunAt: Option[Instant],
    nextRunAt: Option[Instant],
    createdAt: Instant
) derives JsonCodec

/** Запрос на создание расписания */
final case class CreateScheduledReport(
    name: String,
    reportType: ReportType,
    schedule: String,
    timezone: Option[String],
    vehicleIds: List[Long],
    groupIds: List[Long],
    periodType: PeriodType,
    deliveryChannels: List[String],
    recipients: String,
    exportFormat: ExportFormat,
    templateId: Option[Long]
) derives JsonCodec

/** Запись в истории отчётов */
final case class ReportHistory(
    id: Long,
    organizationId: Long,
    userId: Option[Long],
    scheduledId: Option[Long],
    reportType: ReportType,
    parameters: String,        // JSON
    status: ReportHistoryStatus,
    fileUrl: Option[String],
    fileSize: Option[Long],
    errorMessage: Option[String],
    createdAt: Instant,
    completedAt: Option[Instant],
    expiresAt: Option[Instant]
) derives JsonCodec

// ============================================================
// Агрегированные данные из TimescaleDB
// ============================================================

/** Строка из daily_vehicle_stats */
final case class DailyVehicleStats(
    day: LocalDate,
    deviceId: Long,
    mileageMeters: Double,
    avgSpeed: Double,
    maxSpeed: Double,
    pointCount: Long,
    firstPoint: Instant,
    lastPoint: Instant,
    startLat: Double,
    startLon: Double,
    endLat: Double,
    endLon: Double
)

/** Строка из hourly_fuel_stats */
final case class HourlyFuelStats(
    hour: Instant,
    deviceId: Long,
    startLevel: Double,
    endLevel: Double,
    minLevel: Double,
    maxLevel: Double,
    mileageMeters: Double
)

/** Строка из daily_motion_stats */
final case class DailyMotionStats(
    day: LocalDate,
    deviceId: Long,
    movingSeconds: Double,
    idleSeconds: Double,
    engineSeconds: Double
)
