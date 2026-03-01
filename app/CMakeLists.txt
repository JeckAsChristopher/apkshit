cmake_minimum_required(VERSION 3.22.1)
project(locai VERSION 1.0)

# ─── llama.cpp ───────────────────────────────────────────────────────────────
# The llama.cpp source must be cloned into:
#   app/src/main/cpp/llama.cpp/
#
# To do this once:
#   cd ~/Locai/app/src/main/cpp
#   git clone --depth 1 https://github.com/ggerganov/llama.cpp.git
#
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# Build options — keep it lean for mobile
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL            OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama_build)

# ─── Our JNI shared library ───────────────────────────────────────────────────
add_library(locai_jni SHARED locai_jni.cpp)

target_include_directories(locai_jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/ggml/include
)

target_link_libraries(locai_jni
    llama          # llama.cpp core
    ggml           # ggml tensor backend
    android        # Android system lib
    log            # __android_log_print
)

# ARM optimisations
target_compile_options(locai_jni PRIVATE
    -O3
    -ffast-math
    -fvisibility=hidden
)
