cmake_minimum_required(VERSION 3.25)
project(borealis_native LANGUAGES C CXX)

# Repository root: this file lives in apps/android/borealis/native/src/main/cpp.
get_filename_component(BOREALIS_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../.." ABSOLUTE)

# borealis-core is published as a shared library linked against libc++_shared (see
# apps/android/borealis/README.md): it exports only the C ABI, so every consumer (this module's JNI
# library and the ndk-build bridge in :app) shares one copy.
block()
  set(BUILD_SHARED_LIBS ON)
  set(BOREALIS_CORE_BUILD_TESTS OFF)
  set(BOREALIS_CORE_BUILD_FUZZERS OFF)
  add_subdirectory("${BOREALIS_REPO_ROOT}/core" borealis_core_build)
endblock()

# Hand-written JNI for the Kotlin API (ADR 0043).
add_library(borealis_jni SHARED borealis_jni.cpp)
target_link_libraries(borealis_jni PRIVATE borealis::core)
target_compile_options(borealis_jni PRIVATE -Wall -Wextra -Wpedantic)
set_target_properties(borealis_jni PROPERTIES CXX_VISIBILITY_PRESET hidden)
