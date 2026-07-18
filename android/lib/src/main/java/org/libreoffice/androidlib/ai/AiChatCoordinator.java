package org.libreoffice.androidlib.ai;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public class AiChatCoordinator {
    private static final String TAG = "AiChatCoordinator";

    public static final String MODE_DOC_QA = "doc_qa";
    public static final String MODE_CHAT = "chat";
    public static final String MODE_CONTINUE = "continue_write";
    public static final String MODE_EXPAND = "expand";
    public static final String MODE_POLISH = "polish";
    public static final String MODE_SUMMARIZE = "summarize";
    public static final String MODE_CONDENSE = "condense";
    public static final String MODE_REWRITE = "rewrite";
    public static final String MODE_TRANSLATE = "translate";
    public static final String MODE_TYPESET = "typeset";
    public static final String MODE_OUTLINE = "outline";
    public static final String MODE_ARTICLE_GENERATE = "article_generate";
    public static final String MODE_TEXT_EXTRACT = "text_extract";
    public static final String MODE_FORMAT_BATCH = "format_batch";
    public static final String MODE_IMAGE_GENERATE = "image_generate";
    public static final String MODE_CALC_FORMULA = "calc_formula";
    public static final String MODE_CALC_COND_FORMAT = "calc_cond_format";
    public static final String MODE_CALC_NEW_TABLE = "calc_new_table";
    public static final String MODE_CALC_DATA_PROCESS = "calc_data_process";
    public static final String MODE_CALC_DATA_ANALYSIS = "calc_data_analysis";
    public static final String MODE_CALC_CHART = "calc_chart";
    public static final String MODE_IMPRESS_OUTLINE = "impress_outline";
    public static final String MODE_IMPRESS_GENERATE = "impress_generate";

    // 润色风格
    public static final String POLISH_STYLE_QUICK = "quick";
    public static final String POLISH_STYLE_FORMAL = "formal";
    public static final String POLISH_STYLE_LIVELY = "lively";
    public static final String POLISH_STYLE_PARTY_GOVT = "party_govt";
    public static final String POLISH_STYLE_COLLOQUIAL = "colloquial";
    public static final String POLISH_STYLE_ACADEMIC = "academic";
    public static final String POLISH_STYLE_INTERNET = "internet";

    // 翻译语言
    public static final String TRANSLATE_LANG_AUTO = "auto";
    public static final String TRANSLATE_LANG_ZH = "zh";
    public static final String TRANSLATE_LANG_EN = "en";
    public static final String TRANSLATE_LANG_JA = "ja";
    public static final String TRANSLATE_LANG_KO = "ko";
    public static final String TRANSLATE_LANG_FR = "fr";
    public static final String TRANSLATE_LANG_DE = "de";
    public static final String TRANSLATE_LANG_ES = "es";
    public static final String TRANSLATE_LANG_RU = "ru";

    // 大纲类型（生成大纲功能）
    public static final String OUTLINE_TYPE_PAPER = "paper";     // 论文
    public static final String OUTLINE_TYPE_REPORT = "report";   // 工作报告
    public static final String OUTLINE_TYPE_SPEECH = "speech";   // 演讲稿
    public static final String OUTLINE_TYPE_EVENT = "event";     // 活动策划
    public static final String OUTLINE_TYPE_GENERAL = "general"; // 通用文档

    private final AiConversationStore conversationStore;
    private JSONArray docQaHistory = new JSONArray();
    private JSONArray chatHistory = new JSONArray();
    private boolean docQaContextInjected = false;

    public AiChatCoordinator(Context context, URI documentUri, String urlToLoad, long loadDocumentMillis) {
        conversationStore = new AiConversationStore(context, documentUri, urlToLoad, loadDocumentMillis);
    }

    public void load() {
        docQaHistory = conversationStore.loadHistory(MODE_DOC_QA);
        chatHistory = conversationStore.loadHistory(MODE_CHAT);
        docQaContextInjected = hasAssistantHistory(docQaHistory);
    }

    public JSONArray getHistory(String mode) {
        return MODE_DOC_QA.equals(mode) ? docQaHistory : chatHistory;
    }

    public boolean isFirstDocQaTurn(String mode) {
        return MODE_DOC_QA.equals(mode) && !docQaContextInjected;
    }

    public void markDocQaContextInjected() {
        docQaContextInjected = true;
    }

    public void appendHistoryMessage(String mode, String role, String content) throws JSONException {
        String normalized = normalize(content);
        if (normalized.isEmpty()) {
            return;
        }
        conversationStore.appendHistoryMessage(mode, getHistory(mode), role, normalized);
    }

    public JSONArray cloneHistory(String mode) {
        JSONArray source = getHistory(mode);
        try {
            return new JSONArray(source.toString());
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public void clearHistoriesForCurrentDocument() {
        conversationStore.clearHistoriesForCurrentDocument();
    }

    public void reset(boolean clearHistoryFiles) {
        docQaContextInjected = false;
        if (clearHistoryFiles) {
            clearHistoriesForCurrentDocument();
        }
        docQaHistory = new JSONArray();
        chatHistory = new JSONArray();
    }

    private boolean hasAssistantHistory(JSONArray history) {
        if (history == null) {
            return false;
        }
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if ("assistant".equals(item.optString("role", ""))
                    && !normalize(item.optString("content", "")).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOperateMode(String mode) {
        if (mode == null) {
            return false;
        }
        switch (mode) {
            case MODE_CONTINUE:
            case MODE_SUMMARIZE:
                return true;
            default:
                return false;
        }
    }

    public static JSONArray buildOperateMessages(String mode, String selection) throws JSONException {
        String systemPrompt;
        String userPrompt;
        String text = selection == null ? "" : selection.trim();

        switch (mode) {
            case MODE_CONTINUE:
                systemPrompt = "You are a creative Chinese writer. Continue naturally in the same style and tone. Return only the continuation.";
                userPrompt = "请自然流畅地接续以下文本，保持一致的风格和语气：\n\n---\n" + text + "\n---";
                break;
            case MODE_EXPAND:
                systemPrompt = "You are a detailed Chinese writer. Expand text with rich detail, examples, and arguments.";
                userPrompt = "请将以下内容扩展得更详细丰富，增加细节、例证和论述：\n\n---\n" + text + "\n---";
                break;
            case MODE_POLISH:
                systemPrompt = "You are a professional Chinese editor. Fix grammar, improve fluency and clarity. Return only the polished full text.";
                userPrompt = "请优化以下文本的表达，修正语法错误，提升流畅度和专业性。直接返回润色后的全文：\n\n---\n" + text + "\n---";
                break;
            case MODE_SUMMARIZE:
                systemPrompt = "You are a concise summarizer. Extract key points precisely. Return only the summary.";
                userPrompt = "请用简洁的语言概括以下内容的核心要点：\n\n---\n" + text + "\n---";
                break;
            case MODE_CONDENSE:
                systemPrompt = "You are a text condenser. Reduce length while preserving key meaning.";
                userPrompt = "请压缩以下文本，保留关键信息，去除冗余，缩减至原长度的一半左右：\n\n---\n" + text + "\n---";
                break;
            case MODE_REWRITE:
                systemPrompt = "You are a versatile Chinese writer. Rewrite in a fresh way while preserving original meaning.";
                userPrompt = "请用不同的表达方式和句式重写以下内容，保持原意不变：\n\n---\n" + text + "\n---";
                break;
            case MODE_TRANSLATE:
                systemPrompt = "You are a professional Chinese-English translator. Translate naturally and accurately. Return only the translation.";
                userPrompt = "请将以下中文翻译成自然流畅的英文：\n\n---\n" + text + "\n---";
                break;
            default:
                throw new JSONException("Unknown operate mode: " + mode);
        }

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.put(userMsg);

        return messages;
    }

    /**
     * 构建AI排版消息
     * @param typesetType 排版类型：paper(论文) | gov(党政公文) | contract(合同协议) | general(通用文档)
     * @param fullText 文档全文
     */
    public static JSONArray buildTypesetMessages(String typesetType, String fullText) throws JSONException {
        String systemPrompt;
        String userPrompt;
        String text = fullText == null ? "" : fullText.trim();

        switch (typesetType) {
            case "paper":
                systemPrompt = "你是学术论文排版专家。你的任务是将用户提供的论文全文内容按照标准学术论文格式进行排版，并返回完整的 HTML 格式结果。\n\n"
                        + "排版规范：\n"
                        + "1. 标题层级：使用 <h1> 作为论文标题，<h2> 作为章节标题，<h3> 作为小节标题\n"
                        + "2. 摘要：用 <p><strong>摘要：</strong> 包裹摘要内容\n"
                        + "3. 关键词：用 <p><strong>关键词：</strong> 列出关键词，用顿号分隔\n"
                        + "4. 正文：用 <p> 包裹段落，段首不缩进\n"
                        + "5. 图表：用 <table> 制作表格，<caption> 作为表格标题\n"
                        + "6. 参考文献：用 <ol> 编号列表，每个文献用 <li> 包裹\n"
                        + "7. 公式：简单公式用 <sub>/<sup>，复杂公式用文本描述\n\n"
                        + "请只返回排版后的 HTML，不要包含任何其他说明文字或代码块标记。不要使用 CSS 样式，只用 HTML 语义化标签。";
                userPrompt = "请将以下论文内容按照标准学术论文格式排版，返回完整的 HTML：\n\n---\n" + text + "\n---\n\n请直接返回排版后的 HTML，不要包含任何其他说明文字。";
                break;
            case "gov":
                systemPrompt = "你是党政公文排版专家。你的任务是将用户提供的公文内容按照标准党政公文格式（GB/T 9704-2012）进行排版，并返回完整的 HTML 格式结果。\n\n"
                        + "排版规范：\n"
                        + "1. 发文机关标志：用 <div align=\"center\"><h1> 发文机关名称 </h1></div>\n"
                        + "2. 发文字号：用 <div align=\"center\"><p> ××发〔2026〕×号 </p></div>\n"
                        + "3. 标题：用 <div align=\"center\"><h2> 公文标题 </h2></div>\n"
                        + "4. 主送机关：用 <p><strong>×××：</strong></p>，顶格\n"
                        + "5. 正文：用 <p> 包裹段落，首行不缩进\n"
                        + "6. 附件说明：用 <p> 附件：1.××× </p>\n"
                        + "7. 发文机关署名：用 <div align=\"right\"><p> ×××局 </p></div>\n"
                        + "8. 成文日期：用 <div align=\"right\"><p> 2026年6月18日 </p></div>\n"
                        + "9. 版记：用分隔线 <hr>，抄送用 <p>\n\n"
                        + "请只返回排版后的 HTML，不要使用 CSS，只用 HTML 属性（align, font size）和语义化标签。";
                userPrompt = "请按照标准党政公文格式排版以下内容，返回完整的 HTML：\n\n---\n" + text + "\n---\n\n请直接返回排版后的 HTML。";
                break;
            case "contract":
                systemPrompt = "你是合同协议排版专家。你的任务是将用户提供的合同内容按照标准合同格式进行排版，并返回完整的 HTML 格式结果。\n\n"
                        + "排版规范：\n"
                        + "1. 合同标题：用 <h1> 合同名称 </h1>，居中\n"
                        + "2. 合同编号：用 <p> 合同编号：××× </p>\n"
                        + "3. 甲乙双方：用 <p> 甲方：××× </p> 和 <p> 乙方：××× </p>\n"
                        + "4. 日期地点：用 <p> 签订日期：×××年××月××日 </p> 和 <p> 签订地点：××× </p>\n"
                        + "5. 条款标题：用 <h3> 第一条 ××× </h3>，或用 <ol> 编号列表\n"
                        + "6. 条款内容：用 <p> 包裹每一条款内容\n"
                        + "7. 子项：用 <ul> 或 <ol> 列表\n"
                        + "8. 签名区：用 <hr> 分隔，然后用 <div align=\"right\"><p> 甲方（签字）：_________ </p></div>\n\n"
                        + "请只返回排版后的 HTML，不使用 CSS。";
                userPrompt = "请按照合同协议标准格式排版以下内容，返回完整的 HTML：\n\n---\n" + text + "\n---\n\n请直接返回排版后的 HTML。";
                break;
            case "general":
            default:
                systemPrompt = "你是通用文档排版专家。你的任务是将用户提供的文档内容进行清晰的格式化排版，并返回完整的 HTML 格式结果。\n\n"
                        + "排版原则：\n"
                        + "1. 自动识别标题层级，将短小且独立的行设为 <h2> 或 <h3>\n"
                        + "2. 正常段落用 <p>\n"
                        + "3. 列表项用 <ul> 或 <ol>\n"
                        + "4. 表格用 <table>\n"
                        + "5. 强调内容用 <strong> 或 <em>\n"
                        + "6. 保持原有内容顺序，不增删内容\n"
                        + "7. 使文档结构清晰、易于阅读\n\n"
                        + "请只返回排版后的 HTML，不使用 CSS。";
                userPrompt = "请对以下内容进行清晰的格式化排版，返回完整的 HTML：\n\n---\n" + text + "\n---\n\n请直接返回排版后的 HTML。";
                break;
        }

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.put(userMsg);

        return messages;
    }

    // ============================================================
    //  V2: JSON-structured typeset prompts (for docx template filling)
    // ============================================================

    /**
     * Build typeset messages that ask the AI to return structured JSON
     * mapping template section keys to content (instead of raw HTML).
     */
    public static JSONArray buildTypesetMessagesV2(String typesetType, String fullText) throws JSONException {
        String text = fullText == null ? "" : fullText.trim();
        String[] sectionKeys = org.libreoffice.androidlib.typeset.TemplateSectionMap.getSectionKeys(typesetType);
        String sectionList = buildSectionList(sectionKeys);

        String systemPrompt = buildTypesetV2SystemPrompt(typesetType, sectionKeys, sectionList);
        String userPrompt = "请将以下原始文档内容按模板分区进行结构化拆分，返回 JSON。"
                + "不要修改原文内容，仅将各部分填入对应分区。\n\n"
                + "注意：原文中的图片已标记为[图1]、[图2]等占位符，请保留这些标记在原文对应的位置，不要删除或改写。\n\n"
                + "分区列表：" + sectionList + "\n\n"
                + "原始文档内容：\n---\n" + text + "\n---\n\n"
                + "请直接返回 JSON，不要包含任何 markdown 标记或解释文字。";

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.put(userMsg);

        return messages;
    }

    /**
     * Build type-specific system prompt for V2 JSON output.
     */
    private static String buildTypesetV2SystemPrompt(String typesetType, String[] sectionKeys, String sectionList) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是专业的文档排版助手。请从用户提供的原始文档中提取内容，");
        sb.append("将原文各部分填入模板对应的分区中，以 JSON 格式返回。\n");
        sb.append("重要原则：保持原文内容不变，不要改写、扩写、缩写或润色原文。");
        sb.append("仅进行结构化拆分——把原文各部分分配到对应的模板分区。\n\n");

        switch (typesetType) {
            case "paper":
                sb.append("【论文模板分区】\n");
                sb.append("- title: 论文标题\n");
                sb.append("- abstract: 摘要正文（约200-300字，概括研究目的、方法、结果、结论）\n");
                sb.append("- keywords: 关键词（3-8个，用顿号分隔）\n");
                sb.append("- introduction: 引言/背景介绍\n");
                sb.append("- heading1: 第一个一级章节标题\n");
                sb.append("- heading2: 第一个二级小节标题\n");
                sb.append("- heading3: 第一个三级小节标题\n");
                sb.append("- body: 正文（可包含多个段落，段落间用双换行分隔）\n");
                sb.append("- conclusion_body: 结语/结论内容\n");
                sb.append("- ack_body: 致谢内容\n\n");
                sb.append("要求：从原文中提取各分区内容，保留原文的学术风格和核心论点；正文应完整保留原文内容，不做删改；标题层级根据原文结构确定。");
                break;

            case "gov":
                sb.append("【公文模板分区】\n");
                sb.append("- recipient: 主送机关（含\"主送机关：\"前缀，如原文无则根据内容推断合理的主送机关）\n");
                sb.append("- body: 正文（可包含多个段落，段落间用双换行分隔）\n");
                sb.append("- signature_org: 发文机关署名（如原文无署名则填\"（请填写）\"）\n");
                sb.append("- signature_date: 成文日期（格式：20××年×月×日，如原文无日期则填\"20××年×月×日\"）\n");
                sb.append("- notes: 附注内容（含括号，如无附注则填\"（无）\"）\n\n");
                sb.append("要求：从原文中提取各分区内容，保留公文的正式严谨风格和原文措辞，不做修改。");
                sb.append("所有分区键值必须非空——即使原文无对应内容，也必须使用上述指定的回退值填充。");
                break;

            case "contract":
                sb.append("【合同协议模板分区】\n");
                sb.append("- title: 合同/协议标题\n");
                sb.append("- contract_number: 合同编号（含\"合同编号：\"前缀）\n");
                sb.append("- party_a: 甲方信息（含\"甲方：\"前缀，后跟名称、地址等）\n");
                sb.append("- party_a_id: 甲方身份证号/统一社会信用代码（含\"身份证号码：\"或\"统一社会信用代码：\"前缀）\n");
                sb.append("- party_b: 乙方信息（含\"乙方：\"前缀）\n");
                sb.append("- party_b_id: 乙方身份证号/统一社会信用代码（含\"身份证号码：\"或\"统一社会信用代码：\"前缀）\n");
                sb.append("- preamble: 鉴于条款/前言（说明合同背景和目的）\n");
                sb.append("- clause_title: 主要条款标题（如\"第一条 项目内容\"）\n");
                sb.append("- clause_subtitle: 次要条款标题（如\"1.1 具体范围\"）\n");
                sb.append("- clause_body: 条款正文（可多段，双换行分隔）\n\n");
                sb.append("要求：从原文中提取各分区内容，保留合同的法律严谨性和原文措辞，不做修改。");
                break;

            case "general":
            default:
                sb.append("【通用文档模板分区】\n");
                sb.append("- title: 文档标题\n");
                sb.append("- heading1: 一级标题\n");
                sb.append("- heading2: 二级标题\n");
                sb.append("- heading3: 三级标题\n");
                sb.append("- body: 正文（可包含多个段落，段落间用双换行分隔）\n\n");
                sb.append("要求：从原文中提取各分区内容，根据原文自动识别层级结构；保留原文风格和信息完整，不做修改。");
                break;
        }

        sb.append("\n\n【输出格式】\n");
        sb.append("请严格按以下 JSON 格式返回：\n");
        sb.append("{\"sections\": {\n");
        for (int i = 0; i < sectionKeys.length; i++) {
            sb.append("  \"").append(sectionKeys[i]).append("\": \"...\"");
            if (i < sectionKeys.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("}}\n\n");
        sb.append("重要：只返回 JSON 对象本身，不要用 ```json 或 ``` 包裹，不要添加任何解释文字。");
        sb.append("每一个键的值都必须是非空字符串——禁止使用空字符串（\"\"）作为值。");
        sb.append("如果原文中确实找不到某个分区对应的内容，请使用合理的占位文本（如\"（无）\"、\"（请填写）\"等），不要留空。");

        return sb.toString();
    }

    private static String buildSectionList(String[] keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(keys[i]);
        }
        return sb.toString();
    }

    /**
     * Parse the AI's JSON response into a section key → content map.
     * Handles markdown code fences, whitespace, and missing "sections" wrapper.
     *
     * @param jsonResponse the raw AI response text
     * @return map of sectionKey → content, or null if parsing fails
     */
    public static Map<String, String> parseTypesetSections(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) return null;

        String json = jsonResponse.trim();

        // Strip markdown code fences
        json = stripMarkdownFences(json);

        // Try to parse as JSON
        try {
            JSONObject root = new JSONObject(json);

            // Case 1: {"sections": {...}}
            if (root.has("sections")) {
                JSONObject sectionsObj = root.getJSONObject("sections");
                LinkedHashMap<String, String> result = new LinkedHashMap<>();
                for (java.util.Iterator<String> it = sectionsObj.keys(); it.hasNext(); ) {
                    String key = it.next();
                    String value = sectionsObj.optString(key, "");
                    if (!value.isEmpty()) {
                        result.put(key, value);
                    }
                }
                if (!result.isEmpty()) return result;
            }

            // Case 2: flat object — treat all string values as sections
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            for (java.util.Iterator<String> it = root.keys(); it.hasNext(); ) {
                String key = it.next();
                Object val = root.opt(key);
                if (val instanceof String && !((String) val).isEmpty()) {
                    result.put(key, (String) val);
                }
            }
            if (!result.isEmpty()) return result;

        } catch (JSONException e) {
            // Not valid JSON, return null for fallback
            Log.w(TAG, "parseTypesetSections_json_failed at: "
                    + (json.length() > 120 ? json.substring(0, 120) + "..." : json));
        }

        return null;
    }

    /**
     * Strip markdown code fences (```json ... ``` or ``` ... ```) from AI response.
     */
    private static String stripMarkdownFences(String text) {
        if (text == null) return null;
        String t = text.trim();

        // Remove opening fence
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) {
                t = t.substring(firstNewline + 1);
            } else {
                t = t.substring(3);
            }
        }

        // Remove closing fence
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3).trim();
        }

        return t.trim();
    }

    /**
     * Fallback: delegate to the old HTML-based typeset prompt.
     */
    public static JSONArray buildTypesetFallbackMessages(String typesetType, String fullText) throws JSONException {
        return buildTypesetMessages(typesetType, fullText);
    }

    // ============================================================
    //  Paragraph-level classification (preserves original text verbatim)
    // ============================================================

    /** One paragraph → section mapping from AI classification. */
    public static class ParaSection {
        public final int paraIndex;
        public final String section;
        public ParaSection(int paraIndex, String section) {
            this.paraIndex = paraIndex;
            this.section = section;
        }
    }

    /**
     * Build messages that ask the AI to classify each paragraph into a template section.
     * The AI only assigns section labels — it does NOT rewrite the text.
     */
    public static JSONArray buildTypesetParagraphMessages(
            String typesetType, java.util.List<String> paragraphs,
            java.util.List<java.util.List<String>> paraImageMarkers) throws JSONException {
        String[] sectionKeys = org.libreoffice.androidlib.typeset.TemplateSectionMap.getSectionKeys(typesetType);
        String sectionList = buildSectionList(sectionKeys);

        StringBuilder userText = new StringBuilder();
        userText.append("请为每个段落判断它属于模板中的哪个分区。\n\n");
        userText.append("可用分区：").append(sectionList).append("\n\n");
        userText.append("段落内容（按编号）：\n");
        userText.append("---\n");

        for (int i = 0; i < paragraphs.size(); i++) {
            String para = paragraphs.get(i);
            StringBuilder line = new StringBuilder();
            line.append(i).append(": ");
            // Prepend image markers if any
            if (paraImageMarkers != null && i < paraImageMarkers.size()) {
                for (String m : paraImageMarkers.get(i)) {
                    line.append("[").append(m).append("]");
                }
            }
            line.append(para);
            userText.append(line).append("\n---\n");
        }

        userText.append("\n请直接返回 JSON 数组，不要使用 ```json 或 markdown 标记，格式：\n");
        userText.append("[{\"paraIndex\": 0, \"section\": \"title\"}, {\"paraIndex\": 1, \"section\": \"body\"}, ...]\n");
        userText.append("每个段落必须分配一个分区。如果某个段落不匹配任何可用分区，使用最接近的分区。");

        String systemPrompt = "你是文档段落分类助手。你的任务是将文档的每个段落分配到模板的对应分区中。\n"
                + "只返回 JSON 数组，格式：[{\"paraIndex\":0,\"section\":\"title\"},{\"paraIndex\":1,\"section\":\"body\"},...]\n"
                + "不要改写原文，不要增减内容，只做分类。返回纯 JSON 字符串，不要用 markdown 包裹。";

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userText.toString());
        messages.put(userMsg);

        return messages;
    }

    /**
     * Parse the AI's paragraph classification response into ParaSection list.
     * Expected format: [{"paraIndex":0,"section":"title"}, ...]
     */
    public static java.util.List<ParaSection> parseTypesetParagraphResult(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) return null;

        String json = jsonResponse.trim();
        // Strip markdown code fences
        if (json.startsWith("```")) {
            int nl = json.indexOf('\n');
            if (nl > 0) json = json.substring(nl + 1);
            else json = json.substring(3);
            json = json.trim();
            if (json.endsWith("```")) json = json.substring(0, json.length() - 3).trim();
        }

        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            java.util.List<ParaSection> result = new java.util.ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject item = arr.getJSONObject(i);
                int paraIndex = item.getInt("paraIndex");
                String section = item.optString("section", "");
                if (!section.isEmpty()) {
                    result.add(new ParaSection(paraIndex, section));
                }
            }
            return result.isEmpty() ? null : result;
        } catch (org.json.JSONException e) {
            Log.w(TAG, "parseTypesetParagraphResult_failed", e);
            return null;
        }
    }

    /**
     * 构建生成大纲消息
     * @param outlineType 大纲类型：paper(论文) | report(工作报告) | speech(演讲稿) | event(活动策划) | general(通用文档)
     * @param contextText 参考内容（选区文字或文档全文，可为空）
     * @param userDesc 用户补充说明，可为空
     */
    public static JSONArray buildOutlineMessages(String outlineType, String contextText, String userDesc) throws JSONException {
        String typeLabel;
        if (outlineType == null) {
            outlineType = OUTLINE_TYPE_GENERAL;
        }
        switch (outlineType) {
            case OUTLINE_TYPE_PAPER:
                typeLabel = "学术论文";
                break;
            case OUTLINE_TYPE_REPORT:
                typeLabel = "工作报告";
                break;
            case OUTLINE_TYPE_SPEECH:
                typeLabel = "演讲稿";
                break;
            case OUTLINE_TYPE_EVENT:
                typeLabel = "活动策划";
                break;
            case OUTLINE_TYPE_GENERAL:
            default:
                typeLabel = "通用文档";
                break;
        }

        String systemPrompt = "你是专业的大纲生成助手。请根据用户提供的文档类型、参考内容和补充说明，"
                + "生成一份结构清晰、层次分明的大纲。\n\n"
                + "要求：\n"
                + "1. 使用中文编号：一级用「一、二、三…」，二级用「1. 2. 3.」，三级用「(1) (2) (3)」\n"
                + "2. 每个一级标题下给出必要的二级要点，三级按需展开\n"
                + "3. 标题简洁明确，要点可附一句简要说明\n"
                + "4. 覆盖该类型文档的完整结构（如论文含摘要/引言/方法/结果/结论）\n"
                + "5. 只输出大纲本身，不要输出前言、解释或额外说明";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请生成一份【").append(typeLabel).append("】大纲。\n");
        String text = contextText == null ? "" : contextText.trim();
        if (!text.isEmpty()) {
            userPrompt.append("\n参考内容：\n").append(text).append("\n");
        }
        String desc = userDesc == null ? "" : userDesc.trim();
        if (!desc.isEmpty()) {
            userPrompt.append("\n补充说明：").append(desc).append("\n");
        }
        userPrompt.append("\n请直接输出大纲。");

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt.toString());
        messages.put(userMsg);

        return messages;
    }

    /**
     * 构建文案生成消息
     * @param template 文案模板
     * @param values 与 template.variables 顺序对应的用户输入值
     */
    public static JSONArray buildArticleMessages(ArticleTemplate template, String[] values)
            throws JSONException {
        if (template == null) {
            throw new JSONException("Article template is null");
        }
        String systemPrompt = "你是中文文案写作专家，请根据用户提供的要素撰写一份规范、得体的"
                + template.subTypeLabel + "。只输出正文内容，不要输出解释或标题前缀。";

        String userPrompt = template.promptTemplate;
        ArticleTemplate.Variable[] vars = template.variables;
        for (int i = 0; i < vars.length; i++) {
            String placeholder = "{变量" + (i + 1) + "}";
            String value = (values != null && i < values.length) ? values[i] : "";
            if (value == null || value.trim().isEmpty()) {
                value = vars[i].hint;
            }
            userPrompt = userPrompt.replace(placeholder, value.trim());
        }

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.put(userMsg);

        return messages;
    }

    public static JSONArray buildExpandMessages(String selection, String requirement) throws JSONException {
        String text = selection == null ? "" : selection.trim();
        String systemPrompt = "你是中文文案扩写专家，请将用户提供的文本扩展得更详细丰富，增加细节、例证和论述。只返回扩写后的全文。";
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请将以下内容扩展得更详细丰富：\n\n---\n").append(text).append("\n---");
        String req = requirement == null ? "" : requirement.trim();
        if (!req.isEmpty()) {
            userPrompt.append("\n\n额外要求：").append(req);
        }
        return buildSimpleMessages(systemPrompt, userPrompt.toString());
    }

    public static JSONArray buildCondenseMessages(String selection, String requirement) throws JSONException {
        String text = selection == null ? "" : selection.trim();
        String systemPrompt = "你是中文文案缩写专家，请压缩用户提供的文本，保留关键信息，去除冗余，缩减至原长度的一半左右。只返回缩写后的全文。";
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请压缩以下文本，保留关键信息：\n\n---\n").append(text).append("\n---");
        String req = requirement == null ? "" : requirement.trim();
        if (!req.isEmpty()) {
            userPrompt.append("\n\n额外要求：").append(req);
        }
        return buildSimpleMessages(systemPrompt, userPrompt.toString());
    }

    public static JSONArray buildPolishMessages(String polishStyle, String selection) throws JSONException {
        String text = selection == null ? "" : selection.trim();
        if (polishStyle == null || polishStyle.isEmpty()) {
            polishStyle = POLISH_STYLE_QUICK;
        }
        String systemPrompt;
        String styleLabel;
        switch (polishStyle) {
            case POLISH_STYLE_FORMAL:
                systemPrompt = "你是中文文案润色专家，请将用户提供的文案润色得更正式、更书面化，使用规范用语，避免口语表达。只返回润色后的全文。";
                styleLabel = "更正式";
                break;
            case POLISH_STYLE_LIVELY:
                systemPrompt = "你是中文文案润色专家，请将用户提供的文案润色得更活泼生动，语气轻松有活力，增强感染力。只返回润色后的全文。";
                styleLabel = "更活泼";
                break;
            case POLISH_STYLE_PARTY_GOVT:
                systemPrompt = "你是中文文案润色专家，请将用户提供的文案润色成党政公文风格，用语规范严谨，符合党政机关行文习惯。只返回润色后的全文。";
                styleLabel = "党政风";
                break;
            case POLISH_STYLE_COLLOQUIAL:
                systemPrompt = "你是中文文案润色专家，请将用户提供的文案润色得更口语化，贴近日常交流，自然亲切。只返回润色后的全文。";
                styleLabel = "口语化";
                break;
            case POLISH_STYLE_ACADEMIC:
                systemPrompt = "你是中文文案润色专家，请将用户提供的文案润色得更学术化，用词严谨准确，逻辑清晰，符合学术写作规范。只返回润色后的全文。";
                styleLabel = "更学术";
                break;
            case POLISH_STYLE_INTERNET:
                systemPrompt = "你是中文文案润色专家，请将用户提供的文案润色成网络话术风格，生动有趣，适当使用网络流行表达。只返回润色后的全文。";
                styleLabel = "网络话术";
                break;
            case POLISH_STYLE_QUICK:
            default:
                systemPrompt = "你是中文文案润色专家，请对用户提供的文案进行快速润色，修正语病、提升流畅度，保持原意。只返回润色后的全文。";
                styleLabel = "快速润色";
                break;
        }
        String userPrompt = "请将以下文案润色成" + styleLabel + "风格：\n\n---\n" + text + "\n---";
        return buildSimpleMessages(systemPrompt, userPrompt);
    }

    public static JSONArray buildTranslateMessages(String sourceLang, String targetLang, String text)
            throws JSONException {
        String content = text == null ? "" : text.trim();
        if (sourceLang == null || sourceLang.isEmpty()) {
            sourceLang = TRANSLATE_LANG_AUTO;
        }
        if (targetLang == null || targetLang.isEmpty()) {
            targetLang = TRANSLATE_LANG_ZH;
        }
        String targetLabel = getTranslateLanguageLabel(targetLang);
        String systemPrompt;
        if (TRANSLATE_LANG_AUTO.equals(sourceLang)) {
            systemPrompt = "你是专业翻译，请自动识别用户提供的文本语言，并将其翻译成"
                    + targetLabel + "，自然流畅、准确传达原意。只返回译文。";
        } else {
            String sourceLabel = getTranslateLanguageLabel(sourceLang);
            systemPrompt = "你是专业翻译，请将用户提供的" + sourceLabel + "文本翻译成"
                    + targetLabel + "，自然流畅、准确传达原意。只返回译文。";
        }
        String userPrompt = "请将以下文本翻译成" + targetLabel + "：\n\n---\n" + content + "\n---";
        return buildSimpleMessages(systemPrompt, userPrompt);
    }

    public static JSONArray buildRewriteMessages(String selection, String requirement) throws JSONException {
        String text = selection == null ? "" : selection.trim();
        String systemPrompt = "You are a versatile Chinese writer. Rewrite in a fresh way while preserving original meaning.";
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请用不同的表达方式和句式重写以下内容，保持原意不变：\n\n---\n").append(text).append("\n---");
        String req = requirement == null ? "" : requirement.trim();
        if (!req.isEmpty()) {
            userPrompt.append("\n\n额外要求：").append(req);
        }
        return buildSimpleMessages(systemPrompt, userPrompt.toString());
    }

    private static String getTranslateLanguageLabel(String key) {
        switch (key) {
            case TRANSLATE_LANG_ZH:
                return "中文";
            case TRANSLATE_LANG_EN:
                return "英文";
            case TRANSLATE_LANG_JA:
                return "日文";
            case TRANSLATE_LANG_KO:
                return "韩文";
            case TRANSLATE_LANG_FR:
                return "法文";
            case TRANSLATE_LANG_DE:
                return "德文";
            case TRANSLATE_LANG_ES:
                return "西班牙文";
            case TRANSLATE_LANG_RU:
                return "俄文";
            case TRANSLATE_LANG_AUTO:
            default:
                return "目标语言";
        }
    }

    private static JSONArray buildSimpleMessages(String systemPrompt, String userPrompt)
            throws JSONException {
        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.put(userMsg);
        return messages;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    /** 构造视觉模型 OCR 请求 messages（OpenAI vision content 数组格式）。 */
    /**
     * 构建 Calc AI 公式生成 messages
     * @param userInput 用户自然语言描述的公式需求
     * @param cellAddress 当前选中的单元格地址（如 "A1"），可为空
     */
    public static JSONArray buildCalcFormulaMessages(String userInput, String cellAddress) throws JSONException {
        String input = userInput == null ? "" : userInput.trim();
        String addr = cellAddress == null ? "" : cellAddress.trim();

        StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("你是 Excel/Calc 公式生成助手。根据用户用自然语言描述的公式需求，生成对应的电子表格函数公式。\n");
        sysPrompt.append("要求：\n");
        sysPrompt.append("1. 只返回公式本身（如 =AVERAGE(A1:A10)），不要包含任何解释或额外内容\n");
        sysPrompt.append("2. 公式必须以 = 开头\n");
        sysPrompt.append("3. 注意单元格引用语法，非中文函数的 region 使用英文函数名\n");
        sysPrompt.append("4. 如果用户指定了筛选条件（如「大于 10」），请确保公式语法正确\n");
        if (!addr.isEmpty()) {
            sysPrompt.append("\n当前选中单元格：").append(addr).append("，注意相对引用。");
        }
        sysPrompt.append("\n\n示例：\n");
        sysPrompt.append("用户：计算 A1 到 A10 的平均值\n");
        sysPrompt.append("公式：=AVERAGE(A1:A10)\n");
        sysPrompt.append("用户：计算 B 列的和\n");
        sysPrompt.append("公式：=SUM(B:B)");

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", sysPrompt.toString());
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", input);
        messages.put(userMsg);

        return messages;
    }

    /**
     * 构建 Calc AI 条件格式 messages
     * @param userInput 用户自然语言描述的条件格式需求
     * @param cellRange 当前选中的单元格范围（如 "A1:A10"），可为空
     */
    public static JSONArray buildCondFormatMessages(String userInput, String cellRange, String cellData) throws JSONException {
        String input = userInput == null ? "" : userInput.trim();
        String range = cellRange == null ? "" : cellRange.trim();
        String data = cellData == null ? "" : cellData.trim();

        StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("你是 Excel/Calc 条件格式智能分析助手。\n");
        sysPrompt.append("用户提供了一段选中的表格数据和一个条件格式需求。\n");
        sysPrompt.append("请分析数据并决定最合适的条件格式类型，以 JSON 格式输出。\n\n");
        sysPrompt.append("选中范围：").append(range).append("\n");
        sysPrompt.append("选中数据：\n");
        sysPrompt.append(data.isEmpty() ? "（无数据或空区域）" : data).append("\n\n");
        sysPrompt.append("用户需求：").append(input).append("\n\n");
        sysPrompt.append("JSON 输出格式（不要多余文字，不要代码块标记）：\n");
        sysPrompt.append("{\n");
        sysPrompt.append("  \"conditionType\": \"greater|less|equal|between|top_n|bottom_n|above_average|below_average|duplicate|unique|contains_text|formula|clear\",\n");
        sysPrompt.append("  \"value\": \"条件值\",\n");
        sysPrompt.append("  \"value2\": \"第二个值（between 时使用）\",\n");
        sysPrompt.append("  \"range\": \"应用范围，如 A1:C100\",\n");
        sysPrompt.append("  \"format\": {\n");
        sysPrompt.append("    \"backgroundColor\": \"#RRGGBB\",\n");
        sysPrompt.append("    \"fontColor\": \"#RRGGBB\",\n");
        sysPrompt.append("    \"fontBold\": true,\n");
        sysPrompt.append("    \"fontItalic\": false,\n");
        sysPrompt.append("    \"border\": {\n");
        sysPrompt.append("      \"color\": \"#RRGGBB\",\n");
        sysPrompt.append("      \"style\": \"thin|medium|thick|none\"\n");
        sysPrompt.append("    }\n");
        sysPrompt.append("  },\n");
        sysPrompt.append("  \"style\": \"Bad|Good|Neutral（仅当 format 无法确定时兜底）\",\n");
        sysPrompt.append("  \"description\": \"中文说明（15字以内）\"\n");
        sysPrompt.append("}\n\n");
        sysPrompt.append("格式要求：\n");
        sysPrompt.append("- 必须优先输出 format 对象，颜色一律用 6 位十六进制（如 #FFCCCC）\n");
        sysPrompt.append("- 根据用户语义选择背景色和字体色；用户给出具体色值时原样使用\n");
        sysPrompt.append("- 常见语义映射：标红/异常/警告 → 背景 #FFCCCC 字体 #CC0000；标绿/达标 → 背景 #CCFFCC 字体 #006600；标黄/提醒 → 背景 #FFFFCC 字体 #996600\n");
        sysPrompt.append("- 淡蓝底示例：背景 #E8F4FD 字体 #1A4A7A；紫色高亮：背景 #F3E8FF 字体 #6B21A8\n");
        sysPrompt.append("- 用户要求加粗/斜体时设置 fontBold/fontItalic 为 true；未提及则省略该字段\n");
        sysPrompt.append("- 用户要求红框/边框/框线时设置 border.color 和 border.style（默认 thin）；不需要边框时省略 border 或 style=none\n");
        sysPrompt.append("- format 中可省略未提及的属性；至少提供 backgroundColor、fontColor、fontBold、fontItalic、border 之一\n\n");
        sysPrompt.append("条件类型说明：\n");
        sysPrompt.append("- greater: 大于某值（value=数字）\n");
        sysPrompt.append("- less: 小于某值\n");
        sysPrompt.append("- equal: 等于某值\n");
        sysPrompt.append("- between: 介于两值之间（value, value2）\n");
        sysPrompt.append("- top_n: 前 N 名（value=N）\n");
        sysPrompt.append("- bottom_n: 后 N 名\n");
        sysPrompt.append("- above_average: 高于平均值（不需要 value）\n");
        sysPrompt.append("- below_average: 低于平均值\n");
        sysPrompt.append("- duplicate: 重复值\n");
        sysPrompt.append("- unique: 唯一值\n");
        sysPrompt.append("- contains_text: 包含文本（value=匹配文本）\n");
        sysPrompt.append("- formula: 自定义布尔公式（value 必须以 = 开头，结果 TRUE/FALSE；用于「最高价对应行」「MATCH/INDEX」等跨行逻辑）\n");
        sysPrompt.append("  公式示例（高亮 A 列中价格最高者，价格在 D 列，数据行 A2:D13）：\n");
        sysPrompt.append("  conditionType=formula, value==A2=INDEX($A$2:$A$13,MATCH(MAX($D$2:$D$13),$D$2:$D$13,0)), range=A2:A13\n");
        sysPrompt.append("  注意：公式中当前行引用用相对地址（如 A2），数据区用绝对地址（$A$2:$A$13）；range 必须是公式要格式化的列\n\n");
        sysPrompt.append("- clear: **清除/取消/恢复默认**条件格式与直接格式（用户说「取消格式」「恢复正常颜色」「去掉条件格式」时使用）。\n");
        sysPrompt.append("  此时 conditionType 必须为 \"clear\"，range 为要清除的范围（如 F1:F15 或 F:F），不要输出 format/style/value。\n\n");
        sysPrompt.append("请基于实际数据分析判断最合适的条件格式类型，确保 range 是实际的数据范围。");

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", sysPrompt.toString());
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", input);
        messages.put(userMsg);

        return messages;
    }

    public static JSONArray buildNewCalcTableMessages(String userInput) throws JSONException {
        String input = (userInput == null ? "" : userInput.trim());
        if (input.isEmpty()) {
            input = "生成一份示例数据表";
        }

        StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("你是电子表格数据生成助手。根据用户的自然语言描述，生成对应的表格数据。\n");
        sysPrompt.append("要求：\n");
        sysPrompt.append("1. 只返回纯 JSON，不要包含 Markdown 包裹、代码块标记、解释或任何额外文字\n");
        sysPrompt.append("2. JSON 格式严格如下：\n");
        sysPrompt.append("{\n");
        sysPrompt.append("  \"columns\": [\"列名1\", \"列名2\", \"列名3\", ...],\n");
        sysPrompt.append("  \"data\": [\n");
        sysPrompt.append("    [\"值1\", \"值2\", \"值3\"],\n");
        sysPrompt.append("    [\"值1\", \"值2\", \"值3\"],\n");
        sysPrompt.append("    ...\n");
        sysPrompt.append("  ]\n");
        sysPrompt.append("}\n");
        sysPrompt.append("3. 列名为中文，清晰表达每列含义\n");
        sysPrompt.append("4. 数据至少包含 8 行，数据内容要真实合理、有变化\n");
        sysPrompt.append("5. 数字类数据不要加引号（使用 number 类型），文本类数据加引号\n");
        sysPrompt.append("6. 所有字符串值使用双引号\n");
        sysPrompt.append("\n示例：\n");
        sysPrompt.append("用户：帮我生成 2024 年各季度销售数据表\n");
        sysPrompt.append("输出：\n");
        sysPrompt.append("{\n");
        sysPrompt.append("  \"columns\": [\"季度\", \"销售额\", \"同比增长\", \"备注\"],\n");
        sysPrompt.append("  \"data\": [\n");
        sysPrompt.append("    [\"Q1\", 1200000, \"10%\", \"春节促销\"],\n");
        sysPrompt.append("    [\"Q2\", 1500000, \"25%\", \"618 大促\"],\n");
        sysPrompt.append("    [\"Q3\", 1800000, \"20%\", \"双十一预热\"],\n");
        sysPrompt.append("    [\"Q4\", 2500000, \"39%\", \"年终冲刺\"]\n");
        sysPrompt.append("  ]\n");
        sysPrompt.append("}\n");

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", sysPrompt.toString());
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", input);
        messages.put(userMsg);

        return messages;
    }

    /**
     * 构建 AI 数据处理 messages。
     * @param userInput 用户自然语言描述的数据处理需求
     * @param cellRange 选中单元格范围（如 "A1:C20"）
     * @param cellDataSample 选中区域的单元格数据（前 N 行或全部），用于 AI 分析
     */
    public static JSONArray buildDataProcessMessages(String userInput, String cellRange, String cellDataSample) throws JSONException {
        String input = userInput == null ? "" : userInput.trim();
        String range = cellRange == null ? "" : cellRange.trim();
        String data = cellDataSample == null ? "" : cellDataSample.trim();

        StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("你是电子表格数据处理专家。根据用户的自然语言描述，分析已选中的数据并生成操作指令。\n");
        sysPrompt.append("返回严格的 JSON 格式，不要包含 Markdown 包裹、代码块标记或其他任何文字。\n\n");
        sysPrompt.append("JSON 输出格式：\n");
        sysPrompt.append("{\n");
        sysPrompt.append("  \"description\": \"简短的操作说明（30字以内）\",\n");
        sysPrompt.append("  \"actions\": [\n");
        sysPrompt.append("    {\"type\": \"...\", \"range\": \"A1:C100\", \"value\": \"...\", \"ascending\": true, ...},\n");
        sysPrompt.append("    ...\n");
        sysPrompt.append("  ]\n");
        sysPrompt.append("}\n\n");
        sysPrompt.append("支持的 action type：\n");
        sysPrompt.append("=== 简单模式（直接写值/公式）===\n");
        sysPrompt.append("- set_formula: 写入公式。params.value = 公式文本（如 =AVERAGE(A1:A10)）\n");
        sysPrompt.append("- set_value: 写入静态值。params.value = 值文本\n\n");
        sysPrompt.append("=== 表格操作模式（需要多步执行）===\n");
        sysPrompt.append("- sort: 排序。params.keyColumn = 列字母, params.ascending = true/false, params.hasHeader = true（默认第一行为标题行，不参与排序）\n");
        sysPrompt.append("- filter: 自动筛选\n");
        sysPrompt.append("- clear_formatting: 清除所有直接格式\n");
        sysPrompt.append("- delete_rows: 删除行\n");
        sysPrompt.append("- delete_columns: 删除列\n");
        sysPrompt.append("- insert_rows: 插入行。params.position = \"before\"/\"after\"\n");
        sysPrompt.append("- insert_columns: 插入列。params.position = \"before\"/\"after\"\n");
        sysPrompt.append("- format_number: 数字格式。params.style = \"percent\"/\"currency\"/\"date\"/\"decimal\", params.decimals = 小数位数（可选，decimal style 时生效）\n");
        sysPrompt.append("- set_column_width: 自适应列宽\n");
        sysPrompt.append("- merge_cells: 合并单元格\n");
        sysPrompt.append("- bold: 加粗\n");
        sysPrompt.append("- calculate: 重新计算\n");
        sysPrompt.append("\n约束：\n");
        sysPrompt.append("1. range 必须基于用户选中区域：").append(range).append("\n");
        sysPrompt.append("2. 如果用户需求不需要分步、只要写一个公式或值，使用 set_formula/set_value，动作数量为1\n");
        sysPrompt.append("3. description 控制在 30 字以内\n");
        sysPrompt.append("4. 如果用户需求无法用以上操作类型实现，则返回 {\"description\": \"无法执行的操作\", \"actions\": []}\n");
        sysPrompt.append("5. 当用户需求是\"追加\"\"添加行\"\"在下方增加一行\"\"统计\"\"汇总\"\"求平均/和/最大值/最小值\"等**在已有数据区域末尾添加内容**的操作：\n");
        sysPrompt.append("   **优先使用 set_formula / set_value 直接写入已有数据下方的空白行**，range 写目标单元格（如 A9），不要先 insert_rows 再写入。\n");
        sysPrompt.append("   仅当用户明确要求\"在行5和行6之间插入一行\"这类**中间插入**时才使用 insert_rows。\n");
        sysPrompt.append("   insert_rows 本身是可行的，只是对于\"数据末尾追加\"场景没有必要先插入空行。\n");
        sysPrompt.append("6. set_value/set_formula 的 range 必须是 \"列字母+行号\"（如 A9、B10、C2:C9）。\n");
        sysPrompt.append("7. delete_columns / insert_columns 的 range 只表示**要操作的列**，格式 \"A:A\" 或 \"B:D\"，与用户是否全选无关；\n");
        sysPrompt.append("   用户说「删除 A 列」必须返回 range=\"A:A\"，禁止返回整表范围（如 A1:AMJ1048576）。\n");
        sysPrompt.append("8. delete_rows / insert_rows 的 range 只表示**要操作的行**，格式 \"5:5\" 或 \"3:7\"（行号），与全选无关。\n");
        sysPrompt.append("9. 「删除/清空列的数据/内容」用 set_value，range 写目标列单元格范围（如 F1:F15），value 留空 \"\"；\n");
        sysPrompt.append("   「删除整列（列本身消失）」才用 delete_columns，range 写 \"F:F\"。\n");
        sysPrompt.append("10. insert_columns / add_column 后写入的新列不要附带条件格式；若需纯数据列，写入后追加 clear_formatting 到新列 range。\n");

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", sysPrompt.toString());
        messages.put(sysMsg);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("用户选中区域：").append(range).append("\n\n");
        userPrompt.append("选中的数据：\n");
        userPrompt.append(data.isEmpty() ? "（无数据或空区域）" : data);
        userPrompt.append("\n\n用户需求：").append(input);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt.toString());
        messages.put(userMsg);

        return messages;
    }

    /**
     * 构建 AI 数据分析 messages。分析数据趋势、统计信息，不修改数据。
     */
    public static JSONArray buildDataAnalysisMessages(String userInput, String cellRange, String cellDataSample) throws JSONException {
        String input = userInput == null ? "" : userInput.trim();
        String data = cellDataSample == null ? "" : cellDataSample.trim();

        StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("你是电子表格数据分析助手。根据用户提供的表格数据和问题，进行数据分析。\n");
        sysPrompt.append("数据只作为分析参考，不要输出 JSON，直接用中文给出分析结论。\n");
        sysPrompt.append("分析内容包括（根据数据情况选择性提供）：\n");
        sysPrompt.append("- 数据概览：总行数、列数、关键字段\n");
        sysPrompt.append("- 统计摘要：合计、平均值、最大值、最小值（针对数值列）\n");
        sysPrompt.append("- 数据分布：是否有异常值、空值、重复\n");
        sysPrompt.append("- 业务洞察：基于数据内容的发现和建议\n");
        sysPrompt.append("- 回答用户的具体问题\n\n");
        sysPrompt.append("格式要求：\n");
        sysPrompt.append("- 用中文，简明扼要\n");
        sysPrompt.append("- 重要数据用数字突出\n");
        sysPrompt.append("- 不加 Markdown 代码块\n");

        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", sysPrompt.toString());
        messages.put(sysMsg);

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("选中数据（范围：").append(cellRange).append("）：\n");
        userPrompt.append(data.isEmpty() ? "（无数据）" : data);
        userPrompt.append("\n\n用户问题：").append(input);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt.toString());
        messages.put(userMsg);

        return messages;
    }

    /**
     * AI图表生成 prompt
     */
    public static JSONArray buildChartMessages(String userInput, String cellRange, String cellDataSample) throws JSONException {
        JSONArray messages = new JSONArray();

        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        String prompt = "你是一个Calc电子表格图表专家。你的任务是根据用户指令和选中的单元格数据，生成图表。\n\n"
            + "用户提供了选中的单元格范围：" + cellRange + "\n"
            + "单元格数据样本：\n" + cellDataSample + "\n\n"
            + "请分析用户指令，按以下 JSON 格式返回：\n\n"
            + "如果用户指令不需要数据预处理（直接基于选中区域创建图表）：\n"
            + "```json\n"
            + "{\n"
            + "  \"preprocess\": [],\n"
            + "  \"chart\": {\n"
            + "    \"dataRange\": \"$Sheet1.$A$1:$B$10\",\n"
            + "    \"chartType\": \"pie|bar|column|line\",\n"
            + "    \"title\": \"图表标题\"\n"
            + "  }\n"
            + "}\n"
            + "```\n\n"
            + "如果需要数据预处理（如计算平均值、求和等）：\n"
            + "```json\n"
            + "{\n"
            + "  \"preprocess\": [\n"
            + "    {\"type\": \"formula\", \"address\": \"$Sheet1.$C$1\", \"value\": \"=AVERAGE($Sheet1.$A$1:$A$10)\"}\n"
            + "  ],\n"
            + "  \"chart\": {\n"
            + "    \"dataRange\": \"$Sheet1.$A$1:$C$11\",\n"
            + "    \"chartType\": \"pie\",\n"
            + "    \"title\": \"图表标题\"\n"
            + "  }\n"
            + "}\n"
            + "```\n\n"
            + "chartType 只支持: pie(饼图), bar(条形图/横向), column(柱状图/纵向), line(折线图)\n"
            + "请严格用 JSON 格式回复，不要添加额外的解释文本。";
        systemMsg.put("content", prompt);
        messages.put(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userInput);
        messages.put(userMsg);

        return messages;
    }

    /**
     * 构建 Impress PPT 大纲生成请求消息
     */
    public static JSONArray buildImpressOutlineMessages(
            String inputType, String userInput, int pageRange,
            String audience, String style) throws JSONException {

        String inputLabel;
        if ("quick".equals(inputType)) {
            inputLabel = "主题";
        } else if ("document".equals(inputType)) {
            inputLabel = "文档内容";
        } else {
            inputLabel = "大纲";
        }

        String systemPrompt = "你是一个专业PPT大纲生成助手。根据用户提供的" + inputLabel
                + "、页数范围、听众类型和风格，生成结构化JSON大纲。\n\n"
                + "输出格式要求（严格JSON，不要额外文字）：\n"
                + "{\n"
                + "  \"slides\": [\n"
                + "    {\"page\": 1, \"type\": \"cover\", \"title\": \"标题\", \"content\": \"副标题/附加信息\"},\n"
                + "    {\"page\": 2, \"type\": \"toc\", \"title\": \"目录\", \"content\": \"1. XX\\n2. XX\\n3. XX\"},\n"
                + "    {\"page\": 3, \"type\": \"section_divider\", \"title\": \"第一章标题\", \"content\": \"本章概述（1-2句）\"},\n"
                + "    {\"page\": 4, \"type\": \"section\", \"title\": \"章节标题\", \"content\": \"• 要点1\\n• 要点2\"},\n"
                + "    {\"page\": \"N\", \"type\": \"end\", \"title\": \"谢谢\", \"content\": \"结束语\"}\n"
                + "  ]\n"
                + "}\n\n"
                + "type枚举：cover(封面)、toc(目录)、section_divider(章节分割页)、section(章节正文)、end(结尾)\n"
                + "每章结构：先一条 section_divider（title=章名，content=本章概述），再一条或多条 section（正文页）\n"
                + "title: 每页标题（简洁有力）\n"
                + "content: 内容要点（Markdown格式，用•开头的列表）\n"
                + "页数不超过" + pageRange + "页\n"
                + "风格：" + style + "\n"
                + "听众：" + audience;

        String userMessage;
        if ("quick".equals(inputType)) {
            userMessage = "请为主题生成PPT大纲：\n" + userInput;
        } else if ("document".equals(inputType)) {
            userMessage = "请根据以下文档内容生成PPT大纲：\n" + userInput;
        } else {
            userMessage = "请根据以下大纲整理为PPT结构：\n" + userInput;
        }

        JSONArray messages = new JSONArray();
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.put(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.put(userMsg);

        return messages;
    }

    /**
     * Build messages for PPT content generation (one batch).
     *
     * @param batchSlides   Slides for the current batch only (typically length 1).
     * @param outlineSlides Full outline for cross-page context.
     * @param templateId    Selected template ID.
     * @param batchIndex    Current batch index (0-based).
     * @param totalBatches  Total batch count.
     * @return messages array for the AI request.
     */
    public static JSONArray buildImpressGenerateMessages(
            JSONArray batchSlides,
            JSONArray outlineSlides,
            String templateId,
            int batchIndex,
            int totalBatches
    ) throws JSONException {
        String systemPrompt = "你是专业PPT内容生成助手。根据用户大纲，生成当前批次的详细内容。\n\n"
                + "输出格式（严格JSON，不要额外文字，不要代码块）：\n"
                + "{\n"
                + "  \"slides\": [\n"
                + "    {\n"
                + "      \"page\": 1,\n"
                + "      \"type\": \"cover|toc|section_divider|section|end\",\n"
                + "      \"title\": \"页面标题\",\n"
                + "      \"subtitle\": \"副标题字符串\",\n"
                + "      \"content_points\": [\"要点1\", \"要点2\"],\n"
                + "      \"detailed_content\": [\"详细内容1\", \"详细内容2\"]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n\n"
                + "要求：\n"
                + "1. slides 数组长度必须恰好为 1，只输出本批次那一页\n"
                + "2. subtitle 必须是字符串；无副标题时写 \"subtitle\": \"\"，禁止 \"subtitle\":, 或省略值\n"
                + "3. content_points 数量必须与模板该页要点槽位一致（2/3/4 等），优先 3 或 4 个\n"
                + "4. detailed_content 与 content_points 一一对应，每个要点展开1-3句详细说明\n"
                + "5. 内容要丰富、专业、有深度，不要笼统空泛\n"
                + "6. cover页只输出title+subtitle，content_points 和 detailed_content 用 []\n"
                + "7. toc页的content_points列出目录项，detailed_content 用 []\n"
                + "8. end页的content_points为致谢信息，subtitle 可为联系方式\n"
                + "9. section页必须输出 section_title 与 title 相同\n"
                + "10. 每个要点以换行符\\n分隔（在 JSON 字符串中用 \\n 表示）";

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("当前批次 ").append(batchIndex + 1).append("/").append(totalBatches).append("\n");
        userPrompt.append("模板：").append(templateId).append("\n\n");
        userPrompt.append("完整大纲（仅供参考，不要为其他页生成内容）：\n");
        if (outlineSlides != null) {
            userPrompt.append(outlineSlides.toString(2));
        }
        userPrompt.append("\n\n本批次必须生成的页（只输出这一页）：\n");
        if (batchSlides != null) {
            userPrompt.append(batchSlides.toString(2));
        }

        JSONArray messages = new JSONArray();
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.put(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt.toString());
        messages.put(userMsg);

        return messages;
    }

    public static JSONArray buildTextExtractMessages(String base64Image) throws JSONException {
        JSONArray messages = new JSONArray();
        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", "你是文字识别专家。请识别并提取图片中的所有文字，保持原始排版和段落结构，只返回提取的文字内容，不要添加解释。");
        messages.put(sysMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        JSONArray content = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", "请识别这张图片中的所有文字并提取出来：");
        content.put(textPart);
        JSONObject imagePart = new JSONObject();
        imagePart.put("type", "image_url");
        JSONObject imageUrl = new JSONObject();
        imageUrl.put("url", "data:image/png;base64," + (base64Image == null ? "" : base64Image));
        imagePart.put("image_url", imageUrl);
        content.put(imagePart);
        userMsg.put("content", content);
        messages.put(userMsg);
        return messages;
    }
}
