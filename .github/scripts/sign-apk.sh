#!/usr/bin/env bash
# Signs a release APK, verifies it and writes SHA256SUMS. The release workflow's `publish` job
# (real keystore) and its dry run (throwaway keystore) both call this script, so the dry run
# exercises exactly what a release does.
#
# Usage: sign-apk.sh UNSIGNED_APK SIGNED_APK [FILE...]
#   FILE  extra files that sit next to SIGNED_APK and go into SHA256SUMS (by name, not by path).
# Environment:
#   BUILD_TOOLS                  Android build-tools directory (zipalign, apksigner)
#   KEYSTORE                     path of the keystore
#   BOREALIS_KEY_ALIAS           alias of the signing key
#   BOREALIS_KEYSTORE_PASSWORD   keystore password
#   BOREALIS_KEY_PASSWORD        key password
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: sign-apk.sh UNSIGNED_APK SIGNED_APK [FILE...]" >&2
  exit 2
fi
unsigned="$1"
signed="$2"
shift 2

for var in BUILD_TOOLS KEYSTORE BOREALIS_KEY_ALIAS BOREALIS_KEYSTORE_PASSWORD BOREALIS_KEY_PASSWORD; do
  if [ -z "${!var:-}" ]; then
    echo "::error::$var is not set"
    exit 1
  fi
done

# AGP already zip-aligns the APK; signing must not change the alignment.
"${BUILD_TOOLS}/zipalign" -c -P 16 4 "$unsigned"
"${BUILD_TOOLS}/apksigner" sign \
  --ks "$KEYSTORE" --ks-key-alias "$BOREALIS_KEY_ALIAS" \
  --ks-pass env:BOREALIS_KEYSTORE_PASSWORD --key-pass env:BOREALIS_KEY_PASSWORD \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --v4-signing-enabled false \
  --out "$signed" \
  "$unsigned"

# minSdk is 28, where the v3 signature is the one that counts (v2 is also written, for tools that
# only read v2, but apksigner skips it when verifying for API 28 and up).
report=$("${BUILD_TOOLS}/apksigner" verify --verbose --print-certs --min-sdk-version 28 "$signed")
grep -E 'Verified using v3 scheme|Number of signers' <<< "$report"
grep -q 'Verified using v3 scheme (APK Signature Scheme v3): true' <<< "$report"
grep -q 'Number of signers: 1' <<< "$report"
"${BUILD_TOOLS}/zipalign" -c -P 16 4 "$signed"

# The APK must be signed by the key in the keystore, not merely by some key.
expected=$(keytool -J-Duser.language=en -list -keystore "$KEYSTORE" -storepass:env BOREALIS_KEYSTORE_PASSWORD \
  -alias "$BOREALIS_KEY_ALIAS" | sed -n 's/^Certificate fingerprint (SHA-256): //p' | tr -d ':[:space:]' | tr 'A-F' 'a-f')
actual=$(sed -nE 's/^(Signer #1|V3[.]0 Signer): certificate SHA-256 digest: //p' <<< "$report" | head -n 1 | tr -d '[:space:]')
if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
  echo "::error::The APK signer does not match the keystore certificate."
  exit 1
fi
echo "APK signature verified"

cd "$(dirname "$signed")"
sha256sum "$(basename "$signed")" "$@" > SHA256SUMS
sha256sum -c SHA256SUMS
