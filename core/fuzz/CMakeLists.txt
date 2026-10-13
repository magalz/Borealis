# libFuzzer scaffolding. Included from core/CMakeLists.txt only when
# BOREALIS_CORE_BUILD_FUZZERS is ON (clang only). No fuzz targets exist yet;
# they arrive with the first parser (see README.md).

# borealis_add_fuzzer(<name> <source>)
# Builds a libFuzzer executable linked against borealis::core with
# -fsanitize=fuzzer,address,undefined.
function(borealis_add_fuzzer name source)
  if(NOT BOREALIS_CORE_BUILD_FUZZERS)
    return()
  endif()
  add_executable(${name} ${source})
  target_link_libraries(${name} PRIVATE borealis::core)
  set_target_properties(
    ${name} PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON CXX_EXTENSIONS OFF
  )
  target_compile_options(${name} PRIVATE -fsanitize=fuzzer,address,undefined -fno-omit-frame-pointer)
  target_link_options(${name} PRIVATE -fsanitize=fuzzer,address,undefined)
  borealis_core_apply_build_options(${name})
endfunction()
