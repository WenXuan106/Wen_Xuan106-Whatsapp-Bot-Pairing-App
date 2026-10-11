#!/usr/bin/env bash
# Builds WABot.apk with only Ubuntu packages + the prebuilt Node.js runtime (no Gradle / Android Studio / NDK):
#   sudo apt install patchelf openjdk-21-jdk-headless aapt apksigner zipalign dalvik-exchange android-framework-res clang lld llvm zip
# Needs: prebuilt/<abi>/{libnode.so,libc++_shared.so}  (see README.md)   and   assets/bot.zip (see make-bot-bundle.sh)
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-android-34.jar}                       # API stubs only used to compile
FRAMEWORK_RES=/usr/share/android-framework-res/framework-res.apk
KEYSTORE=${KEYSTORE:-botpair.keystore}
KEY_ALIAS=${KEY_ALIAS:-botpair}
KEY_PASS=${KEY_PASS:-botpair-sideload}

[ -f assets/bot.zip ] || { echo "assets/bot.zip is missing - run ./make-bot-bundle.sh first"; exit 1; }
[ -f "$ANDROID_JAR" ] || { echo "Put android.jar (API 34) at $ANDROID_JAR - see README.md"; exit 1; }

rm -rf build && mkdir -p build/classes build/lib build/assets
./build-native.sh

# 1. app code
javac --release 8 -Xlint:-options -classpath "$ANDROID_JAR" -d build/classes $(find src -name '*.java')
dalvik-exchange --dex --output=build/classes.dex build/classes

# 2. the bot's files, as an asset the app unpacks on first start
cp assets/bot.zip build/assets/bot.zip
unzip -Z1 assets/bot.zip | grep -vc '/$' > build/assets/bot.count || true
sha1sum assets/bot.zip | cut -d' ' -f1 > build/assets/bot.stamp

# 3. native libraries
for abi in arm64-v8a armeabi-v7a; do
  mkdir -p build/lib/$abi
  cp prebuilt/$abi/libnode.so prebuilt/$abi/libc++_shared.so build/native/$abi/libnodeglue.so build/native/$abi/libemutls.so build/lib/$abi/
  # libnode.so needs __emutls_get_address; make the C++ runtime (which libnode already depends on) pull in libemutls.so
  patchelf --add-needed libemutls.so build/lib/$abi/libc++_shared.so
done

# 4. package, align, sign
aapt package -f -0 zip -M AndroidManifest.xml -S res -A build/assets -I "$FRAMEWORK_RES" -F build/unsigned.apk
(cd build && aapt add unsigned.apk classes.dex lib/*/* >/dev/null)
zipalign -f -p 4 build/unsigned.apk build/aligned.apk
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass "$KEY_PASS" -keypass "$KEY_PASS" -dname "CN=Bot Pairing, O=Wen_Xuan106"
fi
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" --ks-pass "pass:$KEY_PASS" --key-pass "pass:$KEY_PASS" \
  --min-sdk-version 23 --out WABot.apk build/aligned.apk
apksigner verify --verbose WABot.apk | head -4
ls -la WABot.apk
