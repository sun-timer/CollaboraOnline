package org.libreoffice.androidapp.feedback;

/**
 * 反馈服务地址（V1.2）。验收前在此填写，例如 {@code http://192.168.1.10:9010}，无尾斜杠。
 */
public final class FeedbackConfig {

    /** 为空时允许进入 UI，网络请求前 Toast 并中止。 */
    public static final String API_BASE_URL = "";

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
