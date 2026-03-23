//
// Created by Zhenxi on 2023/11/24.
//

#ifndef ZHENXIRUNTIME_MAGISKRUNTIME_H
#define ZHENXIRUNTIME_MAGISKRUNTIME_H

#include <vector>
#include <string>

#include "adapter.h"
#include "version.h"
#include "macros.h"
#include "mylibc.h"

namespace ZhenxiRuntime {


    class MagiskRuntime {

    public:
        std::string kBaseDir = "/data/misc/runtime/";;
        std::string kCoreJarFile = std::string("/system/framework/") + RUNTIME_JAR_NAME + ".jar";;
        std::string kDisableFile = "/data/misc/runtime/disable";;

        static MagiskRuntime *getInstance() {
            if (instance == nullptr) {
                instance = new MagiskRuntime();
            }
            return instance;
        }

        /**
         * Return true if the process should be skipped
         */
        static bool ShouldSkip(bool is_child_zygote, int uid);

        /**
         * runtime base dir check
         */
        bool ShouldDisable();

        bool ptraceProcess(std::string &processName);

        void init(JNIEnv *env);

        void PostForkSystemServer(JNIEnv *env);

        void PostMainApp(JNIEnv *env, jstring processName, bool isMain);

        void OnModuleLoaded();


        void setSharedJar(int fd) {
            sharedJar = fd;
        }

        JavaVM *GetJavaVM() {
            return java_vm;
        }

        JNIEnv *GetJNIEnv();

        bool IsDisabled();

        int sharedJar;

        jobject main_classloader = nullptr;
        jclass java_main_class = nullptr;
        jclass java_engine_class = nullptr;

        jmethodID onSystemServerStart = nullptr;
        jmethodID onAppProcessStart = nullptr;

        std::vector<char> *PreloadDexData();

        /**
         * root fd
         */
        int rootClient = -1;
    private:
        /**
         * dex data in memory
         */
        static std::vector<char> *dex_data;
        /**
         * private instance
         */
        static MagiskRuntime *instance;

        JavaVM *java_vm;




        /**
         * hide create
         */
        MagiskRuntime() {

        }

        ~MagiskRuntime();

        /**
         * delete copy and set
         */
        DISALLOW_COPY_AND_ASSIGN(MagiskRuntime);

        /**
         * init base api
         */
        static void initMagiskRuntime(JNIEnv *env);

        static bool ShouldSkipUid(int uid) {
            // TODO: Get these uids through java world
            int app_id = uid % 100000;
            if (app_id >= 90000) {
                // Isolated process
                return true;
            }
            if (app_id == 1037) {
                // RELRO updater
                return true;
            }
            if (get_sdk_level() >= ANDROID_O) {
                uid_t kWebViewZygoteUid = get_sdk_level() >= ANDROID_P ? 1053 : 1051;
                if (uid == kWebViewZygoteUid) {
                    // WebView zygote
                    return true;
                }
            }
            return false;
        }

        static void RegisterBaseNativeMethod(JNIEnv *env);


        bool LoadDexForService(JNIEnv *env);

        static bool initRuntimeClass(JNIEnv *env, jobject classloader);

        static bool FindEntryMethods(JNIEnv *env, jclass main_class,
                                     bool isApp, jobject client_binder, bool isMainTagApk,
                                     jstring processName
        );

    };
}


#endif //ZHENXIRUNTIME_MAGISKRUNTIME_H
