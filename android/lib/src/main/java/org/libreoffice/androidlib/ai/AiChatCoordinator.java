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

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
