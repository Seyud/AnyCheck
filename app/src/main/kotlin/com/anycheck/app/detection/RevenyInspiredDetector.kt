package com.anycheck.app.detection

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

/**
 * Detection techniques inspired by reveny/Android-Native-Root-Detector.
 * Covers: custom ROM/kernel fingerprinting, resetprop, Hide My Applist,
 * mount inconsistency, addon.d persistence, system-app absence, vendor
 * sepolicy patching, and framework patching indicators.
 */
class RevenyInspiredDetector(private val context: Context) {

    fun runAllChecks(): List<DetectionResult> = listOf(
        checkLineageOSOrCustomROM(),
        checkCustomKernel(),
        checkResetprop(),
        checkDebugFingerprint(),
        checkHideMyApplist(),
        checkMountInconsistency(),
        checkAddonDOrInstallRecovery(),
        checkSystemAppsAbsence(),
        checkVendorSepolicyLineage(),
        checkFrameworkPatch()
    )

    // -------------------------------------------------------------------------
    // Check 1: LineageOS / Custom ROM
    // Mirrors reveny "Detected LineageOS" and "Detected Custom ROM"
    // -------------------------------------------------------------------------
    private fun checkLineageOSOrCustomROM(): DetectionResult {
        val lineageProps = listOf(
            "ro.lineage.version",
            "ro.lineageos.version",
            "ro.cm.version",
            "ro.cyanogenmod.version",
            "ro.pixel.version"
        )
        val detectedProps = lineageProps.filter { readProp(it) != null }

        // Also inspect build fingerprint and display for common custom ROM strings
        val fingerprint = Build.FINGERPRINT ?: ""
        val display = Build.DISPLAY ?: ""
        val brand = Build.BRAND ?: ""
        val customRomKeywords = listOf("lineage", "cyanogenmod", "resurrection", "paranoid",
            "calyx", "graphene", "e/os", "calyxos", "havoc", "pixel_experience",
            "evolution_x", "arrow", "dot", "aosip", "carbon", "slim", "aicp",
            "omni", "validus", "bliss", "rebellion", "nusantara", "ion", "rising")
        val matchedKeywords = customRomKeywords.filter { kw ->
            fingerprint.contains(kw, ignoreCase = true) ||
                display.contains(kw, ignoreCase = true) ||
                brand.contains(kw, ignoreCase = true)
        }

        val vendorSepolicyFile = File("/vendor/etc/selinux/vendor_sepolicy.cil")
        val hasLineageSepolicy = vendorSepolicyFile.exists() && runCatching {
            vendorSepolicyFile.readText(Charsets.UTF_8)
        }.getOrNull()?.contains("lineage", ignoreCase = true) == true

        val indicators = mutableListOf<String>()
        if (detectedProps.isNotEmpty()) indicators.add("Props: ${detectedProps.joinToString(", ")}")
        if (matchedKeywords.isNotEmpty()) {
            indicators.add("Fingerprint/display keywords: ${matchedKeywords.joinToString(", ")}")
        }
        if (hasLineageSepolicy) indicators.add("vendor_sepolicy.cil contains 'lineage'")

        return if (indicators.isNotEmpty()) {
            DetectionResult(
                id = "reveny_custom_rom",
                name = "Custom ROM Detected",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "A custom ROM (e.g. LineageOS) was detected on this device.",
                detailedReason = "Custom ROM indicators found: ${indicators.joinToString("; ")}. " +
                    "Custom ROMs may have relaxed security policies and often ship with root access enabled.",
                solution = "Flash the official OEM firmware via fastboot to restore a stock environment.",
                technicalDetail = indicators.joinToString("; ")
            )
        } else {
            DetectionResult(
                id = "reveny_custom_rom",
                name = "Custom ROM",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "No custom ROM detected.",
                detailedReason = "No known custom ROM properties or fingerprint keywords found.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Check 2: Custom / non-stock kernel
    // Mirrors reveny "Detected Custom Kernel"
    // -------------------------------------------------------------------------
    private fun checkCustomKernel(): DetectionResult {
        val kernelVersion = readProp("os.version")
            ?: runCatching { System.getProperty("os.version") }.getOrNull()
            ?: ""
        // /proc/version gives the full kernel banner
        val procVersion = runCatching {
            File("/proc/version").readText(Charsets.UTF_8).trim()
        }.getOrNull() ?: ""

        // Custom kernel indicators: common kernel project names / unofficial strings
        val customKernelKeywords = listOf(
            "lineageos", "lineage", "kali", "nethunter", "elementary",
            "sultan", "arter97", "flar2", "blu_spark", "eas", "optimus",
            "ElementalX", "liqx", "savoca", "neffos", "sunxi-kernel",
            "franco", "zen", "CAF"
        )
        val matched = customKernelKeywords.filter { kw ->
            procVersion.contains(kw, ignoreCase = true)
        }

        // Also check if kernel was compiled with a non-OEM email/hostname
        val customCompilerPattern = Regex("""@[a-z0-9\-]+\.(local|home|pc|laptop|desktop|server)""",
            RegexOption.IGNORE_CASE)
        val hasPersonalBuild = customCompilerPattern.containsMatchIn(procVersion)

        val indicators = mutableListOf<String>()
        if (matched.isNotEmpty()) indicators.add("Keywords: ${matched.joinToString(", ")}")
        if (hasPersonalBuild) indicators.add("Personal build hostname in kernel banner")

        return if (indicators.isNotEmpty()) {
            DetectionResult(
                id = "reveny_custom_kernel",
                name = "Custom Kernel Detected",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "A non-stock (custom) kernel was detected.",
                detailedReason = "Custom kernel indicators in /proc/version: ${indicators.joinToString("; ")}. " +
                    "Custom kernels may have security patches removed or extra capabilities enabled.",
                solution = "Flash the OEM stock kernel/boot image to restore an official kernel.",
                technicalDetail = "Kernel banner: ${procVersion.take(200)}"
            )
        } else {
            DetectionResult(
                id = "reveny_custom_kernel",
                name = "Custom Kernel",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "No custom kernel indicators found.",
                detailedReason = "The kernel banner does not contain known custom-kernel strings.",
                solution = "No action required.",
                technicalDetail = "Kernel banner: ${procVersion.take(200)}"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Check 3: Resetprop binary
    // Mirrors reveny "Detected Resetprop"
    // resetprop is a Magisk-bundled tool for faking system properties.
    // -------------------------------------------------------------------------
    private fun checkResetprop(): DetectionResult {
        val paths = listOf(
            "/data/adb/magisk/resetprop",
            "/data/adb/modules/.core/resetprop",
            "/sbin/resetprop",
            "/system/bin/resetprop",
            "/system/xbin/resetprop"
        )
        val found = paths.filter { File(it).exists() }
        return if (found.isNotEmpty()) {
            DetectionResult(
                id = "reveny_resetprop",
                name = "Resetprop Detected",
                category = DetectionCategory.MAGISK,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "The resetprop tool was found on this device.",
                detailedReason = "resetprop is a Magisk utility that can modify Android system properties " +
                    "at runtime without leaving traces in the normal property service. " +
                    "Found at: ${found.joinToString(", ")}.",
                solution = "Uninstall Magisk via the Magisk Manager app or flash a stock boot image.",
                technicalDetail = "Paths: ${found.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "reveny_resetprop",
                name = "Resetprop",
                category = DetectionCategory.MAGISK,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Resetprop tool not found.",
                detailedReason = "No resetprop binary was detected at known Magisk paths.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Check 4: Debug fingerprint / userdebug build
    // Mirrors reveny "Debug Fingerprint detected"
    // -------------------------------------------------------------------------
    private fun checkDebugFingerprint(): DetectionResult {
        val fingerprint = Build.FINGERPRINT ?: ""
        val buildType = Build.TYPE ?: ""
        val tags = Build.TAGS ?: ""

        val isUserDebug = buildType.equals("userdebug", ignoreCase = true)
        val isEng = buildType.equals("eng", ignoreCase = true)
        val fingerprintHasDebug = fingerprint.contains(":userdebug/") ||
            fingerprint.contains(":eng/")

        return if (isUserDebug || isEng || fingerprintHasDebug) {
            val detail = "buildType=$buildType, fingerprint=${fingerprint.take(80)}, tags=$tags"
            DetectionResult(
                id = "reveny_debug_fingerprint",
                name = "Debug Fingerprint Detected",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "The device is running a userdebug or eng build.",
                detailedReason = "Build type '$buildType' is a debug variant. " +
                    "Debug builds have ADB root, relaxed SELinux, and may expose sensitive " +
                    "system interfaces. Fingerprint: ${fingerprint.take(80)}.",
                solution = "Use a production ('user') build for proper security hardening.",
                technicalDetail = detail
            )
        } else {
            DetectionResult(
                id = "reveny_debug_fingerprint",
                name = "Debug Fingerprint",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "No debug fingerprint detected.",
                detailedReason = "Build type is '$buildType', which is a production build.",
                solution = "No action required.",
                technicalDetail = "buildType=$buildType"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Check 5: Hide My Applist
    // Mirrors reveny "Detected Hide My Applist"
    // -------------------------------------------------------------------------
    private fun checkHideMyApplist(): DetectionResult {
        val hmaPackages = listOf(
            "com.tsng.hidemyapplist",
            "com.tsng.hidemyapplist.debug",
            "cn.hidemyapplist"
        )
        val foundPackages = hmaPackages.filter { packageExists(it) }

        // Also check for service socket that HMA creates
        val socketPath = "/dev/unix/hidemyapplist"
        val hasSocket = File(socketPath).exists()

        // Check proc/net/unix for HMA socket name
        val hasUnixSocket = runCatching {
            File("/proc/net/unix").readLines().any { line ->
                line.contains("hidemyapplist", ignoreCase = true) ||
                    line.contains("hma", ignoreCase = true)
            }
        }.getOrNull() ?: false

        val indicators = mutableListOf<String>()
        if (foundPackages.isNotEmpty()) indicators.add("Packages: ${foundPackages.joinToString(", ")}")
        if (hasSocket) indicators.add("Socket: $socketPath")
        if (hasUnixSocket) indicators.add("Unix socket in /proc/net/unix")

        return if (indicators.isNotEmpty()) {
            DetectionResult(
                id = "reveny_hide_my_applist",
                name = "Hide My Applist Detected",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Hide My Applist framework detected.",
                detailedReason = "Hide My Applist (HMA) is an Xposed/LSPosed module that intercepts " +
                    "PackageManager calls to hide installed apps from other applications. " +
                    "Indicators: ${indicators.joinToString("; ")}.",
                solution = "Disable or uninstall the Hide My Applist module via LSPosed Manager.",
                technicalDetail = indicators.joinToString("; ")
            )
        } else {
            DetectionResult(
                id = "reveny_hide_my_applist",
                name = "Hide My Applist",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Hide My Applist not detected.",
                detailedReason = "No Hide My Applist packages or sockets were found.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Check 6: Mount inconsistency / umount-based hiding
    // Mirrors reveny "Detected Mount Inconsistency" and "Umount Detected"
    // Compares /proc/mounts with /proc/self/mountinfo to find hidden mounts.
    // -------------------------------------------------------------------------
    private fun checkMountInconsistency(): DetectionResult {
        return try {
            val mountsEntries = File("/proc/mounts").readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.split("\\s+".toRegex()).getOrElse(1) { "" } } // target/mountpoint
                .toSet()

            val mountInfoEntries = File("/proc/self/mountinfo").readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { parts ->
                    // mountinfo field 5 is the mount point
                    parts.split("\\s+".toRegex()).getOrElse(4) { "" }
                }
                .toSet()

            // Entries in mountinfo but not in mounts could indicate umount hiding
            val hiddenMounts = mountInfoEntries - mountsEntries
            // Filter to only suspicious paths (root-related targets)
            val suspiciousPaths = listOf("/system", "/vendor", "/product", "/apex",
                "/data", "/sbin", "/proc")
            val suspicious = hiddenMounts.filter { mp ->
                suspiciousPaths.any { mp.startsWith(it) }
            }

            if (suspicious.isNotEmpty()) {
                DetectionResult(
                    id = "reveny_mount_inconsistency",
                    name = "Mount Inconsistency Detected",
                    category = DetectionCategory.SYSTEM_INTEGRITY,
                    status = DetectionStatus.DETECTED,
                    riskLevel = RiskLevel.HIGH,
                    description = "Mount table inconsistency detected — possible umount-based hiding.",
                    detailedReason = "Mounts visible in /proc/self/mountinfo but absent from /proc/mounts " +
                        "suggest a process has called umount() to hide root-related bind mounts from " +
                        "simpler mount file parsers. Suspicious paths: ${suspicious.take(5).joinToString(", ")}.",
                    solution = "This is a strong indicator of Magisk or a similar framework using " +
                        "mount namespace manipulation. Uninstall the root framework.",
                    technicalDetail = "Hidden mount points: ${suspicious.joinToString("; ")}"
                )
            } else {
                DetectionResult(
                    id = "reveny_mount_inconsistency",
                    name = "Mount Inconsistency",
                    category = DetectionCategory.SYSTEM_INTEGRITY,
                    status = DetectionStatus.NOT_DETECTED,
                    riskLevel = RiskLevel.HIGH,
                    description = "No mount table inconsistency detected.",
                    detailedReason = "/proc/mounts and /proc/self/mountinfo are consistent.",
                    solution = "No action required."
                )
            }
        } catch (_: Exception) {
            DetectionResult(
                id = "reveny_mount_inconsistency",
                name = "Mount Inconsistency",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.ERROR,
                riskLevel = RiskLevel.HIGH,
                description = "Mount inconsistency check could not be completed.",
                detailedReason = "Failed to read /proc/mounts or /proc/self/mountinfo.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Check 7: Addon.d or install-recovery.sh
    // Mirrors reveny "Addon.d or install-recovery.sh exists"
    // These scripts are used by custom ROMs / Magisk to persist across OTA updates.
    // -------------------------------------------------------------------------
    private fun checkAddonDOrInstallRecovery(): DetectionResult {
        val addonDDir = File("/system/addon.d")
        val installRecovery = File("/system/etc/install-recovery.sh")
        val installRecovery2 = File("/system/bin/install-recovery.sh")

        val hasAddonD = addonDDir.exists() && (addonDDir.list()?.isNotEmpty() == true)
        val hasInstallRecovery = installRecovery.exists() || installRecovery2.exists()

        val indicators = mutableListOf<String>()
        if (hasAddonD) {
            val scripts = addonDDir.list() ?: emptyArray()
            indicators.add("addon.d directory with ${scripts.size} script(s): ${scripts.take(3).joinToString(", ")}")
        }
        if (hasInstallRecovery) indicators.add("install-recovery.sh present")

        return if (indicators.isNotEmpty()) {
            DetectionResult(
                id = "reveny_addon_d",
                name = "Addon.d / install-recovery.sh Detected",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "OTA persistence scripts found in the system partition.",
                detailedReason = "addon.d scripts and install-recovery.sh are used by custom ROMs " +
                    "and rooting frameworks to survive OTA updates by re-applying patches after " +
                    "a system update. Indicators: ${indicators.joinToString("; ")}.",
                solution = "These files indicate a modified system. Flash an official OEM ROM " +
                    "to remove them.",
                technicalDetail = indicators.joinToString("; ")
            )
        } else {
            DetectionResult(
                id = "reveny_addon_d",
                name = "Addon.d / install-recovery.sh",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "No addon.d scripts or install-recovery.sh found.",
                detailedReason = "No OTA persistence scripts were detected in the system partition.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Check 8: System apps absence
    // Mirrors reveny "No system apps found"
    // If the PackageManager returns very few system apps, an app-list hiding
    // framework (e.g. HMA) is likely intercepting the call.
    // -------------------------------------------------------------------------
    private fun checkSystemAppsAbsence(): DetectionResult {
        return try {
            val pm = context.packageManager
            val systemApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 }

            // A typical Android device has 50–200+ system apps. Fewer than 5 is extremely suspicious.
            val threshold = 5
            return if (systemApps.size < threshold) {
                DetectionResult(
                    id = "reveny_system_apps_absent",
                    name = "System Apps Hidden",
                    category = DetectionCategory.XPOSED,
                    status = DetectionStatus.DETECTED,
                    riskLevel = RiskLevel.HIGH,
                    description = "Abnormally few system apps visible — app-list hiding likely active.",
                    detailedReason = "Only ${systemApps.size} system app(s) returned by PackageManager. " +
                        "Normal Android devices expose 50+ system apps. This strongly indicates an " +
                        "app-list hiding framework (e.g. Hide My Applist) is intercepting queries.",
                    solution = "Disable the app-list hiding module via LSPosed Manager.",
                    technicalDetail = "System app count: ${systemApps.size}"
                )
            } else {
                DetectionResult(
                    id = "reveny_system_apps_absent",
                    name = "System Apps Visibility",
                    category = DetectionCategory.XPOSED,
                    status = DetectionStatus.NOT_DETECTED,
                    riskLevel = RiskLevel.HIGH,
                    description = "System apps appear normally visible.",
                    detailedReason = "${systemApps.size} system apps returned by PackageManager — within normal range.",
                    solution = "No action required.",
                    technicalDetail = "System app count: ${systemApps.size}"
                )
            }
        } catch (_: Exception) {
            DetectionResult(
                id = "reveny_system_apps_absent",
                name = "System Apps Visibility",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.ERROR,
                riskLevel = RiskLevel.HIGH,
                description = "Could not query system app list.",
                detailedReason = "PackageManager query failed.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Check 9: vendor_sepolicy.cil contains LineageOS entries
    // Mirrors reveny "vendor_sepolicy.cil contains lineage"
    // -------------------------------------------------------------------------
    private fun checkVendorSepolicyLineage(): DetectionResult {
        val sepolicyFile = File("/vendor/etc/selinux/vendor_sepolicy.cil")
        if (!sepolicyFile.exists()) {
            return DetectionResult(
                id = "reveny_vendor_sepolicy",
                name = "Vendor SEPolicy Lineage Check",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "vendor_sepolicy.cil not found or not accessible.",
                detailedReason = "The vendor SELinux policy file was not present or not readable.",
                solution = "No action required."
            )
        }
        return try {
            val content = sepolicyFile.readText(Charsets.UTF_8)
            val lineageEntries = listOf("lineage", "lineageos", "cyanogenmod")
            val found = lineageEntries.filter { content.contains(it, ignoreCase = true) }
            if (found.isNotEmpty()) {
                DetectionResult(
                    id = "reveny_vendor_sepolicy",
                    name = "LineageOS SEPolicy Detected",
                    category = DetectionCategory.SYSTEM_INTEGRITY,
                    status = DetectionStatus.DETECTED,
                    riskLevel = RiskLevel.MEDIUM,
                    description = "vendor_sepolicy.cil contains LineageOS-specific entries.",
                    detailedReason = "The vendor SELinux policy file contains custom ROM policy entries: " +
                        "${found.joinToString(", ")}. This confirms a custom ROM build.",
                    solution = "Flash an official OEM ROM to restore the original SELinux policy.",
                    technicalDetail = "Found keywords: ${found.joinToString(", ")} in vendor_sepolicy.cil"
                )
            } else {
                DetectionResult(
                    id = "reveny_vendor_sepolicy",
                    name = "Vendor SEPolicy Lineage Check",
                    category = DetectionCategory.SYSTEM_INTEGRITY,
                    status = DetectionStatus.NOT_DETECTED,
                    riskLevel = RiskLevel.MEDIUM,
                    description = "No LineageOS entries in vendor_sepolicy.cil.",
                    detailedReason = "The vendor SELinux policy does not contain known custom ROM entries.",
                    solution = "No action required."
                )
            }
        } catch (_: Exception) {
            DetectionResult(
                id = "reveny_vendor_sepolicy",
                name = "Vendor SEPolicy Lineage Check",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.ERROR,
                riskLevel = RiskLevel.MEDIUM,
                description = "Could not read vendor_sepolicy.cil.",
                detailedReason = "Permission denied or I/O error reading the SELinux policy file.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Check 10: Framework patch indicators
    // Mirrors reveny "Detected Framework Patch"
    // LSPosed and some Zygisk modules patch /system/framework/*.jar (or .odex)
    // We look for anomalous modification times compared to other framework files.
    // -------------------------------------------------------------------------
    private fun checkFrameworkPatch(): DetectionResult {
        val frameworkDir = File("/system/framework")
        if (!frameworkDir.exists()) {
            return DetectionResult(
                id = "reveny_framework_patch",
                name = "Framework Patch Check",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "/system/framework not accessible.",
                detailedReason = "The framework directory could not be read.",
                solution = "No action required."
            )
        }
        return try {
            val frameworkFiles = frameworkDir.listFiles() ?: emptyArray()
            if (frameworkFiles.isEmpty()) {
                return DetectionResult(
                    id = "reveny_framework_patch",
                    name = "Framework Patch Check",
                    category = DetectionCategory.XPOSED,
                    status = DetectionStatus.NOT_DETECTED,
                    riskLevel = RiskLevel.HIGH,
                    description = "No files found in /system/framework.",
                    detailedReason = "The framework directory appears empty.",
                    solution = "No action required."
                )
            }

            // Check for known LSPosed / Xposed framework injection files
            val suspiciousNames = listOf(
                "XposedBridge.jar",
                "xposed",
                "lspd",
                "edxp",
                "framework-patch"
            )
            val suspiciousFound = frameworkFiles
                .filter { f -> suspiciousNames.any { f.name.contains(it, ignoreCase = true) } }
                .map { it.name }

            // Check modification time anomaly: services.jar / services.odex modified
            // significantly more recently than other framework files is suspicious.
            val servicesJar = frameworkFiles.firstOrNull { it.name == "services.jar" }
            val servicesOdex = frameworkDir.walkTopDown()
                .firstOrNull { it.name == "services.odex" }
            val otherFiles = frameworkFiles.filter {
                it.name != "services.jar" && it.isFile && it.length() > 0
            }

            val anomalousModTime = if (servicesJar != null && otherFiles.isNotEmpty()) {
                val servicesTime = servicesJar.lastModified()
                val medianTime = otherFiles.map { it.lastModified() }.sorted()
                    .let { times -> times[times.size / 2] }
                // If services.jar is >30 days newer than median, flag it
                val diffDays = (servicesTime - medianTime) / (1000L * 60 * 60 * 24)
                diffDays > 30
            } else false

            val indicators = mutableListOf<String>()
            if (suspiciousFound.isNotEmpty()) indicators.add("Suspicious files: ${suspiciousFound.joinToString(", ")}")
            if (anomalousModTime) indicators.add("services.jar modification time anomaly detected")

            if (indicators.isNotEmpty()) {
                DetectionResult(
                    id = "reveny_framework_patch",
                    name = "Framework Patch Detected",
                    category = DetectionCategory.XPOSED,
                    status = DetectionStatus.DETECTED,
                    riskLevel = RiskLevel.HIGH,
                    description = "System framework may have been patched.",
                    detailedReason = "Evidence of framework modification: ${indicators.joinToString("; ")}. " +
                        "Xposed / LSPosed work by patching the Android runtime framework " +
                        "(services.jar, framework.jar) to allow hooking.",
                    solution = "Remove the Xposed framework and restore the original framework files " +
                        "by flashing a stock ROM.",
                    technicalDetail = indicators.joinToString("; ")
                )
            } else {
                DetectionResult(
                    id = "reveny_framework_patch",
                    name = "Framework Patch",
                    category = DetectionCategory.XPOSED,
                    status = DetectionStatus.NOT_DETECTED,
                    riskLevel = RiskLevel.HIGH,
                    description = "No framework patch indicators found.",
                    detailedReason = "No suspicious files or modification time anomalies in /system/framework.",
                    solution = "No action required."
                )
            }
        } catch (_: Exception) {
            DetectionResult(
                id = "reveny_framework_patch",
                name = "Framework Patch Check",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.ERROR,
                riskLevel = RiskLevel.HIGH,
                description = "Could not complete framework patch check.",
                detailedReason = "Permission denied or I/O error reading /system/framework.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun readProp(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val result = BufferedReader(InputStreamReader(process.inputStream))
                .readLine()?.trim()
            process.destroy()
            if (result.isNullOrEmpty()) null else result
        } catch (_: Exception) {
            null
        }
    }

    private fun packageExists(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
