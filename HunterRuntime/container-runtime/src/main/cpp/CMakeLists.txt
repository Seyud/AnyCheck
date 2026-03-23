cmake_minimum_required(VERSION 3.4.1)
#sandhook inlinehook新版本 采用C++ 17编译,优先采用17编译。
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-narrowing -std=c++20")
#添加内联支持
enable_language(C ASM)


add_definitions(-DIO_PATH_ALL)

message(STATUS "building lib name -> ${ZHENXI_BUILD_LIB_NAME}  ")


#32位和64位实现在不同文件,方便区分。
if (${CMAKE_ANDROID_ARCH_ABI} STREQUAL "arm64-v8a")
    string(CONCAT TEMP_SO_NAME "${ZHENXI_BUILD_LIB_NAME}" "64")
    set(RUNTIME_SO_NAME ${TEMP_SO_NAME})
    string(CONCAT TEMP_CORE_SO_NAME "lib" "${ZHENXI_BUILD_LIB_NAME}" "64.so")
    add_definitions(-DCORE_SO_NAME="${TEMP_CORE_SO_NAME}")

elseif (${CMAKE_ANDROID_ARCH_ABI} STREQUAL "armeabi-v7a")
    set(RUNTIME_SO_NAME ${ZHENXI_BUILD_LIB_NAME})
    string(CONCAT TEMP_CORE_SO_NAME "lib" "${ZHENXI_BUILD_LIB_NAME}" ".so")
    add_definitions(-DCORE_SO_NAME="${TEMP_CORE_SO_NAME}")
endif ()

message(STATUS "building lib name 111 -> ${RUNTIME_SO_NAME}  ")


find_library(
        log-lib
        log
)


string(REGEX REPLACE "(.*)/container-runtime/src.*" "\\1" HomePath ${PROJECT_SOURCE_DIR})

message(STATUS "Building   ${LIB_NAME} for Android(${CMAKE_SYSTEM_PROCESSOR}) ")
message(STATUS "HomePath ->   ${HomePath} PROJECT_SOURCE_DIR -> ${PROJECT_SOURCE_DIR} ")


#添加头文件路径位置
include_directories(
        ./
        include
)


include_directories(${HomePath}/external/nativeLib/src/main/cpp/includes/)
add_subdirectory(${HomePath}/external external.out)

#test
add_subdirectory(test)
add_subdirectory(runtime/sandbox)


set(RUNTIME_EXTRA_SO_NAME_LIB runtime_maps_hide)
string(CONCAT TEMP_EXTRA_CORE_SO_NAME "lib" "${RUNTIME_EXTRA_SO_NAME_LIB}" ".so")
add_definitions(-DEXTRA_SO_NAME="${TEMP_EXTRA_CORE_SO_NAME}")
#hide maps
add_subdirectory(runtime/extra)


add_library(
        ${RUNTIME_SO_NAME}

        SHARED

        main.cpp
        runtime/IORelocator.cpp
)


target_link_libraries(
        ${RUNTIME_SO_NAME}

        ${log-lib}


        sandbox
)



#runtime nativeLib基础库链接
target_link_libraries(
        ${RUNTIME_SO_NAME}

        nativeLib
)





find_package(lsplant REQUIRED CONFIG)
#lsplant
target_link_libraries(
        ${RUNTIME_SO_NAME}

        lsplant::lsplant
)










