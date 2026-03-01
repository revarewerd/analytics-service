package com.wayrecall.tracker.analytics.generator

import com.wayrecall.tracker.analytics.algorithm.TripDetector
import com.wayrecall.tracker.analytics.domain.*
import com.wayrecall.tracker.analytics.query.QueryEngine
import zio.*

// ============================================================
// IdleReportGenerator — генератор отчёта по простою
// ============================================================

trait IdleReportGenerator extends ReportGenerator[IdleReport]

object IdleReportGenerator:

  val live: ZLayer[QueryEngine, Nothing, IdleReportGenerator] =
    ZLayer.fromFunction { (queryEngine: QueryEngine) =>
      new IdleReportGeneratorLive(queryEngine)
    }

final class IdleReportGeneratorLive(queryEngine: QueryEngine) extends IdleReportGenerator:

  override def generate(params: ReportParams, vehicleId: Long): Task[IdleReport] =
    for {
      // Получаем суточную статистику движения
      motionStats <- queryEngine.getDailyMotionStats(vehicleId, params.from, params.to)
      // Для детализации стоянок — raw GPS точки
      gpsPoints   <- queryEngine.getGpsPoints(vehicleId, params.from, params.to)

      // Детектируем стоянки
      parkings = TripDetector.detectParkings(gpsPoints)

      // Считаем итоги из агрегатов
      totalIdleMinutes       = motionStats.map(_.idleSeconds).sum / 60.0
      totalEngineIdleMinutes = motionStats.map { ds =>
        // Время с работающим двигателем на стоянке = engine_seconds - moving_seconds
        math.max(0, ds.engineSeconds - ds.movingSeconds) / 60.0
      }.sum

    } yield IdleReport(
      vehicleId = vehicleId,
      period = DateRange(params.from, params.to),
      totalIdleMinutes = math.round(totalIdleMinutes * 100) / 100.0,
      totalIdleWithEngineMinutes = math.round(totalEngineIdleMinutes * 100) / 100.0,
      totalParkings = parkings.size,
      parkings = parkings
    )
