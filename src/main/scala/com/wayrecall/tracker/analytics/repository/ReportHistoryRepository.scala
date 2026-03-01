package com.wayrecall.tracker.analytics.repository

import com.wayrecall.tracker.analytics.domain.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import zio.*
import zio.interop.catz.*
import java.time.Instant

// ============================================================
// Repository: История отчётов (PostgreSQL / TimescaleDB)
// ============================================================

trait ReportHistoryRepository:
  /** Получить историю по организации с пагинацией */
  def findByOrganization(
      orgId: Long,
      from: Option[Instant],
      to: Option[Instant],
      status: Option[ReportHistoryStatus],
      limit: Int
  ): Task[List[ReportHistory]]

  /** Создать запись в истории */
  def create(
      orgId: Long,
      userId: Option[Long],
      scheduledId: Option[Long],
      reportType: ReportType,
      parameters: String,
      status: ReportHistoryStatus
  ): Task[ReportHistory]

  /** Обновить статус записи (при завершении или ошибке) */
  def updateStatus(
      id: Long,
      status: ReportHistoryStatus,
      fileUrl: Option[String],
      fileSize: Option[Long],
      errorMessage: Option[String]
  ): Task[Unit]

  /** Получить запись по ID */
  def findById(id: Long, orgId: Long): Task[Option[ReportHistory]]

object ReportHistoryRepository:

  val live: ZLayer[Transactor[Task], Nothing, ReportHistoryRepository] =
    ZLayer.fromFunction { (xa: Transactor[Task]) =>
      new ReportHistoryRepositoryLive(xa)
    }

final class ReportHistoryRepositoryLive(xa: Transactor[Task]) extends ReportHistoryRepository:

  override def findByOrganization(
      orgId: Long,
      from: Option[Instant],
      to: Option[Instant],
      status: Option[ReportHistoryStatus],
      limit: Int
  ): Task[List[ReportHistory]] =
    // Собираем SQL динамически по фильтрам
    val baseQuery = fr"""
      SELECT id, organization_id, user_id, scheduled_id, report_type, parameters,
             status, file_url, file_size, error_message, created_at, completed_at, expires_at
      FROM report_history
      WHERE organization_id = $orgId
    """
    val fromClause   = from.map(f => fr"AND created_at >= $f").getOrElse(fr"")
    val toClause     = to.map(t => fr"AND created_at <= $t").getOrElse(fr"")
    val statusClause = status.map(s => fr"AND status = ${s.toString.toLowerCase}").getOrElse(fr"")
    val orderLimit   = fr"ORDER BY created_at DESC LIMIT $limit"

    (baseQuery ++ fromClause ++ toClause ++ statusClause ++ orderLimit)
      .query[ReportHistory].to[List].transact(xa)

  override def create(
      orgId: Long,
      userId: Option[Long],
      scheduledId: Option[Long],
      reportType: ReportType,
      parameters: String,
      status: ReportHistoryStatus
  ): Task[ReportHistory] =
    val now = Instant.now()
    val rt  = reportType.toString.toLowerCase
    val st  = status.toString.toLowerCase
    sql"""
      INSERT INTO report_history 
        (organization_id, user_id, scheduled_id, report_type, parameters, status, created_at)
      VALUES ($orgId, $userId, $scheduledId, $rt, $parameters::jsonb, $st, $now)
      RETURNING id, organization_id, user_id, scheduled_id, report_type, parameters,
                status, file_url, file_size, error_message, created_at, completed_at, expires_at
    """.query[ReportHistory].unique.transact(xa)

  override def updateStatus(
      id: Long,
      status: ReportHistoryStatus,
      fileUrl: Option[String],
      fileSize: Option[Long],
      errorMessage: Option[String]
  ): Task[Unit] =
    val now  = Instant.now()
    val st   = status.toString.toLowerCase
    val expiry = now.plusSeconds(7 * 24 * 3600) // 7 дней
    sql"""
      UPDATE report_history SET
        status = $st,
        file_url = $fileUrl,
        file_size = $fileSize,
        error_message = $errorMessage,
        completed_at = $now,
        expires_at = $expiry
      WHERE id = $id
    """.update.run.transact(xa).unit

  override def findById(id: Long, orgId: Long): Task[Option[ReportHistory]] =
    sql"""
      SELECT id, organization_id, user_id, scheduled_id, report_type, parameters,
             status, file_url, file_size, error_message, created_at, completed_at, expires_at
      FROM report_history
      WHERE id = $id AND organization_id = $orgId
    """.query[ReportHistory].option.transact(xa)

  // Doobie Read для ReportHistory
  given Read[ReportHistory] = Read[
    (Long, Long, Option[Long], Option[Long], String, String,
     String, Option[String], Option[Long], Option[String],
     Instant, Option[Instant], Option[Instant])
  ].map { case (id, orgId, userId, scheduledId, rt, params,
                st, fileUrl, fileSize, errorMsg,
                createdAt, completedAt, expiresAt) =>
    ReportHistory(
      id = id,
      organizationId = orgId,
      userId = userId,
      scheduledId = scheduledId,
      reportType = ReportType.values.find(_.toString.equalsIgnoreCase(rt)).getOrElse(ReportType.Mileage),
      parameters = params,
      status = ReportHistoryStatus.values.find(_.toString.equalsIgnoreCase(st))
        .getOrElse(ReportHistoryStatus.Pending),
      fileUrl = fileUrl,
      fileSize = fileSize,
      errorMessage = errorMsg,
      createdAt = createdAt,
      completedAt = completedAt,
      expiresAt = expiresAt
    )
  }
