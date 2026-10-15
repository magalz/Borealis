#!/usr/bin/env bash
# Exports BUILD_TOOLS (the Android build-tools directory) to the following workflow steps and
# installs the build-tools package when the runner image does not have it.
# Needs BUILD_TOOLS_VERSION and ANDROID_HOME, and GITHUB_ENV (set by the runner).
set -euo pipefail

tools="${ANDROID_HOME}/build-tools/${BUILD_TOOLS_VERSION}"
if [ ! -x "${tools}/apksigner" ]; then
  "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" --install "build-tools;${BUILD_TOOLS_VERSION}" > /dev/null
fi
echo "BUILD_TOOLS=${tools}" >> "$GITHUB_ENV"
