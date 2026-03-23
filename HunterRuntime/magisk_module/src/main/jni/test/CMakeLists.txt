cmake_minimum_required(VERSION 3.4.1)
#SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-narrowing -std=c++20")
#添加内联支持
enable_language(C ASM)


find_library(
        log-lib
        log
)

#native基础类库路径
include_directories(
        ${HomePath}/external/nativeLib/src/main/cpp/includes/
)


#这个So主要是为了用于测试
add_library(
        MagiskClientTest
        STATIC
        Test.cpp
        Base64Utils.cpp
)


target_link_libraries(
        MagiskClientTest
        nativeLib
        mediandk
        ${log-lib}
)










