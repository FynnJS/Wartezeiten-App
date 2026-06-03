package de.wartezeiten.app.ui.waitingtimes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.wartezeiten.app.domain.model.ParkTrendSummary
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ParkTrendDashboard(
    summary: ParkTrendSummary,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    if (
        summary == ParkTrendSummary.Empty ||
        summary.latestSnapshotAtMillis == null ||
        summary.displayCrowdLevel == null
    ) {
        return
    }

    val ageMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - summary.latestSnapshotAtMillis)
    val trend = when {
        summary.medianCrowdLevel == null -> 0
        summary.displayCrowdLevel > summary.medianCrowdLevel + 0.5f -> 1
        summary.displayCrowdLevel < summary.medianCrowdLevel - 0.5f -> -1
        else -> 0
    }

    val crowdStatus = CrowdLevelLabeler.getStatus(summary.displayCrowdLevel)

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Statistik Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Min: ${summary.minCrowdLevel ?: "-"}")
                    Text("Median: ${summary.medianCrowdLevel ?: "-"}")
                    Text("Max: ${summary.maxCrowdLevel ?: "-"}")
                    Text("Volatilität: ${summary.volatility ?: "-"}")
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Schließen") } }
        )
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Auslastung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showDialog = true }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(crowdStatus.label, style = MaterialTheme.typography.headlineSmall, color = crowdStatus.color, fontWeight = FontWeight.Bold)
                    Text("ca. ${summary.displayCrowdLevel?.let { String.format(Locale.GERMAN, "%.0f", it.coerceIn(0f, 100f)) } ?: "-"}% Auslastung", style = MaterialTheme.typography.bodyMedium)
                }
                
                Icon(
                    imageVector = when (trend) {
                        1 -> Icons.AutoMirrored.Filled.TrendingUp
                        -1 -> Icons.AutoMirrored.Filled.TrendingDown
                        else -> Icons.AutoMirrored.Filled.TrendingFlat
                    },
                    contentDescription = "Trend Details",
                    modifier = Modifier.size(32.dp),
                    tint = when (trend) {
                        1 -> Color(0xFFD32F2F)
                        -1 -> Color(0xFF388E3C)
                        else -> Color.Gray
                    }
                )
            }

            Text(
                text = "Daten von vor $ageMinutes Min.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrendMetric(
                    label = "Heute min.",
                    value = summary.minCrowdLevel.percentText(),
                    modifier = Modifier.weight(1f),
                )
                TrendMetric(
                    label = "Median",
                    value = summary.medianCrowdLevel.percentText(),
                    modifier = Modifier.weight(1f),
                )
                TrendMetric(
                    label = "Peak",
                    value = summary.maxCrowdLevel.percentText(),
                    modifier = Modifier.weight(1f),
                )
            }

            summary.displayCrowdLevel?.let { current ->
                LinearProgressIndicator(
                    progress = { (current / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = crowdStatus.color,
                    trackColor = MaterialTheme.colorScheme.surface,
                )
            }

            TrendLineChart(
                points = summary.points,
                color = crowdStatus.color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
            )

            Text(
                text = if (summary.hasPublicHistory) {
                    "${summary.sampleCount} Messpunkte aus lokalem und oeffentlichem Verlauf"
                } else {
                    "${summary.sampleCount} Messpunkte im lokalen Verlauf"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrendLineChart(
    points: List<de.wartezeiten.app.domain.model.ParkTrendPoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return

    Canvas(modifier = modifier) {
        val horizontalPadding = 8.dp.toPx()
        val verticalPadding = 12.dp.toPx()
        val width = size.width - (horizontalPadding * 2)
        val height = size.height - (verticalPadding * 2)
        val minTime = points.minOf { it.capturedAtMillis }
        val maxTime = points.maxOf { it.capturedAtMillis }.coerceAtLeast(minTime + 1)

        repeat(4) { index ->
            val y = verticalPadding + (height * index / 3f)
            drawLine(
                color = Color.Gray.copy(alpha = 0.18f),
                start = androidx.compose.ui.geometry.Offset(horizontalPadding, y),
                end = androidx.compose.ui.geometry.Offset(horizontalPadding + width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val path = Path()
        points.sortedBy { it.capturedAtMillis }.forEachIndexed { index, point ->
            val x = horizontalPadding + ((point.capturedAtMillis - minTime).toFloat() / (maxTime - minTime).toFloat()) * width
            val y = verticalPadding + (1f - (point.crowdLevel / 100f).coerceIn(0f, 1f)) * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )

        points.takeLast(12).forEach { point ->
            val x = horizontalPadding + ((point.capturedAtMillis - minTime).toFloat() / (maxTime - minTime).toFloat()) * width
            val y = verticalPadding + (1f - (point.crowdLevel / 100f).coerceIn(0f, 1f)) * height
            drawCircle(
                color = if (point.source == de.wartezeiten.app.domain.model.ParkTrendSource.PublicHistory) {
                    Color(0xFF1565C0)
                } else {
                    color
                },
                radius = 3.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(x, y),
            )
        }
    }
}

@Composable
private fun TrendMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Float?.percentText(): String {
    return this?.let { "${String.format(Locale.GERMAN, "%.0f", it.coerceIn(0f, 100f))}%" } ?: "-"
}
