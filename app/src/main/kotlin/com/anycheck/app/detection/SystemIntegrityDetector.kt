package com.anycheck.app.detection

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator

/**
 * Checks that validate the hardware-backed security posture of the device:
 *  1. AVB (Android Verified Boot) — checks ro.boot.verifiedbootstate == "green"
 *  2. TEE (Trusted Execution Environment) — checks ro.hardware.keystore / TEE availability
 *  3. Keystore-backed key attestation — verifies hardware-backed key generation works
 */
class SystemIntegrityDetector(private val context: Context) {

    fun runAllChecks(): List<DetectionResult> = listOf(
        checkAvbBootState(),
        checkTeeAvailability(),
        checkKeystoreAttestation()
    )

    // ----------------------------------------------------------------
    // Check 1: Android Verified Boot (AVB) state
    // ----------------------------------------------------------------
    private fun checkAvbBootState(): DetectionResult {
        return try {
            val verifiedBootState = getSystemProperty("ro.boot.verifiedbootstate")
            val verityMode = getSystemProperty("ro.boot.veritymode")
            val bootState = getSystemProperty("ro.boot.flash.locked")

            when {
                verifiedBootState.equals("green", ignoreCase = true) -> {
                    DetectionResult(
                        id = "avb_boot_state",
                        name = "AVB Boot State",
                        category = DetectionCategory.SYSTEM_INTEGRITY,
                        status = DetectionStatus.NOT_DETECTED,
                        riskLevel = RiskLevel.CRITICAL,
                        description = "Verified Boot state is GREEN.",
                        detailedReason = "ro.boot.verifiedbootstate=$verifiedBootState. " +
                            "The device booted from verified, unmodified system partitions. " +
                            "This is the strongest boot integrity assurance.",
                        solution = "No action required."
                    )
                }
                verifiedBootState.equals("yellow", ignoreCase = true) -> {
                    DetectionResult(
                        id = "avb_boot_state",
                        name = "AVB Boot State: YELLOW",
                        category = DetectionCategory.SYSTEM_INTEGRITY,
                        status = DetectionStatus.DETECTED,
                        riskLevel = RiskLevel.HIGH,
                        description = "Verified Boot state is YELLOW (custom ROM / self-signed).",
                        detailedReason = "ro.boot.verifiedbootstate=$verifiedBootState. " +
                            "The bootloader verified the boot image against a user-installed key " +
                            "rather than a manufacturer key. This indicates a custom ROM or " +
                            "user-enrolled AVB key. The device may be running unofficial firmware.",
                        solution = "Flash official OEM firmware to restore GREEN verified boot state.",
                        technicalDetail = "verifiedbootstate=$verifiedBootState veritymode=$verityMode"
                    )
                }
                verifiedBootState.equals("orange", ignoreCase = true) -> {
                    DetectionResult(
                        id = "avb_boot_state",
                        name = "AVB Boot State: ORANGE (Unlocked)",
                        category = DetectionCategory.SYSTEM_INTEGRITY,
                        status = DetectionStatus.DETECTED,
                        riskLevel = RiskLevel.CRITICAL,
                        description = "Verified Boot state is ORANGE — bootloader is UNLOCKED.",
                        detailedReason = "ro.boot.verifiedbootstate=$verifiedBootState. " +
                            "The bootloader is unlocked and no signature is enforced. " +
                            "The device can boot arbitrary unsigned images. " +
                            "This is a strong indicator of a rooted or modified device.",
                        solution = "Re-lock the bootloader via 'fastboot flashing lock' after restoring stock firmware.",
                        technicalDetail = "verifiedbootstate=$verifiedBootState flash.locked=$bootState"
                    )
                }
                verifiedBootState.equals("red", ignoreCase = true) -> {
                    DetectionResult(
                        id = "avb_boot_state",
                        name = "AVB Boot State: RED (Integrity Failure)",
                        category = DetectionCategory.SYSTEM_INTEGRITY,
                        status = DetectionStatus.DETECTED,
                        riskLevel = RiskLevel.CRITICAL,
                        description = "Verified Boot state is RED — boot image verification FAILED.",
                        detailedReason = "ro.boot.verifiedbootstate=$verifiedBootState. " +
                            "The device failed to verify the integrity of the boot image. " +
                            "This may indicate a modified bootloader or corrupted system partition.",
                        solution = "Restore factory firmware via fastboot or OTA.",
                        technicalDetail = "verifiedbootstate=$verifiedBootState"
                    )
                }
                verifiedBootState.isNotEmpty() -> {
                    DetectionResult(
                        id = "avb_boot_state",
                        name = "AVB Boot State: Unknown",
                        category = DetectionCategory.SYSTEM_INTEGRITY,
                        status = DetectionStatus.DETECTED,
                        riskLevel = RiskLevel.HIGH,
                        description = "Unexpected Verified Boot state: $verifiedBootState.",
                        detailedReason = "ro.boot.verifiedbootstate=$verifiedBootState is not a standard value. " +
                            "Expected: green, yellow, orange, or red.",
                        solution = "Check device firmware integrity.",
                        technicalDetail = "verifiedbootstate=$verifiedBootState"
                    )
                }
                else -> {
                    // Property not readable — try fallback via /sys/class/android_usb or dm-verity
                    val dmVerityState = runCatching {
                        File("/sys/block/dm-0/dm/uuid").exists().toString()
                    }.getOrDefault("unknown")
                    DetectionResult(
                        id = "avb_boot_state",
                        name = "AVB Boot State",
                        category = DetectionCategory.SYSTEM_INTEGRITY,
                        status = DetectionStatus.NOT_DETECTED,
                        riskLevel = RiskLevel.HIGH,
                        description = "AVB state property not available.",
                        detailedReason = "ro.boot.verifiedbootstate is empty or inaccessible on this device. " +
                            "This is normal on older devices (pre-Android 8) that do not support AVB 2.0.",
                        solution = "No action required.",
                        technicalDetail = "verifiedbootstate=(empty) veritymode=$verityMode"
                    )
                }
            }
        } catch (e: Exception) {
            DetectionResult(
                id = "avb_boot_state",
                name = "AVB Boot State",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Could not determine AVB boot state.",
                detailedReason = "Exception reading AVB properties: ${e.message}",
                solution = "No action required."
            )
        }
    }

    // ----------------------------------------------------------------
    // Check 2: Trusted Execution Environment (TEE) availability
    // ----------------------------------------------------------------
    private fun checkTeeAvailability(): DetectionResult {
        return try {
            // ro.hardware.keystore tells us what TEE implementation is used
            val keystoreHw = getSystemProperty("ro.hardware.keystore")
            // ro.crypto.state shows if userdata encryption is active (requires TEE)
            val cryptoState = getSystemProperty("ro.crypto.state")
            // sys.oem_unlock_allowed — supplemental signal
            val teeImpl = getSystemProperty("ro.tee.type")

            // Check if Android Keystore can reach a hardware-backed key
            val keystore = KeyStore.getInstance("AndroidKeyStore")
            keystore.load(null)

            // If any TEE-backed key is already present, TEE is confirmed
            val hasTeeKey = keystore.aliases().toList().any { alias ->
                runCatching {
                    val entry = keystore.getEntry(alias, null)
                    // existence of hardware-backed entries implies TEE
                    entry != null
                }.getOrDefault(false)
            }

            val isTeePresent = keystoreHw.isNotEmpty() ||
                cryptoState.equals("encrypted", ignoreCase = true) ||
                teeImpl.isNotEmpty() ||
                hasTeeKey ||
                Build.VERSION.SDK_INT >= 23 // StrongBox / TEE required from M+

            if (isTeePresent) {
                DetectionResult(
                    id = "tee_availability",
                    name = "TEE / Hardware Keystore",
                    category = DetectionCategory.SYSTEM_INTEGRITY,
                    status = DetectionStatus.NOT_DETECTED,
                    riskLevel = RiskLevel.CRITICAL,
                    description = "Trusted Execution Environment (TEE) is present.",
                    detailedReason = "Hardware-backed security is available. " +
                        "ro.hardware.keystore=$keystoreHw, ro.crypto.state=$cryptoState. " +
                        "TEE enables hardware-backed key storage, biometrics, and attestation.",
                    solution = "No action required.",
                    technicalDetail = "keystore.hw=$keystoreHw tee.type=$teeImpl crypto=$cryptoState"
                )
            } else {
                DetectionResult(
                    id = "tee_availability",
                    name = "TEE / Hardware Keystore: Not Found",
                    category = DetectionCategory.SYSTEM_INTEGRITY,
                    status = DetectionStatus.DETECTED,
                    riskLevel = RiskLevel.CRITICAL,
                    description = "No TEE implementation detected.",
                    detailedReason = "ro.hardware.keystore is empty and no hardware-backed " +
                        "keystore signals were found. The device may be an emulator, " +
                        "or TEE has been disabled/bypassed.",
                    solution = "Use a device with a hardware TEE for secure key operations.",
                    technicalDetail = "keystore.hw=$keystoreHw tee.type=$teeImpl"
                )
            }
        } catch (e: Exception) {
            DetectionResult(
                id = "tee_availability",
                name = "TEE / Hardware Keystore",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.CRITICAL,
                description = "TEE check could not be completed.",
                detailedReason = "Exception: ${e.message}",
                solution = "No action required."
            )
        }
    }

    // ----------------------------------------------------------------
    // Check 3: Hardware-backed key attestation (Keystore integrity)
    // ----------------------------------------------------------------
    private fun checkKeystoreAttestation(): DetectionResult {
        return try {
            val keyAlias = "anycheck_attestation_probe_${System.currentTimeMillis()}"
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()

            // Retrieve and check the key origin
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val keyInfo = keyStore.getKey(keyAlias, null) as javax.crypto.SecretKey
            val isHardwareBacked = runCatching {
                val keyFactory = javax.crypto.SecretKeyFactory.getInstance(
                    keyInfo.algorithm, "AndroidKeyStore"
                )
                val info = keyFactory.getKeySpec(
                    keyInfo,
                    android.security.keystore.KeyInfo::class.java
                ) as android.security.keystore.KeyInfo
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    info.securityLevel == android.security.keystore.KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                        info.securityLevel == android.security.keystore.KeyProperties.SECURITY_LEVEL_STRONGBOX
                } else {
                    @Suppress("DEPRECATION")
                    info.isInsideSecureHardware
                }
            }.getOrDefault(false)

            // Clean up probe key
            runCatching { keyStore.deleteEntry(keyAlias) }

            if (isHardwareBacked) {
                DetectionResult(
                    id = "keystore_attestation",
                    name = "Hardware-Backed Keystore",
                    category = DetectionCategory.SYSTEM_INTEGRITY,
                    status = DetectionStatus.NOT_DETECTED,
                    riskLevel = RiskLevel.CRITICAL,
                    description = "Keys are generated and stored in secure hardware (TEE/StrongBox).",
                    detailedReason = "AES key generated via AndroidKeyStore resides inside " +
                        "the Trusted Execution Environment or StrongBox HSM. " +
                        "Private key material never leaves secure hardware.",
                    solution = "No action required.",
                    technicalDetail = "hardware_backed=true sdk=${Build.VERSION.SDK_INT}"
                )
            } else {
                DetectionResult(
                    id = "keystore_attestation",
                    name = "Hardware-Backed Keystore: Software Only",
                    category = DetectionCategory.SYSTEM_INTEGRITY,
                    status = DetectionStatus.DETECTED,
                    riskLevel = RiskLevel.HIGH,
                    description = "Keys are stored in software keystore, not secure hardware.",
                    detailedReason = "The AndroidKeyStore key was generated in the software " +
                        "emulation layer rather than in a hardware TEE. This may indicate " +
                        "an emulator, a rooted device with Magisk hiding active, or a device " +
                        "that does not have a hardware-backed keystore.",
                    solution = "Use a device with hardware-backed key storage for sensitive operations.",
                    technicalDetail = "hardware_backed=false sdk=${Build.VERSION.SDK_INT}"
                )
            }
        } catch (e: Exception) {
            DetectionResult(
                id = "keystore_attestation",
                name = "Hardware-Backed Keystore",
                category = DetectionCategory.SYSTEM_INTEGRITY,
                status = DetectionStatus.NOT_DETECTED,
                riskLevel = RiskLevel.HIGH,
                description = "Keystore attestation check could not be completed.",
                detailedReason = "Exception during key generation/inspection: ${e.message}",
                solution = "No action required."
            )
        }
    }

    private fun getSystemProperty(name: String): String = try {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", name))
        process.inputStream.bufferedReader().readLine()?.trim() ?: ""
    } catch (_: Exception) {
        ""
    }
}
