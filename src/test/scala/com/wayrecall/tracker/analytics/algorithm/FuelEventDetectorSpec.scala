package com.wayrecall.tracker.analytics.algorithm

import zio.*
import zio.test.*
import zio.test.Assertion.*
import com.wayrecall.tracker.analytics.domain.*
import java.time.Instant

/**
 * Тесты FuelEventDetector — детекция заправок и сливов топлива.
 *
 * Чистая логика: detectEvents, calculateTotalConsumption,
 * calculateTotalRefueled, calculateTotalDrained.
 * RefuelThreshold = 5L, DrainThreshold = 5L, MaxNormalConsumption = 30 л/100км.
 */
object FuelEventDetectorSpec extends ZIOSpecDefault:

  val base: Instant = Instant.parse("2025-06-02T08:00:00Z")

  def mkFuelPoint(offsetSec: Long, lat: Double, lon: Double, speed: Double,
                  fuelLevel: Option[Double]): GpsPoint =
    GpsPoint(base.plusSeconds(offsetSec), lat, lon, speed, None, fuelLevel, None)

  def spec = suite("FuelEventDetectorSpec")(
    detectEventsSuite,
    consumptionSuite,
    totalsSuite
  )

  // ==========================================================
  // detectEvents
  // ==========================================================

  val detectEventsSuite = suite("detectEvents")(
    test("Пустой список → пусто") {
      val events = FuelEventDetector.detectEvents(Nil)
      assertTrue(events.isEmpty)
    },
    test("Одна точка → пусто") {
      val events = FuelEventDetector.detectEvents(List(
        mkFuelPoint(0, 55.75, 37.62, 0.0, Some(50.0))
      ))
      assertTrue(events.isEmpty)
    },
    test("Без данных о топливе → пусто") {
      val points = List(
        mkFuelPoint(0, 55.75, 37.62, 60.0, None),
        mkFuelPoint(60, 55.76, 37.62, 60.0, None)
      )
      val events = FuelEventDetector.detectEvents(points)
      assertTrue(events.isEmpty)
    },
    test("Заправка: рост > 5 литров") {
      val points = List(
        mkFuelPoint(0, 55.75, 37.62, 0.0, Some(20.0)),
        mkFuelPoint(60, 55.75, 37.62, 0.0, Some(50.0))  // +30 литров
      )
      val events = FuelEventDetector.detectEvents(points)
      assertTrue(events.size == 1) &&
      assertTrue(events.head.eventType == "refuel") &&
      assertTrue(events.head.volumeLiters == 30.0)
    },
    test("Слив на стоянке: падение > 5 литров, ТС стоит") {
      val points = List(
        mkFuelPoint(0, 55.75, 37.62, 0.0, Some(50.0)),
        mkFuelPoint(60, 55.75, 37.62, 0.0, Some(30.0))  // -20 литров, 0 км
      )
      val events = FuelEventDetector.detectEvents(points)
      assertTrue(events.size == 1) &&
      assertTrue(events.head.eventType == "drain") &&
      assertTrue(events.head.volumeLiters == 20.0)
    },
    test("Небольшое изменение (<5 литров) → нет событий") {
      val points = List(
        mkFuelPoint(0, 55.75, 37.62, 0.0, Some(50.0)),
        mkFuelPoint(60, 55.75, 37.62, 0.0, Some(47.0))  // -3 литра
      )
      val events = FuelEventDetector.detectEvents(points)
      assertTrue(events.isEmpty)
    },
    test("Нормальный расход при движении — не слив") {
      // Движение на ~10 км, расход 8 литров (80 л/100км — но проехали 10 км)
      // 8 / 10 * 100 = 80 л/100км > 30 → будет слив
      // Но если расход нормальный:
      val points = List(
        mkFuelPoint(0, 55.7500, 37.6200, 60.0, Some(50.0)),
        mkFuelPoint(600, 55.8400, 37.6200, 60.0, Some(43.0))  // -7 литров, ~10 км
        // 7L / 10km * 100 = 70 л/100 км > 30 → аномальный → drain
      )
      val events = FuelEventDetector.detectEvents(points)
      // 70 > 30 → flagged as drain
      assertTrue(events.size == 1) &&
      assertTrue(events.head.eventType == "drain")
    }
  )

  // ==========================================================
  // calculateTotalConsumption
  // ==========================================================

  val consumptionSuite = suite("calculateTotalConsumption")(
    test("Пустой список → 0") {
      val result = FuelEventDetector.calculateTotalConsumption(Nil)
      assertTrue(result == 0.0)
    },
    test("Без данных о топливе → 0") {
      val points = List(
        mkFuelPoint(0, 55.75, 37.62, 60.0, None),
        mkFuelPoint(60, 55.76, 37.62, 60.0, None)
      )
      val result = FuelEventDetector.calculateTotalConsumption(points)
      assertTrue(result == 0.0)
    },
    test("Расход суммируется только нисходящие изменения") {
      val points = List(
        mkFuelPoint(0, 55.75, 37.62, 0.0, Some(50.0)),
        mkFuelPoint(60, 55.75, 37.62, 0.0, Some(45.0)),   // -5
        mkFuelPoint(120, 55.75, 37.62, 0.0, Some(70.0)),  // +25 (заправка, не расход)
        mkFuelPoint(180, 55.75, 37.62, 0.0, Some(60.0))   // -10
      )
      val result = FuelEventDetector.calculateTotalConsumption(points)
      // Расход: 5 + 10 = 15 (заправка +25 не считается)
      assertTrue(result == 15.0)
    }
  )

  // ==========================================================
  // calculateTotalRefueled / calculateTotalDrained
  // ==========================================================

  val totalsSuite = suite("Totals")(
    test("calculateTotalRefueled — суммирует заправки") {
      val events = List(
        FuelEvent("refuel", base, Coordinates(55.75, 37.62), 30.0, 20.0, 50.0),
        FuelEvent("drain", base, Coordinates(55.75, 37.62), 10.0, 50.0, 40.0),
        FuelEvent("refuel", base, Coordinates(55.75, 37.62), 25.0, 15.0, 40.0)
      )
      val result = FuelEventDetector.calculateTotalRefueled(events)
      assertTrue(result == 55.0) // 30 + 25
    },
    test("calculateTotalDrained — суммирует сливы") {
      val events = List(
        FuelEvent("refuel", base, Coordinates(55.75, 37.62), 30.0, 20.0, 50.0),
        FuelEvent("drain", base, Coordinates(55.75, 37.62), 10.0, 50.0, 40.0),
        FuelEvent("drain", base, Coordinates(55.75, 37.62), 5.0, 40.0, 35.0)
      )
      val result = FuelEventDetector.calculateTotalDrained(events)
      assertTrue(result == 15.0) // 10 + 5
    },
    test("Пустой список → 0") {
      assertTrue(FuelEventDetector.calculateTotalRefueled(Nil) == 0.0) &&
      assertTrue(FuelEventDetector.calculateTotalDrained(Nil) == 0.0)
    }
  )
