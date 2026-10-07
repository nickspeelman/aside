package com.nickspeelman.localjournal.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.nickspeelman.localjournal.analytics.MonthlyReportAnalytics
import com.nickspeelman.localjournal.data.UserSettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Draws the compact dashboard used by the expanded monthly notification. */
object MonthlyReportRenderer {
    private const val DESIGN_WIDTH = 720
    private const val BASE_HEIGHT = 330
    private const val ROW_HEIGHT = 38
    private const val TIME_HEADER_HEIGHT = 28
    private const val TIME_BUCKET_ROW_HEIGHT = 31

    fun render(
        report: MonthlyReportAnalytics.Report,
        settings: UserSettings,
        targetWidthPx: Int = DESIGN_WIDTH
    ): Bitmap {
        val standardRows = listOf(
            report.highest != null || report.lowest != null,
            report.hashtags.isNotEmpty()
        ).count { it }
        val timeSectionHeight = if (report.timeBuckets.isNotEmpty()) {
            TIME_HEADER_HEIGHT + report.timeBuckets.size * TIME_BUCKET_ROW_HEIGHT
        } else 0
        val height = BASE_HEIGHT + standardRows * ROW_HEIGHT + timeSectionHeight
        val bitmap = Bitmap.createBitmap(DESIGN_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        val strong = Paint(text).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val muted = Paint(text).apply { color = Color.rgb(100, 100, 100) }
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(225, 225, 225)
            strokeWidth = 2f
        }
        val current = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 91, 164)
            strokeWidth = 6f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val previous = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(150, 150, 150)
            strokeWidth = 4f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            pathEffect = DashPathEffect(floatArrayOf(12f, 9f), 0f)
        }

        strong.textSize = 34f
        canvas.drawText("MONTHLY REPORT", 34f, 46f, strong)

        strong.textSize = 32f
        canvas.drawText(headline(report), 34f, 86f, strong)
        muted.textSize = 24f
        muted.textAlign = Paint.Align.RIGHT
        canvas.drawText("${report.checkInCount} CHECK-INS", DESIGN_WIDTH - 34f, 84f, muted)
        muted.textAlign = Paint.Align.LEFT

        drawChart(canvas, report, current, previous, grid, muted)

        var y = 318f
        if (report.highest != null || report.lowest != null) {
            drawPairRow(
                canvas = canvas,
                y = y,
                leftLabel = "HIGHEST",
                leftValue = report.highest?.let { "${shortDate(it.date)}  ${oneDecimal(it.average)}" } ?: "—",
                rightLabel = "LOWEST",
                rightValue = report.lowest?.let { "${shortDate(it.date)}  ${oneDecimal(it.average)}" } ?: "—",
                strong = strong,
                muted = muted
            )
            y += ROW_HEIGHT
        }

        if (report.hashtags.isNotEmpty()) {
            muted.textSize = 19f
            canvas.drawText("HASHTAGS", 34f, y, muted)
            strong.textSize = 23f
            val values = report.hashtags.joinToString("     ") {
                "${it.tag}  ${oneDecimal(it.average)} · ${it.count}"
            }
            canvas.drawText(values, 155f, y, strong)
            y += ROW_HEIGHT
        }

        if (report.timeBuckets.isNotEmpty()) {
            drawTimeOfDaySection(
                canvas = canvas,
                y = y,
                buckets = report.timeBuckets,
                use24Hour = settings.use24Hour,
                strong = strong,
                muted = muted
            )
        }

        val safeWidth = targetWidthPx.coerceAtLeast(320).coerceAtMost(DESIGN_WIDTH)
        if (safeWidth == DESIGN_WIDTH) return bitmap
        val scaledHeight = (height * (safeWidth.toFloat() / DESIGN_WIDTH.toFloat())).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, safeWidth, scaledHeight, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    private fun drawChart(
        canvas: Canvas,
        report: MonthlyReportAnalytics.Report,
        currentPaint: Paint,
        previousPaint: Paint,
        gridPaint: Paint,
        labelPaint: Paint
    ) {
        val left = 64f
        val right = DESIGN_WIDTH - 64f
        val top = 108f
        val bottom = 238f
        val chartHeight = bottom - top
        val chartWidth = right - left

        fun yFor(value: Double): Float =
            bottom - (((value.coerceIn(1.0, 5.0) - 1.0) / 4.0).toFloat() * chartHeight)
        for (mood in 1..5) {
            val y = yFor(mood.toDouble())
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        fun xFor(index: Int): Float = left + chartWidth * index / 29f

        fun drawTrend(values: List<Double?>, paint: Paint) {
            val path = Path()
            var started = false
            values.forEachIndexed { index, value ->
                if (value != null) {
                    val x = xFor(index)
                    val y = yFor(value)
                    if (!started) {
                        path.moveTo(x, y)
                        started = true
                    } else path.lineTo(x, y)
                }
            }
            if (started) canvas.drawPath(path, paint)
        }

        val previousTrend = MonthlyReportAnalytics.trendValues(report.previousDays, windowDays = 7)
        val currentTrend = MonthlyReportAnalytics.trendValues(report.currentDays, windowDays = 7)
        drawTrend(previousTrend, previousPaint)
        drawTrend(currentTrend, currentPaint)

        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = currentPaint.color
            style = Paint.Style.FILL
        }
        report.currentDays.forEachIndexed { index, point ->
            point.average?.let { canvas.drawCircle(xFor(index), yFor(it), 4f, pointPaint) }
        }

        labelPaint.textSize = 17f
        labelPaint.textAlign = Paint.Align.CENTER
        val labelIndices = listOf(0, 9, 19, 29)
        labelIndices.forEach { index ->
            val date = report.currentDays[index].date
            canvas.drawText(shortDate(date), xFor(index), 267f, labelPaint)
        }

        labelPaint.textAlign = Paint.Align.LEFT
        labelPaint.textSize = 18f
        val currentLegend = Paint(currentPaint).apply { strokeWidth = 5f; pathEffect = null }
        canvas.drawLine(125f, 287f, 159f, 287f, currentLegend)
        canvas.drawText("LAST 30 DAYS", 168f, 293f, labelPaint)
        canvas.drawLine(395f, 287f, 429f, 287f, previousPaint)
        canvas.drawText("PREVIOUS 30", 438f, 293f, labelPaint)
    }

    private fun drawTimeOfDaySection(
        canvas: Canvas,
        y: Float,
        buckets: List<MonthlyReportAnalytics.TimeBucket>,
        use24Hour: Boolean,
        strong: Paint,
        muted: Paint
    ) {
        muted.textAlign = Paint.Align.LEFT
        muted.textSize = 19f
        canvas.drawText("TIME OF DAY", 34f, y, muted)
        val valueX = DESIGN_WIDTH - 34f
        buckets.forEachIndexed { index, bucket ->
            val rowY = y + TIME_HEADER_HEIGHT + index * TIME_BUCKET_ROW_HEIGHT
            strong.textAlign = Paint.Align.LEFT
            strong.textSize = 21f
            canvas.drawText(
                "${formatTime(bucket.start, use24Hour)}–${formatTime(bucket.end, use24Hour)}",
                34f,
                rowY,
                strong
            )
            strong.textAlign = Paint.Align.RIGHT
            canvas.drawText("${oneDecimal(bucket.average)} · ${bucket.count}", valueX, rowY, strong)
        }
        strong.textAlign = Paint.Align.LEFT
    }

    private fun drawPairRow(
        canvas: Canvas,
        y: Float,
        leftLabel: String,
        leftValue: String,
        rightLabel: String,
        rightValue: String,
        strong: Paint,
        muted: Paint
    ) {
        muted.textSize = 18f
        strong.textSize = 23f
        canvas.drawText(leftLabel, 34f, y, muted)
        canvas.drawText(leftValue, 125f, y, strong)
        canvas.drawText(rightLabel, 380f, y, muted)
        canvas.drawText(rightValue, 455f, y, strong)
    }

    fun headline(report: MonthlyReportAnalytics.Report): String {
        val current = oneDecimal(report.average)
        val delta = report.displayedDelta ?: return current
        if (delta == 0.0) return "$current · no change"
        val arrow = if (delta > 0.0) "↑" else "↓"
        return "$current  $arrow ${oneDecimal(kotlin.math.abs(delta))}"
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private fun shortDate(date: LocalDate): String = date.format(dateFormatter)
    private fun oneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

    private fun formatTime(time: LocalTime, use24Hour: Boolean): String {
        return if (use24Hour) {
            String.format(Locale.getDefault(), "%02d:%02d", time.hour, time.minute)
        } else {
            val amPm = if (time.hour < 12) "AM" else "PM"
            val hour = when {
                time.hour == 0 -> 12
                time.hour > 12 -> time.hour - 12
                else -> time.hour
            }
            if (time.minute == 0) "$hour $amPm"
            else String.format(Locale.getDefault(), "%d:%02d %s", hour, time.minute, amPm)
        }
    }
}
