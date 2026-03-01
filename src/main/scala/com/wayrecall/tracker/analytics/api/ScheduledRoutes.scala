package com.wayrecall.tracker.analytics.api

import com.wayrecall.tracker.analytics.domain.*
import com.wayrecall.tracker.analytics.export.ExportService
import com.wayrecall.tracker.analytics.repository.{ScheduledReportRepository, ReportHistoryRepository}
import com.wayrecall.tracker.analytics.scheduler.ReportScheduler
import zio.*
import zio.http.*
import zio.json.*

// ============================================================
// ScheduledRoutes — CRUD расписаний и история отчётов
// GET    /api/v1/scheduled?orgId=            — список расписаний
// POST   /api/v1/scheduled                   — создать расписание
// PUT    /api/v1/scheduled/:id               — обновить расписание
// DELETE /api/v1/scheduled/:id               — удалить расписание
// POST   /api/v1/scheduled/:id/run           — выполнить вручную
// GET    /api/v1/reports/history?orgId=       — история отчётов
// ============================================================

object ScheduledRoutes:

  def routes: Routes[
    ScheduledReportRepository & ReportHistoryRepository & ReportScheduler,
    Nothing
  ] = Routes(
    // Список расписаний организации
    Method.GET / "api" / "v1" / "scheduled" -> handler { (req: Request) =>
      (for {
        orgId     <- extractOrgId(req)
        repo      <- ZIO.service[ScheduledReportRepository]
        schedules <- repo.findByOrganization(orgId)
      } yield Response.json(schedules.toJson))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // Создать расписание
    Method.POST / "api" / "v1" / "scheduled" -> handler { (req: Request) =>
      (for {
        body    <- req.body.asString
        create  <- ZIO.fromEither(body.fromJson[CreateScheduledReport])
                     .mapError(e => new RuntimeException(s"Неверный JSON: $e"))
        repo    <- ZIO.service[ScheduledReportRepository]
        id      <- repo.create(create)
      } yield Response.json(s"""{"id":$id,"status":"created"}""").status(Status.Created))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // Обновить расписание
    Method.PUT / "api" / "v1" / "scheduled" / long("id") -> handler { (id: Long, req: Request) =>
      (for {
        body    <- req.body.asString
        update  <- ZIO.fromEither(body.fromJson[CreateScheduledReport])
                     .mapError(e => new RuntimeException(s"Неверный JSON: $e"))
        repo    <- ZIO.service[ScheduledReportRepository]
        _       <- repo.update(id, update)
      } yield Response.json(s"""{"id":$id,"status":"updated"}"""))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // Удалить расписание
    Method.DELETE / "api" / "v1" / "scheduled" / long("id") -> handler { (id: Long, _: Request) =>
      (for {
        repo <- ZIO.service[ScheduledReportRepository]
        _    <- repo.delete(id)
      } yield Response.json(s"""{"id":$id,"status":"deleted"}"""))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // Запустить вручную
    Method.POST / "api" / "v1" / "scheduled" / long("id") / "run" -> handler { (id: Long, _: Request) =>
      (for {
        scheduler <- ZIO.service[ReportScheduler]
        result    <- scheduler.runNow(id)
      } yield Response.json(result.toJson).status(Status.Accepted))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // История отчётов
    Method.GET / "api" / "v1" / "reports" / "history" -> handler { (req: Request) =>
      (for {
        orgId   <- extractOrgId(req)
        repo    <- ZIO.service[ReportHistoryRepository]
        history <- repo.findByOrganization(orgId, None, None, None)
      } yield Response.json(history.toJson))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    }
  )

  // ============================================================
  // Вспомогательные методы
  // ============================================================

  private def extractOrgId(req: Request): Task[Long] =
    ZIO.attempt {
      req.url.queryParams.get("orgId")
        .flatMap(_.headOption)
        .flatMap(_.toLongOption)
        .getOrElse(throw new RuntimeException("orgId обязателен"))
    }

  private def errorResponse(status: Int, message: String): Response =
    Response.json(ErrorResponse("error", message).toJson).status(Status.fromInt(status))
