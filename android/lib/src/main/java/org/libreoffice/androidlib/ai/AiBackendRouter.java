package org.libreoffice.androidlib.ai;

public final class AiBackendRouter {
    public static final boolean LOCAL_DOC_QA_ENABLED = false;
    public static final int LOCAL_DOC_QA_MAX_CHARS = 8000;

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

        if (AiChatCoordinator.MODE_DOC_QA.equals(taskType)) {
            if (!LOCAL_DOC_QA_ENABLED) {
                return new ResolvedRoute(AiBackend.BACKEND_CLOUD, "base", "doc_qa_default_cloud");
            }
            if (docCharCount > LOCAL_DOC_QA_MAX_CHARS) {
                return new ResolvedRoute(AiBackend.BACKEND_CLOUD, "base", "doc_qa_too_long_for_local");
            }
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

    /** Text tasks use local when the on-device model is ready (efficiency experiments). */
    public static boolean isLocalTextTask(String taskType) {
        if (taskType == null || taskType.isEmpty()) {
            return false;
        }
        if (isCloudOnlyTask(taskType)) {
            return false;
        }
        if (AiChatCoordinator.MODE_FORMAT_BATCH.equals(taskType)) {
            return false;
        }
        if (AiChatCoordinator.MODE_DOC_QA.equals(taskType)) {
            return LOCAL_DOC_QA_ENABLED;
        }
        return true;
    }

    public static boolean isMultiTurnTask(String taskType) {
        return AiChatCoordinator.MODE_CHAT.equals(taskType)
                || AiChatCoordinator.MODE_DOC_QA.equals(taskType);
    }
}
