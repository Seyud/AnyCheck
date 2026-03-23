//
// Created by Zhenxi on 2022/8/26.
//


#include <dirent.h>
#include <sys/stat.h>

#include "libpath.h"
#include "mylibc.h"
#include "raw_syscall.h"
#include "ZhenxiLog.h"
#include "string"
#include <string>
#include <map>
#include <list>
#include <cstring>
#include <cstdio>
#include <regex>
#include <cerrno>
#include <climits>
#include <iostream>
#include <fstream>

#define MAX_LINE 512
#define MAX_LENGTH 256

#include <sys/mman.h>
#include <unistd.h>
#include <linux/elf.h>
#include <linux/fcntl.h>


using namespace std;
static const char *FRIDA_THREAD_GUM_JS_LOOP = "gum-js-loop";
static const char *FRIDA_THREAD_GMAIN = "gmain";
static const char *FRIDA_NAMEDPIPE_LINJECTOR = "linjector";
static const char *PROC_MAPS = "/proc/self/maps";
static const char *PROC_STATUS = "/proc/self/task/%s/status";
static const char *PROC_FD = "/proc/self/fd";
static const char *PROC_TASK = "/proc/self/task";


//#define MEMORY_R    PROT_READ
//#define MEMORY_RW   PROT_READ |PROT_WRITE
//#define MEMORY_RWX  PROT_READ |PROT_WRITE| PROT_EXEC
//#define MEMORY_RX   PROT_READ | PROT_EXEC

int page_size = -1;


static unsigned long checksum(void *buffer, size_t len) {
    if (buffer == nullptr || len < 100) {
        return 0;
    }
    unsigned long seed = 0;
    auto *buf = (uint8_t *) buffer;
    for (size_t i = 0; i < len; ++i) {
        uint8_t *ptr = buf++;
        //if(is_address_readable(ptr)){
        seed += (unsigned long) (*ptr);
        //}
    }
    return seed;
}

__attribute__((always_inline))
static inline ssize_t read_one_line(int fd, char *buf, unsigned int max_len) {
    char b;
    ssize_t ret;
    ssize_t bytes_read = 0;

    my_memset(buf, 0, max_len);

    do {
        ret = my_read(fd, &b, 1);

        if (ret != 1) {
            if (bytes_read == 0) {
                // error or EOF
                return -1;
            } else {
                return bytes_read;
            }
        }

        if (b == '\n') {
            return bytes_read;
        }

        *(buf++) = b;
        bytes_read += 1;

    } while (bytes_read < max_len - 1);

    return bytes_read;
}


//记录可执行段的结构体,一个是plt段一个是text段
//所以对应的数量是2
typedef struct stExecSection {
    int execSectionCount;
    unsigned long offset[2];
    unsigned long memsize[2];
    unsigned long checksum[2];
    unsigned long startAddrinMem;
    bool isSuccess = false;
} execSection;


#if defined(__LP64__)
typedef Elf64_Ehdr Elf_Ehdr;
typedef Elf64_Shdr Elf_Shdr;
typedef Elf64_Addr Elf_Addr;
typedef Elf64_Dyn Elf_Dyn;
typedef Elf64_Rela Elf_Rela;
typedef Elf64_Sym Elf_Sym;
typedef Elf64_Off Elf_Off;

#define ELF_R_SYM(i) ELF64_R_SYM(i)

#else
typedef Elf32_Ehdr Elf_Ehdr;
typedef Elf32_Shdr Elf_Shdr;
typedef Elf32_Addr Elf_Addr;
typedef Elf32_Dyn Elf_Dyn;
typedef Elf32_Rel Elf_Rela;
typedef Elf32_Sym Elf_Sym;
typedef Elf32_Off Elf_Off;

#define ELF_R_SYM(i) ELF32_R_SYM(i)
#endif

/**
 * 获取本地文件的 Check sum
 * 读取耗时操作,只初始化一次保存到本地。
 */
execSection fetch_checksum_of_library(const char *filePath) {
    execSection section = {0};
    Elf_Ehdr ehdr;
    Elf_Shdr sectHdr;
    int fd;
    int execSectionCount = 0;
    fd = my_openat(AT_FDCWD, filePath, O_RDONLY, 0);
    if (fd < 0) {
        return section;
    }

    my_read(fd, &ehdr, sizeof(Elf_Ehdr));
    my_lseek(fd, (off_t) ehdr.e_shoff, SEEK_SET);

    unsigned long memSize[2] = {0};
    unsigned long offset[2] = {0};

    //查找section的plt和text开始位置和长度
    for (int i = 0; i < ehdr.e_shnum; i++) {
        my_memset(&sectHdr, 0, sizeof(Elf_Shdr));
        my_read(fd, &sectHdr, sizeof(Elf_Shdr));
        //通常 PLT and Text 一般都是可执行段
        if (sectHdr.sh_flags & SHF_EXECINSTR) {
            offset[execSectionCount] = sectHdr.sh_offset;
            memSize[execSectionCount] = sectHdr.sh_size;
            execSectionCount++;
            if (execSectionCount == 2) {
                break;
            }
        }
    }
    if (execSectionCount == 0) {
        LOG(INFO) << "get elf section error " << filePath;
        my_close(fd);
        return section;
    }

    //记录个数
    section.execSectionCount = execSectionCount;

    section.startAddrinMem = 0;
    for (int i = 0; i < execSectionCount; i++) {
        my_lseek(fd, (off_t) offset[i], SEEK_SET);
        //void * buffer = alloca(memSize[i] * sizeof(uint8_t));
        //存放text或者plt全部的数据内容,大约5-10M大小,为了兼容小内存手机。
        //所以放在堆里面,而不是栈,防止小内存手机栈指针溢出。
        auto buffer = (void *) calloc(1, memSize[i] * sizeof(uint8_t));
        if (buffer == nullptr) {
            free(buffer);
            return section;
        }
        my_read(fd, buffer, memSize[i]);
        section.offset[i] = offset[i];
        section.memsize[i] = memSize[i];
        section.checksum[i] = checksum(buffer, memSize[i]);

//        LOGE("fetch_checksum_of_library %s ExecSection:[%d][%ld][%ld][%ld]",
//             filePath, i, offset[i], memSize[i], section->checksum[i])
        free(buffer);
    }
    section.isSuccess = true;
    my_close(fd);
    return section;
}

/**
 * 检测问的check sum
 * 检测到check未修改返回0
 * 检测已修改返回1
 * 检测失败返回-1
 */
int scan_executable_segments(
        char *mapItem,
        execSection *pElfSectArr,
        const char *libraryName) {
    unsigned long start, end;
    char buf[MAX_LINE] = "";
    char path[MAX_LENGTH] = "";
    char tmp[100] = "";

    sscanf(mapItem, "%lx-%lx %s %s %s %s %s", &start, &end, buf, tmp, tmp, tmp, path);

    if (buf[2] == 'x') {
        if (buf[0] == 'r') {
            uint8_t *buffer;

            buffer = (uint8_t *) start;
            for (int i = 0; i < pElfSectArr->execSectionCount; i++) {
                if (start + pElfSectArr->offset[i] + pElfSectArr->memsize[i] > end) {
                    if (pElfSectArr->startAddrinMem != 0) {
                        buffer = (uint8_t *) pElfSectArr->startAddrinMem;
                        pElfSectArr->startAddrinMem = 0;
                        break;
                    }
                }
            }
            for (int i = 0; i < pElfSectArr->execSectionCount; i++) {
                auto begin = (void *) (buffer + pElfSectArr->offset[i]);
                unsigned long size = pElfSectArr->memsize[i];
                LOGI("%s [%p] size ->[%lu]", libraryName, begin, size);
                //MPROTECT((size_t)begin, size, MEMORY_R);
                unsigned long output = checksum(begin, size);
                LOGI("%s checksum:[%ld][%ld]", libraryName, output, pElfSectArr->checksum[i])
                if (output != pElfSectArr->checksum[i]) {
                    //和本地的So Checksum 对不上
                    return 1;
                }
            }
        }
        return 0;
    } else {
        if (buf[0] == 'r') {
            pElfSectArr->startAddrinMem = start;
        }
    }
    return 0;
}

/**
 * 检测问的check sum
 * 检测到check未修改返回0
 * 检测已修改返回1
 * 检测失败返回-1
 */
int detect_elf_checksum(const char *soPath, execSection *pSection) {
    if (pSection == nullptr) {
        LOGI("detect_elf_checksum execSection == null  ");
        return -1;
    }

    char map[MAX_LINE];
    const char *maps_path = string("proc/").append(to_string(getpid())).append("/maps").c_str();

    int fd = my_openat(AT_FDCWD, maps_path, O_RDONLY, 0);

    if (fd <= 0) {
        LOGE("detect_elf_checksum open %s fail ", PROC_MAPS);
        return -1;
    }
    int checkSum = 0;
    while ((read_one_line(fd, map, MAX_LINE)) > 0) {
        if (my_strstr(map, soPath) != nullptr) {
            checkSum = scan_executable_segments(map, pSection, soPath);
            if (checkSum == 1) {
                break;
            }
        }
    }

    my_close(fd);
    return checkSum;
}

int checkLinkerCheckSum() {
    auto section = fetch_checksum_of_library(getLinkerPath().c_str());
    if (!section.isSuccess) {
        return -1;
    }
    return detect_elf_checksum(getLinkerPath().c_str(), &section);
}

int checkMySoCheckSum(const char *soPath) {
    auto section = fetch_checksum_of_library(soPath);
    if (!section.isSuccess) {
        return -1;
    }
    return detect_elf_checksum(soPath, &section);
}


/**
 * 检测libc的可执行权限 checksum
 * 当前线程存在Hook的时候,会修改libc的可执行权限
 * 所以和本地libc的可执行权限的开始位置和结束位置对不上,我们则认为当前线程可能存在被Hook的可能性
 */
int checkLibcCheckSum() {
    auto section = fetch_checksum_of_library(getlibcPath().c_str());
    if (!section.isSuccess) {
        return -1;
    }
    return detect_elf_checksum(getlibcPath().c_str(), &section);
}


int checkLibArtCheckSum() {
    auto section = fetch_checksum_of_library(getlibArtPath().c_str());
    if (!section.isSuccess) {
        return -1;
    }
    return detect_elf_checksum(getlibArtPath().c_str(), &section);
}

