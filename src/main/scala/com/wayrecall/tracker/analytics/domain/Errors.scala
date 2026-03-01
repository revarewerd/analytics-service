package com.wayrecall.tracker.analytics.domain

import zio.json.*

// ============================================================
// Ошибки Analytics Service
// ============================================================

/** Типизированные ошибки сервиса аналитики */
sealed trait AnalyticsError extends Exception:
  def message: String
  override def getMessage: String = message

object AnalyticsError:

  /** Транспорт не найден */
  final case class VehicleNotFound(vehicleId: Long) extends AnalyticsError:
    val message = s"Транспорт не найден: $vehicleId"

  /** Организация не найдена */
  final case class OrganizationNotFound(orgId: Long) extends AnalyticsError:
    val message = s"Организация не найдена: $orgId"

  /** Неверный диапазон дат */
  final case class InvalidDateRange(details: String) extends AnalyticsError:
    val message = s"Неверный диапазон дат: $details"

  /** Неверные параметры запроса */
  final case class InvalidParameters(details: String) extends AnalyticsError:
    val message = s"Неверные параметры: $details"

  /** Шаблон не найден */
  final case class TemplateNotFound(templateId: Long) extends AnalyticsError:
    val message = s"Шаблон не найден: $templateId"

  /** Расписание не найдено */
  final case class ScheduleNotFound(scheduleId: Long) extends AnalyticsError:
    val message = s"Расписание не найдено: $scheduleId"

  /** Задача экспорта не найдена */
  final case class ExportTaskNotFound(taskId: String) extends AnalyticsError:
    val message = s"Задача экспорта не найдена: $taskId"

  /** Файл не найден */
  final case class FileNotFound(fileUrl: String) extends AnalyticsError:
    val message = s"Файл не найден: $fileUrl"

  /** Ошибка генерации отчёта */
  final case class ReportGenerationError(reportType: String, cause: String) extends AnalyticsError:
    val message = s"Ошибка генерации отчёта $reportType: $cause"

  /** Ошибка экспорта */
  final case class ExportError(format: String, cause: String) extends AnalyticsError:
    val message = s"Ошибка экспорта в формат $format: $cause"

  /** Ошибка S3/MinIO */
  final case class StorageError(operation: String, cause: String) extends AnalyticsError:
    val message = s"Ошибка хранилища ($operation): $cause"

  /** Ошибка кеша */
  final case class CacheError(cause: String) extends AnalyticsError:
    val message = s"Ошибка кеша: $cause"

  /** Ошибка базы данных */
  final case class DatabaseError(cause: String) extends AnalyticsError:
    val message = s"Ошибка БД: $cause"

  /** Превышен лимит параллельных экспортов */
  final case class ExportLimitExceeded(maxConcurrent: Int) extends AnalyticsError:
    val message = s"Превышен лимит параллельных экспортов: $maxConcurrent"

  /** Неверный cron выражение */
  final case class InvalidCronExpression(cron: String, cause: String) extends AnalyticsError:
    val message = s"Неверное cron выражение '$cron': $cause"

  // JSON сериализация ошибок для API ответов
  final case class ErrorResponse(error: String, message: String) derives JsonCodec
