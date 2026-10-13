cmake_minimum_required(VERSION 3.25)

project(
  borealis_core
  VERSION 0.1.0
  DESCRIPTION "Platform-neutral LuminalShine host rules shared by Borealis clients"
  LANGUAGES CXX
)

# ---------------------------------------------------------------------------
# Options
# ---------------------------------------------------------------------------
option(BOREALIS_CORE_WARNINGS_AS_ERRORS "Treat compiler warnings as errors" OFF)
option(BOREALIS_CORE_SANITIZE "Build borealis_core and its tests with ASan + UBSan" OFF)
option(BOREALIS_CORE_BUILD_FUZZERS "Build libFuzzer targets (requires clang)" OFF)

# Tests are only on by default when core is the top-level project, so that a
# parent project consuming core with add_subdirectory() never fetches GoogleTest.
if(PROJECT_IS_TOP_LEVEL)
  set(_borealis_core_tests_default ON)
else()
  set(_borealis_core_tests_default OFF)
endif()
option(BOREALIS_CORE_BUILD_TESTS "Build borealis_core unit tests" ${_borealis_core_tests_default})
unset(_borealis_core_tests_default)

if(BOREALIS_CORE_BUILD_FUZZERS AND NOT CMAKE_CXX_COMPILER_ID MATCHES "Clang")
  message(
    FATAL_ERROR
      "BOREALIS_CORE_BUILD_FUZZERS requires clang (libFuzzer); found ${CMAKE_CXX_COMPILER_ID}"
  )
endif()

# ---------------------------------------------------------------------------
# Per-target build options (never set globally, so nothing leaks to a parent)
# ---------------------------------------------------------------------------
function(borealis_core_apply_build_options target)
  if(MSVC)
    target_compile_options(${target} PRIVATE /W4)
    if(BOREALIS_CORE_WARNINGS_AS_ERRORS)
      target_compile_options(${target} PRIVATE /WX)
    endif()
  else()
    target_compile_options(${target} PRIVATE -Wall -Wextra -Wpedantic -Wconversion -Wshadow)
    if(BOREALIS_CORE_WARNINGS_AS_ERRORS)
      target_compile_options(${target} PRIVATE -Werror)
    endif()
  endif()

  if(BOREALIS_CORE_SANITIZE)
    if(MSVC)
      message(FATAL_ERROR "BOREALIS_CORE_SANITIZE is only supported with clang/gcc")
    endif()
    target_compile_options(${target} PRIVATE -fsanitize=address,undefined -fno-omit-frame-pointer)
    target_link_options(${target} PRIVATE -fsanitize=address,undefined)
  endif()
endfunction()

# ---------------------------------------------------------------------------
# Generated version header (lives in the build tree)
# ---------------------------------------------------------------------------
set(_borealis_core_generated_dir "${CMAKE_CURRENT_BINARY_DIR}/generated")
configure_file(
  "${CMAKE_CURRENT_SOURCE_DIR}/src/version_config.h.in"
  "${_borealis_core_generated_dir}/borealis/version_config.h" @ONLY
)

# ---------------------------------------------------------------------------
# Library
# ---------------------------------------------------------------------------
add_library(borealis_core src/version.cpp)
add_library(borealis::core ALIAS borealis_core)

target_include_directories(
  borealis_core
  PUBLIC "$<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>"
  PRIVATE "${_borealis_core_generated_dir}"
)
target_compile_features(borealis_core PUBLIC cxx_std_20)
set_target_properties(
  borealis_core
  PROPERTIES CXX_STANDARD 20
             CXX_STANDARD_REQUIRED ON
             CXX_EXTENSIONS OFF
             POSITION_INDEPENDENT_CODE ON
             CXX_VISIBILITY_PRESET hidden
             VISIBILITY_INLINES_HIDDEN ON
             VERSION ${PROJECT_VERSION}
)

get_target_property(_borealis_core_type borealis_core TYPE)
if(_borealis_core_type STREQUAL "SHARED_LIBRARY")
  target_compile_definitions(
    borealis_core
    PUBLIC BOREALIS_CORE_SHARED
    PRIVATE BOREALIS_CORE_BUILDING
  )
endif()
unset(_borealis_core_type)

borealis_core_apply_build_options(borealis_core)

# ---------------------------------------------------------------------------
# Tests / fuzzers
# ---------------------------------------------------------------------------
if(BOREALIS_CORE_BUILD_TESTS)
  enable_testing()
  add_subdirectory(tests)
endif()

if(BOREALIS_CORE_BUILD_FUZZERS)
  add_subdirectory(fuzz)
endif()
