package de.wartezeiten.app.ui.waitingtimes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

@Composable
fun ParkStatisticsDashboard(
    statistics: ParkWaitStatistics?,
    currentCrowdLevel: Float?,
    language: String,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (language == "en") "Park statistics" else "Parkstatistik",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = currentCrowdLevel?.let { level ->
                    if (language == "en") {
                        "Current crowd level: ${level.percentText()}"
                    } else {
                        "Aktuelle Auslastung: ${level.percentText()}"
                    }
                } ?: if (language == "en") {
                    "Current crowd level: unavailable"
                } else {
                    "Aktuelle Auslastung: nicht verfügbar"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (statistics == null) {
                Text(
                    text = if (language == "en") {
                        "No central wait-time measurements are available for today yet."
                    } else {
                        "Für heute sind noch keine zentralen Wartezeit-Messpunkte verfügbar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatisticsMetric(
                    label = if (language == "en") "Average" else "Durchschnitt",
                    value = statistics.averageWaitMinutes.minutesText(),
                    modifier = Modifier.weight(1f),
                )
                StatisticsMetric(
                    label = if (language == "en") "Min / max" else "Min. / Max.",
                    value = "${statistics.minAverageWaitMinutes.minutesText()} / ${statistics.maxAverageWaitMinutes.minutesText()}",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatisticsMetric(
                    label = if (language == "en") "Latest" else "Zuletzt",
                    value = statistics.latestAverageWaitMinutes.minutesText(),
                    modifier = Modifier.weight(1f),
                )
                StatisticsMetric(
                    label = if (language == "en") "Open attractions" else "Offene Attraktionen",
                    value = statistics.latestOpenAttractionCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatisticsMetric(
                    label = if (language == "en") "Samples" else "Messpunkte",
                    value = statistics.points.size.toString(),
                    modifier = Modifier.weight(1f),
                )
            }

            ParkAverageWaitChart(
                points = statistics.points,
                openFrom = statistics.openFrom,
                closedFrom = statistics.closedFrom,
                language = language,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}

@Composable
private fun StatisticsMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ParkAverageWaitChart(
    points: List<ParkWaitStatisticsPoint>,
    openFrom: String?,
    closedFrom: String?,
    language: String,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Text(
            text = if (language == "en") "Not enough measurements for a chart yet." else "Noch zu wenige Messpunkte für einen Graphen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val sortedPoints = remember(points) { points.sortedBy { it.capturedAtMillis } }
    val minTime = sortedPoints.first().capturedAtMillis
    val maxTime = sortedPoints.last().capturedAtMillis.coerceAtLeast(minTime + 1)
    val midTime = minTime + ((maxTime - minTime) / 2)
    val yMax = remember(sortedPoints) {
        val maximum = ceil(sortedPoints.maxOf { it.averageWaitMinutes }).toInt()
        ((maximum.coerceAtLeast(10) + 9) / 10) * 10
    }
    val chartZoneId = remember(openFrom, closedFrom) {
        openFrom.toOffsetZoneIdOrNull()
            ?: closedFrom.toOffsetZoneIdOrNull()
            ?: ZoneId.systemDefault()
    }
    val timeFormatter = remember(chartZoneId) {
        DateTimeFormatter.ofPattern("HH:mm").withZone(chartZoneId)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text("$yMax", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${yMax / 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val horizontalPadding = 4.dp.toPx()
                val verticalPadding = 8.dp.toPx()
                val width = size.width - horizontalPadding * 2
                val height = size.height - verticalPadding * 2

                repeat(3) { index ->
                    val y = verticalPadding + height * index / 2f
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.18f),
                        start = Offset(horizontalPadding, y),
                        end = Offset(horizontalPadding + width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                fun xFor(timestamp: Long): Float = horizontalPadding +
                    ((timestamp - minTime).toFloat() / (maxTime - minTime).toFloat()) * width
                fun yFor(wait: Float): Float = verticalPadding +
                    (1f - (wait / yMax.toFloat()).coerceIn(0f, 1f)) * height

                val path = Path()
                sortedPoints.forEachIndexed { index, point ->
                    val x = xFor(point.capturedAtMillis)
                    val y = yFor(point.averageWaitMinutes)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = neutralColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
                sortedPoints.takeLast(24).forEach { point ->
                    drawCircle(
                        color = neutralColor,
                        radius = 3.dp.toPx(),
                        center = Offset(xFor(point.capturedAtMillis), yFor(point.averageWaitMinutes)),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 34.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(timeFormatter.format(Instant.ofEpochMilli(minTime)), style = MaterialTheme.typography.labelSmall)
            Text(timeFormatter.format(Instant.ofEpochMilli(midTime)), style = MaterialTheme.typography.labelSmall)
            Text(timeFormatter.format(Instant.ofEpochMilli(maxTime)), style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = if (language == "en") {
                "Average wait time across all open attractions"
            } else {
                "Durchschnittliche Wartezeit aller geöffneten Attraktionen"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Float.minutesText(): String = "${String.format(Locale.GERMAN, "%.1f", this)} Min."

private fun Float.percentText(): String = "${String.format(Locale.GERMAN, "%.0f", coerceIn(0f, 100f))}%"

private fun String?.toOffsetZoneIdOrNull(): ZoneId? {
    return this?.let { value ->
        runCatching { OffsetDateTime.parse(value).offset }.getOrNull()
    }
}
