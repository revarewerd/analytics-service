package com.wayrecall.tracker.analytics.algorithm

import zio.*
import zio.test.*
import zio.test.Assertion.*
import com.wayrecall.tracker.analytics.domain.*
import java.time.Instant

/**
 * Тесты MileageCalculator — расчёт пробега по формуле Haversine.
 *
 * Чистая математика: haversineDistance, calculateMileageKm,
 * calculateMileageByOdometer, calculateBestMileageKm.
 */
object MileageCalculatorSpec extends ZIOSpecDefault:

  val now: Instant = Instant.parse("2025-06-02T12:00:00Z")

  def spec = suite("MileageCalculatorSpec")(
    haversineSuite,
    mileageKmSuite,
    odometerSuite,
    bestMileageSuite
  )

  // ==========================================================
  // haversineDistance
  // ==========================================================

  val haversineSuite = suite("haversineDistance")(
    test("Одна и та же точка → 0 метров") {
      val d = MileageCalculator.haversineDistance(55.75, 37.62, 55.75, 37.62)
      assertTrue(d == 0.0)
    },
    test("Москва — Санкт-Петербург (~635 км)") {
      // Москва: 55.7558, 37.6173
      // Питер:  59.9343, 30.3351
      val d = MileageCalculator.haversineDistance(55.7558, 37.6173, 59.9343, 30.3351)
      val km = d / 1000.0
      // Прямая ~630-640 км
      assertTrue(km > 620.0 && km < 650.0)
    },
    test("Малое расстояние (~100 метров)") {
      // Одна точка чуть от другой: ~0.001 градуса ≈ 100 м
      val d = MileageCalculator.haversineDistance(55.7500, 37.6200, 55.7510, 37.6200)
      // ~111 метров на 0.001° широты
      assertTrue(d > 80.0 && d < 150.0)
    },
    test("Экватор → полюс") {
      val d = MileageCalculator.haversineDistance(0.0, 0.0, 90.0, 0.0)
      val km = d / 1000.0
      // Четверть окружности ~10000 км
      assertTrue(km > 9900.0 && km < 10100.0)
    }
  )

  // ==========================================================
  // calculateMileageKm
  // ==========================================================

  val mileageKmSuite = suite("calculateMileageKm")(
    test("Пустой список → 0") {
      val result = MileageCalculator.calculateMileageKm(Nil)
      assertTrue(result == 0.0)
    },
    test("Одна точка → 0") {
      val points = List(GpsPoint(now, 55.75, 37.62, 60.0, None, None, None))
      val result = MileageCalculator.calculateMileageKm(points)
      assertTrue(result == 0.0)
    },
    test("Две одинаковые точки → 0") {
      val p = GpsPoint(now, 55.75, 37.62, 0.0, None, None, None)
      val result = MileageCalculator.calculateMileageKm(List(p, p))
      assertTrue(result == 0.0)
    },
    test("Три точки — суммирует расстояния между парами") {
      val points = List(
        GpsPoint(now, 55.7500, 37.6200, 60.0, None, None, None),
        GpsPoint(now.plusSeconds(60), 55.7600, 37.6200, 60.0, None, None, None),
        GpsPoint(now.plusSeconds(120), 55.7700, 37.6200, 60.0, None, None, None)
      )
      val result = MileageCalculator.calculateMileageKm(points)
      // Два сегмента по ~1.11 км каждый → ~2.22 км
      assertTrue(result > 2.0 && result < 2.5)
    }
  )

  // ==========================================================
  // calculateMileageByOdometer
  // ==========================================================

  val odometerSuite = suite("calculateMileageByOdometer")(
    test("Нет данных одометра → None") {
      val points = List(
        GpsPoint(now, 55.75, 37.62, 60.0, None, None, None),
        GpsPoint(now.plusSeconds(60), 55.76, 37.62, 60.0, None, None, None)
      )
      val result = MileageCalculator.calculateMileageByOdometer(points)
      assertTrue(result.isEmpty)
    },
    test("Одна точка с одометром → None") {
      val points = List(
        GpsPoint(now, 55.75, 37.62, 60.0, Some(100000.0), None, None)
      )
      val result = MileageCalculator.calculateMileageByOdometer(points)
      assertTrue(result.isEmpty)
    },
    test("Две точки с одометром → разница max-min / 1000") {
      val points = List(
        GpsPoint(now, 55.75, 37.62, 60.0, Some(100000.0), None, None),
        GpsPoint(now.plusSeconds(3600), 55.85, 37.62, 60.0, Some(110000.0), None, None)
      )
      val result = MileageCalculator.calculateMileageByOdometer(points)
      // (110000 - 100000) / 1000 = 10 км
      assertTrue(result == Some(10.0))
    },
    test("Смесь точек с и без одометра — учитывает только с одометром") {
      val points = List(
        GpsPoint(now, 55.75, 37.62, 60.0, Some(50000.0), None, None),
        GpsPoint(now.plusSeconds(60), 55.76, 37.62, 60.0, None, None, None),
        GpsPoint(now.plusSeconds(120), 55.77, 37.62, 60.0, Some(55000.0), None, None)
      )
      val result = MileageCalculator.calculateMileageByOdometer(points)
      // (55000 - 50000) / 1000 = 5 км
      assertTrue(result == Some(5.0))
    }
  )

  // ==========================================================
  // calculateBestMileageKm
  // ==========================================================

  val bestMileageSuite = suite("calculateBestMileageKm")(
    test("Есть одометр → используем одометр") {
      val points = List(
        GpsPoint(now, 55.75, 37.62, 60.0, Some(100000.0), None, None),
        GpsPoint(now.plusSeconds(3600), 55.85, 37.62, 60.0, Some(110000.0), None, None)
      )
      val result = MileageCalculator.calculateBestMileageKm(points)
      // Одометр: (110000 - 100000) / 1000 = 10 км
      assertTrue(result == 10.0)
    },
    test("Нет одометра → используем Haversine") {
      val points = List(
        GpsPoint(now, 55.7500, 37.6200, 60.0, None, None, None),
        GpsPoint(now.plusSeconds(60), 55.7600, 37.6200, 60.0, None, None, None)
      )
      val result = MileageCalculator.calculateBestMileageKm(points)
      // Haversine: ~1.11 км
      assertTrue(result > 1.0 && result < 1.2)
    }
  )
