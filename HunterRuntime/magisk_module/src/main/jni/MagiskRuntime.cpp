//
// Created by Zhenxi on 2023/11/24.
//
#include <dlfcn.h>
#include <unistd.h>
#include <string>
#include <iterator>
#include <fcntl.h>
#include <cerrno>
#include <sys/stat.h>
#include <fstream>
#include <sstream>
#include <asm-generic/mman.h>
#include <sys/mman.h>
#include <sched.h>      /* CLONE_*,  */
#include <sys/types.h>  /* pid_t, */
#include <sys/ptrace.h> /* ptrace(1), PTRACE_*, */
#include <sys/types.h>  /* waitpid(2), */
#include <sys/wait.h>   /* waitpid(2), */
#include <sys/utsname.h> /* uname(2), */
#include <unistd.h>     /* fork(2), chdir(2), getpid(2), */
#include <linux/seccomp.h>

#include "MagiskRuntime.h"
#include "ZhenxiLog.h"
#include "macros.h"
#include "DexLoader.h"
#include "well_known_classes.h"
#include "adapter.h"
#include "version.h"
#include "binder.h"
#include "jni_helper.hpp"

#include "profile_saver.h"
#include "elf_util.h"
#include "lsplant.hpp"
#include "HookUtils.h"
#include "fileUtils.h"

#include "Test.h"

using namespace ZhenxiRuntime;
using namespace lsplant;

static bool disabled = false;

#define JAVA_MAIN_STUB "com.runtime.magisk.MainStub"
#define JAVA_MAIN_ENGINE "com.runtime.magisk.MagiskEngine"

MagiskRuntime *MagiskRuntime::instance = nullptr;

std::vector<char> *MagiskRuntime::dex_data = nullptr;

//int MagiskRuntime::sharedJar = -1;

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

jobject
RposedBridge_hook0(JNIEnv *env, [[maybe_unused]] jclass clazz, jobject context,
                   jobject originalMethod, jobject callbackMethod) {
    return lsplant::Hook(env, originalMethod, context, callbackMethod);
}

jboolean
RposedBridge_unhook(JNIEnv *env, [[maybe_unused]] jclass clazz, jobject originalMethod) {
    return lsplant::UnHook(env, originalMethod);
}

jboolean
RposedBridge_instanceOf(JNIEnv *env,
                        [[maybe_unused]] jclass thiz,
                        jobject obj,
                        jclass clazz) {
    return env->IsInstanceOf(obj, clazz);
}

[[maybe_unused]] jboolean
RposedBridge_Deoptimize(JNIEnv *env,
                        [[maybe_unused]] jclass thiz,
                        jobject method) {

    return lsplant::Deoptimize(env, method);
}

void
init_lsplant(JNIEnv *env) {
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
            .generated_class_name = "setting",
            .generated_source_name = "android"
    };

    bool isInitLsp = lsplant::Init(env, initInfo);
    if (!isInitLsp) {
        LOG(INFO) << "zhenxi runtime JNI_OnLoad init lsplant error !!! ";
        return;
    }

    auto RposedBridgeClazz = JNIHelper::FindClassFromClassLoader(env,
                                                                 "com/hunter/api/rposed/RposedBridge",
                                                                 MagiskRuntime::getInstance()->main_classloader,
                                                                 WellKnownClasses::java_lang_ClassLoader_loadClass);
    JNINativeMethod RpMethods[] = {
            {"hook0",      "(Ljava/lang/Object;Ljava/lang/reflect/Member;Ljava/lang/reflect/Method;)Ljava/lang/reflect/Method;", (void *) RposedBridge_hook0},
            {"unhook",     "(Ljava/lang/reflect/Member;)Z",                                                                      (void *) RposedBridge_unhook},
            {"instanceOf", "(Ljava/lang/Object;Ljava/lang/Class;)Z",                                                             (void *) RposedBridge_instanceOf}
            //{"Deoptimize", "(Ljava/lang/reflect/Member;)Z",                                                                      (void *) RposedBridge_Deoptimize}
    };

    if (env->RegisterNatives(RposedBridgeClazz, RpMethods,
                             sizeof(RpMethods) / sizeof(RpMethods[0])) <
        0) {
        return;
    }

}

void MagiskEngine_systemServerNativeInit(JNIEnv *env,
                                         [[maybe_unused]] jclass clazz) {
    LOGI("MagiskEngine_systemServerNativeInit is call ")
    //anti jit
    disable_profile_saver();
    //init xposed
    init_lsplant(env);

    LOGI("MagiskEngine_systemServerNativeInit init finish ~ ")
}
void
MagiskEngine_startMethodTrace(JNIEnv *env, jclass clazz) {
    //invokePrintf::HookJNIInvoke(env, nullptr);
}

void
MagiskEngine_stopMethodTrace(JNIEnv *env, jclass clazz) {
    //invokePrintf::Stop();
}

void
MagiskEngine_clientTestMethod(JNIEnv *env, jclass clazz,jobject context) {

}

void
MagiskEngine_serviceMockDrm(JNIEnv *env, jclass clazz,jstring mockDrmValue) {
    //sFingerprint::mockDrmInfo("111");
}
static bool isRegisterBaseNative = false;


void MagiskRuntime::initMagiskRuntime(JNIEnv *env) {
    Binder::Init(env);
    WellKnownClasses::Init(env);
}

/**
 * load dex in memory
 */
std::vector<char> *MagiskRuntime::PreloadDexData() {
    if (dex_data) return dex_data;
    std::string core_jar_file(kCoreJarFile);
    std::ifstream is(core_jar_file, std::ios::binary);
    if (!is.good()) {
        LOGE("Cannot open the core dex file:%s %s", kCoreJarFile.c_str(), strerror(errno))
        return nullptr;
    }
    dex_data = new std::vector<char>{std::istreambuf_iterator<char>(is),
                                     std::istreambuf_iterator<char>()};
    is.close();
    if (dex_data == nullptr) {
        LOGE("loadDexFromMemory error ,not find dex file in memory")
        return nullptr;
    }
    LOGI("load dex into memory success ! %lu", dex_data->size())
    return dex_data;
}

/**
 * init runtime need class
 */
bool MagiskRuntime::initRuntimeClass(JNIEnv *env, jobject classloader) {
    //2、load class
    jclass main_class = JNIHelper::FindClassFromClassLoader(env,
                                                            JAVA_MAIN_STUB, classloader,
                                                            WellKnownClasses::java_lang_ClassLoader_loadClass);
    if (JNIHelper::ExceptionCheck(env)) {
        LOGE("initRuntimeClazz %s not found", JAVA_MAIN_STUB)
        return false;
    }
    jclass engine_class = JNIHelper::FindClassFromClassLoader(env, JAVA_MAIN_ENGINE, classloader,
                                                              WellKnownClasses::java_lang_ClassLoader_loadClass);
    if (JNIHelper::ExceptionCheck(env)) {
        LOGE("initRuntimeClazz %s not found", JAVA_MAIN_ENGINE)
        return false;
    }
    instance->java_main_class = (jclass) env->NewGlobalRef(main_class);
    instance->java_engine_class = (jclass) env->NewGlobalRef(engine_class);
    instance->main_classloader = env->NewGlobalRef(classloader);
    //LOGI("LoadDexAndClazz get class success !!!! ")
    return true;
}

/**
 * load dex & main class
 */
bool MagiskRuntime::LoadDexForService(JNIEnv *env) {
    //1、load dex & classloader
    //PreloadDexData();
    //jobject classloader = DexLoader::FromMemory(env, dex_data->data(), dex_data->size());
    //服务端优先使用pathclassloader
    jobject classloader = DexLoader::FromFile(env,kCoreJarFile);
    if (classloader == nullptr) {
        LOGE("LoadDexAndClazz get classloader error ")
        delete dex_data;
        return false;
    }
    LOGI("LoadDexAndClazz get classloader success !!!! ")
    if (WellKnownClasses::java_lang_ClassLoader_loadClass == nullptr) {
        LOGE("LoadDexAndClazz java_lang_ClassLoader_loadClass == null ")
        return false;
    }
    if (!initRuntimeClass(env, classloader)) {
        LOGE("LoadDexAndClazz initRuntimeClass error ")
        return false;
    }
    //LOGI("LoadDexAndClazz get method id success !!!! ")
    return true;
}


//std::string get_pid_by_name(const std::string& processName) {
//    std::string cmd = "ps -ef | grep " + processName + " | grep -v grep | awk '{print $2}'";
//    std::array<char, 128> buffer;
//    std::string result;
//    // 使用unique_ptr管理popen提供的FILE*
//    std::unique_ptr<FILE, decltype(&pclose)> pipe(popen(cmd.c_str(), "r"), pclose);
//    if (!pipe) {
//        return {};
//    }
//    // 读取数据
//    while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
//        result += buffer.data();
//    }
//    return result;
//}
/**
 * ptrace注入到指定进程,防止内核init 孵化出来的进程不通过zygisk执行
 */
bool MagiskRuntime::ptraceProcess(std::string& processName){
//    const string &pid = get_pid_by_name(processName);
//    if(pid.empty()) {
//        LOGE("MagiskRuntime::ptraceProcess pid.empty() ")
//        return false;
//    }
//    LOGI("MagiskRuntime::ptraceProcess pid %s ",pid.c_str())
//    pid_t child = fork();
//    if (child < 0) {
//        LOGE("MagiskRuntime::ptraceProcess svc fork() error ")
//        return false;
//    }
//    if (child == 0) {
//        //fork process
//        long status = ptrace(PTRACE_ATTACH, std::stoi(pid), NULL, NULL);
//        if (status != 0) {
//            //attch失败
//            LOGE(">>>>>>>>> error: attach target process %lu %s", status, strerror(errno))
//            return false;
//        }
//
//    } else{
//
//    }
    return false;
}


/**
 * 初始化Magisk Runtime实例,用于保存变量
 */
void MagiskRuntime::init(JNIEnv *env) {
    instance = getInstance();
    //init java_vm
    CHECK_FOR_JNI(env->GetJavaVM(&instance->java_vm) == JNI_OK, "env->GetJavaVM failed");
    initMagiskRuntime(env);

}


bool
MagiskRuntime::FindEntryMethods(JNIEnv *env, jclass main_class,
                                bool isApp,
                                jobject client_binder,
                                bool isMainTagApk,
                                jstring processName
                                ) {
    if (isApp) {
        instance->onAppProcessStart = env->GetStaticMethodID(main_class,
                                                             "onAppProcessStart",
                                                             "(Landroid/os/IBinder;ZLjava/lang/String;)V"
        );
        if (instance->onAppProcessStart == nullptr) {
            LOGE("Method onAppProcessStart() not found")
            JNIHelper::AssertAndClearPendingException(env);
            return false;
        }
        env->CallStaticVoidMethod(instance->java_main_class,
                                  instance->onAppProcessStart,
                                  client_binder,
                                  isMainTagApk?JNI_TRUE:JNI_FALSE,
                                  processName
                                  );
        if (UNLIKELY(JNIHelper::ExceptionCheck(env))) {
            LOGE("Failed to call java callback method onAppProcessStart")
            return false;
        }
    } else {
        instance->onSystemServerStart = env->GetStaticMethodID(main_class, "onSystemServerStart",
                                                               "()V");
        if (instance->onSystemServerStart == nullptr) {
            LOGE("Method onSystemServerStart() not found")
            JNIHelper::AssertAndClearPendingException(env);
            return false;
        }
        env->CallStaticVoidMethod(instance->java_main_class, instance->onSystemServerStart,JNI_FALSE);
        if (UNLIKELY(JNIHelper::ExceptionCheck(env))) {
            //LOGE("Failed to call java callback method onSystemServerStart")
            return false;
        }
    }
//    LOGI("get method %s() success !",isApp?"onAppProcessStart":"onSystemServerStart")
    return true;
}


bool MagiskRuntime::ShouldSkip(bool is_child_zygote, int uid) {
    if (UNLIKELY(disabled)) return true;

    if (UNLIKELY(ShouldSkipUid(uid))) {
        LOGW("Skipping this process because it is"
             " isolated service, RELTO updater or webview zygote (%s)",getprogname())
        return true;
    }

    if (UNLIKELY(is_child_zygote)) {
        // child zygote is not allowed to do binder transaction, so our binder call will crash it
        LOGW("Skipping this process because it is a child zygote")
        return true;
    }
    return false;
}

void MagiskRuntime::OnModuleLoaded() {
    //启动检测基础文件检测判断是否存在
    disabled = MagiskRuntime::ShouldDisable();
    if (disabled) {
        LOGE(">>>>>>>>>>>>>> OnModuleLoaded not find runtime file !!!")
    } else {
        //LOGE(">>>>>>>>>>>>>> magisk runtime init success ! ")
    }
}





bool MagiskRuntime::IsDisabled() {
    return disabled;
}

bool MagiskRuntime::ShouldDisable() {

//    if (access(kBaseDir, F_OK) != 0) {
//        LOGE("runtime framework is broken: base directory is not exist! %s",kBaseDir);
//        return true;
//    }

    if (access(kCoreJarFile.c_str(), F_OK) != 0) {
        LOGE("runtime framework is broken: core jar is not exist! %s ", kCoreJarFile.c_str())
        return true;
    }

//    if (access(kDisableFile, F_OK) == 0) {
//        LOGW("runtime is disabled: disable flag file is exist! %s",kDisableFile);
//        return true;
//    }

    return false;
}


JNIEnv *MagiskRuntime::GetJNIEnv() {
    JNIEnv *env = nullptr;
    CHECK(java_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK,
          "java_vm->GetEnv failed");
    CHECK(env != nullptr, "env == nullptr");
    return env;
}

MagiskRuntime::~MagiskRuntime() {
    if (java_main_class != nullptr) {
        GetJNIEnv()->DeleteGlobalRef(java_main_class);
    }
    if (java_engine_class != nullptr) {
        GetJNIEnv()->DeleteGlobalRef(java_engine_class);
    }
    if (main_classloader != nullptr) {
        GetJNIEnv()->DeleteGlobalRef(main_classloader);
    }
}

/**
 * 客户端调用方法
 * 请求服务端,得到多个fd和size,然后加载dex
 */
void MagiskRuntime::PostMainApp(JNIEnv *env,jstring processName,bool isMain) {
    if (UNLIKELY(instance == nullptr)) return;
    jobject service = Binder::GetBinder(env);
    if (service == nullptr) {
        LOGE("PostMainApp get service binder error ")
        return;
    }
    const std::vector<std::tuple<int, size_t>> &dex = Binder::GetDexInfo(env);
    jobject classloader = DexLoader::FromDexFd(env,dex);
    if (classloader == nullptr||!initRuntimeClass(env, classloader)) {
        LOGE("PostMainApp initRuntimeClass error ")
        return;
    }
    RegisterBaseNativeMethod(env);
    if (!FindEntryMethods(env, instance->java_main_class, true, service,isMain,processName)) {
        LOGE("PostForkSystemServer failed to find entry methods for app process")
        return;
    }
    Binder::Cleanup(env);
}

/**
 * call SystemServer onSystemServerStart
 *
 * 1、初始化公共Api
 * 2、Hook服务端逻辑,挂载我们的服务。为客户端提供dex
 * 3、调用onSystemServerStart
 */
void MagiskRuntime::PostForkSystemServer(JNIEnv *env) {
    if (instance == nullptr || env == nullptr) {
        LOGE(">>>>>>>>>>>>>> PostForkSystemServer instance == nullptr ")
        return;
    }
    if (!instance->LoadDexForService(env)) {
        LOGE("load dex failed in system_server")
        return;
    }
    RegisterBaseNativeMethod(env);
    //服务端不存在关联tag apk
    if (!FindEntryMethods(env, instance->java_main_class, false, nullptr, false, nullptr)) {
        LOGE("PostForkSystemServer failed to find entry methods for app process")
        return;
    }
}

/**
 * regiester system_service & client base method
 */
void MagiskRuntime::RegisterBaseNativeMethod(JNIEnv *env) {
    if (isRegisterBaseNative) return;
    if (instance->java_engine_class == nullptr) {
        LOGE("RegisterBaseNativeMethod instance->java_engine_class == null ")
        return;
    }
    //NativeFingerPrintEngine init
    JNINativeMethod methods[] = {
            {"systemServerNativeInit", "()V", (void *) MagiskEngine_systemServerNativeInit}
    };
    if (env->RegisterNatives(instance->java_engine_class, methods,
                             sizeof(methods) / sizeof(methods[0])) < 0) {
        return;
        LOGE("RegisterBaseNativeMethod RegisterNatives systemServerNativeInit error ")
    }
    isRegisterBaseNative = true;
}