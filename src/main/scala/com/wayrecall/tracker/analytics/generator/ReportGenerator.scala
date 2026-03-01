package com.wayrecall.tracker.analytics.generator

import com.wayrecall.tracker.analytics.domain.*
import zio.*

// ============================================================
// ReportGenerator — общий трейт для генераторов отчётов
// ============================================================

/** Генератор отчёта определённого типа */
trait ReportGenerator[R]:
  /** Генерация отчёта по параметрам для одного ТС */
  def generate(params: ReportParams, vehicleId: Long): Task[R]
