package org.libreoffice.androidlib.ai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;

public class AiChatCoordinator {
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
            case MODE_EXPAND:
            case MODE_POLISH:
            case MODE_SUMMARIZE:
            case MODE_CONDENSE:
            case MODE_REWRITE:
            case MODE_TRANSLATE:
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

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
