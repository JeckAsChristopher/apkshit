#!/data/data/com.termux/files/usr/bin/bash
set -e

TERMUX_PREFIX="/data/data/com.termux/files/usr"
PROJ="$(cd "$(dirname "$0")" && pwd)"
CPP_DIR="$PROJ/app/src/main/cpp"
OUT="$PROJ/app/src/main/jniLibs/arm64-v8a"
BUILD="$PROJ/.native_build"

echo "==> Installing tools..."
"$TERMUX_PREFIX/bin/pkg" install -y clang cmake git

# Find jni.h
JNI_H="$(find "$TERMUX_PREFIX" -name "jni.h" 2>/dev/null | head -1)"
JNI_DIR="$(dirname "$JNI_H")"
JNI_MD="$(find "$TERMUX_PREFIX" -name "jni_md.h" 2>/dev/null | head -1)"
JNI_MD_DIR="$(dirname "$JNI_MD")"
echo "==> jni.h: $JNI_DIR"

echo "==> Cloning llama.cpp..."
if [ ! -d "$CPP_DIR/llama.cpp" ]; then
  "$TERMUX_PREFIX/bin/git" clone --depth 1 \
    https://github.com/ggerganov/llama.cpp "$CPP_DIR/llama.cpp"
fi

rm -rf "$BUILD"
mkdir -p "$BUILD"

echo "==> Building llama.cpp with -fPIC (~10 min)..."
cmake -S "$CPP_DIR/llama.cpp" -B "$BUILD" \
  -DCMAKE_C_COMPILER="$TERMUX_PREFIX/bin/clang" \
  -DCMAKE_CXX_COMPILER="$TERMUX_PREFIX/bin/clang++" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DCMAKE_C_FLAGS="-fPIC -march=armv8.2-a+dotprod+fp16" \
  -DCMAKE_CXX_FLAGS="-fPIC -march=armv8.2-a+dotprod+fp16" \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_SERVER=OFF \
  -DLLAMA_CURL=OFF \
  -DBUILD_SHARED_LIBS=OFF \
  -DGGML_OPENMP=OFF \
  -DLLAMA_OPENMP=OFF \
  -DGGML_CPU_ARM_ARCH="armv8.2-a+dotprod+fp16" \
  -DGGML_NATIVE=OFF

cmake --build "$BUILD" --target llama ggml -j$(nproc)

echo "==> Linking JNI bridge..."
mkdir -p "$OUT"

STATIC_LIBS="$(find "$BUILD" -name '*.a' | tr '\n' ' ')"

"$TERMUX_PREFIX/bin/clang++" -shared -fPIC -std=c++17 -O2 \
  -I "$CPP_DIR/llama.cpp/include" \
  -I "$CPP_DIR/llama.cpp/ggml/include" \
  -I "$JNI_DIR" \
  -I "$JNI_MD_DIR" \
  "$CPP_DIR/locai_jni.cpp" \
  $STATIC_LIBS \
  -o "$OUT/liblocai_jni.so"

echo "==> Copying libc++_shared.so..."
# libc++_shared.so lives in Termux's clang lib directory
LIBCXX="$(find "$TERMUX_PREFIX" -name "libc++_shared.so" 2>/dev/null | head -1)"

if [ -n "$LIBCXX" ]; then
  cp "$LIBCXX" "$OUT/libc++_shared.so"
  echo "    Copied: $LIBCXX"
else
  # Termux clang links libc++ statically by default — try static link instead
  echo "    libc++_shared.so not found, relinking with static libc++..."
  "$TERMUX_PREFIX/bin/clang++" -shared -fPIC -std=c++17 -O2 \
    -static-libstdc++ \
    -I "$CPP_DIR/llama.cpp/include" \
    -I "$CPP_DIR/llama.cpp/ggml/include" \
    -I "$JNI_DIR" \
    -I "$JNI_MD_DIR" \
    "$CPP_DIR/locai_jni.cpp" \
    $STATIC_LIBS \
    -o "$OUT/liblocai_jni.so"
  echo "    Linked with static libc++ — no libc++_shared.so needed"
fi

echo ""
echo "==> Output:"
ls -lh "$OUT/"
echo ""
echo "Done! Now run: ./gradlew assembleDebug"
