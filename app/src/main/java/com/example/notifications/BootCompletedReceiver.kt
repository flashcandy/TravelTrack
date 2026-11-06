package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.widget.TravelTrackWidgetUpdater
import com.example.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    WidgetRefreshScheduler.scheduleAll(context)
                    TravelTrackWidgetUpdater.updateAllWidgets(context)
                    TripNotificationScheduler.syncAllTripAlerts(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
