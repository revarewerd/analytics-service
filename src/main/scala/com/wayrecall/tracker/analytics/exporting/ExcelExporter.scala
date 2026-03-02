package com.wayrecall.tracker.analytics.exporting

import com.wayrecall.tracker.analytics.domain.*
import zio.*
import zio.json.*
import org.apache.poi.xssf.usermodel.{XSSFWorkbook, XSSFSheet, XSSFCellStyle}
import org.apache.poi.ss.usermodel.{HorizontalAlignment, FillPatternType, IndexedColors}
import java.io.{File, FileOutputStream}
import java.time.format.DateTimeFormatter

// ============================================================
// ExcelExporter — генерация Excel (.xlsx) через Apache POI
// ============================================================

object ExcelExporter:

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  /** Экспорт отчёта по пробегу в Excel */
  def exportMileage(report: MileageReport, filePath: String): Task[File] =
    ZIO.attemptBlocking {
      val workbook = new XSSFWorkbook()
      val headerStyle = createHeaderStyle(workbook)

      // Лист: Итого
      val summarySheet = workbook.createSheet("Итого")
      addSummaryRow(summarySheet, 0, "Транспорт ID", report.vehicleId.toString, headerStyle)
      addSummaryRow(summarySheet, 1, "Период", s"${report.period.from} — ${report.period.to}", headerStyle)
      addSummaryRow(summarySheet, 2, "Общий пробег (км)", f"${report.totalMileageKm}%.2f", headerStyle)
      addSummaryRow(summarySheet, 3, "Моточасы", f"${report.totalEngineHours}%.2f", headerStyle)
      addSummaryRow(summarySheet, 4, "Средняя скорость (км/ч)", f"${report.averageSpeed}%.2f", headerStyle)
      addSummaryRow(summarySheet, 5, "Макс. скорость (км/ч)", f"${report.maxSpeed}%.1f", headerStyle)
      autoSizeColumns(summarySheet, 2)

      // Лист: По дням
      val dailySheet = workbook.createSheet("По дням")
      val dailyHeaders = List("Дата", "Пробег (км)", "Ср. скорость", "Макс. скорость", "Моточасы", "Точек")
      addHeaderRow(dailySheet, dailyHeaders, headerStyle)
      report.dailyData.zipWithIndex.foreach { case (d, i) =>
        val row = dailySheet.createRow(i + 1)
        row.createCell(0).setCellValue(d.date.format(dateFormatter))
        row.createCell(1).setCellValue(d.mileageKm)
        row.createCell(2).setCellValue(d.avgSpeed)
        row.createCell(3).setCellValue(d.maxSpeed)
        row.createCell(4).setCellValue(d.engineHours)
        row.createCell(5).setCellValue(d.pointCount.toDouble)
      }
      autoSizeColumns(dailySheet, dailyHeaders.size)

      // Лист: Поездки (если есть)
      if report.trips.nonEmpty then
        val tripsSheet = workbook.createSheet("Поездки")
        val tripHeaders = List("Начало", "Конец", "Расстояние (км)", "Макс. скорость", "Ср. скорость", "Длительность (мин)")
        addHeaderRow(tripsSheet, tripHeaders, headerStyle)
        report.trips.zipWithIndex.foreach { case (t, i) =>
          val row = tripsSheet.createRow(i + 1)
          row.createCell(0).setCellValue(t.startTime.toString)
          row.createCell(1).setCellValue(t.endTime.toString)
          row.createCell(2).setCellValue(t.distanceKm)
          row.createCell(3).setCellValue(t.maxSpeed)
          row.createCell(4).setCellValue(t.avgSpeed)
          row.createCell(5).setCellValue(t.durationMinutes)
        }
        autoSizeColumns(tripsSheet, tripHeaders.size)

      writeWorkbook(workbook, filePath)
    }

  /** Экспорт отчёта по топливу в Excel */
  def exportFuel(report: FuelReport, filePath: String): Task[File] =
    ZIO.attemptBlocking {
      val workbook = new XSSFWorkbook()
      val headerStyle = createHeaderStyle(workbook)

      // Итого
      val summarySheet = workbook.createSheet("Итого")
      addSummaryRow(summarySheet, 0, "Транспорт ID", report.vehicleId.toString, headerStyle)
      addSummaryRow(summarySheet, 1, "Расход (л)", f"${report.totalConsumedLiters}%.2f", headerStyle)
      addSummaryRow(summarySheet, 2, "Заправлено (л)", f"${report.totalRefueledLiters}%.2f", headerStyle)
      addSummaryRow(summarySheet, 3, "Слито (л)", f"${report.totalDrainedLiters}%.2f", headerStyle)
      addSummaryRow(summarySheet, 4, "Расход на 100 км", f"${report.avgConsumptionPer100km}%.2f", headerStyle)
      autoSizeColumns(summarySheet, 2)

      // По дням
      val dailySheet = workbook.createSheet("По дням")
      val headers = List("Дата", "Расход (л)", "Пробег (км)", "Расход/100км")
      addHeaderRow(dailySheet, headers, headerStyle)
      report.dailyConsumption.zipWithIndex.foreach { case (d, i) =>
        val row = dailySheet.createRow(i + 1)
        row.createCell(0).setCellValue(d.date.format(dateFormatter))
        row.createCell(1).setCellValue(d.consumedLiters)
        row.createCell(2).setCellValue(d.mileageKm)
        row.createCell(3).setCellValue(d.avgConsumptionPer100km)
      }
      autoSizeColumns(dailySheet, headers.size)

      writeWorkbook(workbook, filePath)
    }

  /** Экспорт сводного отчёта в Excel */
  def exportSummary(report: SummaryReport, filePath: String): Task[File] =
    ZIO.attemptBlocking {
      val workbook = new XSSFWorkbook()
      val headerStyle = createHeaderStyle(workbook)

      // Итого
      val sheet = workbook.createSheet("Сводка")
      val headers = List("ТС ID", "Название", "Пробег (км)", "Расход (л)", "Моточасы", "Макс. скорость", "Нарушения", "Поездки", "Простой (мин)")
      addHeaderRow(sheet, headers, headerStyle)
      report.vehicles.zipWithIndex.foreach { case (v, i) =>
        val row = sheet.createRow(i + 1)
        row.createCell(0).setCellValue(v.vehicleId.toDouble)
        row.createCell(1).setCellValue(v.vehicleName)
        row.createCell(2).setCellValue(v.mileageKm)
        row.createCell(3).setCellValue(v.fuelConsumedLiters)
        row.createCell(4).setCellValue(v.engineHours)
        row.createCell(5).setCellValue(v.maxSpeed)
        row.createCell(6).setCellValue(v.speedViolations.toDouble)
        row.createCell(7).setCellValue(v.tripCount.toDouble)
        row.createCell(8).setCellValue(v.idleMinutes)
      }
      autoSizeColumns(sheet, headers.size)

      writeWorkbook(workbook, filePath)
    }

  // ============================================================
  // Вспомогательные методы
  // ============================================================

  private def createHeaderStyle(workbook: XSSFWorkbook): XSSFCellStyle =
    val style = workbook.createCellStyle()
    val font = workbook.createFont()
    font.setBold(true)
    style.setFont(font)
    style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex)
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND)
    style.setAlignment(HorizontalAlignment.CENTER)
    style

  private def addHeaderRow(sheet: XSSFSheet, headers: List[String], style: XSSFCellStyle): Unit =
    val row = sheet.createRow(0)
    headers.zipWithIndex.foreach { case (h, i) =>
      val cell = row.createCell(i)
      cell.setCellValue(h)
      cell.setCellStyle(style)
    }

  private def addSummaryRow(sheet: XSSFSheet, rowNum: Int, label: String, value: String, headerStyle: XSSFCellStyle): Unit =
    val row = sheet.createRow(rowNum)
    val labelCell = row.createCell(0)
    labelCell.setCellValue(label)
    labelCell.setCellStyle(headerStyle)
    row.createCell(1).setCellValue(value)

  private def autoSizeColumns(sheet: XSSFSheet, count: Int): Unit =
    (0 until count).foreach(sheet.autoSizeColumn)

  private def writeWorkbook(workbook: XSSFWorkbook, filePath: String): File =
    val file = new File(filePath)
    file.getParentFile.mkdirs()
    val fos = new FileOutputStream(file)
    try workbook.write(fos)
    finally
      fos.close()
      workbook.close()
    file
