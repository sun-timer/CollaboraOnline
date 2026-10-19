package org.libreoffice.androidlib.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class LocalPromptBuilder {
    public static final int SAFETY_MARGIN = 128;
    private static final String FINAL_ANSWER_ONLY_INSTRUCTION =
            "只输出给用户看的最终答案，不要展示分析、思考过程、草稿或内部推理；推理请在内部完成。 "
                    + "Respond only with the final answer intended for the user. Do not expose analysis, "
                    + "chain-of-thought, internal reasoning, or draft text.";

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
            chars += item.optString("role", "").length();
            chars += item.optString("content", "").length();
        }
        return Math.max(1, chars / 2);
    }

    public static JSONArray buildPrompt(JSONArray history, int contextSize, int maxGenTokens, boolean multiTurn)
            throws JSONException {
        if (history == null) {
            return new JSONArray();
        }
        int budget = Math.max(256, contextSize - maxGenTokens - SAFETY_MARGIN);
        if (!multiTurn) {
            JSONArray selected = truncateToTokenBudget(new JSONArray(history.toString()), budget);
            return applyFinalAnswerOnlyMode(selected);
        }

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
        return applyFinalAnswerOnlyMode(selected);
    }

    private static JSONArray applyFinalAnswerOnlyMode(JSONArray messages) throws JSONException {
        JSONArray result = new JSONArray();
        int firstSystemIndex = -1;
        for (int i = 0; i < messages.length(); i++) {
            String role = messages.optJSONObject(i) == null ? ""
                    : messages.optJSONObject(i).optString("role", "");
            if (firstSystemIndex < 0 && "system".equals(role)) {
                firstSystemIndex = i;
            }
        }

        if (firstSystemIndex < 0) {
            result.put(new JSONObject()
                    .put("role", "system")
                    .put("content", FINAL_ANSWER_ONLY_INSTRUCTION));
        }

        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.getJSONObject(i);
            JSONObject copy = new JSONObject(item.toString());
            if (i == firstSystemIndex) {
                String existing = copy.optString("content", "");
                copy.put("content", existing.isEmpty()
                        ? FINAL_ANSWER_ONLY_INSTRUCTION
                        : existing + "\n\n" + FINAL_ANSWER_ONLY_INSTRUCTION);
            }
            result.put(copy);
        }
        return result;
    }

    public static JSONArray truncateToTokenBudget(JSONArray messages, int maxTokens) throws JSONException {
        if (messages == null || messages.length() == 0 || maxTokens <= 0) {
            return messages == null ? new JSONArray() : messages;
        }
        if (estimateTokens(messages) <= maxTokens) {
            return messages;
        }

        JSONObject system = null;
        List<JSONObject> turns = new ArrayList<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject item = messages.getJSONObject(i);
            if ("system".equals(item.optString("role", "")) && system == null) {
                system = item;
            } else {
                turns.add(item);
            }
        }

        JSONArray trimmed = new JSONArray();
        if (system != null) {
            trimmed.put(system);
        }
        for (int i = turns.size() - 1; i >= 0; i--) {
            JSONArray trial = new JSONArray();
            if (system != null) {
                trial.put(system);
            }
            for (int j = i; j < turns.size(); j++) {
                trial.put(turns.get(j));
            }
            if (estimateTokens(trial) <= maxTokens) {
                trimmed = trial;
                break;
            }
        }
        if (trimmed.length() == 0 && !turns.isEmpty()) {
            trimmed.put(turns.get(turns.size() - 1));
        }
        return trimmed;
    }
}
