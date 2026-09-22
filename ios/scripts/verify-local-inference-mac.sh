#!/usr/bin/env bash
# Mac-side gate for iOS local inference: logic tests, xcframework, Debug Simulator build, smoke compile.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${IOS_ROOT}/.." && pwd)"

XCFW="${IOS_ROOT}/build-deps/llama.xcframework"
FAIL=0

step() { echo ""; echo "== $1"; }

step "1/4 llama.xcframework"
if [[ ! -d "${XCFW}/ios-arm64_x86_64-simulator" ]]; then
    echo "FAIL: missing ${XCFW}" >&2
    FAIL=1
else
    du -sh "${XCFW}"
    "${SCRIPT_DIR}/ensure-llama-xcframework.sh"
    echo "OK"
fi

step "2/4 ai-logic-test (Router + Prompt)"
"${SCRIPT_DIR}/ai-logic-test.sh"

step "3/4 Xcode Mobile Debug · iphonesimulator (arm64)"
DEST='platform=iOS Simulator,name=iPhone 17,OS=26.5'
if ! xcodebuild -project "${IOS_ROOT}/Mobile.xcodeproj" -scheme Mobile \
    -sdk iphonesimulator -destination "${DEST}" -configuration Debug build \
    | tail -5 | grep -q 'BUILD SUCCEEDED'; then
    echo "FAIL: Debug Simulator build" >&2
    FAIL=1
else
    echo "OK: Debug BUILD SUCCEEDED"
fi

step "4/4 llama-smoke compile (optional run needs .gguf)"
FW="${XCFW}/macos-arm64_x86_64/llama.framework"
OUT="${IOS_ROOT}/build/llama-smoke"
mkdir -p "${OUT}"
clang++ -std=c++17 -O2 -arch "$(uname -m)" \
    -I"${FW}/Headers" -I"${IOS_ROOT}/Mobile/AI" \
    -framework llama -framework Accelerate -framework Metal -framework Foundation \
    -F"${XCFW}/macos-arm64_x86_64" \
    "${IOS_ROOT}/Mobile/AI/LlamaInferenceCore.cpp" "${IOS_ROOT}/tools/llama-smoke-main.cpp" \
    -o "${OUT}/llama-smoke"
echo "OK: ${OUT}/llama-smoke"
if [[ -n "${1:-}" && -f "${1}" ]]; then
    echo "Running smoke with ${1}..."
    "${OUT}/llama-smoke" "$1"
fi

echo ""
if [[ "${FAIL}" -ne 0 ]]; then
    echo "verify-local-inference-mac: FAILED" >&2
    exit 1
fi
echo "verify-local-inference-mac: ALL PASSED"
echo "Next: Xcode → Mobile · Debug · Simulator → ⌘R (本地模型下载/启用/续写)"
