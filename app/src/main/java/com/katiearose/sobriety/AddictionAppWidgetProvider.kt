package com.katiearose.sobriety

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.katiearose.sobriety.activities.Main
import com.katiearose.sobriety.utils.convertRangeToString
import com.katiearose.sobriety.utils.convertMilestoneToString
import java.time.Instant
import androidx.core.content.edit
import com.katiearose.sobriety.shared.Addiction
import com.katiearose.sobriety.shared.CacheHandler
import java.io.FileNotFoundException
import java.util.ArrayList

class AddictionAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val cacheHandler = CacheHandler(context)
        if (addictions.isEmpty())
            try {
                context.openFileInput("Sobriety.cache").use {
                    addictions.addAll(cacheHandler.readCache(it))
                }
            } catch (_: FileNotFoundException) {
            }

        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit {
            for (appWidgetId in appWidgetIds) {
                remove("addiction_creation_timestamp_$appWidgetId")
            }
        }
    }

    companion object {

        val addictions = ArrayList<Addiction>()
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0,
                Intent(context, Main::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val creationTimestamp = prefs.getLong("addiction_creation_timestamp_$appWidgetId", -1)
            val addiction = addictions.find { it.creationTimestamp == creationTimestamp }

            val views = RemoteViews(
                context.packageName,
                R.layout.app_widget
            )

            if (addiction != null) {

                views.apply {
                    setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                    setTextViewText(R.id.widgetAddictionName, addiction.name)
                    setTextViewText(R.id.widgetAddictionTime, context.convertRangeToString( addiction.history.keys.last(),
                        Instant.now().toEpochMilli(), false))
                    val nextMilestone = addiction.getNextMilestone()
                    if(nextMilestone != null){
                        setProgressBar(R.id.widgetMilestoneProgress, 100,
                            addiction.calculateMilestoneProgressionPercentage(nextMilestone).second, false)
                        setTextViewText(R.id.widgetMilestoneText,
                            StringBuilder().append(
                                context.getString(R.string.next_milestone)).append(": ")
                                .append(context.convertMilestoneToString(nextMilestone)))
                    }
                    else {
                        setProgressBar(R.id.widgetMilestoneProgress, 100,0, false)
                        setTextViewText(R.id.widgetMilestoneText, context.getString(R.string.no_next_milestone))
                    }
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

}