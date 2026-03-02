package com.wayrecall.tracker.analytics.config

import zio.*
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*

// ============================================================
// Конфигурация Analytics Service
// ============================================================

/** Настройки TimescaleDB (GPS данные, continuous aggregates) */
final case class TimescaleDbConfig(
    url: String,
    user: String,
    password: String,
    maxPoolSize: Int
)

/** Настройки PostgreSQL (шаблоны, расписания, история) */
final case class PostgresConfig(
    url: String,
    user: String,
    password: String,
    maxPoolSize: Int
)

/** Настройки Redis (кеш отчётов, задачи экспорта) */
final case class RedisConfig(
    host: String,
    port: Int
)

/** Настройки S3/MinIO (хранилище файлов экспорта) */
final case class S3Config(
    endpoint: String,
    accessKey: String,
    secretKey: String,
    bucket: String,
    presignedUrlTtlHours: Int
)

/** Настройки кеширования */
final case class CacheConfig(
    historicalTtlSeconds: Int,
    realtimeTtlSeconds: Int
)

/** Настройки экспорта */
final case class ExportConfig(
    maxConcurrent: Int,
    tempDir: String,
    maxFileSizeMb: Int,
    retentionDays: Int
)

/** Настройки планировщика */
final case class SchedulerConfig(
    enabled: Boolean,
    checkIntervalSeconds: Int,
    maxConcurrentSchedules: Int
)

/** Настройки HTTP сервера */
final case class ServerConfig(
    port: Int
)

/** Корневая конфигурация сервиса */
final case class AppConfig(
    timescaledb: TimescaleDbConfig,
    postgres: PostgresConfig,
    redis: RedisConfig,
    s3: S3Config,
    cache: CacheConfig,
    `export`: ExportConfig,
    scheduler: SchedulerConfig,
    server: ServerConfig
)

object AppConfig:
  /** Загрузка конфигурации из application.conf */
  val live: ZLayer[Any, Config.Error, AppConfig] =
    ZLayer.fromZIO(
      ZIO.config[AppConfig](
        deriveConfig[AppConfig].mapKey(toKebabCase).nested("analytics-service")
      )
    )
