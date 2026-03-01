package com.wayrecall.tracker.analytics.generator

import com.wayrecall.tracker.analytics.algorithm.{MileageCalculator, TripDetector}
import com.wayrecall.tracker.analytics.domain.*
import com.wayrecall.tracker.analytics.query.QueryEngine
import zio.*

// ============================================================
// MileageReportGenerator — генератор отчёта по пробегу
// ============================================================

trait MileageReportGenerator extends ReportGenerator[MileageReport]

object MileageReportGenerator:

  val live: ZLayer[QueryEngine, Nothing, MileageReportGenerator] =
    ZLayer.fromFunction { (queryEngine: QueryEngine) =>
      new MileageReportGeneratorLive(queryEngine)
    }

final class MileageReportGeneratorLive(queryEngine: QueryEngine) extends MileageReportGenerator:

  override def generate(params: ReportParams, vehicleId: Long): Task[MileageReport] =
    for {
      // Получаем суточные агрегаты из continuous aggregates
      dailyStats <- queryEngine.getDailyVehicleStats(vehicleId, params.from, params.to)
      // Получаем статистику движения для моточасов
      motionStats <- queryEngine.getDailyMotionStats(vehicleId, params.from, params.to)

      // Конвертируем в DailyMileage
      dailyData = dailyStats.map { ds =>
        val motion = motionStats.find(_.day == ds.day)
        DailyMileage(
          date = ds.day,
          mileageKm = math.round(ds.mileageMeters / 1000.0 * 100) / 100.0,
          avgSpeed = math.round(ds.avgSpeed * 100) / 100.0,
          maxSpeed = ds.maxSpeed,
          engineHours = motion.map(_.engineSeconds / 3600.0).getOrElse(0.0),
          pointCount = ds.pointCount
        )
      }

      // Считаем итоги
      totalMileageKm   = dailyData.map(_.mileageKm).sum
      totalEngineHours = dailyData.map(_.engineHours).sum
      avgSpeed         = if dailyData.nonEmpty then dailyData.map(_.avgSpeed).sum / dailyData.size else 0.0
      maxSpeed         = if dailyData.nonEmpty then dailyData.map(_.maxSpeed).max else 0.0

      // Детектируем поездки (если запрошено)
      trips <- if params.includeTrips.getOrElse(false) then
        queryEngine.getGpsPoints(vehicleId, params.from, params.to)
          .map(TripDetector.detectTrips)
      else ZIO.succeed(Nil)

    } yield MileageReport(
      vehicleId = vehicleId,
      period = DateRange(params.from, params.to),
      totalMileageKm = math.round(totalMileageKm * 100) / 100.0,
      totalEngineHours = math.round(totalEngineHours * 100) / 100.0,
      averageSpeed = math.round(avgSpeed * 100) / 100.0,
      maxSpeed = maxSpeed,
      dailyData = dailyData,
      trips = trips
    )
