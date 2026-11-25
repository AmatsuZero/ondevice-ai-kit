cmake_minimum_required(VERSION 3.22)
project(ondevice_demo LANGUAGES CXX)
add_subdirectory(../../examples/android-realtime-sr/src/main/cpp srtiming)
add_subdirectory(../openvoice openvoice)
