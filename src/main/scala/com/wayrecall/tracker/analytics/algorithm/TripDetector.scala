package com.wayrecall.tracker.analytics.algorithm

import com.wayrecall.tracker.analytics.domain.*
import java.time.{Duration, Instant}

// ============================================================
// TripDetector — детекция поездок и стоянок
// ============================================================

object TripDetector:

  /** Порог скорости для определения движения (км/ч) */
  private val SpeedThresholdKmh = 3.0

  /** Минимальная длительность стоянки (секунды) */
  private val MinParkingDurationSec = 300L // 5 минут

  /** Минимальная длина поездки (км) */
  private val MinTripDistanceKm = 0.1

  /**
   * Детектирует поездки по последовательности GPS точек.
   * 
   * Алгоритм:
   * 1. Разбиваем точки на сегменты по скорости (> порога = движение)
   * 2. Группируем стоянки (скорость ≤ порога >= MinParkingDuration)
   * 3. Объединяем короткие стоянки в одну поездку
   * 4. Фильтруем слишком короткие поездки (< MinTripDistance)
   */
  def detectTrips(points: List[GpsPoint]): List[Trip] =
    if points.size < 2 then return Nil

    // Разбиваем на сегменты: движение / стоянка
    val segments = splitIntoSegments(points)

    // Конвертируем сегменты движения в Trip
    segments.collect { case MovingSegment(pts) if pts.size >= 2 =>
      val distance = MileageCalculator.calculateBestMileageKm(pts)
      if distance >= MinTripDistanceKm then
        val speeds = pts.map(_.speed)
        val duration = Duration.between(pts.head.timestamp, pts.last.timestamp)
        Some(Trip(
          startTime = pts.head.timestamp,
          endTime = pts.last.timestamp,
          startCoords = Coordinates(pts.head.latitude, pts.head.longitude),
          endCoords = Coordinates(pts.last.latitude, pts.last.longitude),
          distanceKm = math.round(distance * 100.0) / 100.0,
          maxSpeed = if speeds.nonEmpty then speeds.max else 0.0,
          avgSpeed = if speeds.nonEmpty then speeds.sum / speeds.size else 0.0,
          durationMinutes = duration.toSeconds / 60.0
        ))
      else None
    }.flatten

  /**
   * Детектирует стоянки по последовательности GPS точек.
   */
  def detectParkings(points: List[GpsPoint]): List[Parking] =
    if points.size < 2 then return Nil

    val segments = splitIntoSegments(points)

    segments.collect { case StoppedSegment(pts) if pts.size >= 2 =>
      val duration = Duration.between(pts.head.timestamp, pts.last.timestamp)
      if duration.getSeconds >= MinParkingDurationSec then
        val engineOn = pts.exists(_.ignition.getOrElse(false))
        Some(Parking(
          startTime = pts.head.timestamp,
          endTime = pts.last.timestamp,
          coords = Coordinates(pts.head.latitude, pts.head.longitude),
          durationMinutes = duration.toSeconds / 60.0,
          engineOn = engineOn
        ))
      else None
    }.flatten

  // ============================================================
  // Внутренние типы и методы
  // ============================================================

  /** Сегмент: движение или стоянка */
  private sealed trait Segment
  private case class MovingSegment(points: List[GpsPoint]) extends Segment
  private case class StoppedSegment(points: List[GpsPoint]) extends Segment

  /**
   * Разбивает точки на сегменты движения и стоянки.
   * Объединяет короткие стоянки (< MinParkingDuration) с соседними поездками.
   */
  private def splitIntoSegments(points: List[GpsPoint]): List[Segment] =
    if points.isEmpty then return Nil

    var segments = List.empty[Segment]
    var currentPoints = List.empty[GpsPoint]
    var isMoving = points.head.speed > SpeedThresholdKmh

    points.foreach { point =>
      val pointMoving = point.speed > SpeedThresholdKmh
      if pointMoving == isMoving then
        currentPoints = currentPoints :+ point
      else
        // Смена режима — сохраняем текущий сегмент
        if currentPoints.nonEmpty then
          val segment = if isMoving then MovingSegment(currentPoints) else StoppedSegment(currentPoints)
          segments = segments :+ segment
        currentPoints = List(point)
        isMoving = pointMoving
    }
    // Последний сегмент
    if currentPoints.nonEmpty then
      val segment = if isMoving then MovingSegment(currentPoints) else StoppedSegment(currentPoints)
      segments = segments :+ segment

    // Объединяем короткие стоянки с соседними поездками
    mergeShortStops(segments)

  /**
   * Объединяет StoppedSegment длительностью < MinParkingDuration
   * с соседними MovingSegment-ами.
   */
  private def mergeShortStops(segments: List[Segment]): List[Segment] =
    if segments.size <= 1 then return segments

    segments.foldLeft(List.empty[Segment]) { (acc, segment) =>
      segment match
        case s: StoppedSegment if s.points.size >= 2 =>
          val dur = Duration.between(s.points.head.timestamp, s.points.last.timestamp)
          if dur.getSeconds < MinParkingDurationSec then
            // Короткая стоянка — объединяем с предыдущим сегментом движения
            acc match
              case (prev: MovingSegment) :: rest =>
                MovingSegment(prev.points ++ s.points) :: rest
              case _ => acc :+ s
          else acc :+ s
        case other => acc :+ other
    }
