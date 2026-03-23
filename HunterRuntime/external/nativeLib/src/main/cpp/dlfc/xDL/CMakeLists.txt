cmake_minimum_required(VERSION 3.22.1)
aux_source_directory(. xdl_src_list)




add_library(
        xdl

        STATIC

        ${xdl_src_list}
)

target_link_libraries(
        xdl

        ${log-lib}
)