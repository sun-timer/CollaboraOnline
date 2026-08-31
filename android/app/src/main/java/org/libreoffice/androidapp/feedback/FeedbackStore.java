package org.libreoffice.androidapp.feedback;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 反馈记录本地存储（SharedPreferences + JSON）。
 * 后端 API 尚未接入：提交/查询目前在本地闭环（见 {@link FeedbackApi} 占位），
 * API url 确定后在此之上切换为网络数据源。
 */
public final class FeedbackStore {

    private static final String PREFS = "feedback_store";
    private static final String KEY_RECORDS = "records";
    private static final String ACTION_REPLY_TEXT =
            "感谢您的反馈，我们已经收到并转交相关同事处理。";
    private static final long REPLY_DELAY_MS = 0; // API 接入前即时模拟回复

    private FeedbackStore() {
    }

    public static synchronized List<FeedbackRecord> load(Context context) {
        List<FeedbackRecord> records = new ArrayList<>();
        try {
            String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_RECORDS, null);
            if (raw == null || raw.isEmpty()) {
                return records;
            }
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                records.add(FeedbackRecord.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return records;
    }

    public static synchronized void save(Context context, List<FeedbackRecord> records) {
        JSONArray arr = new JSONArray();
        for (FeedbackRecord r : records) {
            arr.put(r.toJson());
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_RECORDS, arr.toString()).apply();
    }

    /** 新增记录（新记录在前）。 */
    public static synchronized void add(Context context, FeedbackRecord record) {
        List<FeedbackRecord> records = load(context);
        records.add(0, record);
        save(context, records);
    }

    /** 按编号查找。 */
    public static synchronized FeedbackRecord find(Context context, String id) {
        for (FeedbackRecord r : load(context)) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** 更新状态（保存整个列表，保持顺序）。 */
    public static synchronized void update(Context context, FeedbackRecord updated) {
        List<FeedbackRecord> records = load(context);
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).id.equals(updated.id)) {
                records.set(i, updated);
                break;
            }
        }
        save(context, records);
    }
}