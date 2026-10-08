package com.hermeslux.btclive

import java.util.Locale

data class AutostartTarget(val packageName: String, val className: String)

data class OemGuidance(
    val brand: String,
    val batterySteps: String,
    val autostartNote: String?,
    val autostartTargets: List<AutostartTarget>,
)

private val RIGHT_CLOCK = setOf(
    "samsung",
    "xiaomi",
    "redmi",
    "poco",
    "huawei",
    "honor",
    "vivo",
    "iqoo",
    "meizu",
    "tecno",
    "infinix",
    "itel",
)

fun normalizeBrand(value: String): String {
    return value.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
}

fun prefersRightStatusClock(manufacturer: String, brand: String = ""): Boolean {
    val keys = listOf(normalizeBrand(manufacturer), normalizeBrand(brand)).filter { it.isNotEmpty() }
    return keys.any { key -> RIGHT_CLOCK.any { token -> key == token || key.contains(token) } }
}

fun oemGuidance(manufacturer: String, brand: String = ""): OemGuidance {
    val key = listOf(normalizeBrand(manufacturer), normalizeBrand(brand)).firstOrNull { it.isNotEmpty() } ?: ""
    return when {
        key.contains("samsung") -> OemGuidance(
            brand = "Samsung",
            batterySteps = "Settings > Battery and device care > Battery > Background usage limits. Set BTCLive to Unrestricted and remove it from Sleeping apps.",
            autostartNote = null,
            autostartTargets = emptyList(),
        )
        key.contains("xiaomi") || key.contains("redmi") || key.contains("poco") -> OemGuidance(
            brand = "Xiaomi",
            batterySteps = "Settings > Apps > BTCLive > Battery saver > No restrictions.",
            autostartNote = "Also turn on Autostart for BTCLive. HyperOS and MIUI stop background apps that are not in this list.",
            autostartTargets = listOf(
                AutostartTarget("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ),
        )
        key.contains("huawei") || key.contains("honor") -> OemGuidance(
            brand = if (key.contains("honor")) "Honor" else "Huawei",
            batterySteps = "Settings > Battery > App launch > BTCLive > Manage manually, then allow auto-launch, secondary launch, and run in background.",
            autostartNote = "Open App launch if the button does not land on the right screen.",
            autostartTargets = listOf(
                AutostartTarget("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                AutostartTarget("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ),
        )
        key.contains("oppo") || key.contains("realme") -> OemGuidance(
            brand = if (key.contains("realme")) "Realme" else "Oppo",
            batterySteps = "Settings > Battery > BTCLive > Allow background activity.",
            autostartNote = "Turn on Auto-launch for BTCLive in the startup manager.",
            autostartTargets = listOf(
                AutostartTarget("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                AutostartTarget("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                AutostartTarget("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ),
        )
        key.contains("vivo") || key.contains("iqoo") -> OemGuidance(
            brand = if (key.contains("iqoo")) "iQOO" else "vivo",
            batterySteps = "Settings > Battery > Background power consumption management > BTCLive > Don't restrict.",
            autostartNote = "Allow BTCLive to auto-start.",
            autostartTargets = listOf(
                AutostartTarget("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                AutostartTarget("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                AutostartTarget("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            ),
        )
        key.contains("oneplus") -> OemGuidance(
            brand = "OnePlus",
            batterySteps = "Settings > Battery > BTCLive > Don't optimize. Advanced optimization should be off for this app.",
            autostartNote = "On older OxygenOS builds, also allow auto-launch.",
            autostartTargets = listOf(
                AutostartTarget("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            ),
        )
        key.contains("tecno") || key.contains("infinix") || key.contains("itel") -> OemGuidance(
            brand = "this phone",
            batterySteps = "Settings > Battery > BTCLive > No restrictions.",
            autostartNote = "Turn on Autostart for BTCLive in Phone Master or the app settings.",
            autostartTargets = emptyList(),
        )
        else -> OemGuidance(
            brand = "this phone",
            batterySteps = "Settings > Apps > BTCLive > Battery > Unrestricted. That keeps 5-second updates running while the screen is on.",
            autostartNote = null,
            autostartTargets = emptyList(),
        )
    }
}

fun clockSideHint(manufacturer: String, brand: String = ""): String {
    val name = oemGuidance(manufacturer, brand).brand
    val subject = if (name == "this phone") "Most Android phones" else "$name phones"
    return if (prefersRightStatusClock(manufacturer, brand)) {
        "$subject usually put the clock on the right. Auto places the price just to its left."
    } else {
        "$subject usually put the clock on the left. Auto places the price just to its right."
    }
}
