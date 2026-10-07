package com.nickspeelman.localjournal.export

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ExportFilename {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE
    fun chart(title: String, start: LocalDate, end: LocalDate): String {
        val stem = title.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(48).ifBlank { "Mood-Trend" }
        return "Aside-$stem-${start.format(iso)}-to-${end.format(iso)}.png"
    }
    fun report(kind: ReportExportKind, start: LocalDate, end: LocalDate): String = when (kind) {
        ReportExportKind.WEEKLY -> "Aside-Weekly-Report-${end.format(iso)}.pdf"
        ReportExportKind.MONTHLY -> "Aside-Monthly-Report-${end.format(DateTimeFormatter.ofPattern("yyyy-MM"))}.pdf"
        ReportExportKind.RANGE -> "Aside-Mood-Report-${start.format(iso)}-to-${end.format(iso)}.pdf"
    }
}
