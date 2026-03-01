package com.wayrecall.tracker.analytics.generator

import com.wayrecall.tracker.analytics.domain.*
import com.wayrecall.tracker.analytics.query.QueryEngine
import zio.*

// ============================================================
// GeozoneReportGenerator — генератор отчёта по геозонам
// ============================================================

trait GeozoneReportGenerator extends ReportGenerator[GeozoneReport]

object GeozoneReportGenerator:

  val live: ZLayer[QueryEngine, Nothing, GeozoneReportGenerator] =
    ZLayer.fromFunction { (queryEngine: QueryEngine) =>
      new GeozoneReportGeneratorLive(queryEngine)
    }

final class GeozoneReportGeneratorLive(queryEngine: QueryEngine) extends GeozoneReportGenerator:

  /**
   * Генерация отчёта по геозонам.
   * 
   * В MVP версии — используются данные из geozone-events (через PostgreSQL),
   * которые записывает Rule Checker. Для полной реализации нужна связь
   * c таблицей geozone_events.
   * 
   * Пока: заглушка с пустыми данными — будет заполнено когда
   * Rule Checker начнёт писать события в PostgreSQL.
   */
  override def generate(params: ReportParams, vehicleId: Long): Task[GeozoneReport] =
    // TODO: Кода Rule Checker будет записывать geozone events в PostgreSQL,
    // подключить реальный запрос к таблице geozone_visits
    ZIO.logInfo(s"Генерация отчёта по геозонам для ТС $vehicleId") *>
      ZIO.succeed(
        GeozoneReport(
          vehicleId = vehicleId,
          period = DateRange(params.from, params.to),
          geozones = Nil,
          visits = Nil
        )
      )
