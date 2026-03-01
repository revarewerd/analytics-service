package com.wayrecall.tracker.analytics.api

import com.wayrecall.tracker.analytics.cache.ReportCache
import com.wayrecall.tracker.analytics.domain.*
import com.wayrecall.tracker.analytics.generator.*
import com.wayrecall.tracker.analytics.query.QueryEngine
import zio.*
import zio.http.*
import zio.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ============================================================
// ReportRoutes — эндпоинты генерации отчётов
// GET /api/v1/reports/{type}?orgId=&vehicleIds=&from=&to=&speedLimit=
// ============================================================

object ReportRoutes:

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def routes: Routes[
    MileageReportGenerator & FuelReportGenerator & GeozoneReportGenerator &
    IdleReportGenerator & SpeedReportGenerator & SummaryReportGenerator &
    ReportCache,
    Nothing
  ] = Routes(
    // Отчёт по пробегу
    Method.GET / "api" / "v1" / "reports" / "mileage" -> handler { (req: Request) =>
      (for {
        params    <- parseParams(req)
        generator <- ZIO.service[MileageReportGenerator]
        cache     <- ZIO.service[ReportCache]
        cacheKey   = s"mileage:${params.organizationId}:${params.vehicleIds.sorted.mkString(",")}:${params.from}:${params.to}"
        cached    <- cache.get[MileageReport](cacheKey)
        report    <- cached match
          case Some(r) => ZIO.succeed(r)
          case None =>
            // Генерируем для первого vehicleId (или можно объединить несколько)
            params.vehicleIds.headOption match
              case Some(vid) =>
                generator.generate(params, vid).tap(r => cache.set(cacheKey, r, params))
              case None =>
                ZIO.fail(new RuntimeException("vehicleIds обязателен"))
      } yield Response.json(report.toJson))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // Отчёт по топливу
    Method.GET / "api" / "v1" / "reports" / "fuel" -> handler { (req: Request) =>
      (for {
        params    <- parseParams(req)
        generator <- ZIO.service[FuelReportGenerator]
        cache     <- ZIO.service[ReportCache]
        cacheKey   = s"fuel:${params.organizationId}:${params.vehicleIds.sorted.mkString(",")}:${params.from}:${params.to}"
        cached    <- cache.get[FuelReport](cacheKey)
        report    <- cached match
          case Some(r) => ZIO.succeed(r)
          case None =>
            params.vehicleIds.headOption match
              case Some(vid) =>
                generator.generate(params, vid).tap(r => cache.set(cacheKey, r, params))
              case None =>
                ZIO.fail(new RuntimeException("vehicleIds обязателен"))
      } yield Response.json(report.toJson))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // Отчёт по геозонам
    Method.GET / "api" / "v1" / "reports" / "geozones" -> handler { (req: Request) =>
      (for {
        params    <- parseParams(req)
        generator <- ZIO.service[GeozoneReportGenerator]
        cache     <- ZIO.service[ReportCache]
        cacheKey   = s"geozones:${params.organizationId}:${params.vehicleIds.sorted.mkString(",")}:${params.from}:${params.to}"
        cached    <- cache.get[GeozoneReport](cacheKey)
        report    <- cached match
          case Some(r) => ZIO.succeed(r)
          case None =>
            params.vehicleIds.headOption match
              case Some(vid) =>
                generator.generate(params, vid).tap(r => cache.set(cacheKey, r, params))
              case None =>
                ZIO.fail(new RuntimeException("vehicleIds обязателен"))
      } yield Response.json(report.toJson))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // Отчёт по простоям
    Method.GET / "api" / "v1" / "reports" / "idle" -> handler { (req: Request) =>
      (for {
        params    <- parseParams(req)
        generator <- ZIO.service[IdleReportGenerator]
        cache     <- ZIO.service[ReportCache]
        cacheKey   = s"idle:${params.organizationId}:${params.vehicleIds.sorted.mkString(",")}:${params.from}:${params.to}"
        cached    <- cache.get[IdleReport](cacheKey)
        report    <- cached match
          case Some(r) => ZIO.succeed(r)
          case None =>
            params.vehicleIds.headOption match
              case Some(vid) =>
                generator.generate(params, vid).tap(r => cache.set(cacheKey, r, params))
              case None =>
                ZIO.fail(new RuntimeException("vehicleIds обязателен"))
      } yield Response.json(report.toJson))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // Отчёт по превышениям скорости
    Method.GET / "api" / "v1" / "reports" / "speed-violations" -> handler { (req: Request) =>
      (for {
        params    <- parseParams(req)
        generator <- ZIO.service[SpeedReportGenerator]
        cache     <- ZIO.service[ReportCache]
        cacheKey   = s"speed:${params.organizationId}:${params.vehicleIds.sorted.mkString(",")}:${params.from}:${params.to}"
        cached    <- cache.get[SpeedViolationsReport](cacheKey)
        report    <- cached match
          case Some(r) => ZIO.succeed(r)
          case None =>
            params.vehicleIds.headOption match
              case Some(vid) =>
                generator.generate(params, vid).tap(r => cache.set(cacheKey, r, params))
              case None =>
                ZIO.fail(new RuntimeException("vehicleIds обязателен"))
      } yield Response.json(report.toJson))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // Сводный отчёт (по всем ТС)
    Method.GET / "api" / "v1" / "reports" / "summary" -> handler { (req: Request) =>
      (for {
        params    <- parseParams(req)
        generator <- ZIO.service[SummaryReportGenerator]
        cache     <- ZIO.service[ReportCache]
        cacheKey   = s"summary:${params.organizationId}:${params.vehicleIds.sorted.mkString(",")}:${params.from}:${params.to}"
        cached    <- cache.get[SummaryReport](cacheKey)
        report    <- cached match
          case Some(r) => ZIO.succeed(r)
          case None =>
            generator.generate(params, params.vehicleIds).tap(r => cache.set(cacheKey, r, params))
      } yield Response.json(report.toJson))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    }
  )

  // ============================================================
  // Вспомогательные методы
  // ============================================================

  /** Парсинг query-параметров запроса */
  private def parseParams(req: Request): Task[ReportParams] =
    ZIO.attempt {
      val url = req.url
      val orgId = url.queryParams.get("orgId")
        .flatMap(_.headOption).flatMap(_.toLongOption)
        .getOrElse(throw new RuntimeException("orgId обязателен"))
      val vehicleIds = url.queryParams.get("vehicleIds")
        .flatMap(_.headOption)
        .map(_.split(",").flatMap(_.toLongOption).toList)
        .getOrElse(List.empty)
      val from = url.queryParams.get("from")
        .flatMap(_.headOption)
        .map(LocalDate.parse(_, dateFormatter))
        .getOrElse(throw new RuntimeException("from обязателен (yyyy-MM-dd)"))
      val to = url.queryParams.get("to")
        .flatMap(_.headOption)
        .map(LocalDate.parse(_, dateFormatter))
        .getOrElse(throw new RuntimeException("to обязателен (yyyy-MM-dd)"))
      val speedLimit = url.queryParams.get("speedLimit")
        .flatMap(_.headOption).flatMap(_.toDoubleOption)
      val includeTrips = url.queryParams.get("includeTrips")
        .flatMap(_.headOption).map(_ == "true").getOrElse(false)

      ReportParams(
        organizationId = orgId,
        vehicleIds = vehicleIds,
        from = from,
        to = to,
        speedLimit = speedLimit,
        includeTrips = includeTrips
      )
    }

  /** Стандартный ответ об ошибке */
  private def errorResponse(status: Int, message: String): Response =
    Response.json(ErrorResponse("error", message).toJson).status(Status.fromInt(status))
