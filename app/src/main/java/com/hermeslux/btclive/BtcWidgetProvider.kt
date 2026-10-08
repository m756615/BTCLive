package com.hermeslux.btclive

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class BtcWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val price = context.getSharedPreferences(BtcPrefs.SETTINGS, Context.MODE_PRIVATE)
            .getString(BtcPrefs.LAST_PRICE, "$—") ?: "$—"
        updateAll(context, price)
    }

    companion object {
        fun updateAll(context: Context, price: String) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BtcWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, render(context, price))
        }

        fun render(context: Context, price: String): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_btc)
            views.setTextViewText(R.id.widget_price, price)
            val open = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context,
                1,
                open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
