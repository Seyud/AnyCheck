//
// Created by zhenxi on 2022/2/6.
//

#ifndef QCONTAINER_PRO_INVOKEPRINTF_H
#define QCONTAINER_PRO_INVOKEPRINTF_H

#include "AllInclude.h"
#include "ZhenxiLog.h"
#include "logging.h"
#include "HookUtils.h"
#include "libpath.h"

class invokePrintf {
public:
    static void HookJNIInvoke(JNIEnv *env,std::ofstream *os);
    static void HookRegisterNative(JNIEnv *env,std::ofstream *os);

    static void Stop();
};


#endif //QCONTAINER_PRO_INVOKEPRINTF_H
