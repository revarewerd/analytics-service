package com.wayrecall.tracker.analytics.repository

import zio.*
import zio.test.*
import zio.test.Assertion.*
import java.time.Instant
import java.util.UUID

import com.wayrecall.tracker.analytics.domain.*

// === InMemory реализации репозиториев для тестов ===

final case class InMemoryReportTemplateRepo(
  store: Ref[Map[Long, ReportTemplate]],
  seq:   Ref[Long]
) extends ReportTemplateRepository:

  def findByOrganization(orgId: Long): Task[List[ReportTemplate]] =
    store.get.map(_.values.filter(_.organizationId == orgId).toList)

  def findById(id: Long, orgId: Long): Task[Option[ReportTemplate]] =
    store.get.map(_.values.find(t => t.id == id && t.organizationId == orgId))

  def create(
    name: String,
    orgId: Long,
    reportType: ReportType,
    config: String,
    defaultFilters: String,
    createdBy: Long
  ): Task[ReportTemplate] =
    for
      id  <- seq.updateAndGet(_ + 1)
      now  = Instant.now()
      tmpl = ReportTemplate(
        id             = id,
        name           = name,
        organizationId = orgId,
        reportType     = reportType,
        config         = config,
        defaultFilters = defaultFilters,
        createdAt      = now,
        createdBy      = createdBy
      )
      _ <- store.update(_ + (id -> tmpl))
    yield tmpl

object InMemoryReportTemplateRepo:
  val live: ULayer[ReportTemplateRepository] = ZLayer {
    for
      s <- Ref.make(Map.empty[Long, ReportTemplate])
      q <- Ref.make(0L)
    yield InMemoryReportTemplateRepo(s, q)
  }

// --- ScheduledReport ---

final case class InMemoryScheduledReportRepo(
  store: Ref[Map[Long, ScheduledReport]],
  seq:   Ref[Long]
) extends ScheduledReportRepository:

  def findByOrganization(orgId: Long): Task[List[ScheduledReport]] =
    store.get.map(_.values.filter(_.organizationId == orgId).toList)

  def findById(id: Long, orgId: Long): Task[Option[ScheduledReport]] =
    store.get.map(_.values.find(s => s.id == id && s.organizationId == orgId))

  def create(orgId: Long, req: CreateScheduledReport): Task[ScheduledReport] =
    for
      id  <- seq.updateAndGet(_ + 1)
      now  = Instant.now()
      sr   = ScheduledReport(
        id               = id,
        name             = req.name,
        organizationId   = orgId,
        templateId       = req.templateId,
        schedule         = req.schedule,
        timezone         = req.timezone.getOrElse("Europe/Moscow"),
        reportType       = req.reportType,
        vehicleIds       = req.vehicleIds,
        groupIds         = req.groupIds,
        periodType       = req.periodType,
        deliveryChannels = req.deliveryChannels,
        recipients       = req.recipients,
        exportFormat     = req.exportFormat,
        enabled          = true,
        lastRunAt        = None,
        nextRunAt        = None,
        createdAt        = now
      )
      _ <- store.update(_ + (id -> sr))
    yield sr

  def update(id: Long, orgId: Long, req: CreateScheduledReport): Task[Option[ScheduledReport]] =
    store.modify { m =>
      m.get(id).filter(_.organizationId == orgId) match
        case Some(old) =>
          val upd = old.copy(
            name             = req.name,
            reportType       = req.reportType,
            schedule         = req.schedule,
            timezone         = req.timezone.getOrElse(old.timezone),
            vehicleIds       = req.vehicleIds,
            groupIds         = req.groupIds,
            periodType       = req.periodType,
            deliveryChannels = req.deliveryChannels,
            recipients       = req.recipients,
            exportFormat     = req.exportFormat
          )
          (Some(upd), m.updated(id, upd))
        case None => (None, m)
    }

  def delete(id: Long, orgId: Long): Task[Boolean] =
    store.modify { m =>
      if m.get(id).exists(_.organizationId == orgId) then (true, m - id)
      else (false, m)
    }

  def findDueSchedules(now: Instant): Task[List[ScheduledReport]] =
    store.get.map { m =>
      m.values.filter(s => s.enabled && s.nextRunAt.exists(_.isBefore(now))).toList
    }

  def updateRunTimes(id: Long, lastRunAt: Instant, nextRunAt: Instant): Task[Unit] =
    store.update(_.updatedWith(id)(_.map(_.copy(lastRunAt = Some(lastRunAt), nextRunAt = Some(nextRunAt)))))

object InMemoryScheduledReportRepo:
  val live: ULayer[ScheduledReportRepository] = ZLayer {
    for
      s <- Ref.make(Map.empty[Long, ScheduledReport])
      q <- Ref.make(0L)
    yield InMemoryScheduledReportRepo(s, q)
  }

// --- ReportHistory ---

final case class InMemoryReportHistoryRepo(
  store: Ref[Map[Long, ReportHistory]],
  seq:   Ref[Long]
) extends ReportHistoryRepository:

  def findByOrganization(
    orgId: Long,
    from: Option[Instant],
    to: Option[Instant],
    status: Option[ReportHistoryStatus],
    limit: Int
  ): Task[List[ReportHistory]] =
    store.get.map { m =>
      m.values
        .filter(_.organizationId == orgId)
        .filter(h => from.forall(f => h.createdAt.isAfter(f) || h.createdAt == f))
        .filter(h => to.forall(t => h.createdAt.isBefore(t) || h.createdAt == t))
        .filter(h => status.forall(_ == h.status))
        .toList
        .sortBy(_.createdAt)(using Ordering[Instant].reverse)
        .take(limit)
    }

  def create(
    orgId: Long,
    userId: Option[Long],
    scheduledId: Option[Long],
    reportType: ReportType,
    parameters: String,
    status: ReportHistoryStatus
  ): Task[ReportHistory] =
    for
      id  <- seq.updateAndGet(_ + 1)
      now  = Instant.now()
      rh   = ReportHistory(
        id             = id,
        organizationId = orgId,
        userId         = userId,
        scheduledId    = scheduledId,
        reportType     = reportType,
        parameters     = parameters,
        status         = status,
        fileUrl        = None,
        fileSize       = None,
        errorMessage   = None,
        createdAt      = now,
        completedAt    = None,
        expiresAt      = None
      )
      _ <- store.update(_ + (id -> rh))
    yield rh

  def updateStatus(
    id: Long,
    status: ReportHistoryStatus,
    fileUrl: Option[String],
    fileSize: Option[Long],
    errorMessage: Option[String]
  ): Task[Unit] =
    store.update(_.updatedWith(id)(_.map(h =>
      h.copy(
        status       = status,
        fileUrl      = fileUrl.orElse(h.fileUrl),
        fileSize     = fileSize.orElse(h.fileSize),
        errorMessage = errorMessage.orElse(h.errorMessage),
        completedAt  = Some(Instant.now())
      )
    )))

  def findById(id: Long, orgId: Long): Task[Option[ReportHistory]] =
    store.get.map(_.values.find(h => h.id == id && h.organizationId == orgId))

object InMemoryReportHistoryRepo:
  val live: ULayer[ReportHistoryRepository] = ZLayer {
    for
      s <- Ref.make(Map.empty[Long, ReportHistory])
      q <- Ref.make(0L)
    yield InMemoryReportHistoryRepo(s, q)
  }

// === Спецификация тестов ===

object RepositorySpec extends ZIOSpecDefault:

  def spec = suite("Analytics Repository тесты")(
    // --- ReportTemplateRepository ---
    suite("ReportTemplateRepository")(
      test("создание шаблона и поиск по ID") {
        for
          repo <- ZIO.service[ReportTemplateRepository]
          tmpl <- repo.create("Пробег за месяц", 1L, ReportType.Mileage, "{}", "{}", 10L)
          found <- repo.findById(tmpl.id, 1L)
        yield assertTrue(
          found.isDefined,
          found.get.name == "Пробег за месяц",
          found.get.organizationId == 1L,
          found.get.reportType == ReportType.Mileage
        )
      },
      test("поиск по организации фильтрует корректно") {
        for
          repo <- ZIO.service[ReportTemplateRepository]
          _    <- repo.create("Шаблон org1", 1L, ReportType.Fuel, "{}", "{}", 10L)
          _    <- repo.create("Шаблон org2", 2L, ReportType.Idle, "{}", "{}", 20L)
          _    <- repo.create("Шаблон2 org1", 1L, ReportType.SpeedViolations, "{}", "{}", 10L)
          list <- repo.findByOrganization(1L)
        yield assertTrue(
          list.length == 2,
          list.forall(_.organizationId == 1L)
        )
      },
      test("findById с чужой организацией возвращает None") {
        for
          repo  <- ZIO.service[ReportTemplateRepository]
          tmpl  <- repo.create("Шаблон", 1L, ReportType.Summary, "{}", "{}", 10L)
          found <- repo.findById(tmpl.id, 999L)
        yield assertTrue(found.isEmpty)
      },
      test("ID уникальны при множественном создании") {
        for
          repo <- ZIO.service[ReportTemplateRepository]
          t1   <- repo.create("A", 1L, ReportType.Mileage, "{}", "{}", 1L)
          t2   <- repo.create("B", 1L, ReportType.Fuel, "{}", "{}", 1L)
          t3   <- repo.create("C", 1L, ReportType.Idle, "{}", "{}", 1L)
        yield assertTrue(
          t1.id != t2.id,
          t2.id != t3.id,
          t1.id != t3.id
        )
      }
    ).provide(InMemoryReportTemplateRepo.live),

    // --- ScheduledReportRepository ---
    suite("ScheduledReportRepository")(
      test("создание и поиск расписания") {
        for
          repo <- ZIO.service[ScheduledReportRepository]
          req   = CreateScheduledReport(
            name = "Ежедневный пробег",
            reportType = ReportType.Mileage,
            schedule = "0 8 * * *",
            timezone = None,
            vehicleIds = Nil,
            groupIds = Nil,
            periodType = PeriodType.Yesterday,
            deliveryChannels = Nil,
            recipients = "{\"emails\":[\"admin@test.com\"]}",
            exportFormat = ExportFormat.Xlsx,
            templateId = None
          )
          sr   <- repo.create(1L, req)
          found <- repo.findById(sr.id, 1L)
        yield assertTrue(
          found.isDefined,
          found.get.name == "Ежедневный пробег",
          found.get.enabled,
          found.get.schedule == "0 8 * * *"
        )
      },
      test("обновление расписания") {
        for
          repo <- ZIO.service[ScheduledReportRepository]
          req   = CreateScheduledReport(
            name = "Старое",
            reportType = ReportType.Fuel,
            schedule = "0 9 * * 1",
            timezone = None,
            vehicleIds = Nil,
            groupIds = Nil,
            periodType = PeriodType.Yesterday,
            deliveryChannels = Nil,
            recipients = "{}",
            exportFormat = ExportFormat.Csv,
            templateId = None
          )
          sr   <- repo.create(1L, req)
          upd   = req.copy(name = "Новое", schedule = "0 10 * * 1")
          result <- repo.update(sr.id, 1L, upd)
        yield assertTrue(
          result.isDefined,
          result.get.name == "Новое",
          result.get.schedule == "0 10 * * 1"
        )
      },
      test("удаление расписания") {
        for
          repo <- ZIO.service[ScheduledReportRepository]
          req   = CreateScheduledReport(
            name = "Удаляемый",
            reportType = ReportType.Idle,
            schedule = "0 0 * * *",
            timezone = None,
            vehicleIds = Nil,
            groupIds = Nil,
            periodType = PeriodType.Yesterday,
            deliveryChannels = Nil,
            recipients = "{}",
            exportFormat = ExportFormat.Pdf,
            templateId = None
          )
          sr   <- repo.create(1L, req)
          ok   <- repo.delete(sr.id, 1L)
          found <- repo.findById(sr.id, 1L)
        yield assertTrue(ok, found.isEmpty)
      },
      test("delete чужой организации возвращает false") {
        for
          repo <- ZIO.service[ScheduledReportRepository]
          req   = CreateScheduledReport(
            name = "Чужой",
            reportType = ReportType.Summary,
            schedule = "0 0 * * *",
            timezone = None,
            vehicleIds = Nil,
            groupIds = Nil,
            periodType = PeriodType.Yesterday,
            deliveryChannels = Nil,
            recipients = "{}",
            exportFormat = ExportFormat.Xlsx,
            templateId = None
          )
          sr   <- repo.create(1L, req)
          ok   <- repo.delete(sr.id, 999L)
        yield assertTrue(!ok)
      },
      test("findDueSchedules находит подходящие по времени") {
        for
          repo <- ZIO.service[ScheduledReportRepository]
          req   = CreateScheduledReport(
            name = "Дьюшка",
            reportType = ReportType.Mileage,
            schedule = "0 8 * * *",
            timezone = None,
            vehicleIds = Nil,
            groupIds = Nil,
            periodType = PeriodType.Yesterday,
            deliveryChannels = Nil,
            recipients = "{}",
            exportFormat = ExportFormat.Csv,
            templateId = None
          )
          sr   <- repo.create(1L, req)
          past  = Instant.now().minusSeconds(3600)
          _    <- repo.updateRunTimes(sr.id, past, past)
          due  <- repo.findDueSchedules(Instant.now())
        yield assertTrue(due.exists(_.id == sr.id))
      }
    ).provide(InMemoryScheduledReportRepo.live),

    // --- ReportHistoryRepository ---
    suite("ReportHistoryRepository")(
      test("создание истории и поиск по ID") {
        for
          repo <- ZIO.service[ReportHistoryRepository]
          rh   <- repo.create(1L, Some(10L), None, ReportType.Mileage, "{}", ReportHistoryStatus.Pending)
          found <- repo.findById(rh.id, 1L)
        yield assertTrue(
          found.isDefined,
          found.get.status == ReportHistoryStatus.Pending,
          found.get.organizationId == 1L
        )
      },
      test("обновление статуса истории") {
        for
          repo  <- ZIO.service[ReportHistoryRepository]
          rh    <- repo.create(1L, Some(10L), None, ReportType.Fuel, "{}", ReportHistoryStatus.Pending)
          _     <- repo.updateStatus(rh.id, ReportHistoryStatus.Completed, Some("https://s3/file.xlsx"), Some(1024L), None)
          found <- repo.findById(rh.id, 1L)
        yield assertTrue(
          found.get.status == ReportHistoryStatus.Completed,
          found.get.fileUrl.contains("https://s3/file.xlsx"),
          found.get.fileSize.contains(1024L),
          found.get.completedAt.isDefined
        )
      },
      test("фильтрация по статусу") {
        for
          repo <- ZIO.service[ReportHistoryRepository]
          _    <- repo.create(1L, None, None, ReportType.Idle, "{}", ReportHistoryStatus.Pending)
          _    <- repo.create(1L, None, None, ReportType.SpeedViolations, "{}", ReportHistoryStatus.Completed)
          _    <- repo.create(1L, None, None, ReportType.Summary, "{}", ReportHistoryStatus.Failed)
          list <- repo.findByOrganization(1L, None, None, Some(ReportHistoryStatus.Completed), 10)
        yield assertTrue(
          list.length == 1,
          list.head.status == ReportHistoryStatus.Completed
        )
      },
      test("limit ограничивает количество") {
        for
          repo <- ZIO.service[ReportHistoryRepository]
          _    <- ZIO.foreach(1 to 5)(_ => repo.create(1L, None, None, ReportType.Mileage, "{}", ReportHistoryStatus.Completed))
          list <- repo.findByOrganization(1L, None, None, None, 3)
        yield assertTrue(list.length == 3)
      }
    ).provide(InMemoryReportHistoryRepo.live),

    // --- Домен: перечисления ---
    suite("Перечисления отчётов")(
      test("ReportType содержит 6 значений") {
        assertTrue(ReportType.values.length == 6)
      },
      test("ExportFormat содержит 3 формата") {
        val formats = ExportFormat.values.map(_.toString).toSet
        assertTrue(
          formats.contains("Xlsx"),
          formats.contains("Pdf"),
          formats.contains("Csv")
        )
      },
      test("ExportStatus содержит 4 статуса") {
        assertTrue(ExportStatus.values.length == 4)
      },
      test("ReportHistoryStatus содержит 4 статуса") {
        assertTrue(ReportHistoryStatus.values.length == 4)
      }
    )
  )
