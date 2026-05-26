package com.hermeslux.btclive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs = getSharedPreferences("btc_settings", MODE_PRIVATE)
            var appTheme by remember { 
                mutableStateOf(AppTheme.valueOf(prefs.getString("app_theme", "DARK") ?: "DARK")) 
            }
            
            val useDarkTheme = when (appTheme) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            BTCLiveTheme(darkTheme = useDarkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BtcHomeScreen(
                        currentTheme = appTheme,
                        onThemeChange = { 
                            appTheme = it
                            prefs.edit().putString("app_theme", it.name).apply()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun BtcHomeScreen(
    currentTheme: AppTheme,
    onThemeChange: (AppTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsPrefs = remember { context.getSharedPreferences("btc_settings", Context.MODE_PRIVATE) }
    val alertsPrefs = remember { context.getSharedPreferences("btc_alerts", Context.MODE_PRIVATE) }
    var currentMenu by remember { mutableStateOf("MAIN") }
    
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    var selectedColor by remember { mutableStateOf(settingsPrefs.getInt("selected_color", Color.WHITE)) }
    var isDynamic by remember { mutableStateOf(settingsPrefs.getBoolean("is_dynamic", true)) }

    val colorOptions = remember {
        listOf(
            // Grayscale
            Color.WHITE, Color.rgb(245, 245, 245), Color.LTGRAY, Color.GRAY, Color.DKGRAY, Color.BLACK,
            // Ultra Brights
            Color.rgb(0, 255, 0), Color.rgb(255, 0, 255),
            // Pastel Row 1
            Color.rgb(255, 179, 186), Color.rgb(255, 223, 186), Color.rgb(255, 255, 186), Color.rgb(186, 255, 201), Color.rgb(186, 225, 255), Color.rgb(221, 160, 221),
            // Pastel Row 2
            Color.rgb(250, 218, 221), Color.rgb(255, 250, 205), Color.rgb(240, 255, 240), Color.rgb(224, 255, 225), Color.rgb(240, 248, 255), Color.rgb(230, 230, 250),
            // Bold Colors
            Color.rgb(255, 102, 102), Color.RED, Color.rgb(139, 0, 0), Color.rgb(255, 165, 0), Color.rgb(205, 87, 0), Color.rgb(139, 69, 19),
            Color.rgb(255, 255, 0), Color.rgb(255, 215, 0), Color.rgb(184, 134, 11), Color.rgb(144, 238, 144), Color.GREEN, Color.rgb(0, 100, 0),
            Color.rgb(0, 255, 255), Color.rgb(0, 128, 128), Color.rgb(0, 64, 64), Color.rgb(100, 149, 237), Color.BLUE, Color.rgb(0, 0, 139),
            Color.rgb(128, 0, 128), Color.rgb(75, 0, 130), Color.rgb(255, 192, 203), Color.rgb(255, 20, 147), Color.rgb(46, 139, 87),
            Color.rgb(106, 90, 205), Color.rgb(25, 25, 112), Color.rgb(47, 79, 79), Color.rgb(255, 127, 80), Color.rgb(123, 104, 238), Color.rgb(218, 165, 32)
        ).take(52)
    }

    fun startService(color: Int, dynamic: Boolean) {
        val intent = Intent(context, BtcPriceService::class.java).apply {
            putExtra("color", color)
            putExtra("dynamic", dynamic)
        }
        context.startForegroundService(intent)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasNotificationPermission = isGranted }
    )

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { hasOverlayPermission = Settings.canDrawOverlays(context) }
    )

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                val data = JSONObject().apply {
                    put("theme", currentTheme.name)
                    put("color", selectedColor)
                    put("isDynamic", isDynamic)
                    put("alerts", JSONArray(alertsPrefs.getString("active_alerts", "[]")))
                    put("history", JSONArray(alertsPrefs.getString("alert_history", "[]")))
                }
                context.contentResolver.openOutputStream(it)?.use { os -> os.write(data.toString().toByteArray()) }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val sb = StringBuilder()
                    var line: String? = reader.readLine()
                    while (line != null) { sb.append(line); line = reader.readLine() }
                    val data = JSONObject(sb.toString())
                    onThemeChange(AppTheme.valueOf(data.optString("theme", "DARK")))
                    val color = data.optInt("color", Color.WHITE)
                    selectedColor = color
                    val dynamic = data.optBoolean("isDynamic", true)
                    isDynamic = dynamic
                    settingsPrefs.edit().putInt("selected_color", color).putBoolean("is_dynamic", dynamic).apply()
                    alertsPrefs.edit().putString("active_alerts", data.optJSONArray("alerts")?.toString() ?: "[]")
                                     .putString("alert_history", data.optJSONArray("history")?.toString() ?: "[]").apply()
                    startService(color, dynamic)
                }
            }
        }
    )

    LaunchedEffect(hasNotificationPermission, hasOverlayPermission) {
        if (hasNotificationPermission && hasOverlayPermission) startService(selectedColor, isDynamic)
    }

    BackHandler(enabled = currentMenu != "MAIN") { currentMenu = "MAIN" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 50 && currentMenu != "MAIN") currentMenu = "MAIN"
                }
            }
            .padding(16.dp)
    ) {
        if (!hasNotificationPermission || !hasOverlayPermission) {
            PermissionSection(
                hasNotificationPermission, hasOverlayPermission,
                onGrantNotification = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onGrantOverlay = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    overlayLauncher.launch(intent)
                }
            )
        } else {
            AnimatedContent(
                targetState = currentMenu,
                transitionSpec = {
                    if (targetState != "MAIN") slideInHorizontally { it }.togetherWith(slideOutHorizontally { -it })
                    else slideInHorizontally { -it }.togetherWith(slideOutHorizontally { it })
                },
                label = "MenuTransition"
            ) { targetMenu ->
                when (targetMenu) {
                    "MAIN" -> MainMenu(onNavigate = { currentMenu = it })
                    "THEME" -> ThemeMenu(currentAppTheme = currentTheme, isDynamicContrast = isDynamic, onBack = { currentMenu = "MAIN" },
                        onSelectDynamic = { isDynamic = it; startService(selectedColor, it) }, onSelectTheme = onThemeChange)
                    "COLOR" -> ColorMenu(selectedColor, colorOptions, onBack = { currentMenu = "MAIN" }, onSelectColor = {
                        isDynamic = false; selectedColor = it; startService(it, false)
                    })
                    "ALERTS" -> PriceAlertsMenu(onBack = { currentMenu = "MAIN" })
                    "BACKUP" -> BackupMenu(onBack = { currentMenu = "MAIN" }, 
                        onPerformBackup = { exportLauncher.launch("BTCLive_Backup.json") },
                        onPerformImport = { importLauncher.launch(arrayOf("application/json")) })
                    "NOTES" -> ReleaseNotesMenu(onBack = { currentMenu = "MAIN" })
                    "INSTRUCTIONS" -> InstructionsMenu(onBack = { currentMenu = "MAIN" })
                    "ABOUT" -> AboutMenu(onBack = { currentMenu = "MAIN" })
                }
            }
        }
    }
}

@Composable
fun MainMenu(onNavigate: (String) -> Unit) {
    Column {
        Text("BTCLive Pro", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        MenuItem(title = "Theme", icon = Icons.Default.Settings, subtitle = "App style and contrast", onClick = { onNavigate("THEME") })
        MenuItem(title = "Text color options", icon = Icons.Default.Palette, subtitle = "Choose from 52 colors", onClick = { onNavigate("COLOR") })
        MenuItem(title = "Price Alerts", icon = Icons.Default.NotificationsActive, subtitle = "Target price notifications", onClick = { onNavigate("ALERTS") })
        MenuItem(title = "Backup & Cloud", icon = Icons.Default.Backup, subtitle = "Save and restore settings", onClick = { onNavigate("BACKUP") })
        MenuItem(title = "Instructions", icon = Icons.Default.HelpOutline, subtitle = "Full setup guide", onClick = { onNavigate("INSTRUCTIONS") })
        MenuItem(title = "Release Notes", icon = Icons.Default.Description, subtitle = "What's new in v1.5.0", onClick = { onNavigate("NOTES") })
        MenuItem(title = "About", icon = Icons.Default.Info, subtitle = "App information", onClick = { onNavigate("ABOUT") })
    }
}

@Composable
fun ReleaseNotesMenu(onBack: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        BackButton(onBack)
        Text("Release Notes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        
        Text("Version 1.5.0 (Initial Public Release)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Compiled: May 25, 2026", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        
        Text("Full Feature List:", fontWeight = FontWeight.Bold)
        BulletPoint("Real-time BTC status bar price overlay (Android 14+)")
        BulletPoint("High-frequency 5-second market updates")
        BulletPoint("Price Alerts with high-priority system popups")
        BulletPoint("Alert history with exact timestamp and market price")
        BulletPoint("52 sorted colors (Grayscale, Pastels, Ultra-Brights)")
        BulletPoint("Dynamic Contrast mode for auto Light/Dark readability")
        BulletPoint("Full cloud Backup & Restore (.json)")
        BulletPoint("Exportable email logs for all alert targets")
        BulletPoint("Start-on-boot and crash recovery persistence")
        BulletPoint("Swipe-to-back navigation with smooth transitions")
        
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
    val prefs = remember { context.getSharedPreferences("btc_alerts", Context.MODE_PRIVATE) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var alertPrice by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf("") }
    var alertType by remember { mutableStateOf("ABOVE") }
    var activeAlerts by remember { mutableStateOf(prefs.getString("active_alerts", "[]") ?: "[]") }
    var alertHistory by remember { mutableStateOf(prefs.getString("alert_history", "[]") ?: "[]") }

    fun refreshData() {
        activeAlerts = prefs.getString("active_alerts", "[]") ?: "[]"
        alertHistory = prefs.getString("alert_history", "[]") ?: "[]"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackButton(onBack)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Price Alerts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = { showEmailDialog = true }) { Icon(Icons.Default.Email, contentDescription = "Email Log") }
                IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add Alert") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Active Alerts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        LazyColumn(modifier = Modifier.weight(0.4f).fillMaxWidth()) {
            val array = JSONArray(activeAlerts)
            items((0 until array.length()).toList()) { i ->
                val alert = array.getJSONObject(i)
                AlertItem(alert, onRemove = {
                    val newArray = JSONArray()
                    for (j in 0 until array.length()) if (i != j) newArray.put(array.get(j))
                    prefs.edit().putString("active_alerts", newArray.toString()).apply()
                    refreshData()
                })
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("History", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        LazyColumn(modifier = Modifier.weight(0.6f).fillMaxWidth()) {
            val array = JSONArray(alertHistory)
            val historyList = (0 until array.length()).map { array.getJSONObject(it) }.reversed()
            items(historyList) { history -> HistoryItem(history) }
        }
    }

    if (showAddDialog) {
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("Set Price Alert") }, text = {
            Column {
                Text("Popup alerts appear over other apps.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = alertPrice, onValueChange = { alertPrice = it }, label = { Text("Target Price ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notify when price is:")
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { alertType = if (alertType == "ABOVE") "BELOW" else "ABOVE" }) { Text(if (alertType == "ABOVE") "Above ↑" else "Below ↓") }
                }
            }
        }, confirmButton = { Button(onClick = {
            val target = alertPrice.toDoubleOrNull()
            if (target != null) {
                val array = JSONArray(activeAlerts); array.put(JSONObject().put("target", target).put("type", alertType))
                prefs.edit().putString("active_alerts", array.toString()).apply(); refreshData(); showAddDialog = false
            }
        }) { Text("Confirm") } }, dismissButton = { Button(onClick = { showAddDialog = false }) { Text("Cancel") } })
    }

    if (showEmailDialog) {
        AlertDialog(onDismissRequest = { showEmailDialog = false }, title = { Text("Send Log to Email") }, text = {
            Column {
                Text("The log including unhit targets will be sent.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = emailAddress, onValueChange = { emailAddress = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
            }
        }, confirmButton = { Button(onClick = {
            val sb = StringBuilder("BTC Alert Log\n\nActive Alerts:\n")
            val active = JSONArray(activeAlerts)
            for (i in 0 until active.length()) { val a = active.getJSONObject(i); sb.append("- Target: $${a.getDouble("target")} (${a.getString("type")})\n") }
            sb.append("\nHistory:\n")
            val hist = JSONArray(alertHistory)
            for (i in 0 until hist.length()) { val h = hist.getJSONObject(i); sb.append("- Hit $${h.getDouble("target")} at ${h.getString("timestamp")} (Price was ${h.getString("hitPrice")})\n") }
            val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:"); putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress)); putExtra(Intent.EXTRA_SUBJECT, "BTCLive Alert Log"); putExtra(Intent.EXTRA_TEXT, sb.toString()) }
            context.startActivity(Intent.createChooser(intent, "Send Email")); showEmailDialog = false
        }) { Text("Submit") } }, dismissButton = { Button(onClick = { showEmailDialog = false }) { Text("Cancel") } })
    }
}

@Composable
fun BackupMenu(onBack: () -> Unit, onPerformBackup: () -> Unit, onPerformImport: () -> Unit) {
    Column {
        BackButton(onBack)
        Text("Backup & Cloud", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("Manage your settings. Export to save a file to your cloud (Drive, OneDrive), or Import to restore from a file.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPerformBackup, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Backup, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Export to Cloud")
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPerformImport, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.filledTonalButtonColors()) {
            Icon(Icons.Default.FileDownload, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Import from File")
        }
    }
}

@Composable
fun ThemeMenu(currentAppTheme: AppTheme, isDynamicContrast: Boolean, onBack: () -> Unit, onSelectDynamic: (Boolean) -> Unit, onSelectTheme: (AppTheme) -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        BackButton(onBack)
        Text("Theme Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("App Visual Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        SelectionItem(title = "System Default", subtitle = "Matches phone settings", selected = currentAppTheme == AppTheme.SYSTEM, onClick = { onSelectTheme(AppTheme.SYSTEM) })
        SelectionItem(title = "Light Mode", subtitle = "Force light theme", selected = currentAppTheme == AppTheme.LIGHT, onClick = { onSelectTheme(AppTheme.LIGHT) })
        SelectionItem(title = "Dark Mode", subtitle = "Force dark theme", selected = currentAppTheme == AppTheme.DARK, onClick = { onSelectTheme(AppTheme.DARK) })
        Spacer(Modifier.height(24.dp))
        Text("Status Bar Contrast", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        SelectionItem(title = "Dynamic Contrast", subtitle = "Auto Black/White based on screen", selected = isDynamicContrast, onClick = { onSelectDynamic(true) })
        SelectionItem(title = "Fixed Color", subtitle = "Use manual color from Color menu", selected = !isDynamicContrast, onClick = { onSelectDynamic(false) })
    }
}

@Composable
fun ColorMenu(selectedColor: Int, options: List<Int>, onBack: () -> Unit, onSelectColor: (Int) -> Unit) {
    Column {
        BackButton(onBack)
        Text("Text Color", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 60.dp), contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(options) { color ->
                Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(color))
                    .border(if (selectedColor == color) 4.dp else 1.dp, if (selectedColor == color) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.LightGray, CircleShape)
                    .clickable { onSelectColor(color) })
            }
        }
    }
}

@Composable
fun InstructionsMenu(onBack: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        BackButton(onBack)
        Text("Setup Guide & Features", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        InstructionStep(1, "Essential Permissions", "Enable 'Display over other apps' to show the price next to the clock and 'Notifications' for price alerts and popups.")
        InstructionStep(2, "Real-Time Optimization", "Go to Settings > Apps > BTCLive > Battery and select 'Unrestricted'. This is critical for reliable 5-second updates.")
        InstructionStep(3, "Lock Screen Display", "Set lock screen notifications to 'Show all' to see the live BTC price while your device is locked.")
        InstructionStep(4, "Price Alerts", "Set target prices to receive high-priority system popups. You can view hit history and email logs to yourself.")
        InstructionStep(5, "Cloud Persistence", "The app remembers all choices across reboots. Use 'Backup & Cloud' to export settings to Google Drive or OneDrive.")
        InstructionStep(6, "Appearance", "Pick from 52 sorted colors or use 'Dynamic Contrast' for automatic readability optimization.")
    }
}

@Composable
fun InstructionStep(number: Int, title: String, description: String) {
    Row(modifier = Modifier.padding(vertical = 12.dp)) {
        Box(modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AboutMenu(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth()) { BackButton(onBack) }
        Text("About", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
        Text(text = "Created by Hermes Lux.", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/HermesLux")); context.startActivity(intent) }) { Text("Follow on X @HermesLux") }
        Spacer(Modifier.height(32.dp))
        Text("Version 1.5.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun AlertItem(alert: JSONObject, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Price ${if (alert.getString("type") == "ABOVE") "Above" else "Below"}", style = MaterialTheme.typography.labelMedium)
                Text("$${alert.getDouble("target")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}

@Composable
fun HistoryItem(history: JSONObject) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hit Target: $${history.getDouble("target")}", fontWeight = FontWeight.Bold)
                Text(history.getString("timestamp"), style = MaterialTheme.typography.labelSmall)
            }
            Text("Actual Price: ${history.getString("hitPrice")}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MenuItem(title: String, icon: ImageVector, subtitle: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SelectionItem(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp).background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        if (selected) Box(modifier = Modifier.padding(end = 16.dp).size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun BackButton(onBack: () -> Unit) {
    Text("< Back", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onBack).padding(bottom = 16.dp), style = MaterialTheme.typography.labelLarge)
}

@Composable
fun PermissionSection(hasNotif: Boolean, hasOverlay: Boolean, onGrantNotification: () -> Unit, onGrantOverlay: () -> Unit) {
    Column {
        Text("Setup Required", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        if (!hasNotif && Build.VERSION.SDK_INT >= 33) {
            Button(onClick = onGrantNotification, modifier = Modifier.fillMaxWidth()) { Text("Grant Notification Permission") }
            Spacer(Modifier.height(8.dp))
        }
        if (!hasOverlay) {
            Button(onClick = onGrantOverlay, modifier = Modifier.fillMaxWidth()) { Text("Grant Overlay Permission") }
        }
    }
}
