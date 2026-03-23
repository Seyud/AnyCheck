cmake_minimum_required(VERSION 3.4.1)
enable_language(C ASM)



add_library(
        dexloader

        STATIC

        DexLoader.cpp
)

target_link_libraries(
        dexloader


        ${log-lib}
)









