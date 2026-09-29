package org.libreoffice.androidapp.feedback;

/**
 * 反馈服务地址（V1.2）。验收前在此填写，例如 {@code http://192.168.1.10:9010}，无尾斜杠。
 */
public final class FeedbackConfig {

    /**
     * 为空时允许进入 UI，网络请求前 Toast 并中止。
     * 本机 Mock 联调（debug）：先 {@code python3 issues/android-feedback-api/mock-server.py --host 0.0.0.0}
     * 模拟器填 {@code http://10.0.2.2:9010}，真机填 {@code http://<电脑局域网IP>:9010}。
     */
    public static final String API_BASE_URL = "";

    /**
     * 扩展 {@link #PATH_UPLOAD_LOG}：后端定稿前保持 false，勾选日志仅 Toast。
     * 联调时改为 true，并在 submit 中携带返回的 logPath（字段名以后端为准）。
     */
    public static final boolean LOG_UPLOAD_ENABLED = false;

    public static final long MAX_LOG_BYTES = 5L * 1024 * 1024;

    public static final String PATH_UPLOAD_AVATAR = "/v1.0/feedback/uploadAvatar";
    public static final String PATH_UPLOAD_IMAGES = "/v1.0/feedback/uploadImages";
    public static final String PATH_UPLOAD_LOG = "/v1.0/feedback/uploadLog";
    public static final String PATH_SUBMIT = "/v1.0/feedback/submit";
    public static final String PATH_LIST = "/v1.0/feedback/list";
    public static final String PATH_DETAIL = "/v1.0/feedback/detail";
    public static final String PATH_CLOSE = "/v1.0/feedback/close";

    private FeedbackConfig() {
    }

    public static boolean isConfigured() {
        return API_BASE_URL != null && !API_BASE_URL.trim().isEmpty();
    }

    public static boolean isLogUploadEnabled() {
        return LOG_UPLOAD_ENABLED && isConfigured();
    }

    public static String endpoint(String path) {
        String base = API_BASE_URL.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    public static String assetUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return "";
        }
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;
        }
        String rel = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return endpoint("/ai_office/" + rel);
    }
}
