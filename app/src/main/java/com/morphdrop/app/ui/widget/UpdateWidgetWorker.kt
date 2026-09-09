package com.morphdrop.app.ui.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateWidgetWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Update all 3 Glance AppWidget types
            val glanceManager = GlanceAppWidgetManager(appContext)

            // 1. Combined Widget
            val combinedIds = glanceManager.getGlanceIds(MorphDropWidget::class.java)
            for (id in combinedIds) {
                MorphDropWidget().update(appContext, id)
            }
            if (combinedIds.isEmpty()) {
                MorphDropWidget().updateAll(appContext)
            }

            // 2. History Widget
            val historyIds = glanceManager.getGlanceIds(MorphDropHistoryWidget::class.java)
            for (id in historyIds) {
                MorphDropHistoryWidget().update(appContext, id)
            }
            if (historyIds.isEmpty()) {
                MorphDropHistoryWidget().updateAll(appContext)
            }

            // 3. Tools Widget
            val toolsIds = glanceManager.getGlanceIds(MorphDropToolsWidget::class.java)
            for (id in toolsIds) {
                MorphDropToolsWidget().update(appContext, id)
            }
            if (toolsIds.isEmpty()) {
                MorphDropToolsWidget().updateAll(appContext)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("UpdateWidgetWorker", "Failed to update widget", e)
            Result.retry()
        }
    }
}
