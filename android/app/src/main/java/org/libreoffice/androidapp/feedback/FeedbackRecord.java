package org.libreoffice.androidapp.feedback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** 反馈列表/详情展示模型（与服务端 V1.2 字段对应）。 */
public class FeedbackRecord {

    public enum Status {
        SUBMITTED,
        PROCESSING,
        REPLIED,
        RESOLVED,
        CLOSED
    }

    /** 服务端 feedbackNo */
    public String id = "";
    public String type = "";
    public long submitTime;
    public String content = "";
    /** 用户图：相对路径或本地 content URI 字符串 */
    public List<String> imageUris = new ArrayList<>();
    public String contact = "";
    public boolean shareLog;
    public Status status = Status.SUBMITTED;
    public String replyText = "";
    public long replyTime = 0;
    public List<String> replyImageUris = new ArrayList<>();

    public boolean canClose() {
        return status == Status.REPLIED || status == Status.RESOLVED;
    }

    public static Status fromApiLabel(String label) {
        if (label == null) {
            return Status.SUBMITTED;
        }
        switch (label) {
            case "处理中":
                return Status.PROCESSING;
            case "已回复":
                return Status.REPLIED;
            case "已解决":
                return Status.RESOLVED;
            case "关闭":
                return Status.CLOSED;
            case "已提交":
            default:
                return Status.SUBMITTED;
        }
    }

    static FeedbackRecord fromDetailJson(JSONObject data) {
        FeedbackRecord r = new FeedbackRecord();
        r.id = data.optString("feedbackNo");
        r.type = data.optString("feedbackType");
        r.content = data.optString("content");
        r.contact = data.optString("contact", "");
        r.status = fromApiLabel(data.optString("status"));
        r.submitTime = parseServerTimeStatic(data.optString("submitTime"));
        r.imageUris = jsonStringList(data.optJSONArray("imagePaths"));
        JSONObject reply = data.optJSONObject("reply");
        if (reply != null) {
            r.replyText = reply.optString("replyContent", "");
            r.replyTime = parseServerTimeStatic(reply.optString("replyTime"));
            r.replyImageUris = jsonStringList(reply.optJSONArray("imagePaths"));
        }
        return r;
    }

    private static List<String> jsonStringList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr == null) {
            return list;
        }
        for (int i = 0; i < arr.length(); i++) {
            list.add(arr.optString(i));
        }
        return list;
    }

    private static long parseServerTimeStatic(String text) {
        if (text == null || text.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            Date d = fmt.parse(text);
            return d != null ? d.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /** @deprecated 仅保留给旧 JSON 迁移；新数据来自 API */
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("type", type);
            o.put("submitTime", submitTime);
            o.put("content", content);
            o.put("imageUris", new JSONArray(imageUris));
            o.put("contact", contact);
            o.put("shareLog", shareLog);
            o.put("status", status.name());
            o.put("replyText", replyText);
            o.put("replyTime", replyTime);
            o.put("replyImageUris", new JSONArray(replyImageUris));
        } catch (JSONException ignored) {
        }
        return o;
    }
}
