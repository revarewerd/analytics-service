package com.wayrecall.tracker.analytics.generator

import com.wayrecall.tracker.analytics.domain.*
import com.wayrecall.tracker.analytics.query.QueryEngine
import zio.*
import java.time.Duration

// ============================================================
// SpeedReportGenerator — генератор отчёта по превышениям скорости
// ============================================================

trait SpeedReportGenerator extends ReportGenerator[SpeedViolationsReport]

object SpeedReportGenerator:

  /** Лимит скорости по умолчанию (км/ч) */
  val DefaultSpeedLimit = 90

  val live: ZLayer[QueryEngine, Nothing, SpeedReportGenerator] =
    ZLayer.fromFunction { (queryEngine: QueryEngine) =>
      new SpeedReportGeneratorLive(queryEngine)
    }

final class SpeedReportGeneratorLive(queryEngine: QueryEngine) extends SpeedReportGenerator:

  override def generate(params: ReportParams, vehicleId: Long): Task[SpeedViolationsReport] =
    val limit = params.speedLimit.getOrElse(SpeedReportGenerator.DefaultSpeedLimit).toDouble

    for {
      // Получаем точки с превышением скорости
      points <- queryEngine.getSpeedExceedingPoints(vehicleId, params.from, params.to, limit)

      // Группируем последовательные превышения в одно нарушение
      violations = groupViolations(points, limit)

    } yield SpeedViolationsReport(
      vehicleId = vehicleId,
      period = DateRange(params.from, params.to),
      speedLimit = limit,
      totalViolations = violations.size,
      violations = violations
    )

  /**
   * Группирует последовательные точки превышения в одно нарушение.
   * Если между точками < 60 секунд — это одно и то же нарушение.
   */
  private def groupViolations(points: List[GpsPoint], limit: Double): List[SpeedViolation] =
    if points.isEmpty then return Nil

    var violations = List.empty[SpeedViolation]
    var currentGroup = List(points.head)

    points.tail.foreach { point =>
      val lastPoint = currentGroup.last
      val gap = Duration.between(lastPoint.timestamp, point.timestamp).getSeconds

      if gap <= 60 then
        // Продолжение текущего нарушения
        currentGroup = currentGroup :+ point
      else
        // Новое нарушение — завершаем текущее
        violations = violations :+ buildViolation(currentGroup, limit)
        currentGroup = List(point)
    }

    // Последнее нарушение
    if currentGroup.nonEmpty then
      violations = violations :+ buildViolation(currentGroup, limit)

    violations

  /** Строит SpeedViolation из группы точек */
  private def buildViolation(points: List[GpsPoint], limit: Double): SpeedViolation =
    val maxSpeedPoint = points.maxBy(_.speed)
    val duration = if points.size > 1 then
      Duration.between(points.head.timestamp, points.last.timestamp).getSeconds
    else 0L

    SpeedViolation(
      timestamp = maxSpeedPoint.timestamp,
      coords = Coordinates(maxSpeedPoint.latitude, maxSpeedPoint.longitude),
      actualSpeed = maxSpeedPoint.speed,
      speedLimit = limit,
      overspeedKmh = math.round((maxSpeedPoint.speed - limit) * 100) / 100.0,
      durationSeconds = duration
    )
