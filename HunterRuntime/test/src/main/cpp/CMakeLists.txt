cmake_minimum_required(VERSION 3.4.1)
#sandhook inlinehook新版本 采用C++ 17编译,优先采用17编译。
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-narrowing -std=c++17")

#添加内联支持
enable_language(C ASM)

string(REGEX REPLACE "(.*)/test/src.*" "\\1" HomePath ${PROJECT_SOURCE_DIR})



include_directories(
        /utils/
        /dlfc/
)


add_library(
        testQC

        SHARED

        main.cpp
        test.cpp

)




find_library(
        log-lib
        log
)

target_link_libraries(
        testQC

        ${log-lib}
)











