package com.anycheck.app.detection

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets

/**
 * Detection techniques inspired by byxiaorun/Ruru (ApplistDetector).
 *
 * Covers checks unique to Ruru that are not already present in other AnyCheck detectors:
 *  1. Dual / Work-profile environment detection
 *  2. XPrivacyLua data directory
 *  3. Xposed Edge data directory
 *  4. Riru Clipboard data directory
 *  5. Privacy Space (cn.geektang.privacyspace) data directory
 *  6. Hide My Applist old-version data directory
 *  7. PM cross-method anomaly  (shell `pm list packages` vs. API)
 *  8. Xposed module metadata scan (xposedminversion / xposeddescription)
 *  9. LSPatch via appComponentFactory (API 28+)
 * 10. Account list anomaly
 * 11. VPN connection (tun0 / ppp0 / http.proxyHost)
 * 12. Accessibility services scan
 * 13. Package-manager API discrepancy (getPackageUid / getInstallSourceInfo / getLaunchIntentForPackage)
 */
class RuruInspiredDetector(private val context: Context) {

    fun runAllChecks(): List<DetectionResult> = listOf(
        checkDualOrWorkProfile(),
        checkXPrivacyLuaFile(),
        checkXposedEdgeFile(),
        checkRiruClipboardFile(),
        checkPrivacySpaceFile(),
        checkHmaOldVersionFile(),
        checkPmCommandVsApi(),
        checkXposedModuleMetadata(),
        checkLSPatchAppComponentFactory(),
        checkAccountListAnomaly(),
        checkVpnConnection(),
        checkAccessibilityServices(),
        checkPmApiDiscrepancy()
    )

    // -------------------------------------------------------------------------
    // 1. Dual / Work-profile detection
    // Ruru: filesDir starts with /data/user but NOT /data/user/0
    // -------------------------------------------------------------------------
    private fun checkDualOrWorkProfile(): DetectionResult {
        val filesDir = context.filesDir.path
        val isDual = filesDir.startsWith("/data/user") && !filesDir.startsWith("/data/user/0")
        return if (isDual) {
            DetectionResult(
                id = "ruru_dual_work_profile",
                name = "Dual / Work Profile Detected",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "App is running in a clone/dual-space or work profile.",
                detailedReason = "The app's filesDir is '$filesDir', which starts with /data/user " +
                    "but not /data/user/0. This indicates the app is running in a secondary " +
                    "user, device-clone space, or Android work profile rather than the primary user space.",
                solution = "This is expected in dual-space or work-profile scenarios. " +
                    "Verify this is the intended execution environment.",
                technicalDetail = "filesDir=$filesDir"
            )
        } else {
            DetectionResult(
                id = "ruru_dual_work_profile",
                name = "Dual / Work Profile",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "Running in the primary user space (no dual/work profile detected).",
                detailedReason = "filesDir='$filesDir' starts with /data/user/0, indicating the primary user.",
                solution = "No action required.",
                technicalDetail = "filesDir=$filesDir"
            )
        }
    }

    // -------------------------------------------------------------------------
    // 2. XPrivacyLua data directory
    // Ruru: detectFile("/data/system/xlua")
    // -------------------------------------------------------------------------
    private fun checkXPrivacyLuaFile(): DetectionResult {
        val path = "/data/system/xlua"
        val exists = File(path).exists()
        return if (exists) {
            DetectionResult(
                id = "ruru_xprivacylua_file",
                name = "XPrivacyLua Data Directory Detected",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "XPrivacyLua data directory found at $path.",
                detailedReason = "XPrivacyLua is an Xposed module that fakes or restricts " +
                    "privacy-sensitive data returned to apps. Its presence at $path strongly " +
                    "indicates an active Xposed/LSPosed environment with privacy hooking.",
                solution = "Disable or remove the XPrivacyLua module via LSPosed Manager.",
                technicalDetail = "Path exists: $path"
            )
        } else {
            DetectionResult(
                id = "ruru_xprivacylua_file",
                name = "XPrivacyLua Data Directory",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "XPrivacyLua data directory not found.",
                detailedReason = "No XPrivacyLua data directory detected at $path.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // 3. Xposed Edge data directory
    // Ruru: detectFile("/data/system/xedge")
    // -------------------------------------------------------------------------
    private fun checkXposedEdgeFile(): DetectionResult {
        val path = "/data/system/xedge"
        val exists = File(path).exists()
        return if (exists) {
            DetectionResult(
                id = "ruru_xposed_edge_file",
                name = "Xposed Edge Data Directory Detected",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "Xposed Edge data directory found at $path.",
                detailedReason = "Xposed Edge is an Xposed module for advanced edge-gesture " +
                    "customization. Its data directory at $path indicates an active Xposed framework.",
                solution = "Disable the Xposed Edge module if not intentionally installed.",
                technicalDetail = "Path exists: $path"
            )
        } else {
            DetectionResult(
                id = "ruru_xposed_edge_file",
                name = "Xposed Edge Data Directory",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "Xposed Edge data directory not found.",
                detailedReason = "No Xposed Edge data directory detected at $path.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // 4. Riru Clipboard data directory
    // Ruru: detectFile("/data/misc/clipboard")
    // -------------------------------------------------------------------------
    private fun checkRiruClipboardFile(): DetectionResult {
        val path = "/data/misc/clipboard"
        val exists = File(path).exists()
        return if (exists) {
            DetectionResult(
                id = "ruru_riru_clipboard_file",
                name = "Riru Clipboard Directory Detected",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "Riru Clipboard module data directory found at $path.",
                detailedReason = "The Riru Clipboard Zygisk module leaves its data at $path. " +
                    "This directory indicates Riru or a related Zygisk module is (or was) installed.",
                solution = "Remove or disable the Riru Clipboard module if not intentionally installed.",
                technicalDetail = "Path exists: $path"
            )
        } else {
            DetectionResult(
                id = "ruru_riru_clipboard_file",
                name = "Riru Clipboard Directory",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "Riru Clipboard data directory not found.",
                detailedReason = "No Riru Clipboard directory found at $path.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // 5. Privacy Space data directory
    // Ruru: detectFile("/data/system/cn.geektang.privacyspace")
    // -------------------------------------------------------------------------
    private fun checkPrivacySpaceFile(): DetectionResult {
        val path = "/data/system/cn.geektang.privacyspace"
        val exists = File(path).exists()
        return if (exists) {
            DetectionResult(
                id = "ruru_privacy_space_file",
                name = "Privacy Space Data Directory Detected",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Privacy Space (cn.geektang.privacyspace) data directory found.",
                detailedReason = "Privacy Space is an Xposed module that hides installed packages " +
                    "by intercepting PackageManager APIs. Its data directory at $path confirms " +
                    "it is installed and likely active.",
                solution = "Disable or remove the Privacy Space module via LSPosed Manager.",
                technicalDetail = "Path exists: $path"
            )
        } else {
            DetectionResult(
                id = "ruru_privacy_space_file",
                name = "Privacy Space Data Directory",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Privacy Space data directory not found.",
                detailedReason = "No Privacy Space directory found at $path.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // 6. HMA old-version data directory
    // Ruru: detectFile("/data/misc/hide_my_applist")
    // -------------------------------------------------------------------------
    private fun checkHmaOldVersionFile(): DetectionResult {
        val path = "/data/misc/hide_my_applist"
        val exists = File(path).exists()
        return if (exists) {
            DetectionResult(
                id = "ruru_hma_old_file",
                name = "HMA (Old Version) Data Directory Detected",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Hide My Applist old-version data directory found at $path.",
                detailedReason = "Older versions of the Hide My Applist Xposed module stored " +
                    "configuration data in $path. The presence of this directory strongly " +
                    "suggests HMA was or is installed.",
                solution = "Uninstall Hide My Applist and clean up residual data.",
                technicalDetail = "Path exists: $path"
            )
        } else {
            DetectionResult(
                id = "ruru_hma_old_file",
                name = "HMA (Old Version) Data Directory",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "HMA old-version data directory not found.",
                detailedReason = "No old-version HMA directory found at $path.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // 7. PM cross-method anomaly
    // Ruru: compares `pm list packages` shell output against getInstalledPackages()
    // A discrepancy means something is intercepting PackageManager calls.
    // -------------------------------------------------------------------------
    private val suspiciousPackages = listOf(
        "com.topjohnwu.magisk",
        "io.github.vvb2060.magisk",
        "io.github.vvb2060.magisk.lite",
        "de.robv.android.xposed.installer",
        "org.meowcat.edxposed.manager",
        "org.lsposed.manager",
        "com.tsng.hidemyapplist",
        "cn.geektang.privacyspace",
        "io.github.lsposed.manager",
        "com.lsposed.manager"
    )

    private fun getPackagesViaShell(): Set<String>? {
        return try {
            val process = Runtime.getRuntime().exec("pm list packages")
            val list = mutableSetOf<String>()
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { br ->
                var line = br.readLine()
                while (line != null) {
                    line = line.trim()
                    if (line.length > 8 && line.substring(0, 8).equals("package:", ignoreCase = true)) {
                        val pkg = line.substring(8).trim()
                        if (pkg.isNotEmpty()) list.add(pkg)
                    }
                    line = br.readLine()
                }
            }
            process.destroy()
            if (list.isEmpty()) null else list
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun checkPmCommandVsApi(): DetectionResult {
        val shellPackages = getPackagesViaShell()
        if (shellPackages == null) {
            return DetectionResult(
                id = "ruru_pm_cross_method",
                name = "PM Cross-Method Anomaly",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Could not run `pm list packages` shell command.",
                detailedReason = "The shell command `pm list packages` was unavailable or returned empty results. " +
                    "Cannot compare results against the PackageManager API.",
                solution = "Ensure the app has permission to execute shell commands.",
                technicalDetail = "pm list packages returned null or empty"
            )
        }

        val apiPackages = mutableSetOf<String>()
        try {
            context.packageManager.getInstalledPackages(0).forEach { apiPackages.add(it.packageName) }
            context.packageManager.getInstalledApplications(0).forEach { apiPackages.add(it.packageName) }
        } catch (_: Exception) {}

        // Find packages visible to shell but NOT to the API — these are being hidden
        val hiddenFromApi = suspiciousPackages.filter { pkg ->
            shellPackages.contains(pkg) && !apiPackages.contains(pkg)
        }

        // Also flag suspicious packages visible via API
        val foundViaApi = suspiciousPackages.filter { apiPackages.contains(it) }

        val discrepancies = hiddenFromApi
        return if (discrepancies.isNotEmpty()) {
            DetectionResult(
                id = "ruru_pm_cross_method",
                name = "PM Cross-Method Anomaly Detected",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "PackageManager API is hiding packages visible via shell.",
                detailedReason = "The following packages appear in `pm list packages` shell output " +
                    "but are invisible to getInstalledPackages() API, indicating a " +
                    "PackageManager hook (e.g. HideMyApplist) is filtering API results. " +
                    "Hidden packages: ${discrepancies.joinToString(", ")}.",
                solution = "Disable any Xposed/LSPosed module that hooks PackageManager (e.g. HideMyApplist, Privacy Space).",
                technicalDetail = "Hidden from API: ${discrepancies.joinToString("; ")}; " +
                    "Found via API: ${foundViaApi.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "ruru_pm_cross_method",
                name = "PM Cross-Method Anomaly",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "No discrepancy found between shell pm and PackageManager API.",
                detailedReason = "Shell `pm list packages` and getInstalledPackages() API return " +
                    "consistent results for the checked package list.",
                solution = "No action required.",
                technicalDetail = "Shell packages checked: ${suspiciousPackages.size}; " +
                    "API packages total: ${apiPackages.size}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // 8. Xposed module metadata scan
    // Ruru: looks for xposedminversion / xposeddescription in app metadata
    // -------------------------------------------------------------------------
    @SuppressLint("QueryPermissionsNeeded")
    private fun checkXposedModuleMetadata(): DetectionResult {
        val xposedMetaKeys = listOf("xposedminversion", "xposeddescription")
        val foundModules = mutableListOf<String>()

        try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in apps) {
                val meta = app.metaData ?: continue
                if (xposedMetaKeys.any { meta.containsKey(it) }) {
                    val label = runCatching { pm.getApplicationLabel(app).toString() }
                        .getOrElse { app.packageName }
                    foundModules.add("$label (${app.packageName})")
                }
            }
        } catch (_: Exception) {}

        return if (foundModules.isNotEmpty()) {
            DetectionResult(
                id = "ruru_xposed_module_metadata",
                name = "Xposed Modules Detected via Metadata",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Apps with Xposed module metadata found.",
                detailedReason = "The following installed apps declare 'xposedminversion' or " +
                    "'xposeddescription' metadata keys, identifying them as Xposed modules: " +
                    foundModules.joinToString(", ") + ".",
                solution = "Disable or uninstall identified Xposed modules via LSPosed Manager.",
                technicalDetail = "Modules: ${foundModules.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "ruru_xposed_module_metadata",
                name = "Xposed Module Metadata Scan",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "No apps with Xposed module metadata found.",
                detailedReason = "No installed app declares 'xposedminversion' or 'xposeddescription' metadata.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // 9. LSPatch via appComponentFactory (API 28+)
    // Ruru: checks appComponentFactory attribute for "lsposed" string
    // -------------------------------------------------------------------------
    @SuppressLint("QueryPermissionsNeeded")
    private fun checkLSPatchAppComponentFactory(): DetectionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return DetectionResult(
                id = "ruru_lspatch_component_factory",
                name = "LSPatch via AppComponentFactory",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Check requires API 28+; skipped on this device.",
                detailedReason = "The appComponentFactory attribute is only available on Android 9 (API 28)+.",
                solution = "No action required."
            )
        }

        val foundApps = mutableListOf<String>()
        try {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            val activities = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            for (resolveInfo in activities) {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val factory = appInfo.appComponentFactory ?: continue
                if (factory.contains("lsposed", ignoreCase = true) ||
                    factory.contains("lspatch", ignoreCase = true)
                ) {
                    val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                        .getOrElse { appInfo.packageName }
                    foundApps.add("$label (factory=$factory)")
                }
            }
        } catch (_: Exception) {}

        return if (foundApps.isNotEmpty()) {
            DetectionResult(
                id = "ruru_lspatch_component_factory",
                name = "LSPatch via AppComponentFactory Detected",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Apps patched with LSPatch detected via appComponentFactory.",
                detailedReason = "The following apps have an appComponentFactory attribute containing " +
                    "'lsposed' or 'lspatch', indicating they were patched using LSPatch (an " +
                    "Xposed implementation that embeds the framework into APKs directly): " +
                    foundApps.joinToString(", ") + ".",
                solution = "Replace LSPatch-embedded APKs with original versions from official sources.",
                technicalDetail = "LSPatch apps: ${foundApps.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "ruru_lspatch_component_factory",
                name = "LSPatch via AppComponentFactory",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "No LSPatch-patched apps detected via appComponentFactory.",
                detailedReason = "No app's appComponentFactory attribute contains 'lsposed' or 'lspatch'.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // 10. Account list anomaly
    // Ruru: checks AccountManager for any accounts (suspicious presence)
    // -------------------------------------------------------------------------
    private fun checkAccountListAnomaly(): DetectionResult {
        val accountList = mutableListOf<String>()
        try {
            val accounts = AccountManager.get(context).accounts
            for (account in accounts) {
                accountList.add("${account.type}: ${account.name}")
            }
        } catch (_: Exception) {}

        return if (accountList.isNotEmpty()) {
            DetectionResult(
                id = "ruru_account_list",
                name = "Account List Anomaly",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.INFO,
                description = "Device accounts found via AccountManager.",
                detailedReason = "AccountManager reports ${accountList.size} account(s) on this device. " +
                    "Some privacy-invasive apps or frameworks register fake accounts. " +
                    "Accounts: ${accountList.joinToString(", ")}.",
                solution = "Review listed accounts and remove any suspicious ones via device Settings → Accounts.",
                technicalDetail = "Accounts: ${accountList.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "ruru_account_list",
                name = "Account List Anomaly",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.INFO,
                description = "No device accounts found in AccountManager.",
                detailedReason = "AccountManager returned no accounts.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // 11. VPN connection detection
    // Ruru: checks tun0/ppp0 network interface or http.proxyHost property
    // -------------------------------------------------------------------------
    private fun checkVpnConnection(): DetectionResult {
        var vpnActive = false
        val indicators = mutableListOf<String>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (iface in interfaces) {
                if (iface.isUp && iface.interfaceAddresses.isNotEmpty() &&
                    (iface.name == "tun0" || iface.name == "ppp0")
                ) {
                    vpnActive = true
                    indicators.add("Network interface: ${iface.name}")
                }
            }
        } catch (_: Exception) {}

        try {
            @Suppress("DEPRECATION")
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            @Suppress("DEPRECATION")
            val vpnInfo = connMgr?.getNetworkInfo(17) // TYPE_VPN = 17
            if (vpnInfo?.isConnectedOrConnecting == true) {
                vpnActive = true
                indicators.add("ConnectivityManager TYPE_VPN connected")
            }
        } catch (_: Exception) {}

        try {
            val proxyHost = System.getProperty("http.proxyHost")
            val proxyPort = System.getProperty("http.proxyPort")?.toIntOrNull() ?: -1
            if (!proxyHost.isNullOrEmpty() && proxyPort != -1) {
                vpnActive = true
                indicators.add("http.proxyHost=$proxyHost:$proxyPort")
            }
        } catch (_: Exception) {}

        return if (vpnActive) {
            DetectionResult(
                id = "ruru_vpn_connection",
                name = "VPN Connection Detected",
                category = DetectionCategory.NETWORK,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "An active VPN or proxy connection was detected.",
                detailedReason = "A VPN or proxy connection is active on this device. " +
                    "VPNs can be used to intercept network traffic or route it through " +
                    "third-party servers. Indicators: ${indicators.joinToString(", ")}.",
                solution = "Disconnect any VPN or proxy if it is not intentionally configured.",
                technicalDetail = "VPN indicators: ${indicators.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "ruru_vpn_connection",
                name = "VPN Connection",
                category = DetectionCategory.NETWORK,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "No VPN or proxy connection detected.",
                detailedReason = "No VPN network interfaces (tun0/ppp0), TYPE_VPN connectivity, " +
                    "or http.proxyHost system property were found.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // 12. Accessibility services scan
    // Ruru: collects enabled accessibility service names + checks isEnabled
    // -------------------------------------------------------------------------
    private fun checkAccessibilityServices(): DetectionResult {
        val serviceNames = mutableListOf<String>()
        var accessibilityEnabled = false

        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null) {
                accessibilityEnabled = am.isEnabled
                val services = am.getEnabledAccessibilityServiceList(
                    android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                ) ?: emptyList()
                for (svc in services) {
                    val label = runCatching {
                        context.packageManager.getApplicationLabel(
                            svc.resolveInfo.serviceInfo.applicationInfo
                        ).toString()
                    }.getOrElse { svc.resolveInfo.serviceInfo.packageName }
                    serviceNames.add(label)
                }
            }
        } catch (_: Exception) {}

        // Also query Settings.Secure for completeness
        try {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (!settingValue.isNullOrEmpty()) {
                val fromSettings = settingValue.split(':')
                    .filter { it.isNotEmpty() && !serviceNames.contains(it) }
                serviceNames.addAll(fromSettings)
            }
            val enabledFlag = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            )
            if (enabledFlag != 0) accessibilityEnabled = true
        } catch (_: Exception) {}

        return if (accessibilityEnabled || serviceNames.isNotEmpty()) {
            DetectionResult(
                id = "ruru_accessibility_services",
                name = "Accessibility Services Active",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "One or more accessibility services are currently enabled.",
                detailedReason = "Accessibility services have broad system access and can read screen " +
                    "contents, simulate taps, and intercept input events. This may pose a " +
                    "security concern if unknown services are active. " +
                    "AccessibilityManager.isEnabled=$accessibilityEnabled, " +
                    "Services: ${if (serviceNames.isEmpty()) "(none listed)" else serviceNames.joinToString(", ")}.",
                solution = "Review enabled accessibility services in Settings → Accessibility and " +
                    "disable any you do not recognize.",
                technicalDetail = "isEnabled=$accessibilityEnabled; services=${serviceNames.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "ruru_accessibility_services",
                name = "Accessibility Services",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "No accessibility services are currently active.",
                detailedReason = "AccessibilityManager.isEnabled=false and no enabled accessibility services found.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // 13. Package-manager API discrepancy
    // Ruru (PMSundryAPIs): tests getPackageUid / getInstallSourceInfo /
    //                       getLaunchIntentForPackage for known packages
    //                       that should NOT be visible to a normal app if HMA
    //                       is filtering correctly — but a discrepancy between
    //                       these APIs reveals inconsistent filtering.
    // -------------------------------------------------------------------------
    private fun getPackageUidSafe(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return try {
                context.packageManager.getPackageUid(packageName, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        return false
    }

    private fun getInstallSourceInfoSafe(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                context.packageManager.getInstallSourceInfo(packageName)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        return false
    }

    private fun getLaunchIntentSafe(packageName: String): Boolean {
        return context.packageManager.getLaunchIntentForPackage(packageName) != null
    }

    private fun checkPmApiDiscrepancy(): DetectionResult {
        // Build a quick baseline: which suspicious packages are visible via getInstalledPackages
        val apiVisible = suspiciousPackages.filter { pkg ->
            try {
                context.packageManager.getApplicationInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }.toSet()

        // Now test sundry APIs for the same packages
        val discrepancies = mutableListOf<String>()
        for (pkg in suspiciousPackages) {
            val viaUid = getPackageUidSafe(pkg)
            val viaInstallSource = getInstallSourceInfoSafe(pkg)
            val viaLaunchIntent = getLaunchIntentSafe(pkg)
            val visibleViaSundry = viaUid || viaInstallSource || viaLaunchIntent
            val visibleViaBaseline = apiVisible.contains(pkg)

            if (visibleViaSundry != visibleViaBaseline) {
                discrepancies.add(
                    "$pkg (baseline=$visibleViaBaseline uid=$viaUid installSrc=$viaInstallSource launch=$viaLaunchIntent)"
                )
            }
        }

        return if (discrepancies.isNotEmpty()) {
            DetectionResult(
                id = "ruru_pm_api_discrepancy",
                name = "Package Manager API Discrepancy Detected",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "PackageManager APIs return inconsistent results for the same packages.",
                detailedReason = "Discrepancies found between getApplicationInfo and sundry PM APIs " +
                    "(getPackageUid / getInstallSourceInfo / getLaunchIntentForPackage). " +
                    "This is the hallmark signature of an incomplete PackageManager hook " +
                    "(e.g. HideMyApplist): it intercepts some APIs but misses others. " +
                    "Discrepancies: ${discrepancies.joinToString(", ")}.",
                solution = "Disable any Xposed/LSPosed modules that hook PackageManager.",
                technicalDetail = "Discrepancies: ${discrepancies.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "ruru_pm_api_discrepancy",
                name = "Package Manager API Discrepancy",
                category = DetectionCategory.ENVIRONMENT,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "PackageManager APIs return consistent results.",
                detailedReason = "getApplicationInfo and sundry PM APIs (getPackageUid, " +
                    "getInstallSourceInfo, getLaunchIntentForPackage) agree on visibility " +
                    "for all checked packages.",
                solution = "No action required."
            )
        }
    }
}
