/* Copyright 2022-2023 John "topjohnwu" Wu
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 * LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
 * OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

// This is the public API for Zygisk modules.
// DO NOT MODIFY ANY CODE IN THIS HEADER.

#pragma once

#include <jni.h>
#include <ostream>
#include <sstream>
#include <string>

#define ZYGISK_API_VERSION 4

/*

***************
* Introduction
***************

On Android, all app processes are forked from a special daemon called "Zygote".
For each new app process, zygote will fork a new process and perform "specialization".
This specialization operation enforces the Android security sandbox on the newly forked
process to make sure that 3rd party application code is only loaded after it is being
restricted within a sandbox.
在 Android 上，所有的应用进程都是从一个特殊的守护进程 "Zygote" 分叉(fork)出来的。
对于每一个新的应用进程，Zygote 会分叉一个新进程并进行 "(specialization)" 操作。
这个专门化操作强制实施 Android 安全沙盒，确保第三方应用代码只有在被限制在沙盒中之后才加载。

On Android, there is also this special process called "system_server". This single
process hosts a significant portion of system services, which controls how the
Android operating system and apps interact with each other.
在 Android 上，还有一个特殊的进程叫做 "system_server"。
这个单一进程承载了大部分的系统服务，控制着 Android 操作系统和应用程序之间的互动方式。

The Zygisk framework provides a way to allow developers to build modules and run custom
code before and after system_server and any app processes' specialization.
This enable developers to inject code and alter the behavior of system_server and app processes.
Zygisk 框架提供了一种方法，允许开发者构建模块并在 system_server
和任何应用进程的specialization之前和之后运行自定义代码。
这使开发者能够注入代码并改变 system_server 和应用进程的行为。

Please note that modules will only be loaded after zygote has forked the child process.
THIS MEANS ALL OF YOUR CODE RUNS IN THE APP/SYSTEM_SERVER PROCESS, NOT THE ZYGOTE DAEMON!
请注意，模块只会在 Zygote fork 子进程之后加载。
这意味着你的所有代码都是在应用/系统服务器进程中运行，而不是在 Zygote 守护进程中运行！

*********************
* Development Guide
*********************

Define a class and inherit zygisk::ModuleBase to implement the functionality of your module.
Use the macro REGISTER_ZYGISK_MODULE(className) to register that class to Zygisk.

Example code:

static jint (*orig_logger_entry_max)(JNIEnv *env);
static jint my_logger_entry_max(JNIEnv *env) { return orig_logger_entry_max(env); }

class ExampleModule : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *api, JNIEnv *env) override {
        this->api = api;
        this->env = env;
    }
    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        JNINativeMethod methods[] = {
            { "logger_entry_max_payload_native", "()I", (void*) my_logger_entry_max },
        };
        api->hookJniNativeMethods(env, "android/util/Log", methods, 1);
        *(void **) &orig_logger_entry_max = methods[0].fnPtr;
    }
private:
    zygisk::Api *api;
    JNIEnv *env;
};

REGISTER_ZYGISK_MODULE(ExampleModule)

-----------------------------------------------------------------------------------------

or runs in the sandbox of the target process in post[XXX]Specialize, the code in your class
never runs in a true superuser environment.
由于你的模块类的代码在 pre[XXX]Specialize 中以 Zygote 的特权运行，
或者在 post[XXX]Specialize 中在目标进程的沙盒中运行，你的类中的代码从不在一个真正的超级用户环境中运行。

If your module require access to superuser permissions, you can create and register
a root companion handler function. This function runs in a separate root companion
daemon process, and an Unix domain socket is provided to allow you to perform IPC between
your target process and the root companion process.
如果你的模块需要访问超级用户权限，你可以创建并注册一个根伴随处理函数(root companion handler function)。
这个函数在一个单独的根伴随守护进程中运行，并提供了一个 Unix socket，允许你在目标进程和根伴随进程之间进行
IPC（进程间通信）。

Example code:

static void example_handler(int socket) { ... }

REGISTER_ZYGISK_COMPANION(example_handler)

*/

namespace zygisk {

    struct Api;
    struct AppSpecializeArgs;
    struct ServerSpecializeArgs;

    class ModuleBase {
    public:

        // This method is called as soon as the module is loaded into the target process. A Zygisk API handle will be passed as an argument.
        // 一旦模块被加载到目标进程中，就会调用此方法。一个 Zygisk API 句柄会作为参数传递。
        virtual void onLoad([[maybe_unused]] Api *api, [[maybe_unused]] JNIEnv *env) {}

        // This method is called before the app process is specialized.
        // At this point, the process just got forked from zygote, but no app specific specialization
        // is applied. This means that the process does not have any sandbox restrictions and
        // still runs with the same privilege of zygote.
        // 在应用进程专业化之前调用，此时进程刚从 zygote 分叉出来，但还未应用任何特定于应用的专业化设置。
        // 这意味着进程还没有任何沙箱限制，并且仍然以 zygote 相同的权限运行。
        virtual void preAppSpecialize([[maybe_unused]] AppSpecializeArgs *args) {}

        // This method is called after the app process is specialized.
        // At this point, the process has all sandbox restrictions enabled for this application.
        // This means that this method runs with the same privilege of the app's own code.
        // 在应用进程专业化之后调用，此时进程已经为该应用启用了所有沙盒限制。
        // 这意味着这个方法与应用自己的代码以相同的权限运行。
        virtual void postAppSpecialize([[maybe_unused]] const AppSpecializeArgs *args) {}

        // This method is called before the system server process is specialized.
        // 在系统服务器进程专业化之前调用此方法。
        // See preAppSpecialize(args) for more info.
        // 有关更多信息，请参考 preAppSpecialize(args)。
        virtual void preServerSpecialize([[maybe_unused]] ServerSpecializeArgs *args) {}

        // This method is called after the system server process is specialized.
        // At this point, the process runs with the privilege of system_server.
        // 在系统服务器进程专业化之后调用。
        // 此时，进程以 system_server 的权限运行。
        virtual void postServerSpecialize([[maybe_unused]] const ServerSpecializeArgs *args) {}
    };

    struct AppSpecializeArgs {
        // Required arguments.
        // These arguments are guaranteed to exist on all Android versions.
        jint &uid;
        jint &gid;
        jintArray &gids;
        jint &runtime_flags;
        jobjectArray &rlimits;
        jint &mount_external;
        jstring &se_info;
        jstring &nice_name;
        jstring &instruction_set;
        jstring &app_data_dir;

        // Optional arguments. Please check whether the pointer is null before de-referencing
        jintArray *const fds_to_ignore;
        jboolean *const is_child_zygote;
        jboolean *const is_top_app;
        jobjectArray *const pkg_data_info_list;
        jobjectArray *const whitelisted_data_info_list;
        jboolean *const mount_data_dirs;
        jboolean *const mount_storage_dirs;

        AppSpecializeArgs() = delete;

        std::string toString(JNIEnv *env) const {
            std::ostringstream stream;
            printJString(env, stream, "nice_name", nice_name);

//        printJString(env, stream, "app_data_dir", app_data_dir);
//
//        printJString(env, stream, "se_info", se_info);
//        printJString(env, stream, "instruction_set", instruction_set);
//        stream << "uid: " << uid << " gid: " << gid << " runtime_flags: " << runtime_flags<< " mount_external: " << mount_external;
//
//        printJIntArray(env, stream, "gids", gids);
//
//        printJBoolean(env, stream, "is_child_zygote", is_child_zygote);
//        printJBoolean(env, stream, "is_top_app", is_top_app);
//        printJBoolean(env, stream, "mount_data_dirs", mount_data_dirs);
//        printJBoolean(env, stream, "mount_storage_dirs", mount_storage_dirs);
//
//        printJObjectArrayPointer(env, stream, "pkg_data_info_list", pkg_data_info_list);
//        printJObjectArrayPointer(env, stream, "whitelisted_data_info_list", whitelisted_data_info_list);

            return stream.str();
        }

    private:
        [[maybe_unused]]
        void printJString(JNIEnv *env, std::ostringstream &stream, const std::string &name,
                          jstring value) const {
            if (value != nullptr) {
                const char *chars = env->GetStringUTFChars(value, nullptr);
                stream << " " << name << ": " << chars << "\n";
                env->ReleaseStringUTFChars(value, chars);
            } else {
                stream << " " << name << ": null" << "\n";
            }
        }

        [[maybe_unused]]
        void printJIntArray(JNIEnv *env, std::ostringstream &stream, const std::string &name,
                            jintArray value) const {
            if (value != nullptr) {
                jsize length = env->GetArrayLength(value);
                jint *elements = env->GetIntArrayElements(value, nullptr);
                stream << " " << name << ": [";
                for (jsize i = 0; i < length; ++i) {
                    stream << elements[i];
                    if (i < length - 1) {
                        stream << ", ";
                    }
                }
                stream << "]" << "\n";
                env->ReleaseIntArrayElements(value, elements, JNI_ABORT);
            } else {
                stream << " " << name << ": null" << "\n";
            }
        }

        [[maybe_unused]]
        void printJBoolean(JNIEnv *env, std::ostringstream &stream,
                           const std::string &name, jboolean *value) const {
            if (value != nullptr) {
                stream << " " << name << ": " << ((*value) == JNI_TRUE ? "true" : "flase") << "\n";
            } else {
                stream << " " << name << ": null" << "\n";
            }
        }

        [[maybe_unused]]
        void
        printJObjectArrayPointer(JNIEnv *env, std::ostringstream &stream, const std::string &name,
                                 jobjectArray *arrayPtr) const {
            if (arrayPtr && *arrayPtr) {
                jobjectArray array = *arrayPtr;
                jsize length = env->GetArrayLength(array);
                stream << " " << name << ": [";
                for (jsize i = 0; i < length; ++i) {
                    jstring str = (jstring) env->GetObjectArrayElement(array, i);
                    const char *chars = env->GetStringUTFChars(str, nullptr);
                    stream << chars;
                    env->ReleaseStringUTFChars(str, chars);
                    if (i < length - 1) {
                        stream << ", ";
                    }
                }
                stream << "]" << "\n";
            } else {
                stream << " " << name << ": null" << "\n";
            }
        }

    };

    struct ServerSpecializeArgs {
        jint &uid;
        jint &gid;
        jintArray &gids;
        jint &runtime_flags;
        jlong &permitted_capabilities;
        jlong &effective_capabilities;

        ServerSpecializeArgs() = delete;
    };

    namespace internal {
        struct api_table;

        template<class T>
        void entry_impl(api_table *, JNIEnv *);
    }
    // These values are used in Api::setOption(Option)
    // 这些值被用在 Api::setOption(Option) 方法中
    enum Option : int {
        // Force Magisk's denylist unmount routines to run on this process.
        // 强制在此进程上运行 Magisk 的拒绝列表unmount routines。
        //
        // Setting this option only makes sense in preAppSpecialize.
        // 只有在 preAppSpecialize 中设置此选项才有意义。
        // The actual unmounting happens during app process specialization.
        // 实际的卸载过程发生在应用进程specialization期间。
        //
        // Set this option to force all Magisk and modules' files to be unmounted from the
        // mount namespace of the process, regardless of the denylist enforcement status.
        // 设置此选项以强制从进程的挂载命名空间中卸载所有 Magisk 和模块的文件，无论拒绝列表的执行状态如何。
        // 这个主要是卸载magisk整个系统文件
        FORCE_DENYLIST_UNMOUNT = 0,

        // When this option is set, your module's library will be dlclose-ed after post[XXX]Specialize.
        // 设置此选项时，你的模块库将在 post[XXX]Specialize 之后被 dlclose。
        // Be aware that after dlclose-ing your module, all of your code will be unmapped from memory.
        // 注意，dlclose 你的模块后，你所有的代码都将从内存中取消映射。
        // YOU MUST NOT ENABLE THIS OPTION AFTER HOOKING ANY FUNCTIONS IN THE PROCESS.
        // 在进程中挂钩任何函数后，你必须不启用此选项。
        // 这个是卸载so文件
        DLCLOSE_MODULE_LIBRARY = 1,
    };

    // Bit masks of the return value of Api::getFlags()
    // Api::getFlags() 返回值的位掩码
    enum StateFlag : uint32_t {
        // The user has granted root access to the current process
        // 用户已授予当前进程根访问权限
        PROCESS_GRANTED_ROOT = (1u << 0),

        // The current process was added on the denylist
        // 当前进程被添加到拒绝列表中
        PROCESS_ON_DENYLIST = (1u << 1),
    };

    // All API methods will stop working after post[XXX]Specialize as Zygisk will be unloaded
    // from the specialized process afterwards.
    // 所有API方法post[XXX]Specialize后停止工作，因为Zygisk将在之后从专业化过程中卸载。
    struct Api {

        // Connect to a root companion process and get a Unix domain socket for IPC.
        // 获取和root守护进程通讯的句柄,可以直接通过这个句柄进行ipc通讯 。
        // 连接到root companion进程并获取 Unix socket进行 IPC。
        //
        // This API only works in the pre[XXX]Specialize methods due to SELinux restrictions.
        // 由于 SELinux 限制，此 API 仅在 pre[XXX]Specialize 方法中有效。
        //
        // The pre[XXX]Specialize methods run with the same privilege of zygote.
        // pre[XXX]Specialize 方法以与 zygote 相同的权限运行。
        // If you would like to do some operations with superuser permissions, register a handler
        // function that would be called in the root process with REGISTER_ZYGISK_COMPANION(func).
        // 如果你想以超级用户权限执行一些操作，请注册一个处理函数，
        // 在根进程中以 REGISTER_ZYGISK_COMPANION(func) 调用。

        // Another good use case for a companion process is that if you want to share some resources
        // across multiple processes, hold the resources in the companion process and pass it over.
        // companion进程的另一个好用途是，如果你想在多个进程间共享一些资源，
        // 可以在companion进程中保持这些资源并传递过去。
        //
        // The root companion process is ABI aware; that is, when calling this method from a 32-bit
        // process, you will be connected to a 32-bit companion process, and vice versa for 64-bit.
        // root伴随进程是 ABI 意识到的；
        // 也就是说，当从 32 位进程调用此方法时，你将连接到一个 32 位的伴随进程，对于 64 位也是如此。
        //
        // Returns a file descriptor to a socket that is connected to the socket passed to your
        // module's companion request handler. Returns -1 if the connection attempt failed.
        // 返回一个文件描述符到一个套接字，该套接字连接到传递给模块的伴随请求处理器的套接字。
        // 如果连接尝试失败，则返回 -1。
        int connectCompanion();

        // Get the file descriptor of the root folder of the current module.
        // 获取当前模块的根文件夹的文件描述符。
        //
        // This API only works in the pre[XXX]Specialize methods.
        // 此 API 仅在 pre[XXX]Specialize 方法中工作。
        // Accessing the directory returned is only possible in the pre[XXX]Specialize methods
        // or in the root companion process (assuming that you sent the fd over the socket).
        // 只有在 pre[XXX]Specialize 方法或根伴随进程中（假设你通过套接字发送了 fd）才能访问返回的目录。
        // Both restrictions are due to SELinux and UID.
        // 这两个限制都是由于 SELinux 和 UID。
        //
        // Returns -1 if errors occurred.
        // 如果发生错误，则返回 -1。
        int getModuleDir();

        // Set various options for your module.
        // 为你的模块设置各种选项。
        // Please note that this method accepts one single option at a time.
        // 请注意，此方法一次只接受一个选项。
        // Check zygisk::Option for the full list of options available.
        // 查看 zygisk::Option 以获取可用选项的完整列表。
        void setOption(Option opt);

        // Get information about the current process.
        // 获取有关当前进程的信息。
        // Returns bitwise-or'd zygisk::StateFlag values.
        // 返回按位或的 zygisk::StateFlag 值。
        uint32_t getFlags();

        // Exempt the provided file descriptor from being automatically closed.
        // 免除提供的文件描述符被自动关闭。
        //
        // This API only make sense in preAppSpecialize; calling this method in any other situation
        // is either a no-op (returns true) or an error (returns false).
        // 此 API 仅在 preAppSpecialize 中有意义；
        // 在任何其他情况下调用此方法要么是无操作（返回 true），要么是错误（返回 false）。
        //
        // When false is returned, the provided file descriptor will eventually be closed by zygote.
        // 当返回 false 时，提供的文件描述符最终将被 zygote 关闭。
        bool exemptFd(int fd);

        // Hook JNI native methods for a class
        // 为一个类挂钩 JNI 原生方法
        //
        // Lookup all registered JNI native methods and replace it with your own methods.
        // 查找所有注册的 JNI 原生方法并用你自己的方法替换它。
        // The original function pointer will be saved in each JNINativeMethod's fnPtr.
        // 原始函数指针将被保存在每个 JNINativeMethod 的 fnPtr 中。
        // If no matching class, method name, or signature is found, that specific JNINativeMethod.fnPtr
        // will be set to nullptr.
        // 如果没有找到匹配的类、方法名或签名，那么特定的 JNINativeMethod.fnPtr 将被设置为 nullptr。
        void hookJniNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *methods,
                                  int numMethods);

        // Hook functions in the PLT (Procedure Linkage Table) of ELFs loaded in memory.
        // hook在内存中加载的 ELF 文件的 PLT（程序链接表）中得函数。
        //
        // Parsing /proc/[PID]/maps will give you the memory map of a process. As an example:
        // 解析 /proc/[PID]/maps 将给你一个进程的内存映射。例如：
        //
        //       <address>       <perms>  <offset>   <dev>  <inode>           <pathname>
        //       地址             权限    偏移量     设备    节点               路径名
        // 56b4346000-56b4347000  r-xp    00002000   fe:00    235       /system/bin/app_process64
        // (More details: https://man7.org/linux/man-pages/man5/proc.5.html)
        // （更多详情请访问：https://man7.org/linux/man-pages/man5/proc.5.html）
        //
        // The `dev` and `inode` pair uniquely identifies a file being mapped into memory.
        // `dev` 和 `inode` 对唯一标识了被映射到内存中的文件。
        // For matching ELFs loaded in memory, replace function `symbol` with `newFunc`.
        // 对于匹配的在内存中加载的 ELF 文件，将函数 `symbol` 替换为 `newFunc`。
        // If `oldFunc` is not nullptr, the original function pointer will be saved to `oldFunc`.
        // 如果 `oldFunc` 不是 nullptr，原始的函数指针将被保存到 `oldFunc`。
        void
        pltHookRegister(dev_t dev, ino_t inode, const char *symbol, void *newFunc, void **oldFunc);

        // Commit all the hooks that was previously registered.
        // 提交之前注册的所有钩子。
        // Returns false if an error occurred.
        // 如果发生错误，返回 false。
        bool pltHookCommit();

    private:
        internal::api_table *tbl;

        template<class T>
        friend void internal::entry_impl(internal::api_table *, JNIEnv *);
    };

// Register a class as a Zygisk module

#define REGISTER_ZYGISK_MODULE(clazz) \
void zygisk_module_entry(zygisk::internal::api_table *table, JNIEnv *env) { \
    zygisk::internal::entry_impl<clazz>(table, env);                        \
}

// Register a root companion request handler function for your module
// 为模块注册root伴随请求处理程序函数

// The function runs in a superuser daemon process and handles a root companion request from
// your module running in a target process.
// 该函数在超级用户守护进程中运行，并处理来自目标进程中运行的模块的根伴随请求。
// The function has to accept an integer value,
// which is a Unix domain socket that is connected to the target process.
// 函数必须接受一个整数值，该整数值是连接到目标进程的Unix socket。

// See Api::connectCompanion() for more info.
//
// NOTE:
// the function can run concurrently on multiple threads.
// Be aware of race conditions if you have globally shared resources.
// 该函数可以在多个线程上同时运行。如果您拥有全球共享的资源，请注意种族状况。

#define REGISTER_ZYGISK_COMPANION(func) \
void zygisk_companion_entry(int client) { func(client); }

/*********************************************************
 * The following is internal ABI implementation detail.
 * You do not have to understand what it is doing.
 *********************************************************/

    namespace internal {

        struct module_abi {
            long api_version;
            ModuleBase *impl;

            void (*preAppSpecialize)(ModuleBase *, AppSpecializeArgs *);

            void (*postAppSpecialize)(ModuleBase *, const AppSpecializeArgs *);

            void (*preServerSpecialize)(ModuleBase *, ServerSpecializeArgs *);

            void (*postServerSpecialize)(ModuleBase *, const ServerSpecializeArgs *);

            module_abi(ModuleBase *

            module) : api_version(ZYGISK_API_VERSION), impl(module) {
                preAppSpecialize = [](auto m, auto args) { m->preAppSpecialize(args); };
                postAppSpecialize = [](auto m, auto args) { m->postAppSpecialize(args); };
                preServerSpecialize = [](auto m, auto args) { m->preServerSpecialize(args); };
                postServerSpecialize = [](auto m, auto args) { m->postServerSpecialize(args); };
            }
        };

        struct api_table {
            // Base
            void *impl;

            bool (*registerModule)(api_table *, module_abi *);

            void (*hookJniNativeMethods)(JNIEnv *, const char *, JNINativeMethod *, int);

            void (*pltHookRegister)(dev_t, ino_t, const char *, void *, void **);

            bool (*exemptFd)(int);

            bool (*pltHookCommit)();

            int (*connectCompanion)(void * /* impl */);

            void (*setOption)(void * /* impl */, Option);

            int (*getModuleDir)(void * /* impl */);

            uint32_t (*getFlags)(void * /* impl */);
        };

        template<class T>
        void entry_impl(api_table *table, JNIEnv *env) {
            static Api api;
            api.tbl = table;
            static T module;
            ModuleBase *m = &module;
            static module_abi abi(m);
            if (!table->registerModule(table, &abi)) return;
            m->onLoad(&api, env);
        }

    } // namespace internal

    inline int Api::connectCompanion() {
        return tbl->connectCompanion ? tbl->connectCompanion(tbl->impl) : -1;
    }

    inline int Api::getModuleDir() {
        return tbl->getModuleDir ? tbl->getModuleDir(tbl->impl) : -1;
    }

    inline void Api::setOption(Option opt) {
        if (tbl->setOption) tbl->setOption(tbl->impl, opt);
    }

    inline uint32_t Api::getFlags() {
        return tbl->getFlags ? tbl->getFlags(tbl->impl) : 0;
    }

    inline bool Api::exemptFd(int fd) {
        return tbl->exemptFd != nullptr && tbl->exemptFd(fd);
    }

    inline void
    Api::hookJniNativeMethods(JNIEnv *env, const char *className, JNINativeMethod *methods,
                              int numMethods) {
        if (tbl->hookJniNativeMethods)
            tbl->hookJniNativeMethods(env, className, methods, numMethods);
    }

    inline void Api::pltHookRegister(dev_t dev, ino_t inode, const char *symbol, void *newFunc,
                                     void **oldFunc) {
        if (tbl->pltHookRegister) tbl->pltHookRegister(dev, inode, symbol, newFunc, oldFunc);
    }

    inline bool Api::pltHookCommit() {
        return tbl->pltHookCommit != nullptr && tbl->pltHookCommit();
    }

} // namespace zygisk

extern "C" {

[[gnu::visibility("default"), maybe_unused]]
void zygisk_module_entry(zygisk::internal::api_table *, JNIEnv *);

[[gnu::visibility("default"), maybe_unused]]
void zygisk_companion_entry(int);

} // extern "C"
