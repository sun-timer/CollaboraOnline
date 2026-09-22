#!/usr/bin/env bash
# Builds llama.xcframework into ios/build-deps/ when missing (one-time, ~10+ min).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CO_ROOT="$(cd "${IOS_ROOT}/.." && pwd)"
LLAMA_DIR="${CO_ROOT}/android/lib/src/main/cpp/llama.cpp"
OUT="${IOS_ROOT}/build-deps/llama.xcframework"

if [[ -d "${OUT}" ]]; then
    exit 0
fi

if [[ ! -f "${LLAMA_DIR}/build-xcframework.sh" ]]; then
    echo "error: llama.cpp submodule missing at ${LLAMA_DIR}" >&2
    exit 1
fi

echo "Building llama.xcframework (Metal); output -> ${OUT}"
export GIT_CONFIG_COUNT=1
export GIT_CONFIG_KEY_0='safe.directory'
export GIT_CONFIG_VALUE_0="${LLAMA_DIR}"
(
    cd "${LLAMA_DIR}"
    ./build-xcframework.sh
)
mkdir -p "$(dirname "${OUT}")"
rm -rf "${OUT}"
mv "${LLAMA_DIR}/build-apple/llama.xcframework" "${OUT}"
echo "Done: ${OUT}"
