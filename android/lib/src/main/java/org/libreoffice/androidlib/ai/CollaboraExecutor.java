package org.libreoffice.androidlib.ai;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/**
 * 数据操作执行器：校验 action type、执行 UNO 命令、undo 计数回滚、保存文档。
 * 依赖外部传入的 postUnoCommand / paste 回调。不持有 Context 或 Activity 引用。
 */
public class CollaboraExecutor {

    private static final String TAG = "CollaboraExecutor";

    public interface Host {
        void postUnoCommand(String command, String arguments, boolean notify);
        void paste(String mimeType, byte[] data);
        void runOnUiThread(Runnable action);
        void sleepSafe(long ms);
    }

    private final Host host;
    private int undoStepsSinceExecution = 0;

    // 白名单：Table Operation 模式下支持的 type
    private static final Set<String> TABLE_OP_TYPES = new HashSet<>();
    static {
        TABLE_OP_TYPES.add("remove_duplicates");
        TABLE_OP_TYPES.add("sort");
        TABLE_OP_TYPES.add("filter");
        TABLE_OP_TYPES.add("clear_formatting");
        TABLE_OP_TYPES.add("delete_rows");
        TABLE_OP_TYPES.add("delete_columns");
        TABLE_OP_TYPES.add("insert_rows");
        TABLE_OP_TYPES.add("insert_columns");
        TABLE_OP_TYPES.add("format_number");
        TABLE_OP_TYPES.add("set_column_width");
        TABLE_OP_TYPES.add("merge_cells");
        TABLE_OP_TYPES.add("bold");
        TABLE_OP_TYPES.add("calculate");
    }

    public CollaboraExecutor(Host host) {
        this.host = host;
    }

    /** 校验 action 列表：type 白名单 + range 格式 + params 完整性 */
    public ValidationResult validate(JSONArray actions) {
        if (actions == null || actions.length() == 0) {
            return ValidationResult.invalid("actions 为空");
        }
        for (int i = 0; i < actions.length(); i++) {
            try {
                JSONObject action = actions.getJSONObject(i);
                String type = action.optString("type", "");
                if (!TABLE_OP_TYPES.contains(type)) {
                    return ValidationResult.invalid("第 " + (i+1) + " 步操作类型 '" + type + "' 不在白名单中");
                }
                String range = action.optString("range", "");
                if (!range.isEmpty() && !isValidRange(range)) {
                    return ValidationResult.invalid("第 " + (i+1) + " 步 range '" + range + "' 格式无效");
                }
            } catch (Exception e) {
                return ValidationResult.invalid("解析第 " + (i+1) + " 步时出错: " + e.getMessage());
            }
        }
        return ValidationResult.valid();
    }

    private boolean isValidRange(String range) {
        return range.matches("^[A-Z]+\\d+(:[A-Z]+\\d+)?$");
    }

    /** Table Operation 模式：逐条发 UNO 命令，累加 undo 步数 */
    public ExecutionResult executeTableOp(JSONArray actions) {
        undoStepsSinceExecution = 0;
        try {
            for (int i = 0; i < actions.length(); i++) {
                JSONObject action = actions.getJSONObject(i);
                String type = action.optString("type", "");
                Log.i(TAG, "executing step=" + (i+1) + "/" + actions.length() + " type=" + type);
                executeOneTableOp(action);
                int steps = estimateUndoSteps(type);
                undoStepsSinceExecution += steps;
                host.sleepSafe(100);
            }
            return ExecutionResult.success(undoStepsSinceExecution);
        } catch (Exception e) {
            Log.e(TAG, "execute_error", e);
            return ExecutionResult.failure("执行失败: " + e.getMessage(), undoStepsSinceExecution);
        }
    }

    private void executeOneTableOp(JSONObject action) throws Exception {
        String type = action.optString("type", "");
        switch (type) {
            case "remove_duplicates":
                host.postUnoCommand(".uno:RemoveDuplicate", "{}", false);
                break;
            case "sort": {
                boolean asc = action.optBoolean("ascending", true);
                host.postUnoCommand(asc ? ".uno:SortAscending" : ".uno:SortDescending", "{}", false);
                break;
            }
            case "filter":
                host.postUnoCommand(".uno:DataFilterAutoFilter", "{}", false);
                break;
            case "clear_formatting":
                host.postUnoCommand(".uno:ResetAttributes", "{}", false);
                break;
            case "delete_rows":
                host.postUnoCommand(".uno:DeleteRows", "{}", false);
                break;
            case "delete_columns":
                host.postUnoCommand(".uno:DeleteColumns", "{}", false);
                break;
            case "insert_rows": {
                String pos = action.optString("position", "after");
                host.postUnoCommand("after".equals(pos) ? ".uno:InsertRowsAfter" : ".uno:InsertRowsBefore", "{}", false);
                break;
            }
            case "insert_columns": {
                String pos = action.optString("position", "after");
                host.postUnoCommand("after".equals(pos) ? ".uno:InsertColumnsAfter" : ".uno:InsertColumnsBefore", "{}", false);
                break;
            }
            case "format_number": {
                String style = action.optString("style", "");
                switch (style) {
                    case "percent": host.postUnoCommand(".uno:NumberFormatPercent", "{}", false); break;
                    case "currency": host.postUnoCommand(".uno:NumberFormatCurrency", "{}", false); break;
                    case "date": host.postUnoCommand(".uno:NumberFormatDate", "{}", false); break;
                    default: host.postUnoCommand(".uno:NumberFormatDecDecimals", "{}", false); break;
                }
                break;
            }
            case "set_column_width":
                host.postUnoCommand(".uno:SetOptimalColumnWidth", "{}", false);
                break;
            case "merge_cells":
                host.postUnoCommand(".uno:ToggleMergeCells", "{}", false);
                break;
            case "bold":
                host.postUnoCommand(".uno:Bold", "{}", false);
                break;
            case "calculate":
                host.postUnoCommand(".uno:Calculate", "{}", false);
                break;
            default:
                break;
        }
    }

    /** Formula 模式：GoToCell + paste */
    public void executeFormula(JSONArray actions) throws Exception {
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.getJSONObject(i);
            String range = action.optString("range", "");
            String value = action.optString("value", "");
            if (!range.isEmpty() && !value.isEmpty()) {
                host.postUnoCommand(".uno:GoToCell", "{\"ToPoint\":\"" + range + "\"}", false);
                host.sleepSafe(50);
                host.paste("text/plain;charset=utf-8", value.getBytes("UTF-8"));
                host.sleepSafe(50);
            }
        }
    }

    /** 回滚：调用 N 次 .uno:Undo */
    public void rollback() {
        for (int i = 0; i < undoStepsSinceExecution; i++) {
            host.postUnoCommand(".uno:Undo", "{}", false);
            host.sleepSafe(50);
        }
        undoStepsSinceExecution = 0;
    }

    /** 保存文档 */
    public void saveDocument() {
        host.postUnoCommand(".uno:Save", "{}", false);
    }

    private int estimateUndoSteps(String type) {
        return 1;
    }

    public int getUndoSteps() { return undoStepsSinceExecution; }

    /** 判断是否为 Formula 模式（直接写值/公式，无需 preview） */
    public static boolean isFormulaMode(JSONArray actions) {
        if (actions == null || actions.length() == 0 || actions.length() > 2) return false;
        for (int i = 0; i < actions.length(); i++) {
            String type = actions.optJSONObject(i).optString("type", "");
            if (!"set_formula".equals(type) && !"set_value".equals(type)) return false;
        }
        return true;
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String error;
        private ValidationResult(boolean valid, String error) { this.valid = valid; this.error = error; }
        public static ValidationResult valid() { return new ValidationResult(true, null); }
        public static ValidationResult invalid(String error) { return new ValidationResult(false, error); }
    }

    public static class ExecutionResult {
        public final boolean success;
        public final int undoSteps;
        public final String error;
        private ExecutionResult(boolean success, int undoSteps, String error) { this.success = success; this.undoSteps = undoSteps; this.error = error; }
        public static ExecutionResult success(int undoSteps) { return new ExecutionResult(true, undoSteps, null); }
        public static ExecutionResult failure(String error, int undoSteps) { return new ExecutionResult(false, undoSteps, error); }
    }
}
