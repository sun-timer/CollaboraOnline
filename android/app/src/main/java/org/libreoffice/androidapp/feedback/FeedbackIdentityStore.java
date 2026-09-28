package org.libreoffice.androidapp.feedback;

import android.content.Context;
import android.net.Uri;

import org.libreoffice.androidapp.ui.AiSettingsStore;

import java.util.UUID;

/**
 * 反馈 API 用户标识（与 {@link AiSettingsStore} 分离）。
 */
public final class FeedbackIdentityStore {

    private static final String PREFS = "feedback_identity";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_AVATAR_SERVER_PATH = "avatar_server_path";

    private FeedbackIdentityStore() {
    }

    public static synchronized String getOrCreateUserId(Context context) {
        Context app = context.getApplicationContext();
        String id = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_USER_ID, null);
        if (id == null || id.isEmpty()) {
            id = "aio-" + UUID.randomUUID().toString().replace("-", "");
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_USER_ID, id).apply();
        }
        return id;
    }

    public static String getNickname(Context context) {
        String name = AiSettingsStore.prefs(context).getString(
                AiSettingsStore.KEY_PROFILE_NAME, "");
        if (name == null) {
            return "";
        }
        name = name.trim();
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    public static String getAvatarServerPath(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_AVATAR_SERVER_PATH, "");
    }

    public static void setAvatarServerPath(Context context, String relativePath) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_AVATAR_SERVER_PATH, relativePath != null ? relativePath : "")
                .apply();
    }

    /** 资料页改头像后调用（后台线程）。 */
    public static void uploadAvatarFromUri(Context context, Uri uri, FeedbackApi.VoidCallback callback) {
        FeedbackApi.runAsync(() -> {
            try {
                if (!FeedbackConfig.isConfigured()) {
                    FeedbackApi.postError(callback, "feedback_api_not_configured", "");
                    return;
                }
                String path = FeedbackClient.uploadAvatar(
                        context, getOrCreateUserId(context), uri);
                setAvatarServerPath(context, path);
                FeedbackApi.postSuccess(callback);
            } catch (FeedbackApiException e) {
                FeedbackApi.postError(callback, e.reason, e.getMessage());
            }
        });
    }

    /** submit 前：若无 server path 且有本地头像 URI，尝试 upload。 */
    public static void ensureAvatarUploaded(Context context, Uri localAvatarUri,
            FeedbackApi.VoidCallback callback) {
        String existing = getAvatarServerPath(context);
        if (existing != null && !existing.isEmpty()) {
            FeedbackApi.postSuccess(callback);
            return;
        }
        if (localAvatarUri == null) {
            FeedbackApi.postSuccess(callback);
            return;
        }
        uploadAvatarFromUri(context, localAvatarUri, callback);
    }

    static Uri getLocalAvatarUri(Context context) {
        String raw = AiSettingsStore.prefs(context)
                .getString(AiSettingsStore.KEY_PROFILE_AVATAR_URI, "");
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Uri.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
