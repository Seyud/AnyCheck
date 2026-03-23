
#ifndef VMP_LOG_H
#define VMP_LOG_H


#include <android/log.h>


#define TAG "Zhenxi"

#define FATAL(...) do {\
LOGE("*** Runtime aborting because of fatal error: ");\
LOGE(__VA_ARGS__);\
LOGE("Aborting...");\
abort();\
} while(0)

#define FATAL_FOR_JNI(...) do {\
LOGE("*** Runtime aborting because of fatal error: ");\
LOGE(__VA_ARGS__);\
if(((env)->ExceptionCheck())) {\
LOGE("JNI ERROR: ");\
(env)->ExceptionDescribe();\
}\
env->FatalError("FATAL_FOR_JNI called.");\
} while(0)

#define CHECK(op, ...) do { if((!(op))) { FATAL(__VA_ARGS__); } } while(0)
#define CHECK_FOR_JNI(op, ...) do { if((!(op))) { FATAL_FOR_JNI(__VA_ARGS__); } } while(0)


#ifdef ZHENXI_BUILD_TYPE_NOLOG

#define LOGE(...)   ((void)0);
#define LOGI(...)   ((void)0);
#define LOGD(...)   ((void)0);
#define LOGW(...)   ((void)0);
#define ALOGI(...) ((void)0);
#define ALOGD(...) ((void)0);
#define ALOGW(...) ((void)0);
#define ALOGE(...) ((void)0);

#define TAG "Zhenxi"


#else



#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__);
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG ,__VA_ARGS__);
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG ,__VA_ARGS__);
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG ,__VA_ARGS__);

#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG ,__VA_ARGS__);
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG ,__VA_ARGS__);
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG ,__VA_ARGS__);
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG ,__VA_ARGS__);


#endif


//专门用于调试ptrace的代码,学习ptrace流程使用
//不想打印的话可以在cmake中去掉
#ifdef ZHENXI_PTRACE_DEBUG

#define PLOGE(...)  __android_log_print(ANDROID_LOG_ERROR, TAG2, __VA_ARGS__);
#define PLOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG2 ,__VA_ARGS__);
#define PLOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG2 ,__VA_ARGS__);
#define PLOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG2 ,__VA_ARGS__);

#else

#define PLOGE(...)  ((void)0);
#define PLOGI(...)  ((void)0);
#define PLOGD(...)  ((void)0);
#define PLOGW(...)  ((void)0);

#endif

#endif //VMP_LOG_H
