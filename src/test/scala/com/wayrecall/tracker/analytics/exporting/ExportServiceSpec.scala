package com.wayrecall.tracker.analytics.exporting

import zio.*
import zio.test.*
import zio.test.Assertion.*
import java.time.Instant

import com.wayrecall.tracker.analytics.domain.*

// === InMemory ExportService для тестирования ===

final case class InMemoryExportService(
  tasks: Ref[Map[String, ExportTask]]
) extends ExportService:

  def createExportTask(request: ExportRequest): Task[ExportTaskCreated] =
    for
      taskId <- ZIO.succeed(java.util.UUID.randomUUID().toString)
      now     = Instant.now()
      task    = ExportTask(
        taskId         = taskId,
        organizationId = request.organizationId,
        reportType     = request.reportType,
        format         = request.format,
        status         = ExportStatus.Pending,
        progress       = 0,
        fileUrl        = None,
        error          = None,
        createdAt      = now,
        completedAt    = None
      )
      _ <- tasks.update(_ + (taskId -> task))
    yield ExportTaskCreated(taskId, ExportStatus.Pending)

  def getTaskStatus(taskId: String): Task[Option[ExportTask]] =
    tasks.get.map(_.get(taskId))

  def getDownloadUrl(taskId: String): Task[Option[String]] =
    tasks.get.map(_.get(taskId).flatMap(_.fileUrl))

object InMemoryExportService:
  val live: ULayer[ExportService] = ZLayer {
    Ref.make(Map.empty[String, ExportTask]).map(InMemoryExportService(_))
  }

// === Спецификация тестов ===

object ExportServiceSpec extends ZIOSpecDefault:

  private val now = Instant.now()

  /** Вспомогательный метод: создать ReportParams с минимальными defaults */
  private def mkParams(
    orgId: Long,
    vehicleIds: List[Long] = Nil,
    reportType: ReportType = ReportType.Mileage,
    from: Instant = now,
    to: Instant = now
  ): ReportParams =
    ReportParams(
      organizationId = orgId,
      vehicleIds     = vehicleIds,
      groupIds       = Nil,
      from           = from,
      to             = to,
      reportType     = reportType,
      groupBy        = None,
      includeTrips   = None,
      includeEvents  = None,
      geozoneIds     = None,
      speedLimit     = None
    )

  def spec = suite("ExportService тесты")(
    suite("createExportTask")(
      test("создание задачи экспорта возвращает taskId") {
        for
          svc    <- ZIO.service[ExportService]
          params  = mkParams(
            orgId      = 1L,
            vehicleIds = List(100L, 200L),
            reportType = ReportType.Mileage,
            from       = Instant.parse("2026-06-01T00:00:00Z"),
            to         = Instant.parse("2026-06-06T23:59:59Z")
          )
          req     = ExportRequest(
            organizationId = 1L,
            reportType     = ReportType.Mileage,
            format         = ExportFormat.Xlsx,
            parameters     = params
          )
          result <- svc.createExportTask(req)
        yield assertTrue(result.taskId.nonEmpty)
      },
      test("задачи имеют уникальные ID") {
        for
          svc <- ZIO.service[ExportService]
          req  = ExportRequest(1L, ReportType.Fuel, ExportFormat.Csv, mkParams(1L, List(1L), ReportType.Fuel))
          r1  <- svc.createExportTask(req)
          r2  <- svc.createExportTask(req)
        yield assertTrue(r1.taskId != r2.taskId)
      }
    ),

    suite("getTaskStatus")(
      test("несуществующая задача → None") {
        for
          svc    <- ZIO.service[ExportService]
          status <- svc.getTaskStatus("nonexistent")
        yield assertTrue(status.isEmpty)
      },
      test("существующая задача → Pending") {
        for
          svc    <- ZIO.service[ExportService]
          req     = ExportRequest(1L, ReportType.Idle, ExportFormat.Pdf, mkParams(1L, List(1L), ReportType.Idle))
          result <- svc.createExportTask(req)
          status <- svc.getTaskStatus(result.taskId)
        yield assertTrue(
          status.isDefined,
          status.get.status == ExportStatus.Pending,
          status.get.progress == 0
        )
      }
    ),

    suite("getDownloadUrl")(
      test("Pending задача → None (нет URL для скачивания)") {
        for
          svc    <- ZIO.service[ExportService]
          req     = ExportRequest(1L, ReportType.Summary, ExportFormat.Xlsx, mkParams(1L, List(1L), ReportType.Summary))
          result <- svc.createExportTask(req)
          url    <- svc.getDownloadUrl(result.taskId)
        yield assertTrue(url.isEmpty)
      },
      test("несуществующая задача → None") {
        for
          svc <- ZIO.service[ExportService]
          url <- svc.getDownloadUrl("no-such-task")
        yield assertTrue(url.isEmpty)
      }
    ),

    suite("ExportRequest домен")(
      test("ExportRequest содержит все поля") {
        val params = mkParams(
          orgId      = 42L,
          vehicleIds = List(1L, 2L, 3L),
          reportType = ReportType.SpeedViolations,
          from       = Instant.parse("2026-01-01T00:00:00Z"),
          to         = Instant.parse("2026-01-31T23:59:59Z")
        )
        val req = ExportRequest(
          organizationId = 42L,
          reportType     = ReportType.SpeedViolations,
          format         = ExportFormat.Pdf,
          parameters     = params
        )
        val vehicleCount = req.parameters.vehicleIds.length
        assertTrue(
          req.organizationId == 42L,
          vehicleCount == 3,
          req.reportType == ReportType.SpeedViolations,
          req.format == ExportFormat.Pdf
        )
      },
      test("ExportTask статусы") {
        val expected = Set("Pending", "Processing", "Completed", "Failed")
        val actual   = ExportStatus.values.map(_.toString).toSet
        assertTrue(actual == expected)
      }
    )
  ).provide(InMemoryExportService.live)
