package com.wayrecall.tracker.analytics.repository

import com.wayrecall.tracker.analytics.domain.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import zio.*
import zio.interop.catz.*
import java.time.Instant

// ============================================================
// Repository: Расписания отчётов (PostgreSQL)
// ============================================================

trait ScheduledReportRepository:
  /** Получить все расписания организации */
  def findByOrganization(orgId: Long): Task[List[ScheduledReport]]

  /** Получить расписание по ID */
  def findById(id: Long, orgId: Long): Task[Option[ScheduledReport]]

  /** Создать расписание */
  def create(orgId: Long, req: CreateScheduledReport): Task[ScheduledReport]

  /** Обновить расписание */
  def update(id: Long, orgId: Long, req: CreateScheduledReport): Task[Option[ScheduledReport]]

  /** Удалить расписание */
  def delete(id: Long, orgId: Long): Task[Boolean]

  /** Получить расписания, которые пора запустить */
  def findDueSchedules(now: Instant): Task[List[ScheduledReport]]

  /** Обновить время последнего и следующего запуска */
  def updateRunTimes(id: Long, lastRunAt: Instant, nextRunAt: Instant): Task[Unit]

object ScheduledReportRepository:

  val live: ZLayer[Transactor[Task], Nothing, ScheduledReportRepository] =
    ZLayer.fromFunction { (xa: Transactor[Task]) =>
      new ScheduledReportRepositoryLive(xa)
    }

final class ScheduledReportRepositoryLive(xa: Transactor[Task]) extends ScheduledReportRepository:

  override def findByOrganization(orgId: Long): Task[List[ScheduledReport]] =
    sql"""
      SELECT id, name, organization_id, template_id, schedule, timezone, report_type,
             vehicle_ids, group_ids, period_type, delivery_channels, recipients,
             export_format, enabled, last_run_at, next_run_at, created_at
      FROM scheduled_reports
      WHERE organization_id = $orgId
      ORDER BY created_at DESC
    """.query[ScheduledReport].to[List].transact(xa)

  override def findById(id: Long, orgId: Long): Task[Option[ScheduledReport]] =
    sql"""
      SELECT id, name, organization_id, template_id, schedule, timezone, report_type,
             vehicle_ids, group_ids, period_type, delivery_channels, recipients,
             export_format, enabled, last_run_at, next_run_at, created_at
      FROM scheduled_reports
      WHERE id = $id AND organization_id = $orgId
    """.query[ScheduledReport].option.transact(xa)

  override def create(orgId: Long, req: CreateScheduledReport): Task[ScheduledReport] =
    val now = Instant.now()
    val rt  = req.reportType.toString.toLowerCase
    val ef  = req.exportFormat.toString.toLowerCase
    val pt  = req.periodType match
      case PeriodType.Yesterday => "yesterday"
      case PeriodType.LastWeek  => "last_week"
      case PeriodType.LastMonth => "last_month"
      case PeriodType.Custom    => "custom"
    val tz = req.timezone.getOrElse("Europe/Moscow")

    sql"""
      INSERT INTO scheduled_reports 
        (name, organization_id, template_id, schedule, timezone, report_type,
         vehicle_ids, group_ids, period_type, delivery_channels, recipients,
         export_format, enabled, created_at)
      VALUES 
        (${req.name}, $orgId, ${req.templateId}, ${req.schedule}, $tz, $rt,
         ${req.vehicleIds.toArray}, ${req.groupIds.toArray}, $pt, 
         ${req.deliveryChannels.toArray}, ${req.recipients}::jsonb,
         $ef, true, $now)
      RETURNING id, name, organization_id, template_id, schedule, timezone, report_type,
                vehicle_ids, group_ids, period_type, delivery_channels, recipients,
                export_format, enabled, last_run_at, next_run_at, created_at
    """.query[ScheduledReport].unique.transact(xa)

  override def update(id: Long, orgId: Long, req: CreateScheduledReport): Task[Option[ScheduledReport]] =
    val rt = req.reportType.toString.toLowerCase
    val ef = req.exportFormat.toString.toLowerCase
    val pt = req.periodType match
      case PeriodType.Yesterday => "yesterday"
      case PeriodType.LastWeek  => "last_week"
      case PeriodType.LastMonth => "last_month"
      case PeriodType.Custom    => "custom"
    val tz = req.timezone.getOrElse("Europe/Moscow")

    val updateQuery = sql"""
      UPDATE scheduled_reports SET
        name = ${req.name},
        template_id = ${req.templateId},
        schedule = ${req.schedule},
        timezone = $tz,
        report_type = $rt,
        vehicle_ids = ${req.vehicleIds.toArray},
        group_ids = ${req.groupIds.toArray},
        period_type = $pt,
        delivery_channels = ${req.deliveryChannels.toArray},
        recipients = ${req.recipients}::jsonb,
        export_format = $ef
      WHERE id = $id AND organization_id = $orgId
    """.update.run.transact(xa)

    updateQuery.flatMap { rowsAffected =>
      if rowsAffected > 0 then findById(id, orgId)
      else ZIO.succeed(None)
    }

  override def delete(id: Long, orgId: Long): Task[Boolean] =
    sql"""
      DELETE FROM scheduled_reports WHERE id = $id AND organization_id = $orgId
    """.update.run.transact(xa).map(_ > 0)

  override def findDueSchedules(now: Instant): Task[List[ScheduledReport]] =
    sql"""
      SELECT id, name, organization_id, template_id, schedule, timezone, report_type,
             vehicle_ids, group_ids, period_type, delivery_channels, recipients,
             export_format, enabled, last_run_at, next_run_at, created_at
      FROM scheduled_reports
      WHERE enabled = true AND (next_run_at IS NULL OR next_run_at <= $now)
    """.query[ScheduledReport].to[List].transact(xa)

  override def updateRunTimes(id: Long, lastRunAt: Instant, nextRunAt: Instant): Task[Unit] =
    sql"""
      UPDATE scheduled_reports SET last_run_at = $lastRunAt, next_run_at = $nextRunAt
      WHERE id = $id
    """.update.run.transact(xa).unit

  // Doobie Read для ScheduledReport
  given Read[ScheduledReport] = Read[
    (Long, String, Long, Option[Long], String, String, String,
     Array[Long], Array[Long], String, Array[String], String,
     String, Boolean, Option[Instant], Option[Instant], Instant)
  ].map { case (id, name, orgId, templateId, schedule, tz, rt,
                vehicleIds, groupIds, pt, dc, recipients,
                ef, enabled, lastRunAt, nextRunAt, createdAt) =>
    ScheduledReport(
      id = id,
      name = name,
      organizationId = orgId,
      templateId = templateId,
      schedule = schedule,
      timezone = tz,
      reportType = ReportType.values.find(_.toString.equalsIgnoreCase(rt)).getOrElse(ReportType.Mileage),
      vehicleIds = vehicleIds.toList,
      groupIds = groupIds.toList,
      periodType = pt match {
        case "yesterday"  => PeriodType.Yesterday
        case "last_week"  => PeriodType.LastWeek
        case "last_month" => PeriodType.LastMonth
        case _            => PeriodType.Custom
      },
      deliveryChannels = dc.toList,
      recipients = recipients,
      exportFormat = ExportFormat.values.find(_.toString.equalsIgnoreCase(ef)).getOrElse(ExportFormat.Xlsx),
      enabled = enabled,
      lastRunAt = lastRunAt,
      nextRunAt = nextRunAt,
      createdAt = createdAt
    )
  }
