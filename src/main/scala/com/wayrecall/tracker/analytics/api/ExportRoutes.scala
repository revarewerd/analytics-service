package com.wayrecall.tracker.analytics.api

import com.wayrecall.tracker.analytics.domain.*
import com.wayrecall.tracker.analytics.export.ExportService
import zio.*
import zio.http.*
import zio.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ============================================================
// ExportRoutes — эндпоинты фонового экспорта отчётов
// POST /api/v1/reports/export — создать задачу экспорта
// GET  /api/v1/reports/export/:taskId — статус задачи
// GET  /api/v1/reports/export/:taskId/download — скачать файл
// ============================================================

object ExportRoutes:

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def routes: Routes[ExportService, Nothing] = Routes(
    // Создать задачу экспорта
    Method.POST / "api" / "v1" / "reports" / "export" -> handler { (req: Request) =>
      (for {
        body     <- req.body.asString
        request  <- ZIO.fromEither(body.fromJson[ExportRequest]).mapError(e => new RuntimeException(s"Неверный JSON: $e"))
        service  <- ZIO.service[ExportService]
        result   <- service.createExportTask(request)
      } yield Response.json(result.toJson).status(Status.Accepted))
        .catchAll(e => ZIO.succeed(errorResponse(400, e.getMessage)))
    },

    // Статус задачи
    Method.GET / "api" / "v1" / "reports" / "export" / string("taskId") -> handler { (taskId: String, _: Request) =>
      (for {
        service <- ZIO.service[ExportService]
        status  <- service.getTaskStatus(taskId)
        response <- status match
          case Some(task) => ZIO.succeed(Response.json(task.toJson))
          case None       => ZIO.succeed(Response.json(
            ErrorResponse("not_found", s"Задача $taskId не найдена").toJson
          ).status(Status.NotFound))
      } yield response)
        .catchAll(e => ZIO.succeed(errorResponse(500, e.getMessage)))
    },

    // Скачать файл
    Method.GET / "api" / "v1" / "reports" / "export" / string("taskId") / "download" -> handler { (taskId: String, _: Request) =>
      (for {
        service <- ZIO.service[ExportService]
        url     <- service.getDownloadUrl(taskId)
        response <- url match
          case Some(downloadUrl) =>
            // Redirect на presigned S3 URL
            ZIO.succeed(Response.redirect(URL.decode(downloadUrl).getOrElse(URL.empty)))
          case None =>
            ZIO.succeed(Response.json(
              ErrorResponse("not_ready", s"Файл задачи $taskId ещё не готов или не найден").toJson
            ).status(Status.NotFound))
      } yield response)
        .catchAll(e => ZIO.succeed(errorResponse(500, e.getMessage)))
    }
  )

  private def errorResponse(status: Int, message: String): Response =
    Response.json(ErrorResponse("error", message).toJson).status(Status.fromInt(status))
