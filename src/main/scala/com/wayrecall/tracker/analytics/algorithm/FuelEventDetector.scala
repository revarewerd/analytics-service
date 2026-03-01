package com.wayrecall.tracker.analytics.algorithm

import com.wayrecall.tracker.analytics.domain.*
import java.time.{Duration, Instant}

// ============================================================
// FuelEventDetector — детекция заправок и сливов топлива
// ============================================================

object FuelEventDetector:

  /** Порог заправки: резкое увеличение уровня топлива (литры) */
  private val RefuelThresholdLiters = 5.0

  /** Порог слива: резкое уменьшение уровня топлива (литры) */
  private val DrainThresholdLiters = 5.0

  /** Максимальный расход на 100 км (л) для отличия слива от расхода */
  private val MaxNormalConsumptionPer100km = 30.0

  /**
   * Детектирует заправки и сливы по последовательности GPS точек.
   *
   * Алгоритм:
   * 1. Для каждой пары соседних точек с данными о топливе
   * 2. Если уровень резко вырос (> RefuelThreshold) → заправка
   * 3. Если уровень резко упал (> DrainThreshold) и расход аномальный → слив
   * 4. Нормальный расход (пропорциональный пробегу) — не слив
   */
  def detectEvents(points: List[GpsPoint]): List[FuelEvent] =
    // Фильтруем точки с данными о топливе
    val fuelPoints = points.filter(_.fuelLevel.isDefined)
    if fuelPoints.size < 2 then return Nil

    fuelPoints.sliding(2).flatMap { pair =>
      pair match
        case p1 :: p2 :: Nil =>
          val level1 = p1.fuelLevel.getOrElse(0.0)
          val level2 = p2.fuelLevel.getOrElse(0.0)
          val diff   = level2 - level1

          if diff > RefuelThresholdLiters then
            // Заправка: резкий рост уровня
            Some(FuelEvent(
              eventType = "refuel",
              timestamp = p2.timestamp,
              coords = Coordinates(p2.latitude, p2.longitude),
              volumeLiters = math.round(diff * 100.0) / 100.0,
              levelBefore = level1,
              levelAfter = level2
            ))
          else if diff < -DrainThresholdLiters then
            // Проверяем: это слив или нормальный расход?
            val distanceKm = MileageCalculator.haversineDistance(
              p1.latitude, p1.longitude, p2.latitude, p2.longitude
            ) / 1000.0
            val absDiff = math.abs(diff)

            if distanceKm < 0.1 || (distanceKm > 0 && absDiff / distanceKm * 100 > MaxNormalConsumptionPer100km) then
              // Слив: ТС стоит или расход аномально высокий
              Some(FuelEvent(
                eventType = "drain",
                timestamp = p2.timestamp,
                coords = Coordinates(p2.latitude, p2.longitude),
                volumeLiters = math.round(absDiff * 100.0) / 100.0,
                levelBefore = level1,
                levelAfter = level2
              ))
            else None // Нормальный расход
          else None // Несущественное изменение
        case _ => None
    }.toList

  /**
   * Считает общий расход топлива за период.
   * Суммирует только нисходящие изменения уровня (нормальный расход + сливы).
   */
  def calculateTotalConsumption(points: List[GpsPoint]): Double =
    val fuelPoints = points.filter(_.fuelLevel.isDefined)
    if fuelPoints.size < 2 then return 0.0

    fuelPoints.sliding(2).foldLeft(0.0) { case (acc, pair) =>
      pair match
        case p1 :: p2 :: Nil =>
          val level1 = p1.fuelLevel.getOrElse(0.0)
          val level2 = p2.fuelLevel.getOrElse(0.0)
          val diff   = level1 - level2 // Расход = предыдущий - текущий
          if diff > 0 then acc + diff else acc
        case _ => acc
    }

  /**
   * Считает суммарную заправку за период.
   */
  def calculateTotalRefueled(events: List[FuelEvent]): Double =
    events.filter(_.eventType == "refuel").map(_.volumeLiters).sum

  /**
   * Считает суммарный слив за период.
   */
  def calculateTotalDrained(events: List[FuelEvent]): Double =
    events.filter(_.eventType == "drain").map(_.volumeLiters).sum
