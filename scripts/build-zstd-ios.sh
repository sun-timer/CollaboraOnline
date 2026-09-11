#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: build-zstd-ios.sh <abs-path-to-build-top-dir> [OS64|SIMULATORARM64]" >&2
    exit 2
fi

BUILD_PATH=$1
PLATFORM=${2:-OS64}

case "$PLATFORM" in
    OS64)
        DEPLOYMENT_TARGET=13.6
        ;;
    SIMULATORARM64)
        DEPLOYMENT_TARGET=14.5
        ;;
    *)
        echo "Unsupported iOS platform: $PLATFORM" >&2
        exit 2
        ;;
esac

if [[ "$BUILD_PATH" != /* ]]; then
    echo "Build top directory must be an absolute path: $BUILD_PATH" >&2
    exit 2
fi

# Device and Simulator outputs are intentionally isolated. Never remove the
# shared install directory: install/OS64 may contain the shipping Device build.
ZSTD_SOURCE="$BUILD_PATH/ios-zstd"
TOOLCHAIN="$BUILD_PATH/ios-cmake/ios.toolchain.cmake"
BUILD_DIR="$ZSTD_SOURCE/build-$PLATFORM"
INSTALL_DIR="$ZSTD_SOURCE/install/$PLATFORM"

if [[ ! -f "$TOOLCHAIN" || ! -f "$ZSTD_SOURCE/build/cmake/CMakeLists.txt" ]]; then
    echo "Expected existing ios-cmake and ios-zstd checkouts under $BUILD_PATH" >&2
    exit 1
fi

cmake \
    -S "$ZSTD_SOURCE/build/cmake" \
    -B "$BUILD_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DPLATFORM="$PLATFORM" \
    -DDEPLOYMENT_TARGET="$DEPLOYMENT_TARGET" \
    -DENABLE_BITCODE=OFF \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR" \
    -DZSTD_BUILD_PROGRAMS=OFF \
    -DZSTD_BUILD_SHARED=OFF \
    -DZSTD_BUILD_STATIC=ON \
    -DZSTD_BUILD_TESTS=OFF

cmake --build "$BUILD_DIR" --target libzstd_static --parallel 4
cmake --install "$BUILD_DIR"

echo "zstd $PLATFORM installed in $INSTALL_DIR"
