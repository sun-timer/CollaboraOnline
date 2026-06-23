package org.libreoffice.androidlib.ai;

import android.util.Log;

import org.libreoffice.androidlib.lok.LokClipboardData;

public class AiDocumentContextProvider {
    private static final String TAG = "AiDocumentContextProvider";

    public interface Bridge {
        void postUnoCommand(String command, String args, boolean notify);

        void runOnUiThread(Runnable runnable);

        void copyViaWebsocketFallback();

        boolean getClipboardContent(LokClipboardData clipboardData);

        String normalizeAiText(String text);
    }

    private final Bridge bridge;

    public AiDocumentContextProvider(Bridge bridge) {
        this.bridge = bridge;
    }

    public String extractFullTextForDocQaFirstTurn(String requestId) {
        Log.i(TAG, "doc_qa_first_turn_extract_start requestId=" + requestId);
        try {
            // Prefer the core UNO path so Viewing/Editing UI state is not the primary dependency.
            bridge.postUnoCommand(".uno:SelectAll", "{}", false);
            Thread.sleep(120);
            bridge.postUnoCommand(".uno:Copy", "{}", false);

            // Keep the websocket route as a compatibility fallback for current Collabora mobile builds.
            bridge.runOnUiThread(bridge::copyViaWebsocketFallback);

            for (int i = 0; i < 24; i++) {
                Thread.sleep(i < 6 ? 120 : 220);
                LokClipboardData clipboardData = new LokClipboardData();
                if (!bridge.getClipboardContent(clipboardData)) {
                    continue;
                }
                String text = bridge.normalizeAiText(clipboardData.getText());
                if (!text.isEmpty()) {
                    Log.i(TAG, "doc_qa_first_turn_extract_success requestId=" + requestId);
                    return text;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "doc_qa_first_turn_extract_fail requestId=" + requestId, e);
            return "";
        }
        Log.w(TAG, "doc_qa_first_turn_extract_fail requestId=" + requestId + " reason=empty");
        return "";
    }
}
