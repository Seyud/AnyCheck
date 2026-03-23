package com.anycheck.app.detection

/**
 * JNI wrapper for the native LSPosed detection library (libanycheck.so).
 *
 * The native probes use raw POSIX syscalls (open/read/stat) and bionic-level
 * APIs (dlopen, dlsym, __system_property_get) which are not intercepted by
 * LSPlant's Java-layer hooking engine.
 */
object NativeDetector {

    private var libraryLoaded = false

    init {
        try {
            System.loadLibrary("anycheck")
            libraryLoaded = true
        } catch (_: UnsatisfiedLinkError) {
        }
    }

    /**
     * Runs all 6 native probes and returns a semicolon-separated string of
     * findings.  Returns an empty string if nothing is detected or if the
     * native library could not be loaded.
     */
    fun detectLSPosed(): String {
        if (!libraryLoaded) return ""
        return try {
            detectLSPosedJni()
        } catch (_: Throwable) {
            ""
        }
    }

    private external fun detectLSPosedJni(): String
}
