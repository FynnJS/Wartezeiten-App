package de.wartezeiten.app.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import de.wartezeiten.app.MainActivity
import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.domain.model.ParkDetail
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class ParkHomeWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val storedConfig = runCatching {
            val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            ParkWidgetConfigStore.read(context, appWidgetId)
        }.getOrNull() ?: ParkWidgetConfigStore.readLast(context)
        val parkKey = preferences[ParkWidgetState.PARK_KEY] ?: storedConfig?.parkKey
        val parkName = preferences[ParkWidgetState.PARK_NAME].orEmpty().ifBlank {
            storedConfig?.parkName.orEmpty()
        }
        val attractionIds = ParkWidgetState.decodeAttractionIds(preferences[ParkWidgetState.ATTRACTION_IDS])
            .ifEmpty { storedConfig?.attractionIds.orEmpty() }
        val widgetData = parkKey?.let { loadWidgetData(context, it, attractionIds) }

        provideContent {
            when {
                parkKey == null -> UnconfiguredWidgetContent()
                widgetData == null -> LoadingWidgetContent(parkName = parkName)
                else -> {
                    ParkWidgetContent(
                        parkKey = parkKey,
                        data = widgetData,
                    )
                }
            }
        }
    }

    private suspend fun loadWidgetData(
        context: Context,
        parkKey: String,
        attractionIds: List<String>,
    ): ParkWidgetData? {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ParkWidgetEntryPoint::class.java,
        )
        val repository = entryPoint.repository()
        val language = entryPoint.preferencesDataSource().language.first()
        val cachedBeforeRefresh = withTimeoutOrNull(2_000L) {
            repository.observeParkDetail(parkKey).first()
        }
        val refreshResult = repository.refreshParkDetail(parkKey, language)
        val refreshedDetail = if (refreshResult is ApiResult.Success) {
            withTimeoutOrNull(5_000L) {
                repository.observeParkDetail(parkKey).first { detail ->
                    detail.park != null || detail.openingTimes != null || detail.waitingTimes.isNotEmpty()
                }
            }
        } else {
            null
        }
        val detail = refreshedDetail
            ?: cachedBeforeRefresh?.takeIf {
                it.park != null || it.openingTimes != null || it.waitingTimes.isNotEmpty()
            }
            ?: return null
        return buildParkWidgetData(detail, attractionIds)
    }
}

@Composable
private fun ParkWidgetContent(
    parkKey: String,
    data: ParkWidgetData,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetSurface))
            .clickable(actionStartActivity(openParkIntent(parkKey)))
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.width(156.dp)) {
                Text(
                    text = data.parkName,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(WidgetOnSurface),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = data.status.label(),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(data.status.color()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            Text(
                text = data.dataAgeLabel,
                maxLines = 1,
                modifier = GlanceModifier.width(70.dp),
                style = TextStyle(
                    color = ColorProvider(WidgetMuted),
                    fontSize = 11.sp,
                ),
            )
        }
        Spacer(modifier = GlanceModifier.height(7.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            MetricColumn(label = "Schnitt", value = data.averageWaitingTimeLabel, width = 76)
            Spacer(modifier = GlanceModifier.width(8.dp))
            MetricColumn(label = "H\u00f6chste", value = data.highestWaitingTimeLabel, width = 76)
            Spacer(modifier = GlanceModifier.width(8.dp))
            MetricColumn(label = "Attr.", value = data.attractions.size.toString(), width = 48)
        }
        Spacer(modifier = GlanceModifier.height(7.dp))
        data.attractions.take(3).forEach { attraction ->
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = attraction.name,
                    maxLines = 1,
                    modifier = GlanceModifier.width(170.dp),
                    style = TextStyle(
                        color = ColorProvider(WidgetOnSurface),
                        fontSize = 11.sp,
                    ),
                )
                Text(
                    text = attraction.waitingTimeLabel,
                    maxLines = 1,
                    modifier = GlanceModifier.width(54.dp),
                    style = TextStyle(
                        color = ColorProvider(WidgetAccent),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, width: Int) {
    Column(modifier = GlanceModifier.width(width.dp)) {
        Text(
            text = value,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(WidgetOnSurface),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(WidgetMuted),
                fontSize = 10.sp,
            ),
        )
    }
}

@Composable
private fun UnconfiguredWidgetContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetSurface))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Lieblingspark ausw\u00e4hlen",
            maxLines = 2,
            style = TextStyle(
                color = ColorProvider(WidgetOnSurface),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun LoadingWidgetContent(parkName: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WidgetSurface))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = parkName.ifBlank { "Parkdaten" },
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(WidgetOnSurface),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = "Daten werden geladen",
            maxLines = 2,
            style = TextStyle(
                color = ColorProvider(WidgetMuted),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private fun ParkWidgetOpenStatus.label(): String {
    return when (this) {
        ParkWidgetOpenStatus.Open -> "ge\u00f6ffnet"
        ParkWidgetOpenStatus.Closed -> "geschlossen"
        ParkWidgetOpenStatus.Unknown -> "Status unbekannt"
    }
}

private fun ParkWidgetOpenStatus.color(): Color {
    return when (this) {
        ParkWidgetOpenStatus.Open -> WidgetOpen
        ParkWidgetOpenStatus.Closed -> WidgetClosed
        ParkWidgetOpenStatus.Unknown -> WidgetMuted
    }
}

private fun openParkIntent(parkKey: String): Intent {
    return Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("wartezeiten://parks/${Uri.encode(parkKey)}")
        setClassName("de.wartezeiten.app", MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}

private val WidgetSurface = Color(0xFF191C1E)
private val WidgetOnSurface = Color(0xFFE1E2E4)
private val WidgetMuted = Color(0xFFB8C0C6)
private val WidgetAccent = Color(0xFFB1C8FF)
private val WidgetOpen = Color(0xFF70D977)
private val WidgetClosed = Color(0xFFFFB4AB)
