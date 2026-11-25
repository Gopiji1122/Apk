cmake_minimum_required(VERSION 3.22.1)
project(llamajni LANGUAGES C CXX)

set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
# Snapdragon 865 (OnePlus 8) and newer: ARMv8.2 + dot-product instructions
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "" FORCE)

add_subdirectory(llama.cpp ${CMAKE_BINARY_DIR}/llama_build)

add_library(llamajni SHARED llama_jni.cpp)
target_include_directories(llamajni PRIVATE llama.cpp/include llama.cpp/ggml/include)
target_link_libraries(llamajni llama log android)
