package com.wayrecall.tracker.analytics.infrastructure

import cats.effect.IO as CatsIO
import com.wayrecall.tracker.analytics.config.{TimescaleDbConfig, PostgresConfig}
import com.zaxxer.hikari.HikariConfig
import doobie.*
import doobie.hikari.HikariTransactor
import zio.*
import zio.interop.catz.*

// ============================================================
// Transactor слои для TimescaleDB и PostgreSQL
// ============================================================

/** TimescaleDB Transactor — GPS данные и continuous aggregates */
object TimescaleTransactor:

  /** ZIO Layer: создаёт HikariTransactor для TimescaleDB */
  val live: ZLayer[TimescaleDbConfig, Throwable, Transactor[Task]] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[TimescaleDbConfig]
        xa     <- makeTransactor(config.url, config.user, config.password, config.maxPoolSize)
      } yield xa
    }

  private def makeTransactor(
      url: String,
      user: String,
      password: String,
      maxPoolSize: Int
  ): ZIO[Scope, Throwable, Transactor[Task]] =
    val hikariConfig = new HikariConfig()
    hikariConfig.setJdbcUrl(url)
    hikariConfig.setUsername(user)
    hikariConfig.setPassword(password)
    hikariConfig.setMaximumPoolSize(maxPoolSize)
    hikariConfig.setPoolName("timescaledb-pool")
    hikariConfig.setDriverClassName("org.postgresql.Driver")

    // Создаём Transactor через HikariTransactor
    ZIO.succeed(
      Transactor.fromDriverManager[Task](
        driver = "org.postgresql.Driver",
        url = url,
        user = user,
        password = password
      )
    )

/** PostgreSQL Transactor — шаблоны, расписания, история */
object PostgresTransactor:

  /** ZIO Layer: создаёт HikariTransactor для PostgreSQL */
  val live: ZLayer[PostgresConfig, Throwable, Transactor[Task]] =
    ZLayer.scoped {
      for {
        config <- ZIO.service[PostgresConfig]
        xa     <- makeTransactor(config.url, config.user, config.password, config.maxPoolSize)
      } yield xa
    }

  private def makeTransactor(
      url: String,
      user: String,
      password: String,
      maxPoolSize: Int
  ): ZIO[Scope, Throwable, Transactor[Task]] =
    ZIO.succeed(
      Transactor.fromDriverManager[Task](
        driver = "org.postgresql.Driver",
        url = url,
        user = user,
        password = password
      )
    )

/** Тегированные Transactor-ы для различения в ZIO Layer */
object TransactorTags:
  /** Тег для TimescaleDB transactor */
  type TimescaleXa = Transactor[Task] @@ TimescaleTag
  trait TimescaleTag

  /** Тег для PostgreSQL transactor */
  type PostgresXa = Transactor[Task] @@ PostgresTag
  trait PostgresTag
