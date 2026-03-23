//
// Created by zhenxi on 2022/4/30.
//

#include "libpath.h"
#include "version.h"
#include "adapter.h"


string getlibArtPath() {
    string art = {};
#if defined(__aarch64__)
    if (get_sdk_level() >= ANDROID_R) {
        art = "/apex/com.android.art/lib64/libart.so";
    } else if (get_sdk_level() >= ANDROID_Q) {
        art = "/apex/com.android.runtime/lib64/libart.so";
    } else {
        art = "/system/lib64/libart.so";
    }
#else
    if (get_sdk_level() >= ANDROID_R) {
        art = "/apex/com.android.art/lib/libart.so";
    } else if (get_sdk_level() >= ANDROID_Q) {
        art = "/apex/com.android.runtime/lib/libart.so";
    } else {
        art = "/system/lib/libart.so";
    }
#endif

    return art;
}

string getLinkerPath() {
    string linker;
    //get_sdk_level 是dlfc自己实现的方法
    //android_get_device_api_level是系统方法,低版本的NDK没有此方法。
#if defined(__aarch64__)
    if (get_sdk_level() >= ANDROID_R) {
        linker = "/apex/com.android.runtime/bin/linker64";
    } else if (get_sdk_level() >= ANDROID_Q) {
        linker = "/apex/com.android.runtime/bin/linker64";
    } else {
        linker = "/system/bin/linker64";
    }
#else
    if (get_sdk_level() >= ANDROID_R) {
        linker = "/apex/com.android.runtime/bin/linker";
    } else if (get_sdk_level() >= ANDROID_Q) {
        linker = "/apex/com.android.runtime/bin/linker";
    } else {
        linker = "/system/bin/linker";
    }
#endif

    return linker;
}

//这里面包含了一些 对string操作的方法
string getlibcPlusPath() {
    string libc;
#if defined(__aarch64__)
    libc = "/system/lib64/libstdc++.so";
#else
    libc = "/system/lib/libstdc++.so";

#endif
    return libc;
}

string getlibcPath() {
    string libc = {};

#if defined(__aarch64__)
    if (get_sdk_level() >= ANDROID_R) {
        libc = "/apex/com.android.runtime/lib64/bionic/libc.so";
    } else if (get_sdk_level() >= ANDROID_Q) {
        libc = "/apex/com.android.runtime/lib64/bionic/libc.so";
    } else {
        libc = "/system/lib64/libc.so";
    }
#else
    if (get_sdk_level() >= ANDROID_R) {
        libc = "/apex/com.android.runtime/lib/bionic/libc.so";
    } else if (get_sdk_level() >= ANDROID_Q) {
        libc = "/apex/com.android.runtime/lib/bionic/libc.so";
    } else {
        libc = "/system/lib/libc.so";
    }
#endif
    return libc;
}

string getMediaPath() {
    string libc;

#if defined(__aarch64__)
    libc = "/system/lib64/libmediandk.so";
#else
    libc = "/system/lib/libmediandk.so";
#endif
    return libc;
}

string getJitPath() {
    string libc;

#if defined(__aarch64__)
    if (get_sdk_level() >= ANDROID_R) {
        libc = "/apex/com.android.art/lib64/libart-compiler.so";
    } else if (get_sdk_level() >= ANDROID_Q) {
        libc = "/apex/com.android.runtime/lib64/libart-compiler.so";
    } else {
        libc = "/system/lib64/libart-compiler.so";
    }
#else
    if (get_sdk_level() >= ANDROID_R) {
        libc = "/apex/com.android.art/lib/libart-compiler.so";
    } else if (get_sdk_level() >= ANDROID_Q) {
        libc ="/apex/com.android.runtime/lib/libart-compiler.so";
    } else {
        libc = "/system/lib/libart-compiler.so";
    }
#endif
    return libc;
}


