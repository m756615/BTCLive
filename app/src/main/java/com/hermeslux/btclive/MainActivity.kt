package com.hermeslux.btclive

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hermeslux.btclive.ui.theme.BTCLiveTheme
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

enum class AppTheme { SYSTEM, LIGHT, DARK }

class MainActivity : ComponentActivity() {
    private var resumeEpoch by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val epoch = resumeEpoch
            val prefs = getSharedPreferences(BtcPrefs.SETTINGS, MODE_PRIVATE)
            var appTheme by remember {
                mutableStateOf(runCatching {
                    AppTheme.valueOf(prefs.getString(BtcPrefs.THEME, "DARK") ?: "DARK")
                }.getOrDefault(AppTheme.DARK))
            }
            val useDarkTheme = when (appTheme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            BTCLiveTheme(darkTheme = useDarkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        BtcHomeScreen(
                            currentTheme = appTheme,
                            onThemeChange = {
                                appTheme = it
                                prefs.edit().putString(BtcPrefs.THEME, it.name).apply()
                            },
                            resumeEpoch = epoch,
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeEpoch += 1
    }
}

@Composable
fun BtcHomeScreen(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    resumeEpoch: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsPrefs = remember { context.getSharedPreferences(BtcPrefs.SETTINGS, Context.MODE_PRIVATE) }
    val alertsPrefs = remember { context.getSharedPreferences(BtcPrefs.ALERTS, Context.MODE_PRIVATE) }
    var currentMenu by remember { mutableStateOf("MAIN") }

    var hasNotificationPermission by remember { mutableStateOf(notificationGranted(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var selectedColor by remember { mutableStateOf(settingsPrefs.getInt(BtcPrefs.COLOR, Color.WHITE)) }
    var isDynamic by remember { mutableStateOf(settingsPrefs.getBoolean(BtcPrefs.DYNAMIC, true)) }
    var placementMode by remember {
        mutableStateOf(OverlayPlacement.placementFrom(settingsPrefs.getString(BtcPrefs.PLACEMENT, PlacementMode.AUTO.name)))
    }
    var trackingEnabled by remember { mutableStateOf(settingsPrefs.getBoolean(BtcPrefs.TRACKING, true)) }
    var livePrice by remember { mutableStateOf(settingsPrefs.getString(BtcPrefs.LAST_PRICE, "$—") ?: "$—") }
    var liveStatus by remember { mutableStateOf(settingsPrefs.getString(BtcPrefs.LAST_STATUS, "Waiting for a price") ?: "Waiting for a price") }
    var presentation by remember { mutableStateOf(settingsPrefs.getString(BtcPrefs.PRESENTATION, "notification") ?: "notification") }

    val colorOptions = remember {
        listOf(
            Color.WHITE, Color.rgb(245, 245, 245), Color.LTGRAY, Color.GRAY, Color.DKGRAY, Color.BLACK,
            Color.rgb(0, 255, 0), Color.rgb(255, 0, 255),
            Color.rgb(255, 179, 186), Color.rgb(255, 223, 186), Color.rgb(255, 255, 186), Color.rgb(186, 255, 201), Color.rgb(186, 225, 255), Color.rgb(221, 160, 221),
            Color.rgb(250, 218, 221), Color.rgb(255, 250, 205), Color.rgb(240, 255, 240), Color.rgb(224, 255, 225), Color.rgb(240, 248, 255), Color.rgb(230, 230, 250),
            Color.rgb(255, 102, 102), Color.RED, Color.rgb(139, 0, 0), Color.rgb(255, 165, 0), Color.rgb(205, 87, 0), Color.rgb(139, 69, 19),
            Color.rgb(255, 255, 0), Color.rgb(255, 215, 0), Color.rgb(184, 134, 11), Color.rgb(144, 238, 144), Color.GREEN, Color.rgb(0, 100, 0),
            Color.rgb(0, 255, 255), Color.rgb(0, 128, 128), Color.rgb(0, 64, 64), Color.rgb(100, 149, 237), Color.BLUE, Color.rgb(0, 0, 139),
            Color.rgb(128, 0, 128), Color.rgb(75, 0, 130), Color.rgb(255, 192, 203), Color.rgb(255, 20, 147), Color.rgb(46, 139, 87),
            Color.rgb(106, 90, 205), Color.rgb(25, 25, 112), Color.rgb(47, 79, 79), Color.rgb(255, 127, 80), Color.rgb(123, 104, 238), Color.rgb(218, 165, 32),
            Color.rgb(70, 130, 180), Color.rgb(32, 178, 170), Color.rgb(112, 128, 144),
        )
    }

    fun startService(adjust: Boolean = false) {
        val intent = Intent(context, BtcPriceService::class.java).apply {
            putExtra("color", selectedColor)
            putExtra("dynamic", isDynamic)
            if (adjust) putExtra(BtcPriceService.EXTRA_ADJUST, true)
        }
        try {
            context.startForegroundService(intent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Couldn't start tracking. Allow background activity for BTCLive, then open the app again.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasNotificationPermission = granted
            if (trackingEnabled) startService()
        },
    )
    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            hasOverlayPermission = Settings.canDrawOverlays(context)
            if (trackingEnabled) startService()
        },
    )
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                val data = JSONObject().apply {
                    put("theme", currentTheme.name)
                    put("color", selectedColor)
                    put("isDynamic", isDynamic)
                    put("placement", placementMode.name)
                    put("tracking", trackingEnabled)
                    if (settingsPrefs.contains(BtcPrefs.CUSTOM_X)) {
                        put("customX", settingsPrefs.getFloat(BtcPrefs.CUSTOM_X, 0f).toDouble())
                    }
                    if (settingsPrefs.contains(BtcPrefs.CUSTOM_Y)) {
                        put("customY", settingsPrefs.getFloat(BtcPrefs.CUSTOM_Y, 0f).toDouble())
                    }
                    put("alerts", JSONArray(alertsPrefs.getString(BtcPrefs.ACTIVE, "[]") ?: "[]"))
                    put("history", JSONArray(alertsPrefs.getString(BtcPrefs.HISTORY, "[]") ?: "[]"))
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(data.toString().toByteArray()) }
            }.onFailure {
                Toast.makeText(context, "Couldn't save the backup.", Toast.LENGTH_SHORT).show()
            }
        },
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input)).readText()
                } ?: error("Empty file")
                val data = JSONObject(text)
                onThemeChange(runCatching { AppTheme.valueOf(data.optString("theme", "DARK")) }.getOrDefault(AppTheme.DARK))
                val color = data.optInt("color", Color.WHITE)
                val dynamic = data.optBoolean("isDynamic", true)
                val placement = OverlayPlacement.placementFrom(data.optString("placement", PlacementMode.AUTO.name))
                val tracking = if (data.has("tracking")) data.optBoolean("tracking", true) else trackingEnabled
                selectedColor = color
                isDynamic = dynamic
                placementMode = placement
                trackingEnabled = tracking
                val editor = settingsPrefs.edit()
                    .putInt(BtcPrefs.COLOR, color)
                    .putBoolean(BtcPrefs.DYNAMIC, dynamic)
                    .putString(BtcPrefs.PLACEMENT, placement.name)
                    .putBoolean(BtcPrefs.TRACKING, tracking)
                if (data.has("customX")) editor.putFloat(BtcPrefs.CUSTOM_X, data.getDouble("customX").toFloat())
                if (data.has("customY")) editor.putFloat(BtcPrefs.CUSTOM_Y, data.getDouble("customY").toFloat())
                editor.apply()
                alertsPrefs.edit()
                    .putString(BtcPrefs.ACTIVE, data.optJSONArray("alerts")?.toString() ?: "[]")
                    .putString(BtcPrefs.HISTORY, data.optJSONArray("history")?.toString() ?: "[]")
                    .apply()
                if (tracking) startService() else context.stopService(Intent(context, BtcPriceService::class.java))
                Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "That file isn't a BTCLive backup.", Toast.LENGTH_SHORT).show()
            }
        },
    )

    LaunchedEffect(resumeEpoch, trackingEnabled) {
        hasNotificationPermission = notificationGranted(context)
        hasOverlayPermission = Settings.canDrawOverlays(context)
        if (trackingEnabled) startService() else context.stopService(Intent(context, BtcPriceService::class.java))
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                livePrice = intent?.getStringExtra("price") ?: livePrice
                liveStatus = intent?.getStringExtra("status") ?: liveStatus
                presentation = intent?.getStringExtra("presentation") ?: presentation
            }
        }
        val filter = IntentFilter(BtcPriceService.ACTION_PRICE)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    BackHandler(enabled = currentMenu != "MAIN") { currentMenu = "MAIN" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(currentMenu) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 80 && currentMenu != "MAIN") currentMenu = "MAIN"
                }
            }
            .padding(16.dp),
    ) {
        AnimatedContent(
            targetState = currentMenu,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState != "MAIN") {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "MenuTransition",
        ) { targetMenu ->
            when (targetMenu) {
                "MAIN" -> MainMenu(
                    price = livePrice,
                    status = if (trackingEnabled) liveStatus else "Paused",
                    presentation = presentationLabel(presentation, hasOverlayPermission),
                    tracking = trackingEnabled,
                    hasNotification = hasNotificationPermission,
                    hasOverlay = hasOverlayPermission,
                    onTracking = { enabled ->
                        trackingEnabled = enabled
                        settingsPrefs.edit().putBoolean(BtcPrefs.TRACKING, enabled).apply()
                    },
                    onGrantNotification = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onGrantOverlay = {
                        overlayLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        )
                    },
                    onAddWidget = { requestPinWidget(context) },
                    onNavigate = { currentMenu = it },
                )
                "THEME" -> ThemeMenu(
                    currentAppTheme = currentTheme,
                    isDynamicContrast = isDynamic,
                    onBack = { currentMenu = "MAIN" },
                    onSelectDynamic = { dynamic ->
                        isDynamic = dynamic
                        settingsPrefs.edit().putBoolean(BtcPrefs.DYNAMIC, dynamic).apply()
                        startService()
                    },
                    onSelectTheme = onThemeChange,
                )
                "COLOR" -> ColorMenu(
                    selectedColor = selectedColor,
                    options = colorOptions,
                    onBack = { currentMenu = "MAIN" },
                    onSelectColor = { color ->
                        isDynamic = false
                        selectedColor = color
                        settingsPrefs.edit().putInt(BtcPrefs.COLOR, color).putBoolean(BtcPrefs.DYNAMIC, false).apply()
                        startService()
                    },
                )
                "POSITION" -> PositionMenu(
                    mode = placementMode,
                    hint = clockSideHint(Build.MANUFACTURER ?: "", Build.BRAND ?: ""),
                    overlayAllowed = hasOverlayPermission,
                    onBack = { currentMenu = "MAIN" },
                    onSelect = { mode ->
                        placementMode = mode
                        settingsPrefs.edit().putString(BtcPrefs.PLACEMENT, mode.name).apply()
                        if (!hasOverlayPermission) {
                            Toast.makeText(context, "Allow display over other apps to show the price on screen.", Toast.LENGTH_LONG).show()
                        }
                        startService()
                    },
                    onDrag = {
                        if (!Settings.canDrawOverlays(context)) {
                            Toast.makeText(context, "Allow display over other apps, then try again.", Toast.LENGTH_LONG).show()
                        } else {
                            placementMode = PlacementMode.CUSTOM
                            settingsPrefs.edit().putString(BtcPrefs.PLACEMENT, PlacementMode.CUSTOM.name).apply()
                            startService(adjust = true)
                            Toast.makeText(context, "Drag the price, then let go to lock it.", Toast.LENGTH_LONG).show()
                        }
                    },
                )
                "ALERTS" -> PriceAlertsMenu(onBack = { currentMenu = "MAIN" })
                "BACKUP" -> BackupMenu(
                    onBack = { currentMenu = "MAIN" },
                    onPerformBackup = { exportLauncher.launch("BTCLive_Backup.json") },
                    onPerformImport = { importLauncher.launch(arrayOf("application/json")) },
                )
                "NOTES" -> ReleaseNotesMenu(onBack = { currentMenu = "MAIN" })
                "INSTRUCTIONS" -> InstructionsMenu(onBack = { currentMenu = "MAIN" })
                "ABOUT" -> AboutMenu(onBack = { currentMenu = "MAIN" })
            }
        }
    }
}

private fun notificationGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 33) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun presentationLabel(presentation: String, hasOverlay: Boolean): String {
    if (!hasOverlay) return "Price is in the notification and widget until overlay is allowed"
    return when (presentation) {
        "chip" -> "Floating under the status bar"
        "status_bar" -> "In the status bar, beside the clock"
        else -> "Price is in the notification and widget"
    }
}

private fun requestPinWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, BtcWidgetProvider::class.java)
    if (!manager.isRequestPinAppWidgetSupported) {
        Toast.makeText(context, "Long-press the home screen, then choose Widgets > BTCLive.", Toast.LENGTH_LONG).show()
        return
    }
    val pinned = runCatching { manager.requestPinAppWidget(provider, null, null) }.getOrDefault(false)
    if (!pinned) {
        Toast.makeText(context, "Long-press the home screen, then choose Widgets > BTCLive.", Toast.LENGTH_LONG).show()
    }
}

private fun openBatterySettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    try {
        context.startActivity(request)
    } catch (_: Exception) {
        try {
            context.startActivity(details)
        } catch (_: Exception) {
            Toast.makeText(context, "Open BTCLive in system settings and set Battery to Unrestricted.", Toast.LENGTH_LONG).show()
        }
    }
}

private fun openAutostart(context: Context, targets: List<AutostartTarget>) {
    for (target in targets) {
        val intent = Intent().setComponent(ComponentName(target.packageName, target.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (_: Exception) {
        }
    }
    openBatterySettings(context)
}

@Composable
fun MainMenu(
    price: String,
    status: String,
    presentation: String,
    tracking: Boolean,
    hasNotification: Boolean,
    hasOverlay: Boolean,
    onTracking: (Boolean) -> Unit,
    onGrantNotification: () -> Unit,
    onGrantOverlay: () -> Unit,
    onAddWidget: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("BTCLive Pro", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(price, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                Text(presentation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Live", style = MaterialTheme.typography.labelMedium)
                Switch(checked = tracking, onCheckedChange = onTracking)
            }
        }
        TextButton(onClick = onAddWidget) { Text("Add home screen widget") }
        if (!hasNotification || !hasOverlay) {
            PermissionBanner(hasNotification, hasOverlay, onGrantNotification, onGrantOverlay)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        MenuItem("Theme", Icons.Default.Settings, "App style and contrast") { onNavigate("THEME") }
        MenuItem("Text color options", Icons.Default.Palette, "52 colors for the price") { onNavigate("COLOR") }
        MenuItem("On-screen position", Icons.Default.OpenWith, "Status bar, floating chip, or drag") { onNavigate("POSITION") }
        MenuItem("Price Alerts", Icons.Default.NotificationsActive, "Target price notifications") { onNavigate("ALERTS") }
        MenuItem("Backup & Cloud", Icons.Default.Backup, "Save and restore settings") { onNavigate("BACKUP") }
        MenuItem("Instructions", Icons.AutoMirrored.Filled.HelpOutline, "Setup for this phone") { onNavigate("INSTRUCTIONS") }
        MenuItem("Release Notes", Icons.Default.Description, "What's new in v1.6.0") { onNavigate("NOTES") }
        MenuItem("About", Icons.Default.Info, "App information") { onNavigate("ABOUT") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PermissionBanner(
    hasNotif: Boolean,
    hasOverlay: Boolean,
    onGrantNotification: () -> Unit,
    onGrantOverlay: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Optional permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "The price already updates here and in the notification. Overlay permission puts it on the status bar. Notification permission lets alert popups through.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!hasNotif && Build.VERSION.SDK_INT >= 33) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onGrantNotification, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow notifications")
                }
            }
            if (!hasOverlay) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onGrantOverlay, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow display over other apps")
                }
            }
        }
    }
}

@Composable
fun PositionMenu(
    mode: PlacementMode,
    hint: String,
    overlayAllowed: Boolean,
    onBack: () -> Unit,
    onSelect: (PlacementMode) -> Unit,
    onDrag: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
        BackButton(onBack)
        Text("On-screen position", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!overlayAllowed) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Display over other apps is off, so the price stays in the notification and widget.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
        SelectionItem("Auto", "Fit this phone's clock, cutout, and screen size", mode == PlacementMode.AUTO) { onSelect(PlacementMode.AUTO) }
        SelectionItem("Left of the status bar", "Just after a left-side clock", mode == PlacementMode.LEFT) { onSelect(PlacementMode.LEFT) }
        SelectionItem("Right of the status bar", "Just before a right-side clock", mode == PlacementMode.RIGHT) { onSelect(PlacementMode.RIGHT) }
        SelectionItem("Under the status bar", "A chip that stays readable on any wallpaper", mode == PlacementMode.BELOW) { onSelect(PlacementMode.BELOW) }
        SelectionItem("Custom spot", "Use the position you dragged into place", mode == PlacementMode.CUSTOM) { onSelect(PlacementMode.CUSTOM) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDrag, modifier = Modifier.fillMaxWidth()) { Text("Drag the price into place") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ReleaseNotesMenu(onBack: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
        BackButton(onBack)
        Text("Release Notes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("Version 1.6.0", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("October 8, 2026", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        BulletPoint("Status-bar price fits notches, punch-holes, and both clock sides")
        BulletPoint("Drag the price, or park it under the status bar when the bar is full")
        BulletPoint("Home screen widget and an in-app price that work without overlay permission")
        BulletPoint("Menus scroll on small phones, large text, landscape, and tablets")
        BulletPoint("Battery and autostart steps for Samsung, Xiaomi, Oppo, vivo, Huawei, and others")
        BulletPoint("Backup price sources, with slower updates while the screen is off")
        BulletPoint("Restarts after reboot and after the app is updated")
        Spacer(Modifier.height(16.dp))
        Text("Version 1.5.0", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("May 25, 2026", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        BulletPoint("Initial public release with alerts, 52 colors, and backup")
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("• ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PriceAlertsMenu(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BtcPrefs.ALERTS, Context.MODE_PRIVATE) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var alertPrice by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var alertType by remember { mutableStateOf("ABOVE") }
    var activeAlerts by remember { mutableStateOf(prefs.getString(BtcPrefs.ACTIVE, "[]") ?: "[]") }
    var alertHistory by remember { mutableStateOf(prefs.getString(BtcPrefs.HISTORY, "[]") ?: "[]") }

    fun refreshData() {
        activeAlerts = prefs.getString(BtcPrefs.ACTIVE, "[]") ?: "[]"
        alertHistory = prefs.getString(BtcPrefs.HISTORY, "[]") ?: "[]"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackButton(onBack)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Price Alerts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = { showEmailDialog = true }) { Icon(Icons.Default.Email, contentDescription = "Email log") }
                IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add alert") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Active Alerts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        LazyColumn(modifier = Modifier.weight(0.4f).fillMaxWidth()) {
            val array = runCatching { JSONArray(activeAlerts) }.getOrDefault(JSONArray())
            if (array.length() == 0) {
                item { Text("No active alerts", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp)) }
            }
            items((0 until array.length()).toList()) { i ->
                val alert = array.optJSONObject(i) ?: return@items
                AlertItem(alert, onRemove = {
                    val newArray = JSONArray()
                    for (j in 0 until array.length()) if (i != j) newArray.put(array.get(j))
                    prefs.edit().putString(BtcPrefs.ACTIVE, newArray.toString()).apply()
                    refreshData()
                })
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("History", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        LazyColumn(modifier = Modifier.weight(0.6f).fillMaxWidth()) {
            val array = runCatching { JSONArray(alertHistory) }.getOrDefault(JSONArray())
            if (array.length() == 0) {
                item { Text("No hits yet", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp)) }
            }
            val historyList = (0 until array.length()).mapNotNull { array.optJSONObject(it) }.reversed()
            items(historyList) { history -> HistoryItem(history) }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Set Price Alert") },
            text = {
                Column {
                    Text("A high-priority notification fires when the target is hit.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = alertPrice,
                        onValueChange = { alertPrice = it },
                        label = { Text("Target Price ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Notify when price is:")
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { alertType = if (alertType == "ABOVE") "BELOW" else "ABOVE" }) {
                            Text(if (alertType == "ABOVE") "Above ↑" else "Below ↓")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = alertPrice.toDoubleOrNull()
                    if (target != null && target > 0.0 && target < 100_000_000.0) {
                        val array = runCatching { JSONArray(activeAlerts) }.getOrDefault(JSONArray())
                        array.put(JSONObject().put("target", target).put("type", alertType))
                        prefs.edit().putString(BtcPrefs.ACTIVE, array.toString()).apply()
                        refreshData()
                        alertPrice = ""
                        showAddDialog = false
                    } else {
                        Toast.makeText(context, "Enter a price greater than zero.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { Button(onClick = { showAddDialog = false }) { Text("Cancel") } },
        )
    }

    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text("Send Log to Email") },
            text = {
                Column {
                    Text("The log, including alerts that have not hit yet, is sent with your email app.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emailAddress,
                        onValueChange = { emailAddress = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!emailAddress.contains("@") || !emailAddress.contains(".")) {
                        Toast.makeText(context, "Enter a valid email address.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val sb = StringBuilder("BTC Alert Log\n\nActive Alerts:\n")
                    val active = runCatching { JSONArray(activeAlerts) }.getOrDefault(JSONArray())
                    for (i in 0 until active.length()) {
                        val alert = active.optJSONObject(i) ?: continue
                        sb.append("- Target: $${alert.optDouble("target")} (${alert.optString("type")})\n")
                    }
                    sb.append("\nHistory:\n")
                    val hist = runCatching { JSONArray(alertHistory) }.getOrDefault(JSONArray())
                    for (i in 0 until hist.length()) {
                        val entry = hist.optJSONObject(i) ?: continue
                        sb.append("- Hit $${entry.optDouble("target")} at ${entry.optString("timestamp")} (Price was ${entry.optString("hitPrice")})\n")
                    }
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
                        putExtra(Intent.EXTRA_SUBJECT, "BTCLive Alert Log")
                        putExtra(Intent.EXTRA_TEXT, sb.toString())
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, "Send Email"))
                        showEmailDialog = false
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No email app found.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Submit") }
            },
            dismissButton = { Button(onClick = { showEmailDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun BackupMenu(onBack: () -> Unit, onPerformBackup: () -> Unit, onPerformImport: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
        BackButton(onBack)
        Text("Backup & Cloud", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text(
            "Export a JSON file to Drive, OneDrive, or anywhere else you keep files. Import restores theme, colors, position, alerts, and history on this phone or another one.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPerformBackup, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Backup, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Export backup")
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPerformImport,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(),
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Import from file")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ThemeMenu(
    currentAppTheme: AppTheme,
    isDynamicContrast: Boolean,
    onBack: () -> Unit,
    onSelectDynamic: (Boolean) -> Unit,
    onSelectTheme: (AppTheme) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
        BackButton(onBack)
        Text("Theme Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("App Visual Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        SelectionItem("System Default", "Matches phone settings", currentAppTheme == AppTheme.SYSTEM) { onSelectTheme(AppTheme.SYSTEM) }
        SelectionItem("Light Mode", "Force light theme", currentAppTheme == AppTheme.LIGHT) { onSelectTheme(AppTheme.LIGHT) }
        SelectionItem("Dark Mode", "Force dark theme", currentAppTheme == AppTheme.DARK) { onSelectTheme(AppTheme.DARK) }
        Spacer(Modifier.height(24.dp))
        Text("Status Bar Contrast", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        SelectionItem("Dynamic Contrast", "White on a dark status bar, black on a light one", isDynamicContrast) { onSelectDynamic(true) }
        SelectionItem("Fixed Color", "Use the color picked in Text color options", !isDynamicContrast) { onSelectDynamic(false) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ColorMenu(selectedColor: Int, options: List<Int>, onBack: () -> Unit, onSelectColor: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        BackButton(onBack)
        Text("Text Color", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("${options.size} colors. Picking one turns off dynamic contrast.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 56.dp),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(options) { color ->
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(color))
                        .border(
                            if (selectedColor == color) 4.dp else 1.dp,
                            if (selectedColor == color) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.LightGray,
                            CircleShape,
                        )
                        .clickable { onSelectColor(color) },
                )
            }
        }
    }
}

@Composable
fun InstructionsMenu(onBack: () -> Unit) {
    val context = LocalContext.current
    val guidance = remember { oemGuidance(Build.MANUFACTURER ?: "", Build.BRAND ?: "") }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
        BackButton(onBack)
        Text("Setup for ${guidance.brand}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        InstructionStep(1, "See the price", "The home screen shows the live price on every phone. Allow notifications for the shade and lock screen, and display over other apps for the status bar.")
        InstructionStep(2, "Place it", "Open On-screen position. Auto avoids the clock and the camera hole. If the status bar is crowded, the price drops to a chip underneath it. Drag it if you want a different spot.")
        InstructionStep(3, "Keep updates running", guidance.batterySteps)
        Button(onClick = { openBatterySettings(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open battery settings")
        }
        if (guidance.autostartNote != null) {
            Spacer(Modifier.height(12.dp))
            Text(guidance.autostartNote, style = MaterialTheme.typography.bodyMedium)
            if (guidance.autostartTargets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { openAutostart(context, guidance.autostartTargets) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open autostart settings")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        InstructionStep(4, "Home screen widget", "Add the BTCLive widget from the home screen menu, or use Add home screen widget. It updates with the tracker.")
        InstructionStep(5, "Lock screen", "The ongoing notification is public, so the price can show on the lock screen when notifications are visible.")
        InstructionStep(6, "Alerts and backup", "Set a target to get a high-priority alert. Export a backup if you switch phones.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun InstructionStep(number: Int, title: String, description: String) {
    Row(modifier = Modifier.padding(vertical = 12.dp)) {
        Box(
            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AboutMenu(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.6.0"
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) { BackButton(onBack) }
        Text("About", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Created by Hermes Lux.",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/HermesLux"))
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No browser found.", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Follow on X @HermesLux") }
        Spacer(Modifier.height(32.dp))
        Text("Version $version", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(
            "Android 8 and newer, including phones and tablets.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AlertItem(alert: JSONObject, onRemove: () -> Unit) {
    val type = alert.optString("type")
    val target = alert.optDouble("target", Double.NaN)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Price ${if (type == "ABOVE") "Above" else "Below"}", style = MaterialTheme.typography.labelMedium)
                Text(
                    if (target.isFinite()) formatUsdPrice(target) else "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}

@Composable
fun HistoryItem(history: JSONObject) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Hit ${formatUsdPrice(history.optDouble("target"))}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(history.optString("timestamp"), style = MaterialTheme.typography.labelSmall)
            }
            Text("Actual Price: ${history.optString("hitPrice")}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MenuItem(title: String, icon: ImageVector, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SelectionItem(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else androidx.compose.ui.graphics.Color.Transparent,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        if (selected) {
            Box(modifier = Modifier.padding(end = 16.dp).size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun BackButton(onBack: () -> Unit) {
    Text(
        "< Back",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onBack).padding(bottom = 16.dp),
        style = MaterialTheme.typography.labelLarge,
    )
}
