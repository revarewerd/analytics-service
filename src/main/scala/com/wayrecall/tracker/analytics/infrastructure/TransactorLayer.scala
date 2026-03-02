package com.wayrecall.tracker.analytics.infrastructure

import com.wayrecall.tracker.analytics.config.AppConfig
import doobie.*
import zio.*
import zio.interop.catz.*

// ============================================================
// TransactorLayer — единый Transactor для PostgreSQL/TimescaleDB
// В MVP оба хранилища живут на одном инстансе PostgreSQL
// ============================================================

object TransactorLayer:

  /** Единый транзактор (используется и для TimescaleDB, и для PostgreSQL) */
  val live: ZLayer[AppConfig, Throwable, Transactor[Task]] =
    ZLayer.fromZIO {
      for {
        config <- ZIO.service[AppConfig]
        xa = Transactor.fromDriverManager[Task](
          driver = "org.postgresql.Driver",
          url = config.postgres.url,
          user = config.postgres.user,
          password = config.postgres.password,
          logHandler = None
        )
      } yield xa
    }

  /** Алиас для совместимости — TimescaleDB */
  val timescale: ZLayer[AppConfig, Throwable, Transactor[Task]] = live

  /** Алиас для совместимости — PostgreSQL */
  val postgres: ZLayer[AppConfig, Throwable, Transactor[Task]] = live
