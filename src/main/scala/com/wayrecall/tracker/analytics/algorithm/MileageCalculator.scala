package com.wayrecall.tracker.analytics.algorithm

import com.wayrecall.tracker.analytics.domain.*
import scala.math.*

// ============================================================
// MileageCalculator — расчёт пробега по формуле Haversine
// ============================================================

object MileageCalculator:

  /** Радиус Земли в метрах */
  private val EarthRadiusMeters = 6_371_000.0

  /**
   * Расстояние между двумя координатами по формуле Haversine.
   * Возвращает расстояние в метрах.
   */
  def haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
    val dLat = toRadians(lat2 - lat1)
    val dLon = toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
      cos(toRadians(lat1)) * cos(toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    EarthRadiusMeters * c

  /**
   * Расчёт пробега по последовательности GPS точек.
   * Суммирует расстояние между соседними точками.
   * Возвращает пробег в километрах.
   */
  def calculateMileageKm(points: List[GpsPoint]): Double =
    if points.size < 2 then 0.0
    else
      val totalMeters = points.sliding(2).foldLeft(0.0) { case (acc, pair) =>
        pair match
          case p1 :: p2 :: Nil =>
            acc + haversineDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
          case _ => acc
      }
      totalMeters / 1000.0

  /**
   * Расчёт пробега по одометру (если доступен).
   * Разница между макс. и мин. значениями одометра.
   * Возвращает пробег в километрах.
   */
  def calculateMileageByOdometer(points: List[GpsPoint]): Option[Double] =
    val odometerValues = points.flatMap(_.odometer)
    if odometerValues.size < 2 then None
    else
      val min = odometerValues.min
      val max = odometerValues.max
      // Одометр в метрах → конвертируем в км
      Some((max - min) / 1000.0)

  /**
   * Выбирает лучший метод расчёта:
   * - если есть одометр → по одометру (точнее)
   * - иначе → по Haversine
   */
  def calculateBestMileageKm(points: List[GpsPoint]): Double =
    calculateMileageByOdometer(points).getOrElse(calculateMileageKm(points))
