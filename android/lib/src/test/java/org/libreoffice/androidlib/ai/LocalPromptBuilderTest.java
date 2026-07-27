package org.libreoffice.androidlib.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class LocalPromptBuilderTest {
    private static JSONObject msg(String role, String content) throws JSONException {
        return new JSONObject().put("role", role).put("content", content);
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Test
    public void truncatesLongHistoryToFitContext() throws JSONException {
        JSONArray history = new JSONArray();
        history.put(msg("system", "sys"));
        for (int i = 0; i < 50; i++) {
            history.put(msg("user", repeat('x', 500)));
            history.put(msg("assistant", repeat('y', 500)));
        }
        JSONArray out = LocalPromptBuilder.buildPrompt(history, 2048, 512, true);
        int est = LocalPromptBuilder.estimateTokens(out);
        assertTrue(est <= 2048 - 512 - LocalPromptBuilder.SAFETY_MARGIN);
        JSONObject last = out.getJSONObject(out.length() - 1);
        assertEquals("user", last.getString("role"));
    }
}
