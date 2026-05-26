package com.hermeslux.btclive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BtcPriceService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var priceTextView: TextView? = null
    private var selectedColor: Int = Color.WHITE
    private var isDynamic: Boolean = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("btc_settings", Context.MODE_PRIVATE)
        // Load from intent if provided (UI change), otherwise load from persistent storage (boot/crash recovery)
        selectedColor = intent?.getIntExtra("color", prefs.getInt("selected_color", Color.WHITE)) ?: prefs.getInt("selected_color", Color.WHITE)
        isDynamic = intent?.getBooleanExtra("dynamic", prefs.getBoolean("is_dynamic", true)) ?: prefs.getBoolean("is_dynamic", true)
        
        // Save to persistent storage if intent provided new values
        if (intent != null && intent.hasExtra("color")) {
            prefs.edit().putInt("selected_color", selectedColor).apply()
        }
        if (intent != null && intent.hasExtra("dynamic")) {
            prefs.edit().putBoolean("is_dynamic", isDynamic).apply()
        }

        val notification = createPriceNotification("Starting service...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (Settings.canDrawOverlays(this)) {
            showOrUpdateOverlay()
        }

        startPriceUpdates()
        return START_STICKY
    }

    private fun showOrUpdateOverlay() {
        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }
        val density = resources.displayMetrics.density
        if (overlayView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = (70 * density).toInt() 
            params.y = (13 * density).toInt() 

            priceTextView = TextView(this).apply {
                text = "   $..."
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setShadowLayer(3f, 1f, 1f, Color.BLACK)
            }
            overlayView = priceTextView
            try { windowManager?.addView(overlayView, params) } catch (e: Exception) {}
        }
        updateTextColor()
    }

    private fun updateTextColor() {
        val colorToSet = if (isDynamic) {
            val uiMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK
        } else selectedColor
        priceTextView?.setTextColor(colorToSet)
        if (colorToSet == Color.BLACK) priceTextView?.setShadowLayer(2f, 1f, 1f, Color.WHITE)
        else priceTextView?.setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    private fun startPriceUpdates() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val price = fetchBtcPrice()
                    val priceValue = price.replace("$", "").replace(",", "").toDoubleOrNull() ?: 0.0
                    
                    serviceScope.launch(Dispatchers.Main) {
                        priceTextView?.text = "   $price"
                        if (isDynamic) updateTextColor() 
                        updateNotification(price)
                        checkAlerts(priceValue, price)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Update failed: ${e.message}")
                }
                delay(5000) 
            }
        }
    }

    private fun checkAlerts(currentPrice: Double, priceString: String) {
        val prefs = getSharedPreferences("btc_alerts", Context.MODE_PRIVATE)
        val alertsJson = prefs.getString("active_alerts", "[]") ?: "[]"
        val alertsArray = JSONArray(alertsJson)
        val remainingAlerts = JSONArray()
        
        var alertTriggered = false
        var triggeredTarget = 0.0

        for (i in 0 until alertsArray.length()) {
            val alert = alertsArray.getJSONObject(i)
            val target = alert.getDouble("target")
            val type = alert.getString("type") 
            
            val hit = if (type == "ABOVE") currentPrice >= target else currentPrice <= target
            
            if (hit) {
                alertTriggered = true
                triggeredTarget = target
                saveToHistory(target, priceString)
            } else {
                remainingAlerts.put(alert)
            }
        }

        if (alertTriggered) {
            prefs.edit().putString("active_alerts", remainingAlerts.toString()).apply()
            showPopupNotification(triggeredTarget, priceString)
        }
    }

    private fun saveToHistory(target: Double, hitPrice: String) {
        val prefs = getSharedPreferences("btc_alerts", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("alert_history", "[]") ?: "[]"
        val historyArray = JSONArray(historyJson)
        
        val entry = JSONObject()
        entry.put("target", target)
        entry.put("hitPrice", hitPrice)
        entry.put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        
        historyArray.put(entry)
        prefs.edit().putString("alert_history", historyArray.toString()).apply()
    }

    private fun showPopupNotification(target: Double, currentPrice: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "BTC_ALERT_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "BTC Alerts", NotificationManager.IMPORTANCE_HIGH)
            channel.enableVibration(true)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BTC ALERT HIT!")
            .setContentText("Target: $$target | Current: $currentPrice")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ALERT_ID, notification)
    }

    private fun fetchBtcPrice(): String {
        return try { fetchFromCryptoCompare() } catch (e: Exception) { fetchFromCoinGecko() }
    }

    private fun fetchFromCryptoCompare(): String {
        val url = "https://min-api.cryptocompare.com/data/price?fsym=BTC&tsyms=USD"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error")
            val price = JSONObject(response.body?.string()).getDouble("USD")
            return "$${String.format(Locale.US, "%,.0f", price)}"
        }
    }

    private fun fetchFromCoinGecko(): String {
        val url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd"
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Error")
            val price = JSONObject(response.body?.string()).getJSONObject("bitcoin").getDouble("usd")
            return "$${String.format(Locale.US, "%,.0f", price)}"
        }
    }
    
    private fun createPriceNotification(price: String): Notification {
        val channelId = "BTC_LIVE_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "BTC Live Tracker", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("BTC Live Price")
            .setContentText(price)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(price: String) {
        val notification = createPriceNotification(price)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        overlayView?.let { windowManager?.removeView(it) }
    }

    companion object {
        private const val TAG = "BtcPriceService"
        const val NOTIFICATION_ID = 101
        const val NOTIFICATION_ALERT_ID = 102
    }
}
