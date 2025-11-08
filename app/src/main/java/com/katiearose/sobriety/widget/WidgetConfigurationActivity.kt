package com.katiearose.sobriety.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import com.katiearose.sobriety.R
import com.katiearose.sobriety.activities.Main
import androidx.core.content.edit
import com.katiearose.sobriety.AddictionAppWidgetProvider

class WidgetConfigurationActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_configuration)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val addictionListView = findViewById<ListView>(R.id.widget_config_addiction_list)
        addictionListView.choiceMode = ListView.CHOICE_MODE_SINGLE
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, Main.addictions.map { it.name })
        addictionListView.adapter = adapter

        findViewById<android.view.View>(R.id.widget_config_save_button).setOnClickListener {
            val selectedPosition = addictionListView.checkedItemPosition
            if (selectedPosition != ListView.INVALID_POSITION) {
                val selectedAddiction = Main.addictions[selectedPosition]
                // Save the addiction creation timestamp to shared preferences
                getSharedPreferences("widget_prefs", MODE_PRIVATE).edit {
                    putLong(
                        "addiction_creation_timestamp_$appWidgetId",
                        selectedAddiction.creationTimestamp
                    )
                }

                val intent = Intent(this, AddictionAppWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    val ids = intArrayOf(appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                sendBroadcast(intent)

                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(RESULT_OK, resultValue)
                finish()
            }
        }
    }
}