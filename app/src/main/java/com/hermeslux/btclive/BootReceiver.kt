package com.hermeslux.btclive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val shouldStart = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == ACTION_QUICKBOOT ||
            action == ACTION_HTC_QUICKBOOT
        if (!shouldStart) return

        val prefs = context.getSharedPreferences(BtcPrefs.SETTINGS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(BtcPrefs.TRACKING, true)) return

        try {
            context.startForegroundService(Intent(context, BtcPriceService::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start BTCLive after $action", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
        private const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
