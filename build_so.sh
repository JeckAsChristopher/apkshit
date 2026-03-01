#!/data/data/com.termux/files/usr/bin/bash
set -e

PROJ="$(cd "$(dirname "$0")" && pwd)"
CPP_DIR="$PROJ/app/src/main/cpp"
OUT="$PROJ/app/src/main/jniLibs/arm64-v8a"
BUILD="$PROJ/.native_build"

echo "==> Installing tools..."
pkg install -y clang cmake git

# Find jni.h
JNI_H="$(find /data/data/com.termux/files/usr -name "jni.h" 2>/dev/null | head -1)"
JNI_DIR="$(dirname "$JNI_H")"
JNI_MD="$(find /data/data/com.termux/files/usr -name "jni_md.h" 2>/dev/null | head -1)"
JNI_MD_DIR="$(dirname "$JNI_MD")"
echo "==> jni.h: $JNI_DIR"

echo "==> Cloning llama.cpp..."
if [ ! -d "$CPP_DIR/llama.cpp" ]; then
  git clone --depth 1 https://github.com/ggerganov/llama.cpp "$CPP_DIR/llama.cpp"
fi

# Wipe old build — must rebuild with -fPIC
rm -rf "$BUILD"
mkdir -p "$BUILD"

echo "==> Building llama.cpp with -fPIC (takes ~10 min)..."
cmake -S "$CPP_DIR/llama.cpp" -B "$BUILD" \
  -DCMAKE_C_COMPILER=clang \
  -DCMAKE_CXX_COMPILER=clang++ \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DCMAKE_C_FLAGS="-fPIC" \
  -DCMAKE_CXX_FLAGS="-fPIC" \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_SERVER=OFF \
  -DLLAMA_CURL=OFF \
  -DBUILD_SHARED_LIBS=OFF

cmake --build "$BUILD" --target llama ggml -j$(nproc)

echo "==> Linking JNI bridge..."
mkdir -p "$OUT"

# Collect all static libs produced by the build
STATIC_LIBS="$(find "$BUILD" -name '*.a' | tr '\n' ' ')"

clang++ -shared -fPIC -std=c++17 -O2 \
  -I "$CPP_DIR/llama.cpp/include" \
  -I "$CPP_DIR/llama.cpp/ggml/include" \
  -I "$JNI_DIR" \
  -I "$JNI_MD_DIR" \
  "$CPP_DIR/locai_jni.cpp" \
  $STATIC_LIBS \
  -o "$OUT/liblocai_jni.so"

echo ""
echo "Done! $(du -sh $OUT/liblocai_jni.so)"
echo "Now run: ./gradlew assembleDebug"
