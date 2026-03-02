package com.wayrecall.tracker.analytics.exporting

import com.wayrecall.tracker.analytics.domain.*
import com.lowagie.text.{Document, Element, FontFactory, PageSize, Paragraph, Phrase, Rectangle, Chunk as LChunk}
import com.lowagie.text.pdf.{PdfPTable, PdfWriter, PdfPCell}
import zio.*
import java.io.{File, FileOutputStream}
import java.time.format.DateTimeFormatter

// ============================================================
// PdfExporter — генерация PDF через OpenPDF
// ============================================================

object PdfExporter:

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  /** Экспорт отчёта по пробегу в PDF */
  def exportMileage(report: MileageReport, filePath: String): Task[File] =
    ZIO.attemptBlocking {
      val file = new File(filePath)
      file.getParentFile.mkdirs()
      val document = new Document(PageSize.A4.rotate())
      val writer = PdfWriter.getInstance(document, new FileOutputStream(file))
      document.open()

      // Заголовок
      document.add(new Paragraph(
        s"Отчёт по пробегу — ТС ${report.vehicleId}",
        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)
      ))
      document.add(new Paragraph(
        s"Период: ${report.period.from} — ${report.period.to}",
        FontFactory.getFont(FontFactory.HELVETICA, 10f)
      ))
      document.add(LChunk.NEWLINE)

      // Итого
      val summaryTable = new PdfPTable(2)
      summaryTable.setWidthPercentage(50)
      summaryTable.setHorizontalAlignment(Element.ALIGN_LEFT)
      addRow(summaryTable, "Общий пробег (км)", f"${report.totalMileageKm}%.2f")
      addRow(summaryTable, "Моточасы", f"${report.totalEngineHours}%.2f")
      addRow(summaryTable, "Средняя скорость", f"${report.averageSpeed}%.2f")
      addRow(summaryTable, "Макс. скорость", f"${report.maxSpeed}%.1f")
      document.add(summaryTable)
      document.add(LChunk.NEWLINE)

      // Суточные данные
      if report.dailyData.nonEmpty then
        document.add(new Paragraph("По дням", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f)))
        val table = new PdfPTable(6)
        table.setWidthPercentage(100)
        addHeaderCell(table, "Дата")
        addHeaderCell(table, "Пробег (км)")
        addHeaderCell(table, "Ср. скорость")
        addHeaderCell(table, "Макс. скорость")
        addHeaderCell(table, "Моточасы")
        addHeaderCell(table, "Точек")

        report.dailyData.foreach { d =>
          table.addCell(d.date.format(dateFormatter))
          table.addCell(f"${d.mileageKm}%.2f")
          table.addCell(f"${d.avgSpeed}%.2f")
          table.addCell(f"${d.maxSpeed}%.1f")
          table.addCell(f"${d.engineHours}%.2f")
          table.addCell(d.pointCount.toString)
        }
        document.add(table)

      document.close()
      file
    }

  /** Экспорт сводного отчёта в PDF */
  def exportSummary(report: SummaryReport, filePath: String): Task[File] =
    ZIO.attemptBlocking {
      val file = new File(filePath)
      file.getParentFile.mkdirs()
      val document = new Document(PageSize.A4.rotate())
      PdfWriter.getInstance(document, new FileOutputStream(file))
      document.open()

      document.add(new Paragraph(
        s"Сводный отчёт — Организация ${report.organizationId}",
        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)
      ))
      document.add(new Paragraph(
        s"Период: ${report.period.from} — ${report.period.to}",
        FontFactory.getFont(FontFactory.HELVETICA, 10f)
      ))
      document.add(LChunk.NEWLINE)

      // Итого
      val summaryTable = new PdfPTable(2)
      summaryTable.setWidthPercentage(40)
      summaryTable.setHorizontalAlignment(Element.ALIGN_LEFT)
      addRow(summaryTable, "Всего ТС", report.totalVehicles.toString)
      addRow(summaryTable, "Общий пробег (км)", f"${report.totalMileageKm}%.2f")
      addRow(summaryTable, "Общий расход (л)", f"${report.totalFuelConsumedLiters}%.2f")
      addRow(summaryTable, "Общие моточасы", f"${report.totalEngineHours}%.2f")
      document.add(summaryTable)
      document.add(LChunk.NEWLINE)

      // Таблица ТС
      if report.vehicles.nonEmpty then
        val table = new PdfPTable(7)
        table.setWidthPercentage(100)
        scala.List("ТС", "Пробег (км)", "Расход (л)", "Моточасы", "Макс. скорость", "Нарушения", "Простой (мин)")
          .foreach(addHeaderCell(table, _))

        report.vehicles.foreach { v =>
          table.addCell(v.vehicleName)
          table.addCell(f"${v.mileageKm}%.2f")
          table.addCell(f"${v.fuelConsumedLiters}%.2f")
          table.addCell(f"${v.engineHours}%.2f")
          table.addCell(f"${v.maxSpeed}%.1f")
          table.addCell(v.speedViolations.toString)
          table.addCell(f"${v.idleMinutes}%.0f")
        }
        document.add(table)

      document.close()
      file
    }

  // Вспомогательные методы
  private def addHeaderCell(table: PdfPTable, text: String): Unit =
    val cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f)))
    cell.setBackgroundColor(java.awt.Color.LIGHT_GRAY)
    cell.setHorizontalAlignment(Element.ALIGN_CENTER)
    table.addCell(cell)

  private def addRow(table: PdfPTable, label: String, value: String): Unit =
    val labelCell = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f)))
    labelCell.setBorder(Rectangle.NO_BORDER)
    table.addCell(labelCell)
    val valueCell = new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA, 10f)))
    valueCell.setBorder(Rectangle.NO_BORDER)
    table.addCell(valueCell)
