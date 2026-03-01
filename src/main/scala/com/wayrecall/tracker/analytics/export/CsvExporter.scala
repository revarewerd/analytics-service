package com.wayrecall.tracker.analytics.export

import com.wayrecall.tracker.analytics.domain.*
import zio.*
import java.io.{File, PrintWriter}
import java.time.format.DateTimeFormatter

// ============================================================
// CsvExporter — генерация CSV файлов
// ============================================================

object CsvExporter:

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  /** Экспорт отчёта по пробегу в CSV */
  def exportMileage(report: MileageReport, filePath: String): Task[File] =
    ZIO.attemptBlocking {
      val file = new File(filePath)
      file.getParentFile.mkdirs()
      val writer = new PrintWriter(file, "UTF-8")
      try
        // BOM для корректного открытия в Excel
        writer.print("\uFEFF")
        writer.println("Дата;Пробег (км);Средняя скорость;Макс. скорость;Моточасы;Точек")
        report.dailyData.foreach { d =>
          writer.println(
            s"${d.date.format(dateFormatter)};${d.mileageKm};${d.avgSpeed};${d.maxSpeed};${d.engineHours};${d.pointCount}"
          )
        }
        // Итого
        writer.println()
        writer.println(s"Итого пробег (км);${report.totalMileageKm}")
        writer.println(s"Итого моточасы;${report.totalEngineHours}")
        writer.println(s"Средняя скорость;${report.averageSpeed}")
        writer.println(s"Макс. скорость;${report.maxSpeed}")
      finally writer.close()
      file
    }

  /** Экспорт отчёта по топливу в CSV */
  def exportFuel(report: FuelReport, filePath: String): Task[File] =
    ZIO.attemptBlocking {
      val file = new File(filePath)
      file.getParentFile.mkdirs()
      val writer = new PrintWriter(file, "UTF-8")
      try
        writer.print("\uFEFF")
        writer.println("Дата;Расход (л);Пробег (км);Расход/100км")
        report.dailyConsumption.foreach { d =>
          writer.println(
            s"${d.date.format(dateFormatter)};${d.consumedLiters};${d.mileageKm};${d.avgConsumptionPer100km}"
          )
        }
        writer.println()
        writer.println(s"Итого расход (л);${report.totalConsumedLiters}")
        writer.println(s"Итого заправлено (л);${report.totalRefueledLiters}")
        writer.println(s"Итого слито (л);${report.totalDrainedLiters}")
        writer.println(s"Средний расход/100км;${report.avgConsumptionPer100km}")
      finally writer.close()
      file
    }

  /** Экспорт сводного отчёта в CSV */
  def exportSummary(report: SummaryReport, filePath: String): Task[File] =
    ZIO.attemptBlocking {
      val file = new File(filePath)
      file.getParentFile.mkdirs()
      val writer = new PrintWriter(file, "UTF-8")
      try
        writer.print("\uFEFF")
        writer.println("ТС ID;Название;Пробег (км);Расход (л);Моточасы;Макс. скорость;Нарушения;Поездки;Простой (мин)")
        report.vehicles.foreach { v =>
          writer.println(
            s"${v.vehicleId};${v.vehicleName};${v.mileageKm};${v.fuelConsumedLiters};${v.engineHours};${v.maxSpeed};${v.speedViolations};${v.tripCount};${v.idleMinutes}"
          )
        }
        writer.println()
        writer.println(s"Всего ТС;${report.totalVehicles}")
        writer.println(s"Общий пробег (км);${report.totalMileageKm}")
        writer.println(s"Общий расход (л);${report.totalFuelConsumedLiters}")
      finally writer.close()
      file
    }

  /** Экспорт отчёта по превышениям скорости в CSV */
  def exportSpeedViolations(report: SpeedViolationsReport, filePath: String): Task[File] =
    ZIO.attemptBlocking {
      val file = new File(filePath)
      file.getParentFile.mkdirs()
      val writer = new PrintWriter(file, "UTF-8")
      try
        writer.print("\uFEFF")
        writer.println("Время;Широта;Долгота;Скорость;Лимит;Превышение;Длительность (сек)")
        report.violations.foreach { v =>
          writer.println(
            s"${v.timestamp};${v.coords.latitude};${v.coords.longitude};${v.actualSpeed};${v.speedLimit};${v.overspeedKmh};${v.durationSeconds}"
          )
        }
        writer.println()
        writer.println(s"Лимит скорости;${report.speedLimit}")
        writer.println(s"Всего нарушений;${report.totalViolations}")
      finally writer.close()
      file
    }
