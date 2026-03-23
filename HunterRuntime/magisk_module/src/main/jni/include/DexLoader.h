//
// Created by Zhenxi on 2023/11/24.
//

#ifndef ZHENXIRUNTIME_DEXLOADER_H
#define ZHENXIRUNTIME_DEXLOADER_H

#include <jni.h>
#include <list>

#include "ZhenxiLog.h"
#include "macros.h"

namespace ZhenxiRuntime {
    class DexLoader {
    public:
        static jobject FromMemory(JNIEnv* env, const std::string& dex, const size_t size);
        /**
         * 优先使用InMemoryDexClassLoader去加载,暂不支持低版本
         */
        static jobject FromFile(JNIEnv* env, const std::string& path);
        static jobject FromDexFd(JNIEnv* env,const std::vector<std::tuple<int, size_t>> fd_list);
    private:
//        static jobject classloader;

        static jmethodID PathClassLoader_init;
        static jclass PathClassLoader; // For Nougat only
        static jclass InMemoryDexClassLoader; // For Oreo+
        static jmethodID InMemoryDexClassLoader_init;
    };
}

#endif //ZHENXIRUNTIME_DEXLOADER_H
