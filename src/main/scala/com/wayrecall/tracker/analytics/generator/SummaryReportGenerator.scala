package com.wayrecall.tracker.analytics.generator

import com.wayrecall.tracker.analytics.domain.*
import com.wayrecall.tracker.analytics.query.QueryEngine
import zio.*

// ============================================================
// SummaryReportGenerator — генератор сводного отчёта по организации
// ============================================================

trait SummaryReportGenerator:
  /** Генерация сводного отчёта по всем ТС организации */
  def generate(params: ReportParams, vehicleIds: List[Long]): Task[SummaryReport]

object SummaryReportGenerator:

  val live: ZLayer[QueryEngine, Nothing, SummaryReportGenerator] =
    ZLayer.fromFunction { (queryEngine: QueryEngine) =>
      new SummaryReportGeneratorLive(queryEngine)
    }

final class SummaryReportGeneratorLive(queryEngine: QueryEngine) extends SummaryReportGenerator:

  override def generate(params: ReportParams, vehicleIds: List[Long]): Task[SummaryReport] =
    for {
      _ <- ZIO.logInfo(s"Отчёт СВОДНЫЙ: org=${params.organizationId}, ТС=${vehicleIds.size}, период=${params.from}..${params.to}")
      // Генерируем сводку по каждому ТС
      vehicleSummaries <- ZIO.foreachPar(vehicleIds) { vehicleId =>
        generateVehicleSummary(params, vehicleId)
      }
      _ <- ZIO.logDebug(s"Отчёт СВОДНЫЙ: org=${params.organizationId} — обработано ${vehicleSummaries.size} ТС")

      // Считаем итоги по организации
      totalMileage     = vehicleSummaries.map(_.mileageKm).sum
      totalFuel        = vehicleSummaries.map(_.fuelConsumedLiters).sum
      totalEngineHours = vehicleSummaries.map(_.engineHours).sum

    } yield SummaryReport(
      organizationId = params.organizationId,
      period = DateRange(params.from, params.to),
      totalVehicles = vehicleSummaries.size,
      totalMileageKm = math.round(totalMileage * 100) / 100.0,
      totalFuelConsumedLiters = math.round(totalFuel * 100) / 100.0,
      totalEngineHours = math.round(totalEngineHours * 100) / 100.0,
      vehicles = vehicleSummaries
    )

  /** Генерация сводки по одному ТС */
  private def generateVehicleSummary(params: ReportParams, vehicleId: Long): Task[VehicleSummary] =
    for {
      // Суточные агрегаты
      dailyStats  <- queryEngine.getDailyVehicleStats(vehicleId, params.from, params.to)
      motionStats <- queryEngine.getDailyMotionStats(vehicleId, params.from, params.to)

      // Пробег
      mileageKm = dailyStats.map(_.mileageMeters / 1000.0).sum
      maxSpeed  = if dailyStats.nonEmpty then dailyStats.map(_.maxSpeed).max else 0.0

      // Моточасы
      engineHours = motionStats.map(_.engineSeconds / 3600.0).sum

      // Простой
      idleMinutes = motionStats.map(_.idleSeconds / 60.0).sum

      // Кол-во поездок (приблизительно: по кол-ву дней с данными)
      tripCount = dailyStats.count(_.mileageMeters > 100)

      // Кол-во превышений скорости (приблизительно: дни с maxSpeed > 90)
      speedViolations = dailyStats.count(_.maxSpeed > 90)

    } yield VehicleSummary(
      vehicleId = vehicleId,
      vehicleName = s"Vehicle-$vehicleId", // TODO: получить имя из Device Manager
      mileageKm = math.round(mileageKm * 100) / 100.0,
      fuelConsumedLiters = 0.0, // TODO: добавить данные о топливе из hourly_fuel_stats
      engineHours = math.round(engineHours * 100) / 100.0,
      maxSpeed = maxSpeed,
      speedViolations = speedViolations,
      tripCount = tripCount,
      idleMinutes = math.round(idleMinutes * 100) / 100.0
    )
