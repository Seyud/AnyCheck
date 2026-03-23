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

#include "DexLoader.h"
#include "well_known_classes.h"
#include "adapter.h"
#include "version.h"
#include "xdl.h"

#include "jni_helper.hpp"

using namespace ZhenxiRuntime;
using namespace lsplant;

static bool DexLoaderInit = false;

jclass DexLoader::PathClassLoader = nullptr;
jmethodID DexLoader::PathClassLoader_init = nullptr;

jclass DexLoader::InMemoryDexClassLoader = nullptr;
jmethodID DexLoader::InMemoryDexClassLoader_init = nullptr;
//jobject DexLoader::classloader = nullptr;



jobject DexLoader::FromMemory(JNIEnv *env, const std::string& dex, const size_t size) {
    if(InMemoryDexClassLoader == nullptr){
        InMemoryDexClassLoader = WellKnownClasses::CacheClass(env,
              "dalvik/system/InMemoryDexClassLoader");
    }
    if(InMemoryDexClassLoader_init == nullptr){
        //one dex
//        InMemoryDexClassLoader_init = WellKnownClasses::CacheMethod(env,
//              InMemoryDexClassLoader,"<init>","(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V",false);
        //more dex
        InMemoryDexClassLoader_init = WellKnownClasses::CacheMethod(env,
               InMemoryDexClassLoader,"<init>","([Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V",false);
    }

    WellKnownClasses::Init(env);
    ScopedLocalRef<jobject> system_class_loader(env, env->CallStaticObjectMethod(
            WellKnownClasses::java_lang_ClassLoader,
            WellKnownClasses::java_lang_ClassLoader_getSystemClassLoader));
    ScopedLocalRef<jobject> buffer(env,env->NewDirectByteBuffer((void*)dex.c_str(), (long) size));
    jobject loader = env->NewObject(InMemoryDexClassLoader, InMemoryDexClassLoader_init,
                                    buffer.get(), system_class_loader.get());
    if (JNIHelper::ExceptionCheck(env)) {
        return nullptr;
    }
//    classloader = loader;
    return loader;
}

jobject DexLoader::FromFile(JNIEnv* env, const std::string& path) {
    if(PathClassLoader == nullptr) {
        PathClassLoader = WellKnownClasses::CacheClass(env, "dalvik/system/PathClassLoader");
    }
    if(PathClassLoader_init == nullptr) {
        PathClassLoader_init = WellKnownClasses::CacheMethod(env, PathClassLoader, "<init>",
                                                             "(Ljava/lang/String;Ljava/lang/ClassLoader;)V",
                                                             false);
    }
    ScopedLocalRef<jstring> dex_path(env,env->NewStringUTF(path.c_str()));
    ScopedLocalRef<jobject> system_class_loader(env, env->CallStaticObjectMethod(
            WellKnownClasses::java_lang_ClassLoader,
            WellKnownClasses::java_lang_ClassLoader_getSystemClassLoader));
    jobject loader = env->NewObject(PathClassLoader, PathClassLoader_init, dex_path.get(), system_class_loader.get());
    if (env->ExceptionCheck()) {
        LOGE("FromFile Failed to load dex %s", path.c_str());
        env->ExceptionDescribe();
        env->ExceptionClear();
        return nullptr;
    }
    return loader;
}

jobject DexLoader::FromDexFd(JNIEnv *env,
                             const std::vector<std::tuple<int, size_t>> fd_list) {
    if (fd_list.size() == 0) {
        LOGE("DexLoader::FromDexFd fd<=0")
        return nullptr;
    }
    jobjectArray byteBuffArray =
            env->NewObjectArray(fd_list.size(),env->FindClass("java/nio/ByteBuffer"), NULL);

    for(int i=0;i<fd_list.size();i++){
        std::tuple<int, size_t> fd_info =  fd_list.at(i);
        auto fd = std::get<0>(fd_info);
        auto size = std::get<1>(fd_info);
        //服务端分配的只可读,不可写
        //已经是memfd:jit-cache (deleted)
        auto *addr = mmap(nullptr, size, PROT_READ, MAP_SHARED, fd, 0);
        //auto *addr = mmap(nullptr, size, PROT_READ, MAP_ANONYMOUS | MAP_SHARED, fd, 0);
        if (addr == MAP_FAILED) {
            LOGE("DexLoader::FromDexFd mmap error %s ", strerror(errno))
            return nullptr;
        }
        //get byte buff
        ScopedLocalRef<jobject> buffer(env,env->NewDirectByteBuffer(addr, (long) size));
        env->SetObjectArrayElement(byteBuffArray,i,buffer.get());
    }
    if(InMemoryDexClassLoader == nullptr){
        InMemoryDexClassLoader = WellKnownClasses::CacheClass(env,
              "dalvik/system/InMemoryDexClassLoader");
    }
    if(InMemoryDexClassLoader_init == nullptr){
        //more dex
        InMemoryDexClassLoader_init = WellKnownClasses::CacheMethod(env,
              InMemoryDexClassLoader,"<init>","([Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V",false);
    }
    WellKnownClasses::Init(env);
    ScopedLocalRef<jobject> system_class_loader(env, env->CallStaticObjectMethod(
            WellKnownClasses::java_lang_ClassLoader,
            WellKnownClasses::java_lang_ClassLoader_getSystemClassLoader));
    jobject loader = env->NewObject(InMemoryDexClassLoader, InMemoryDexClassLoader_init,
                                    byteBuffArray, system_class_loader.get());
    if (JNIHelper::ExceptionCheck(env)) {
        return nullptr;
    }
    return loader;
}




