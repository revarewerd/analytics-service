package com.wayrecall.tracker.analytics.domain

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.json.*
import java.time.Instant

/**
 * Тесты доменных моделей Analytics Service
 *
 * Покрываем:
 * - Enums (ReportType, ExportFormat, ExportStatus, PeriodType)
 * - AnalyticsError sealed trait (все 15 подтипов)
 * - GpsPoint, DateRange, Coordinates
 */
object DomainSpec extends ZIOSpecDefault:

  val now: Instant = Instant.parse("2025-06-02T12:00:00Z")

  def spec = suite("DomainSpec")(
    reportTypeSuite,
    exportFormatSuite,
    exportStatusSuite,
    periodTypeSuite,
    modelsSuite,
    errorsSuite
  )

  // ==========================================================
  // ReportType
  // ==========================================================

  val reportTypeSuite = suite("ReportType")(
    test("Все 6 типов отчётов") {
      val types = ReportType.values.toList
      assertTrue(types.size == 6) &&
      assertTrue(types.contains(ReportType.Mileage)) &&
      assertTrue(types.contains(ReportType.Fuel)) &&
      assertTrue(types.contains(ReportType.Geozones)) &&
      assertTrue(types.contains(ReportType.Idle)) &&
      assertTrue(types.contains(ReportType.SpeedViolations)) &&
      assertTrue(types.contains(ReportType.Summary))
    },
    test("ReportType JSON roundtrip (lowercase)") {
      val encoded = ReportType.Mileage.toJson
      assertTrue(encoded.contains("mileage")) &&
      assertTrue("\"mileage\"".fromJson[ReportType] == Right(ReportType.Mileage))
    }
  )

  // ==========================================================
  // ExportFormat
  // ==========================================================

  val exportFormatSuite = suite("ExportFormat")(
    test("Все 3 формата экспорта") {
      val formats = ExportFormat.values.toList
      assertTrue(formats.size == 3) &&
      assertTrue(formats.contains(ExportFormat.Xlsx)) &&
      assertTrue(formats.contains(ExportFormat.Pdf)) &&
      assertTrue(formats.contains(ExportFormat.Csv))
    }
  )

  // ==========================================================
  // ExportStatus
  // ==========================================================

  val exportStatusSuite = suite("ExportStatus")(
    test("Все 4 статуса экспорта") {
      val statuses = ExportStatus.values.toList
      assertTrue(statuses.size == 4) &&
      assertTrue(statuses.contains(ExportStatus.Pending)) &&
      assertTrue(statuses.contains(ExportStatus.Processing)) &&
      assertTrue(statuses.contains(ExportStatus.Completed)) &&
      assertTrue(statuses.contains(ExportStatus.Failed))
    }
  )

  // ==========================================================
  // PeriodType
  // ==========================================================

  val periodTypeSuite = suite("PeriodType")(
    test("Все 4 типа периода") {
      val types = PeriodType.values.toList
      assertTrue(types.size == 4) &&
      assertTrue(types.contains(PeriodType.Yesterday)) &&
      assertTrue(types.contains(PeriodType.LastWeek)) &&
      assertTrue(types.contains(PeriodType.LastMonth)) &&
      assertTrue(types.contains(PeriodType.Custom))
    },
    test("PeriodType JSON roundtrip") {
      val encoded = PeriodType.Yesterday.toJson
      assertTrue(encoded.contains("yesterday")) &&
      assertTrue("\"last_week\"".fromJson[PeriodType] == Right(PeriodType.LastWeek))
    }
  )

  // ==========================================================
  // Models
  // ==========================================================

  val modelsSuite = suite("Models")(
    test("GpsPoint с опциональными полями") {
      val p = GpsPoint(now, 55.75, 37.62, 60.0, Some(123456.0), Some(45.5), Some(true))
      assertTrue(p.latitude == 55.75) &&
      assertTrue(p.odometer == Some(123456.0)) &&
      assertTrue(p.fuelLevel == Some(45.5)) &&
      assertTrue(p.ignition == Some(true))
    },
    test("GpsPoint без опциональных полей") {
      val p = GpsPoint(now, 55.75, 37.62, 0.0, None, None, None)
      assertTrue(p.odometer.isEmpty) &&
      assertTrue(p.fuelLevel.isEmpty)
    },
    test("DateRange JSON roundtrip") {
      val range = DateRange(now, now.plusSeconds(3600))
      val json = range.toJson
      val parsed = json.fromJson[DateRange]
      assertTrue(parsed == Right(range))
    },
    test("Coordinates") {
      val c = Coordinates(55.7558, 37.6173)
      assertTrue(c.latitude == 55.7558) &&
      assertTrue(c.longitude == 37.6173)
    }
  )

  // ==========================================================
  // Errors — все 15 подтипов
  // ==========================================================

  val errorsSuite = suite("AnalyticsError")(
    test("VehicleNotFound") {
      val err = AnalyticsError.VehicleNotFound(42L)
      assertTrue(err.getMessage.contains("42"))
    },
    test("OrganizationNotFound") {
      val err = AnalyticsError.OrganizationNotFound(1L)
      assertTrue(err.getMessage.contains("1"))
    },
    test("InvalidDateRange") {
      val err = AnalyticsError.InvalidDateRange("from > to")
      assertTrue(err.getMessage.contains("from > to"))
    },
    test("InvalidParameters") {
      val err = AnalyticsError.InvalidParameters("vehicleIds пуст")
      assertTrue(err.getMessage.contains("vehicleIds"))
    },
    test("TemplateNotFound") {
      val err = AnalyticsError.TemplateNotFound(10L)
      assertTrue(err.getMessage.contains("10"))
    },
    test("ScheduleNotFound") {
      val err = AnalyticsError.ScheduleNotFound(5L)
      assertTrue(err.getMessage.contains("5"))
    },
    test("ExportTaskNotFound") {
      val err = AnalyticsError.ExportTaskNotFound("task-123")
      assertTrue(err.getMessage.contains("task-123"))
    },
    test("FileNotFound") {
      val err = AnalyticsError.FileNotFound("/reports/file.xlsx")
      assertTrue(err.getMessage.contains("file.xlsx"))
    },
    test("ReportGenerationError") {
      val err = AnalyticsError.ReportGenerationError("mileage", "timeout")
      assertTrue(err.getMessage.contains("mileage")) &&
      assertTrue(err.getMessage.contains("timeout"))
    },
    test("ExportError") {
      val err = AnalyticsError.ExportError("pdf", "out of memory")
      assertTrue(err.getMessage.contains("pdf"))
    },
    test("StorageError") {
      val err = AnalyticsError.StorageError("upload", "S3 unreachable")
      assertTrue(err.getMessage.contains("upload"))
    },
    test("CacheError") {
      val err = AnalyticsError.CacheError("Redis timeout")
      assertTrue(err.getMessage.contains("Redis"))
    },
    test("DatabaseError") {
      val err = AnalyticsError.DatabaseError("Connection pool exhausted")
      assertTrue(err.getMessage.contains("Connection"))
    },
    test("ExportLimitExceeded") {
      val err = AnalyticsError.ExportLimitExceeded(5)
      assertTrue(err.getMessage.contains("5"))
    },
    test("InvalidCronExpression") {
      val err = AnalyticsError.InvalidCronExpression("* * *", "wrong format")
      assertTrue(err.getMessage.contains("* * *"))
    },
    test("Pattern matching — все 15 подтипов исчерпывающий") {
      val err: AnalyticsError = AnalyticsError.VehicleNotFound(1L)
      val result = err match
        case _: AnalyticsError.VehicleNotFound => "vehicle"
        case _: AnalyticsError.OrganizationNotFound => "org"
        case _: AnalyticsError.InvalidDateRange => "date"
        case _: AnalyticsError.InvalidParameters => "params"
        case _: AnalyticsError.TemplateNotFound => "template"
        case _: AnalyticsError.ScheduleNotFound => "schedule"
        case _: AnalyticsError.ExportTaskNotFound => "export_task"
        case _: AnalyticsError.FileNotFound => "file"
        case _: AnalyticsError.ReportGenerationError => "generation"
        case _: AnalyticsError.ExportError => "export"
        case _: AnalyticsError.StorageError => "storage"
        case _: AnalyticsError.CacheError => "cache"
        case _: AnalyticsError.DatabaseError => "db"
        case _: AnalyticsError.ExportLimitExceeded => "limit"
        case _: AnalyticsError.InvalidCronExpression => "cron"
      assertTrue(result == "vehicle")
    },
    test("AnalyticsError extends Exception") {
      val err: AnalyticsError = AnalyticsError.DatabaseError("test")
      val t: Throwable = err
      assertTrue(t.getMessage.contains("test"))
    }
  )
