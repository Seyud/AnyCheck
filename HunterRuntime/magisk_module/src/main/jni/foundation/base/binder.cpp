//
// Created by canyie on 2021/1/28.
//

#include "binder.h"
#include "adapter.h"
#include "ZhenxiLog.h"
#include "jni_helper.hpp"

using namespace ZhenxiRuntime;
using namespace lsplant;

static bool BinderLoaderInit = false;

jclass Binder::ServiceManager = nullptr;
jclass Binder::IBinder = nullptr;
jclass Binder::Parcel = nullptr;
jclass Binder::parcel_file_descriptor_class_;

jmethodID Binder::read_long_method_ = nullptr;
jmethodID Binder::read_int_method_ = nullptr;

jmethodID Binder::getService = nullptr;
jmethodID Binder::transact = nullptr;
jmethodID Binder::obtainParcel = nullptr;
jmethodID Binder::writeInterfaceToken = nullptr;
jmethodID Binder::readException = nullptr;

jmethodID Binder::readStrongBinder = nullptr;
jmethodID Binder::recycleParcel = nullptr;
jstring Binder::serviceName = nullptr;
jstring Binder::interfaceToken = nullptr;

jmethodID Binder::read_file_descriptor_method_ = nullptr;
jmethodID Binder::detach_fd_method_ = nullptr;

using namespace std;

template<typename T>
static T MakeGlobalRef(JNIEnv *env, T local) {
    T global = (T) env->NewGlobalRef(local);
    env->DeleteLocalRef(local);
    return global;
}

template<typename T>
static void FreeGlobalRef(JNIEnv *env, T &ref) {
    env->DeleteGlobalRef(ref);
    ref = nullptr;
}

void Binder::Init(JNIEnv *env) {
    if (BinderLoaderInit) return;

    ServiceManager = MakeGlobalRef(env, env->FindClass("android/os/ServiceManager"));
    IBinder = MakeGlobalRef(env, env->FindClass("android/os/IBinder"));
    Parcel = MakeGlobalRef(env, env->FindClass("android/os/Parcel"));
    parcel_file_descriptor_class_ = MakeGlobalRef(env, env->FindClass(
            "android/os/ParcelFileDescriptor"));


    getService = env->GetStaticMethodID(ServiceManager, "getService",
                                        "(Ljava/lang/String;)Landroid/os/IBinder;");
    transact = env->GetMethodID(IBinder, "transact", "(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z");
    obtainParcel = env->GetStaticMethodID(Parcel, "obtain", "()Landroid/os/Parcel;");
    writeInterfaceToken = env->GetMethodID(Parcel, "writeInterfaceToken", "(Ljava/lang/String;)V");
    readException = env->GetMethodID(Parcel, "readException", "()V");
    readStrongBinder = env->GetMethodID(Parcel, "readStrongBinder", "()Landroid/os/IBinder;");
    recycleParcel = env->GetMethodID(Parcel, "recycle", "()V");

    serviceName = MakeGlobalRef(env, env->NewStringUTF(kServiceName));
    interfaceToken = MakeGlobalRef(env, env->NewStringUTF(kInterfaceToken));
    read_file_descriptor_method_ = JNI_GetMethodID(env, Parcel, "readFileDescriptor",
                                                   "()Landroid/os/ParcelFileDescriptor;");
    detach_fd_method_ = JNI_GetMethodID(env, parcel_file_descriptor_class_, "detachFd", "()I");
    read_long_method_ = JNI_GetMethodID(env, Parcel, "readLong", "()J");
    read_int_method_ = JNI_GetMethodID(env, Parcel, "readInt", "()I");

    BinderLoaderInit = true;

}
// TODO: JNI is very slow, use pure-native code instead if we can.
#define FAIL_IF(cond, error_msg) do {\
    if (cond) {\
    LOGE(error_msg);\
    goto fail;\
    }\
    \
} while (0)

#define FAIL_IF_EXCEPTION(error_msg) FAIL_IF(env->ExceptionCheck(), error_msg)


jobject Binder::GetBinder(JNIEnv *env) {
    Init(env);

    ScopedLocalRef<jobject> clipboard(env);
    ScopedLocalRef<jobject> data(env);
    ScopedLocalRef<jobject> reply(env);
    jboolean success;
    jobject service = nullptr;

    clipboard.reset(env->CallStaticObjectMethod(ServiceManager, getService, serviceName));
    FAIL_IF_EXCEPTION("ServiceManager.getService threw exception");

    if (UNLIKELY(clipboard.IsNull())) {
        // Isolated process or google gril service process is not allowed to access clipboard service
        LOGW("Clipboard service is unavailable in current process, skipping");
        return nullptr;
    }

    data.reset(env->CallStaticObjectMethod(Parcel, obtainParcel));
    FAIL_IF(data.IsNull(), "Failed to obtain data parcel");
    reply.reset(env->CallStaticObjectMethod(Parcel, obtainParcel));
    FAIL_IF(reply.IsNull(), "Failed to obtain reply parcel");

    env->CallVoidMethod(data.get(), writeInterfaceToken, interfaceToken);
    FAIL_IF_EXCEPTION("Parcel.writeInterfaceToken threw exception");

    success = env->CallBooleanMethod(clipboard.get(), transact, kTransactionCode, data.get(),
                                     reply.get(), 0);
    FAIL_IF_EXCEPTION("Binder.transact threw exception");

    env->CallVoidMethod(reply.get(), readException);
    FAIL_IF_EXCEPTION("Clipboard service threw exception");

    if (success) {
        service = env->CallObjectMethod(reply.get(), readStrongBinder);
        FAIL_IF_EXCEPTION("readStrongBinder threw exception");
    }

    RecycleParcel(env, data.get());
    RecycleParcel(env, reply.get());

    return service;

    fail:
    env->ExceptionDescribe();
    env->ExceptionClear();
    RecycleParcel(env, data.get());
    RecycleParcel(env, reply.get());
    return nullptr;
}

std::vector<std::tuple<int, size_t>> Binder::GetDexInfo(JNIEnv *env) {

    Init(env);
    ScopedLocalRef<jobject> clipboard(env);
    ScopedLocalRef<jobject> data(env);
    ScopedLocalRef<jobject> reply(env);
    jboolean success;

    clipboard.reset(env->CallStaticObjectMethod(ServiceManager, getService, serviceName));
    if (JNIHelper::ExceptionCheck(env) || UNLIKELY(clipboard.IsNull())) {
        // Isolated process or google gril service process is not allowed to access clipboard service
        LOGW("Clipboard service is unavailable in current process, skipping");
        return {{-1, 0}};
    }

    data.reset(env->CallStaticObjectMethod(Parcel, obtainParcel));
    FAIL_IF(data.IsNull(), "Failed to obtain data parcel");
    reply.reset(env->CallStaticObjectMethod(Parcel, obtainParcel));
    FAIL_IF(reply.IsNull(), "Failed to obtain reply parcel");
    success = env->CallBooleanMethod(clipboard.get(), transact, DEX_TRANSACTION_CODE, data.get(),
                                     reply.get(), 0);
    FAIL_IF_EXCEPTION("Binder.transact threw exception");


    if (success) {
        env->CallVoidMethod(reply.get(), readException);
        FAIL_IF_EXCEPTION("Clipboard service threw exception");
        //read size
        jint fd_size = env->CallIntMethod(reply.get(), read_int_method_);
        FAIL_IF_EXCEPTION("Clipboard read fd size error ");
        //LOGE("ipc read dex size %d",fd_size)
        std::vector<std::tuple<int, size_t>> fd_list ;
        for(int i=0;i<fd_size;i++){
            jobject parcel_fd = env->CallObjectMethod(reply.get(), read_file_descriptor_method_);
            FAIL_IF_EXCEPTION("call read_file_descriptor_method_ error");
            jint fd = env->CallIntMethod(parcel_fd, detach_fd_method_);
            FAIL_IF_EXCEPTION("call detach_fd_method_ error");
            auto size = (size_t) env->CallLongMethod(reply.get(), read_long_method_);
            FAIL_IF_EXCEPTION("call read_long_method_ error");
            fd_list.push_back({fd,size});
            //LOGD("native get fd=%d, size=%lu", fd, size)
        }
        return fd_list;
    }

    fail:
    env->ExceptionDescribe();
    env->ExceptionClear();
    RecycleParcel(env, data.get());
    RecycleParcel(env, reply.get());
    return {{-1, 0}};
}

void Binder::Cleanup(JNIEnv *env) {
    FreeGlobalRef(env, ServiceManager);
    FreeGlobalRef(env, IBinder);
    FreeGlobalRef(env, Parcel);
    FreeGlobalRef(env, serviceName);
    FreeGlobalRef(env, interfaceToken);
    FreeGlobalRef(env, parcel_file_descriptor_class_);
    BinderLoaderInit = false;
}
