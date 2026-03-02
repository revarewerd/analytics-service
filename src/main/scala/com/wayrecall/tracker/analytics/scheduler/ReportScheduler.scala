package com.wayrecall.tracker.analytics.scheduler

import com.wayrecall.tracker.analytics.cache.ReportCache
import com.wayrecall.tracker.analytics.config.SchedulerConfig
import com.wayrecall.tracker.analytics.domain.*
import com.wayrecall.tracker.analytics.exporting.ExportService
import com.wayrecall.tracker.analytics.repository.{ScheduledReportRepository, ReportHistoryRepository}
import zio.*
import java.time.{Instant, LocalDate, ZoneId}

// ============================================================
// ReportScheduler — cron-планировщик отчётов
// Проверяет due schedules каждую минуту, генерирует и экспортирует
// ============================================================

trait ReportScheduler:
  /** Запустить планировщик как фоновую задачу */
  def start: Task[Unit]

  /** Выполнить конкретный расписанный отчёт вручную */
  def runNow(scheduleId: Long, orgId: Long): Task[ExportTaskCreated]

object ReportScheduler:

  val live: ZLayer[
    ScheduledReportRepository & ReportHistoryRepository & ExportService & SchedulerConfig,
    Nothing,
    ReportScheduler
  ] =
    ZLayer.fromFunction { (
      scheduledRepo: ScheduledReportRepository,
      historyRepo: ReportHistoryRepository,
      exportService: ExportService,
      config: SchedulerConfig
    ) =>
      new ReportSchedulerLive(scheduledRepo, historyRepo, exportService, config)
    }

final class ReportSchedulerLive(
    scheduledRepo: ScheduledReportRepository,
    historyRepo: ReportHistoryRepository,
    exportService: ExportService,
    config: SchedulerConfig
) extends ReportScheduler:

  override def start: Task[Unit] =
    if !config.enabled then
      ZIO.logInfo("Планировщик отчётов отключён")
    else
      ZIO.logInfo(s"Запуск планировщика отчётов (интервал: ${config.checkIntervalSeconds}s)") *>
        checkAndRunSchedules
          .catchAll(e => ZIO.logError(s"Ошибка планировщика: ${e.getMessage}"))
          .repeat(Schedule.fixed(Duration.fromSeconds(config.checkIntervalSeconds)))
          .fork
          .unit

  override def runNow(scheduleId: Long, orgId: Long): Task[ExportTaskCreated] =
    for {
      schedule <- scheduledRepo.findById(scheduleId, orgId).flatMap {
        case Some(s) => ZIO.succeed(s)
        case None    => ZIO.fail(new RuntimeException(s"Расписание $scheduleId не найдено"))
      }
      result <- executeSchedule(schedule)
    } yield result

  /** Проверить и запустить готовые расписания */
  private def checkAndRunSchedules: Task[Unit] =
    for {
      now      <- Clock.instant
      // Получаем все расписания, где next_run_at <= now
      due      <- scheduledRepo.findDueSchedules(now)
      _        <- ZIO.logInfo(s"Найдено ${due.size} расписаний к выполнению")
      // Запускаем каждое параллельно, но с ограничением
      _        <- ZIO.foreachParDiscard(due)(schedule =>
        executeSchedule(schedule)
          .catchAll(e =>
            ZIO.logError(s"Ошибка выполнения расписания ${schedule.id}: ${e.getMessage}")
          )
      ).withParallelism(config.maxConcurrentSchedules)
    } yield ()

  /** Выполнить одно расписание: создать задачу экспорта и обновить расписание */
  private def executeSchedule(schedule: ScheduledReport): Task[ExportTaskCreated] =
    for {
      _        <- ZIO.logInfo(s"Выполняю расписание '${schedule.name}' (id=${schedule.id})")

      // Определяем период отчёта на основе periodType
      period   <- calculatePeriod(schedule.periodType, schedule.timezone)

      // Создаём параметры отчёта
      params = ReportParams(
        organizationId = schedule.organizationId,
        vehicleIds     = schedule.vehicleIds,
        groupIds       = schedule.groupIds,
        from           = period.from,
        to             = period.to,
        reportType     = schedule.reportType,
        groupBy        = None,
        includeTrips   = None,
        includeEvents  = None,
        geozoneIds     = None,
        speedLimit     = None
      )

      // Создаём запрос на экспорт
      request = ExportRequest(
        organizationId = schedule.organizationId,
        reportType     = schedule.reportType,
        format         = schedule.exportFormat,
        parameters     = params
      )

      // Запускаем экспорт
      result   <- exportService.createExportTask(request)

      // Записываем в историю
      _        <- historyRepo.create(
        orgId       = schedule.organizationId,
        userId      = None,
        scheduledId = Some(schedule.id),
        reportType  = schedule.reportType,
        parameters  = s"""{"vehicleIds":${schedule.vehicleIds.mkString("[", ",", "]")},"from":"${period.from}","to":"${period.to}"}""",
        status      = ReportHistoryStatus.Processing
      )

      // Обновляем last_run_at и next_run_at
      now      <- Clock.instant
      nextRun   = calculateNextRun(schedule.schedule, schedule.timezone, now)
      _        <- scheduledRepo.updateRunTimes(schedule.id, now, nextRun)
      _        <- ZIO.logInfo(s"Расписание '${schedule.name}' выполнено, следующий запуск: $nextRun")
    } yield result

  /** Подсчёт периода отчёта на основе типа периода */
  private def calculatePeriod(periodType: PeriodType, timezone: String): Task[DateRange] =
    ZIO.attempt {
      val zone = ZoneId.of(timezone)
      val today = LocalDate.now(zone)
      periodType match
        case PeriodType.Yesterday =>
          val yesterday = today.minusDays(1)
          DateRange(
            yesterday.atStartOfDay(zone).toInstant,
            yesterday.plusDays(1).atStartOfDay(zone).toInstant
          )
        case PeriodType.LastWeek =>
          val weekStart = today.minusDays(7)
          val weekEnd = today.minusDays(1)
          DateRange(
            weekStart.atStartOfDay(zone).toInstant,
            weekEnd.plusDays(1).atStartOfDay(zone).toInstant
          )
        case PeriodType.LastMonth =>
          val monthStart = today.minusMonths(1).withDayOfMonth(1)
          val monthEnd = today.minusDays(1)
          DateRange(
            monthStart.atStartOfDay(zone).toInstant,
            monthEnd.plusDays(1).atStartOfDay(zone).toInstant
          )
        case PeriodType.Custom =>
          // Для Custom используем вчерашний день как дефолт
          val yesterday = today.minusDays(1)
          DateRange(
            yesterday.atStartOfDay(zone).toInstant,
            yesterday.plusDays(1).atStartOfDay(zone).toInstant
          )
    }

  /** Рассчитать следующий запуск на основе cron-выражения */
  private def calculateNextRun(cronExpression: String, timezone: String, from: Instant): Instant =
    // Упрощённая логика — в продакшн использовать cron4s
    // Парсим базовые случаи
    cronExpression.trim match
      case s if s.startsWith("0 0 * * *") =>
        // Ежедневно в полночь
        from.plusSeconds(86400)
      case s if s.startsWith("0 0 * * 1") =>
        // Еженедельно (понедельники)
        from.plusSeconds(7 * 86400)
      case s if s.startsWith("0 0 1 * *") =>
        // Ежемесячно (1 числа)
        from.plusSeconds(30 * 86400)
      case _ =>
        // По умолчанию — через 24 часа
        from.plusSeconds(86400)
