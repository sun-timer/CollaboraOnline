package org.libreoffice.androidapp.feedback;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 反馈 V1.2 API 门面（{@link FeedbackClient} + {@link FeedbackIdentityStore}）。
 */
public final class FeedbackApi {

    public interface VoidCallback {
        void onSuccess();

        void onError(String reason, String message);
    }

    public interface RecordCallback {
        void onSuccess(FeedbackRecord record);

        void onError(String reason, String message);
    }

    public interface ListCallback {
        void onSuccess(java.util.List<FeedbackRecord> records);

        void onError(String reason, String message);
    }

    public static final class ListPage {
        public int pageNum;
        public int pageSize;
        public int total;
        public int pages;
        public final java.util.List<FeedbackRecord> list = new java.util.ArrayList<>();
    }

    public interface ListPageCallback {
        void onSuccess(ListPage page);

        void onError(String reason, String message);
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private FeedbackApi() {
    }

    public static boolean isConfigured() {
        return FeedbackConfig.isConfigured();
    }

    public static void runAsync(Runnable task) {
        EXEC.execute(task);
    }

    static void postSuccess(VoidCallback callback) {
        if (callback == null) {
            return;
        }
        MAIN.post(callback::onSuccess);
    }

    static void postError(VoidCallback callback, String reason, String message) {
        if (callback == null) {
            return;
        }
        MAIN.post(() -> callback.onError(reason, message));
    }

    private static void postSuccess(RecordCallback callback, FeedbackRecord record) {
        if (callback == null) {
            return;
        }
        MAIN.post(() -> callback.onSuccess(record));
    }

    private static void postError(RecordCallback callback, String reason, String message) {
        if (callback == null) {
            return;
        }
        MAIN.post(() -> callback.onError(reason, message));
    }

    private static void postSuccess(ListCallback callback,
            java.util.List<FeedbackRecord> records) {
        if (callback == null) {
            return;
        }
        MAIN.post(() -> callback.onSuccess(records));
    }

    private static void postError(ListCallback callback, String reason, String message) {
        if (callback == null) {
            return;
        }
        MAIN.post(() -> callback.onError(reason, message));
    }

    public static void submitForm(Context context, String feedbackType, String content,
            String contact, List<Uri> imageUris, boolean shareLogRequested,
            RecordCallback callback) {
        if (!isConfigured()) {
            postError(callback, "feedback_api_not_configured", "");
            return;
        }
        if (shareLogRequested && !FeedbackConfig.isLogUploadEnabled()) {
            postError(callback, "feedback_log_upload_pending", "");
            return;
        }
        String trimmed = content != null ? content.trim() : "";
        int len = trimmed.codePointCount(0, trimmed.length());
        if (len < 10 || len > 500) {
            postError(callback, "feedback_validation", "content length");
            return;
        }
        final String bodyContent = trimmed;
        runAsync(() -> {
            try {
                Context app = context.getApplicationContext();
                String userId = FeedbackIdentityStore.getOrCreateUserId(app);
                String nickname = FeedbackIdentityStore.getNickname(app);
                String avatar = FeedbackIdentityStore.getAvatarServerPath(app);
                Uri localAvatar = FeedbackIdentityStore.getLocalAvatarUri(app);
                if ((avatar == null || avatar.isEmpty()) && localAvatar != null) {
                    avatar = FeedbackClient.uploadAvatar(app, userId, localAvatar);
                    FeedbackIdentityStore.setAvatarServerPath(app, avatar);
                }
                List<String> paths = imageUris.isEmpty()
                        ? java.util.Collections.emptyList()
                        : FeedbackClient.uploadImages(app, imageUris);
                String logPath = null;
                if (shareLogRequested) {
                    java.io.File logFile = FeedbackLogExporter.exportLogFile(app);
                    try {
                        logPath = FeedbackClient.uploadLog(app, userId, logFile);
                        android.util.Log.i("LOActivity",
                                "feedback log upload ok logPath=" + logPath);
                    } finally {
                        if (logFile != null && logFile.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            logFile.delete();
                        }
                    }
                }
                String version = appVersion(app);
                FeedbackRecord record = FeedbackClient.submit(app, userId, nickname, avatar,
                        feedbackType, bodyContent, contact, paths, logPath, version,
                        FeedbackClient.deviceModel(), FeedbackClient.osVersion());
                android.util.Log.i("LOActivity",
                        "feedback submit ok feedbackNo=" + record.id);
                postSuccess(callback, record);
            } catch (FeedbackApiException e) {
                android.util.Log.w("LOActivity",
                        "feedback_core_fail reason=" + e.reason + " msg=" + e.getMessage());
                postError(callback, e.reason, e.getMessage());
            }
        });
    }

    public static void fetchList(Context context, int pageNum, ListCallback callback) {
        fetchListPage(context, pageNum, new ListPageCallback() {
            @Override
            public void onSuccess(ListPage page) {
                postSuccess(callback, page.list);
            }

            @Override
            public void onError(String reason, String message) {
                postError(callback, reason, message);
            }
        });
    }

    public static void fetchListPage(Context context, int pageNum, ListPageCallback callback) {
        if (!isConfigured()) {
            postErrorListPage(callback, "feedback_api_not_configured", "");
            return;
        }
        runAsync(() -> {
            try {
                Context app = context.getApplicationContext();
                String userId = FeedbackIdentityStore.getOrCreateUserId(app);
                String nickname = FeedbackIdentityStore.getNickname(app);
                String avatar = FeedbackIdentityStore.getAvatarServerPath(app);
                FeedbackClient.FeedbackListPage raw = FeedbackClient.list(
                        app, userId, nickname, avatar, pageNum, 10);
                ListPage page = new ListPage();
                page.pageNum = raw.pageNum;
                page.pageSize = raw.pageSize;
                page.total = raw.total;
                page.pages = raw.pages;
                page.list.addAll(raw.list);
                MAIN.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(page);
                    }
                });
            } catch (FeedbackApiException e) {
                android.util.Log.w("LOActivity",
                        "feedback_core_fail reason=" + e.reason + " msg=" + e.getMessage());
                postErrorListPage(callback, e.reason, e.getMessage());
            }
        });
    }

    private static void postErrorListPage(ListPageCallback callback, String reason,
            String message) {
        if (callback == null) {
            return;
        }
        MAIN.post(() -> callback.onError(reason, message));
    }

    public static void fetchDetail(Context context, String feedbackNo, RecordCallback callback) {
        if (!isConfigured()) {
            postError(callback, "feedback_api_not_configured", "");
            return;
        }
        runAsync(() -> {
            try {
                Context app = context.getApplicationContext();
                String userId = FeedbackIdentityStore.getOrCreateUserId(app);
                String nickname = FeedbackIdentityStore.getNickname(app);
                String avatar = FeedbackIdentityStore.getAvatarServerPath(app);
                FeedbackRecord record = FeedbackClient.detail(
                        app, userId, nickname, avatar, feedbackNo);
                postSuccess(callback, record);
            } catch (FeedbackApiException e) {
                android.util.Log.w("LOActivity",
                        "feedback_core_fail reason=" + e.reason + " msg=" + e.getMessage());
                postError(callback, e.reason, e.getMessage());
            }
        });
    }

    public static void closeFeedback(Context context, String feedbackNo, VoidCallback callback) {
        if (!isConfigured()) {
            postError(callback, "feedback_api_not_configured", "");
            return;
        }
        runAsync(() -> {
            try {
                Context app = context.getApplicationContext();
                String userId = FeedbackIdentityStore.getOrCreateUserId(app);
                String nickname = FeedbackIdentityStore.getNickname(app);
                String avatar = FeedbackIdentityStore.getAvatarServerPath(app);
                FeedbackClient.close(app, userId, nickname, avatar, feedbackNo);
                postSuccess(callback);
            } catch (FeedbackApiException e) {
                android.util.Log.w("LOActivity",
                        "feedback_core_fail reason=" + e.reason + " msg=" + e.getMessage());
                postError(callback, e.reason, e.getMessage());
            }
        });
    }

    private static String appVersion(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
