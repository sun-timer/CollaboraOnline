package org.libreoffice.androidlib.ai;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AiConversationStore {
    private static final String TAG = "AiConversationStore";
    private static final String AI_MODE_DOC_QA = "doc_qa";
    private static final String AI_MODE_CHAT = "chat";
    private static final String AI_HISTORY_DIR = "ai_history";

    private final Context context;
    private final URI documentUri;
    private final String urlToLoad;
    private final long loadDocumentMillis;
    private String documentKeyCache = "";

    public AiConversationStore(Context context, URI documentUri, String urlToLoad, long loadDocumentMillis) {
        this.context = context;
        this.documentUri = documentUri;
        this.urlToLoad = urlToLoad;
        this.loadDocumentMillis = loadDocumentMillis;
    }

    public JSONArray loadHistory(String mode) {
        File file = getHistoryFile(mode);
        if (!file.exists()) {
            return new JSONArray();
        }
        try (FileInputStream inputStream = new FileInputStream(file);
                InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
            if (builder.length() == 0) {
                return new JSONArray();
            }
            return new JSONArray(builder.toString());
        } catch (Exception e) {
            Log.w(TAG, "loadHistory failed for mode=" + mode, e);
            return new JSONArray();
        }
    }

    public void saveHistory(String mode, JSONArray history) {
        File file = getHistoryFile(mode);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            Log.w(TAG, "saveHistory failed to mkdirs for " + parent.getAbsolutePath());
            return;
        }
        try (FileOutputStream outputStream = new FileOutputStream(file, false);
                OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                BufferedWriter bufferedWriter = new BufferedWriter(writer)) {
            bufferedWriter.write(history.toString());
            bufferedWriter.flush();
        } catch (Exception e) {
            Log.e(TAG, "saveHistory failed for mode=" + mode, e);
        }
    }

    public void appendHistoryMessage(String mode, JSONArray history, String role, String content) throws JSONException {
        JSONObject entry = new JSONObject();
        entry.put("role", role);
        entry.put("content", content);
        history.put(entry);
        saveHistory(mode, history);
    }

    public void clearHistoriesForCurrentDocument() {
        File docQaFile = getHistoryFile(AI_MODE_DOC_QA);
        File chatFile = getHistoryFile(AI_MODE_CHAT);
        if (docQaFile.exists() && !docQaFile.delete()) {
            Log.w(TAG, "Failed to delete ai history file: " + docQaFile.getAbsolutePath());
        }
        if (chatFile.exists() && !chatFile.delete()) {
            Log.w(TAG, "Failed to delete ai history file: " + chatFile.getAbsolutePath());
        }
    }

    private File getHistoryFile(String mode) {
        String suffix = AI_MODE_DOC_QA.equals(mode) ? "docqa" : "chat";
        return new File(new File(context.getFilesDir(), AI_HISTORY_DIR), getDocumentKey() + "." + suffix + ".json");
    }

    private String getDocumentKey() {
        if (documentKeyCache != null && !documentKeyCache.isEmpty()) {
            return documentKeyCache;
        }
        String raw = "";
        if (documentUri != null) {
            raw = documentUri.toString();
        }
        if ((raw == null || raw.isEmpty()) && urlToLoad != null) {
            raw = urlToLoad;
        }
        if (raw == null || raw.isEmpty()) {
            raw = "doc-" + loadDocumentMillis;
        }
        documentKeyCache = sha256Hex(raw);
        return documentKeyCache;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
