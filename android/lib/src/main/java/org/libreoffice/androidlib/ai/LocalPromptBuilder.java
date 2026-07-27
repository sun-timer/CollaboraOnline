package org.libreoffice.androidlib.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class LocalPromptBuilder {
    public static final int SAFETY_MARGIN = 128;

    private LocalPromptBuilder() {}

    public static int estimateTokens(JSONArray messages) throws JSONException {
        int chars = 0;
        if (messages == null) {
            return 0;
        }
        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.optJSONObject(i);
            if (item == null) {
                continue;
            }
            chars += item.optString("content", "").length();
        }
        return Math.max(1, chars / 2);
    }

    public static JSONArray buildPrompt(JSONArray history, int contextSize, int maxGenTokens, boolean multiTurn)
            throws JSONException {
        if (history == null) {
            return new JSONArray();
        }
        if (!multiTurn) {
            return new JSONArray(history.toString());
        }

        int budget = Math.max(256, contextSize - maxGenTokens - SAFETY_MARGIN);
        JSONObject system = null;
        List<JSONObject> turns = new ArrayList<>();
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.getJSONObject(i);
            if ("system".equals(item.optString("role", "")) && system == null) {
                system = item;
            } else {
                turns.add(item);
            }
        }

        JSONArray selected = new JSONArray();
        for (int start = turns.size() - 1; start >= 0; start--) {
            JSONArray trial = new JSONArray();
            if (system != null) {
                trial.put(system);
            }
            for (int j = start; j < turns.size(); j++) {
                trial.put(turns.get(j));
            }
            if (estimateTokens(trial) <= budget || selected.length() == 0) {
                selected = trial;
            } else {
                break;
            }
        }
        if (selected.length() == 0 && system != null) {
            selected.put(system);
        }
        return selected;
    }

    public static String formatPrompt(JSONArray messages) throws JSONException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.getJSONObject(i);
            String role = item.optString("role", "user");
            String content = item.optString("content", "");
            if ("system".equals(role)) {
                sb.append("System: ").append(content).append("\n\n");
            } else if ("assistant".equals(role)) {
                sb.append("Assistant: ").append(content).append("\n\n");
            } else {
                sb.append("User: ").append(content).append("\n\n");
            }
        }
        sb.append("Assistant: ");
        return sb.toString();
    }
}
