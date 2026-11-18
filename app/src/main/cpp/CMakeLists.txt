cmake_minimum_required(VERSION 3.22.1)

# Siempre optimizado, también en el APK de depuración: con -O0 whisper es decenas de veces más lento.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)

project(transcriptor C CXX)

# Mismo perfil que la prueba de velocidad del 2026-11-16 (Snapdragon 7 Gen 3): solo CPU, sin OpenMP.
# La GPU (OpenCL/Adreno) se midió 5 veces más lenta para whisper y queda fuera.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp)

add_library(transcriptor SHARED transcriptor.cpp)
target_link_libraries(transcriptor whisper log)
