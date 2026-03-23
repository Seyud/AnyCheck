#include <jni.h>
#include <string>
#include <vector>
#include <cstring>

#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <sys/system_properties.h>
#include <android/log.h>

#define LOG_TAG "anycheck_native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// N1 — Raw /proc/self/maps scan
// Uses open(2)/read(2) syscalls — NOT fopen() — to resist GOT-level hooks.
// ---------------------------------------------------------------------------
static std::vector<std::string> scanMaps() {
    static const char *NEEDLES[] = {
        "lsposed", "lsplant", "liblsplant", "lspd",
        "edxposed", "libxposed-native", "liblspatch",
        "libfake-linker", nullptr
    };

    std::vector<std::string> findings;
    int fd = open("/proc/self/maps", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return findings;

    char buf[4096];
    std::string partial;
    ssize_t n;
    while ((n = read(fd, buf, sizeof(buf) - 1)) > 0) {
        buf[n] = '\0';
        partial += buf;
        size_t pos;
        while ((pos = partial.find('\n')) != std::string::npos) {
            std::string line = partial.substr(0, pos);
            partial = partial.substr(pos + 1);
            // The path is after the last space on the line
            size_t space = line.rfind(' ');
            std::string path = (space != std::string::npos) ? line.substr(space + 1) : line;
            size_t slash = path.rfind('/');
            std::string filename = (slash != std::string::npos) ? path.substr(slash + 1) : path;
            // Lower-case compare
            std::string fl = filename;
            for (char &c : fl) c = (char)tolower((unsigned char)c);
            for (int i = 0; NEEDLES[i] != nullptr; ++i) {
                if (fl.find(NEEDLES[i]) != std::string::npos) {
                    std::string entry = path.substr(0, 120);
                    bool dup = false;
                    for (const auto &f : findings) { if (f == entry) { dup = true; break; } }
                    if (!dup) findings.push_back(entry);
                    break;
                }
            }
        }
    }
    close(fd);
    return findings;
}

// ---------------------------------------------------------------------------
// N2 — dlopen(RTLD_NOLOAD) probe
// RTLD_NOLOAD succeeds (returns non-null) only if the library is already mapped.
// ---------------------------------------------------------------------------
static std::vector<std::string> probeDlopen() {
    static const char *TARGETS[] = {
        "liblsplant.so", "liblsposed.so",
        "libxposed-native.so", "liblspd.so", nullptr
    };

    std::vector<std::string> findings;
    for (int i = 0; TARGETS[i] != nullptr; ++i) {
        void *h = dlopen(TARGETS[i], RTLD_NOLOAD | RTLD_LAZY);
        if (h != nullptr) {
            findings.push_back(std::string("dlopen:") + TARGETS[i]);
            dlclose(h);
        }
    }
    return findings;
}

// ---------------------------------------------------------------------------
// N3 — dlsym(RTLD_DEFAULT) symbol search
// Looks for exported XposedBridge / LSPlant symbols in the global namespace.
// ---------------------------------------------------------------------------
static std::vector<std::string> probeDlsym() {
    static const char *SYMBOLS[] = {
        "xposedBridgeHandleHookedMethod",
        "Java_de_robv_android_xposed_XposedBridge_hookMethodNative",
        "_ZN7lsplant10InitializeEP7_JNIEnv",
        "_ZN7lsplant4HookEP7_JNIEnvP8_jobjectP10_jmethodIDS4_",
        nullptr
    };

    std::vector<std::string> findings;
    for (int i = 0; SYMBOLS[i] != nullptr; ++i) {
        void *sym = dlsym(RTLD_DEFAULT, SYMBOLS[i]);
        if (sym != nullptr) {
            findings.push_back(std::string("dlsym:") + SYMBOLS[i]);
        }
    }
    return findings;
}

// ---------------------------------------------------------------------------
// N4 — stat() path probe
// stat() on known LSPosed/Riru/Zygisk paths.
// ---------------------------------------------------------------------------
static std::vector<std::string> probePaths() {
    static const char *PATHS[] = {
        "/data/misc/lspd",
        "/data/misc/lspd/config.json",
        "/data/adb/lspd",
        "/data/adb/modules/zygisk_lsposed",
        "/data/adb/modules/riru_lsposed",
        "/data/adb/modules/lsposed",
        nullptr
    };

    std::vector<std::string> findings;
    struct stat st{};
    for (int i = 0; PATHS[i] != nullptr; ++i) {
        if (stat(PATHS[i], &st) == 0) {
            findings.push_back(std::string("stat:") + PATHS[i]);
        }
    }
    return findings;
}

// ---------------------------------------------------------------------------
// N5 — ro.dalvik.vm.native.bridge system property
// The Riru variant of LSPosed sets this to its own loader library name.
// ---------------------------------------------------------------------------
static std::string probeNativeBridge() {
    char value[PROP_VALUE_MAX] = {0};
    __system_property_get("ro.dalvik.vm.native.bridge", value);
    if (value[0] != '\0' && strcmp(value, "0") != 0) {
        return std::string("native.bridge=") + value;
    }
    return {};
}

// ---------------------------------------------------------------------------
// N6 — /proc/self/smaps Private_Dirty probe
// LSPlant patches libc.so / libart.so pages, leaving non-zero Private_Dirty
// on r-xp (executable) segments of those libraries.
// ---------------------------------------------------------------------------
static std::vector<std::string> probeSmaps() {
    std::vector<std::string> findings;
    int fd = open("/proc/self/smaps", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return findings;

    char buf[8192];
    std::string partial;
    ssize_t n;
    bool inTarget = false;
    std::string currentLib;
    while ((n = read(fd, buf, sizeof(buf) - 1)) > 0) {
        buf[n] = '\0';
        partial += buf;
        size_t pos;
        while ((pos = partial.find('\n')) != std::string::npos) {
            std::string line = partial.substr(0, pos);
            partial = partial.substr(pos + 1);
            // Detect a mapping header line (contains memory address range)
            if (line.size() > 20 && line[8] == '-') {
                inTarget = false;
                currentLib.clear();
                // Check if it's an executable mapping of libart/libc
                if (line.find("r-xp") != std::string::npos) {
                    size_t sp = line.rfind(' ');
                    std::string path = (sp != std::string::npos) ? line.substr(sp + 1) : "";
                    size_t sl = path.rfind('/');
                    std::string fname = (sl != std::string::npos) ? path.substr(sl + 1) : path;
                    if (fname == "libart.so" || fname == "libc.so") {
                        inTarget = true;
                        currentLib = fname;
                    }
                }
            } else if (inTarget && line.find("Private_Dirty:") == 0) {
                // Parse the kB value
                size_t colon = line.find(':');
                if (colon != std::string::npos) {
                    std::string valStr = line.substr(colon + 1);
                    // trim whitespace
                    size_t start = valStr.find_first_not_of(" \t");
                    if (start != std::string::npos) valStr = valStr.substr(start);
                    size_t end = valStr.find(' ');
                    if (end != std::string::npos) valStr = valStr.substr(0, end);
                    int kbVal = 0;
                    try { kbVal = std::stoi(valStr); } catch (...) {}
                    if (kbVal > 0) {
                        std::string entry = "smaps_dirty:" + currentLib + "=" + valStr + "kB";
                        bool dup = false;
                        for (const auto &f : findings) { if (f == entry) { dup = true; break; } }
                        if (!dup) findings.push_back(entry);
                    }
                }
                inTarget = false;
            }
        }
    }
    close(fd);
    return findings;
}

// ---------------------------------------------------------------------------
// JNI entry point
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jstring JNICALL
Java_com_anycheck_app_detection_NativeDetector_detectLSPosedJni(JNIEnv *env, jobject /* thiz */) {
    std::vector<std::string> allFindings;

    // N1: maps scan
    auto maps = scanMaps();
    allFindings.insert(allFindings.end(), maps.begin(), maps.end());

    // N2: dlopen probe
    auto dlo = probeDlopen();
    allFindings.insert(allFindings.end(), dlo.begin(), dlo.end());

    // N3: dlsym probe
    auto sym = probeDlsym();
    allFindings.insert(allFindings.end(), sym.begin(), sym.end());

    // N4: stat paths
    auto paths = probePaths();
    allFindings.insert(allFindings.end(), paths.begin(), paths.end());

    // N5: native bridge property
    auto bridge = probeNativeBridge();
    if (!bridge.empty()) allFindings.push_back(bridge);

    // N6: smaps Private_Dirty
    auto smaps = probeSmaps();
    allFindings.insert(allFindings.end(), smaps.begin(), smaps.end());

    // Build semicolon-separated result string
    std::string result;
    for (size_t i = 0; i < allFindings.size(); ++i) {
        if (i > 0) result += "; ";
        result += allFindings[i];
    }

    return env->NewStringUTF(result.c_str());
}
