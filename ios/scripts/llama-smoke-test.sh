#!/usr/bin/env bash
# Build and run LlamaInferenceCore smoke test on macOS (no iPhone required).
# Requires: Xcode CLI, llama.xcframework (macOS slice), a local .gguf file.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CO_ROOT="$(cd "${IOS_ROOT}/.." && pwd)"

XCFW="${IOS_ROOT}/build-deps/llama.xcframework"
if [[ ! -d "${XCFW}" ]]; then
    XCFW="${CO_ROOT}/android/lib/src/main/cpp/llama.cpp/build-apple/llama.xcframework"
fi
if [[ ! -d "${XCFW}" ]]; then
    echo "error: llama.xcframework not found; run ios/scripts/ensure-llama-xcframework.sh first" >&2
    exit 1
fi

MODEL="${1:-}"
if [[ -z "${MODEL}" || ! -f "${MODEL}" ]]; then
    echo "Usage: $0 /path/to/model.gguf" >&2
    exit 2
fi

FW="${XCFW}/macos-arm64_x86_64/llama.framework"
if [[ ! -d "${FW}" ]]; then
    echo "error: macOS framework slice missing under ${XCFW}" >&2
    exit 1
fi

OUT="${IOS_ROOT}/build/llama-smoke"
mkdir -p "${OUT}"

CORE="${IOS_ROOT}/Mobile/AI/LlamaInferenceCore.cpp"
MAIN="${IOS_ROOT}/tools/llama-smoke-main.cpp"
BIN="${OUT}/llama-smoke"

echo "Compiling smoke test..."
clang++ -std=c++17 -O2 \
    -arch "$(uname -m)" \
    -I"${FW}/Headers" \
    -I"${IOS_ROOT}/Mobile/AI" \
    -framework llama -framework Accelerate -framework Metal -framework Foundation \
    -F"${XCFW}/macos-arm64_x86_64" \
    "${CORE}" "${MAIN}" \
    -o "${BIN}"

echo "Running inference (CPU/Metal via llama.cpp on macOS)..."
"${BIN}" "${MODEL}"
