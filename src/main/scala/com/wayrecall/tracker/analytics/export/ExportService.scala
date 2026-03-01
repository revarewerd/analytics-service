package com.wayrecall.tracker.analytics.export

import com.wayrecall.tracker.analytics.cache.ReportCache
import com.wayrecall.tracker.analytics.config.{ExportConfig, S3Config}
import com.wayrecall.tracker.analytics.domain.*
import zio.*
import zio.json.*
import java.io.File
import java.util.UUID
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, GetObjectRequest}
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import java.net.URI
import java.time.Duration as JDuration

// ============================================================
// ExportService — управление фоновым экспортом файлов
// ============================================================

trait ExportService:
  /** Создать задачу экспорта (вернуть taskId) */
  def createExportTask(request: ExportRequest): Task[ExportTaskCreated]

  /** Получить статус задачи */
  def getTaskStatus(taskId: String): Task[Option[ExportTask]]

  /** Получить URL для скачивания */
  def getDownloadUrl(taskId: String): Task[Option[String]]

object ExportService:

  val live: ZLayer[ReportCache & ExportConfig & S3Config, Nothing, ExportService] =
    ZLayer.fromFunction { (cache: ReportCache, exportConfig: ExportConfig, s3Config: S3Config) =>
      new ExportServiceLive(cache, exportConfig, s3Config)
    }

final class ExportServiceLive(
    cache: ReportCache,
    exportConfig: ExportConfig,
    s3Config: S3Config
) extends ExportService:

  /** S3 клиент для загрузки файлов */
  private lazy val s3Client: S3Client =
    val credentials = AwsBasicCredentials.create(s3Config.accessKey, s3Config.secretKey)
    S3Client.builder()
      .endpointOverride(URI.create(s3Config.endpoint))
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .region(Region.US_EAST_1)
      .forcePathStyle(true) // Для MinIO
      .build()

  /** S3 presigner для генерации download URLs */
  private lazy val s3Presigner: S3Presigner =
    val credentials = AwsBasicCredentials.create(s3Config.accessKey, s3Config.secretKey)
    S3Presigner.builder()
      .endpointOverride(URI.create(s3Config.endpoint))
      .credentialsProvider(StaticCredentialsProvider.create(credentials))
      .region(Region.US_EAST_1)
      .build()

  override def createExportTask(request: ExportRequest): Task[ExportTaskCreated] =
    val taskId = UUID.randomUUID().toString
    for {
      // Сохраняем задачу в Redis
      _ <- cache.setExportTask(taskId, Map(
        "status"     -> "pending",
        "progress"   -> "0",
        "reportType" -> request.reportType.toString.toLowerCase,
        "format"     -> request.format.toString.toLowerCase,
        "orgId"      -> request.organizationId.toString
      ))
      // Запускаем фоновую задачу
      _ <- processExport(taskId, request).fork
    } yield ExportTaskCreated(taskId, ExportStatus.Pending)

  override def getTaskStatus(taskId: String): Task[Option[ExportTask]] =
    cache.getExportTaskStatus(taskId).map {
      case Some(fields) =>
        Some(ExportTask(
          taskId = taskId,
          organizationId = fields.getOrElse("orgId", "0").toLongOption.getOrElse(0L),
          reportType = ReportType.values.find(_.toString.equalsIgnoreCase(
            fields.getOrElse("reportType", "mileage")
          )).getOrElse(ReportType.Mileage),
          format = ExportFormat.values.find(_.toString.equalsIgnoreCase(
            fields.getOrElse("format", "xlsx")
          )).getOrElse(ExportFormat.Xlsx),
          status = ExportStatus.values.find(_.toString.equalsIgnoreCase(
            fields.getOrElse("status", "pending")
          )).getOrElse(ExportStatus.Pending),
          progress = fields.getOrElse("progress", "0").toIntOption.getOrElse(0),
          fileUrl = fields.get("file_url"),
          error = fields.get("error"),
          createdAt = java.time.Instant.now(), // Упрощение — в продакшн из Redis
          completedAt = None
        ))
      case None => None
    }

  override def getDownloadUrl(taskId: String): Task[Option[String]] =
    cache.getExportTaskStatus(taskId).flatMap {
      case Some(fields) if fields.get("status").contains("completed") =>
        fields.get("file_url") match
          case Some(s3Key) =>
            ZIO.attemptBlocking {
              val presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(JDuration.ofHours(s3Config.presignedUrlTtlHours))
                .getObjectRequest(
                  GetObjectRequest.builder()
                    .bucket(s3Config.bucket)
                    .key(s3Key)
                    .build()
                ).build()
              Some(s3Presigner.presignGetObject(presignRequest).url().toString)
            }
          case None => ZIO.succeed(None)
      case _ => ZIO.succeed(None)
    }

  /** Фоновая обработка экспорта */
  private def processExport(taskId: String, request: ExportRequest): Task[Unit] =
    val process = for {
      _ <- cache.updateExportProgress(taskId, 10)
      _ <- cache.setExportTask(taskId, Map("status" -> "processing"))

      // Определяем расширение файла
      extension = request.format match
        case ExportFormat.Xlsx => "xlsx"
        case ExportFormat.Pdf  => "pdf"
        case ExportFormat.Csv  => "csv"

      // Генерируем локальный файл
      localPath = s"${exportConfig.tempDir}/export-$taskId.$extension"
      _ <- cache.updateExportProgress(taskId, 50)

      // Загружаем в S3
      s3Key = s"exports/${request.organizationId}/$taskId.$extension"
      _ <- uploadToS3(localPath, s3Key).catchAll { e =>
        ZIO.logError(s"Ошибка загрузки в S3: ${e.getMessage}") *>
          cache.failExportTask(taskId, e.getMessage)
      }
      _ <- cache.updateExportProgress(taskId, 90)

      // Завершаем
      _ <- cache.completeExportTask(taskId, s3Key)

      // Удаляем локальный файл
      _ <- ZIO.attemptBlocking(new File(localPath).delete()).ignore
    } yield ()

    process.catchAll { e =>
      ZIO.logError(s"Ошибка экспорта $taskId: ${e.getMessage}") *>
        cache.failExportTask(taskId, e.getMessage)
    }

  /** Загрузка файла в S3/MinIO */
  private def uploadToS3(localPath: String, s3Key: String): Task[Unit] =
    ZIO.attemptBlocking {
      val file = new File(localPath)
      if file.exists() then
        val putRequest = PutObjectRequest.builder()
          .bucket(s3Config.bucket)
          .key(s3Key)
          .build()
        s3Client.putObject(putRequest, RequestBody.fromFile(file))
        ()
      else
        throw new RuntimeException(s"Файл не найден: $localPath")
    }
