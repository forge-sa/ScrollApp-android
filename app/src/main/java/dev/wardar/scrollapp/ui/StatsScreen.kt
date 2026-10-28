package dev.wardar.scrollapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wardar.scrollapp.Platform
import dev.wardar.scrollapp.R
import dev.wardar.scrollapp.stats.DayStat
import dev.wardar.scrollapp.stats.StatsRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateStyle
import java.util.Locale
import kotlin.math.ceil

private enum class Range(val label: String, val days: Long?, val phrase: String) {
    TODAY("Today", 1, "scrolled today"),
    WEEK("7 days", 7, "scrolled in the last 7 days"),
    MONTH("30 days", 30, "scrolled in the last 30 days"),
    ALL("All time", null, "scrolled in total"),
}

private data class Totals(val watchMs: Long, val videos: Int, val adsSkipped: Int)

@Composable
fun StatsScreen(stats: StatsRepository, onBack: () -> Unit) {
    var rows by remember { mutableStateOf<List<DayStat>?>(null) }
    var range by rememberSaveable { mutableStateOf(Range.WEEK) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    LaunchedEffect(Unit) { rows = stats.load() }

    Surface(Modifier.fillMaxSize(), color = Ink.Page) {
        Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
            ScreenHeader("Your scrolling", onBack, action = {
                IconAction(R.drawable.ic_delete, "Reset statistics", { confirmReset = true }, tint = Ink.Secondary)
            })
            Box(Modifier.padding(horizontal = 16.dp)) {
                Segmented(Range.entries, range, { it.label }) { range = it }
            }
            val all = rows ?: return@Column
            val from = range.days?.let { today.minusDays(it - 1) }
            val inRange = all.filter { from == null || !it.day.isBefore(from) }
            val totals = Platform.entries.associateWith { platform ->
                val mine = inRange.filter { it.platform == platform }
                Totals(mine.sumOf { it.watchMs }, mine.sumOf { it.videos }, mine.sumOf { it.adsSkipped })
            }

            Hero(totals.values, range)

            SectionLabel("By source")
            Card {
                Platform.entries.forEachIndexed { i, platform ->
                    if (i > 0) HorizontalDivider(color = Ink.Grid, modifier = Modifier.padding(vertical = 10.dp))
                    SourceRow(platform, totals.getValue(platform))
                }
            }

            val chartDays = if (range == Range.MONTH || range == Range.ALL) 30 else 7
            SectionLabel("Watch time, last $chartDays days")
            Card { DailyChart(all, today, chartDays) }
            Gap(24)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset statistics?") },
            text = { Text("This deletes all watch time, video counts and skipped-ad counts.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        stats.clear()
                        rows = stats.load()
                    }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Hero(totals: Collection<Totals>, range: Range) {
    val watchMs = totals.sumOf { it.watchMs }
    val videos = totals.sumOf { it.videos }
    val ads = totals.sumOf { it.adsSkipped }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text(formatDuration(watchMs), color = Ink.Primary, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text(range.phrase, color = Ink.Secondary, fontSize = 15.sp)
        Gap(8)
        Text(
            "${plural(videos, "video")} · ${plural(ads, "ad")} skipped",
            color = Ink.Secondary,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun SourceRow(platform: Platform, totals: Totals) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(platform.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(platform.title, color = Ink.Primary, fontSize = 16.sp)
            val average = if (totals.videos > 0) "about ${formatDuration(totals.watchMs / totals.videos)} each" else "nothing yet"
            Text(average, color = Ink.Muted, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${totals.videos} ${platform.itemNoun}", color = Ink.Primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(formatDuration(totals.watchMs), color = Ink.Secondary, fontSize = 13.sp)
        }
    }
}

/** Stacked minutes per day, one segment per source in fixed order from the baseline. */
@Composable
private fun DailyChart(rows: List<DayStat>, today: LocalDate, days: Int) {
    val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = rows.groupBy { it.day }
    val minutes = dates.map { day ->
        Platform.entries.map { p -> (byDay[day]?.filter { it.platform == p }?.sumOf { it.watchMs } ?: 0L) / 60_000f }
    }
    val maxTotal = minutes.maxOf { it.sum() }
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    var showTable by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for (platform in Platform.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(platform.color, size = 8)
                Spacer(Modifier.width(6.dp))
                Text(platform.shortName, color = Ink.Secondary, fontSize = 13.sp)
            }
        }
    }
    Gap(12)

    if (maxTotal <= 0f) {
        Text("No watch time in these days yet.", color = Ink.Muted, fontSize = 14.sp)
        return
    }

    val (top, step) = niceScale(maxTotal)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Ink.Muted, fontSize = 11.sp)
    val dayFormat = remember { DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) }
    val summary = "Watch time per day for the last $days days, most ${formatMinutes(maxTotal)} in a day"

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = summary }
            .pointerInput(days) {
                detectTapGestures { offset ->
                    val left = AXIS_WIDTH.dp.toPx()
                    val slot = (size.width - left) / days
                    val index = ((offset.x - left) / slot).toInt()
                    selected = if (offset.x < left || index !in 0 until days || selected == index) null else index
                }
            },
    ) {
        val left = AXIS_WIDTH.dp.toPx()
        val plotTop = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val plotHeight = bottom - plotTop
        val slot = (size.width - left) / days
        val barWidth = slot * if (days <= 7) 0.56f else 0.66f
        val gap = 2.dp.toPx()
        val radius = 4.dp.toPx()

        // Recessive grid with value labels on the left.
        var tick = 0f
        while (tick <= top + 0.001f) {
            val y = bottom - tick / top * plotHeight
            if (tick > 0f) drawLine(Ink.Grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(if (tick == 0f) "0" else formatMinutes(tick), labelStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f))
            tick += step
        }

        selected?.let { i ->
            drawRect(Ink.Wash, topLeft = Offset(left + slot * i, plotTop), size = Size(slot, plotHeight))
        }

        minutes.forEachIndexed { i, values ->
            val x = left + slot * i + (slot - barWidth) / 2f
            var y = bottom
            val lastShown = values.indexOfLast { it > 0f }
            values.forEachIndexed { p, value ->
                if (value <= 0f) return@forEachIndexed
                val height = value / top * plotHeight
                val segmentBottom = if (y < bottom) y - gap else y
                val segmentTop = minOf(y - height, segmentBottom - 1f)
                val color = Platform.entries[p].color
                if (p == lastShown) {
                    // Only the data end is rounded; the baseline end stays square.
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = x, top = segmentTop, right = x + barWidth, bottom = segmentBottom,
                                topLeftCornerRadius = CornerRadius(radius),
                                topRightCornerRadius = CornerRadius(radius),
                            ),
                        )
                    }
                    drawPath(path, color)
                } else {
                    drawRect(color, topLeft = Offset(x, segmentTop), size = Size(barWidth, segmentBottom - segmentTop))
                }
                y -= height
            }
        }

        drawLine(Ink.Baseline, Offset(left, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())

        dates.forEachIndexed { i, day ->
            val text = when {
                days <= 7 -> day.dayOfWeek.getDisplayName(DateStyle.SHORT, Locale.getDefault())
                (days - 1 - i) % 7 == 0 -> day.format(dayFormat)
                else -> return@forEachIndexed
            }
            val label = measurer.measure(text, labelStyle)
            val center = left + slot * i + slot / 2f
            val x = (center - label.size.width / 2f).coerceIn(left, size.width - label.size.width)
            drawText(label, topLeft = Offset(x, bottom + 6.dp.toPx()))
        }
    }

    Gap(10)
    val pick = selected
    if (pick == null) {
        Text("Tap a bar to see that day", color = Ink.Muted, fontSize = 13.sp)
    } else {
        val values = minutes[pick]
        Text(
            "${dates[pick].format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))} · ${formatMinutes(values.sum())}",
            color = Ink.Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Gap(4)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Platform.entries.forEachIndexed { p, platform ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(platform.color, size = 8)
                    Spacer(Modifier.width(6.dp))
                    Text("${platform.shortName} ${formatMinutes(values[p])}", color = Ink.Secondary, fontSize = 13.sp)
                }
            }
        }
    }

    TextButton(onClick = { showTable = !showTable }, modifier = Modifier.padding(top = 4.dp)) {
        Text(if (showTable) "Hide table" else "Show as table")
    }
    if (showTable) DailyTable(dates, minutes)
}

@Composable
private fun DailyTable(dates: List<LocalDate>, minutes: List<List<Float>>) {
    val format = remember { DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()) }
    val headers = listOf("Day") + Platform.entries.map { it.shortName } + "Total"
    Column {
        TableRow(headers, header = true)
        for (i in dates.indices.reversed()) {
            HorizontalDivider(color = Ink.Grid)
            val values = minutes[i]
            TableRow(listOf(dates[i].format(format)) + values.map { formatMinutes(it) } + formatMinutes(values.sum()))
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        cells.forEachIndexed { i, cell ->
            Text(
                cell,
                color = if (header) Ink.Muted else if (i == 0) Ink.Secondary else Ink.Primary,
                fontSize = 13.sp,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(if (i == 0) 1.6f else 1f),
            )
        }
    }
}

private const val AXIS_WIDTH = 40

/** Axis top and gridline step, in minutes, giving at most four gridlines. */
private fun niceScale(max: Float): Pair<Float, Float> {
    val steps = listOf(1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f, 180f, 240f, 360f, 480f, 720f)
    val step = steps.firstOrNull { ceil(max / it) <= 4 } ?: ceil(max / 4 / 60f) * 60f
    return ceil(max / step) * step to step
}

private fun formatMinutes(minutes: Float): String = formatDuration((minutes * 60_000).toLong())

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    return when {
        ms < 60_000 -> "${ms / 1000}s"
        totalMinutes < 60 -> "${totalMinutes}m"
        else -> "${totalMinutes / 60}h ${"%02d".format(totalMinutes % 60)}m"
    }
}

private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"
