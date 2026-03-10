package com.wayrecall.tracker.analytics.query

import zio.*
import zio.test.*
import zio.test.Assertion.*
import java.time.{Instant, LocalDate, ZoneOffset}

import com.wayrecall.tracker.analytics.domain.*

// === InMemory QueryEngine для тестирования генераторов ===

final case class InMemoryQueryEngine(
  points:      Ref[Map[Long, List[GpsPoint]]],
  dailyStats:  Ref[Map[Long, List[DailyVehicleStats]]],
  hourlyFuel:  Ref[Map[Long, List[HourlyFuelStats]]],
  dailyMotion: Ref[Map[Long, List[DailyMotionStats]]]
) extends QueryEngine:

  def getDailyVehicleStats(deviceId: Long, from: Instant, to: Instant): Task[List[DailyVehicleStats]] =
    val fromDate = LocalDate.ofInstant(from, ZoneOffset.UTC)
    val toDate   = LocalDate.ofInstant(to, ZoneOffset.UTC)
    dailyStats.get.map(_.getOrElse(deviceId, Nil).filter(s =>
      (s.day.isAfter(fromDate) || s.day == fromDate) && (s.day.isBefore(toDate) || s.day == toDate)
    ))

  def getHourlyFuelStats(deviceId: Long, from: Instant, to: Instant): Task[List[HourlyFuelStats]] =
    hourlyFuel.get.map(_.getOrElse(deviceId, Nil).filter(s =>
      (s.hour.isAfter(from) || s.hour == from) && (s.hour.isBefore(to) || s.hour == to)
    ))

  def getDailyMotionStats(deviceId: Long, from: Instant, to: Instant): Task[List[DailyMotionStats]] =
    val fromDate = LocalDate.ofInstant(from, ZoneOffset.UTC)
    val toDate   = LocalDate.ofInstant(to, ZoneOffset.UTC)
    dailyMotion.get.map(_.getOrElse(deviceId, Nil).filter(s =>
      (s.day.isAfter(fromDate) || s.day == fromDate) && (s.day.isBefore(toDate) || s.day == toDate)
    ))

  def getGpsPoints(deviceId: Long, from: Instant, to: Instant): Task[List[GpsPoint]] =
    points.get.map(_.getOrElse(deviceId, Nil).filter(p =>
      (p.timestamp.isAfter(from) || p.timestamp == from) && (p.timestamp.isBefore(to) || p.timestamp == to)
    ))

  def getSpeedExceedingPoints(deviceId: Long, from: Instant, to: Instant, speedLimit: Double): Task[List[GpsPoint]] =
    getGpsPoints(deviceId, from, to).map(_.filter(_.speed > speedLimit))

object InMemoryQueryEngine:
  def live(data: Map[Long, List[GpsPoint]] = Map.empty): ULayer[QueryEngine] = ZLayer {
    for
      pts <- Ref.make(data)
      ds  <- Ref.make(Map.empty[Long, List[DailyVehicleStats]])
      hf  <- Ref.make(Map.empty[Long, List[HourlyFuelStats]])
      dm  <- Ref.make(Map.empty[Long, List[DailyMotionStats]])
    yield InMemoryQueryEngine(pts, ds, hf, dm)
  }

// === Тесты QueryEngine ===

object QueryEngineSpec extends ZIOSpecDefault:

  private val now   = Instant.parse("2026-06-06T12:00:00Z")
  private val from  = Instant.parse("2026-06-06T00:00:00Z")
  private val to    = Instant.parse("2026-06-06T23:59:59Z")

  // Тестовые GPS точки — маршрут по Москве
  private val testPoints = List(
    GpsPoint(now.minusSeconds(3600), 55.7558, 37.6173, 60.0, Some(1000.0), Some(45.0), Some(true)),
    GpsPoint(now.minusSeconds(1800), 55.7600, 37.6200, 85.0, Some(1030.0), Some(43.0), Some(true)),
    GpsPoint(now,                    55.7650, 37.6250, 120.0, Some(1060.0), Some(40.0), Some(true))
  )

  def spec = suite("QueryEngine тесты")(
    suite("getGpsPoints")(
      test("пустое хранилище возвращает пустой список") {
        for
          qe   <- ZIO.service[QueryEngine]
          pts  <- qe.getGpsPoints(1L, from, to)
        yield assertTrue(pts.isEmpty)
      }.provide(InMemoryQueryEngine.live()),

      test("возвращает точки для устройства в диапазоне") {
        for
          qe   <- ZIO.service[QueryEngine]
          pts  <- qe.getGpsPoints(1L, from, to)
        yield assertTrue(pts.length == 3)
      }.provide(InMemoryQueryEngine.live(Map(1L -> testPoints))),

      test("не возвращает точки другого устройства") {
        for
          qe   <- ZIO.service[QueryEngine]
          pts  <- qe.getGpsPoints(999L, from, to)
        yield assertTrue(pts.isEmpty)
      }.provide(InMemoryQueryEngine.live(Map(1L -> testPoints)))
    ),

    suite("getSpeedExceedingPoints")(
      test("фильтрует по лимиту скорости") {
        for
          qe  <- ZIO.service[QueryEngine]
          pts <- qe.getSpeedExceedingPoints(1L, from, to, 90.0)
        yield assertTrue(
          pts.length == 1,
          pts.head.speed == 120.0
        )
      }.provide(InMemoryQueryEngine.live(Map(1L -> testPoints))),

      test("строгий лимит — точки с совпадающей скоростью не включаются") {
        for
          qe  <- ZIO.service[QueryEngine]
          pts <- qe.getSpeedExceedingPoints(1L, from, to, 120.0)
        yield assertTrue(pts.isEmpty)
      }.provide(InMemoryQueryEngine.live(Map(1L -> testPoints)))
    ),

    suite("GpsPoint домен")(
      test("поля GpsPoint корректны") {
        val p = testPoints.head
        assertTrue(
          p.speed == 60.0,
          p.odometer.contains(1000.0),
          p.fuelLevel.contains(45.0),
          p.ignition.contains(true)
        )
      },
      test("DateRange валидация") {
        val dr = DateRange(from, to)
        assertTrue(
          dr.from.isBefore(dr.to)
        )
      },
      test("Coordinates содержат lat/lon") {
        val c = Coordinates(55.7558, 37.6173)
        assertTrue(
          c.latitude == 55.7558,
          c.longitude == 37.6173
        )
      }
    )
  )
