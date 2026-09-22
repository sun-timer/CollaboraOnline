#!/usr/bin/env bash
# Compile and run AiBackendRouter + LocalPromptBuilder logic tests (no Xcode test target).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
AI="${IOS_ROOT}/Mobile/AI"
OUT="${IOS_ROOT}/build/ai-logic-test"
BIN="${OUT}/ai-logic-test"

mkdir -p "${OUT}"

echo "Compiling ai-logic-test..."
clang++ -std=c++17 -fobjc-arc -O0 -g \
    -I"${AI}" \
    "${AI}/AiBackendRouter.mm" \
    "${AI}/LocalPromptBuilder.mm" \
    "${AI}/LocalModelStore.mm" \
    "${IOS_ROOT}/tools/LocalInferenceEngineTestStub.mm" \
    "${IOS_ROOT}/tools/ai-logic-test-main.mm" \
    -framework Foundation \
    -o "${BIN}"

echo "Running..."
"${BIN}"
