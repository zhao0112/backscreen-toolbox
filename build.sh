#!/bin/sh
# Build Back Screen Toolbox APK without Gradle.
#
# Environment overrides:
#   ANDROID_SDK_ROOT / ANDROID_HOME  Android SDK path        (default: ~/Android/Sdk)
#   ANDROID_PLATFORM                 platform dir name        (default: android-36)
#   BUILD_TOOLS_VERSION              build-tools version      (default: 36.0.0)
#   JAVA_HOME                        JDK 17 path              (default: ~/Android/jdk17)
#   KEYSTORE / KEY_ALIAS / KS_PASS_FILE                      signing config
#   SKIP_SIGN=1                      build unsigned APK only
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
PLATFORM="${ANDROID_PLATFORM:-android-36}"
BT_VER="${BUILD_TOOLS_VERSION:-36.0.0}"
BT="$SDK/build-tools/$BT_VER"
PLAT="$SDK/platforms/$PLATFORM/android.jar"
JDK="${JAVA_HOME:-$HOME/Android/jdk17}"
MIN_SDK="${MIN_SDK_VERSION:-34}"
TARGET_SDK="${TARGET_SDK_VERSION:-36}"

# 签名配置。默认用 Android Studio 常见的 debug.keystore；
# 缺文件就自动跳过签名，输出 unsigned APK。
KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"
KEY_ALIAS="${KEY_ALIAS:-androiddebugkey}"
KS_PASS_FILE="${KS_PASS_FILE:-}"
SKIP_SIGN="${SKIP_SIGN:-0}"

[ -f "$PLAT" ] || { echo "missing $PLAT (set ANDROID_SDK_ROOT / ANDROID_PLATFORM)"; exit 1; }
[ -x "$JDK/bin/javac" ] || { echo "missing JDK 17 at $JDK (set JAVA_HOME)"; exit 1; }

export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$BT:$PATH"

rm -rf "$HERE/build"
mkdir -p "$HERE/build/classes" "$HERE/build/dex" "$HERE/build/gen"

echo "== 1/6 compile resources =="
"$BT/aapt2" compile --dir "$HERE/res" -o "$HERE/build/res.zip"

echo "== 2/6 aapt2 link (generates R.java) =="
AAPT_ASSETS=""
[ -d "$HERE/assets" ] && AAPT_ASSETS="-A $HERE/assets"
"$BT/aapt2" link -I "$PLAT" --manifest "$HERE/AndroidManifest.xml" \
    --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
    $AAPT_ASSETS \
    --java "$HERE/build/gen" -o "$HERE/build/unsigned.apk" "$HERE/build/res.zip"

echo "== 3/6 javac =="
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLAT" \
    -d "$HERE/build/classes" $(find "$HERE/src" "$HERE/build/gen" -name '*.java')

echo "== 4/6 d8 =="
"$BT/d8" --min-api "$MIN_SDK" --lib "$PLAT" --output "$HERE/build/dex" \
    $(find "$HERE/build/classes" -name '*.class')

echo "== 5/6 package + assets + zipalign =="
(cd "$HERE/build/dex" && zip -q "$HERE/build/unsigned.apk" classes.dex)
# assets 目录打进 APK 根目录
"$BT/zipalign" -P 16 -f 4 "$HERE/build/unsigned.apk" "$HERE/build/aligned.apk"
cp "$HERE/build/aligned.apk" "$HERE/build/backscreen-player-unsigned.apk"

if [ "$SKIP_SIGN" = "1" ] || [ ! -f "$KEYSTORE" ]; then
    echo "== 6/6 skip signing (unsigned APK) =="
    echo "output: $HERE/build/backscreen-player-unsigned.apk"
    exit 0
fi

echo "== 6/6 apksigner =="
# 密码优先级：KS_PASS_FILE > 环境变量 KS_PASS > 默认口令
if [ -n "$KS_PASS_FILE" ] && [ -f "$KS_PASS_FILE" ]; then
    SHARE_KIT_STORE_PASS="$(cat "$KS_PASS_FILE")"
elif [ -n "${KS_PASS:-}" ]; then
    SHARE_KIT_STORE_PASS="$KS_PASS"
else
    SHARE_KIT_STORE_PASS="android"
fi
export SHARE_KIT_STORE_PASS
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass env:SHARE_KIT_STORE_PASS --key-pass env:SHARE_KIT_STORE_PASS \
    --out "$HERE/build/backscreen-player.apk" "$HERE/build/aligned.apk"

echo "output: $HERE/build/backscreen-player.apk"
