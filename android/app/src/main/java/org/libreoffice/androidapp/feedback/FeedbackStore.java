package org.libreoffice.androidapp.feedback;

import android.content.Context;

/**
 * 旧版本地 mock 存储；接 API 后仅用于一次性清空 legacy 数据。
 */
public final class FeedbackStore {

    private static final String PREFS = "feedback_store";
    private static final String KEY_RECORDS = "records";
    private static final String KEY_LEGACY_CLEARED = "legacy_mock_cleared_v1";

    private FeedbackStore() {
    }

    /** 方案 A：首次接 API 时清空旧 mock 列表。 */
    public static void clearLegacyMockIfNeeded(Context context) {
        Context app = context.getApplicationContext();
        if (app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_LEGACY_CLEARED, false)) {
            return;
        }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_RECORDS)
                .putBoolean(KEY_LEGACY_CLEARED, true)
                .apply();
    }
}
