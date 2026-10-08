package com.hermeslux.btclive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class BtcPriceService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var updateJob: Job? = null

    private val client: OkHttpClient by lazy {
        val version = appVersionName()
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Accept", "application/json")
                        .header("User-Agent", "BTCLive/$version (Android)")
                        .build()
                )
            }
            .build()
    }
    private val priceClient by lazy { BtcPriceClient(::httpGet) }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var priceTextView: TextView? = null
    private var selectedColor: Int = Color.WHITE
    private var isDynamic: Boolean = true
    private var placementMode: PlacementMode = PlacementMode.AUTO
    private var customX: Float? = null
    private var customY: Float? = null
    private var trackingEnabled: Boolean = true
    private var displayedPrice: String = ""
    private var dragMode: Boolean = false
    private var failures: Int = 0
    private var screenInteractive: Boolean = true
    private var lastScreen: ScreenProfile? = null
    private var lastLayout: OverlayLayout? = null
    private var originShiftX: Int = 0
    private var originShiftY: Int = 0
    private var shiftMeasured: Boolean = false
    private var statusBarBlocked: Boolean = false
    private var priceChannelReady: Boolean = false
    private var alertChannelReady: Boolean = false
    private var screenReceiver: android.content.BroadcastReceiver? = null
    private var configRegistered: Boolean = false

    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            statusBarBlocked = false
            originShiftX = 0
            originShiftY = 0
            shiftMeasured = false
            relayoutOverlay()
        }

        override fun onLowMemory() = Unit
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loadSettings(intent)
        if (intent?.getBooleanExtra(EXTRA_ADJUST, false) == true) dragMode = true

        if (trackingEnabled && Settings.canDrawOverlays(this)) {
            try {
                relayoutOverlay()
            } catch (e: Exception) {
                Log.e(TAG, "Overlay setup failed", e)
            }
        }

        try {
            val notification = createPriceNotification(displayedPrice.ifBlank { "Starting…" })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!trackingEnabled) {
            removeOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureScreenReceiver()
        ensureConfigCallbacks()
        if (Settings.canDrawOverlays(this)) relayoutOverlay() else removeOverlay()
        ensureLoop()
        return START_STICKY
    }

    private fun loadSettings(intent: Intent?) {
        val prefs = getSharedPreferences(BtcPrefs.SETTINGS, MODE_PRIVATE)
        val fallbackColor = prefs.getInt(BtcPrefs.COLOR, Color.WHITE)
        val fallbackDynamic = prefs.getBoolean(BtcPrefs.DYNAMIC, true)
        selectedColor = intent?.getIntExtra("color", fallbackColor) ?: fallbackColor
        isDynamic = if (intent != null && intent.hasExtra("dynamic")) {
            intent.getBooleanExtra("dynamic", fallbackDynamic)
        } else {
            fallbackDynamic
        }
        val editor = prefs.edit()
        if (intent != null && intent.hasExtra("color")) editor.putInt(BtcPrefs.COLOR, selectedColor)
        if (intent != null && intent.hasExtra("dynamic")) editor.putBoolean(BtcPrefs.DYNAMIC, isDynamic)
        editor.apply()

        placementMode = OverlayPlacement.placementFrom(
            prefs.getString(BtcPrefs.PLACEMENT, PlacementMode.AUTO.name)
        )
        customX = if (prefs.contains(BtcPrefs.CUSTOM_X)) prefs.getFloat(BtcPrefs.CUSTOM_X, 0f) else null
        customY = if (prefs.contains(BtcPrefs.CUSTOM_Y)) prefs.getFloat(BtcPrefs.CUSTOM_Y, 0f) else null
        trackingEnabled = prefs.getBoolean(BtcPrefs.TRACKING, true)
        if (displayedPrice.isBlank()) {
            displayedPrice = prefs.getString(BtcPrefs.LAST_PRICE, "$…") ?: "$…"
        }
    }

    private fun ensureScreenReceiver() {
        if (screenReceiver != null) return
        screenInteractive = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                screenInteractive = intent?.action != Intent.ACTION_SCREEN_OFF
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        screenReceiver = receiver
    }

    private fun ensureConfigCallbacks() {
        if (configRegistered) return
        registerComponentCallbacks(configCallbacks)
        configRegistered = true
    }

    private fun ensureLoop() {
        if (updateJob?.isActive == true) return
        updateJob = serviceScope.launch {
            while (isActive) {
                val result = runCatching { priceClient.fetchUsd() }
                if (result.isSuccess) {
                    failures = 0
                    val (usd, source) = result.getOrThrow()
                    val display = formatUsdPrice(usd)
                    val decision = try {
                        checkAlerts(usd, display)
                    } catch (e: Exception) {
                        Log.e(TAG, "Alert check failed", e)
                        AlertDecision(emptyList(), emptyList())
                    }
                    withContext(Dispatchers.Main) {
                        displayedPrice = display
                        if (dragMode) {
                            priceTextView?.text = display
                        } else if (Settings.canDrawOverlays(this@BtcPriceService)) {
                            relayoutOverlay()
                        } else {
                            removeOverlay()
                        }
                        publish(display, "Live · $source")
                        if (decision.hits.isNotEmpty()) showPopup(decision.hits, display)
                    }
                } else {
                    failures += 1
                    Log.w(TAG, "Price update failed", result.exceptionOrNull())
                    withContext(Dispatchers.Main) {
                        publish(displayedPrice.ifBlank { "$…" }, "Reconnecting…")
                    }
                }
                delay(nextDelayMs())
            }
        }
    }

    private fun nextDelayMs(): Long {
        val base = if (screenInteractive) 5_000L else 60_000L
        if (failures <= 0) return base
        val factor = 1L shl failures.coerceAtMost(4)
        val cap = if (screenInteractive) 60_000L else 5 * 60_000L
        return (base * factor).coerceAtMost(cap)
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (response.code == 429 || response.code == 418) {
                throw java.io.IOException("Rate limited ${response.code}")
            }
            if (!response.isSuccessful || body.isBlank()) {
                throw java.io.IOException("HTTP ${response.code}")
            }
            return body
        }
    }

    private fun relayoutOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            removeOverlay()
            return
        }
        if (windowManager == null) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        }
        val screen = readScreen()
        lastScreen = screen
        val mode = if (
            statusBarBlocked &&
            placementMode != PlacementMode.CUSTOM &&
            placementMode != PlacementMode.BELOW
        ) {
            PlacementMode.BELOW
        } else {
            placementMode
        }
        val price = displayedPrice.ifBlank { "$00,000" }
        val layout = OverlayPlacement.resolve(
            screen = screen,
            mode = mode,
            customXFraction = customX,
            customYFraction = customY,
            priceText = price,
        )
        lastLayout = layout
        val params = buildParams(layout)
        if (priceTextView == null) {
            priceTextView = TextView(this).apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                includeFontPadding = false
                gravity = Gravity.CENTER
                setOnTouchListener { view, event -> handleDrag(view, event) }
            }
            overlayView = priceTextView
            priceTextView?.text = price
            applyChrome(layout, dragMode)
            try {
                windowManager?.addView(overlayView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to add overlay", e)
                overlayView = null
                priceTextView = null
                return
            }
        } else {
            priceTextView?.text = price
            applyChrome(layout, dragMode)
            try {
                windowManager?.updateViewLayout(overlayView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to move overlay", e)
            }
        }
        requestShiftMeasure()
    }

    private fun buildParams(layout: OverlayLayout): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayFlags(),
            PixelFormat.TRANSLUCENT,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            params.setFitInsetsTypes(0)
            params.setFitInsetsSides(0)
            params.setFitInsetsIgnoringVisibility(true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        params.gravity = Gravity.TOP or Gravity.LEFT
        params.x = layout.xPx - originShiftX
        params.y = layout.yPx - originShiftY
        return params
    }

    private fun overlayFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!dragMode) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return flags
    }

    private fun applyChrome(layout: OverlayLayout, drag: Boolean) {
        val tv = priceTextView ?: return
        val color = resolveColor()
        tv.setTextColor(color)
        tv.textSize = layout.textSizeSp
        val chip = layout.chip || drag
        val density = resources.displayMetrics.density
        if (chip) {
            tv.setBackgroundColor(if (isLight(color)) 0xE6121212.toInt() else 0xF2FFFFFF.toInt())
            val h = (10f * density).roundToInt()
            val v = (4f * density).roundToInt()
            tv.setPadding(h, v, h, v)
            tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else {
            tv.setBackgroundColor(Color.TRANSPARENT)
            tv.setPadding(0, 0, 0, 0)
            if (isLight(color)) tv.setShadowLayer(3f, 1f, 1f, Color.BLACK)
            else tv.setShadowLayer(2f, 1f, 1f, Color.WHITE)
        }
        tv.minWidth = if (drag) (64f * density).roundToInt() else 0
        tv.minHeight = if (drag) (32f * density).roundToInt() else 0
    }

    private fun resolveColor(): Int {
        if (!isDynamic) return selectedColor
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (night == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK
    }

    private fun isLight(color: Int): Boolean {
        val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return luminance >= 160.0
    }

    private fun requestShiftMeasure() {
        if (shiftMeasured || dragMode) return
        val view = overlayView ?: return
        view.post {
            if (shiftMeasured || overlayView !== view) return@post
            shiftMeasured = true
            measureShift(view)
        }
    }

    private fun measureShift(view: View) {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val screen = lastScreen ?: return
        val layout = lastLayout ?: return
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        originShiftX = loc[0] - params.x
        originShiftY = loc[1] - params.y
        val centerY = loc[1] + if (view.height > 0) view.height / 2 else 0
        val pushedOut = !layout.chip && screen.statusBarHeightPx > 0 && centerY >= screen.statusBarHeightPx
        if (pushedOut && placementMode != PlacementMode.CUSTOM) {
            statusBarBlocked = true
            relayoutOverlay()
        } else if (originShiftX != 0 || originShiftY != 0) {
            relayoutOverlay()
        }
    }

    private fun handleDrag(view: View, event: MotionEvent): Boolean {
        if (!dragMode) return false
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.tag = floatArrayOf(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val origin = view.tag as? FloatArray ?: return true
                val dx = (event.rawX - origin[0]).roundToInt()
                val dy = (event.rawY - origin[1]).roundToInt()
                origin[0] = event.rawX
                origin[1] = event.rawY
                val screen = lastScreen
                val maxX = ((screen?.widthPx ?: 0) - view.width).coerceAtLeast(0)
                val maxY = ((screen?.heightPx ?: 0) - view.height).coerceAtLeast(0)
                val screenX = (params.x + originShiftX + dx).coerceIn(0, maxX)
                val screenY = (params.y + originShiftY + dy).coerceIn(0, maxY)
                params.x = screenX - originShiftX
                params.y = screenY - originShiftY
                try {
                    windowManager?.updateViewLayout(view, params)
                } catch (e: Exception) {
                    Log.e(TAG, "Drag update failed", e)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lockDraggedPosition(params)
                return true
            }
        }
        return true
    }

    private fun lockDraggedPosition(params: WindowManager.LayoutParams) {
        val screen = lastScreen ?: readScreen()
        if (screen.widthPx <= 0 || screen.heightPx <= 0) {
            dragMode = false
            relayoutOverlay()
            return
        }
        val screenX = params.x + originShiftX
        val screenY = params.y + originShiftY
        customX = (screenX.toFloat() / screen.widthPx).coerceIn(0f, 1f)
        customY = (screenY.toFloat() / screen.heightPx).coerceIn(0f, 1f)
        placementMode = PlacementMode.CUSTOM
        dragMode = false
        getSharedPreferences(BtcPrefs.SETTINGS, MODE_PRIVATE).edit()
            .putString(BtcPrefs.PLACEMENT, PlacementMode.CUSTOM.name)
            .putFloat(BtcPrefs.CUSTOM_X, customX ?: 0f)
            .putFloat(BtcPrefs.CUSTOM_Y, customY ?: 0f)
            .apply()
        relayoutOverlay()
        Toast.makeText(this, "Price position saved", Toast.LENGTH_SHORT).show()
    }

    private fun readScreen(): ScreenProfile {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) readScreenModern() else readScreenLegacy()
        } catch (e: Exception) {
            Log.w(TAG, "Falling back to legacy screen metrics", e)
            readScreenLegacy()
        }
    }

    private fun readScreenModern(): ScreenProfile {
        val wm = windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = wm.currentWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets
        val status = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            .takeIf { it > 0 } ?: fallbackStatusBar()
        val cutout = insets.displayCutout
        val rect = cutout?.boundingRects
            ?.filter { it.bottom > 0 && it.top < status + 8 }
            ?.fold(null as Rect?) { acc, item ->
                if (acc == null) Rect(item) else acc.apply { union(item) }
            }
        return profile(bounds.width(), bounds.height(), status, rect)
    }

    @Suppress("DEPRECATION")
    private fun readScreenLegacy(): ScreenProfile {
        val wm = windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager
        val point = Point()
        wm.defaultDisplay.getRealSize(point)
        val status = fallbackStatusBar()
        var rect: Rect? = null
        if (Build.VERSION.SDK_INT >= 29) {
            val cut = wm.defaultDisplay.cutout
            rect = cut?.boundingRects
                ?.filter { it.top < status }
                ?.fold(null as Rect?) { acc, item ->
                    if (acc == null) Rect(item) else acc.apply { union(item) }
                }
        }
        return profile(point.x, point.y, status, rect)
    }

    private fun profile(width: Int, height: Int, status: Int, cutout: Rect?): ScreenProfile {
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val clockOnRight = rtl || prefersRightStatusClock(Build.MANUFACTURER ?: "", Build.BRAND ?: "")
        return ScreenProfile(
            widthPx = width,
            heightPx = height,
            density = resources.displayMetrics.density,
            statusBarHeightPx = status,
            cutoutLeftPx = cutout?.left ?: 0,
            cutoutRightPx = cutout?.right ?: 0,
            cutoutTopPx = cutout?.top ?: 0,
            cutoutBottomPx = cutout?.bottom ?: 0,
            rtl = rtl,
            landscape = landscape,
            clockOnRight = clockOnRight,
        )
    }

    private fun fallbackStatusBar(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        val px = if (id > 0) resources.getDimensionPixelSize(id) else (24f * resources.displayMetrics.density).roundToInt()
        return px.coerceAtLeast((20f * resources.displayMetrics.density).roundToInt())
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        priceTextView = null
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun checkAlerts(currentPrice: Double, priceString: String): AlertDecision {
        val prefs = getSharedPreferences(BtcPrefs.ALERTS, MODE_PRIVATE)
        val alerts = loadAlerts(prefs.getString(BtcPrefs.ACTIVE, "[]") ?: "[]")
        val decision = evaluateAlerts(alerts, currentPrice)
        if (decision.hits.isEmpty()) return decision
        saveAlerts(decision.remaining)
        decision.hits.forEach { saveToHistory(it.target, priceString) }
        return decision
    }

    private fun loadAlerts(raw: String): List<PriceAlert> {
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val type = when (item.optString("type")) {
                        "BELOW" -> AlertType.BELOW
                        "ABOVE" -> AlertType.ABOVE
                        else -> continue
                    }
                    val target = item.optDouble("target", Double.NaN)
                    if (target.isFinite() && target > 0.0) add(PriceAlert(target, type))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring unreadable alerts", e)
            emptyList()
        }
    }

    private fun saveAlerts(alerts: List<PriceAlert>) {
        val array = JSONArray()
        alerts.forEach { alert ->
            array.put(JSONObject().put("target", alert.target).put("type", alert.type.name))
        }
        getSharedPreferences(BtcPrefs.ALERTS, MODE_PRIVATE).edit()
            .putString(BtcPrefs.ACTIVE, array.toString())
            .commit()
    }

    private fun saveToHistory(target: Double, hitPrice: String) {
        val prefs = getSharedPreferences(BtcPrefs.ALERTS, MODE_PRIVATE)
        val history = try {
            JSONArray(prefs.getString(BtcPrefs.HISTORY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        history.put(
            JSONObject()
                .put("target", target)
                .put("hitPrice", hitPrice)
                .put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        )
        prefs.edit().putString(BtcPrefs.HISTORY, history.toString()).commit()
    }

    private fun showPopup(hits: List<PriceAlert>, currentPrice: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        ensureAlertChannel(notificationManager)
        val text = if (hits.size == 1) {
            val hit = hits.first()
            val direction = if (hit.type == AlertType.ABOVE) "above" else "below"
            "Target $direction ${formatUsdPrice(hit.target)} | Now $currentPrice"
        } else {
            "Hit ${hits.size} targets | Now $currentPrice"
        }
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setContentTitle("BTC alert hit")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_btc)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ALERT_ID, notification)
    }

    private fun publish(price: String, status: String) {
        val presentation = when {
            overlayView == null || !Settings.canDrawOverlays(this) -> "notification"
            lastLayout?.chip == true -> "chip"
            else -> "status_bar"
        }
        getSharedPreferences(BtcPrefs.SETTINGS, MODE_PRIVATE).edit()
            .putString(BtcPrefs.LAST_PRICE, price)
            .putString(BtcPrefs.LAST_STATUS, status)
            .putString(BtcPrefs.PRESENTATION, presentation)
            .apply()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createPriceNotification(price))
        runCatching { BtcWidgetProvider.updateAll(this, price) }
        sendBroadcast(
            Intent(ACTION_PRICE)
                .setPackage(packageName)
                .putExtra("price", price)
                .putExtra("status", status)
                .putExtra("presentation", presentation)
        )
    }

    private fun createPriceNotification(price: String): Notification {
        ensurePriceChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, PRICE_CHANNEL)
            .setContentTitle(price)
            .setContentText("Bitcoin")
            .setSmallIcon(R.drawable.ic_stat_btc)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setShowWhen(false)
            .build()
    }

    private fun ensurePriceChannel() {
        if (priceChannelReady || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(PRICE_CHANNEL, "BTC Live Tracker", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        priceChannelReady = true
    }

    private fun ensureAlertChannel(manager: NotificationManager) {
        if (alertChannelReady || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(ALERT_CHANNEL, "BTC Alerts", NotificationManager.IMPORTANCE_HIGH)
        channel.enableVibration(true)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(channel)
        alertChannelReady = true
    }

    @Suppress("DEPRECATION")
    private fun appVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.6.0"
        } catch (_: Exception) {
            "1.6.0"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        updateJob?.cancel()
        serviceJob.cancel()
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        if (configRegistered) {
            runCatching { unregisterComponentCallbacks(configCallbacks) }
            configRegistered = false
        }
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BtcPriceService"
        const val ACTION_PRICE = "com.hermeslux.btclive.PRICE"
        const val EXTRA_ADJUST = "adjust"
        const val NOTIFICATION_ID = 101
        const val NOTIFICATION_ALERT_ID = 102
        private const val PRICE_CHANNEL = "BTC_LIVE_CHANNEL"
        private const val ALERT_CHANNEL = "BTC_ALERT_CHANNEL"
    }
}
