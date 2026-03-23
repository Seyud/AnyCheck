project(external)

OPTION(LSPLANT_BUILD_SHARED ON)

#if(ZHENXI_BUILD_TYPE STREQUAL "RELEASE")
add_definitions(-DLOG_DISABLED)
add_definitions(-DNDEBUG)
#endif ()

add_subdirectory(lsplant/lsplant/src/main/jni)
add_subdirectory(nativeLib/src/main/cpp)
