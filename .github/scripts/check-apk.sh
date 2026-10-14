#!/usr/bin/env bash
# Checks that a release APK carries the Borealis identity, that R8 kept the JNI entry classes and
# that the universal APK has the native libraries for all four ABIs.
# Used by the release workflow's build job, for real releases and for the dry run.
#
# Usage: check-apk.sh APK VERSION      (VERSION is MAJOR.MINOR.PATCH, as in the tag)
# Environment:
#   BUILD_TOOLS   Android build-tools directory (aapt2)
#   APKANALYZER   path of apkanalyzer (default: ${ANDROID_HOME}/cmdline-tools/latest/bin/apkanalyzer)
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: check-apk.sh APK VERSION" >&2
  exit 2
fi
apk="$1"
version="$2"
apkanalyzer="${APKANALYZER:-${ANDROID_HOME}/cmdline-tools/latest/bin/apkanalyzer}"

IFS=. read -r major minor patch <<< "$version"
expected_code=$((major * 1000000 + minor * 1000 + patch))

failed=0
fail() {
  echo "::error::$1"
  failed=1
}

badging=$("${BUILD_TOOLS}/aapt2" dump badging "$apk")
grep -E "^(package|application-label|application-debuggable):" <<< "$badging"

package_line=$(echo "$badging" | grep '^package:')
[ "$(echo "$package_line" | sed -n "s/.* name='\([^']*\)'.*/\1/p")" = "io.github.magalz.borealis" ] ||
  fail "The package is not io.github.magalz.borealis (an upstream suffix such as .unofficial may still apply)."
[ "$(echo "$package_line" | sed -n "s/.* versionName='\([^']*\)'.*/\1/p")" = "$version" ] ||
  fail "versionName is not $version."
[ "$(echo "$package_line" | sed -n "s/.* versionCode='\([^']*\)'.*/\1/p")" = "$expected_code" ] ||
  fail "versionCode is not $expected_code."
grep -q "^application-label:'Borealis'$" <<< "$badging" ||
  fail "The application label is not Borealis."
if grep -q '^application-debuggable' <<< "$badging"; then
  fail "The release APK is debuggable."
fi

# With R8 on, an unreferenced class is removed. The JNI entry classes are only reached from native
# code, so they survive only through keep rules.
classes=$("$apkanalyzer" dex packages --defined-only "$apk" | tr -d '\r')
for class in \
  com.limelight.nvstream.jni.MoonBridge \
  io.github.magalz.borealis.nativecore.CoreJni; do
  grep -qE "^C[[:space:]].*[[:space:]]${class//./[.]}\$" <<< "$classes" ||
    fail "R8 removed or renamed $class."
done

# The release is one universal APK (ADR 0050), so every ABI must carry the native libraries.
abis=$(unzip -l "$apk" | grep -oE 'lib/[^/]+/libmoonlight-core\.so' | sort -u | wc -l)
[ "$abis" -eq 4 ] || fail "Expected libmoonlight-core.so for 4 ABIs, found $abis."

if [ "$failed" -ne 0 ]; then
  exit 1
fi
echo "APK identity, JNI classes and the four ABIs verified"
