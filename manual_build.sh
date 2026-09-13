#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 || (${2:-} != "" && ${2:-} != "--unsigned") ]]; then
  echo "Usage: $0 /path/to/android-sdk [--unsigned]" >&2
  exit 2
fi

project_dir="$(cd "$(dirname "$0")" && pwd)"
android_sdk="$1"
build_tools="$android_sdk/build-tools/34.0.0"
android_jar="$android_sdk/platforms/android-34/android.jar"
prebuilt_so="$project_dir/manual-prebuilt/arm64-v8a/libhev-socks5-tunnel.so"
output_apk="$project_dir/release/ParvesCombo-Premium-v3.2.apk"
if [[ ${2:-} == "--unsigned" ]]; then
  output_apk="$project_dir/release/ParvesCombo-Premium-v3.2-unsigned.apk"
fi
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT

for required in "$build_tools/aapt2" "$build_tools/d8" \
  "$build_tools/zipalign" "$build_tools/apksigner" "$android_jar" "$prebuilt_so"; do
  if [[ ! -e "$required" ]]; then
    echo "Missing build input: $required" >&2
    exit 1
  fi
done

mkdir -p "$project_dir/release" "$temp_dir/generated" "$temp_dir/classes" "$temp_dir/dex" "$temp_dir/merge"

"$build_tools/aapt2" compile \
  --dir "$project_dir/app/src/main/res" \
  -o "$temp_dir/resources.zip"

"$build_tools/aapt2" link \
  -o "$temp_dir/resources.apk" \
  -I "$android_jar" \
  --manifest "$project_dir/app/src/main/AndroidManifest.xml" \
  --java "$temp_dir/generated" \
  --custom-package hev.sockstun \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code 5 \
  --version-name 3.2 \
  "$temp_dir/resources.zip"

mapfile -d '' java_sources < <(find \
  "$project_dir/app/src/main/java" "$temp_dir/generated" \
  -type f -name '*.java' -print0)

java --add-modules jdk.compiler com.sun.tools.javac.Main \
  -encoding UTF-8 -source 8 -target 8 \
  -cp "$android_jar" \
  -d "$temp_dir/classes" \
  "${java_sources[@]}"

(
  cd "$temp_dir/classes"
  zip -q -r "$temp_dir/classes.jar" .
)
"$build_tools/d8" \
  --min-api 24 \
  --lib "$android_jar" \
  --output "$temp_dir/dex" \
  "$temp_dir/classes.jar"

cp "$temp_dir/dex/classes.dex" "$temp_dir/merge/classes.dex"
mkdir -p "$temp_dir/merge/lib/arm64-v8a"
cp "$prebuilt_so" "$temp_dir/merge/lib/arm64-v8a/libhev-socks5-tunnel.so"
cp "$temp_dir/resources.apk" "$temp_dir/unsigned.apk"
(
  cd "$temp_dir/merge"
  zip -q -r "$temp_dir/unsigned.apk" classes.dex lib
)

"$build_tools/zipalign" -f -p 4 "$temp_dir/unsigned.apk" "$temp_dir/aligned.apk"
"$build_tools/zipalign" -c -p 4 "$temp_dir/aligned.apk"
if [[ ${2:-} == "--unsigned" ]]; then
  cp "$temp_dir/aligned.apk" "$output_apk"
  echo "Unsigned build (not installable until signed): $output_apk"
  exit 0
fi
"$build_tools/apksigner" sign \
  --ks "$project_dir/debug.keystore" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$output_apk" \
  "$temp_dir/aligned.apk"

"$build_tools/apksigner" verify --verbose "$output_apk"
echo "$output_apk"
