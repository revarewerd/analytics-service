package com.wayrecall.tracker.analytics.repository

import com.wayrecall.tracker.analytics.domain.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import zio.*
import zio.interop.catz.*
import java.time.Instant

// ============================================================
// Repository: Шаблоны отчётов (PostgreSQL)
// ============================================================

trait ReportTemplateRepository:
  /** Получить все шаблоны организации */
  def findByOrganization(orgId: Long): Task[List[ReportTemplate]]

  /** Получить шаблон по ID */
  def findById(id: Long, orgId: Long): Task[Option[ReportTemplate]]

  /** Создать шаблон */
  def create(
      name: String,
      orgId: Long,
      reportType: ReportType,
      config: String,
      defaultFilters: String,
      createdBy: Long
  ): Task[ReportTemplate]

object ReportTemplateRepository:

  val live: ZLayer[Transactor[Task], Nothing, ReportTemplateRepository] =
    ZLayer.fromFunction { (xa: Transactor[Task]) =>
      new ReportTemplateRepositoryLive(xa)
    }

final class ReportTemplateRepositoryLive(xa: Transactor[Task]) extends ReportTemplateRepository:

  override def findByOrganization(orgId: Long): Task[List[ReportTemplate]] =
    sql"""
      SELECT id, name, organization_id, report_type, config, default_filters, created_at, created_by
      FROM report_templates
      WHERE organization_id = $orgId
      ORDER BY created_at DESC
    """.query[ReportTemplate].to[List].transact(xa)

  override def findById(id: Long, orgId: Long): Task[Option[ReportTemplate]] =
    sql"""
      SELECT id, name, organization_id, report_type, config, default_filters, created_at, created_by
      FROM report_templates
      WHERE id = $id AND organization_id = $orgId
    """.query[ReportTemplate].option.transact(xa)

  override def create(
      name: String,
      orgId: Long,
      reportType: ReportType,
      config: String,
      defaultFilters: String,
      createdBy: Long
  ): Task[ReportTemplate] =
    val now = Instant.now()
    val rt  = reportType.toString.toLowerCase
    sql"""
      INSERT INTO report_templates (name, organization_id, report_type, config, default_filters, created_at, created_by)
      VALUES ($name, $orgId, $rt, $config::jsonb, $defaultFilters::jsonb, $now, $createdBy)
      RETURNING id, name, organization_id, report_type, config, default_filters, created_at, created_by
    """.query[ReportTemplate].unique.transact(xa)

  // Doobie Reads для domain типов
  given Read[ReportTemplate] = Read[(Long, String, Long, String, String, String, Instant, Long)].map {
    case (id, name, orgId, rt, config, filters, createdAt, createdBy) =>
      ReportTemplate(
        id = id,
        name = name,
        organizationId = orgId,
        reportType = ReportType.values.find(_.toString.equalsIgnoreCase(rt)).getOrElse(ReportType.Mileage),
        config = config,
        defaultFilters = filters,
        createdAt = createdAt,
        createdBy = createdBy
      )
  }
