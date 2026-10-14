include(FetchContent)
include(GoogleTest)

# Pinned GoogleTest release archive (checksum verified by download).
FetchContent_Declare(
  googletest
  URL https://github.com/google/googletest/releases/download/v1.18.0/googletest-1.18.0.tar.gz
  URL_HASH SHA256=6e3191c1455468b3fc35a417fb565c1c5071aee1b7e7f85e30cf48a98d37d8b5
  DOWNLOAD_EXTRACT_TIMESTAMP TRUE
  SYSTEM
)
set(INSTALL_GTEST OFF CACHE BOOL "" FORCE)
set(BUILD_GMOCK OFF CACHE BOOL "" FORCE)
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

add_executable(
  borealis_core_tests
  version_test.cpp
  host_caps_test.cpp
  launch_decide_test.cpp
  launch_fps_test.cpp
  vectors_test.cpp
)
target_link_libraries(borealis_core_tests PRIVATE borealis::core GTest::gtest_main)
# The expected value comes from the CMake project version, not a literal.
target_compile_definitions(
  borealis_core_tests
  PRIVATE BOREALIS_CORE_EXPECTED_VERSION="${borealis_core_VERSION}"
          BOREALIS_CORE_FIXTURES_DIR="${CMAKE_CURRENT_SOURCE_DIR}/fixtures"
)
set_target_properties(
  borealis_core_tests PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON CXX_EXTENSIONS OFF
)
borealis_core_apply_build_options(borealis_core_tests)

# Shared builds on Windows have no rpath: place dependent DLLs next to the test
# executable. Must precede gtest_discover_tests, which runs the exe post-build.
if(WIN32)
  add_custom_command(
    TARGET borealis_core_tests
    POST_BUILD
    COMMAND
      ${CMAKE_COMMAND} -E
      $<IF:$<BOOL:$<TARGET_RUNTIME_DLLS:borealis_core_tests>>,copy_if_different,true>
      $<TARGET_RUNTIME_DLLS:borealis_core_tests> $<TARGET_FILE_DIR:borealis_core_tests>
    COMMAND_EXPAND_LISTS
  )
endif()

gtest_discover_tests(borealis_core_tests)
