#include "IORelocator.h"

#include "libpath.h"
#include "ZhenxiLog.h"
#include "elf_util.h"
#include "SandboxFs.h"

#include "MapItemInfo.h"
#include "fileUtils.h"
#include "HookUtils.h"
#include "parse.h"
#include "fileUtils.h"
#include "appUtils.h"
#include "version.h"
#include "xdl.h"

#include "BinarySyscallFinder.h"

#include "linkerHandler.h"
#include "Symbol.h"
#include "mylibc.h"
#include "xunwind.h"
#include "xdl.h"
#include "adapter.h"


using namespace ZhenxiRunTime;
using namespace StringUtils;
using namespace std;


bool need_load_env = true;
bool skip_kill = false;

//这个轻易不要关,发现错误早解决,只有在release才会关闭
#ifdef ZHENXI_BUILD_TYPE
bool debug_kill = false;
#else
bool debug_kill = true;
#endif


int g_preview_api_level = 0;
int g_api_level = 0;


#define HOOK_SYMBOL(handle, func)  \
hook_libc_function(handle, #func, (void*) new_##func, (void**) &orig_##func) \




//void onSoLoadedBefore(const char *name) {
//
//}

//void onSoLoadedAfter(const char *org_path, [[maybe_unused]] const char *new_path,[[maybe_unused]] void *handle) {
//    //根据 maps得到so的开始地址和结束地址
//    auto mapInfo = getSoBaseAddress(org_path);
//    //说明没找到,被io重定向的so都是需要用new_path去查找
//    if(mapInfo.start == 0) {
//        mapInfo = getSoBaseAddress(new_path);
//    }
//    LOGE("%s %s "
//         "start-> 0x%zx  end-> 0x%zx  size -> %lu  ",
//         org_path,new_path, mapInfo.start, mapInfo.end, (mapInfo.end - mapInfo.start))
//
//    if(ZhenxiRunTime::linkerLoad::linkerCallBackList!= nullptr) {
//        for(const auto &callback: (*linkerLoad::linkerCallBackList)) {
//            callback->loadAfter(org_path);
//        }
//    }
//}

int inline getArrayItemCount(char *const array[]) {
    int i;
    for (i = 0; array[i]; ++i);
    return i;
}


void IOUniformer::init_env_before_all() {
    if (!need_load_env) {
        return;
    }
    need_load_env = false;
    char *ld_preload = getenv("LD_PRELOAD");
    //ALOGE("init_env_before_all LD_PRELOAD %s ",ld_preload)
    if (!ld_preload || !strstr(ld_preload, CORE_SO_NAME)) {
        return;
    }

    char src_key[KEY_MAX];
    char dst_key[KEY_MAX];
    int i = 0;
    while (true) {
        memset(src_key, 0, sizeof(src_key));
        memset(dst_key, 0, sizeof(dst_key));
        sprintf(src_key, "V_REPLACE_ITEM_SRC_%d", i);
        sprintf(dst_key, "V_REPLACE_ITEM_DST_%d", i);
        char *src_value = getenv(src_key);
        if (!src_value) {
            break;
        }
        char *dst_value = getenv(dst_key);
        add_replace_item(src_value, dst_value);
        i++;
    }
    i = 0;
    while (true) {
        memset(src_key, 0, sizeof(src_key));
        sprintf(src_key, "V_KEEP_ITEM_%d", i);
        char *keep_value = getenv(src_key);
        if (!keep_value) {
            break;
        }
        add_keep_item(keep_value);
        i++;
    }
    i = 0;
    while (true) {
        memset(src_key, 0, sizeof(src_key));
        sprintf(src_key, "V_FORBID_ITEM_%d", i);
        char *forbid_value = getenv(src_key);
        if (!forbid_value) {
            break;
        }
        add_forbidden_item(forbid_value);
        i++;
    }
    char *api_level_char = getenv("V_API_LEVEL");
    char *preview_api_level_chars = getenv("V_PREVIEW_API_LEVEL");
    if (api_level_char != nullptr) {
        int api_level = atoi(api_level_char);
        g_api_level = api_level;
        int preview_api_level;
        preview_api_level = atoi(preview_api_level_chars);
        g_preview_api_level = preview_api_level;
        startIOHook(nullptr, api_level);
    }
    ALOGE("init_env_before_all 初始化完毕")

}


void IOUniformer::redirect(const char *orig_path, const char *new_path) {
    add_replace_item(orig_path, new_path);
}

const char *IOUniformer::query(const char *orig_path, char *const buffer, const size_t size) {
    return relocate_path(orig_path, buffer, size);
}

void IOUniformer::whitelist(const char *_path) {
    add_keep_item(_path);
}

void IOUniformer::forbid(const char *_path) {
    add_forbidden_item(_path);
}

void IOUniformer::readOnly(const char *_path) {
    add_readonly_item(_path);
}

const char *IOUniformer::reverse(const char *_path, char *const buffer, const size_t size) {
    return reverse_relocate_path(_path, buffer, size, false);
}



// int faccessat(int dirfd, const char *pathname, int mode, int flags);
HOOK_DEF(int, faccessat, int dirfd, const char *pathname, int mode, int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path && !(mode & W_OK && isReadOnly(relocated_path))) {
        return (int) syscall(__NR_faccessat, dirfd, relocated_path, mode, flags);
    }
    errno = EACCES;
    return -1;
}

// int fchmodat(int dirfd, const char *pathname, mode_t mode, int flags);
HOOK_DEF(int, fchmodat, int dirfd, const char *pathname, mode_t mode, int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return (int) syscall(__NR_fchmodat, dirfd, relocated_path, mode, flags);
    }
    errno = EACCES;
    return -1;
}

// int fstatat64(int dirfd, const char *pathname, struct stat *buf, int flags);
HOOK_DEF(int, fstatat64, int dirfd, const char *pathname, struct stat *buf, int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        long ret;
#if defined(__arm__) || defined(__i386__)
        ret = syscall(__NR_fstatat64, dirfd, relocated_path, buf, flags);
#else
        ret = syscall(__NR_newfstatat, dirfd, relocated_path, buf, flags);
#endif
        return (int) ret;
    }
    errno = EACCES;
    return -1;
}
// void abort(void);
HOOK_DEF(void, abort) {
//    Dl_info info;
//    dladdr((void *) __builtin_return_address(0), &info);
//    ALOGE("find call abort  %s   %s  base -> 0x%lx  call -> 0x%zx  ",
//          info.dli_sname,
//          info.dli_fname,
//          (long) info.dli_fbase,
//          (long) info.dli_saddr
//          )
    return orig_abort();
    //劫持,我们自己进行打印,防止被原始handler处理
    //abort();
}

// int kill(pid_t pid, int sig);
HOOK_DEF(int, kill, pid_t pid, int sig) {
//    char *name = parse::get_process_name();
//    ALOGE("IO find kill >>> pid : %d, sig : %d  %s", pid, sig, name)
//    free(name);

    if (debug_kill && (sig == SIGKILL ||sig == SIGSEGV||sig == SIGIOT)) {
        ZhenxiRuntime::getExitSignInfo(
                "libc kill find sig ",(int )sig,
                (void*) __builtin_return_address(0), nullptr,getpid(),gettid());
    }
    if (skip_kill) {
        return 1;
    }
    return (int) syscall(__NR_kill, pid, sig);
}
//这个也是一个发送信号函数
//HOOK_DEF(int, raise, int sig) {
//
//}
#ifndef __LP64__

// int __statfs64(const char *path, size_t size, struct statfs *stat);
HOOK_DEF(int, __statfs64, const char *pathname, size_t size, struct statfs *stat) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_statfs64, relocated_path, size, stat);
    }
    errno = EACCES;
    return -1;
}

// int __open(const char *pathname, int flags, int mode);
HOOK_DEF(int, __open, const char *pathname, int flags, int mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path && !((flags & O_WRONLY || flags & O_RDWR) && isReadOnly(relocated_path))) {
        return syscall(__NR_open, relocated_path, flags, mode);
    }
    errno = EACCES;
    return -1;
}

// ssize_t readlink(const char *path, char *buf, size_t bufsiz);
HOOK_DEF(ssize_t, readlink, const char *pathname, char *buf, size_t bufsiz) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        long ret = syscall(__NR_readlink, relocated_path, buf, bufsiz);
        if (ret < 0) {
            return ret;
        } else {
            // relocate link content
            if (reverse_relocate_path_inplace(buf, bufsiz) != -1) {
                return ret;
            }
        }
    }
    errno = EACCES;
    return -1;
}

// int mkdir(const char *pathname, mode_t mode);
HOOK_DEF(int, mkdir, const char *pathname, mode_t mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_mkdir, relocated_path, mode);
    }
    errno = EACCES;
    return -1;
}

// int rmdir(const char *pathname);
HOOK_DEF(int, rmdir, const char *pathname) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_rmdir, relocated_path);
    }
    errno = EACCES;
    return -1;
}

// int lchown(const char *pathname, uid_t owner, gid_t group);
HOOK_DEF(int, lchown, const char *pathname, uid_t owner, gid_t group) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_lchown, relocated_path, owner, group);
    }
    errno = EACCES;
    return -1;
}

// int utimes(const char *filename, const struct timeval *tvp);
HOOK_DEF(int, utimes, const char *pathname, const struct timeval *tvp) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_utimes, relocated_path, tvp);
    }
    errno = EACCES;
    return -1;
}

// int link(const char *oldpath, const char *newpath);
HOOK_DEF(int, link, const char *oldpath, const char *newpath) {
    char temp[PATH_MAX];
    const char *relocated_path_old = relocate_path(oldpath, temp, sizeof(temp));
    if (relocated_path_old) {
        return syscall(__NR_link, relocated_path_old, newpath);
    }
    errno = EACCES;
    return -1;
}

// int access(const char *pathname, int mode);
HOOK_DEF(int, access, const char *pathname, int mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path && !(mode & W_OK && isReadOnly(relocated_path))) {
        return syscall(__NR_access, relocated_path, mode);
    }
    errno = EACCES;
    return -1;
}

// int chmod(const char *path, mode_t mode);
HOOK_DEF(int, chmod, const char *pathname, mode_t mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_chmod, relocated_path, mode);
    }
    errno = EACCES;
    return -1;
}

// int chown(const char *path, uid_t owner, gid_t group);
HOOK_DEF(int, chown, const char *pathname, uid_t owner, gid_t group) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_chown, relocated_path, owner, group);
    }
    errno = EACCES;
    return -1;
}

// int lstat(const char *path, struct stat *buf);
HOOK_DEF(int, lstat, const char *pathname, struct stat *buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_lstat64, relocated_path, buf);
    }
    errno = EACCES;
    return -1;
}

// int stat(const char *path, struct stat *buf);
HOOK_DEF(int, stat, const char *pathname, struct stat *buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        long ret = syscall(__NR_stat64, relocated_path, buf);
        if (isReadOnly(relocated_path)) {
            buf->st_mode &= ~S_IWGRP;
        }
        return ret;
    }
    errno = EACCES;
    return -1;
}

// int symlink(const char *oldpath, const char *newpath);
HOOK_DEF(int, symlink, const char *oldpath, const char *newpath) {
    char temp[PATH_MAX];
    const char *relocated_path_old = relocate_path(oldpath, temp, sizeof(temp));
    if (relocated_path_old) {
        return syscall(__NR_symlink, relocated_path_old, newpath);
    }
    errno = EACCES;
    return -1;
}

// int unlink(const char *pathname);
HOOK_DEF(int, unlink, const char *pathname) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path && !isReadOnly(relocated_path)) {
        return syscall(__NR_unlink, relocated_path);
    }
    errno = EACCES;
    return -1;
}

// int fchmod(const char *pathname, mode_t mode);
HOOK_DEF(int, fchmod, const char *pathname, mode_t mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_fchmod, relocated_path, mode);
    }
    errno = EACCES;
    return -1;
}


// int fstatat(int dirfd, const char *pathname, struct stat *buf, int flags);
HOOK_DEF(int, fstatat, int dirfd, const char *pathname, struct stat *buf, int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_fstatat64, dirfd, relocated_path, buf, flags);
    }
    errno = EACCES;
    return -1;
}

// int fstat(const char *pathname, struct stat *buf, int flags);
HOOK_DEF(int, fstat, const char *pathname, struct stat *buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_fstat64, relocated_path, buf);
    }
    errno = EACCES;
    return -1;
}

// int mknod(const char *pathname, mode_t mode, dev_t dev);
HOOK_DEF(int, mknod, const char *pathname, mode_t mode, dev_t dev) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return syscall(__NR_mknod, relocated_path, mode, dev);
    }
    errno = EACCES;
    return -1;
}

// int rename(const char *oldpath, const char *newpath);
HOOK_DEF(int, rename, const char *oldpath, const char *newpath) {
    char temp_old[PATH_MAX], temp_new[PATH_MAX];
    const char *relocated_path_old = relocate_path(oldpath, temp_old, sizeof(temp_old));
    const char *relocated_path_new = relocate_path(newpath, temp_new, sizeof(temp_new));
    if (relocated_path_old && relocated_path_new) {
        return syscall(__NR_rename, relocated_path_old, relocated_path_new);
    }
    errno = EACCES;
    return -1;
}

#endif


// int mknodat(int dirfd, const char *pathname, mode_t mode, dev_t dev);
HOOK_DEF(int, mknodat, int dirfd, const char *pathname, mode_t mode, dev_t dev) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return (int) syscall(__NR_mknodat, dirfd, relocated_path, mode, dev);
    }
    errno = EACCES;
    return -1;
}

// int utimensat(int dirfd, const char *pathname, const struct timespec times[2], int flags);
HOOK_DEF(int, utimensat, int dirfd, const char *pathname, const struct timespec times[2],
         int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return (int) syscall(__NR_utimensat, dirfd, relocated_path, times, flags);
    }
    errno = EACCES;
    return -1;
}

// int fchownat(int dirfd, const char *pathname, uid_t owner, gid_t group, int flags);
HOOK_DEF(int, fchownat, int dirfd, const char *pathname, uid_t owner, gid_t group, int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return (int) syscall(__NR_fchownat, dirfd, relocated_path, owner, group, flags);
    }
    errno = EACCES;
    return -1;
}

// int chroot(const char *pathname);
HOOK_DEF(int, chroot, const char *pathname) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return (int) syscall(__NR_chroot, relocated_path);
    }
    errno = EACCES;
    return -1;
}

// int renameat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath);
HOOK_DEF(int, renameat, [[maybe_unused]] int olddirfd, const char *oldpath,
         [[maybe_unused]] int newdirfd, const char *newpath) {
    char temp_old[PATH_MAX], temp_new[PATH_MAX];
    const char *relocated_path_old = relocate_path(oldpath, temp_old, sizeof(temp_old));
    const char *relocated_path_new = relocate_path(newpath, temp_new, sizeof(temp_new));
    if (relocated_path_old && relocated_path_new) {
        return (int) syscall(__NR_renameat, olddirfd, relocated_path_old, newdirfd,
                             relocated_path_new);
    }
    errno = EACCES;
    return -1;
}

// int statfs64(const char *__path, struct statfs64 *__buf) __INTRODUCED_IN(21);
HOOK_DEF(int, statfs64, const char *filename, struct statfs64 *buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(filename, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return (int) syscall(__NR_statfs, relocated_path, buf);
    }
    errno = EACCES;
    return -1;
}

// int unlinkat(int dirfd, const char *pathname, int flags);
HOOK_DEF(int, unlinkat, int dirfd, const char *pathname, int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path && !isReadOnly(relocated_path)) {
        return (int) syscall(__NR_unlinkat, dirfd, relocated_path, flags);
    }
    errno = EACCES;
    return -1;
}

// int symlinkat(const char *oldpath, int newdirfd, const char *newpath);
HOOK_DEF(int, symlinkat, const char *oldpath, [[maybe_unused]] int newdirfd, const char *newpath) {
    char temp[PATH_MAX];
    const char *relocated_path_old = relocate_path(oldpath, temp, sizeof(temp));
    if (relocated_path_old) {
        return (int) syscall(__NR_symlinkat, relocated_path_old, newdirfd, newpath);
    }
    errno = EACCES;
    return -1;
}

// int linkat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath, int flags);
HOOK_DEF(int, linkat, [[maybe_unused]] int olddirfd, const char *oldpath,
         [[maybe_unused]] int newdirfd, const char *newpath,
         int flags) {
    char temp[PATH_MAX];
    const char *relocated_path_old = relocate_path(oldpath, temp, sizeof(temp));
    if (relocated_path_old) {
        return (int) syscall(__NR_linkat, olddirfd, relocated_path_old, newdirfd, newpath,
                             flags);
    }
    errno = EACCES;
    return -1;
}

// int mkdirat(int dirfd, const char *pathname, mode_t mode);
HOOK_DEF(int, mkdirat, int dirfd, const char *pathname, mode_t mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    //LOGI(">>>>>>>>>>>>  io mkdirat is called -> %s  %s ",pathname,relocated_path);
    if (__predict_true(relocated_path)) {
        return syscall(__NR_mkdirat, dirfd, relocated_path, mode);
    }
    errno = EACCES;
    return -1;
}

// int readlinkat(int dirfd, const char *pathname, char *buf, size_t bufsiz);
HOOK_DEF(int, readlinkat, int dirfd, const char *pathname, char *buf, size_t bufsiz) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        long ret = syscall(__NR_readlinkat, dirfd, relocated_path, buf, bufsiz);
        if (ret < 0) {
            return (int) ret;
        } else {
            // relocate link content
            if (reverse_relocate_path_inplace(buf, bufsiz) != -1) {
                return (int) ret;
            }
        }
    }
    errno = EACCES;
    return -1;
}


// int truncate(const char *path, off_t length);
HOOK_DEF(int, truncate, const char *pathname, off_t length) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return (int) syscall(__NR_truncate, relocated_path, length);
    }
    errno = EACCES;
    return -1;
}

// int chdir(const char *path);
HOOK_DEF(int, chdir, const char *pathname) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return (int) syscall(__NR_chdir, relocated_path);
    }
    errno = EACCES;
    return -1;
}

// int __getcwd(char *buf, size_t size);
HOOK_DEF(int, __getcwd, char *buf, size_t size) {
    long ret = syscall(__NR_getcwd, buf, size);
    if (!ret) {
        if (reverse_relocate_path_inplace(buf, size) < 0) {
            errno = EACCES;
            return -1;
        }
    }
    return (int) ret;
}

/**
 * 判断当前dir是不是一个fd,不是则返回null,是则返回fd的路径
 */
string is_reading_fd(string fd) {
    if (!StringUtils::isNumeric(fd)) {
        return {};
    }
    if (fcntl(std::stoi(fd), F_GETFD) == -1) {
        return {};
    }
    // Read the symlink target.
    std::string symlink_path = "/proc/self/fd/" + fd;
    char target_path[PATH_MAX];
    ssize_t target_len = readlink(symlink_path.c_str(), target_path, sizeof(target_path) - 1);
    if (target_len < 0) {
        return {};
    }
    target_path[target_len] = '\0';
    return target_path;
}

// FILE *popen(const char *cmd_path, const char *mode);
HOOK_DEF(FILE*, popen, const char *cmd, const char *mode) {
    if (cmd == nullptr) {
        return orig_popen(cmd, mode);
    }
    return orig_popen(cmd, mode);
}
// 添加下划线是openat的内部实现方法, 本身的openat因为太短的关系不好Hook
// int __openat(int fd, const char *pathname, int flags, int mode);
HOOK_DEF(int, __openat, int fd, const char *pathname, int flags, int mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
//        int fake_fd = redirect_proc_maps(relocated_path, flags, mode);
//        if (fake_fd != 0) {
//            return fake_fd;
//        }
        return (int) syscall(__NR_openat, fd, relocated_path, flags, mode);
    }
    errno = EACCES;
    return -1;
}


// int openat(int fd, const char *pathname, int flags, int mode);
HOOK_DEF(int, openat, int fd, const char *pathname, int flags, int mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
//        int fake_fd = redirect_proc_maps(relocated_path, flags, mode);
//        if (fake_fd != 0) {
//            return fake_fd;
//        }
        return (int) syscall(__NR_openat, fd, relocated_path, flags, mode);
    }
    errno = EACCES;
    return -1;
}


// int __statfs (__const char *__file, struct statfs *__buf);
HOOK_DEF(int, __statfs, [[maybe_unused]]__const char *__file,
         [[maybe_unused]] struct statfs *__buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(__file, temp, sizeof(temp));
    if (__predict_true(relocated_path)) {
        return (int) syscall(__NR_statfs, relocated_path, __buf);
    }
    errno = EACCES;
    return -1;
}

static struct sigaction old_sig_act{};

HOOK_DEF(int, sigaction, int sig, [[maybe_unused]] struct sigaction *new_act,
         [[maybe_unused]] struct sigaction *old_act) {
    if (sig != SIGABRT) {
        return orig_sigaction(sig, new_act, old_act);
    } else {
        if (old_act) {
            *old_act = old_sig_act;
        }
        if (new_act) {
            old_sig_act = *new_act;
        }
        return 0;
    }
}

/**
 * 在exec执行之前,增加环境信息
 * 将io重定向的item添加到exec新fork出来的进程里面
 */
[[maybe_unused]] static
char **relocate_envp(const char *pathname, char *const envp[]) {
    if (strstr(pathname, "libweexjsb.so")) {
        return const_cast<char **>(envp);
    }

    char *soPath64 = getenv(V_SO_PATH_64);
    char *soPath32 = getenv(V_SO_PATH_32);
    /**
     * 根据判断,当前env应该加载的环境So变量
     */
    char *env_so_path = nullptr;
    //执行之前先判断这个文件是否存在
    //如果程序执行失败的话,添加环境信息没有意义
    FILE *fd = fopen(pathname, "r");
    if (!fd) {
        return const_cast<char **>(envp);
    }
    for (int i = 0; i < 4; ++i) {
        fgetc(fd);
    }
    //根据可执行文件的第五个字节判断是32还是64
    int type = fgetc(fd);

    //判断被执行的程序是32还是64位
    if (type == ELFCLASS32) {
        env_so_path = soPath32;
    } else if (type == ELFCLASS64) {
        env_so_path = soPath64;
    }
    //判断结束关闭
    fclose(fd);
    if (env_so_path == nullptr) {
        LOGE("io relocator handler execve env env_so_path == null  ")
        return const_cast<char **>(envp);
    }
    //LOGI("io relocator handler execve env env_so_path -> %s  ", env_so_path)

    int len = 0;
    int ld_preload_index = -1;
    int self_so_index = -1;


    if (envp != nullptr) {
        //不等于null,开始查找LD_PRELOAD 的index
        while (envp[len]) {
            /* find LD_PRELOAD element */
            if (ld_preload_index == -1 && !strncmp(envp[len], "LD_PRELOAD=", 11)) {
                ld_preload_index = len;
            }
            //默认是64位
            if (self_so_index == -1 && !strncmp(envp[len], "V_SO_PATH_64=", 10)) {
                self_so_index = len;
            }
            ++len;
        }
    }

    /* append LD_PRELOAD element */
    if (ld_preload_index == -1) {
        ++len;
    }
    //如果没有查找到我们自己的SO的index,重新计算len的长度
    /* append V_env element */
    if (self_so_index == -1) {
        // V_SO_PATH_64
        // V_API_LEVEL
        // V_PREVIEW_API_LEVEL
        // V_NATIVE_PATH
        // 添加四个上述变量
        len += 4;
        if (soPath32) {
            //如果是32位额外添加一个
            // V_SO_PATH_32
            len++;
        }
        len += get_keep_item_count();
        len += get_forbidden_item_count();
        len += get_replace_item_count() * 2;
    }
    //附加结束符
    /* append NULL element */
    ++len;

    //将新创建的变量赋值
    char **relocated_envp = (char **) malloc(len * sizeof(char *));
    memset(relocated_envp, 0, len * sizeof(char *));
    if (envp != nullptr) {
        for (int i = 0; envp[i]; ++i) {
            //不要覆盖ld_preload的index
            if (i != ld_preload_index) {
                relocated_envp[i] = strdup(envp[i]);
            }
        }
    }
    //设置LD_PRELOAD带上我们自己的SO
    char LD_PRELOAD_VARIABLE[PATH_MAX];
    if (ld_preload_index == -1) {
        //放在倒数第二个
        ld_preload_index = len - 2;
        sprintf(LD_PRELOAD_VARIABLE, "LD_PRELOAD=%s", env_so_path);
    } else {
        const char *orig_ld_preload = envp[ld_preload_index] + 11;
        sprintf(LD_PRELOAD_VARIABLE, "LD_PRELOAD=%s:%s", env_so_path, orig_ld_preload);
    }

    relocated_envp[ld_preload_index] = strdup(LD_PRELOAD_VARIABLE);
    int index = 0;
    while (relocated_envp[index]) index++;
    if (self_so_index == -1) {
        char element[PATH_MAX] = {0};
        sprintf(element, "V_SO_PATH_64=%s", soPath64);
        relocated_envp[index++] = strdup(element);
        if (soPath32) {
            sprintf(element, "V_SO_PATH_32=%s", soPath32);
            relocated_envp[index++] = strdup(element);
        }
        sprintf(element, "V_API_LEVEL=%s", getenv("V_API_LEVEL"));
        relocated_envp[index++] = strdup(element);
        sprintf(element, "V_PREVIEW_API_LEVEL=%s", getenv("V_PREVIEW_API_LEVEL"));
        relocated_envp[index++] = strdup(element);
        sprintf(element, "V_NATIVE_PATH=%s", getenv("V_NATIVE_PATH"));
        relocated_envp[index++] = strdup(element);

        for (int i = 0; i < get_keep_item_count(); ++i) {
            PathItem &item = get_keep_items()[i];
            char env[PATH_MAX] = {0};
            sprintf(env, "V_KEEP_ITEM_%d=%s", i, item.path);
            relocated_envp[index++] = strdup(env);
        }

        for (int i = 0; i < get_forbidden_item_count(); ++i) {
            PathItem &item = get_forbidden_items()[i];
            char env[PATH_MAX] = {0};
            sprintf(env, "V_FORBID_ITEM_%d=%s", i, item.path);
            relocated_envp[index++] = strdup(env);
        }

        for (int i = 0; i < get_replace_item_count(); ++i) {
            ReplaceItem &item = get_replace_items()[i];
            char src[PATH_MAX] = {0};
            char dst[PATH_MAX] = {0};
            sprintf(src, "V_REPLACE_ITEM_SRC_%d=%s", i, item.orig_path);
            sprintf(dst, "V_REPLACE_ITEM_DST_%d=%s", i, item.new_path);
            relocated_envp[index++] = strdup(src);
            relocated_envp[index++] = strdup(dst);
        }
    }
    return relocated_envp;
}


/**
 * disable inline
 * 替换exec参数,阻止方法的inline
 * 防止某些方法因为inline导致hook不到
 */
char **build_new_argv(char *const argv[]) {

    int orig_argv_count = getArrayItemCount(argv);

    int new_argv_count = orig_argv_count + 2;
    char **new_argv = (char **) malloc(new_argv_count * sizeof(char *));
    int cur = 0;
    for (int i = 0; i < orig_argv_count; ++i) {
        new_argv[cur++] = argv[i];
    }

    //(api_level == 28 && g_preview_api_level > 0) = Android Q Preview
    if (g_api_level >= ANDROID_L2 && g_api_level < ANDROID_Q) {
        new_argv[cur++] = (char *) "--compile-pic";
    }
    if (g_api_level >= ANDROID_M) {
        //将inline数量设置成0
        //编译器调优用设置成0,大于7.1适用
        new_argv[cur++] = (char *) (g_api_level > ANDROID_N2 ? "--inline-max-code-units=0"
                                                             : "--inline-depth-limit=0");
    }

    new_argv[cur] = nullptr;

    return new_argv;
}

/**
 * @param pathname 参数表示你要启动程序的名称包括路径名
 * @param argv 参数
 * @param envp 环境变量
 * @return
 */
// int (*origin_execve)(const char *pathname, char *const argv[], char *const envp[]);
HOOK_DEF(int, execve, const char *pathname,
         [[maybe_unused]] char *argv[],
         [[maybe_unused]] char *const envp[]
) {
    std::stringstream ss;
    int i = 0;
    while (argv[i] != nullptr) {
        ss << argv[i] << " ";
        i++;
    }
    ALOGI("io relocator find execve before  [%s] -> [%s]", pathname, ss.str().c_str())

    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (!relocated_path) {
        LOGE("io relocator execve relocate_path error %s ", pathname)
        errno = EACCES;
        return -1;
    }

    char **new_argv = nullptr;

    if (strstr(pathname, "dex2oat")) {
        LOGI("skip dex2oat ")
        //阻止方法被inline,防止hook不到
        new_argv = build_new_argv(argv);
    }
    //把exec启动的新进程,把我们的SO加载进去
    char **relocated_envp = relocate_envp(relocated_path, envp);

    //调用__NR_execve,执行成功则替换当前进程
    long ret = syscall(__NR_execve, relocated_path,
                        //是否需要处理inline
                       new_argv != nullptr ? new_argv : argv,
                        //设置环境信息
                       envp);
    if (ret < 0) {
        LOGE("io relocator execve error  %s", strerror(errno))
    }
    if (relocated_envp != envp) {
        int i = 0;
        while (relocated_envp[i] != nullptr) {
            free(relocated_envp[i]);
            ++i;
        }
        free(relocated_envp);
    }
    if (new_argv != nullptr) {
        free(new_argv);
    }
    return (int) ret;
}



//void *dlsym(void *handle, const char *symbol)
HOOK_DEF(void*, dlsym, void *handle, char *symbol) {
    return orig_dlsym(handle, symbol);
}

HOOK_DEF(pid_t, vfork) {
    return fork();
}

HOOK_DEF(bool, SetCheckJniEnabled, void *Vm, [[maybe_unused]] bool enbaled) {
    return orig_SetCheckJniEnabled(Vm, false);
}

HOOK_DEF(bool, is_accessible, void *thiz, const std::string &file) {
    return true;
}

//long syscall(long __number, ...);
HOOK_DEF(long, syscall, [[maybe_unused]] long __number, void *arg ...) {
    if (__number == __NR_kill) {
        va_list ap;
        va_start(ap, arg);
        pid_t pid = va_arg(ap, pid_t);
        int sigNum = va_arg(ap, int);
        if (sigNum == 9) {
            abort();
        }
    }
    return orig_syscall(__number, arg);
}


bool relocate_art(JNIEnv *env, const char *art_path) {
    intptr_t art_addr, art_off, symbol;
    if ((art_addr = get_addr(art_path)) == 0) {
        ALOGE("cannot found art address %s ", art_path)
        return false;
    }
    //disable jni check
    if (g_api_level >= ANDROID_L && env &&
        resolve_symbol(art_path, "_ZN3art9JavaVMExt18SetCheckJniEnabledEb",
                       &art_off) == 0) {
        symbol = art_addr + art_off;
        orig_SetCheckJniEnabled = reinterpret_cast<bool (*)(void *, bool)>(symbol);
        JavaVM *vm;
        env->GetJavaVM(&vm);
        //关闭jni检测
        orig_SetCheckJniEnabled(vm, false);
    }
    return true;
}

bool fuck_linker(const char *linker_path) {
    auto is_accessible_str = "__dl__ZN19android_namespace_t13is_accessibleERKNSt3__112basic_stringIcNS0_11char_traitsIcEENS0_9allocatorIcEEEE";
    void *is_accessible_addr = getSymCompat(linker_path, is_accessible_str);
    if (is_accessible_addr) {
        //这个方法是用于反射的,判断某个变量是否反射可以获取
        //method.setAccessible();相当于不用设置这个也可以进行获取
        HookUtils::Hooker(is_accessible_addr,
                          (void *) new_is_accessible,
                          (void **) &orig_is_accessible);
        if (orig_is_accessible != nullptr) {
            ALOGI("io hook linker namespace setAccessible  sucess!")
        } else {
            ALOGE("io hook linker namespace setAccessible fail! %s ", linker_path)
        }
    }
    return true;
}

//bool relocate_linker(const char *linker_path) {
//    intptr_t linker_addr, dlopen_off, symbol;
//    if ((linker_addr = get_addr(linker_path)) == 0) {
//        ALOGE("cannot found linker addr  %s", linker_path)
//        return false;
//    }
//    if (resolve_symbol(linker_path, "__dl__Z9do_dlopenPKciPK17android_dlextinfoPKv",
//                       &dlopen_off) == 0) {
//        symbol = linker_addr + dlopen_off;
//        HookUtils::Hooker((void *) symbol, (void *) new_do_dlopen_CIVV,
//                       (void **) &orig_do_dlopen_CIVV);
//        return true;
//    } else if (resolve_symbol(linker_path, "__dl__Z9do_dlopenPKciPK17android_dlextinfoPv",
//                              &dlopen_off) == 0) {
//        symbol = linker_addr + dlopen_off;
//        HookUtils::Hooker((void *) symbol, (void *) new_do_dlopen_CIVV,
//                       (void **) &orig_do_dlopen_CIVV);
//        return true;
//    } else if (resolve_symbol(linker_path, "__dl__ZL10dlopen_extPKciPK17android_dlextinfoPv",
//                              &dlopen_off) == 0) {
//        symbol = linker_addr + dlopen_off;
//        HookUtils::Hooker((void *) symbol, (void *) new_do_dlopen_CIVV,
//                       (void **) &orig_do_dlopen_CIVV);
//        return true;
//    } else if (
//            resolve_symbol(linker_path, "__dl__Z20__android_dlopen_extPKciPK17android_dlextinfoPKv",
//                           &dlopen_off) == 0) {
//        symbol = linker_addr + dlopen_off;
//        HookUtils::Hooker((void *) symbol, (void *) new_do_dlopen_CIVV,
//                       (void **) &orig_do_dlopen_CIVV);
//        return true;
//    } else if (
//            resolve_symbol(linker_path, "__dl___loader_android_dlopen_ext",
//                           &dlopen_off) == 0) {
//        symbol = linker_addr + dlopen_off;
//        HookUtils::Hooker((void *) symbol, (void *) new_do_dlopen_CIVV,
//                       (void **) &orig_do_dlopen_CIVV);
//        return true;
//    } else if (resolve_symbol(linker_path, "__dl__Z9do_dlopenPKciPK17android_dlextinfo",
//                              &dlopen_off) == 0) {
//        symbol = linker_addr + dlopen_off;
//        HookUtils::Hooker((void *) symbol, (void *) new_do_dlopen_CIV,
//                       (void **) &orig_do_dlopen_CIV);
//        return true;
//    } else if (resolve_symbol(linker_path, "__dl__Z8__dlopenPKciPKv",
//                              &dlopen_off) == 0) {
//        symbol = linker_addr + dlopen_off;
//        HookUtils::Hooker((void *) symbol, (void *) new_do_dlopen_CIV,
//                       (void **) &orig_do_dlopen_CIV);
//        return true;
//    } else if (resolve_symbol(linker_path, "__dl___loader_dlopen",
//                              &dlopen_off) == 0) {
//        symbol = linker_addr + dlopen_off;
//        HookUtils::Hooker((void *) symbol, (void *) new_do_dlopen_CIV,
//                       (void **) &orig_do_dlopen_CIV);
//        return true;
//    } else if (resolve_symbol(linker_path, "__dl_dlopen",
//                              &dlopen_off) == 0) {
//        symbol = linker_addr + dlopen_off;
//        HookUtils::Hooker((void *) symbol, (void *) new_dlopen_CI,
//                       (void **) &orig_dlopen_CI);
//        return true;
//    }
//    return false;
//}

#if defined(__aarch64__)

[[maybe_unused]]
bool on_found_syscall_aarch64([[maybe_unused]] const char *path, int num, void *func) {

    static uint pass = 0;
    //每次hook成功+1
    switch (num) {
        HOOK_SYSCALL(fchmodat)
        HOOK_SYSCALL(fchownat)
        HOOK_SYSCALL(renameat)
        HOOK_SYSCALL(mkdirat)// svc 部分拦截不到
        HOOK_SYSCALL(mknodat)
        HOOK_SYSCALL(truncate)
        HOOK_SYSCALL(linkat)
        HOOK_SYSCALL(faccessat)
        HOOK_SYSCALL_(statfs)
        HOOK_SYSCALL_(getcwd)
        HOOK_SYSCALL_(openat)
        HOOK_SYSCALL(readlinkat)
        HOOK_SYSCALL(unlinkat)
        HOOK_SYSCALL(symlinkat)
        HOOK_SYSCALL(utimensat)
        HOOK_SYSCALL(chdir)
        HOOK_SYSCALL(execve)
        HOOK_SYSCALL(kill)
    }
    if (pass == 17 || pass == 18) {
        ALOGI("64 io hook sucess by syscall  %d ", pass)
        return BREAK_FIND_SYSCALL;
    }
    //ALOGI("64 io hook by syscall  %s %d", stringify_sysnum_fornum(num), pass);
    return CONTINUE_FIND_SYSCALL;
}

[[maybe_unused]]
bool my_on_found_syscall_aarch64([[maybe_unused]] const char *path, int num, void *func) {

    static uint pass = 0;
    //每次hook成功+1
    switch (num) {
        HOOK_SYSCALL(mkdirat)// svc 部分拦截不到
        HOOK_SYSCALL(kill)
        HOOK_SYSCALL_(openat)
    }
    if (pass == 3) {
        ALOGI("64 io replace svc hook  success by syscall  %d ", pass)
        return BREAK_FIND_SYSCALL;
    }
    //ALOGI("64 io hook by syscall  %s %d", stringify_sysnum_fornum(num), pass);
    return CONTINUE_FIND_SYSCALL;
}

//bool on_found_linker_syscall_arch64([[maybe_unused]] const char *path, int num, void *func) {
//    //ALOGE("start hook linker load so for 64 %d  ",num);
//    static uint pass = 0;
//    switch (num) {
//        case __NR_openat:
//            HookUtils::Hooker(func, (void *) new___openat, (void **) &orig___openat);
//            return BREAK_FIND_SYSCALL;
//    }
//    if (pass == 5) {
//        return BREAK_FIND_SYSCALL;
//    }
//    return CONTINUE_FIND_SYSCALL;
//}

#else

//bool on_found_linker_syscall_arm(const char *path, int num, void *func) {
//    switch (num) {
//        case __NR_openat:
//            HookUtils::Hooker(func, (void *) new___openat, (void **) &orig___openat);
//            break;
//        case __NR_open:
//            HookUtils::Hooker(func, (void *) new___open, (void **) &orig___open);
//            break;
//    }
//    //ALOGE("32 on_found_linker_syscall_arm sucess!");
//    return CONTINUE_FIND_SYSCALL;
//}

bool on_found_libc_syscall_arm(const char *path, int num, void *func) {
    switch (num) {
        case __NR_kill:{
            ALOGI(">>>>>>>>>>>> 32  syscall find kill ");
            HookUtils::Hooker(func, (void *) new_kill, (void **) &orig_kill);
            return BREAK_FIND_SYSCALL;
        }
    }
    //ALOGE("32 on_found_linker_syscall_arm %s %d   ",stringify_sysnum(num),__NR_kill);
    return CONTINUE_FIND_SYSCALL;
}
#endif

[[maybe_unused]]
void ZhenxiInterruptHandler(int signum, siginfo_t *siginfo, void *uc) {
    ALOGE("################################### io find sign ,start abort !!! %d",signum)
    ALOGE("################################### io find sign ,start abort !!! %d",signum)
    ALOGE("################################### io find sign ,start abort !!! %d",signum)
    ALOGE("ZhenxiInterruptHandler Fault address: %p", siginfo->si_addr)
    ZhenxiRuntime::getExitSignInfo(
            "sigaction find sig ",(int )signum,
            (void*)siginfo->si_addr,uc,getpid(),gettid());

//    old_sig_act.sa_sigaction(signum, siginfo, uc);
//    abort();
}

/**
 * 打印linker加载的回调
 */
class RuntimeLinkerCallBack : public LinkerLoadCallBack {
public:
    void loadBefore(const char *path) const override {
    }

    void loadAfter(const char *path, const char *redirect_path, void *ret) const override {

    }
};


/**
 * 处理runtime的linker io重定向逻辑
 */
class RuntimeLinkerIORedirect : public LinkerIORedirect {
public:
    string linkerRedirect(const char *path) const override {
        char temp[PATH_MAX];
        return {relocate_path(path, temp, sizeof(temp))};
    }
};


void startIOHook(JNIEnv *env, int api_level) {

    const char *linker = getLinkerPath().c_str();
    const char *libc = getlibcPath().c_str();
    const char *art = getlibArtPath().c_str();
    if (api_level >= ANDROID_Q) {
        fuck_linker(linker);
    }

    if (api_level >= ANDROID_L && env) {
        relocate_art(env, art);
    }
    void *handle = dlopen("libc.so", RTLD_NOW);
    if (handle == nullptr) {
        ALOGI(">>>>>>>>>>>> io relocator get libc.so error !")
        return;
    }
    //在测试环境新增回调
//    if (debug_kill) {
//        //对中断信号函数进行拦截
//        struct sigaction sig={0};
//        sigemptyset(&sig.sa_mask);
//        sig.sa_flags = SA_SIGINFO;
//        sig.sa_sigaction = ZhenxiInterruptHandler;
//
//        //处理-6信号
//        if (sigaction(SIGABRT,
//                      &sig, &old_sig_act) != -1) {
//        }
//        //11信号
//        if (sigaction(SIGSEGV,
//                      &sig, &old_sig_act) != -1) {
//        }
//        //HOOK_SYMBOL(handle, sigaction);
//    }


#if defined(__aarch64__)
        //64底层都会走syscall ,所以能从syscall hook直接从syscall hook
        if (!findSyscalls(libc, on_found_syscall_aarch64)) {
            ALOGI("starting 64 io hook by fun, io hook not found  %s", libc)
            HOOK_SYMBOL(handle, fchownat);
            HOOK_SYMBOL(handle, renameat);
            HOOK_SYMBOL(handle, mkdirat);
            HOOK_SYMBOL(handle, mknodat);
            HOOK_SYMBOL(handle, truncate);
            HOOK_SYMBOL(handle, linkat);
            HOOK_SYMBOL(handle, readlinkat);
            HOOK_SYMBOL(handle, unlinkat);
            HOOK_SYMBOL(handle, symlinkat);
            HOOK_SYMBOL(handle, utimensat);
            HOOK_SYMBOL(handle, chdir);
            HOOK_SYMBOL(handle, execve);
            HOOK_SYMBOL(handle, statfs64);
            HOOK_SYMBOL(handle, kill);
            HOOK_SYMBOL(handle, vfork);
            HOOK_SYMBOL(handle, fstatat64);
        }
#else
            HOOK_SYMBOL(handle, faccessat);
            HOOK_SYMBOL(handle, access);
            HOOK_SYMBOL(handle, __openat);
            HOOK_SYMBOL(handle, fchmodat);
            HOOK_SYMBOL(handle, fchownat);
            HOOK_SYMBOL(handle, renameat);
            HOOK_SYMBOL(handle, fstatat64);
            HOOK_SYMBOL(handle, __statfs);
            HOOK_SYMBOL(handle, __statfs64);
            HOOK_SYMBOL(handle, mkdirat);
            HOOK_SYMBOL(handle, mknodat);
            HOOK_SYMBOL(handle, truncate);
            HOOK_SYMBOL(handle, linkat);
            HOOK_SYMBOL(handle, readlinkat);
            HOOK_SYMBOL(handle, unlinkat);
            HOOK_SYMBOL(handle, symlinkat);
            HOOK_SYMBOL(handle, utimensat);
            HOOK_SYMBOL(handle, __getcwd);
            HOOK_SYMBOL(handle, chdir);
            HOOK_SYMBOL(handle, execve);
            HOOK_SYMBOL(handle, kill);
            HOOK_SYMBOL(handle, vfork);
#endif

//#if defined(__aarch64__)
//    if (!findSyscalls(libc, my_on_found_syscall_aarch64)){
//        HOOK_SYMBOL(handle, mkdirat);
//        HOOK_SYMBOL(handle, openat);
//        HOOK_SYMBOL(handle, kill);
//    }
//#else
//        HOOK_SYMBOL(handle, mkdirat);
//        HOOK_SYMBOL(handle, __openat);
//        HOOK_SYMBOL(handle, kill);
//#endif

    //HOOK_SYMBOL(handle, execve);
    //HOOK_SYMBOL(handle, mkdirat);
    //HOOK_SYMBOL(handle, kill);
    //pass thead check
    //HOOK_SYMBOL(handle, readdir);
    //pass popen
    //HOOK_SYMBOL(handle, popen);

    //hook linker ,add callback
    linkerHandler::init();
    //添加linker加载so回调
    linkerHandler::addLinkerCallBack(new RuntimeLinkerCallBack());
    //添加处理linker 加载so路径处理重定向
    linkerHandler::linkerIORedirect(new RuntimeLinkerIORedirect());

    ALOGE(">>>>> IO Hook finish process-> [%s] pid -> %d  ", parse::get_process_name().c_str(),
          getpid())
    dlclose(handle);
}


void
IOUniformer::startUniformer(JNIEnv *env,
                            const char *so_path_32,
                            const char *so_path_64,
                            const char *native_path,
                            int api_level,
                            int preview_api_level) {
    char api_level_chars[56];
    setenv(V_SO_PATH_32, so_path_32, 1);
    setenv(V_SO_PATH_64, so_path_64, 1);
    sprintf(api_level_chars, "%i", api_level);
    setenv("V_API_LEVEL", api_level_chars, 1);
    sprintf(api_level_chars, "%i", preview_api_level);
    setenv("V_PREVIEW_API_LEVEL", api_level_chars, 1);
    setenv("V_NATIVE_PATH", native_path, 1);

    startIOHook(env, api_level);
}
