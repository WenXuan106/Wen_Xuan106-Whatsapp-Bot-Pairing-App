#!/usr/bin/env bash
# Builds libnodeglue.so for arm64 and 32-bit ARM phones with Ubuntu's clang + lld (no Android NDK needed).
set -euo pipefail
cd "$(dirname "$0")"
JDK=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
for pair in arm64-v8a:aarch64-linux-android24 armeabi-v7a:armv7a-linux-androideabi24; do
  abi=${pair%%:*}; triple=${pair##*:}
  out=build/native/$abi; mkdir -p "$out" stubs/$abi
  flags="--target=$triple -fuse-ld=lld -nostdlib -ffreestanding -fno-stack-protector -fPIC -O2 -Wl,-z,max-page-size=16384"
  clang $flags -shared -Wl,-soname,libc.so  -o stubs/$abi/libc.so  stubs/libc_stub.c
  clang $flags -shared -Wl,-soname,libdl.so -o stubs/$abi/libdl.so stubs/libdl_stub.c
  clang $flags -shared -nostdlibinc -Inative/fakeinc -I"$JDK/include" -I"$JDK/include/linux" -Wl,-soname,libnodeglue.so \
        -L stubs/$abi -lc -ldl -o "$out/libnodeglue.so" native/nodeglue.c
  clang $flags -shared -nostdlibinc -Wl,-soname,libemutls.so -L stubs/$abi -lc -o "$out/libemutls.so" native/emutls.c
  for lib in libnodeglue.so libemutls.so; do
    llvm-strip --strip-unneeded "$out/$lib" 2>/dev/null || /usr/lib/llvm-18/bin/llvm-strip --strip-unneeded "$out/$lib"
  done
  echo "built $out/libnodeglue.so"
done
