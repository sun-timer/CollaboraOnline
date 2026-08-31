package org.libreoffice.androidapp.feedback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 一条本地反馈记录（对应 Figma「反馈记录」列表/详情数据模型）。 */
public class FeedbackRecord {

    public enum Status {
        /** 已提交（列表灰点） */
        SUBMITTED,
        /** 处理中（底部「已收到，感谢反馈」） */
        PROCESSING,
        /** 已回复（蓝点，含客服回复气泡） */
        REPLIED,
        /** 已关闭（底部「该反馈已关闭」） */
        CLOSED
    }

    /** 反馈编号，格式 yyyyMMddHHmm（Figma 例：202604150028）。 */
    public String id;
    /** 问题类型文案（功能异常…）。 */
    public String type;
    /** 提交时间 epoch millis。 */
    public long submitTime;
    /** 问题描述。 */
    public String content;
    /** 附件图片 content uri 列表。 */
    public List<String> imageUris = new ArrayList<>();
    /** 联系方式（选填）。 */
    public String contact = "";
    /** 是否共享应用日志。 */
    public boolean shareLog;
    public Status status = Status.SUBMITTED;
    /** 客服回复（REPLIED 时有效）。 */
    public String replyText = "";
    public long replyTime = 0;
    public List<String> replyImageUris = new ArrayList<>();

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

    public static FeedbackRecord fromJson(JSONObject o) {
        FeedbackRecord r = new FeedbackRecord();
        r.id = o.optString("id");
        r.type = o.optString("type");
        r.submitTime = o.optLong("submitTime");
        r.content = o.optString("content");
        r.imageUris = listFrom(o.optJSONArray("imageUris"));
        r.contact = o.optString("contact");
        r.shareLog = o.optBoolean("shareLog");
        try {
            r.status = Status.valueOf(o.optString("status", Status.SUBMITTED.name()));
        } catch (IllegalArgumentException e) {
            r.status = Status.SUBMITTED;
        }
        r.replyText = o.optString("replyText");
        r.replyTime = o.optLong("replyTime");
        r.replyImageUris = listFrom(o.optJSONArray("replyImageUris"));
        return r;
    }

    private static List<String> listFrom(JSONArray a) {
        List<String> list = new ArrayList<>();
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                list.add(a.optString(i));
            }
        }
        return list;
    }
}