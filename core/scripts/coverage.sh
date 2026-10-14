#!/bin/sh
# Builds borealis-core with clang source-based coverage, runs the tests and
# writes an lcov report limited to core's own sources (include/ and src/).
#
# Output: core/build/coverage/coverage.lcov, with repository-relative paths
# (core/src/version.cpp, ...) so Codecov can map them to the repository tree.
#
# Environment:
#   CXX             clang++ to use (default: clang++$LLVM_SUFFIX)
#   LLVM_SUFFIX     version suffix of the LLVM tools, e.g. -18 on Ubuntu
#   LLVM_PROFDATA   llvm-profdata binary (default: llvm-profdata$LLVM_SUFFIX)
#   LLVM_COV        llvm-cov binary (default: llvm-cov$LLVM_SUFFIX)
# The compiler and the LLVM tools must come from the same LLVM version.
set -eu

core_dir=$(cd "$(dirname "$0")/.." && pwd)
repo_dir=$(cd "$core_dir/.." && pwd)
suffix=${LLVM_SUFFIX:-}
profdata=${LLVM_PROFDATA:-llvm-profdata$suffix}
llvm_cov=${LLVM_COV:-llvm-cov$suffix}
CXX=${CXX:-clang++$suffix}
export CXX

for tool in "$CXX" "$profdata" "$llvm_cov" cmake ctest; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "coverage.sh: required tool not found: $tool" >&2
    exit 1
  }
done

build_dir=$core_dir/build/coverage
profiles_dir=$build_dir/profiles
profdata_file=$build_dir/coverage.profdata
lcov_file=$build_dir/coverage.lcov

cd "$core_dir"
# Start from a clean set of profiles so stale runs never leak into the report.
rm -rf "$profiles_dir" "$profdata_file" "$lcov_file"
# gtest_discover_tests runs the test binary during the build; keep its profile
# out of the working directory too.
LLVM_PROFILE_FILE=$profiles_dir/%p-%m.profraw
export LLVM_PROFILE_FILE

cmake --preset coverage
cmake --build --preset coverage
ctest --preset coverage

set -- "$profiles_dir"/*.profraw
[ -e "$1" ] || {
  echo "coverage.sh: no .profraw files were produced" >&2
  exit 1
}
"$profdata" merge -sparse "$@" -o "$profdata_file"

# Test binaries follow the *_tests naming convention (see tests/CMakeLists.txt).
binaries=$(find "$build_dir/tests" -maxdepth 1 -type f \( -name '*_tests' -o -name '*_tests.exe' \))
[ -n "$binaries" ] || {
  echo "coverage.sh: no test binaries found in $build_dir/tests" >&2
  exit 1
}
set --
for binary in $binaries; do
  if [ $# -eq 0 ]; then
    set -- "$binary"
  else
    set -- "$@" -object "$binary"
  fi
done

"$llvm_cov" report "$@" -instr-profile="$profdata_file" "$core_dir/include" "$core_dir/src"

# Keep only records for core's own sources and make their paths repo-relative.
# On Windows (Git Bash), llvm-cov emits drive-letter paths: use the native root.
native_repo_dir=$(cd "$repo_dir" && { pwd -W 2>/dev/null || pwd; } | tr '\\' '/')
"$llvm_cov" export -format=lcov "$@" -instr-profile="$profdata_file" \
  "$core_dir/include" "$core_dir/src" | tr '\\' '/' |
  awk -v root="$native_repo_dir" '
    /^SF:/ {
      path = substr($0, 4)
      keep = 0
      if (index(path, root "/core/include/") == 1 || index(path, root "/core/src/") == 1) {
        keep = 1
        $0 = "SF:core/" substr(path, length(root "/core/") + 1)
      }
    }
    keep { print }
    /^end_of_record/ { keep = 0 }
  ' >"$lcov_file"

[ -s "$lcov_file" ] || {
  echo "coverage.sh: lcov report is empty; check that core sources are covered" >&2
  exit 1
}
echo "coverage.sh: wrote $lcov_file"
