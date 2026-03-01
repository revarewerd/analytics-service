package com.wayrecall.tracker.analytics.api

import zio.*
import zio.http.*
import zio.json.*

// ============================================================
// HealthRoutes — эндпоинты здоровья и метрик
// GET /health — liveness probe
// GET /ready — readiness probe
// ============================================================

object HealthRoutes:

  val routes: Routes[Any, Nothing] = Routes(
    // Liveness
    Method.GET / "health" -> handler {
      Response.json("""{"status":"ok","service":"analytics-service"}""")
    },
    // Readiness (можно расширить проверками БД и Redis)
    Method.GET / "ready" -> handler {
      Response.json("""{"status":"ready","service":"analytics-service"}""")
    }
  )
