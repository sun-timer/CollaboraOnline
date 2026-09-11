#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: build-poco-ios.sh <abs-path-to-build-top-dir> [OS64|SIMULATORARM64]" >&2
    exit 2
fi

BUILD_PATH=$1
PLATFORM=${2:-OS64}

case "$PLATFORM" in
    OS64|SIMULATORARM64) ;;
    *)
        echo "Unsupported iOS platform: $PLATFORM" >&2
        exit 2
        ;;
esac

if [[ "$BUILD_PATH" != /* ]]; then
    echo "Build top directory must be an absolute path: $BUILD_PATH" >&2
    exit 2
fi

# Keep Device and Simulator builds side by side. Each configuration has its
# own CMake tree; both install into the selected platform directory so Release
# and Debug-postfix libraries can coexist.
POCO_SOURCE="$BUILD_PATH/ios-poco"
TOOLCHAIN="$BUILD_PATH/ios-cmake/ios.toolchain.cmake"
INSTALL_DIR="$POCO_SOURCE/install/$PLATFORM"

if [[ ! -f "$TOOLCHAIN" || ! -f "$POCO_SOURCE/CMakeLists.txt" ]]; then
    echo "Expected existing ios-cmake and ios-poco checkouts under $BUILD_PATH" >&2
    exit 1
fi

for CONFIGURATION in Release Debug; do
    BUILD_DIR="$POCO_SOURCE/build-$PLATFORM-$CONFIGURATION"

    cmake \
        -S "$POCO_SOURCE" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DPLATFORM="$PLATFORM" \
        -DDEPLOYMENT_TARGET=14.5 \
        -DENABLE_BITCODE=OFF \
        -DCMAKE_BUILD_TYPE="$CONFIGURATION" \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR" \
        -DBUILD_SHARED_LIBS=OFF \
        -DPOCO_MINIMAL_BUILD=ON \
        -DENABLE_FOUNDATION=ON \
        -DENABLE_XML=ON \
        -DENABLE_JSON=ON \
        -DENABLE_UTIL=ON \
        -DENABLE_NET=ON \
        -DENABLE_TESTS=OFF \
        -DENABLE_SAMPLES=OFF

    cmake --build "$BUILD_DIR" --parallel 4
    cmake --install "$BUILD_DIR"
done

echo "POCO $PLATFORM installed in $INSTALL_DIR"
