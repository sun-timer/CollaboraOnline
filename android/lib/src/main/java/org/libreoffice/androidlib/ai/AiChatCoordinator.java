package org.libreoffice.androidlib.ai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;

public class AiChatCoordinator {
    public static final String MODE_DOC_QA = "doc_qa";
    public static final String MODE_CHAT = "chat";

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

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
