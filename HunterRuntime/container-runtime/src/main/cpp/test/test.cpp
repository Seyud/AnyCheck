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

#include <logging.h>

#include <cfrida.h>



extern "C"
JNIEXPORT void JNICALL
Java_com_hunter_runtime_TestClass_test(JNIEnv *env, jclass clazz, jobject obj) {
    checkLibcCheckSum();
    checkLibArtCheckSum();
}




jint JNICALL JNI_OnLoad(JavaVM *_vm, void *) {
    JNIEnv *env = nullptr;
    if (_vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }



    LOG(INFO) << "hunter runtime test JNI_OnLoad init end ,init sucess !   " ;
    return JNI_VERSION_1_6;
}
