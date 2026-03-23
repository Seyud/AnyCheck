//
// Created by Zhenxi on 2023/12/30.
//

#ifndef RUNTIME_ACTION_H
#define RUNTIME_ACTION_H

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

namespace ZhenxiRuntime {
    class action {
    public:
        /**
         * 装载Runtime驱动
         */
        static constexpr const int INSTALL_KERNEL_DRIVE = 0x1;
        /**
         * mount --bind orig_path new_path
         */
        static constexpr const int MOUNT_PATH = 0x2;


        static void installKerNelDrive(const std::string &module_path);

        static void mountPath(const std::string &orig_path,const std::string &new_path);
    private:

    };
}

#endif //RUNTIME_ACTION_H
