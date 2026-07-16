package org.libreoffice.androidlib.ai;

import android.graphics.Color;
import android.widget.EditText;

/**
 * AI 功能弹窗输入框：灰色 hint 占位 + 空输入时使用默认值发起请求。
 */
public final class AiDialogInputHelper {

    private static final int HINT_COLOR = Color.parseColor("#9AA0A6");

    public static final String DEFAULT_CALC_FORMULA =
            "根据当前选中单元格和数据自动生成合适的公式";
    public static final String DEFAULT_COND_FORMAT =
            "高亮选中区域中的最大值和最小值";
    public static final String DEFAULT_DATA_PROCESS =
            "清洗选中数据：统一数字格式并去除多余空格";
    public static final String DEFAULT_CHART =
            "根据选中数据生成合适的柱状图";
    public static final String DEFAULT_OUTLINE_DESC =
            "根据文档内容生成结构清晰的大纲";
    public static final String DEFAULT_EXPAND_REQUIREMENT =
            "在保持原意前提下适当扩展内容、补充细节";
    public static final String DEFAULT_CONDENSE_REQUIREMENT =
            "压缩至原文一半左右，保留核心信息";
    public static final String DEFAULT_REWRITE_REQUIREMENT =
            "换一种表述重写，保持原意不变";
    public static final String DEFAULT_AI_IMAGE_PROMPT =
            "一张简洁专业的商务插图";

    private AiDialogInputHelper() {
    }

    /** 保留布局 hint，仅绑定默认值与 hint 颜色。 */
    public static void bindDefault(EditText edit, String defaultValue) {
        bind(edit, null, defaultValue);
    }

    public static void bind(EditText edit, String hint, String defaultValue) {
        if (edit == null) {
            return;
        }
        if (hint != null && !hint.isEmpty()) {
            edit.setHint(hint);
        }
        edit.setHintTextColor(HINT_COLOR);
        edit.setTag(defaultValue != null ? defaultValue : "");
    }

    public static String resolve(EditText edit) {
        if (edit == null) {
            return "";
        }
        String text = edit.getText().toString().trim();
        if (!text.isEmpty()) {
            return text;
        }
        Object tag = edit.getTag();
        if (tag instanceof String) {
            String def = ((String) tag).trim();
            if (!def.isEmpty()) {
                return def;
            }
        }
        CharSequence layoutHint = edit.getHint();
        if (layoutHint != null) {
            String hintStr = layoutHint.toString().trim();
            if (!hintStr.isEmpty()) {
                return hintStr;
            }
        }
        return "";
    }

    /** 空输入时用默认值填充输入框并返回最终文案。 */
    public static String resolveAndApply(EditText edit) {
        String resolved = resolve(edit);
        if (edit != null && edit.getText().toString().trim().isEmpty() && !resolved.isEmpty()) {
            edit.setText(resolved);
            edit.setSelection(resolved.length());
        }
        return resolved;
    }
}
