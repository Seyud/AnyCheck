cmake_minimum_required(VERSION 3.4.1)
enable_language(C ASM)


if(ZHENXI_BUILD_TYPE STREQUAL "RELEASE")
    # 设置字符串隐藏
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden")
    # 删除调试符号
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -s")
    # 开启空间优化(适用于so大小优化)
    # SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Os")
    # 这个选项表示“最高级别的优化”，它的目标是生成尽可能快的代码。和os互斥
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3")
    # 使用gc-section优化,以函数为单位而非文件
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections")
    # 去除栈信息保护,因为release不需要打印调用栈
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fomit-frame-pointer")
    # 在链接时移除未使用的函数和数据，减小最终可执行文件的大小。
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wl,--gc-sections")
    # 不打印日志,参考log.h
    add_definitions(-DZHENXI_BUILD_TYPE_NOLOG)
    add_definitions(-DZHENXI_BUILD_TYPE_RELEASE)
endif()



#native基础类库路径
include_directories(
        ${HomePath}/nativeLib/src/main/cpp/
        ${HomePath}/nativeLib/src/main/cpp/includes/
)


add_library(
        ${RUNTIME_EXTRA_SO_NAME_LIB}

        SHARED

        HideMaps.cpp
)

target_link_libraries(
        ${RUNTIME_EXTRA_SO_NAME_LIB}

        adapter
        dlfc
        runtimehook
        libpath

        utils
        raw_syscall
        xunwind #栈回溯
        xdl
        linkerHandler#处理linker相关的方法
        findsyscall
        mapshidepath
)









