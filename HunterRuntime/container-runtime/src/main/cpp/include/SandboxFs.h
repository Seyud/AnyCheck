#ifndef SANDBOX_FS_H
#define SANDBOX_FS_H

#include <string.h>
#include <errno.h>
#include <list>

#define KEY_MAX 256

#define PUBLIC  __attribute__ ((visibility ("default")))
#define PRIVATE __attribute__ ((visibility ("hidden")))

#ifdef __cplusplus
extern "C" {
#endif

typedef struct PathItem {
    char *path;
    bool is_folder;
    size_t size;
} PathItem;

typedef struct ReplaceItem {
    char *orig_path;
    size_t orig_size;
    char *new_path;
    size_t new_size;
    bool is_folder;
} ReplaceItem;

enum RelocateResult {
    MATCH,
    NOT_MATCH,
    FORBID,
    KEEP
};

PUBLIC bool isReadOnly(const char *path);

PUBLIC const char *relocate_path(const char *path, char *const buffer, const size_t size);
/**
 * 查询反转路径,也就是IO重定向之前的路径
 */
PUBLIC const char *reverse_relocate_path(const char *path, char *const buffer, const size_t size,bool needKeep);

PUBLIC const char *reverse_relocate_file(const char *path);

PUBLIC int reverse_relocate_path_inplace(char *const path, const size_t size);

PUBLIC int add_keep_item(const char *path);

/**
 * 打印沙箱保存的全部IO路径
 */
PUBLIC void getSandBoxIoPathInfo();

PUBLIC int add_forbidden_item(const char *path);

PUBLIC int add_replace_item(const char *orig_path, const char *new_path);

PUBLIC int add_forbidden_item(const char *path);

PUBLIC int add_readonly_item(const char *path);

PUBLIC PathItem *get_keep_items();

PUBLIC PathItem *get_forbidden_items();

PUBLIC ReplaceItem *get_replace_items();

PUBLIC PathItem *get_readonly_items();

PUBLIC int get_keep_item_count();

PUBLIC int get_forbidden_item_count();

PUBLIC int get_replace_item_count();

bool isRuntimeHideDir(char* path , const char *name);


#ifdef __cplusplus
}
#endif

#endif //SANDBOX_FS_H
