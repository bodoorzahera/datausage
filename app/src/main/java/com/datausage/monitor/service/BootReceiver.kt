package com.datausage.monitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(
                DataMonitorService.PREFS_NAME, Context.MODE_PRIVATE
            )
            val activeProfileId = prefs.getLong(DataMonitorService.KEY_ACTIVE_PROFILE_ID, -1)

            if (activeProfileId != -1L) {
                DataMonitorService.start(context, activeProfileId)
            }

            // Also enqueue the safety-net worker
            UsagePollingWorker.enqueuePeriodicWork(context)
        }
    }
}
