//
// VirtualApp Native Project
//

#ifndef NDK_HOOK_H
#define NDK_HOOK_H


#include <string>
#include <map>
#include <list>
#include <jni.h>
#include <dlfcn.h>
#include <stddef.h>
#include <fcntl.h>
#include <dirent.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <syscall.h>
#include <limits.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <sys/user.h>
#include <pthread.h>
#include <vector>
#include <zlib.h>
#include <list>



#define V_SO_PATH_32 "V_SO_PATH_32"
#define V_SO_PATH_64 "V_SO_PATH_64"


#define HOOK_DEF(ret, func, ...) \
  ret (*orig_##func)(__VA_ARGS__)=nullptr; \
  ret new_##func(__VA_ARGS__)


//通过查找system去Hook指定函数
#define HOOK_SYSCALL(syscall_name) \
case __NR_##syscall_name: \
HookUtils::Hooker(func, (void *) new_##syscall_name, (void **) &orig_##syscall_name); \
pass++;  \
break;  \


#define HOOK_SYSCALL_(syscall_name) \
case __NR_##syscall_name: \
HookUtils::Hooker(func, (void *) new___##syscall_name, (void **) &orig___##syscall_name); \
pass++; \
break; \


//如果Hook linker  Dlopen则当前App所有加载的So都会走此回调
void onSoLoadedAfter(const char *org_path, [[maybe_unused]] void *handle);

void onSoLoadedBefore(const char *name);

void startIOHook(JNIEnv *env, int api_level);


namespace ZhenxiRunTime::IOUniformer {

    void init_env_before_all();

    void startUniformer(
            JNIEnv *env,
            const char *so_path,
            const char *so_path_64,
            const char *native_path,
            int api_level,
            int preview_api_level);


    void redirect(const char *orig_path, const char *new_path);

    void whitelist(const char *path);

    const char *query(const char *orig_path, char *const buffer, const size_t size);

    const char *reverse(const char *_path, char *const buffer, const size_t size);

    void forbid(const char *path);

    const char *query(const char *orig_path, char *const buffer, const size_t size);

    void readOnly(const char *_path);


}


#endif //NDK_HOOK_H

