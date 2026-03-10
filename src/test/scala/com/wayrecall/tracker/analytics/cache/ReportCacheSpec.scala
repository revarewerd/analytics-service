package com.wayrecall.tracker.analytics.cache

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.json.*
import com.wayrecall.tracker.analytics.domain.*
import java.time.Instant

/**
 * Тесты ReportCache — in-memory кеш отчётов.
 *
 * Ref-based: reportCache и exportTasks.
 * Тестируем: get/set, hashParams, export task lifecycle, invalidate.
 */
object ReportCacheSpec extends ZIOSpecDefault:

  val now: Instant = Instant.parse("2025-06-02T12:00:00Z")

  val testLayer: ZLayer[Any, Nothing, ReportCache] = ReportCache.live

  def spec = suite("ReportCacheSpec")(
    hashParamsSuite,
    reportCacheSuite,
    exportTaskSuite
  ).provide(testLayer)

  // ==========================================================
  // hashParams
  // ==========================================================

  val hashParamsSuite = suite("hashParams")(
    test("Одинаковые параметры → одинаковый хеш") {
      val h1 = ReportCache.hashParams("test-params-123")
      val h2 = ReportCache.hashParams("test-params-123")
      assertTrue(h1 == h2)
    },
    test("Разные параметры → разные хеши") {
      val h1 = ReportCache.hashParams("params-a")
      val h2 = ReportCache.hashParams("params-b")
      assertTrue(h1 != h2)
    },
    test("Хеш — строка hex") {
      val h = ReportCache.hashParams("test")
      assertTrue(h.matches("[0-9a-f]+")) &&
      assertTrue(h.length == 32) // MD5 = 32 hex chars
    }
  )

  // ==========================================================
  // Report cache get/set
  // ==========================================================

  val reportCacheSuite = suite("report cache")(
    test("get несуществующего ключа → None") {
      for {
        cache  <- ZIO.service[ReportCache]
        result <- cache.get[String]("nonexistent")
      } yield assertTrue(result.isEmpty)
    },
    test("set и get — JSON roundtrip") {
      val params = ReportParams(
        organizationId = 1L, vehicleIds = List(1L), groupIds = Nil,
        from = now, to = now.plusSeconds(86400),
        reportType = ReportType.Mileage,
        groupBy = None, includeTrips = None, includeEvents = None,
        geozoneIds = None, speedLimit = None
      )
      for {
        cache <- ZIO.service[ReportCache]
        _     <- cache.set("test-key", "cached-report-data", params)
        result <- cache.get[String]("test-key")
      } yield assertTrue(result == Some("cached-report-data"))
    },
    test("invalidate удаляет ключ") {
      val params = ReportParams(
        organizationId = 1L, vehicleIds = List(1L), groupIds = Nil,
        from = now, to = now.plusSeconds(86400),
        reportType = ReportType.Fuel,
        groupBy = None, includeTrips = None, includeEvents = None,
        geozoneIds = None, speedLimit = None
      )
      for {
        cache  <- ZIO.service[ReportCache]
        _      <- cache.set("del-key", "data", params)
        before <- cache.get[String]("del-key")
        _      <- cache.invalidate("del-key")
        after  <- cache.get[String]("del-key")
      } yield assertTrue(before == Some("data")) && assertTrue(after.isEmpty)
    }
  )

  // ==========================================================
  // Export task lifecycle
  // ==========================================================

  val exportTaskSuite = suite("export tasks")(
    test("Несуществующая задача → None") {
      for {
        cache  <- ZIO.service[ReportCache]
        result <- cache.getExportTaskStatus("no-task")
      } yield assertTrue(result.isEmpty)
    },
    test("Создание и получение задачи экспорта") {
      for {
        cache <- ZIO.service[ReportCache]
        _     <- cache.setExportTask("task-1", Map("status" -> "pending", "progress" -> "0"))
        result <- cache.getExportTaskStatus("task-1")
      } yield assertTrue(result.isDefined) &&
             assertTrue(result.get("status") == "pending")
    },
    test("updateExportProgress — обновляет прогресс") {
      for {
        cache <- ZIO.service[ReportCache]
        _     <- cache.setExportTask("task-2", Map("status" -> "processing", "progress" -> "0"))
        _     <- cache.updateExportProgress("task-2", 50)
        result <- cache.getExportTaskStatus("task-2")
      } yield assertTrue(result.get("progress") == "50")
    },
    test("completeExportTask — статус completed, прогресс 100%") {
      for {
        cache <- ZIO.service[ReportCache]
        _     <- cache.setExportTask("task-3", Map("status" -> "processing"))
        _     <- cache.completeExportTask("task-3", "/reports/file.xlsx")
        result <- cache.getExportTaskStatus("task-3")
      } yield assertTrue(result.get("status") == "completed") &&
             assertTrue(result.get("progress") == "100") &&
             assertTrue(result.get("file_url") == "/reports/file.xlsx")
    },
    test("failExportTask — статус failed, ошибка сохранена") {
      for {
        cache <- ZIO.service[ReportCache]
        _     <- cache.setExportTask("task-4", Map("status" -> "processing"))
        _     <- cache.failExportTask("task-4", "Out of memory")
        result <- cache.getExportTaskStatus("task-4")
      } yield assertTrue(result.get("status") == "failed") &&
             assertTrue(result.get("error") == "Out of memory")
    }
  )
