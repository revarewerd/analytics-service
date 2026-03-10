package com.wayrecall.tracker.analytics.algorithm

import zio.*
import zio.test.*
import zio.test.Assertion.*
import com.wayrecall.tracker.analytics.domain.*
import java.time.Instant

/**
 * Тесты TripDetector — детекция поездок и стоянок.
 *
 * Чистая логика: splitIntoSegments, detectTrips, detectParkings.
 * SpeedThreshold = 3.0 км/ч, MinParkingDuration = 300 сек, MinTripDistance = 0.1 км.
 */
object TripDetectorSpec extends ZIOSpecDefault:

  val base: Instant = Instant.parse("2025-06-02T08:00:00Z")

  def mkPoint(offsetSec: Long, lat: Double, lon: Double, speed: Double,
              ignition: Option[Boolean] = None): GpsPoint =
    GpsPoint(base.plusSeconds(offsetSec), lat, lon, speed, None, None, ignition)

  def spec = suite("TripDetectorSpec")(
    detectTripsSuite,
    detectParkingsSuite,
    edgeCasesSuite
  )

  // ==========================================================
  // Детекция поездок
  // ==========================================================

  val detectTripsSuite = suite("detectTrips")(
    test("Пустой список → пусто") {
      val trips = TripDetector.detectTrips(Nil)
      assertTrue(trips.isEmpty)
    },
    test("Одна точка → пусто") {
      val trips = TripDetector.detectTrips(List(mkPoint(0, 55.75, 37.62, 60.0)))
      assertTrue(trips.isEmpty)
    },
    test("Все точки стоят (speed <= 3) → нет поездок") {
      val points = List(
        mkPoint(0, 55.75, 37.62, 0.0),
        mkPoint(60, 55.75, 37.62, 1.0),
        mkPoint(120, 55.75, 37.62, 2.0),
        mkPoint(180, 55.75, 37.62, 0.0)
      )
      val trips = TripDetector.detectTrips(points)
      assertTrue(trips.isEmpty)
    },
    test("Одна поездка — все точки в движении") {
      // Разные координаты с достаточным расстоянием (>0.1 км)
      val points = List(
        mkPoint(0, 55.7500, 37.6200, 40.0),
        mkPoint(60, 55.7520, 37.6200, 50.0),
        mkPoint(120, 55.7540, 37.6200, 45.0),
        mkPoint(180, 55.7560, 37.6200, 40.0)
      )
      val trips = TripDetector.detectTrips(points)
      assertTrue(trips.size == 1) &&
      assertTrue(trips.head.distanceKm > 0.1) &&
      assertTrue(trips.head.maxSpeed == 50.0)
    },
    test("Поездка с короткой остановкой (<5 мин) — объединяется") {
      val points = List(
        // Движение
        mkPoint(0, 55.7500, 37.6200, 40.0),
        mkPoint(60, 55.7520, 37.6200, 50.0),
        // Короткая стоянка (2 мин < 5 мин MinParkingDuration)
        mkPoint(120, 55.7540, 37.6200, 0.0),
        mkPoint(180, 55.7540, 37.6200, 0.0),
        // Движение продолжается
        mkPoint(240, 55.7560, 37.6200, 45.0),
        mkPoint(300, 55.7580, 37.6200, 40.0)
      )
      val trips = TripDetector.detectTrips(points)
      // Короткая стоянка объединяется → одна поездка
      assertTrue(trips.size == 1)
    }
  )

  // ==========================================================
  // Детекция стоянок
  // ==========================================================

  val detectParkingsSuite = suite("detectParkings")(
    test("Пустой список → пусто") {
      val parkings = TripDetector.detectParkings(Nil)
      assertTrue(parkings.isEmpty)
    },
    test("Длинная стоянка (> 5 мин) определяется") {
      val points = List(
        mkPoint(0, 55.75, 37.62, 0.0, Some(false)),
        mkPoint(120, 55.75, 37.62, 0.0, Some(false)),
        mkPoint(240, 55.75, 37.62, 1.0, Some(false)),
        mkPoint(360, 55.75, 37.62, 0.0, Some(false))
      )
      val parkings = TripDetector.detectParkings(points)
      assertTrue(parkings.size == 1) &&
      assertTrue(parkings.head.durationMinutes >= 5.0)
    },
    test("Стоянка с включённым двигателем — engineOn = true") {
      val points = List(
        mkPoint(0, 55.75, 37.62, 0.0, Some(true)),
        mkPoint(120, 55.75, 37.62, 0.0, Some(true)),
        mkPoint(240, 55.75, 37.62, 0.0, Some(true)),
        mkPoint(360, 55.75, 37.62, 0.0, Some(true))
      )
      val parkings = TripDetector.detectParkings(points)
      assertTrue(parkings.nonEmpty) &&
      assertTrue(parkings.head.engineOn)
    }
  )

  // ==========================================================
  // Крайние случаи
  // ==========================================================

  val edgeCasesSuite = suite("Крайние случаи")(
    test("Слишком короткая поездка (<0.1 км) отфильтровывается") {
      // Две точки рядом: speed > 3, но расстояние < 0.1 км
      val points = List(
        mkPoint(0, 55.75000, 37.62000, 10.0),
        mkPoint(5, 55.75001, 37.62001, 10.0) // ~0.1 метра
      )
      val trips = TripDetector.detectTrips(points)
      assertTrue(trips.isEmpty)
    }
  )
