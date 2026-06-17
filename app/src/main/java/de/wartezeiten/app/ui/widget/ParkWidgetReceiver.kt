package de.wartezeiten.app.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class ParkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ParkHomeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ParkWidgetUpdateScheduler.ensureBackgroundUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        ParkWidgetUpdateScheduler.cancelBackgroundUpdates(context)
    }
}
