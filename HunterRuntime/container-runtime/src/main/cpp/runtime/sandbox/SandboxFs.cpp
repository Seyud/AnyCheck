#include <cstdlib>
#include <cstring>


#include "SandboxFs.h"
#include "canonicalize_md.h"
#include "dlfcn.h"
#include "parse.h"
#include "stringUtils.h"
#include "version.h"
#include "arch.h"
#include "adapter.h"
#include "xdl.h"
#include "appUtils.h"
#include "mylibc.h"
#include "pmparser.h"

#include <list>
#include <sys/shm.h>
#include <linux/ashmem.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <mylibc.h>
#include <android/sharedmem.h>

using namespace StringUtils;
using namespace ZhenxiRunTime;

PathItem *keep_items;
PathItem *forbidden_items;
PathItem *readonly_items;
ReplaceItem *replace_items;


int keep_item_count;
int forbidden_item_count;
int readonly_item_count;
int replace_item_count;

void getSandBoxIoPathInfo() {
    for (int i = 0; i < replace_item_count; i++) {
        ReplaceItem &item = replace_items[i];
        LOGE("IO重定向 org -> %s rep -> %s ", item.orig_path, item.new_path)
    }
    for (int i = 0; i < keep_item_count; i++) {
        PathItem &item = keep_items[i];
        LOGE("白名单目录 -> %s  ", item.path)
    }
    for (int i = 0; i < forbidden_item_count; i++) {
        PathItem &item = keep_items[i];
        LOGE("不可读目录 -> %s  ", item.path)
    }
    for (int i = 0; i < readonly_item_count; i++) {
        PathItem &item = keep_items[i];
        LOGE("只读目录 -> %s  ", item.path)
    }
    LOGE("---------------------------------------------")
}

int add_keep_item(const char *path) {
    char keep_env_name[KEY_MAX];
    sprintf(keep_env_name, "V_KEEP_ITEM_%d", keep_item_count);
    setenv(keep_env_name, path, 1);
    keep_items = (PathItem *) realloc(keep_items,
                                      keep_item_count * sizeof(PathItem) + sizeof(PathItem));
    PathItem &item = keep_items[keep_item_count];
    item.path = strdup(path);
    item.size = strlen(path);
    item.is_folder = (path[strlen(path) - 1] == '/');
    return ++keep_item_count;
}

int add_forbidden_item(const char *path) {
    ALOGI("add forbidden_item item : %s ", path);

    char forbidden_env_name[KEY_MAX];
    sprintf(forbidden_env_name, "V_FORBID_ITEM_%d", forbidden_item_count);
    setenv(forbidden_env_name, path, 1);
    forbidden_items = (PathItem *) realloc(forbidden_items,
                                           forbidden_item_count * sizeof(PathItem) +
                                           sizeof(PathItem));
    PathItem &item = forbidden_items[forbidden_item_count];
    item.path = strdup(path);
    item.size = strlen(path);
    item.is_folder = (path[strlen(path) - 1] == '/');
    return ++forbidden_item_count;
}

int add_readonly_item(const char *path) {
    ALOGE("add readonly_item item : %s ", path);

    char readonly_env_name[KEY_MAX];
    sprintf(readonly_env_name, "V_READONLY_ITEM_%d", readonly_item_count);
    setenv(readonly_env_name, path, 1);
    readonly_items = (PathItem *) realloc(readonly_items,
                                          readonly_item_count * sizeof(PathItem) +
                                          sizeof(PathItem));
    PathItem &item = readonly_items[readonly_item_count];
    item.path = strdup(path);
    item.size = strlen(path);
    item.is_folder = (path[strlen(path) - 1] == '/');
    return ++readonly_item_count;
}

int add_replace_item(const char *orig_path, const char *new_path) {
    //ALOGI("add replace item : %s -> %s", orig_path, new_path);
    char src_env_name[KEY_MAX];
    char dst_env_name[KEY_MAX];
    sprintf(src_env_name, "V_REPLACE_ITEM_SRC_%d", replace_item_count);
    sprintf(dst_env_name, "V_REPLACE_ITEM_DST_%d", replace_item_count);
    setenv(src_env_name, orig_path, 1);
    setenv(dst_env_name, new_path, 1);

    replace_items = (ReplaceItem *) realloc(replace_items,
                                            replace_item_count * sizeof(ReplaceItem) +
                                            sizeof(ReplaceItem));
    ReplaceItem &item = replace_items[replace_item_count];
    item.orig_path = strdup(orig_path);
    item.orig_size = strlen(orig_path);
    item.new_path = strdup(new_path);
    item.new_size = strlen(new_path);
    item.is_folder = (orig_path[strlen(orig_path) - 1] == '/');
    return ++replace_item_count;
}

PathItem *get_keep_items() {
    return keep_items;
}

PathItem *get_forbidden_items() {
    return forbidden_items;
}

PathItem *get_readonly_items() {
    return readonly_items;
}

ReplaceItem *get_replace_items() {
    return replace_items;
}

int get_keep_item_count() {
    return keep_item_count;
}

int get_forbidden_item_count() {
    return forbidden_item_count;
}

int get_replace_item_count() {
    return replace_item_count;
}

inline bool
match_path(bool is_folder, size_t size, const char *item_path, const char *path, size_t path_len) {
    if (is_folder) {
        if (path_len < size) {
            // ignore the last '/'
            return strncmp(item_path, path, size - 1) == 0 && item_path[size - 1] == '/';
        } else {
            return strncmp(item_path, path, size) == 0;
        }
    } else {
        return strcmp(item_path, path) == 0;
    }
}

bool isReadOnly(const char *path) {
    for (int i = 0; i < readonly_item_count; ++i) {
        PathItem &item = readonly_items[i];
        if (match_path(item.is_folder, item.size, item.path, path, strlen(path))) {
            return true;
        }
    }
    return false;
}

inline const char *relocate_path_internal(const char *path, char *const buffer, const size_t size) {

    if (nullptr == path) {
        return path;
    }
    //空字符串也进行返回,很多app发现传入的是“”导致问题
    if (strcmp(path, "") == 0) {
        return path;
    }
    const char *orig_path = path;
    path = canonicalize_path(path, buffer, size);

    const size_t pathLen = strlen(path);
    //查询是否需要keep,也就是白名单
    for (int i = 0; i < keep_item_count; ++i) {
        PathItem &item = keep_items[i];
        if (match_path(item.is_folder, item.size, item.path, path, pathLen)) {
            return orig_path;
        }
    }

    for (int i = 0; i < forbidden_item_count; ++i) {
        PathItem &item = forbidden_items[i];
        if (match_path(item.is_folder, item.size, item.path, path, pathLen)) {
            return nullptr;
        }
    }

    for (int i = 0; i < replace_item_count; ++i) {
        ReplaceItem &item = replace_items[i];
        if (match_path(item.is_folder, item.orig_size, item.orig_path, path, pathLen)) {
            //如果path的长度小于,这种情况大概率不存在
            //因为我们的基本都是放在/data/下所以基本都是大于原来的长度
            if (pathLen < item.orig_size) {
                // 迁移原来的,生成新的string
                std::string relocated_path(item.new_path, 0, item.new_size - 1);
                //创建新地址并返回
                return strdup(relocated_path.c_str());
            } else {

                const size_t remain_size = pathLen - item.orig_size + 1u;
                //字符串长度大于4096
                if (size < item.new_size + remain_size) {
                    ALOGE("buffer overflow %u", static_cast<unsigned int>(size));
                    return nullptr;
                }
                //在原始path基础上向后偏移path的长度
                //这个时候buff和remain里面的内容 "",现在的remain 就是一个'\0'
                const char *const remain = path + item.orig_size;
                if (path != buffer) {
                    //将new_path数据赋值到buffer里面,
                    memcpy(buffer, item.new_path, item.new_size);
                    memcpy(buffer + item.new_size, remain, remain_size);
                } else {
                    void *const remain_temp = alloca(remain_size);
                    memcpy(remain_temp, remain, remain_size);
                    memcpy(buffer, item.new_path, item.new_size);
                    memcpy(buffer + item.new_size, remain_temp, remain_size);
                }
                return buffer;
            }
        }
    }
    return orig_path;
}


static int bootIdReadCount = 0;

__attribute__((always_inline))
const char *antiCheck(const char *result, const char *path) {
    if (strstr(result, "magisk")) {
        return nullptr;
    } else if (strstr(result, "xposed")) {
        return nullptr;
    } else if (strstr(result, "edxp")) {
        return nullptr;
    } else if (strstr(result, "lsposed")) {
        return nullptr;
    } else if (strstr(result, "libriru") || strstr(result, "/riru")) {
        return nullptr;
    } else if (strstr(result, "sandhook")) {
        return nullptr;
    } else if (endsWith(result, "/su")) {
        //su结尾的root文件都直接干掉
        return nullptr;
    } else if (strstr(result, "zygisk")) {
        return nullptr;
    } else if (strstr(result, "EdHooker")) {
        return nullptr;
    } else if (strstr(result, "/data/adb/")) {
        //这个文件里面包含很多magisk相关的,比如模块的list /data/adb/modules/
        //https://github.com/LSPosed/NativeDetector/blob/master/app/src/main/jni/activity.cpp
        ALOGE(">>>>>>>>>>>IO find read /data/adb/  return nullptr   -> %s", result)
        return nullptr;
    } else if (endsWith(result, "/boot_id")) {
        //android 11 以下直接返回就可以,不会崩溃
        if (get_sdk_level() >= ANDROID_R) {
            //绕过第一次ptrace读取bootid
            if (bootIdReadCount < 1) {
                result = path;
            }
            bootIdReadCount = bootIdReadCount + 1;
            //LOGI("io sandbox boot_id %s -> %s  %d " , path, result, bootIdReadCount)
        }
    }
    return result;
}

const char *handlerTaskPath(const char *path, char *const buffer, const size_t size) {
    /**
   * 这个主要是为了防止类似
   * /proc/17738/task/17741/stat
   * 无法被全部IO重定向的问题,将路径换成去掉task的基础路径
   * 很多企业壳会去子线程查询stat反调试
   *
   * 将上述路径换成,类似如下
   * /proc/17738/task/17741/stat -> /proc/17738/stat
   * /proc/17738/task/17741/fd/56 -> /proc/17738/fd/56
   */
    if ((StringUtils::startWith(path, "/proc/") || StringUtils::startWith(path, "proc/")) &&
        strstr(path, "/task/") != nullptr) {
        memset((void *) buffer, 0, size);
        const char *path1 = strstr(path, "/task/");
        strncpy(buffer, path, strlen(path) - strlen(path1));
        const char *path2 = path1 + strlen("/task/");
        //防止/proc/16414/task/ 这种目录崩溃
        //proc/6770/task/6770
        if (strlen(path2) > 1) {
            const char *path3 = strstr(path2, "/");
            if (path3 != nullptr) {
                strcat(buffer, path3);
                //LOGI("sandbox handler task path [%s]  [%s] ",path,buffer)
                return buffer;
            }
        }
    }
    return nullptr;
}

static bool runtimeIsFinish = true;

const char *relocate_path(const char *path, char *const buffer, const size_t size) {
    //fix org path == null
    if (path == nullptr) {
        return nullptr;
    }
    if (path[0] == '\0') {
        return path;
    }
    //未处理之前最原始的路径
    string orig_path(path);
    const char *taskPath = handlerTaskPath(path, buffer, size);
    if (taskPath != nullptr) {
        path = taskPath;
    }
    const char *result = relocate_path_internal(path, buffer, size);

    //上述都处理完毕以后,二次处理,过滤特征文件
    result = antiCheck(result, path);
    //打印变化
    if (result == nullptr) {
        //等于NULL也属于打印变化
#ifdef IO_PATH_CHANGE
        if(runtimeIsFinish){
            ALOGI(">>>>>  sandbox   %s -> %s",
                  orig_path.c_str(),
                  result == nullptr ? "null" : result
            )
        }
#endif
        //等于NULL也属于全部
#ifdef IO_PATH_ALL
        if (runtimeIsFinish) {
            ALOGI(">>>>>  sandbox   %s -> %s",
                  orig_path.c_str(),
                  result == nullptr ? "null" : result
            )
        }
#endif

#ifdef IO_PATH_NULL
        if(runtimeIsFinish){
            ALOGI(">>>>>  sandbox   %s -> %s",
                  orig_path.c_str(),
                  result == nullptr ? "null" : result
            )
        }
#endif
        return result;
    }





//打印返回结果NULL
#ifdef IO_PATH_NULL
    if(result==nullptr){
      if(runtimeIsFinish){
            ALOGI(">>>>>  sandbox   %s -> %s",
                  orig_path.c_str(),
                  result == nullptr ? "null" : result
            )
        }
    }
#endif

//打印变化
#ifdef IO_PATH_CHANGE
    if(path!=result){
        if(runtimeIsFinish){
            ALOGI(">>>>>  sandbox   %s -> %s",
                  orig_path.c_str(),
                  result == nullptr ? "null" : result
            )
        }
    }
#endif

//打印全部文件路径
#ifdef IO_PATH_ALL
    if (runtimeIsFinish) {
        ALOGI(">>>>>  sandbox   %s -> %s",
              orig_path.c_str(),
              result == nullptr ? "null" : result
        )
    }
#endif

//路径没有发生变化
#ifdef IO_PATH_NO_CHANGE
    //传入的参数和返回值相等则打印
    if(path==result){
        if(runtimeIsFinish){
            ALOGI(">>>>>  sandbox   %s -> %s",
                  orig_path.c_str(),
                  result == nullptr ? "null" : result
            )
        }
    }
#endif
    //ALOGI("全部打印 io sandbox  %s -> %s   " ,path, result)
    return result;
}


int reverse_relocate_path_inplace(char *const path, const size_t size) {
    char path_temp[PATH_MAX];
    const char *redirect_path = reverse_relocate_path(path, path_temp, sizeof(path_temp), false);
    if (redirect_path) {
        if (redirect_path != path) {
            const size_t len = strlen(redirect_path) + 1u;
            if (len <= size) {
                memcpy(path, redirect_path, len);
            }
        }
        return 0;
    }
    return -1;
}

/**
 * 查询反转文件路径,也就是IO重定向之前的路径
 * 主要针对文件 IO重定向反查
 */
const char *reverse_relocate_file(const char *path) {
    for (int i = 0; i < replace_item_count; ++i) {
        ReplaceItem &item = replace_items[i];
        //不是文件夹,并且路径匹配,目前只处理文件
        if (!item.is_folder && strcmp(path, item.new_path) == 0) {
            return item.orig_path;
        }
    }
    return nullptr;
}

/**
 * 查询反转路径,也就是IO重定向之前的路径
 *
 * 因为会对当前分身的目录进行白名单,防止多级问题。
 * 导致白名单路径不会被反转,当@needKeep 为false时候无视白名单,防止白名单影响逻辑
 *
 * 只有在需要处理白名单的时候才进行过滤。
 *
 * 反查的时候,应该忽略/data/data/ /data/user/0/开头不一样的影响。
 * /data/data/com.example.test/app_virtual_devices/666/runtime_data/runtime_temp/com.example.test_stat
 * /data/user/0/com.example.test/app_virtual_devices/666/runtime_data/runtime_temp/com.example.test_stat
 *
 * 比如上面的路径查找到的结果是一样的应该,不应该因为前面的路径不一样导致反查失败。
 */
const char *
reverse_relocate_path(const char *path, char *const buffer, const size_t size, bool needKeep) {
    if (path == nullptr) {
        return nullptr;
    }
    path = canonicalize_path(path, buffer, size);
    const size_t len = strlen(path);
    //如果这个路径加了白名单,则不会得到重定位之前的路径。
    //只有在需要处理白名单的时候才进行过滤
    if (needKeep) {
        for (int i = 0; i < keep_item_count; ++i) {
            PathItem &item = keep_items[i];
            if (match_path(item.is_folder, item.size, item.path, path, len)) {
                return path;
            }
        }
    }
    //优先全量查找,当全部路径都匹配,则直接返回
    //这个主要是查找文件
    for (int i = 0; i < replace_item_count; ++i) {
        ReplaceItem &item = replace_items[i];
        if (strcmp(path, item.new_path) == 0) {
            return item.orig_path;
        }
    }
    //全量匹配找不到,则进行模糊反转
    for (int i = 0; i < replace_item_count; ++i) {
        ReplaceItem &item = replace_items[i];
        if (match_path(item.is_folder, item.new_size, item.new_path, path, len)) {
            if (len < item.new_size) {
                return item.orig_path;
            } else {
                const size_t remain_size = len - item.new_size + 1u;
                if (size < item.orig_size + remain_size) {
                    ALOGE("reverse buffer overflow %u", static_cast<unsigned int>(size));
                    return nullptr;
                }
                const char *const remain = path + item.new_size;
                if (path != buffer) {
                    memcpy(buffer, item.orig_path, item.orig_size);
                    memcpy(buffer + item.orig_size, remain, remain_size);
                } else {
                    void *const remain_temp = alloca(remain_size);
                    memcpy(remain_temp, remain, remain_size);
                    memcpy(buffer, item.orig_path, item.orig_size);
                    memcpy(buffer + item.orig_size, remain_temp, remain_size);
                }
                return buffer;
            }
        }
    }

    //走到这的话说明可能路径没有被反转,可能是因为路径头的问题导致的,反转失败
    if (StringUtils::startWith(path, "/data/data/")) {
        auto new_str = string(path);
        StringUtils::replace(new_str, "/data/data/", "/data/user/0/");
        //再次进行查找
        for (int i = 0; i < replace_item_count; ++i) {
            ReplaceItem &item = replace_items[i];
            if (match_path(item.is_folder, item.new_size, item.new_path, new_str.c_str(), len)) {
                if (len < item.new_size) {
                    return item.orig_path;
                } else {
                    const size_t remain_size = len - item.new_size + 1u;
                    if (size < item.orig_size + remain_size) {
                        ALOGE("reverse buffer overflow %u", static_cast<unsigned int>(size));
                        return nullptr;
                    }
                    const char *const remain = new_str.c_str() + item.new_size;
                    if (new_str.c_str() != buffer) {
                        memcpy(buffer, item.orig_path, item.orig_size);
                        memcpy(buffer + item.orig_size, remain, remain_size);
                    } else {
                        void *const remain_temp = alloca(remain_size);
                        memcpy(remain_temp, remain, remain_size);
                        memcpy(buffer, item.orig_path, item.orig_size);
                        memcpy(buffer + item.orig_size, remain_temp, remain_size);
                    }
                    return buffer;
                }
            }
        }
    } else if (StringUtils::startWith(path, "/data/user/0/")) {
        auto new_str = string(path);
        StringUtils::replace(new_str, "/data/user/0/", "/data/data/");
        //再次进行查找
        for (int i = 0; i < replace_item_count; ++i) {
            ReplaceItem &item = replace_items[i];
            if (match_path(item.is_folder, item.new_size, item.new_path, new_str.c_str(), len)) {
                if (len < item.new_size) {
                    return item.orig_path;
                } else {
                    const size_t remain_size = len - item.new_size + 1u;
                    if (size < item.orig_size + remain_size) {
                        ALOGE("reverse buffer overflow %u", static_cast<unsigned int>(size));
                        return nullptr;
                    }
                    const char *const remain = new_str.c_str() + item.new_size;
                    if (new_str.c_str() != buffer) {
                        memcpy(buffer, item.orig_path, item.orig_size);
                        memcpy(buffer + item.orig_size, remain, remain_size);
                    } else {
                        void *const remain_temp = alloca(remain_size);
                        memcpy(remain_temp, remain, remain_size);
                        memcpy(buffer, item.orig_path, item.orig_size);
                        memcpy(buffer + item.orig_size, remain_temp, remain_size);
                    }
                    return buffer;
                }
            }
        }
    }
    return path;
}

/**
 * 返回这个文件是否可读
 * @param tracer_pid 调试进程pid,跟踪者
 * @param path  对应的文件路径
 * @param name  文件名
 * @param pc
 * @param lr
 * @return
 */
bool isRuntimeHideDir(char *path, const char *name) {
    bool isHide = false;
    //hide hook elf
    if (my_strstr(name, CORE_SO_NAME)) {
        LOG(INFO) << ">>>>>>>>>> hide dir true name -> ["
                  << path << " " << name << "] is my so  ";
        isHide = true;
    }


    return isHide;
}

