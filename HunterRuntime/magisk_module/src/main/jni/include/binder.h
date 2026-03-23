//
// Created by canyie on 2021/1/28.
//

#ifndef DREAMLAND_BINDER_H
#define DREAMLAND_BINDER_H


#include <jni.h>
#include <map>
#include <list>

namespace ZhenxiRuntime {
    class Binder {
    public:
        static void Init(JNIEnv* env);
        static jobject GetBinder(JNIEnv* env);
        /**
         * get dex info
         */
        static std::vector<std::tuple<int, size_t>> GetDexInfo(JNIEnv* env);
        static void Cleanup(JNIEnv* env);

    private:
        static void RecycleParcel(JNIEnv* env, jobject parcel) {
            if (parcel) {
                env->CallVoidMethod(parcel, recycleParcel);
                env->ExceptionClear();
            }
        }
        static jmethodID read_int_method_ ;
        static jclass ServiceManager;
        static jclass IBinder;
        static jclass Parcel;
        static jmethodID getService;
        static jmethodID transact;
        static jmethodID obtainParcel;
        static jmethodID writeInterfaceToken;
        static jmethodID readException;
        static jmethodID readStrongBinder;
        static jmethodID recycleParcel;
        static jstring serviceName;
        static jstring interfaceToken;
        static jmethodID read_file_descriptor_method_;
        static jmethodID detach_fd_method_;
        static jclass parcel_file_descriptor_class_ ;
        static jmethodID read_long_method_ ;

        /**
         * 动态代理剪切板服务进行注入,这个服务是最简单的服务。
         */
        static constexpr const char* kServiceName = "clipboard";
        static constexpr const char* kInterfaceToken = "android.content.IClipboard";
        static constexpr jint kTransactionCode = ('_'<<24)|('D'<<16)|('M'<<8)|'S';
        static constexpr jint DEX_TRANSACTION_CODE = 1310096052;

    };
}


#endif //DREAMLAND_BINDER_H
