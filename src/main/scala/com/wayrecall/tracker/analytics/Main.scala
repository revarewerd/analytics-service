package com.wayrecall.tracker.analytics

import com.wayrecall.tracker.analytics.api.*
import com.wayrecall.tracker.analytics.cache.ReportCache
import com.wayrecall.tracker.analytics.config.AppConfig
import com.wayrecall.tracker.analytics.export.ExportService
import com.wayrecall.tracker.analytics.generator.*
import com.wayrecall.tracker.analytics.infrastructure.{TransactorLayer, TransactorTags}
import com.wayrecall.tracker.analytics.query.QueryEngine
import com.wayrecall.tracker.analytics.repository.*
import com.wayrecall.tracker.analytics.scheduler.ReportScheduler
import zio.*
import zio.http.*
import zio.logging.backend.SLF4J

// ============================================================
// Main — точка входа Analytics Service (порт 8095)
// Собирает все ZIO Layer и запускает HTTP сервер + планировщик
// ============================================================

object Main extends ZIOAppDefault:

  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  override def run: ZIO[Any, Any, Any] =
    val program = for {
      config    <- ZIO.service[AppConfig]
      _         <- ZIO.logInfo(s"=== Analytics Service запускается на порту ${config.server.port} ===")

      // Запускаем планировщик в фоне
      scheduler <- ZIO.service[ReportScheduler]
      _         <- scheduler.start

      // Собираем все маршруты
      allRoutes  = HealthRoutes.routes ++
                   ReportRoutes.routes ++
                   ExportRoutes.routes ++
                   ScheduledRoutes.routes

      // Запускаем HTTP-сервер
      _         <- Server.serve(allRoutes)
    } yield ()

    program.provide(
      // Конфигурация
      AppConfig.live,

      // Транзакторы БД
      TransactorLayer.timescale,
      TransactorLayer.postgres,

      // Redis
      zio.redis.Redis.local,
      zio.redis.RedisExecutor.local,
      zio.redis.CodecSupplier.utf8,

      // Репозитории
      ReportTemplateRepository.live,
      ScheduledReportRepository.live,
      ReportHistoryRepository.live,

      // Кэш
      ReportCache.live,

      // Query Engine
      QueryEngine.live,

      // Генераторы отчётов
      MileageReportGenerator.live,
      FuelReportGenerator.live,
      GeozoneReportGenerator.live,
      IdleReportGenerator.live,
      SpeedReportGenerator.live,
      SummaryReportGenerator.live,

      // Экспорт
      ExportService.live,

      // Планировщик
      ReportScheduler.live,

      // Конфиг-слои для компонентов
      ZLayer.service[AppConfig].flatMap(env =>
        ZLayer.succeed(env.get.export) ++
        ZLayer.succeed(env.get.s3) ++
        ZLayer.succeed(env.get.scheduler) ++
        ZLayer.succeed(env.get.cache) ++
        ZLayer.succeed(env.get.server)
      ),

      // HTTP-сервер
      Server.defaultWithPort(8095)
    )
