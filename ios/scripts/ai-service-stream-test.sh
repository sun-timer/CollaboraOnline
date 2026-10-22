#!/usr/bin/env bash
# Exercise AIService's real URLSession streaming callbacks without networking.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
AI="${IOS_ROOT}/Mobile/AI"
OUT="${IOS_ROOT}/build/ai-service-stream-test"
mkdir -p "${OUT}"
xcrun clang++ -std=c++17 -fobjc-arc -I"${AI}" \
    "${IOS_ROOT}/tools/ai-service-stream-test-main.mm" \
    "${AI}/AIService.mm" "${AI}/AIRequestSession.mm" \
    "${AI}/AIConfigurationStore.mm" "${AI}/AIModelConfigStore.mm" \
    "${AI}/KeychainStore.mm" "${AI}/AiBackendRouter.mm" \
    "${AI}/LocalModelStore.mm" "${IOS_ROOT}/tools/LocalInferenceEngineTestStub.mm" \
    -framework Foundation -framework Security -o "${OUT}/ai-service-stream-test"
"${OUT}/ai-service-stream-test"
