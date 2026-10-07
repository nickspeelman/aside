package com.nickspeelman.localjournal.export

import java.time.LocalDate
import java.time.LocalDateTime

/** Sanitized export model. It deliberately cannot contain journal-note text. */
data class ShareableChart(
    val title: String,
    val contextLabel: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val checkInCount: Int,
    val averageRating: Double?,
    val values: List<Double?>,
    val trendValues: List<Double?>? = null,
    val comparisonTrendValues: List<Double?>? = null,
    val xLabels: List<String>,
    val valueLabel: String = "Mood rating",
    val trendLabel: String? = null,
    val comparisonLabel: String? = null
)

enum class ReportExportKind { WEEKLY, MONTHLY, RANGE }

data class ExportHashtag(val tag: String, val average: Double, val count: Int)
data class ExportTimeBucket(val label: String, val average: Double, val count: Int)
data class ExportHighlight(val label: String, val average: Double)
data class ExportNote(val dateTime: LocalDateTime, val rating: Int?, val note: String)

data class ShareableReport(
    val kind: ReportExportKind,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val average: Double,
    val previousAverage: Double?,
    val displayedDelta: Double?,
    val checkInCount: Int,
    val dailyValues: List<Double?>,
    val trendValues: List<Double?>,
    val comparisonTrendValues: List<Double?>?,
    val highest: ExportHighlight?,
    val lowest: ExportHighlight?,
    val hashtags: List<ExportHashtag>,
    val timeBuckets: List<ExportTimeBucket>,
    val notes: List<ExportNote>
)
