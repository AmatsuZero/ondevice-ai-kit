cmake_minimum_required(VERSION 3.22)
project(srtiming LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(srtiming SHARED egl_timing.cpp)

find_library(EGL_LIBRARY EGL)
find_library(GLESV3_LIBRARY GLESv3)

if(NOT EGL_LIBRARY OR NOT GLESV3_LIBRARY)
    message(FATAL_ERROR "EGL/GLESv3 not found in NDK sysroot")
endif()

target_link_libraries(srtiming
        ${EGL_LIBRARY}
        ${GLESV3_LIBRARY})
