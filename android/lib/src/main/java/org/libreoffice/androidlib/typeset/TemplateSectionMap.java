package org.libreoffice.androidlib.typeset;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps docx template placeholder text to AI section keys for each template type.
 *
 * <p>Each template has structural headings (kept as-is) and content placeholder
 * paragraphs (replaced with AI-generated content). The placeholder text is used
 * as the key to identify which paragraphs to fill.</p>
 */
public class TemplateSectionMap {

    /** Template type constants — match the existing typesetType values. */
    public static final String TYPE_PAPER = "paper";
    public static final String TYPE_GOV = "gov";
    public static final String TYPE_CONTRACT = "contract";
    public static final String TYPE_GENERAL = "general";

    // ---- Placeholder text -> section key mappings ----

    /**
     * Paper (论文) template sections.
     * Structural headings "摘要", "引言", "结语", "致谢" are NOT in this map —
     * they are preserved as-is. Only content placeholders are listed.
     */
    private static final LinkedHashMap<String, String> PAPER_SECTIONS = new LinkedHashMap<>();
    static {
        PAPER_SECTIONS.put("论文标题", "title");
        PAPER_SECTIONS.put("摘要正文", "abstract");
        PAPER_SECTIONS.put("关键词", "keywords");
        PAPER_SECTIONS.put("引言正文", "introduction");
        PAPER_SECTIONS.put("一级标题", "heading1");
        PAPER_SECTIONS.put("二级标题", "heading2");
        PAPER_SECTIONS.put("三级标题", "heading3");
        PAPER_SECTIONS.put("正文", "body");
        PAPER_SECTIONS.put("结语正文", "conclusion_body");
        PAPER_SECTIONS.put("致谢内容", "ack_body");
    }

    /**
     * Government document (公文) template sections.
     * The template uses a TABLE for the red-header area (issuing authority, document number,
     * title line) — table cell text is matched just like paragraph text.
     * Body paragraphs use "×" repetition as placeholders.
     */
    private static final LinkedHashMap<String, String> GOV_SECTIONS = new LinkedHashMap<>();
    static {
        GOV_SECTIONS.put("主送机关：", "recipient");
        // The ××× paragraphs — match by prefix since the text is very long
        GOV_SECTIONS.put("××", "body");
        GOV_SECTIONS.put("发文机关署名（比日期长）", "signature_org");
        GOV_SECTIONS.put("20××年×月×日", "signature_date");
        GOV_SECTIONS.put("（附注内容）", "notes");
    }

    /**
     * Contract (合同协议) template sections.
     */
    private static final LinkedHashMap<String, String> CONTRACT_SECTIONS = new LinkedHashMap<>();
    static {
        CONTRACT_SECTIONS.put("合同协议", "title");
        // Contract number: matches prefix "合同编号："
        CONTRACT_SECTIONS.put("合同编号：", "contract_number");
        CONTRACT_SECTIONS.put("甲方：", "party_a");
        // First "身份证号码：" maps to party_a_id; second one is handled specially
        CONTRACT_SECTIONS.put("身份证号码：", "party_a_id");
        CONTRACT_SECTIONS.put("乙方：", "party_b");
        // Preamble paragraph (starts with "按照平等互利")
        CONTRACT_SECTIONS.put("按照平等互利", "preamble");
        CONTRACT_SECTIONS.put("第一条 一级标题", "clause_title");
        CONTRACT_SECTIONS.put("1.1 二级标题", "clause_subtitle");
        CONTRACT_SECTIONS.put("正文", "clause_body");
    }

    /**
     * General (通用) template sections.
     */
    private static final LinkedHashMap<String, String> GENERAL_SECTIONS = new LinkedHashMap<>();
    static {
        GENERAL_SECTIONS.put("文档标题", "title");
        GENERAL_SECTIONS.put("一级标题", "heading1");
        GENERAL_SECTIONS.put("二级标题", "heading2");
        GENERAL_SECTIONS.put("三级标题", "heading3");
        // The body paragraph has repeated text, match by prefix
        GENERAL_SECTIONS.put("正文段落", "body");
    }

    // ---- Lookup ----

    /**
     * Get the section map for a given template type.
     *
     * @param typesetType one of TYPE_PAPER, TYPE_GOV, TYPE_CONTRACT, TYPE_GENERAL
     * @return ordered map of placeholder text → section key, or null if unknown type
     */
    public static LinkedHashMap<String, String> getSectionMap(String typesetType) {
        switch (typesetType) {
            case TYPE_PAPER:    return new LinkedHashMap<>(PAPER_SECTIONS);
            case TYPE_GOV:      return new LinkedHashMap<>(GOV_SECTIONS);
            case TYPE_CONTRACT: return new LinkedHashMap<>(CONTRACT_SECTIONS);
            case TYPE_GENERAL:  return new LinkedHashMap<>(GENERAL_SECTIONS);
            default:            return null;
        }
    }

    /**
     * Try to match a paragraph's text against the placeholder entries.
     * Returns the section key if matched, null otherwise.
     *
     * <p>Matching rules (in order):
     * <ol>
     *   <li>Exact match on the placeholder text</li>
     *   <li>Starts-with match (for long placeholder texts like "×××...")</li>
     * </ol>
     * </p>
     */
    public static String matchPlaceholder(String paragraphText, LinkedHashMap<String, String> sectionMap) {
        if (paragraphText == null || paragraphText.isEmpty()) return null;
        String trimmed = paragraphText.trim();
        if (trimmed.isEmpty()) return null;

        for (Map.Entry<String, String> entry : sectionMap.entrySet()) {
            String placeholder = entry.getKey();
            if (trimmed.equals(placeholder)) {
                return entry.getValue();
            }
            // Prefix match for placeholder texts (2+ chars to avoid false matches
            // on single-character placeholders; "××" in gov template is a valid 2-char prefix)
            if (placeholder.length() >= 2 && trimmed.startsWith(placeholder)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Check if a section key has been used already in this filling pass.
     * Some placeholders (like "身份证号码：" in contract template) appear twice;
     * the second occurrence should map to a different key.
     *
     * @param sectionKey the key matched by {@link #matchPlaceholder}
     * @param usedKeys   set of already-used keys
     * @return the actual section key to use (may differ for duplicate placeholders)
     */
    public static String resolveDuplicateKey(String sectionKey, java.util.Set<String> usedKeys) {
        if (!usedKeys.contains(sectionKey)) {
            return sectionKey;
        }
        // Handle known duplicates
        if ("party_a_id".equals(sectionKey)) {
            return "party_b_id";
        }
        // Generic: append _2, _3, etc.
        int suffix = 2;
        while (usedKeys.contains(sectionKey + "_" + suffix)) {
            suffix++;
        }
        return sectionKey + "_" + suffix;
    }

    /**
     * Get the R.raw resource ID for a template type.
     */
    public static int getTemplateResId(String typesetType) {
        // These resource IDs are resolved at runtime by the caller using Context.getResources()
        // We return a lookup key; the actual resolution happens in DocxTemplateFiller
        switch (typesetType) {
            case TYPE_PAPER:    return 0; // caller resolves to R.raw.typeset_template_paper
            case TYPE_GOV:      return 1;
            case TYPE_CONTRACT: return 2;
            case TYPE_GENERAL:  return 3;
            default:            return -1;
        }
    }

    /**
     * Get ordered list of section keys for a given template type.
     * Used by AI prompt generation to tell the AI what sections to fill.
     */
    public static String[] getSectionKeys(String typesetType) {
        LinkedHashMap<String, String> map = getSectionMap(typesetType);
        if (map == null) return new String[0];
        // Return unique keys in order
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        for (String key : map.values()) {
            keys.add(key);
        }
        return keys.toArray(new String[0]);
    }
}
