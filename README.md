# WA Bot (Android app that runs the bot on the phone)

* `WABot.apk` — install it, tap **Start bot**, then **Pair WhatsApp**.
* The app contains Node.js 18 (from nodejs-mobile) and your bot. Nothing is hosted anywhere: each phone runs its own bot
  for its own WhatsApp number, and the login stays on that phone.

## Rebuilding
Install: `sudo apt install openjdk-21-jdk-headless aapt apksigner zipalign dalvik-exchange android-framework-res clang lld llvm zip fonts-dejavu-core`

Not included in this zip (too big) — get them once:
1. `prebuilt/<abi>/libnode.so` — from https://github.com/nodejs-mobile/nodejs-mobile/releases (v18.20.4, `nodejs-mobile-v18.20.4-android.zip`, `bin/arm64-v8a` and `bin/armeabi-v7a`), then `llvm-strip --strip-unneeded`.
2. `prebuilt/<abi>/libc++_shared.so` — from any Android NDK (r25 or newer), `sysroot/usr/lib/<triple>/libc++_shared.so`.
3. `android-34.jar` — the Android API stubs (e.g. from https://github.com/Sable/android-platforms), used only to compile.
4. `assets/bot.zip` — run `./make-bot-bundle.sh /path/to/your/bot`.

Then `./build.sh` produces `WABot.apk`. `botpair.keystore` signs it (password in build.sh): keep using the same one so updates install over the old version.
