package de.wartezeiten.app.ui.waitingtimes

import androidx.compose.foundation.clickable
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
    if (summary == ParkTrendSummary.Empty || summary.latestSnapshotAtMillis == null) return

    val ageMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - summary.latestSnapshotAtMillis)
    val trend = when {
        summary.displayCrowdLevel == null || summary.medianCrowdLevel == null -> 0
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
        }
    }
}
