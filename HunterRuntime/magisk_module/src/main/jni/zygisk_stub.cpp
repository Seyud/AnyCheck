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

#include <cstdlib>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <iostream>
#include <cstring>
#include <cstdio>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/mount.h>
#include <cstdio>
#include <unistd.h>
#include <fcntl.h>
#include <sys/utsname.h>
#include <dirent.h>
#include <media/NdkMediaDrm.h>
#include <string>
#include <sstream>
#include <sys/vfs.h>
#include <iostream>
#include <sys/prctl.h>
#include <iostream>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <algorithm>
//#include <ranges>

#include "include/zygisk.hpp"
#include "ZhenxiLog.h"
#include "logging.h"
#include "mylibc.h"
#include "xdl.h"
//#include "jni_helper.hpp"
#include "MagiskRuntime.h"
#include "stringUtils.h"
#include "fileUtils.h"
#include "action.h"


using namespace std;
using namespace ZhenxiRuntime;

using zygisk::Api;
using zygisk::AppSpecializeArgs;
using zygisk::ServerSpecializeArgs;

//static constexpr const char *HUNTER_NAME = "com.zhenxi.hunter";
//static constexpr const char *TEST_APP_NAME = "com.example.test2";



/**
 * ptrace注入目标进程
 */
static constexpr const int PTRACE_TAG_PROCESS = 0x2;


namespace ZhenxiRuntime {

    namespace {
        ssize_t xsendmsg(int sockfd, const struct msghdr *msg, int flags) {
            int sent = sendmsg(sockfd, msg, flags);
            if (sent < 0) {
                PLOGE("sendmsg");
            }
            return sent;
        }

        ssize_t xrecvmsg(int sockfd, struct msghdr *msg, int flags) {
            int rec = recvmsg(sockfd, msg, flags);
            if (rec < 0) {
                PLOGE("recvmsg");
            }
            return rec;
        }

        // Read exact same size as count
        ssize_t xxread(int fd, void *buf, size_t count) {
            size_t read_sz = 0;
            ssize_t ret;
            do {
                ret = read(fd, (std::byte *) buf + read_sz, count - read_sz);
                if (ret < 0) {
                    if (errno == EINTR)
                        continue;
                    PLOGE("read");
                    return ret;
                }
                read_sz += ret;
            } while (read_sz != count && ret != 0);
            if (read_sz != count) {
                PLOGE("read ({} != {})", count, read_sz);
            }
            return read_sz;
        }

        // Write exact same size as count
        ssize_t xwrite(int fd, const void *buf, size_t count) {
            size_t write_sz = 0;
            ssize_t ret;
            do {
                ret = write(fd, (std::byte *) buf + write_sz, count - write_sz);
                if (ret < 0) {
                    if (errno == EINTR)
                        continue;
                    PLOGE("write");
                    return ret;
                }
                write_sz += ret;
            } while (write_sz != count && ret != 0);
            if (write_sz != count) {
                PLOGE("write ({} != {})", count, write_sz);
            }
            return write_sz;
        }

        int send_fds(int sockfd, void *cmsgbuf, size_t bufsz, const int *fds, int cnt) {
            iovec iov = {
                    .iov_base = &cnt,
                    .iov_len  = sizeof(cnt),
            };
            msghdr msg = {
                    .msg_iov        = &iov,
                    .msg_iovlen     = 1,
            };

            if (cnt) {
                msg.msg_control = cmsgbuf;
                msg.msg_controllen = bufsz;
                cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);
                cmsg->cmsg_len = CMSG_LEN(sizeof(int) * cnt);
                cmsg->cmsg_level = SOL_SOCKET;
                cmsg->cmsg_type = SCM_RIGHTS;

                memcpy(CMSG_DATA(cmsg), fds, sizeof(int) * cnt);
            }

            return xsendmsg(sockfd, &msg, 0);
        }

        int send_fd(int sockfd, int fd) {
            if (fd < 0) {
                return send_fds(sockfd, nullptr, 0, nullptr, 0);
            }
            char cmsgbuf[CMSG_SPACE(sizeof(int))];
            return send_fds(sockfd, cmsgbuf, sizeof(cmsgbuf), &fd, 1);
        }

        void *recv_fds(int sockfd, char *cmsgbuf, size_t bufsz, int cnt) {
            iovec iov = {
                    .iov_base = &cnt,
                    .iov_len  = sizeof(cnt),
            };
            msghdr msg = {
                    .msg_iov        = &iov,
                    .msg_iovlen     = 1,
                    .msg_control    = cmsgbuf,
                    .msg_controllen = bufsz
            };

            xrecvmsg(sockfd, &msg, MSG_WAITALL);
            cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);

            if (msg.msg_controllen != bufsz ||
                cmsg == nullptr ||
                cmsg->cmsg_len != CMSG_LEN(sizeof(int) * cnt) ||
                cmsg->cmsg_level != SOL_SOCKET ||
                cmsg->cmsg_type != SCM_RIGHTS) {
                return nullptr;
            }

            return CMSG_DATA(cmsg);
        }

        int recv_fd(int sockfd) {
            char cmsgbuf[CMSG_SPACE(sizeof(int))];

            void *data = recv_fds(sockfd, cmsgbuf, sizeof(cmsgbuf), 1);
            if (data == nullptr)
                return -1;

            int result;
            memcpy(&result, data, sizeof(int));
            return result;
        }

        int read_int(int fd) {
            int val;
            if (xxread(fd, &val, sizeof(val)) != sizeof(val))
                return -1;
            return val;
        }

        void write_int(int fd, int val) {
            if (fd < 0) return;
            xwrite(fd, &val, sizeof(val));
        }

        int allow_unload = 0;
    }

//    void installKernel(int client, int ModuleDir) {
//        write_int(client, action::INSTALL_KERNEL_DRIVE);
//        auto path = fileUtils::get_file_name(ModuleDir, getpid());
//        write_int(client, (int) path.length());
//        xwrite(client, path.c_str(), path.length());
//    }


    class RuntimeModule : public zygisk::ModuleBase {

    public:
        int root_client;

        void onLoad(Api *api, JNIEnv *env) override {
            this->api_ = api;
            this->env_ = env;
            skip_ = true;
            //LOGE(">>>>>>>>>>>>>>> RuntimeModule onLoad ")
            //init
            MagiskRuntime::getInstance()->MagiskRuntime::init(env_);
            //check base file
            MagiskRuntime::getInstance()->OnModuleLoaded();
            //get root client
            //root_client = api->connectCompanion();
            //install kernel
            //installKernel(root_client, api->getModuleDir());
            //close(client);
        }

        void preAppSpecialize(AppSpecializeArgs *args) override {
            skip_ = MagiskRuntime::ShouldSkip(args->is_child_zygote && *args->is_child_zygote,
                                              args->uid);

        }

        void postAppSpecialize(const AppSpecializeArgs *args) override {
            auto nice_name = env_->GetStringUTFChars(args->nice_name, nullptr);
            //LOGI("postAppSpecialize load nice_name %s ", nice_name)
            if (isLoadMainApk(nice_name)) {
                //LOGW("postAppSpecialize -> %s ", nice_name)
                MagiskRuntime::getInstance()->PostMainApp(env_, args->nice_name, true);
            }  else {
                api_->setOption(zygisk::Option::DLCLOSE_MODULE_LIBRARY);
            }
            env_->ReleaseStringUTFChars(args->nice_name, nice_name);
        }

        void preServerSpecialize(ServerSpecializeArgs *args) override {

        }

        void postServerSpecialize(const ServerSpecializeArgs *args) override {
            if (!MagiskRuntime::getInstance()->IsDisabled()) {
                LOGW("post system_service start")
                MagiskRuntime::getInstance()->PostForkSystemServer(env_);
            }
        }


    private:
        Api *api_ = nullptr;
        JNIEnv *env_ = nullptr;

        bool skip_ = true;

        //判断是否是tag apk
        bool isLoadMainApk(const char *nice_name) const {
            if (skip_) {
                return false;
            }
            if (MagiskRuntime::getInstance()->IsDisabled()) {
                return false;
            }
            //目标apk
            if (StringUtils::equals(nice_name, TAG_APP_NAME)) {
                return true;
            }
            return false;
        }

//        bool isRelevance(const char *nice_name) const {
//            static std::string apk_list[] = {
//                    //oaid
//                    "com.miui.securitycenter.remote"
//            };
//            if (skip_) {
//                return false;
//            }
//            if (MagiskRuntime::getInstance()->IsDisabled()) {
//                return false;
//            }
//            if (std::ranges::any_of(apk_list,
//                                    [&nice_name](const auto &apk) {
//                                        return StringUtils::equals(nice_name, apk);
//                                    })) {
//                //LOGE(">>>>>>>>>> isRelevance IS FIND %s  ", apk.c_str()); // You can log outside if needed
//                return true;
//            }
//
//            return false;
//        }

    };

    /**
     * Root进程运行,在客户端进程链接以后执行
     */
    void CompanionEntry(int client) {

    }


}


REGISTER_ZYGISK_MODULE(RuntimeModule)

REGISTER_ZYGISK_COMPANION(ZhenxiRuntime::CompanionEntry)
