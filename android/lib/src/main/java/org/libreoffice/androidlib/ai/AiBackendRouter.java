package org.libreoffice.androidlib.ai;

public final class AiBackendRouter {
    public static final boolean LOCAL_DOC_QA_ENABLED = false;
    public static final int LOCAL_DOC_QA_MAX_CHARS = 8000;
    /** Rough prefill cap for on-device inference (operate-mode tasks). */
    public static final int LOCAL_MAX_PREFILL_TOKENS = 256;

    public static final class ResolvedRoute {
        public final int backend;
        public final String modelMode;
        public final String reason;

        public ResolvedRoute(int backend, String modelMode, String reason) {
            this.backend = backend;
            this.modelMode = modelMode;
            this.reason = reason == null ? "" : reason;
        }
    }

    public static final class LocalModelState {
        public final boolean deviceSupported;
        public final boolean installed;
        public final boolean enabled;

        public LocalModelState(boolean deviceSupported, boolean installed, boolean enabled) {
            this.deviceSupported = deviceSupported;
            this.installed = installed;
            this.enabled = enabled;
        }

        public boolean isReady() {
            return deviceSupported && installed && enabled && LocalInferenceEngine.isNativeAvailable();
        }
    }

    public ResolvedRoute resolve(String taskType, String modelMode, int docCharCount, LocalModelState state) {
        if (isCloudOnlyTask(taskType)) {
            return new ResolvedRoute(AiBackend.BACKEND_CLOUD, modelMode, "cloud_only_task");
        }

        if (isLocalLongPrefillTask(taskType)) {
            return new ResolvedRoute(AiBackend.BACKEND_CLOUD, modelMode, "local_long_task_cloud");
        }

        if (AiChatCoordinator.MODE_DOC_QA.equals(taskType)) {
            if (!LOCAL_DOC_QA_ENABLED) {
                return new ResolvedRoute(AiBackend.BACKEND_CLOUD, "base", "doc_qa_default_cloud");
            }
            if (docCharCount > LOCAL_DOC_QA_MAX_CHARS) {
                return new ResolvedRoute(AiBackend.BACKEND_CLOUD, "base", "doc_qa_too_long_for_local");
            }
        }

        if (docCharCount > LOCAL_MAX_PREFILL_TOKENS * 4) {
            return new ResolvedRoute(AiBackend.BACKEND_CLOUD, modelMode, "local_prefill_too_long");
        }

        if (state != null && state.isReady() && isLocalTextTask(taskType)) {
            return new ResolvedRoute(AiBackend.BACKEND_LOCAL, "local", "local_ready");
        }

        return new ResolvedRoute(AiBackend.BACKEND_CLOUD, modelMode, "cloud_fallback");
    }

    public static boolean isCloudOnlyTask(String taskType) {
        return AiChatCoordinator.MODE_IMAGE_GENERATE.equals(taskType)
                || AiChatCoordinator.MODE_TEXT_EXTRACT.equals(taskType);
    }

    /** Long-context generation tasks stay on cloud even when local model is ready. */
    public static boolean isLocalLongPrefillTask(String taskType) {
        if (taskType == null || taskType.isEmpty()) {
            return false;
        }
        switch (taskType) {
            case AiChatCoordinator.MODE_OUTLINE:
            case AiChatCoordinator.MODE_CONTINUE:
            case AiChatCoordinator.MODE_TYPESET:
            case AiChatCoordinator.MODE_ARTICLE_GENERATE:
            case AiChatCoordinator.MODE_IMPRESS_OUTLINE:
            case AiChatCoordinator.MODE_IMPRESS_GENERATE:
                return true;
            default:
                return false;
        }
    }

    public static boolean isLocalTextTask(String taskType) {
        if (taskType == null || taskType.isEmpty()) {
            return false;
        }
        if (AiChatCoordinator.MODE_FORMAT_BATCH.equals(taskType)) {
            return false;
        }
        if (isLocalLongPrefillTask(taskType)) {
            return false;
        }
        if (AiChatCoordinator.MODE_DOC_QA.equals(taskType)) {
            return LOCAL_DOC_QA_ENABLED;
        }
        switch (taskType) {
            case AiChatCoordinator.MODE_CHAT:
            case AiChatCoordinator.MODE_EXPAND:
            case AiChatCoordinator.MODE_POLISH:
            case AiChatCoordinator.MODE_CONDENSE:
            case AiChatCoordinator.MODE_REWRITE:
            case AiChatCoordinator.MODE_TRANSLATE:
            case AiChatCoordinator.MODE_CALC_FORMULA:
            case AiChatCoordinator.MODE_CALC_COND_FORMAT:
            case AiChatCoordinator.MODE_CALC_NEW_TABLE:
            case AiChatCoordinator.MODE_CALC_DATA_PROCESS:
            case AiChatCoordinator.MODE_CALC_DATA_ANALYSIS:
            case AiChatCoordinator.MODE_CALC_CHART:
                return true;
            default:
                return false;
        }
    }

    public static boolean isMultiTurnTask(String taskType) {
        return AiChatCoordinator.MODE_CHAT.equals(taskType)
                || AiChatCoordinator.MODE_DOC_QA.equals(taskType);
    }
}
