//
// Created by Zhenxi on 2023/4/23.
//
#include <jni.h>
#include <set>
#include <iostream>
#include <strstream>

#include "hide_maps_path.h"
#include "ZhenxiLog.h"
#include "logging.h"
#include "parse.h"
#include "common_macros.h"
#include "stringUtils.h"

using namespace std;



extern "C" JNIEXPORT
void RuntimeHideMapsMarks(JNIEnv *env,jobject list, jstring packageName) {
    //LOGI("start hide maps !!!!!!!!!!")
    const auto &marksList = parse::jlist2clist(env, list);
    //PRINTF_LIST_LINE(marksList);
    //LOGI(">>>>>>>>> mark list printf finish")
    //FIND_PACKAGE_ITEMS_IN_MAPS(getpid(),"com.example.test");
    std::set<std::string_view> marks{marksList.begin(), marksList.end()};
    //clean elf magic
    ZhenxiRuntime::MapsItemHide::hide_elf_header(marks,ZhenxiRuntime::CLEAN_ELF_HEADER_TYPE::CLEAN_ELF);
    //hide maps
    ZhenxiRuntime::MapsItemHide::riru_hide(marks);
    //LOGE("------------------------------------------------------")
    //FIND_PACKAGE_ITEMS_IN_MAPS(getpid(),"com.example.test");

    //const auto &str_packageName = parse::jstring2str(env, packageName);
    //FIND_PACKAGE_ITEMS_IN_MAPS(getpid(),str_packageName);
    //LOGI("Hide maps success !")
}


