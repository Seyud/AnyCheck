cmake_minimum_required(VERSION 3.4.1)
#sandhook inlinehook新版本 采用C++ 17编译,优先采用17编译。
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-narrowing -std=c++20")
#添加内联支持
enable_language(C ASM)


#添加头文件路径位置
include_directories(
        ./
        crc
        ${HomePath}/nativeLib/src/main/cpp/
        ${HomePath}/nativeLib/src/main/cpp/includes/
)

add_library(
        test

        SHARED

        test.cpp
        crc/cfrida.cpp
)


target_link_libraries(
        test

        ${log-lib}
)



#runtime nativeLib基础库链接
target_link_libraries(
        test

        adapter
        dlfc
        runtimehook
        libpath

        utils
        raw_syscall
)











