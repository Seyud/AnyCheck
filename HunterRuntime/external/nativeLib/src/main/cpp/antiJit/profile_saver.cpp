#include "profile_saver.h"
#include "logging.h"
#include "ZhenxiLog.h"
#include "xdl.h"
#include "libpath.h"
#include "adapter.h"
#include "HookUtils.h"

static void *backup = nullptr;
bool replace_process_profiling_info() {
    LOGD("Ignoring profile saver request");
    return true;
}

/**
 * disable jit
 */
bool disable_profile_saver() {
    if (backup) {
        LOGW("disableProfileSaver called multiple times - It is already disabled.");
        return true;
    }
    //LOGE("start disable_profile_saver ")
    // MIUI moment, see https://github.com/canyie/pine/commit/ef0f5fb08e6aa42656065e431c65106b41f87799
    auto process_profiling_info = getSymCompat(getlibArtPath().c_str(),
                            "_ZN3art12ProfileSaver20ProcessProfilingInfoEbPtb");
    if (!process_profiling_info) {
        const char *symbol;
        if (get_sdk_level() < 26) {
            // https://android.googlesource.com/platform/art/+/nougat-release/runtime/jit/profile_saver.cc#270
            symbol = "_ZN3art12ProfileSaver20ProcessProfilingInfoEPt";
        } else if (get_sdk_level() < 31) {
            // https://android.googlesource.com/platform/art/+/android11-release/runtime/jit/profile_saver.cc#514
            symbol = "_ZN3art12ProfileSaver20ProcessProfilingInfoEbPt";
        } else {
            // https://android.googlesource.com/platform/art/+/android12-release/runtime/jit/profile_saver.cc#823
            symbol = "_ZN3art12ProfileSaver20ProcessProfilingInfoEbbPt";
        }
        process_profiling_info = getSymCompat(getlibArtPath().c_str(),symbol);
    }

    if (!process_profiling_info) {
        LOGE("Failed to disable ProfileSaver: ProfileSaver::ProcessProfilingInfo not found");
        return false;
    }
    bool ret = HookUtils::Hooker(process_profiling_info,
                                 reinterpret_cast<void *>(replace_process_profiling_info),
                                 reinterpret_cast<void **>(&backup));
    if (ret) {
        LOGI("hook profile save disabled jit success");
        return true;
    } else {
        LOGE("failed to hook disable jit profile save");
        return false;
    }
}
