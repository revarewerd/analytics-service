package com.wayrecall.tracker.analytics.cache

import com.wayrecall.tracker.analytics.domain.ReportParams
import zio.*
import zio.json.*
import java.security.MessageDigest

// ============================================================
// ReportCache — in-memory кеширование отчётов через Ref
// (заменяет Redis для MVP; в продакшне вернуть Redis)
// ============================================================

trait ReportCache:
  /** Получить кешированный отчёт по ключу */
  def get[A: JsonDecoder](key: String): Task[Option[A]]

  /** Сохранить отчёт в кеш */
  def set[A: JsonEncoder](key: String, value: A, params: ReportParams): Task[Unit]

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
  def invalidate(key: String): Task[Unit]

object ReportCache:

  /** Генерация hash для параметров запроса */
  def hashParams(params: String): String =
    val digest = MessageDigest.getInstance("MD5")
    val bytes  = digest.digest(params.getBytes("UTF-8"))
    bytes.map("%02x".format(_)).mkString

  val live: ULayer[ReportCache] =
    ZLayer.fromZIO {
      for {
        rc <- Ref.make(Map.empty[String, String])
        et <- Ref.make(Map.empty[String, Map[String, String]])
      } yield ReportCacheLive(rc, et)
    }

final class ReportCacheLive(
    reportCache: Ref[Map[String, String]],
    exportTasks: Ref[Map[String, Map[String, String]]]
) extends ReportCache:

  override def get[A: JsonDecoder](key: String): Task[Option[A]] =
    reportCache.get.map(_.get(key)).flatMap {
      case Some(json) =>
        ZIO.fromEither(json.fromJson[A])
          .map(Some(_))
          .catchAll(_ => ZIO.succeed(None))
      case None => ZIO.succeed(None)
    }

  override def set[A: JsonEncoder](key: String, value: A, params: ReportParams): Task[Unit] =
    reportCache.update(_ + (key -> value.toJson))

  override def getExportTaskStatus(taskId: String): Task[Option[Map[String, String]]] =
    exportTasks.get.map(_.get(taskId).filter(_.nonEmpty))

  override def setExportTask(taskId: String, fields: Map[String, String]): Task[Unit] =
    exportTasks.update(m => m + (taskId -> (m.getOrElse(taskId, Map.empty) ++ fields)))

  override def updateExportProgress(taskId: String, progress: Int): Task[Unit] =
    exportTasks.update(m =>
      m.updatedWith(taskId)(_.map(_ + ("progress" -> progress.toString)))
    )

  override def completeExportTask(taskId: String, fileUrl: String): Task[Unit] =
    exportTasks.update(m =>
      m.updatedWith(taskId)(_.map(_ ++ Map(
        "status" -> "completed",
        "progress" -> "100",
        "file_url" -> fileUrl
      )))
    )

  override def failExportTask(taskId: String, error: String): Task[Unit] =
    exportTasks.update(m =>
      m.updatedWith(taskId)(_.map(_ ++ Map(
        "status" -> "failed",
        "error" -> error
      )))
    )

  override def invalidate(key: String): Task[Unit] =
    reportCache.update(_ - key)
