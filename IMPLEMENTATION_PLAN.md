# JNI Native LSPosed Detection — Implementation Plan

## Background & Problem Statement

The existing 26 Kotlin/Java checks in `XposedDetector.kt` all use Java-layer APIs
(`File.exists()`, `BufferedReader`, `Class.forName`, etc.).  LSPosed uses
**LSPlant** (an ART hook engine) which can patch these Java methods so they
return falsified results.  Moving detection code into native C++ bypasses
LSPlant because:

* Native `open()`/`read()` syscalls are not intercepted by LSPlant.
* `dlopen`/`dlsym` at the C level can see loaded libraries before the
  Java class-loader is involved.
* `__system_property_get()` reads the property system directly from the
  C bionic layer.

---

## Analysis: `libreveny.so` (the `.FROSTELF.` file)

| Property | Value |
|---|---|
| File size | 2,392,969 bytes |
| File magic | `.FROSTELF.` (not a standard `\x7fELF`) |
| Entropy | 7.999 / 8.0 — strongly encrypted |
| Standard ELF magic | **Not present** anywhere in file |

**Conclusion**: `libreveny.so` is an AES/ChaCha20-encrypted ELF blob using
reveny's proprietary `.FROSTELF.` packing format.  The decryption/loader code
is **not** present in reveny's public GitHub source.  Therefore:

* We **cannot** ship or load this blob directly via `System.loadLibrary()`.
* We **cannot** wrap it with a JNI caller without the decryptor stub.
* The correct approach is to **write our own native C++** library that
  implements the same detection techniques.

---

## What reveny's Native Library Checks (from source & `new.md` reverse)

| ID | Function | Technique |
|---|---|---|
| N1 | maps-scan | `/proc/self/maps` raw scan for lsposed/lsplant filenames |
| N2 | dlopen-probe | `dlopen(name, RTLD_NOLOAD)` — succeeds only if lib already loaded |
| N3 | dlsym-symbol | `dlsym(RTLD_DEFAULT, "xposedBridgeHandleHookedMethod")` |
| N4 | stat-paths | `stat("/data/misc/lspd")`, `/data/adb/modules/zygisk_lsposed` |
| N5 | native-bridge | `__system_property_get("ro.dalvik.vm.native.bridge")` ≠ empty/0 |
| N6 | smaps-dirty | Read `Private_Dirty` on `libart.so`/`libc.so` via raw `/proc/self/smaps` |
| N7 | fork-isolate | `fork()` child process re-runs N1-N4; bypasses parent-level hooks |

---

## Implementation Plan

### Step 1 — Enable NDK + CMake in `app/build.gradle.kts`

Inside `android { defaultConfig { … } }`, add:

```kotlin
externalNativeBuild {
    cmake {
        cppFlags("-std=c++17")
        arguments("-DANDROID_STL=c++_shared")
    }
}
ndk {
    abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
}
```

After the `defaultConfig` block, add a sibling block:

```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
```

---

### Step 2 — Create `app/src/main/cpp/CMakeLists.txt`

```cmake
cmake_minimum_required(VERSION 3.22.1)
project("anycheck")

add_library(anycheck SHARED lsposed_detect.cpp)
target_link_libraries(anycheck log)
```

---

### Step 3 — Create `app/src/main/cpp/lsposed_detect.cpp`

The file exposes a single JNI function:

```c
JNIEXPORT jstring JNICALL
Java_com_anycheck_app_detection_NativeDetector_detectLSPosed(JNIEnv *env, jobject)
```

It returns a semicolon-separated string of findings (empty = nothing detected).

#### Sub-check N1: Raw `/proc/self/maps` scan

```cpp
// Open with open(), read line-by-line with read()
// Search filename part of each mapping against needle list:
static const char *MAPS_NEEDLES[] = {
    "lsposed", "lsplant", "liblsplant", "lspd",
    "edxposed", "xposed", "libxposed-native",
    "liblspatch", "libfake-linker", nullptr
};
```

Key: use raw POSIX `open()` / `read()` — NOT `fopen()` or Java `File`.  
LSPlant hooks Java methods and libc's `fopen()` can be hooked via GOT patching
in certain configurations; raw `open(2)` is safer.

#### Sub-check N2: `dlopen(RTLD_NOLOAD)` probe

```cpp
static const char *DLOPEN_TARGETS[] = {
    "liblsplant.so", "liblsposed.so",
    "libxposed-native.so", "liblspd.so", nullptr
};
// void *h = dlopen(name, RTLD_NOLOAD | RTLD_LAZY);
// Non-null → already mapped → detected
```

#### Sub-check N3: `dlsym(RTLD_DEFAULT)` symbol search

```cpp
// XposedBridge (old Xposed):
dlsym(RTLD_DEFAULT, "xposedBridgeHandleHookedMethod")
dlsym(RTLD_DEFAULT, "Java_de_robv_android_xposed_XposedBridge_hookMethodNative")
// LSPlant internal exports:
dlsym(RTLD_DEFAULT, "_ZN7lsplant10InitializeEP7_JNIEnv")
dlsym(RTLD_DEFAULT, "_ZN7lsplant4HookEP7_JNIEnvP8_jobjectP10_jmethodIDS4_")
```

#### Sub-check N4: `stat()` path probe

```cpp
static const char *LSPD_PATHS[] = {
    "/data/misc/lspd",
    "/data/misc/lspd/config.json",
    "/data/adb/lspd",
    "/data/adb/modules/zygisk_lsposed",
    "/data/adb/modules/riru_lsposed",
    "/data/adb/modules/lsposed",
    nullptr
};
struct stat st;
if (stat(path, &st) == 0) → detected
```

#### Sub-check N5: `ro.dalvik.vm.native.bridge`

```cpp
#include <sys/system_properties.h>
char value[PROP_VALUE_MAX] = {0};
__system_property_get("ro.dalvik.vm.native.bridge", value);
// If non-empty and not "0": Riru/LSPosed native bridge set → detected
```

**This is the most reliable single check.** LSPosed Riru variant sets this
property to `libriruloader.so` or similar.  LSPosed Zygisk variant may leave it
empty, but the maps/dlopen checks catch the Zygisk variant.

#### Sub-check N6: `/proc/self/smaps` Private_Dirty (native version)

```cpp
// Same logic as Kotlin checkSMAPSInlineHooks() but uses raw open()/read()
// LSPlant patches libc.so/libart.so pages → Private_Dirty > 0 on r-xp segments
```

#### Sub-check N7 (optional / experimental): fork + child isolation

```cpp
pid_t pid = fork();
if (pid == 0) {
    // child: re-run N1–N5 with clean state
    // write result to pipe and exit
    _exit(0);
} else {
    // parent: read pipe result
}
```

This is listed as optional because:
* `fork()` in Android can have side effects (crashes in multi-threaded app).
* The child inherits all parent mappings, so N1/N2 results are identical.
* Main benefit: bypasses any runtime hook that monitors the parent thread.
* Should be guarded by `Build.VERSION.SDK_INT` check and wrapped in try/catch.

---

### Step 4 — Create `NativeDetector.kt`

```kotlin
package com.anycheck.app.detection

object NativeDetector {
    private var libraryLoaded = false

    init {
        try {
            System.loadLibrary("anycheck")
            libraryLoaded = true
        } catch (_: UnsatisfiedLinkError) {}
    }

    /** Returns semicolon-separated findings string, empty if nothing detected. */
    fun detectLSPosed(): String {
        if (!libraryLoaded) return ""
        return try { detectLSPosedJni() } catch (_: Throwable) { "" }
    }

    private external fun detectLSPosedJni(): String
}
```

Note: JNI method name must match:
`Java_com_anycheck_app_detection_NativeDetector_detectLSPosedJni`

---

### Step 5 — Add `checkNativeLSPosedDetection()` to `XposedDetector.kt`

1. Add `checkNativeLSPosedDetection()` to the `runAllChecks()` list.
2. Implement the method following the existing pattern:

```kotlin
private fun checkNativeLSPosedDetection(): DetectionResult {
    val findings = NativeDetector.detectLSPosed()
    return if (findings.isNotEmpty()) {
        DetectionResult(
            id = "native_lsposed_detect",
            name = context.getString(R.string.chk_native_lsposed_name),
            category = DetectionCategory.XPOSED,
            status = DetectionStatus.DETECTED,
            riskLevel = RiskLevel.CRITICAL,
            description = context.getString(R.string.chk_native_lsposed_desc),
            detailedReason = context.getString(R.string.chk_native_lsposed_reason, findings),
            solution = context.getString(R.string.chk_native_lsposed_solution),
            technicalDetail = findings
        )
    } else {
        DetectionResult(
            id = "native_lsposed_detect",
            name = context.getString(R.string.chk_native_lsposed_name_nd),
            category = DetectionCategory.XPOSED,
            status = DetectionStatus.NOT_DETECTED,
            riskLevel = RiskLevel.CRITICAL,
            description = context.getString(R.string.chk_native_lsposed_desc_nd),
            detailedReason = context.getString(R.string.chk_native_lsposed_reason_nd),
            solution = context.getString(R.string.chk_no_action_needed)
        )
    }
}
```

---

### Step 6 — Add String Resources

#### `app/src/main/res/values/strings.xml` (EN)

```xml
<!-- XposedDetector: Check 27 — JNI native LSPosed detection -->
<string name="chk_native_lsposed_name">LSPosed Detected (Native)</string>
<string name="chk_native_lsposed_name_nd">Native LSPosed Detection</string>
<string name="chk_native_lsposed_desc">LSPosed / LSPlant detected via native C++ probes that bypass Java-level hooks.</string>
<string name="chk_native_lsposed_desc_nd">No LSPosed / LSPlant indicators found by native C++ probes.</string>
<string name="chk_native_lsposed_reason">Native probes found: %1$s. Checks: raw /proc/self/maps syscall scan, dlopen(RTLD_NOLOAD), dlsym XposedBridge symbols, stat() path check, ro.dalvik.vm.native.bridge property.</string>
<string name="chk_native_lsposed_reason_nd">All native probes passed: no LSPosed libs in maps, no dlopen match, no exported XposedBridge symbols, no /data/misc/lspd found via stat(), native bridge property unset.</string>
<string name="chk_native_lsposed_solution">Disable all LSPosed modules targeting this app and reboot.</string>
```

#### `app/src/main/res/values-zh/strings.xml` (ZH)

```xml
<!-- XposedDetector: 检测27 — JNI原生 LSPosed 检测 -->
<string name="chk_native_lsposed_name">检测到 LSPosed（原生层）</string>
<string name="chk_native_lsposed_name_nd">原生 LSPosed 检测</string>
<string name="chk_native_lsposed_desc">通过绕过 Java 层 Hook 的原生 C++ 探针检测到 LSPosed / LSPlant。</string>
<string name="chk_native_lsposed_desc_nd">原生 C++ 探针未发现任何 LSPosed / LSPlant 指标。</string>
<string name="chk_native_lsposed_reason">原生探针发现：%1$s。检测手段：raw syscall /proc/self/maps 扫描、dlopen(RTLD_NOLOAD)、dlsym XposedBridge 符号、stat() 路径检查、ro.dalvik.vm.native.bridge 属性。</string>
<string name="chk_native_lsposed_reason_nd">所有原生探针均通过：maps 中未发现 LSPosed 库、dlopen 无匹配、无导出的 XposedBridge 符号、stat() 未找到 /data/misc/lspd、原生桥属性未设置。</string>
<string name="chk_native_lsposed_solution">禁用所有针对此应用的 LSPosed 模块并重启设备。</string>
```

---

### Step 7 — Verify Build

```bash
cd app && ../gradlew assembleDebug
```

Checks:
* Gradle picks up `externalNativeBuild` → CMake runs → `libanycheck.so` compiled.
* `System.loadLibrary("anycheck")` succeeds at runtime.
* `NativeDetector.detectLSPosed()` returns non-empty on an LSPosed device.

---

## File Checklist for Next Session

```
app/build.gradle.kts                              ← add NDK/CMake config
app/src/main/cpp/CMakeLists.txt                   ← NEW
app/src/main/cpp/lsposed_detect.cpp               ← NEW (main implementation)
app/src/main/kotlin/com/anycheck/app/detection/
  NativeDetector.kt                               ← NEW
  XposedDetector.kt                               ← add checkNativeLSPosedDetection()
app/src/main/res/values/strings.xml               ← add 7 new strings
app/src/main/res/values-zh/strings.xml            ← add 7 new ZH strings
```

---

## Important Notes for Implementer

1. **JNI function naming**: The `external fun` in Kotlin must match the C function
   name exactly.  If the Kotlin function is `detectLSPosedJni()` in
   `NativeDetector` object, the C name is
   `Java_com_anycheck_app_detection_NativeDetector_detectLSPosedJni`.

2. **`nullptr` in C++17**: Use `nullptr` not `NULL`.  Include `<cstddef>` if
   needed.

3. **`RTLD_NOLOAD` availability**: Present in Android NDK `dlfcn.h` since API 21.
   `minSdk = 26` so no version guard needed.

4. **`__system_properties.h`**: Available in NDK; include
   `<sys/system_properties.h>`.

5. **`fork()` guard**: If implementing N7, wrap in
   `if (Build.VERSION.SDK_INT >= 26)` (already `minSdk=26`) and use a 100ms
   timeout in the parent's `waitpid()` call.

6. **`libreveny.so` in repo root**: This file should be **removed** from the
   repository root in a follow-up commit — it is a ~2.4 MB encrypted binary
   blob with no direct use in this project.  It is NOT a standard ELF and
   cannot be loaded by `System.loadLibrary()`.

7. **Proguard**: Add to `proguard-rules.pro`:
   ```
   -keep class com.anycheck.app.detection.NativeDetector { *; }
   ```
   to prevent the `external` function name from being obfuscated.

---

## Why Existing Kotlin Checks May Miss LSPosed

| Kotlin check | How LSPlant can defeat it |
|---|---|
| `File("/data/misc/lspd").exists()` | LSPlant can hook `File.exists()` or the underlying `libcore` JNI |
| `/proc/self/maps` via `BufferedReader` | `fopen` in libc is hooker-patchable via GOT |
| `Class.forName("de.robv.android.xposed.XposedBridge")` | Class loading can be intercepted |
| Stack trace inspection | LSPosed removes its frames from `Throwable.getStackTrace()` |
| Package manager queries | LSPosed + HMA together hide packages |

The native probes using raw `open(2)` syscalls and `dlopen(RTLD_NOLOAD)` are
**significantly harder** to bypass because:
* Hooking `open(2)` requires kernel-level or very low-level ptrace tricks.
* `RTLD_NOLOAD` queries the dynamic linker's already-loaded list, which
  LSPlant does not modify.
* `__system_property_get()` goes straight to the bionic property system.
