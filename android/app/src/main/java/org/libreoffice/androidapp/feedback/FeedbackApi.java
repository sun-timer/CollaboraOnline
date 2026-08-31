package org.libreoffice.androidapp.feedback;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 反馈提交回传接口（占位）。
 *
 * TODO(api): 后端接口 URL 待定（需求方尚未提供），接入后在此实现真实提交/查询：
 *   1. POST {api_url}/feedback    body: {type, content, images[], contact, shareLog}
 *   2. GET  {api_url}/feedback/list?page=  返回记录 + 状态 + 客服回复
 * 当前阶段：提交直接落本地 {@link FeedbackStore}，状态按时间推进，保证全流程可演示。
 */
public final class FeedbackApi {

    /** 占位：后端提交地址，确定后替换。 */
    private static final String SUBMIT_URL = ""; // TODO(api): feedback submit url

    private FeedbackApi() {
    }

    /** 生成反馈编号（yyyyMMddHHmm，对齐 Figma 示例 202604150028）。 */
    public static String newFeedbackId(long millis) {
        return new SimpleDateFormat("yyyyMMddHHmm", Locale.US).format(new Date(millis));
    }

    /**
     * 模拟提交：API 接入前直接本地落库，状态置为已提交（随后推进为处理中）。
     * API 接入后改为网络请求：URL 从 {@link #SUBMIT_URL} 读取。
     */
    public static void submit(Context context, FeedbackRecord record) {
        FeedbackStore.add(context, record);
    }

    /** API 接入前：本地模拟客服回复（演示「已回复」状态与回复气泡）。 */
    public static void simulateReply(Context context, FeedbackRecord record) {
        if (record.status == FeedbackRecord.Status.SUBMITTED
                || record.status == FeedbackRecord.Status.PROCESSING) {
            record.status = FeedbackRecord.Status.REPLIED;
            record.replyTime = System.currentTimeMillis();
            record.replyText = ACTION_REPLY_TEXT;
            FeedbackStore.update(context, record);
        }
    }

    private static final String ACTION_REPLY_TEXT =
            "感谢您的反馈，我们已经收到并转交相关同事处理。";
}