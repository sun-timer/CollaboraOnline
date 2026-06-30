package org.libreoffice.androidlib.ai;

import java.util.regex.Pattern;

/**
 * 格式批量处理：本地正则/字符串变换，6 个可独立开关的规则。
 * options 顺序对应 {@link #RULE_EN_TO_ZH_PUNCT} ~ {@link #RULE_REMOVE_HYPERLINK}。
 */
public final class FormatBatchProcessor {

    public static final int RULE_EN_TO_ZH_PUNCT = 0;       // 英文标点转中文
    public static final int RULE_ZH_TO_EN_PUNCT = 1;       // 中文标点转英文
    public static final int RULE_GHOST_TO_SPACE = 2;       // 幽灵字符转空格
    public static final int RULE_REMOVE_EXTRA_BLANK_LINES = 3; // 删除多余空行
    public static final int RULE_REMOVE_WAVY_UNDERLINE = 4;    // 消除下滑波浪线
    public static final int RULE_REMOVE_HYPERLINK = 5;         // 消除超链接识别

    public static final int RULE_COUNT = 6;

    private FormatBatchProcessor() {
    }

    /** 对输入文本按 options 应用规则，返回处理后文本。options 长度须 >= RULE_COUNT。 */
    public static String process(String input, boolean[] options) {
        if (input == null) {
            return "";
        }
        if (options == null) {
            options = new boolean[RULE_COUNT];
        }
        String text = input;
        if (options.length > RULE_EN_TO_ZH_PUNCT && options[RULE_EN_TO_ZH_PUNCT]) {
            text = enPunctToZh(text);
        }
        if (options.length > RULE_ZH_TO_EN_PUNCT && options[RULE_ZH_TO_EN_PUNCT]) {
            text = zhPunctToEn(text);
        }
        if (options.length > RULE_GHOST_TO_SPACE && options[RULE_GHOST_TO_SPACE]) {
            text = ghostCharsToSpace(text);
        }
        if (options.length > RULE_REMOVE_EXTRA_BLANK_LINES && options[RULE_REMOVE_EXTRA_BLANK_LINES]) {
            text = removeExtraBlankLines(text);
        }
        if (options.length > RULE_REMOVE_WAVY_UNDERLINE && options[RULE_REMOVE_WAVY_UNDERLINE]) {
            text = removeWavyUnderlineArtifacts(text);
        }
        if (options.length > RULE_REMOVE_HYPERLINK && options[RULE_REMOVE_HYPERLINK]) {
            text = removeHyperlinkMarkers(text);
        }
        return text;
    }

    /** 英文标点转中文（仅转换常见成对标点与句末标点）。 */
    private static String enPunctToZh(String text) {
        return text
                .replace(",", "，")
                .replace(".", "。")
                .replace("!", "！")
                .replace("?", "？")
                .replace(";", "；")
                .replace(":", "：")
                .replace("(", "（")
                .replace(")", "）");
    }

    /** 中文标点转英文。 */
    private static String zhPunctToEn(String text) {
        return text
                .replace("，", ",")
                .replace("。", ".")
                .replace("！", "!")
                .replace("？", "?")
                .replace("；", ";")
                .replace("：", ":")
                .replace("（", "(")
                .replace("）", ")");
    }

    /** 幽灵字符（零宽字符/BOM/软连字符）转空格，再合并连续空格。 */
    private static String ghostCharsToSpace(String text) {
        String replaced = text
                .replace("\u200B", " ") // zero width space
                .replace("\u200C", " ") // zero width non-joiner
                .replace("\u200D", " ") // zero width joiner
                .replace("\uFEFF", " ") // BOM
                .replace("\u00AD", " "); // soft hyphen
        // 合并连续空格为单个（不影响换行）
        return replaced.replaceAll("[ \\t]{2,}", " ");
    }

    /** 连续 3+ 换行压缩为单个空行（\n\n），首尾去除空白行。 */
    private static String removeExtraBlankLines(String text) {
        String collapsed = text.replaceAll("(\\r\\n|\\r)", "\n").replaceAll("\\n{3,}", "\n\n");
        return collapsed.trim();
    }

    /** 消除下滑波浪线相关伪字符：软连字符、波浪号伪字符、下划线类装饰符。 */
    private static String removeWavyUnderlineArtifacts(String text) {
        return text
                .replace("\u00AD", "")   // soft hyphen
                .replace("\u2307", "")   // wavy overline
                .replace("\uFE26", "")   // combining wavy overline
                .replace("\uFE4F", "")   // wavy low line (dashed underline)
                .replace("\u0330", "")   // combining tilde below (wavy underline)
                .replace("\u0334", "")   // combining tilde overlay
                .replace("\u223C", "")   // tilde operator
                .replace("\uFF5E", "")   // fullwidth tilde
                .replace("\u2305", "");  // wavy underline
    }

    /** 消除超链接识别：markdown 链接 [text](url)→text、文本(url)→文本、裸 URL 行内标记。 */
    private static String removeHyperlinkMarkers(String text) {
        String result = text;
        // [text](url) → text
        result = Pattern.compile("\\[([^\\]]+?)\\]\\((https?://[^\\s)]+)\\)").matcher(result).replaceAll("$1");
        // 文本(https://...) → 文本
        result = Pattern.compile("([^\\s\\(]*)\\((https?://[^\\s)]+)\\)").matcher(result).replaceAll("$1");
        // 裸 URL 行内标记：把 URL 替换为空（保留行内其余文本）
        result = Pattern.compile("https?://[^\\s））]+").matcher(result).replaceAll("");
        return result;
    }
}
