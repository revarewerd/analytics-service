package com.wayrecall.tracker.analytics.cache

import com.wayrecall.tracker.analytics.config.CacheConfig
import zio.*
import zio.redis.*
import zio.json.*
import java.time.{Instant, LocalDate}
import java.security.MessageDigest

// ============================================================
// ReportCache — кеширование отчётов в Redis
// ============================================================

trait ReportCache:
  /** Получить кешированный отчёт по параметрам */
  def get[A: JsonDecoder](reportType: String, paramsHash: String): Task[Option[A]]

  /** Сохранить отчёт в кеш (TTL зависит от периода) */
  def set[A: JsonEncoder](reportType: String, paramsHash: String, report: A, includesCurrentDay: Boolean): Task[Unit]

  /** Получить статус задачи экспорта */
  def getExportTaskStatus(taskId: String): Task[Option[Map[String, String]]]

  /** Создать/обновить задачу экспорта */
  def setExportTask(taskId: String, fields: Map[String, String]): Task[Unit]

  /** Обновить прогресс экспорта */
  def updateExportProgress(taskId: String, progress: Int): Task[Unit]

  /** Завершить задачу экспорта (успех) */
  def completeExportTask(taskId: String, fileUrl: String): Task[Unit]

  /** Завершить задачу экспорта (ошибка) */
  def failExportTask(taskId: String, error: String): Task[Unit]

  /** Удалить кеш по ключу */
  def invalidate(reportType: String, paramsHash: String): Task[Unit]

object ReportCache:

  /** Генерация hash для параметров запроса */
  def hashParams(params: String): String =
    val digest = MessageDigest.getInstance("MD5")
    val bytes  = digest.digest(params.getBytes("UTF-8"))
    bytes.map("%02x".format(_)).mkString

  val live: ZLayer[Redis & CacheConfig, Nothing, ReportCache] =
    ZLayer.fromFunction { (redis: Redis, config: CacheConfig) =>
      new ReportCacheLive(redis, config)
    }

final class ReportCacheLive(redis: Redis, config: CacheConfig) extends ReportCache:

  private val exportTaskTtl = 24 * 3600 // 24 часа в секундах

  override def get[A: JsonDecoder](reportType: String, paramsHash: String): Task[Option[A]] =
    val key = s"report:$reportType:$paramsHash"
    redis.get(key).returning[String].flatMap {
      case Some(json) =>
        ZIO.fromEither(json.fromJson[A])
          .map(Some(_))
          .catchAll(_ => ZIO.succeed(None)) // Если десериализация не удалась — cache miss
      case None => ZIO.succeed(None)
    }.catchAll { e =>
      ZIO.logWarning(s"Ошибка чтения кеша: ${e.getMessage}") *> ZIO.succeed(None)
    }

  override def set[A: JsonEncoder](
      reportType: String,
      paramsHash: String,
      report: A,
      includesCurrentDay: Boolean
  ): Task[Unit] =
    val key = s"report:$reportType:$paramsHash"
    val json = report.toJson
    // Исторические данные — длинный TTL, текущий день — короткий
    val ttl  = if includesCurrentDay then config.realtimeTtlSeconds else config.historicalTtlSeconds
    redis.set(key, json, expireAt = Some(zio.redis.api.Expiration.EX(ttl.toLong))).unit
      .catchAll(e => ZIO.logWarning(s"Ошибка записи кеша: ${e.getMessage}"))

  override def getExportTaskStatus(taskId: String): Task[Option[Map[String, String]]] =
    val key = s"export:$taskId"
    redis.hGetAll(key).returning[String].map { result =>
      if result.isEmpty then None
      else Some(result.toMap)
    }.catchAll { e =>
      ZIO.logWarning(s"Ошибка чтения экспорт задачи: ${e.getMessage}") *> ZIO.succeed(None)
    }

  override def setExportTask(taskId: String, fields: Map[String, String]): Task[Unit] =
    val key = s"export:$taskId"
    ZIO.foreachDiscard(fields.toList) { case (field, value) =>
      redis.hSet(key, (field, value))
    } *> redis.expire(key, zio.Duration.fromSeconds(exportTaskTtl)).unit
      .catchAll(e => ZIO.logWarning(s"Ошибка записи задачи экспорта: ${e.getMessage}"))

  override def updateExportProgress(taskId: String, progress: Int): Task[Unit] =
    val key = s"export:$taskId"
    redis.hSet(key, ("progress", progress.toString)).unit
      .catchAll(e => ZIO.logWarning(s"Ошибка обновления прогресса: ${e.getMessage}"))

  override def completeExportTask(taskId: String, fileUrl: String): Task[Unit] =
    val key = s"export:$taskId"
    ZIO.foreachDiscard(List(
      ("status", "completed"),
      ("progress", "100"),
      ("file_url", fileUrl)
    )) { case (field, value) =>
      redis.hSet(key, (field, value))
    }.catchAll(e => ZIO.logWarning(s"Ошибка завершения экспорта: ${e.getMessage}"))

  override def failExportTask(taskId: String, error: String): Task[Unit] =
    val key = s"export:$taskId"
    ZIO.foreachDiscard(List(
      ("status", "failed"),
      ("error", error)
    )) { case (field, value) =>
      redis.hSet(key, (field, value))
    }.catchAll(e => ZIO.logWarning(s"Ошибка сохранения ошибки экспорта: ${e.getMessage}"))

  override def invalidate(reportType: String, paramsHash: String): Task[Unit] =
    val key = s"report:$reportType:$paramsHash"
    redis.del(key).unit
      .catchAll(e => ZIO.logWarning(s"Ошибка инвалидации кеша: ${e.getMessage}"))
