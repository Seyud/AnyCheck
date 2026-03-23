cmake_minimum_required(VERSION 3.4.1)
project(MagiskModule)
enable_language(C ASM)

message(STATUS ">>>>>>>>>>>-----------------------------")
message(STATUS "SO_NAME: ${SO_NAME}")
message(STATUS "TAG_APP_NAME: ${TAG_APP_NAME}")
message(STATUS "TAG_PACKAGE_NAME: ${TAG_PACKAGE_NAME}")
message(STATUS "ZHENXI_BUILD_TYPE: ${ZHENXI_BUILD_TYPE}")
message(STATUS ">>>>>>>>>>>-----------------------------")

SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++20")
#忽略转换丢失,narrowing是一种可能会导致信息丢失的类型转换
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-narrowing")
#忽略函数的参数没有被使用的时候产生警告。
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wunused-parameter")
#忽略函数的返回值没有被使用的时候产生警告。
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Winvalid-noreturn")
#忽略参数未使用
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wunused-parameter")
find_library(log-lib log)
add_definitions(-fno-exceptions -fno-rtti)

add_definitions(-DTAG_APP_NAME="${TAG_APP_NAME}")
add_definitions(-DRUNTIME_KERNEL_NAME="${RUNTIME_KERNEL_NAME}")
add_definitions(-DRUNTIME_JAR_NAME="${RUNTIME_JAR_NAME}")


if (ZHENXI_BUILD_TYPE STREQUAL "Release")
    #设置字符串隐藏
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -fvisibility-inlines-hidden")
    #删除调试符号,生成so以后手动调用STRIP去符号
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -s")
    #开启空间优化
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Os")
    #使用gc-section优化,以函数为单位而非文件
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections")
    #添加堆栈信息保护,防止缓存攻击
    #SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fstack-protector")
    #移除堆栈保护
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fomit-frame-pointer")

    #在链接时移除未使用的函数和数据，减小最终可执行文件的大小。
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wl,--gc-sections")
    #防止ASLR
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC")
    #关闭日志
    add_definitions(-DZHENXI_BUILD_TYPE_NOLOG)
endif ()

add_subdirectory(${HomePath}/external/ external.out)

include_directories(
        include/
        ${HomePath}/external/nativeLib/src/main/cpp/includes/
)

add_subdirectory(foundation/dexloader)
add_subdirectory(foundation/base)
#add_subdirectory(foundation/service)

#add_subdirectory(root)

add_library(
        ${SO_NAME}

        SHARED

        zygisk_stub.cpp
        MagiskRuntime.cpp
)

target_link_libraries(
        ${SO_NAME}


        nativeLib
)

target_link_libraries(
        ${SO_NAME}
        #lsplant::lsplant
        lsplant_static
)


target_link_libraries(
        ${SO_NAME}

        ${log-lib}
        dexloader
        base


        )


#if(ZHENXI_BUILD_TYPE STREQUAL "DEBUG")
    add_definitions(-DZHENXI_BUILD_TYPE_DEBUG)
    #添加测试目录
    add_subdirectory(test)
    #链接test
    target_link_libraries(
            ${SO_NAME}
            MagiskClientTest
    )
#endif()









