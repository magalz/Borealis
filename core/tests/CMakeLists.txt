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

add_executable(borealis_core_tests version_test.cpp)
target_link_libraries(borealis_core_tests PRIVATE borealis::core GTest::gtest_main)
# The expected value comes from the CMake project version, not a literal.
target_compile_definitions(
  borealis_core_tests PRIVATE BOREALIS_CORE_EXPECTED_VERSION="${borealis_core_VERSION}"
)
set_target_properties(
  borealis_core_tests PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON CXX_EXTENSIONS OFF
)
borealis_core_apply_build_options(borealis_core_tests)

gtest_discover_tests(borealis_core_tests)
