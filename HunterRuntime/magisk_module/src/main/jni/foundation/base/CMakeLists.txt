cmake_minimum_required(VERSION 3.4.1)
enable_language(C ASM)



add_library(
        base

        STATIC

        well_known_classes.cpp
        binder.cpp
)

target_link_libraries(
        base

        ${log-lib}
)









