package org.libreoffice.androidlib.ai;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class AiRequestManager {
    private static final String TAG = "AiRequestManager";

    public interface Callback {
        String sanitizePayload(String requestId, Object raw, String stage);

        void onStreamingState(String requestId);

        void onStreamDelta(String requestId, String delta) throws JSONException;

        void onDone(String requestId, String fullText) throws JSONException;

        void onError(String requestId, String code, String message);
    }

    /** 图片生成回调（非流式，返回 base64 列表） */
    public interface ImageGenCallback {
        void onImages(List<String> base64List);
        void onError(String code, String message);
    }

    public void execute(String requestId, String endpoint, String apiKey, String model, JSONArray messages,
            AiRequestSession session, Callback callback) {
        execute(requestId, endpoint, apiKey, model, messages, null, session, callback);
    }

    public void execute(String requestId, String endpoint, String apiKey, String model, JSONArray messages,
            AiModelConfigStore.SamplingParams sampling, AiRequestSession session, Callback callback) {
        HttpURLConnection connection = null;
        StringBuilder fullText = new StringBuilder();
        StringBuilder nonSseResponse = new StringBuilder();
        boolean sawSsePayload = false;
        boolean sawCompleteMessage = false;
        java.util.concurrent.atomic.AtomicBoolean terminal = new java.util.concurrent.atomic.AtomicBoolean(false);
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            session.bindConnection(connection);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "text/event-stream");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            }

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", true);
            body.put("messages", messages);
            if (sampling != null) {
                AiModelConfigStore.applySamplingToBody(body, sampling);
            }

            byte[] requestBody = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(requestBody);

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String errorText = readStreamAsText(connection.getErrorStream());
                finishError(session, terminal, callback, requestId, "http_" + statusCode,
                        errorText.isEmpty() ? "AI request failed" : limitErrorText(errorText));
                return;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (session.isCancelled()) {
                    return;
                }

                line = line.trim();
                if (!line.startsWith("data:")) {
                    if (!sawSsePayload && !line.startsWith(":")) {
                        if (nonSseResponse.length() > 0) nonSseResponse.append('\n');
                        nonSseResponse.append(line);
                    }
                    continue;
                }

                String data = line.substring("data:".length()).trim();
                if (data.isEmpty()) {
                    continue;
                }
                sawSsePayload = true;

                if ("[DONE]".equals(data)) {
                    finishDone(session, terminal, callback, requestId, fullText.toString());
                    return;
                }

                try {
                    JSONObject chunk = new JSONObject(data);
                    JSONObject providerError = chunk.optJSONObject("error");
                    if (providerError != null) {
                        finishError(session, terminal, callback, requestId, "provider_error",
                                providerError.optString("message", "AI provider returned an error"));
                        return;
                    }
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) {
                        continue;
                    }

                    JSONObject choice = choices.optJSONObject(0);
                    if (choice == null) {
                        continue;
                    }

                    Object finishReasonValue = choice.opt("finish_reason");
                    String finishReason = finishReasonValue instanceof String
                            ? (String) finishReasonValue : "";

                    Object deltaRaw = null;
                    JSONObject deltaObj = choice.optJSONObject("delta");
                    if (deltaObj != null && deltaObj.has("content")) {
                        deltaRaw = deltaObj.opt("content");
                    }
                    if (deltaRaw == null) {
                        JSONObject messageObj = choice.optJSONObject("message");
                        if (messageObj != null && messageObj.has("content")) {
                            Object messageRaw = messageObj.opt("content");
                            String messageText = callback.sanitizePayload(requestId, messageRaw, "stream_message");
                            if (!messageText.isEmpty()) {
                                if (fullText.length() == 0) {
                                    deltaRaw = messageRaw;
                                } else {
                                    fullText.setLength(0);
                                    fullText.append(messageText);
                                    sawCompleteMessage = true;
                                }
                            }
                        }
                    }
                    if (deltaRaw == null && choice.has("text")) {
                        deltaRaw = choice.opt("text");
                    }
                    String delta = callback.sanitizePayload(requestId, deltaRaw, "stream_delta");
                    if (!delta.isEmpty() && !sawCompleteMessage) {
                        if (session.shouldEmitStreamingState()) {
                            callback.onStreamingState(requestId);
                        }
                        fullText.append(delta);
                        callback.onStreamDelta(requestId, delta);
                    }
                    if (!finishReason.isEmpty()) {
                        if ("length".equals(finishReason)) {
                            Log.w(TAG, "response_truncated requestId=" + requestId + " chars=" + fullText.length());
                        }
                        finishDone(session, terminal, callback, requestId, fullText.toString());
                        return;
                    }
                } catch (JSONException parseError) {
                    Log.w(TAG, "Skipping unparsable SSE chunk requestId=" + requestId, parseError);
                }
            }

            if (!session.isCancelled() && !terminal.get()) {
                if (!sawSsePayload && nonSseResponse.length() > 0) {
                    parseNonStreamingResponse(requestId, nonSseResponse.toString(), callback, terminal, fullText, session);
                }
                if (!terminal.get()) {
                    if (fullText.length() == 0) {
                        finishError(session, terminal, callback, requestId, "empty_response", "AI 未返回可用内容，请重试");
                    } else {
                        finishDone(session, terminal, callback, requestId, fullText.toString());
                    }
                }
            }
        } catch (Exception e) {
            if (!session.isCancelled()) {
                finishError(session, terminal, callback, requestId, "request_failed",
                        e.getMessage() == null ? "AI request failed" : e.getMessage());
                Log.e(TAG, "execute failed requestId=" + requestId, e);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void parseNonStreamingResponse(String requestId, String body, Callback callback,
            java.util.concurrent.atomic.AtomicBoolean terminal, StringBuilder fullText,
            AiRequestSession session) throws JSONException {
        if (session.isCancelled()) {
            return;
        }
        JSONObject response;
        try {
            response = new JSONObject(body);
        } catch (JSONException e) {
            finishError(session, terminal, callback, requestId, "invalid_response", "AI 返回内容无法解析，请重试");
            return;
        }
        JSONObject providerError = response.optJSONObject("error");
        if (providerError != null) {
            finishError(session, terminal, callback, requestId, "provider_error",
                    providerError.optString("message", "AI provider returned an error"));
            return;
        }
        JSONArray choices = response.optJSONArray("choices");
        JSONObject choice = choices != null && choices.length() > 0 ? choices.optJSONObject(0) : null;
        JSONObject message = choice == null ? null : choice.optJSONObject("message");
        Object raw = message != null ? message.opt("content") : choice == null ? null : choice.opt("text");
        String content = callback.sanitizePayload(requestId, raw, "done_payload");
        if (content.isEmpty()) {
            finishError(session, terminal, callback, requestId, "empty_response", "AI 未返回可用内容，请重试");
            return;
        }
        fullText.setLength(0);
        fullText.append(content);
        finishDone(session, terminal, callback, requestId, fullText.toString());
    }

    private void finishDone(AiRequestSession session, java.util.concurrent.atomic.AtomicBoolean terminal, Callback callback,
            String requestId, String text) throws JSONException {
        if (session.isCancelled()) {
            return;
        }
        String sanitized = callback.sanitizePayload(requestId, text, "done_payload");
        if (sanitized.isEmpty()) {
            finishError(session, terminal, callback, requestId, "empty_response", "AI 未返回可用内容，请重试");
        } else if (!session.isCancelled() && terminal.compareAndSet(false, true) && !session.isCancelled()) {
            callback.onDone(requestId, sanitized);
        }
    }

    private void finishError(AiRequestSession session, java.util.concurrent.atomic.AtomicBoolean terminal, Callback callback,
            String requestId, String code, String message) {
        if (!session.isCancelled() && terminal.compareAndSet(false, true) && !session.isCancelled()) {
            callback.onError(requestId, code, message == null || message.isEmpty() ? "AI request failed" : message);
        }
    }

    private String limitErrorText(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    private String readStreamAsText(InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        } catch (IOException e) {
            return "";
        }
    }

    /** 非流式图片生成请求（OpenAI images/generations 兼容）。返回 data[].b64_json 列表。 */
    public void executeImageGen(String endpoint, String apiKey, String model, String prompt,
            String size, int count, AiRequestSession session, ImageGenCallback callback) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            session.bindConnection(connection);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            }

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("size", size);
            body.put("response_format", "b64_json");
            body.put("n", count);

            byte[] requestBody = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(requestBody);

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String errorText = readStreamAsText(connection.getErrorStream());
                callback.onError("http_" + statusCode, errorText.isEmpty() ? "图片生成失败" : errorText);
                return;
            }

            String responseText = readStreamAsText(connection.getInputStream());
            if (session.isCancelled()) {
                return;
            }
            JSONObject response = new JSONObject(responseText);
            JSONArray dataArray = response.optJSONArray("data");
            if (dataArray == null || dataArray.length() == 0) {
                String errMsg = response.optString("err_message", "");
                callback.onError("empty_data", errMsg.isEmpty() ? "图片生成返回为空" : errMsg);
                return;
            }
            List<String> base64List = new ArrayList<>();
            for (int i = 0; i < dataArray.length(); i++) {
                if (session.isCancelled()) {
                    return;
                }
                JSONObject item = dataArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String b64 = item.optString("b64_json", "");
                if (!b64.isEmpty()) {
                    base64List.add(b64);
                }
            }
            if (base64List.isEmpty()) {
                callback.onError("no_b64", "返回数据未包含 b64_json 字段");
                return;
            }
            callback.onImages(base64List);
        } catch (Exception e) {
            if (!session.isCancelled()) {
                callback.onError("image_gen_failed", e.getMessage() == null ? "图片生成失败" : e.getMessage());
                Log.e(TAG, "executeImageGen failed", e);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
