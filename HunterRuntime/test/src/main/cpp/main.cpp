#include <jni.h>




#include <string>
#include <sstream>
#include <iostream>
#include <vector>
#include <list>
#include <array>
#include <map>
#include <set>


using namespace std;


static int test(){
    return 1+2;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_test_MainActivity_inlinehook(JNIEnv *env, jobject thiz) {
    static int a = 1;
    int b = 3<<4;

    jclass clazz = env->FindClass("com/example/test/MainActivity");
    jmethodID id = env->GetMethodID(clazz, "toString", "()Ljava/lang/String;");
    jobject pJobject = env->CallObjectMethod(thiz, id);

}


