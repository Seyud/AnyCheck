//
// Created by zhenxi on 2022/2/6.
//

#include "invokePrintf.h"
#include "xdl.h"

#define ANDROID_K 19
#define ANDROID_L 21
#define ANDROID_L2 22
#define ANDROID_M 23
#define ANDROID_N 24
#define ANDROID_N2 25
//Android 8.0
#define ANDROID_O 26
//Android 8.1
#define ANDROID_O2 27
//Android 9.0
#define ANDROID_P 28
//Android 10.0
#define ANDROID_Q 29
//Android 11.0
#define ANDROID_R 30
//Android 12.0
#define ANDROID_S 31

static std::ofstream *invokeOs;
static bool isSave = false;
static void* ArtMethodInvoke = nullptr;
static void* art_method_register = nullptr;
static bool isPrintf = true;

std::string (*invokePrintf_org_PrettyMethodSym)(void *thiz, bool b) = nullptr;

void *(*org_Invoke)(void *thiz, void *self, uint32_t *args, uint32_t args_size, void *result,const char *shorty) = nullptr;
void *new_Invoke(void *thiz, void *self, uint32_t *args, uint32_t args_size, void *result,
                 const char *shorty) {
    string basicString = invokePrintf_org_PrettyMethodSym(thiz, true);

    if(isPrintf){
        LOG(INFO) << "invoke method info -> ["<<basicString<<"]";
    }
    if(isSave){
        *invokeOs << basicString.append("\n");
    }
    return org_Invoke(thiz, self, args, args_size, result, shorty);
}


__always_inline
static string getFileNameForPath(const char *path) {
    if(path == nullptr){
        return "";
    }
    std::string pathStr = path;
    size_t pos = pathStr.rfind('/');
    if (pos != std::string::npos) {
        return pathStr.substr(pos + 1);
    }
    return pathStr;
}
void RegisterNativeCallBack(void *method, const void *native_method) {
    if (method == nullptr || native_method == nullptr) {
        return;
    }
    string basicString = invokePrintf_org_PrettyMethodSym(method, true);
    if (isSave) {
        *invokeOs << basicString.append("\n");
    }
    Dl_info info;
    dladdr(native_method, &info);
    size_t relative_offset =
            reinterpret_cast<size_t>(native_method) - reinterpret_cast<size_t>(info.dli_fbase);

    LOG(INFO) << "REGISTER_NATIVE " << basicString.c_str() << " absolute address(内存地址)["
              << native_method << "]  relative offset(相对地址) [" << (void *) relative_offset
              << "]  所属ELF文件[" << getFileNameForPath(info.dli_fname) + "]";
}

//12以上
//const void* RegisterNative(Thread* self, ArtMethod* method, const void* native_method)
HOOK_DEF(void*, RegisterNative_12, void *self, void *method, const void *native_method) {
    RegisterNativeCallBack(method, native_method);
    return orig_RegisterNative_12(self, method, native_method);
}
//11
HOOK_DEF(void*, RegisterNative_11, void *method, const void *native_method) {
    RegisterNativeCallBack(method, native_method);
    return orig_RegisterNative_11(method, native_method);
}

HOOK_DEF(void*, RegisterNative, void *method, const void *native_method, bool b) {
    RegisterNativeCallBack(method, native_method);
    return orig_RegisterNative(method, native_method,b);
}




void invokePrintf::Stop(){
    if(ArtMethodInvoke!= nullptr){
        HookUtils::unHook(ArtMethodInvoke);
    }
    if(ArtMethodInvoke!= nullptr){
        HookUtils::unHook(art_method_register);
    }
    isPrintf = false;
}

void invokePrintf::HookRegisterNative(JNIEnv *env,std::ofstream *os) {
    if(os!= nullptr){
        invokeOs = os;
        isSave = true;
    }
    if (invokePrintf_org_PrettyMethodSym == nullptr) {
        void *PrettyMethodSym = getSymCompat(getlibArtPath().c_str(), "_ZN3art9ArtMethod12PrettyMethodEb");
        invokePrintf_org_PrettyMethodSym = reinterpret_cast<std::string(*)(void *, bool)>(PrettyMethodSym);
    }
    if (android_get_device_api_level() < ANDROID_S) {
        //android 11
        art_method_register = getSymCompat(getlibArtPath().c_str(),
                                           "_ZN3art9ArtMethod14RegisterNativeEPKv");
        if (art_method_register == nullptr) {
            art_method_register = getSymCompat(getlibArtPath().c_str(),
                                               "_ZN3art9ArtMethod14RegisterNativeEPKvb");
        }
    } else {
        //12以上
        art_method_register = getSymCompat(getlibArtPath().c_str(),
                                           "_ZN3art11ClassLinker14RegisterNativeEPNS_6ThreadEPNS_9ArtMethodEPKv");
    }
    bool isSuccess;
    if (android_get_device_api_level() >= ANDROID_S) {
        isSuccess = HookUtils::Hooker(art_method_register,
                                      (void *) new_RegisterNative_12,
                                      (void **) &orig_RegisterNative_12);
    } else if (android_get_device_api_level() >= ANDROID_R) {
        isSuccess = HookUtils::Hooker(art_method_register,
                                      (void *) new_RegisterNative_11,
                                      (void **) &orig_RegisterNative_11);
    } else {
        isSuccess = HookUtils::Hooker(art_method_register,
                                      (void *) new_RegisterNative,
                                      (void **) &orig_RegisterNative);
    }
    LOGE(">>>>>>>>> hook artmethod RegisterNative success ! %s ",isSuccess?"true":"false")

}
void invokePrintf::HookJNIInvoke(JNIEnv *env,std::ofstream *os) {
    if(os!= nullptr){
        invokeOs = os;
        isSave = true;
    }
    if (invokePrintf_org_PrettyMethodSym == nullptr) {
        void *PrettyMethodSym = getSymCompat(getlibArtPath().c_str(), "_ZN3art9ArtMethod12PrettyMethodEb");
        if(PrettyMethodSym == nullptr){
            PrettyMethodSym = getSymCompat(getlibArtPath().c_str(), "_ZN3art9ArtMethod12PrettyMethodEPS0_b");
        }
        invokePrintf_org_PrettyMethodSym = reinterpret_cast<std::string(*)(void *, bool)>(PrettyMethodSym);
    }
    //artmethod->invoke
    ArtMethodInvoke = getSymCompat(getlibArtPath().c_str(),
                 "_ZN3art9ArtMethod6InvokeEPNS_6ThreadEPjjPNS_6JValueEPKc");
    if(ArtMethodInvoke == nullptr){

    }
    bool regiester = HookUtils::Hooker(ArtMethodInvoke,
                         (void *) new_Invoke,
                         (void **) &org_Invoke);

    LOGE(">>>>>>>>> hook artmethod invoke success ! %s ",regiester?"true":"false")
}