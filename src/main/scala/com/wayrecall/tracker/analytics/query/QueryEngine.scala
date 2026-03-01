package com.wayrecall.tracker.analytics.query

import com.wayrecall.tracker.analytics.domain.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import zio.*
import zio.interop.catz.*
import java.time.{Instant, LocalDate}

// ============================================================
// QueryEngine — запросы к TimescaleDB (continuous aggregates)
// ============================================================

trait QueryEngine:
  /** Суточная статистика по пробегу из daily_vehicle_stats */
  def getDailyVehicleStats(deviceId: Long, from: Instant, to: Instant): Task[List[DailyVehicleStats]]

  /** Почасовая статистика топлива из hourly_fuel_stats */
  def getHourlyFuelStats(deviceId: Long, from: Instant, to: Instant): Task[List[HourlyFuelStats]]

  /** Суточная статистика движения из daily_motion_stats */
  def getDailyMotionStats(deviceId: Long, from: Instant, to: Instant): Task[List[DailyMotionStats]]

  /** Raw GPS точки для детального анализа (поездки, нарушения скорости) */
  def getGpsPoints(deviceId: Long, from: Instant, to: Instant): Task[List[GpsPoint]]

  /** GPS точки с фильтром по скорости (для speed violations) */
  def getSpeedExceedingPoints(deviceId: Long, from: Instant, to: Instant, speedLimit: Double): Task[List[GpsPoint]]

object QueryEngine:

  val live: ZLayer[Transactor[Task], Nothing, QueryEngine] =
    ZLayer.fromFunction { (xa: Transactor[Task]) =>
      new QueryEngineLive(xa)
    }

final class QueryEngineLive(xa: Transactor[Task]) extends QueryEngine:

  override def getDailyVehicleStats(deviceId: Long, from: Instant, to: Instant): Task[List[DailyVehicleStats]] =
    sql"""
      SELECT day, device_id, mileage_meters, avg_speed, max_speed, point_count,
             first_point, last_point, start_lat, start_lon, end_lat, end_lon
      FROM daily_vehicle_stats
      WHERE device_id = $deviceId
        AND day >= ${from}::date
        AND day <= ${to}::date
      ORDER BY day ASC
    """.query[DailyVehicleStats].to[List].transact(xa)

  override def getHourlyFuelStats(deviceId: Long, from: Instant, to: Instant): Task[List[HourlyFuelStats]] =
    sql"""
      SELECT hour, device_id, start_level, end_level, min_level, max_level, mileage_meters
      FROM hourly_fuel_stats
      WHERE device_id = $deviceId
        AND hour >= $from
        AND hour <= $to
      ORDER BY hour ASC
    """.query[HourlyFuelStats].to[List].transact(xa)

  override def getDailyMotionStats(deviceId: Long, from: Instant, to: Instant): Task[List[DailyMotionStats]] =
    sql"""
      SELECT day, device_id, moving_seconds, idle_seconds, engine_seconds
      FROM daily_motion_stats
      WHERE device_id = $deviceId
        AND day >= ${from}::date
        AND day <= ${to}::date
      ORDER BY day ASC
    """.query[DailyMotionStats].to[List].transact(xa)

  override def getGpsPoints(deviceId: Long, from: Instant, to: Instant): Task[List[GpsPoint]] =
    sql"""
      SELECT timestamp, latitude, longitude, speed, odometer, fuel_level, ignition
      FROM gps_positions
      WHERE device_id = $deviceId
        AND timestamp >= $from
        AND timestamp <= $to
      ORDER BY timestamp ASC
    """.query[GpsPoint].to[List].transact(xa)

  override def getSpeedExceedingPoints(
      deviceId: Long,
      from: Instant,
      to: Instant,
      speedLimit: Double
  ): Task[List[GpsPoint]] =
    sql"""
      SELECT timestamp, latitude, longitude, speed, odometer, fuel_level, ignition
      FROM gps_positions
      WHERE device_id = $deviceId
        AND timestamp >= $from
        AND timestamp <= $to
        AND speed > $speedLimit
      ORDER BY timestamp ASC
    """.query[GpsPoint].to[List].transact(xa)

  // Doobie Read instances
  given Read[DailyVehicleStats] =
    Read[(LocalDate, Long, Double, Double, Double, Long, Instant, Instant, Double, Double, Double, Double)].map {
      case (day, deviceId, mileage, avg, max, count, first, last, sLat, sLon, eLat, eLon) =>
        DailyVehicleStats(day, deviceId, mileage, avg, max, count, first, last, sLat, sLon, eLat, eLon)
    }

  given Read[HourlyFuelStats] =
    Read[(Instant, Long, Double, Double, Double, Double, Double)].map {
      case (hour, deviceId, start, end, min, max, mileage) =>
        HourlyFuelStats(hour, deviceId, start, end, min, max, mileage)
    }

  given Read[DailyMotionStats] =
    Read[(LocalDate, Long, Double, Double, Double)].map {
      case (day, deviceId, moving, idle, engine) =>
        DailyMotionStats(day, deviceId, moving, idle, engine)
    }

  given Read[GpsPoint] =
    Read[(Instant, Double, Double, Double, Option[Double], Option[Double], Option[Boolean])].map {
      case (ts, lat, lon, speed, odo, fuel, ign) =>
        GpsPoint(ts, lat, lon, speed, odo, fuel, ign)
    }
