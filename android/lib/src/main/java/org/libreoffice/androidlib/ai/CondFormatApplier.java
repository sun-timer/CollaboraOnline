package org.libreoffice.androidlib.ai;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 条件格式执行器：解析 AI 返回的 JSON → 打开 ConditionalFormatEasy 弹窗 → JS 自动填充 → 点 OK。
 * 条件格式类型与 FormatRule 映射见 getFormatRule()。
 */
public class CondFormatApplier {

    private static final String TAG = "CondFormatApplier";

    public interface Host {
        /** 经 fake WebSocket 派发 UNO（走 ChildSession，可收到 unocommandresult）。 */
        void postMobileUnoCommand(String command, String argumentsJson);
        void registerApplyResultCallback(ApplyResultCallback callback);
        /** @deprecated 旧版弹窗流程使用 */
        void postUnoCommand(String command, String arguments, boolean notify);
        void evaluateJavascript(String script);
        void evaluateJavascript(String script, android.webkit.ValueCallback<String> callback);
        void runOnUiThread(Runnable r);
    }

    /** 条件格式 UNO 命令完成回调（success 来自 unocommandresult）。 */
    public interface ApplyResultCallback {
        void onResult(boolean success);
    }

    private final Host host;

    public CondFormatApplier(Host host) {
        this.host = host;
    }

    /**
     * AI 返回的条件格式计划：JSON 解析后的结构化参数。
     */
    public static class CondFormatPlan {
        public final String conditionType;  // greater, top_n, etc.
        public final String value;          // entryNumber 值
        public final String value2;         // entryNumber2 值（between 用）
        public final String range;          // 应用范围
        public final String style;          // Bad / Good / Neutral（format 缺失时兜底）
        public final String formatJson;     // {"backgroundColor":"#FFCCCC","fontColor":"#CC0000"}
        public final String description;    // 中文说明

        public CondFormatPlan(String conditionType, String value, String value2,
                              String range, String style, String formatJson, String description) {
            this.conditionType = conditionType;
            this.value = value != null ? value : "";
            this.value2 = value2 != null ? value2 : "";
            this.range = range != null ? range : "";
            this.style = style != null ? style : "Bad";
            this.formatJson = formatJson;
            this.description = description != null ? description : "";
        }

        public boolean isValid() {
            return conditionType != null && !conditionType.isEmpty();
        }

        public boolean hasCustomFormat() {
            return formatJson != null && !formatJson.isEmpty();
        }
    }

    // ========================================================================
    // 旧版文本解析（保留为 fallback）
    // ========================================================================

    public static class ParsedRules {
        public final int formatRule;
        public final String value;
        public final String style;

        public ParsedRules(int formatRule, String value, String style) {
            this.formatRule = formatRule;
            this.value = value;
            this.style = style;
        }
    }

    public ParsedRules parseRules(String aiText) {
        int formatRule = 2; // 默认 Greater Than
        String value = "";
        String style = "Bad";

        // 关键词优先检测
        String text = aiText.toLowerCase();
        if (text.contains("重复") || text.contains("duplicate")) {
            formatRule = 8;
            value = "";
        } else if (text.contains("唯一") || text.contains("不重复") || text.contains("unique")) {
            formatRule = 9;
            value = "";
        } else if (text.contains("包含") || text.contains("contain")) {
            formatRule = 23;
        }

        // 公式行解析
        String[] lines = aiText.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("公式：") || trimmed.startsWith("公式:")) {
                String formula = trimmed.substring(
                    trimmed.indexOf('：') > 0 ? trimmed.indexOf('：') + 1 :
                    (trimmed.indexOf(':') > 0 ? trimmed.indexOf(':') + 1 : 0)).trim();
                if (formatRule == 2 || formatRule == 1 || formatRule == 0) {
                    if (formula.contains(">")) {
                        formatRule = 2;
                        int idx = formula.indexOf('>');
                        if (idx + 1 < formula.length()) value = formula.substring(idx + 1).trim();
                    } else if (formula.contains("<")) {
                        formatRule = 1;
                        int idx = formula.indexOf('<');
                        if (idx + 1 < formula.length()) value = formula.substring(idx + 1).trim();
                    } else if (formula.contains("=")) {
                        formatRule = 0;
                        int idx = formula.indexOf('=') + 1;
                        String afterEq = formula.substring(idx).trim();
                        if (!afterEq.isEmpty()) value = afterEq;
                    }
                }
            } else if (trimmed.startsWith("格式：") || trimmed.startsWith("格式:")) {
                String formatDesc = trimmed.substring(
                    trimmed.indexOf('：') > 0 ? trimmed.indexOf('：') + 1 :
                    (trimmed.indexOf(':') > 0 ? trimmed.indexOf(':') + 1 : 0)).trim().toLowerCase();
                if (formatDesc.contains("红") || formatDesc.contains("错误") || formatDesc.contains("警告")) {
                    style = "Bad";
                } else if (formatDesc.contains("绿") || formatDesc.contains("成功")) {
                    style = "Good";
                } else if (formatDesc.contains("黄") || formatDesc.contains("中性")) {
                    style = "Neutral";
                }
            }
        }
        return new ParsedRules(formatRule, value, style);
    }

    // ========================================================================
    // JSON 解析（新流程）
    // ========================================================================

    /**
     * 从 AI 返回文本中解析 JSON 格式的条件格式计划。
     * 兼容带 markdown 代码块标记的文本、以及带前后解释文字的回复。
     */
    public static CondFormatPlan parseFromJson(String text) {
        if (text == null) return null;
        String json = text.trim();

        // 去掉 markdown 代码块标记 ```json ... ``` 或 ``` ... ```
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start > 0 && end > start) {
                json = json.substring(start, end).trim();
            }
        }

        // 如果整段不是纯 JSON，尝试从中提取 { ... } 对象
        if (!json.startsWith("{")) {
            int braceStart = json.indexOf('{');
            int braceEnd = json.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                String candidate = json.substring(braceStart, braceEnd + 1).trim();
                String candidateLog = candidate.length() > 120
                    ? candidate.substring(0, 120) + "..."
                    : candidate;
                Log.d(TAG, "parseFromJson_extracted_braces jsonPreview=" + candidateLog);
                // 先试提取出来的 JSON
                try {
                    JSONObject obj = new JSONObject(candidate);
                    if (obj.has("conditionType")) {
                        return buildPlan(obj);
                    }
                } catch (JSONException ignored) {}
                // 提取失败，继续原逻辑用清洗后的文本
                json = sanitizeJsonNulls(candidate);
            }
        }

        // 尝试修正常见的 JSON 格式问题后再解析
        String sanitized = sanitizeJsonNulls(json);
        if (!sanitized.equals(json)) {
            Log.d(TAG, "parseFromJson_sanitized originalChars=" + json.length()
                    + " sanitizedChars=" + sanitized.length());
        }

        try {
            JSONObject obj = new JSONObject(sanitized);
            if (obj.has("conditionType")) {
                return buildPlan(obj);
            }
        } catch (JSONException e) {
            Log.w(TAG, "parseFromJson_failed textPreview="
                    + (json.length() > 80 ? json.substring(0, 80) + "..." : json), e);
            return null;
        }

        // 最终 fallback：逐行解析（兼容极端格式问题）
        return parseFromTextLines(sanitized);
    }

    /**
     * 清洗 JSON：将 "key":, 或 "key":\s*\n," 等非法空值替换为 "key":null
     */
    private static String sanitizeJsonNulls(String json) {
        if (json == null || json.isEmpty()) return json;
        // 匹配 "key": 后紧跟逗号、换行+逗号、或直接到 } 的场景
        return json.replaceAll("\"\\s*:\\s*,", "\":null,")
                   .replaceAll("\"\\s*:\\s*\\}", "\":null}")
                   .replaceAll("\"\\s*:\\s*\n\\s*,", "\":null,");
    }

    /**
     * 最终 fallback：逐行提取 JSON key/value（用于极端场景，如 AI 返回多行 Key:Value 文本）
     */
    private static CondFormatPlan parseFromTextLines(String text) {
        if (text == null || text.isEmpty()) return null;
        String type = null, value = "", value2 = "", range = "", style = "Bad", desc = "";
        String formatJson = null;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("conditionType") || trimmed.startsWith("\"conditionType\"")) {
                type = extractLineValue(trimmed);
            } else if ((trimmed.startsWith("value") || trimmed.startsWith("\"value\""))
                    && !trimmed.contains("value2")) {
                value = extractLineValue(trimmed);
            } else if (trimmed.startsWith("value2") || trimmed.startsWith("\"value2\"")) {
                value2 = extractLineValue(trimmed);
            } else if (trimmed.startsWith("range") || trimmed.startsWith("\"range\"")) {
                range = extractLineValue(trimmed);
            } else if (trimmed.startsWith("style") || trimmed.startsWith("\"style\"")) {
                style = extractLineValue(trimmed);
            } else if (trimmed.startsWith("description") || trimmed.startsWith("\"description\"")) {
                desc = extractLineValue(trimmed);
            }
        }
        if (type != null) {
            if ("formula".equals(type)) {
                value = normalizeFormula(value);
            }
            Log.i(TAG, "parseFromTextLines type=" + type + " value=" + value + " range=" + range);
            return new CondFormatPlan(type, value, value2, range, style, formatJson, desc);
        }
        return null;
    }

    /** 逐行 fallback 取值：保留公式内逗号，仅去掉首尾引号。 */
    private static String extractLineValue(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) return "";
        String v = line.substring(colon + 1).trim();
        if (v.endsWith(",")) v = v.substring(0, v.length() - 1).trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.trim();
    }

    private static CondFormatPlan buildPlan(JSONObject obj) {
        if (obj == null) return null;
        if (!obj.has("conditionType")) {
            return null;
        }
        String type = obj.optString("conditionType", "");
        String value = obj.optString("value", "");
        if ("formula".equals(type)) {
            value = normalizeFormula(value);
        }
        String value2 = obj.optString("value2", "");
        String range = obj.optString("range", "");
        String style = obj.optString("style", "Bad");
        String desc = obj.optString("description", "");
        String formatJson = buildFormatJson(obj);
        return new CondFormatPlan(type, value, value2, range, style, formatJson, desc);
    }

    private static String buildFormatJson(JSONObject obj) {
        if (obj == null || !obj.has("format")) return null;
        JSONObject fmt = obj.optJSONObject("format");
        if (fmt == null) return null;
        try {
            JSONObject normalized = new JSONObject();
            String bg = normalizeHexColor(fmt.optString("backgroundColor", null));
            String fg = normalizeHexColor(fmt.optString("fontColor", null));
            if (bg != null) normalized.put("backgroundColor", bg);
            if (fg != null) normalized.put("fontColor", fg);
            if (fmt.has("fontBold") && !fmt.isNull("fontBold") && fmt.getBoolean("fontBold")) {
                normalized.put("fontBold", true);
            }
            if (fmt.has("fontItalic") && !fmt.isNull("fontItalic") && fmt.getBoolean("fontItalic")) {
                normalized.put("fontItalic", true);
            }
            putBorderFields(fmt, normalized);
            return normalized.length() > 0 ? normalized.toString() : null;
        } catch (JSONException e) {
            Log.w(TAG, "buildFormatJson_failed", e);
            return null;
        }
    }

    private static void putBorderFields(JSONObject fmt, JSONObject normalized) throws JSONException {
        if (fmt == null || normalized == null) return;
        String borderColor = null;
        String borderStyle = null;
        JSONObject border = fmt.optJSONObject("border");
        if (border != null) {
            borderColor = normalizeHexColor(border.optString("color", null));
            borderStyle = normalizeBorderStyle(border.optString("style", null));
        }
        if (borderColor == null) {
            borderColor = normalizeHexColor(fmt.optString("borderColor", null));
        }
        if (borderStyle == null) {
            borderStyle = normalizeBorderStyle(fmt.optString("borderStyle", null));
        }
        if (borderColor != null && borderStyle != null) {
            normalized.put("borderColor", borderColor);
            normalized.put("borderStyle", borderStyle);
        }
    }

    /** 规范化为 thin/medium/thick；none 或非法值返回 null。 */
    static String normalizeBorderStyle(String style) {
        if (style == null || style.trim().isEmpty()) {
            return "thin";
        }
        String s = style.trim().toLowerCase();
        if ("none".equals(s)) return null;
        if ("thin".equals(s) || "medium".equals(s) || "thick".equals(s)) return s;
        if (s.contains("细")) return "thin";
        if (s.contains("粗")) return "thick";
        if (s.contains("中")) return "medium";
        return "thin";
    }

    /** 规范化为 #RRGGBB；非法色值返回 null。 */
    static String normalizeHexColor(String color) {
        if (color == null) return null;
        String s = color.trim();
        if (s.isEmpty()) return null;
        if (!s.startsWith("#")) s = "#" + s;
        if (s.matches("#(?i)[0-9A-F]{3}")) {
            char r = s.charAt(1), g = s.charAt(2), b = s.charAt(3);
            return ("#" + r + r + g + g + b + b).toUpperCase();
        }
        if (s.matches("#(?i)[0-9A-F]{6}")) {
            return s.toUpperCase();
        }
        return null;
    }

    public static String buildFormatSummary(String formatJson) {
        if (formatJson == null || formatJson.isEmpty()) return "";
        try {
            JSONObject fmt = new JSONObject(formatJson);
            StringBuilder sb = new StringBuilder();
            String bg = fmt.optString("backgroundColor", "");
            String fg = fmt.optString("fontColor", "");
            if (!bg.isEmpty()) sb.append("背景 ").append(bg);
            if (!fg.isEmpty()) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append("字体 ").append(fg);
            }
            if (fmt.optBoolean("fontBold", false)) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append("加粗");
            }
            if (fmt.optBoolean("fontItalic", false)) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append("斜体");
            }
            String borderColor = fmt.optString("borderColor", "");
            String borderStyle = fmt.optString("borderStyle", "");
            if (!borderColor.isEmpty()) {
                if (sb.length() > 0) sb.append(" / ");
                sb.append("边框 ").append(borderColor);
                if (!borderStyle.isEmpty()) {
                    sb.append(" (").append(borderStyle).append(")");
                }
            }
            return sb.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    // ========================================================================
    // 条件类型 → FormatRule 映射
    // ========================================================================

    public static String normalizeFormula(String formula) {
        if (formula == null) return "";
        String s = formula.trim();
        if (s.isEmpty()) return "";
        if (!s.startsWith("=")) s = "=" + s;
        return s;
    }

    public static int getFormatRule(String conditionType) {
        if (conditionType == null) return -1;
        switch (conditionType) {
            case "greater":          return 2;  // ScConditionMode::Greater
            case "less":             return 1;  // ScConditionMode::Less
            case "equal":            return 0;  // ScConditionMode::Equal
            case "between":          return 6;  // ScConditionMode::Between
            case "top_n":            return 11; // ScConditionMode::Top10
            case "bottom_n":         return 12; // ScConditionMode::Bottom10
            case "above_average":    return 15; // ScConditionMode::AboveAverage
            case "below_average":    return 16; // ScConditionMode::BelowAverage
            case "duplicate":        return 8;  // ScConditionMode::Duplicate
            case "unique":           return 9;  // ScConditionMode::NotDuplicate
            case "contains_text":    return 23; // ScConditionMode::ContainsText
            case "formula":          return 10; // ScConditionMode::Direct (formula)
            default:
                Log.w(TAG, "unknown_condition_type type=" + conditionType);
                return -1;
        }
    }

    /** 应用前校验；返回 null 表示通过，否则为错误说明。 */
    public static String validatePlan(CondFormatPlan plan) {
        if (plan == null || !plan.isValid()) {
            return "无效的条件格式计划";
        }
        if (getFormatRule(plan.conditionType) < 0) {
            return "不支持的条件类型: " + plan.conditionType;
        }
        if (plan.range == null || plan.range.trim().isEmpty()) {
            return "应用范围不能为空";
        }
        if ("between".equals(plan.conditionType) && !hasMeaningfulValue(plan.value2)) {
            return "between 类型需要填写 value2";
        }
        if (!isValueLessCondition(plan.conditionType) && !hasMeaningfulValue(plan.value)) {
            return "条件值不能为空";
        }
        if ("formula".equals(plan.conditionType)) {
            String formula = normalizeFormula(plan.value);
            if (formula.isEmpty()) {
                return "公式条件不能为空";
            }
        }
        return null;
    }

    // ========================================================================
    // 新版直设（无 UI）：通过 .uno:ApplyConditionalFormat 直接应用
    // ========================================================================

    /**
     * 通过 postMobileUnoCommand 调用 .uno:ApplyConditionalFormat（走 COOLWSD 链路），
     * notify=true 时 Java 层拦截 unocommandresult 反馈结果。
     */
    public void applyDirect(CondFormatPlan plan, ApplyResultCallback callback) {
        if (plan == null || !plan.isValid()) {
            Log.e(TAG, "applyDirect_invalid_plan");
            if (callback != null) callback.onResult(false);
            return;
        }

        int formatRule = getFormatRule(plan.conditionType);
        if (formatRule < 0) {
            Log.e(TAG, "applyDirect_unknown_type type=" + plan.conditionType);
            if (callback != null) callback.onResult(false);
            return;
        }
        // 裸命令名 + typed JSON 参数（避免 URL 编码把 A1:B2 变成 A1%3AB2 导致 Core 解析失败）。
        String cmd = ".uno:ApplyConditionalFormat";
        String args = buildTypedArgs(plan, formatRule);
        Log.i(TAG, "applyDirect type=" + plan.conditionType
                + " formatRule=" + formatRule + " range=" + plan.range
                + " format=" + (plan.formatJson != null ? plan.formatJson : plan.style));
        Log.i(TAG, "applyDirect_args args=" + args);

        if (callback != null) {
            host.registerApplyResultCallback(callback);
        }
        host.postMobileUnoCommand(cmd, args);
    }

    /**
     * 构造带 URL 查询参数的 UNO 命令（与 executeUnoCommand 路径一致）。
     * 例：.uno:ApplyConditionalFormat?FormatRule:short=2&Expression1:string=20&Range:string=D2:D14&Style:string=Bad
     */
    private static boolean hasMeaningfulValue(String s) {
        if (s == null) return false;
        String t = s.trim();
        return !t.isEmpty() && !"null".equalsIgnoreCase(t);
    }

    private String buildCommandWithUrlParams(CondFormatPlan plan, int formatRule) {
        StringBuilder sb = new StringBuilder(".uno:ApplyConditionalFormat");
        sb.append("?FormatRule:short=").append(formatRule);
        if (hasMeaningfulValue(plan.value)) {
            sb.append("&Expression1:string=").append(urlEncodeParam(plan.value));
        }
        if (hasMeaningfulValue(plan.value2)) {
            sb.append("&Expression2:string=").append(urlEncodeParam(plan.value2));
        }
        if (plan.range != null && !plan.range.isEmpty()) {
            sb.append("&Range:string=").append(urlEncodeParam(plan.range));
        }
        if (plan.style != null && !plan.style.isEmpty()) {
            sb.append("&Style:string=").append(urlEncodeParam(plan.style));
        }
        return sb.toString();
    }

    /** URL 参数编码；单元格范围中的 ':' 必须保留，否则 Core 无法解析 A1:B2。 */
    private static String urlEncodeParam(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            return java.net.URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20")
                    .replace("%3A", ":")
                    .replace("%3a", ":");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    /**
     * 构造 typed UNO JSON 参数（备用；桌面 Web 路径仍可用）。
     */
    private String buildTypedArgs(CondFormatPlan plan, int formatRule) {
        String expr1 = hasMeaningfulValue(plan.value) ? escapeJsonString(plan.value) : "";
        String expr2 = hasMeaningfulValue(plan.value2) ? escapeJsonString(plan.value2) : "";
        StringBuilder sb = new StringBuilder();
        sb.append("{")
            .append("\"FormatRule\":{\"type\":\"short\",\"value\":").append(formatRule).append("},")
            .append("\"Expression1\":{\"type\":\"string\",\"value\":\"").append(expr1).append("\"},")
            .append("\"Expression2\":{\"type\":\"string\",\"value\":\"").append(expr2).append("\"},")
            .append("\"Range\":{\"type\":\"string\",\"value\":\"")
            .append(escapeJsonString(plan.range)).append("\"}");
        if (plan.hasCustomFormat()) {
            sb.append(",\"FormatSpec\":{\"type\":\"string\",\"value\":\"")
                .append(escapeJsonString(plan.formatJson)).append("\"}");
            sb.append(",\"Style\":{\"type\":\"string\",\"value\":\"\"}");
        } else {
            sb.append(",\"Style\":{\"type\":\"string\",\"value\":\"")
                .append(escapeJsonString(plan.style)).append("\"}");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // ========================================================================
    // 旧版执行（弹窗 + JS 注入，保留 fallback，已弃用）
    // ========================================================================

    /** @deprecated 使用 {@link #applyDirect(CondFormatPlan)} 替代。 */
    @Deprecated
    public void apply(CondFormatPlan plan) {
        if (plan == null || !plan.isValid()) {
            Log.e(TAG, "apply_invalid_plan");
            return;
        }

        int formatRule = getFormatRule(plan.conditionType);
        Log.i(TAG, "apply_start type=" + plan.conditionType + " formatRule=" + formatRule
                + " value=" + plan.value + " style=" + plan.style + " range=" + plan.range);

        // Step 0: 检查当前 mobileDialogId（JSDialog 是否可用）
        host.evaluateJavascript(
            "(function(){var w=window.mobileDialogId;return JSON.stringify({wId:w!==undefined&&w!==null&&w!==-1?w:-1,doc:!!document.querySelector('.jsdialog')});})()",
            value -> Log.i(TAG, "apply_precheck mobileDialogState=" + value));

        // Step 1: 打开条件格式快速对话框
        String cmd = ".uno:ConditionalFormatEasy?FormatRule:short=" + formatRule;
        Log.i(TAG, "apply_post_uno cmd=" + cmd);
        host.postUnoCommand(cmd, "{}", false);

        // Step 2: 等 400ms 让 dialog 出现，查询 dialog 状态后再注入
        host.runOnUiThread(() -> {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            // 查询 dialog 是否已创建
            host.evaluateJavascript(
                "(function(){var w=window.mobileDialogId;return JSON.stringify({wId:w!==undefined&&w!==null&&w!==-1?w:-1,doc:!!document.querySelector('.jsdialog')});})()",
                value -> Log.i(TAG, "apply_dialog_state_after_400ms value=" + value));

            String js = buildInjectScript(plan);
            Log.i(TAG, "apply_inject_js scriptChars=" + js.length()
                    + " injectValue=" + !isValueLessCondition(plan.conditionType)
                    + " injectRange=" + !plan.range.isEmpty()
                    + " injectStyle=" + !plan.style.isEmpty());
            host.evaluateJavascript(js);

            // 等 800ms 后读取执行状态
            host.runOnUiThread(() -> {
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                host.evaluateJavascript(
                    "JSON.stringify(window.__cfStatus||{phase:'not_set'})",
                    value -> Log.i(TAG, "apply_js_status value=" + value));
            });
        });
    }

    /** 旧版本 apply：接收 ParsedRules + range（保留 fallback） */
    public void apply(ParsedRules rules, String cellRange) {
        Log.i(TAG, "apply_legacy formatRule=" + rules.formatRule + " value=" + rules.value
                + " style=" + rules.style + " range=" + cellRange);

        String cmd = ".uno:ConditionalFormatEasy?FormatRule:short=" + rules.formatRule;
        host.postUnoCommand(cmd, "{}", false);

        host.runOnUiThread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            host.evaluateJavascript(buildLegacyInjectScript(rules.value, rules.style, cellRange));
        });
    }

    // ========================================================================
    // JS 注入脚本（新）
    // ========================================================================

    private String buildInjectScript(CondFormatPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){\n");
        // 初始化状态追踪对象（Java 侧可通过 evaluateJavascript 读取）
        sb.append("window.__cfStatus={phase:'start',found:false,steps:[],wId:-1,attempts:0};\n");
        sb.append("function _log(step,ok,detail){window.__cfStatus.steps.push({s:step,ok:!!ok,d:detail||''});}\n");
        sb.append("var _s=window.__cfStatus;\n");
        sb.append("var attempts=0;\n");
        sb.append("var checkExist = setInterval(function(){\n");
        sb.append("attempts++;\n");
        sb.append("_s.attempts=attempts;\n");
        sb.append("var wId = window.mobileDialogId;\n");
        sb.append("if(wId === undefined || wId === null || wId === -1){\n");
        sb.append("try{var dlg=document.querySelector('.jsdialog');if(dlg){wId=parseInt(dlg.id)||-1;}}catch(e){}\n");
        sb.append("}\n");
        sb.append("if(wId !== undefined && wId !== null && wId !== -1){\n");
        sb.append("clearInterval(checkExist);\n");
        sb.append("_s.phase='found';_s.found=true;_s.wId=wId;\n");
        sb.append("_log('dialog_found',true,'wId='+wId+' attempts='+attempts);\n");

        // 根据条件类型决定注入哪些字段
        boolean needsValue = !isValueLessCondition(plan.conditionType);
        boolean needsValue2 = "between".equals(plan.conditionType);

        if (needsValue && plan.value != null && !plan.value.isEmpty()) {
            sb.append("try{app.socket.sendMessage('dialogevent '+wId+' {\\\"id\\\":\\\"entryNumber\\\",\\\"cmd\\\":\\\"modify\\\",\\\"data\\\":\\\"")
              .append(escapeJsString(plan.value))
              .append("\\\",\\\"type\\\":\\\"entry\\\"}');_log('entryNumber',true,'value=")
              .append(escapeJsString(plan.value)).append("');}catch(e){_log('entryNumber',false,e+'');}\n");
        } else {
            sb.append("_log('entryNumber','skip','no_value_needed');\n");
        }
        if (needsValue2 && plan.value2 != null && !plan.value2.isEmpty()) {
            sb.append("try{app.socket.sendMessage('dialogevent '+wId+' {\\\"id\\\":\\\"entryNumber2\\\",\\\"cmd\\\":\\\"modify\\\",\\\"data\\\":\\\"")
              .append(escapeJsString(plan.value2))
              .append("\\\",\\\"type\\\":\\\"entry\\\"}');_log('entryNumber2',true,'value2=")
              .append(escapeJsString(plan.value2)).append("');}catch(e){_log('entryNumber2',false,e+'');}\n");
        }
        if (plan.range != null && !plan.range.isEmpty()) {
            sb.append("try{app.socket.sendMessage('dialogevent '+wId+' {\\\"id\\\":\\\"entryRange\\\",\\\"cmd\\\":\\\"modify\\\",\\\"data\\\":\\\"")
              .append(escapeJsString(plan.range))
              .append("\\\",\\\"type\\\":\\\"entry\\\"}');_log('entryRange',true,'range=")
              .append(escapeJsString(plan.range)).append("');}catch(e){_log('entryRange',false,e+'');}\n");
        }
        if (plan.style != null && !plan.style.isEmpty()) {
            sb.append("try{app.socket.sendMessage('dialogevent '+wId+' {\\\"id\\\":\\\"themeCombo\\\",\\\"cmd\\\":\\\"select\\\",\\\"data\\\":\\\"")
              .append(escapeJsString(plan.style))
              .append("\\\",\\\"type\\\":\\\"list\\\"}');_log('themeCombo',true,'style=")
              .append(escapeJsString(plan.style)).append("');}catch(e){_log('themeCombo',false,e+'');}\n");
        }
        // 点 OK — 给注入后 300ms 让 core 处理完字段修改
        sb.append("_s.phase='injecting';\n");
        sb.append("setTimeout(function(){\n");
        sb.append("_s.phase='clicking_ok';\n");
        sb.append("try{app.socket.sendMessage('dialogevent '+wId+' {\\\"id\\\":\\\"ok\\\",\\\"cmd\\\":\\\"click\\\",\\\"data\\\":\\\"0\\\",\\\"type\\\":\\\"pushbutton\\\"}');_log('ok_click',true,'');_s.phase='done';}catch(e){_log('ok_click',false,e+'');_s.phase='error';}\n");
        sb.append("},300);\n");
        sb.append("}\n");
        sb.append("},150);\n");
        sb.append("setTimeout(function(){\n");
        sb.append("clearInterval(checkExist);\n");
        sb.append("if(!_s.found){_s.phase='timeout';_log('dialog_found',false,'timeout after '+attempts+' attempts');}\n");
        sb.append("},5000);\n");
        sb.append("})();");
        return sb.toString();
    }

    /** 旧版 JS 注入脚本（保留 fallback） */
    private String buildLegacyInjectScript(String value, String style, String range) {
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){\n");
        sb.append("var checkExist = setInterval(function(){\n");
        sb.append("var wId = window.mobileDialogId;\n");
        sb.append("if(wId === undefined || wId === null || wId === -1){\n");
        sb.append("try{var dlg=document.querySelector('.jsdialog');if(dlg){wId=parseInt(dlg.id)||-1;}}catch(e){}\n");
        sb.append("}\n");
        sb.append("if(wId !== undefined && wId !== null && wId !== -1){\n");
        sb.append("clearInterval(checkExist);\n");
        sb.append("console.log('cond_format_js found dialog wId='+wId);\n");
        if (value != null && !value.isEmpty()) {
            sb.append("try{app.socket.sendMessage('dialogevent '+wId+' {\\\"id\\\":\\\"entryNumber\\\",\\\"cmd\\\":\\\"modify\\\",\\\"data\\\":\\\"")
              .append(escapeJsString(value))
              .append("\\\",\\\"type\\\":\\\"entry\\\"}');}catch(e){console.log('cond_format_js entry_err='+e);}\n");
        }
        if (range != null && !range.isEmpty()) {
            sb.append("try{app.socket.sendMessage('dialogevent '+wId+' {\\\"id\\\":\\\"entryRange\\\",\\\"cmd\\\":\\\"modify\\\",\\\"data\\\":\\\"")
              .append(escapeJsString(range))
              .append("\\\",\\\"type\\\":\\\"entry\\\"}');}catch(e){console.log('cond_format_js range_err='+e);}\n");
        }
        if (style != null && !style.isEmpty()) {
            sb.append("try{app.socket.sendMessage('dialogevent '+wId+' {\\\"id\\\":\\\"themeCombo\\\",\\\"cmd\\\":\\\"select\\\",\\\"data\\\":\\\"")
              .append(escapeJsString(style))
              .append("\\\",\\\"type\\\":\\\"list\\\"}');}catch(e){console.log('cond_format_js style_err='+e);}\n");
        }
        sb.append("setTimeout(function(){\n");
        sb.append("console.log('cond_format_js clicking ok');\n");
        sb.append("try{app.socket.sendMessage('dialogevent '+wId+' {\\\"id\\\":\\\"ok\\\",\\\"cmd\\\":\\\"click\\\",\\\"data\\\":\\\"0\\\",\\\"type\\\":\\\"pushbutton\\\"}');}catch(e){console.log('cond_format_js ok_err='+e);}\n");
        sb.append("},300);\n");
        sb.append("}\n");
        sb.append("},200);\n");
        sb.append("setTimeout(function(){clearInterval(checkExist); console.log('cond_format_js timeout');},10000);\n");
        sb.append("})();");
        return sb.toString();
    }

    private String escapeJsString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /** 不需要 entryNumber 的条件类型（无值参数） */
    private static boolean isValueLessCondition(String type) {
        return "above_average".equals(type)
            || "below_average".equals(type)
            || "duplicate".equals(type)
            || "unique".equals(type);
    }
}
