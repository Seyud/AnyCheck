//
// Created by Zhenxi on 2022/10/16.
//

#include <jni.h>
#include <set>
#include <iostream>
#include <string>
#include <cassert>
#include <chrono>
#include <functional>
#include <iomanip>
#include <string>
#include <map>
#include <list>
#include <cstring>
#include <cstdio>
#include <regex>
#include <cerrno>
#include <climits>
#include <iostream>
#include <fstream>
#include <adapter.h>


#include "libpath.h"
#include "logging.h"
#include "ZhenxiLog.h"
#include "HookUtils.h"
#include "parse.h"
#include "elf_util.h"

#include "JnitraceForC.h"
#include "stringHandler2.h"
#include "testHandler.h"

#include "invokePrintf.h"
#include "linkerHandler.h"
#include "common_macros.h"
#include "IORelocator.h"
#include "SandboxFs.h"


#include <lsplant.hpp>


using namespace ZhenxiRunTime;

jclass NativiEngineClazz;

using namespace std;


void NativeAnalysis(JNIEnv *env, jclass type, jobject filterList, jstring filepath) {
    const std::list<string> &filter_list = parse::jlist2clist(env, filterList);
    const std::list<string> forbid_list{CORE_SO_NAME};


}


jobject getNativeHideItemList(JNIEnv *env, jclass clazz) {
    list<string> linkerMatchItem = {
            CORE_SO_NAME, "libart.so", "liblsplant.so"
    };


    std::list<std::string> marks;
    auto loadList = ZhenxiRunTime::linkerHandler::getlinkerLoadList();

    for (const auto &item: loadList) {
        auto it = std::find_if(std::begin(linkerMatchItem),
                               std::end(linkerMatchItem),
                               [&item](const std::string &mark) {
                                   return StringUtils::endsWith(item, mark);
                               });
        if (it != std::end(linkerMatchItem)) {
            marks.emplace_back(item);
        }
    }
    // 假设 env 是一个已经定义的环境变量
    return parse::clist2jlist(env, marks);
}

void
NativeEngine_hideMapsMarks(JNIEnv *env, jclass clazz,
                           jobject list, jstring package_name) {
    void *hideMaps = dlopen(EXTRA_SO_NAME, RTLD_NOW);
    if (hideMaps == nullptr) {
        LOGE("hide maps item get %s  == null ", EXTRA_SO_NAME)
        return;
    }
    void *sym = dlsym(hideMaps, "RuntimeHideMapsMarks");
    if (sym == nullptr) {
        LOGE("hide maps item get sym %s  == null ", EXTRA_SO_NAME)
        return;
    }
    auto hideMaps_sym = (void (*)(JNIEnv *env, jobject list, jstring packageName)) sym;
    hideMaps_sym(env, list, package_name);
    dlclose(hideMaps);
}

void
NativeEngine_test(JNIEnv *env, jclass clazz, jobject context) {
    const list<std::string> &loadList =
            ZhenxiRunTime::linkerHandler::getlinkerLoadList();
    for (const auto &item: loadList) {
        LOGE("linker load list info %s ", item.c_str())
    }
}


void
NativeEngine_hideLinkerMarks(JNIEnv *env,
                             jclass clazz,
                             jobject list) {
    const auto &clist = parse::jlist2clist(env, list);
    std::set<std::string_view> marks{clist.begin(), clist.end()};
    PRINTF_LIST(marks);
    ZhenxiRunTime::linkerHandler::removelinkerList(marks);
    //PRINTF_LIST_LINE(ZhenxiRunTime::linkerHandler::getlinkerLoadList());
}


void
NativeEngine_nativeEnableIORedirect(JNIEnv *env, jclass,
                                    jstring soPath32,
                                    jstring soPath64,
                                    jstring nativePath, jint apiLevel,
                                    jint preview_api_level) {

    ScopeUtfString so_path_32(soPath32);
    ScopeUtfString so_path_64(soPath64);
    ScopeUtfString native_path(nativePath);
    IOUniformer::startUniformer(env,
                                so_path_32.c_str(),
                                so_path_64.c_str(),
                                native_path.c_str(),
                                apiLevel,
                                preview_api_level);
}


void
NativeEngine_nativeIOWhitelist(JNIEnv *env, jclass jclazz, jstring _path) {
    ScopeUtfString path(_path);
    IOUniformer::whitelist(path.c_str());
}


void
NativeEngine_nativeIOForbid(JNIEnv *env, jclass jclazz, jstring _path) {
    ScopeUtfString path(_path);
    IOUniformer::forbid(path.c_str());
}


void
NativeEngine_nativeIORedirect(JNIEnv *env, jclass jclazz,
                              jstring origPath,
                              jstring newPath) {
    ScopeUtfString orig_path(origPath);
    ScopeUtfString new_path(newPath);
    IOUniformer::redirect(orig_path.c_str(), new_path.c_str());

}


jstring
NativeEngine_nativeGetRedirectedPath(JNIEnv *env, jclass jclazz,
                                     jstring origPath) {
    ScopeUtfString orig_path(origPath);
    char buffer[PATH_MAX];
    const char *redirected_path = IOUniformer::query(orig_path.c_str(), buffer, sizeof(buffer));
    if (redirected_path != nullptr) {
        return env->NewStringUTF(redirected_path);
    }
    return nullptr;
}


jstring
NativeEngine_nativeReverseRedirectedPath(JNIEnv *env, jclass jclazz,
                                         jstring redirectedPath) {
    ScopeUtfString redirected_path(redirectedPath);
    char buffer[PATH_MAX];
    const char *orig_path = IOUniformer::reverse(redirected_path.c_str(), buffer, sizeof(buffer));
    return env->NewStringUTF(orig_path);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_hunter_api_rposed_RposedBridge_hook0(JNIEnv *env, jclass clazz, jobject context,
                                              jobject originalMethod, jobject callbackMethod) {
    return lsplant::Hook(env, originalMethod, context, callbackMethod);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_hunter_api_rposed_RposedBridge_unhook(JNIEnv *env, jclass clazz, jobject originalMethod) {
    return lsplant::UnHook(env, originalMethod);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_hunter_api_rposed_RposedBridge_instanceOf(JNIEnv *env,
                                                   jclass thiz,
                                                   jobject obj,
                                                   jclass clazz) {
    return env->IsInstanceOf(obj, clazz);
}


static JNINativeMethod HunterRuntimeNativeMethods[] = {
        {"Analysis",                    "(Ljava/util/ArrayList;Ljava/lang/String;)V",                  (void *) NativeAnalysis},
        {"getNativeHideItemList",       "()Ljava/util/ArrayList;",                                     (void *) getNativeHideItemList},
        {"hideMapsMarks",               "(Ljava/util/ArrayList;Ljava/lang/String;)V",                  (void *) NativeEngine_hideMapsMarks},
        {"Test",                        "(Landroid/content/Context;)V",                                (void *) NativeEngine_test},
        {"hideLinkerMarks",             "(Ljava/util/ArrayList;)V",                                    (void *) NativeEngine_hideLinkerMarks},

        {"nativeEnableIORedirect",      "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;II)V", (void *) NativeEngine_nativeEnableIORedirect},
        {"nativeReverseRedirectedPath", "(Ljava/lang/String;)Ljava/lang/String;",                      (void *) NativeEngine_nativeReverseRedirectedPath},
        {"nativeGetRedirectedPath",     "(Ljava/lang/String;)Ljava/lang/String;",                      (void *) NativeEngine_nativeGetRedirectedPath},
        {"nativeIORedirect",            "(Ljava/lang/String;Ljava/lang/String;)V",                     (void *) NativeEngine_nativeIORedirect},
        {"nativeIOWhitelist",           "(Ljava/lang/String;)V",                                       (void *) NativeEngine_nativeIOWhitelist},
        {"nativeIOForbid",              "(Ljava/lang/String;)V",                                       (void *) NativeEngine_nativeIOForbid}
};


void *inlineHooker(void *targetFunc, void *replaceFunc) {
    auto pageSize = sysconf(_SC_PAGE_SIZE);
    auto funcAddress = ((uintptr_t) targetFunc) & (-pageSize);
    mprotect((void *) funcAddress, pageSize, PROT_READ | PROT_WRITE | PROT_EXEC);

    void *originalFunc;
    if (HookUtils::Hooker((void *) targetFunc, (void *) replaceFunc, (void **) &originalFunc)) {
        return originalFunc;
    }
    return nullptr;
}

bool inlineUnHooker(void *originalFunc) {
    return HookUtils::unHook(originalFunc);
}


JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *_vm, void *) {

    JNIEnv *env = nullptr;
    mVm = _vm;
    if (_vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    mEnv = env;
    NativiEngineClazz = mEnv->FindClass("com/hunter/runtime/NativiEngine");


    if (env->RegisterNatives(NativiEngineClazz, HunterRuntimeNativeMethods,
                             sizeof(HunterRuntimeNativeMethods) /
                             sizeof(HunterRuntimeNativeMethods[0])) < 0) {
        return JNI_ERR;
    }

    SandHook::ElfImg art("libart.so");
    lsplant::InitInfo initInfo{
            .inline_hooker = inlineHooker,
            .inline_unhooker = inlineUnHooker,
            .art_symbol_resolver = [&art](std::string_view symbol) -> void * {
                return art.getSymbAddress(symbol);
            },
            .art_symbol_prefix_resolver = [&art](auto symbol) {
                return art.getSymbPrefixFirstOffset(symbol);
            },
    };

    bool isInitLsp = lsplant::Init(env, initInfo);
    if (!isInitLsp) {
        LOG(INFO) << "hunter runtime JNI_OnLoad init lsplant error !!! ";
        return JNI_ERR;
    }


    LOG(INFO) << "hunter runtime JNI_OnLoad init end ,init sucess !   ";


    return JNI_VERSION_1_6;
}
