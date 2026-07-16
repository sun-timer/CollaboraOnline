package org.libreoffice.androidlib.ai;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 数据处理编排器。
 * 6 步流程：collectContext → requestPlan → validate → preview → execute → verify
 */
public class AiDataProcessor {

    public interface Host {
        void showToast(String text);
        void showPlanPreview(String title, String message, Runnable onConfirm, Runnable onCancel);
        void runOnUiThread(Runnable r);
        void postUnoCommand(String cmd, String args, boolean notify);
        void paste(String mimeType, byte[] data);
    }

    private static final String TAG = "AiDataProcessor";
    private final Host host;
    private final CollaboraExecutor executor;

    public AiDataProcessor(Host host) {
        this.host = host;
        this.executor = new CollaboraExecutor(new CollaboraExecutor.Host() {
            public void postUnoCommand(String cmd, String args, boolean notify) { host.postUnoCommand(cmd, args, notify); }
            public void paste(String mimeType, byte[] data) { host.paste(mimeType, data); }
            public void runOnUiThread(Runnable r) { host.runOnUiThread(r); }
            public void sleepSafe(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }
        });
    }

    /** 入口：collectContext → requestPlan(已由 AI 完成) → validate → preview → execute → verify → save */
    public void process(JSONObject aiResponse, String cellRange) {
        new Thread(() -> {
            try {
                // Step 1-2: collectContext 和 requestPlan 已在调用前完成（AI 已返回）
                // 解析 AI 返回的 actions
                JSONArray actions = parseActions(aiResponse);
                if (actions == null || actions.length() == 0) {
                    host.runOnUiThread(() -> host.showToast("AI 未返回可执行的操作，请重试"));
                    return;
                }

                // Step 3: validate
                CollaboraExecutor.ValidationResult vr = executor.validate(actions);
                if (!vr.valid) {
                    host.runOnUiThread(() -> host.showToast("操作校验失败: " + vr.error));
                    return;
                }

                // Step 4: decide mode + preview
                if (CollaboraExecutor.isFormulaMode(actions)) {
                    // Formula 模式：直接执行，不弹 preview
                    executor.executeFormula(actions);
                    executor.saveDocument();
                    host.runOnUiThread(() -> host.showToast("数据处理完成"));
                } else {
                    // Table Operation 模式：弹 preview 确认
                    String previewMsg = buildPreviewMessage(actions);
                    AtomicBoolean confirmed = new AtomicBoolean(false);
                    CountDownLatch latch = new CountDownLatch(1);
                    host.runOnUiThread(() -> {
                        host.showPlanPreview(
                            "AI 数据处理计划",
                            previewMsg,
                            () -> { confirmed.set(true); latch.countDown(); },
                            () -> { confirmed.set(false); latch.countDown(); }
                        );
                    });
                    latch.await();
                    if (!confirmed.get()) {
                        host.runOnUiThread(() -> host.showToast("已取消"));
                        return;
                    }

                    // Step 5: execute
                    CollaboraExecutor.ExecutionResult er = executor.executeTableOp(actions);
                    if (!er.success) {
                        executor.rollback();
                        host.runOnUiThread(() -> host.showToast("数据处理失败，已自动回滚"));
                        return;
                    }

                    // Step 6: verify (简化版 — LO 异步，信任 UNO 执行结果)
                    executor.saveDocument();
                    host.runOnUiThread(() -> host.showToast("数据处理完成"));
                }
            } catch (Exception e) {
                Log.e(TAG, "process_error", e);
                executor.rollback();
                host.runOnUiThread(() -> host.showToast("处理失败，已自动回滚"));
            }
        }).start();
    }

    private JSONArray parseActions(JSONObject response) {
        JSONArray actions = response.optJSONArray("actions");
        if (actions != null && actions.length() > 0) return actions;
        // Fallback: check "operations" field (old format)
        return response.optJSONArray("operations");
    }

    private String buildPreviewMessage(JSONArray actions) {
        StringBuilder sb = new StringBuilder();
        sb.append("影响范围：");
        // Collect ranges for summary
        StringBuilder ranges = new StringBuilder();
        for (int i = 0; i < actions.length(); i++) {
            JSONObject act = actions.optJSONObject(i);
            if (act == null) continue;
            String range = act.optString("range", "");
            if (!range.isEmpty()) {
                if (ranges.length() > 0) ranges.append(", ");
                ranges.append(range);
            }
        }
        if (ranges.length() > 0) {
            sb.append(ranges).append("\n\n");
        } else {
            sb.append("当前选中区域\n\n");
        }

        for (int i = 0; i < actions.length(); i++) {
            JSONObject act = actions.optJSONObject(i);
            if (act == null) continue;
            String type = act.optString("type", "");
            String range = act.optString("range", "");
            String label = getActionLabel(type);
            sb.append(i + 1).append(". [").append(label).append("]");
            if (!range.isEmpty()) sb.append(" ").append(range);
            sb.append("\n");
        }
        sb.append("\n可一键撤回");
        return sb.toString();
    }

    private String getActionLabel(String type) {
        switch (type) {
            case "remove_duplicates": return "删除重复行";
            case "sort": return "排序";
            case "filter": return "自动筛选";
            case "clear_formatting": return "清除格式";
            case "delete_rows": return "删除行";
            case "delete_columns": return "删除列";
            case "insert_rows": return "插入行";
            case "insert_columns": return "插入列";
            case "format_number": return "数字格式";
            case "set_column_width": return "自适应列宽";
            case "merge_cells": return "合并单元格";
            case "bold": return "加粗";
            case "calculate": return "重新计算";
            case "set_formula": return "写公式";
            case "set_value": return "写值";
            default: return type;
        }
    }
}
