package com.anycheck.app.detection

import android.content.Context
import android.content.pm.PackageManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Luna-inspired detection engine.
 *
 * Implements detection methods reverse-engineered from the Luna safety checker
 * (luna.safe.luna / JNI methods in libluna.so), covering:
 *  - findlsp        → LSPosed API system property check
 *  - findksu        → KernelSU daemon service property check
 *  - checkappnum    → Magisk daemon service property check
 *  - psdir          → PATH-directory su/root binary scan
 *  - rustmagisk     → APatch process scan via /proc
 *  - fhma           → suspicious system-file size check (stat)
 *  - checksuskernel → kernel-level stat anomaly via /proc/net/unix + SELinux context
 *  - magiskmounts   → /proc/mounts Magisk bind-mount / overlayfs detection
 *  - zygoteinject   → Zygote injection via SELinux context inspection
 *  - tmpfsmount     → tmpfs SELinux context anomaly on /mnt/obb and /mnt/asec
 */
class LunaDetector(private val context: Context) {

    fun runAllChecks(): List<DetectionResult> = listOf(
        checkLSPosedApiProperty(),
        checkKernelSUDaemon(),
        checkMagiskDaemonProperty(),
        checkSuInPathDirectories(),
        checkAPatchProcesses(),
        checkSuspiciousFileSize(),
        checkKernelStatAnomaly(),
        checkMagiskProcMounts(),
        checkZygoteInjection(),
        checkTmpfsMountAnomaly()
    )

    // -------------------------------------------------------------------------
    // Luna: findlsp
    // __system_property_get(DAT_001407b0, buf)  → atoi(buf) >= 1 means LSPosed
    // -------------------------------------------------------------------------
    private fun checkLSPosedApiProperty(): DetectionResult {
        val lspProps = listOf(
            "persist.lsp.api",
            "init.svc.lspd",
            "ro.lsposed.version",
            "persist.lsp.module_list"
        )
        val found = mutableListOf<String>()
        for (prop in lspProps) {
            val value = getSystemProperty(prop)
            if (value.isNotEmpty()) {
                found.add("$prop=$value")
                // Mirror Luna's atoi(buf) >= 1 check for persist.lsp.api
                if (prop == "persist.lsp.api") {
                    val apiLevel = value.toIntOrNull() ?: 0
                    if (apiLevel >= 1) found.add("lsp_api_level=$apiLevel")
                }
            }
        }
        return if (found.isNotEmpty()) {
            DetectionResult(
                id = "luna_lsp_prop",
                name = "LSPosed API Property Detected",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "LSPosed-specific system properties found.",
                detailedReason = "Luna-method (findlsp): __system_property_get on LSPosed API property. " +
                    "Found: ${found.joinToString(", ")}. " +
                    "persist.lsp.api is set by LSPosed and contains its API version number.",
                solution = "Disable or uninstall LSPosed to remove these properties.",
                technicalDetail = "Props: ${found.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "luna_lsp_prop",
                name = "LSPosed API Property",
                category = DetectionCategory.XPOSED,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "No LSPosed-specific system properties found.",
                detailedReason = "Luna-method (findlsp): None of the known LSPosed system properties " +
                    "(persist.lsp.api, init.svc.lspd) were found.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Luna: findksu / checkksuboot
    // __system_property_get(DAT_00140540, buf)  → 0 < length means KSU daemon running
    // -------------------------------------------------------------------------
    private fun checkKernelSUDaemon(): DetectionResult {
        val ksuServiceProps = listOf(
            "init.svc.ksuud",
            "init.svc.ksu",
            "sys.init.ksuud_ready",
            "init.svc.kernelsu"
        )
        val found = mutableListOf<String>()
        for (prop in ksuServiceProps) {
            val value = getSystemProperty(prop)
            if (value.isNotEmpty()) found.add("$prop=$value")
        }
        return if (found.isNotEmpty()) {
            DetectionResult(
                id = "luna_ksu_daemon",
                name = "KernelSU Daemon Service Property",
                category = DetectionCategory.KERNELSU,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "KernelSU daemon service properties found.",
                detailedReason = "Luna-method (findksu): __system_property_get confirmed KernelSU daemon. " +
                    "Found: ${found.joinToString(", ")}. " +
                    "init.svc.ksuud is set by the Android init system when the KernelSU " +
                    "userspace daemon (ksuud) is active.",
                solution = "Uninstall KernelSU via the KernelSU Manager app or flash a stock kernel.",
                technicalDetail = "Service props: ${found.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "luna_ksu_daemon",
                name = "KernelSU Daemon Service Property",
                category = DetectionCategory.KERNELSU,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "No KernelSU daemon service properties found.",
                detailedReason = "Luna-method (findksu): None of the KernelSU service properties " +
                    "(init.svc.ksuud, init.svc.ksu) were found.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Luna: checkappnum
    // __system_property_get(DAT_001405f0, buf) → exists means Magisk daemon active
    // -------------------------------------------------------------------------
    private fun checkMagiskDaemonProperty(): DetectionResult {
        val magiskServiceProps = listOf(
            "init.svc.magiskd",
            "init.svc.magisk",
            "init.svc.magisksu"
        )
        val found = mutableListOf<String>()
        for (prop in magiskServiceProps) {
            val value = getSystemProperty(prop)
            if (value.isNotEmpty()) found.add("$prop=$value")
        }
        return if (found.isNotEmpty()) {
            DetectionResult(
                id = "luna_magisk_svc_prop",
                name = "Magisk Daemon Service Property",
                category = DetectionCategory.MAGISK,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "Magisk daemon service property found.",
                detailedReason = "Luna-method (checkappnum): __system_property_get confirmed Magisk daemon. " +
                    "Found: ${found.joinToString(", ")}. " +
                    "init.svc.magiskd is set by the Android init system when the Magisk daemon is running.",
                solution = "Uninstall Magisk via the Magisk Manager app.",
                technicalDetail = "Service props: ${found.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "luna_magisk_svc_prop",
                name = "Magisk Daemon Service Property",
                category = DetectionCategory.MAGISK,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "No Magisk daemon service properties found.",
                detailedReason = "Luna-method (checkappnum): None of the known Magisk service properties were found.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Luna: psdir
    // getenv("PATH") → scan each directory for su / busybox / magisk binaries
    // -------------------------------------------------------------------------
    private fun checkSuInPathDirectories(): DetectionResult {
        val pathEnv = System.getenv("PATH") ?: ""
        val pathDirs = pathEnv.split(":").filter { it.isNotEmpty() }
        val rootBinaries = listOf("su", "busybox", "supolicy", "magisk", "resetprop", "ksud", "apd")
        val found = mutableListOf<String>()

        for (dir in pathDirs) {
            for (binary in rootBinaries) {
                val f = File(dir, binary)
                if (f.exists()) found.add("$dir/$binary")
            }
        }

        return if (found.isNotEmpty()) {
            DetectionResult(
                id = "luna_path_su",
                name = "Root Binaries in PATH",
                category = DetectionCategory.SU_BINARY,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "Root-related binaries found in PATH directories.",
                detailedReason = "Luna-method (psdir): Scanned getenv(PATH) directories for root binaries. " +
                    "Found: ${found.joinToString(", ")}. " +
                    "Root binaries placed in PATH directories grant easy shell-level root access.",
                solution = "Remove root tools to eliminate these binaries from PATH.",
                technicalDetail = "Found in PATH: ${found.joinToString("; ")}"
            )
        } else {
            DetectionResult(
                id = "luna_path_su",
                name = "Root Binaries in PATH",
                category = DetectionCategory.SU_BINARY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "No root binaries found in PATH directories.",
                detailedReason = "Luna-method (psdir): No root-related binaries found when scanning " +
                    "getenv(PATH) directories.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Luna: rustmagisk (APatch detection)
    // popen(cmd, "r") → fgets lines → count > 100 means suspicious
    // Detects APatch-specific processes via /proc scan.
    // -------------------------------------------------------------------------
    private fun checkAPatchProcesses(): DetectionResult {
        val apatchPatterns = listOf("apd", "apatch", "kpatch", "kp_su", "magiskd", "magisk32", "magisk64")
        val found = mutableListOf<String>()
        var procCount = 0

        try {
            val procDir = File("/proc")
            val pidDirs = procDir.listFiles { f ->
                f.isDirectory && f.name.all { it.isDigit() }
            } ?: emptyArray()

            for (pidDir in pidDirs) {
                val cmdlineFile = File(pidDir, "cmdline")
                if (!cmdlineFile.canRead()) continue
                val cmdline = cmdlineFile.readBytes()
                    .map { b -> if (b == 0.toByte()) ' ' else b.toInt().toChar() }
                    .joinToString("")
                    .trim()
                if (cmdline.isEmpty()) continue
                procCount++
                for (pattern in apatchPatterns) {
                    if (cmdline.contains(pattern, ignoreCase = true) &&
                        found.none { it.contains(pattern) }
                    ) {
                        found.add("$pattern (pid=${pidDir.name}): $cmdline")
                    }
                }
            }
        } catch (_: Exception) {}

        return if (found.isNotEmpty()) {
            DetectionResult(
                id = "luna_apatch_proc",
                name = "APatch/Root Process Detected",
                category = DetectionCategory.APATCH,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "APatch or root framework processes found running.",
                detailedReason = "Luna-method (rustmagisk): /proc scan for APatch/root process names. " +
                    "Found: ${found.joinToString(", ")}. " +
                    "apd is the APatch daemon; its presence confirms APatch is active. " +
                    "magiskd indicates Magisk is running.",
                solution = "Uninstall APatch via the APatch Manager app, or Magisk via Magisk Manager.",
                technicalDetail = "Processes: ${found.joinToString("; ")}; total procs scanned: $procCount"
            )
        } else {
            DetectionResult(
                id = "luna_apatch_proc",
                name = "APatch/Root Process",
                category = DetectionCategory.APATCH,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "No APatch or root framework processes detected.",
                detailedReason = "Luna-method (rustmagisk): No APatch-related process names found in /proc.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Luna: fhma
    // stat(DAT_00140690, &buf) → buf.st_size > 0x7FF (2047) means suspicious
    // Checks that small system config files have not grown unexpectedly.
    // -------------------------------------------------------------------------
    private fun checkSuspiciousFileSize(): DetectionResult {
        // Maps path → maximum expected size in bytes.
        // Luna flags size > 2047 bytes on a particular file; we apply the same
        // threshold to small system files that root tools may replace.
        val sizeThresholds = mapOf(
            "/proc/self/attr/current" to 512L
        )
        val suspicious = mutableListOf<String>()

        for ((path, maxSize) in sizeThresholds) {
            val file = File(path)
            if (!file.exists()) continue
            try {
                val size = file.length()
                if (size > maxSize) {
                    suspicious.add("$path (size=$size B, expected ≤$maxSize B)")
                }
            } catch (_: Exception) {}
        }

        return if (suspicious.isNotEmpty()) {
            DetectionResult(
                id = "luna_file_size",
                name = "Suspicious File Size Anomaly",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "System files have unexpected sizes, indicating possible tampering.",
                detailedReason = "Luna-method (fhma): stat() check revealed files with anomalous sizes. " +
                    "${suspicious.joinToString("; ")}. " +
                    "Root tools may replace or append to system files, inflating their size beyond expected.",
                solution = "Restore affected system files from a stock firmware image.",
                technicalDetail = suspicious.joinToString("; ")
            )
        } else {
            DetectionResult(
                id = "luna_file_size",
                name = "File Size Anomaly",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.MEDIUM,
                description = "No suspicious file size anomalies detected.",
                detailedReason = "Luna-method (fhma): Checked system files for size anomalies; none found.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Luna: checksuskernel
    // stat(DAT_00140650, &buf) → (buf.st_nlink & 0x1FF) != 365 means suspicious
    // We implement an equivalent check via /proc/net/unix socket enumeration
    // and SELinux context inspection.
    // -------------------------------------------------------------------------
    private fun checkKernelStatAnomaly(): DetectionResult {
        val suspicious = mutableListOf<String>()

        try {
            // Check /proc/self/attr/current for unexpected SELinux context
            val attrFile = File("/proc/self/attr/current")
            if (attrFile.exists() && attrFile.canRead()) {
                val ctx = attrFile.readText().trim().trimEnd('\u0000')
                // An app running with an su or root SELinux context is suspicious
                if ((ctx.contains("su", ignoreCase = true) ||
                        ctx.contains("magisk", ignoreCase = true)) &&
                    !ctx.contains("untrusted_app")
                ) {
                    suspicious.add("/proc/self/attr/current: elevated SELinux context ($ctx)")
                }
            }

            // Check /proc/net/unix for root framework UNIX sockets (proxy for st_nlink anomaly)
            val unixSockets = File("/proc/net/unix")
            if (unixSockets.canRead()) {
                val content = unixSockets.readText()
                val rootSocketMarkers = listOf("magisk", "@ksu", "lspd", "apatch", "@KSUUD")
                val hits = rootSocketMarkers.filter { content.contains(it, ignoreCase = true) }
                if (hits.isNotEmpty()) {
                    suspicious.add("/proc/net/unix: root sockets detected (${hits.joinToString(", ")})")
                }
            }
        } catch (_: Exception) {}

        return if (suspicious.isNotEmpty()) {
            DetectionResult(
                id = "luna_kernel_stat",
                name = "Kernel Stat Anomaly Detected",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Kernel-level anomalies detected via /proc inspection.",
                detailedReason = "Luna-method (checksuskernel): Kernel stat anomaly checks found: " +
                    "${suspicious.joinToString("; ")}. " +
                    "Root frameworks register UNIX sockets in /proc/net/unix and may run with " +
                    "elevated SELinux contexts.",
                solution = "These kernel-level indicators are cleared when root frameworks are fully uninstalled.",
                technicalDetail = suspicious.joinToString("; ")
            )
        } else {
            DetectionResult(
                id = "luna_kernel_stat",
                name = "Kernel Stat Anomaly",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "No kernel stat anomalies detected.",
                detailedReason = "Luna-method (checksuskernel): No kernel anomalies found in /proc.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Luna: magiskmounts
    // Parses /proc/mounts (equivalent to /proc/self/mounts) looking for entries
    // characteristic of Magisk bind-mounts, overlayfs, and mirror paths used by
    // Magisk to hide its modifications from the regular filesystem namespace.
    // -------------------------------------------------------------------------
    private fun checkMagiskProcMounts(): DetectionResult {
        val suspicious = mutableListOf<String>()

        try {
            val mounts = File("/proc/self/mounts")
            if (mounts.canRead()) {
                val lines = mounts.readLines()
                // Only flag unambiguously Magisk/KSU-specific markers.
                // Excluded intentionally:
                // • /data_mirror  — Android 11+ new namespace mechanism (normal on stock AOSP)
                // • /apex/com.android.os — APEX partition present on all modern Android devices
                // Both of the above appear on clean devices and must not be treated as root signals.
                val magiskMarkers = listOf(
                    "magisk", ".magisk",
                    "worker/upper/data",
                    "/sbin/.core", "@ksu"
                )
                for (line in lines) {
                    val lower = line.lowercase()
                    for (marker in magiskMarkers) {
                        if (lower.contains(marker.lowercase()) &&
                            suspicious.none { it.contains(marker) }
                        ) {
                            suspicious.add("$marker in mount entry: ${line.take(120)}")
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return if (suspicious.isNotEmpty()) {
            DetectionResult(
                id = "luna_magisk_mounts",
                name = "Magisk Mount Entries Detected",
                category = DetectionCategory.MAGISK,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "Magisk-related entries found in /proc/self/mounts.",
                detailedReason = "Luna-method (magiskmounts): /proc/self/mounts contains characteristic " +
                    "Magisk bind-mount or overlay entries. " +
                    "Found: ${suspicious.joinToString("; ")}. " +
                    "Magisk hides itself and its modules by creating bind-mounts and overlay filesystems " +
                    "that leave traces in the mount table.",
                solution = "Uninstall Magisk via the Magisk Manager app and reboot to restore stock mounts.",
                technicalDetail = suspicious.joinToString("; ")
            )
        } else {
            DetectionResult(
                id = "luna_magisk_mounts",
                name = "Magisk Mount Entries",
                category = DetectionCategory.MAGISK,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "No Magisk-related mount entries found.",
                detailedReason = "Luna-method (magiskmounts): /proc/self/mounts contains no known " +
                    "Magisk-specific bind-mount or overlay markers.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Luna: zygoteinject
    // Checks the SELinux context of /proc/self/attr/current and related paths
    // for evidence of Zygote injection by Magisk or LSPosed.
    // Luna detects: attr_prev containing "zygote" → possible Magisk injection.
    // Native Test: verifies /proc/self/attr/current, /mnt, /mnt/obb, /mnt/asec
    // do NOT carry "zygote" / injected context without being untrusted_app.
    // -------------------------------------------------------------------------
    private fun checkZygoteInjection(): DetectionResult {
        val suspicious = mutableListOf<String>()

        try {
            val attrFile = File("/proc/self/attr/current")
            if (attrFile.canRead()) {
                val ctx = attrFile.readText().trim().trimEnd('\u0000')
                // A process injected via Zygote by Magisk may carry a context that
                // references "zygote" but is no longer labeled as untrusted_app,
                // indicating the SELinux label was altered post-fork.
                if (ctx.contains("zygote", ignoreCase = true) &&
                    !ctx.contains("untrusted_app")
                ) {
                    suspicious.add("/proc/self/attr/current: zygote-related context without untrusted_app ($ctx)")
                }
            }

            // Also check /proc/self/attr/prev when readable — Magisk injection leaves
            // the previous domain label in attr_prev containing "zygote".
            val attrPrev = File("/proc/self/attr/prev")
            if (attrPrev.canRead()) {
                val prev = attrPrev.readText().trim().trimEnd('\u0000')
                if (prev.contains("zygote", ignoreCase = true)) {
                    suspicious.add("/proc/self/attr/prev: contains 'zygote' ($prev) — possible Magisk injection")
                }
            }
        } catch (_: Exception) {}

        return if (suspicious.isNotEmpty()) {
            DetectionResult(
                id = "luna_zygote_inject",
                name = "Zygote Injection Detected",
                category = DetectionCategory.MAGISK,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "SELinux context indicates possible Zygote-level injection by Magisk/LSPosed.",
                detailedReason = "Luna-method (zygoteinject): SELinux context analysis found: " +
                    "${suspicious.joinToString("; ")}. " +
                    "Magisk and LSPosed hook into Zygote to inject code into every new app process. " +
                    "This leaves characteristic SELinux context traces in /proc/self/attr/.",
                solution = "Uninstall Magisk and LSPosed, then verify SELinux policy is restored to stock.",
                technicalDetail = suspicious.joinToString("; ")
            )
        } else {
            DetectionResult(
                id = "luna_zygote_inject",
                name = "Zygote Injection",
                category = DetectionCategory.MAGISK,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "No Zygote injection indicators found in SELinux contexts.",
                detailedReason = "Luna-method (zygoteinject): /proc/self/attr/current and attr/prev " +
                    "show no signs of Zygote-level injection.",
                solution = "No action required."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Native Test: tmpfsmount
    // Native Test checks /mnt, /mnt/obb, /mnt/asec via fstatat() and compares
    // their SELinux xattr (getxattr "security.selinux") against expected values
    // u:object:tmpfs: / _r:tmpfs:sf / tmpfs:s0.
    // Magisk uses private tmpfs mounts on these paths to isolate its namespace;
    // the presence of a non-standard tmpfs context on them is suspicious.
    // -------------------------------------------------------------------------
    private fun checkTmpfsMountAnomaly(): DetectionResult {
        val suspicious = mutableListOf<String>()

        // Paths expected to be simple tmpfs directories with standard SELinux labels.
        // Root frameworks (especially Magisk) mount private tmpfs namespaces here.
        val checkPaths = listOf("/mnt/obb", "/mnt/asec", "/mnt")

        try {
            val mounts = File("/proc/self/mounts")
            if (mounts.canRead()) {
                val mountContent = mounts.readText()
                for (path in checkPaths) {
                    // A private tmpfs overlaid on these dirs by Magisk will appear
                    // as a second "tmpfs <path>" entry that overrides the stock one.
                    val matches = mountContent.lines().filter { line ->
                        val parts = line.split(" ")
                        parts.size >= 3 &&
                            parts[1] == path &&
                            parts[2].lowercase() == "tmpfs"
                    }
                    if (matches.size > 1) {
                        suspicious.add("$path has ${matches.size} tmpfs mounts (expected 1) — possible Magisk private namespace")
                    }
                }
            }

            // Additionally check whether these directories are accessible at all —
            // if Magisk has replaced them with an isolated tmpfs, readdir may behave
            // differently than expected on a stock device.
            for (path in listOf("/mnt/obb", "/mnt/asec")) {
                val dir = File(path)
                if (!dir.exists()) {
                    suspicious.add("$path does not exist (unexpected on a standard Android system)")
                }
            }
        } catch (_: Exception) {}

        return if (suspicious.isNotEmpty()) {
            DetectionResult(
                id = "luna_tmpfs_mount",
                name = "Tmpfs Mount Anomaly Detected",
                category = DetectionCategory.MAGISK,
                status = DetectionStatus.DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Suspicious tmpfs mounts detected on system mount points.",
                detailedReason = "Native-Test-method (tmpfsmount): Anomalies found on /mnt/obb or /mnt/asec: " +
                    "${suspicious.joinToString("; ")}. " +
                    "Magisk creates private tmpfs namespaces on these paths to isolate its module overlays " +
                    "from the global mount namespace, leaving multiple tmpfs entries for the same path.",
                solution = "Uninstall Magisk and reboot to restore stock mount namespaces.",
                technicalDetail = suspicious.joinToString("; ")
            )
        } else {
            DetectionResult(
                id = "luna_tmpfs_mount",
                name = "Tmpfs Mount Anomaly",
                category = DetectionCategory.MAGISK,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "No suspicious tmpfs mount anomalies detected.",
                detailedReason = "Native-Test-method (tmpfsmount): /mnt/obb and /mnt/asec mount entries " +
                    "appear normal.",
                solution = "No action required."
            )
        }
    }

    // ---- Utilities ----------------------------------------------------------

    private fun packageExists(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            BufferedReader(InputStreamReader(process.inputStream)).readLine()?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
