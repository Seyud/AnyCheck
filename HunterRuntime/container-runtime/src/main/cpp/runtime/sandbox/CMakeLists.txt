cmake_minimum_required(VERSION 3.4.1)
#sandhook inlinehook新版本 采用C++ 17编译,优先采用17编译。
#SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-narrowing -std=c++20")
#添加内联支持
enable_language(C ASM)





string(REGEX REPLACE "(.*)/container-runtime/src.*" "\\1" HomePath ${PROJECT_SOURCE_DIR})

#native基础类库路径
include_directories(
        ./
        ${HomePath}/nativeLib/src/main/cpp/
        ${HomePath}/nativeLib/src/main/cpp/includes/
        ${HomePath}/Dobby/include/
)






add_library(
        sandbox

        STATIC

        canonicalize_md.c
        SandboxFs.cpp
)

target_link_libraries(
        sandbox
        utils
        dlfc
        xdl
        ${log-lib}
)









