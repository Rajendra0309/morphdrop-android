package com.morphdrop.app.ui.widget

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object WidgetUpdateHelper {

    private const val UNIQUE_WORK_NAME = "morphdrop_widget_update_work"

    fun updateAllWidgets(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<UpdateWidgetWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
