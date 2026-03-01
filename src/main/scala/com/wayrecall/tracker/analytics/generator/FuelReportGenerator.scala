package com.wayrecall.tracker.analytics.generator

import com.wayrecall.tracker.analytics.algorithm.FuelEventDetector
import com.wayrecall.tracker.analytics.domain.*
import com.wayrecall.tracker.analytics.query.QueryEngine
import zio.*
import java.time.{LocalDate, ZoneOffset}

// ============================================================
// FuelReportGenerator — генератор отчёта по топливу
// ============================================================

trait FuelReportGenerator extends ReportGenerator[FuelReport]

object FuelReportGenerator:

  val live: ZLayer[QueryEngine, Nothing, FuelReportGenerator] =
    ZLayer.fromFunction { (queryEngine: QueryEngine) =>
      new FuelReportGeneratorLive(queryEngine)
    }

final class FuelReportGeneratorLive(queryEngine: QueryEngine) extends FuelReportGenerator:

  override def generate(params: ReportParams, vehicleId: Long): Task[FuelReport] =
    for {
      // Получаем почасовые данные о топливе
      hourlyStats <- queryEngine.getHourlyFuelStats(vehicleId, params.from, params.to)
      // Получаем суточные данные о пробеге
      dailyStats  <- queryEngine.getDailyVehicleStats(vehicleId, params.from, params.to)
      // Для детализации событий — raw GPS точки
      gpsPoints   <- if params.includeEvents.getOrElse(true) then
        queryEngine.getGpsPoints(vehicleId, params.from, params.to)
      else ZIO.succeed(Nil)

      // Детектируем события (заправки, сливы)
      events = FuelEventDetector.detectEvents(gpsPoints)
      refuels = events.filter(_.eventType == "refuel")
      drains  = events.filter(_.eventType == "drain")

      // Считаем суточный расход
      dailyConsumption = buildDailyFuel(hourlyStats, dailyStats)

      // Общий расход
      totalConsumed = FuelEventDetector.calculateTotalConsumption(gpsPoints)
      totalRefueled = FuelEventDetector.calculateTotalRefueled(events)
      totalDrained  = FuelEventDetector.calculateTotalDrained(events)
      totalMileageKm = dailyStats.map(_.mileageMeters / 1000.0).sum
      avgConsumption = if totalMileageKm > 0 then totalConsumed / totalMileageKm * 100.0 else 0.0

    } yield FuelReport(
      vehicleId = vehicleId,
      period = DateRange(params.from, params.to),
      totalConsumedLiters = math.round(totalConsumed * 100) / 100.0,
      totalRefueledLiters = math.round(totalRefueled * 100) / 100.0,
      totalDrainedLiters = math.round(totalDrained * 100) / 100.0,
      avgConsumptionPer100km = math.round(avgConsumption * 100) / 100.0,
      refuels = refuels,
      drains = drains,
      dailyConsumption = dailyConsumption
    )

  /** Строит суточный расход из почасовых и суточных агрегатов */
  private def buildDailyFuel(
      hourlyStats: List[HourlyFuelStats],
      dailyStats: List[DailyVehicleStats]
  ): List[DailyFuel] =
    // Группируем часовые данные по дню
    val hourlyByDay = hourlyStats.groupBy { h =>
      h.hour.atOffset(ZoneOffset.UTC).toLocalDate
    }

    dailyStats.map { ds =>
      val dayHours = hourlyByDay.getOrElse(ds.day, Nil)
      val consumed = if dayHours.nonEmpty then
        // Расход = start_level первого часа - end_level последнего часа + заправки
        val sorted = dayHours.sortBy(_.hour)
        val startLevel = sorted.head.startLevel
        val endLevel = sorted.last.endLevel
        math.max(0, startLevel - endLevel)
      else 0.0
      val mileageKm = ds.mileageMeters / 1000.0
      val avgPer100 = if mileageKm > 0 then consumed / mileageKm * 100.0 else 0.0

      DailyFuel(
        date = ds.day,
        consumedLiters = math.round(consumed * 100) / 100.0,
        mileageKm = math.round(mileageKm * 100) / 100.0,
        avgConsumptionPer100km = math.round(avgPer100 * 100) / 100.0
      )
    }
