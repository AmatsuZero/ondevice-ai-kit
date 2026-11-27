cmake_minimum_required(VERSION 3.22)
project(ondevice_demo LANGUAGES CXX)
add_subdirectory(../../examples/android-realtime-sr/src/main/cpp srtiming)
add_subdirectory(../openvoice openvoice)
add_subdirectory(../realtimevoice realtimevoice)
# Independent JNI shared library loaded by Kotlin via System.loadLibrary("odai_livevoice").
# No implicit ONNX dependency.
add_library(odai_livevoice SHARED ../realtimevoice/android/RealtimeVoiceJni.cpp)
target_include_directories(odai_livevoice PRIVATE ../realtimevoice ../realtimevoice/include)
target_link_libraries(odai_livevoice realtimevoice)
target_link_options(odai_livevoice PRIVATE "-Wl,-z,max-page-size=16384")
