package org.libreoffice.androidapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetches model IDs from an OpenAI-compatible provider and lets users enter custom IDs. */
final class AiModelPicker {
    private static final String MANUAL_OPTION = "手动填写其他模型名称…";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AiModelPicker() {}

    static void bind(Context context, EditText modelInput, EditText urlInput,
            EditText providerInput, EditText apiKeyInput) {
        if (modelInput == null || urlInput == null) {
            return;
        }
        modelInput.setFocusable(false);
        modelInput.setFocusableInTouchMode(false);
        modelInput.setCursorVisible(false);
        modelInput.setOnClickListener(view -> fetchModels(context, modelInput, urlInput,
                providerInput, apiKeyInput));
        urlInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isCompleteBaseUrl(s == null ? "" : s.toString())) {
                    setUrlError(urlInput, null);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    static boolean validateBaseUrl(EditText urlInput) {
        String value = urlInput == null || urlInput.getText() == null
                ? "" : urlInput.getText().toString().trim();
        if (isCompleteBaseUrl(value)) {
            setUrlError(urlInput, null);
            return true;
        }
        setUrlError(urlInput, "请输入完整的 http:// 或 https:// 接口地址");
        if (urlInput != null) {
            urlInput.requestFocus();
        }
        return false;
    }

    static boolean isCompleteBaseUrl(String value) {
        if (value == null || value.trim().isEmpty() || value.matches(".*\\s+.*")) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void setUrlError(EditText urlInput, String message) {
        if (urlInput == null) {
            return;
        }
        urlInput.setError(message);
        int background = message == null ? org.libreoffice.androidapp.R.drawable.bg_ai_input_field
                : org.libreoffice.androidapp.R.drawable.bg_ai_input_field_error;
        int left = urlInput.getPaddingLeft();
        int top = urlInput.getPaddingTop();
        int right = urlInput.getPaddingRight();
        int bottom = urlInput.getPaddingBottom();
        urlInput.setBackgroundResource(background);
        urlInput.setPadding(left, top, right, bottom);
    }

    private static void fetchModels(Context context, EditText modelInput, EditText urlInput,
            EditText providerInput, EditText apiKeyInput) {
        String endpoint = urlInput.getText() == null ? "" : urlInput.getText().toString().trim();
        if (!isCompleteBaseUrl(endpoint)) {
            validateBaseUrl(urlInput);
            new AlertDialog.Builder(context)
                    .setTitle("接口地址不完整")
                    .setMessage("请先填写完整的 http:// 或 https:// 接口地址，再获取模型列表。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        String provider = providerInput == null || providerInput.getText() == null
                ? "" : providerInput.getText().toString().trim();
        String apiKey = apiKeyInput == null || apiKeyInput.getText() == null
                ? "" : apiKeyInput.getText().toString().trim();
        ProgressDialog progress = ProgressDialog.show(context, null, "正在获取模型列表…", true, false);
        EXECUTOR.execute(() -> {
            List<String> models = null;
            String failure = null;
            try {
                models = requestModels(provider, endpoint, apiKey);
                if (models.isEmpty()) {
                    failure = "接口没有返回可选模型";
                }
            } catch (Exception e) {
                failure = e instanceof IllegalStateException && e.getMessage() != null
                        ? e.getMessage() : "网络连接失败";
            }
            final List<String> result = models;
            final String error = failure;
            MAIN.post(() -> {
                if (progress.isShowing()) {
                    progress.dismiss();
                }
                if (!isContextAvailable(context)) {
                    return;
                }
                if (error != null) {
                    showFetchError(context, modelInput, error);
                } else {
                    showModelChoices(context, modelInput, result);
                }
            });
        });
    }

    private static boolean isContextAvailable(Context context) {
        return !(context instanceof Activity) || (!((Activity) context).isFinishing()
                && !((Activity) context).isDestroyed());
    }

    private static void showFetchError(Context context, EditText modelInput, String error) {
        String message = "获取失败（" + error + "）。请检查接口 URL 是否完整可用，并确认 API Key 正确；"
                + "支持免 Key 的服务可以留空。也可以手动输入模型名称。";
        new AlertDialog.Builder(context)
                .setTitle("无法获取模型列表")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("手动填写", (dialog, which) -> showManualEntry(context, modelInput))
                .show();
    }

    private static void showModelChoices(Context context, EditText modelInput, List<String> models) {
        String[] choices = new String[models.size() + 1];
        for (int i = 0; i < models.size(); i++) {
            choices[i] = models.get(i);
        }
        choices[choices.length - 1] = MANUAL_OPTION;
        new AlertDialog.Builder(context)
                .setTitle("选择模型")
                .setItems(choices, (dialog, which) -> {
                    if (which == choices.length - 1) {
                        showManualEntry(context, modelInput);
                    } else {
                        modelInput.setText(choices[which]);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static void showManualEntry(Context context, EditText modelInput) {
        EditText manualInput = new EditText(context);
        manualInput.setSingleLine(true);
        manualInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        manualInput.setHint("输入模型名称");
        if (modelInput.getText() != null) {
            manualInput.setText(modelInput.getText());
            manualInput.setSelection(manualInput.length());
        }
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        manualInput.setPadding(padding, padding / 2, padding, padding / 2);
        new AlertDialog.Builder(context)
                .setTitle("手动填写模型名称")
                .setView(manualInput)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String value = manualInput.getText() == null ? ""
                            : manualInput.getText().toString().trim();
                    if (!value.isEmpty()) {
                        modelInput.setText(value);
                    }
                })
                .show();
    }

    private static List<String> requestModels(String provider, String endpoint, String apiKey)
            throws Exception {
        RequestSpec spec = buildRequestSpec(provider, endpoint);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(spec.url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            String key = apiKey == null ? "" : apiKey.trim();
            if (key.length() > 0) {
                if (spec.apiKeyHeader != null) {
                    connection.setRequestProperty(spec.apiKeyHeader, key);
                    if ("x-api-key".equalsIgnoreCase(spec.apiKeyHeader)) {
                        connection.setRequestProperty("anthropic-version", "2023-06-01");
                    }
                } else if (spec.useBearer) {
                    connection.setRequestProperty("Authorization", "Bearer " + key);
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status);
            }
            String body;
            try (InputStream input = connection.getInputStream()) {
                body = readBody(input);
            }
            return parseModels(new JSONObject(body));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static RequestSpec buildRequestSpec(String provider, String endpoint) throws Exception {
        URI original = new URI(endpoint);
        String host = original.getHost() == null ? "" : original.getHost().toLowerCase(Locale.ROOT);
        String path = original.getRawPath() == null ? "" : original.getRawPath();
        String normalizedProvider = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
        boolean azure = normalizedProvider.contains("azure") || host.contains("openai.azure.com");
        boolean anthropic = normalizedProvider.contains("anthropic") || host.contains("anthropic.com");
        boolean gemini = normalizedProvider.contains("gemini") || normalizedProvider.contains("google")
                || host.contains("generativelanguage.googleapis.com");
        boolean ollama = normalizedProvider.contains("ollama");
        String apiKeyHeader = null;
        boolean bearer = true;

        if (azure) {
            int deployments = path.toLowerCase(Locale.ROOT).indexOf("/openai/deployments/");
            String prefix = deployments >= 0 ? path.substring(0, deployments)
                    : path.endsWith("/openai") ? path.substring(0, path.length() - "/openai".length()) : path;
            path = trimTrailingSlash(prefix) + "/openai/models";
            String apiVersion = queryValue(original.getRawQuery(), "api-version");
            String query = "api-version=" + (apiVersion == null || apiVersion.isEmpty()
                    ? "2024-10-21" : apiVersion);
            apiKeyHeader = "api-key";
            bearer = false;
            return new RequestSpec(toUrl(original, path, query), apiKeyHeader, bearer);
        }
        if (anthropic) {
            if (!path.endsWith("/models")) {
                int messages = path.lastIndexOf("/messages");
                path = messages >= 0 ? path.substring(0, messages) + "/models"
                        : trimTrailingSlash(path) + "/v1/models";
            }
            apiKeyHeader = "x-api-key";
            bearer = false;
            return new RequestSpec(toUrl(original, path, null), apiKeyHeader, bearer);
        }
        if (gemini && host.contains("generativelanguage.googleapis.com")
                && !path.contains("/openai/")) {
            int version = path.indexOf("/v1beta");
            path = version >= 0 ? path.substring(0, version) + "/v1beta/models" : "/v1beta/models";
            apiKeyHeader = "x-goog-api-key";
            bearer = false;
            return new RequestSpec(toUrl(original, path, null), apiKeyHeader, bearer);
        }
        if (ollama && path.endsWith("/api/tags")) {
            return new RequestSpec(toUrl(original, path, null), null, true);
        }
        if (ollama && (path.endsWith("/api/chat") || path.endsWith("/api/generate"))) {
            path = trimTrailingSlash(path.substring(0, path.lastIndexOf("/api/"))) + "/api/tags";
            bearer = true; // Local Ollama ignores this when no key is supplied; cloud may require it.
            return new RequestSpec(toUrl(original, path, null), null, bearer);
        }
        if (path.endsWith("/models")) {
            // Keep an explicitly configured models endpoint.
        } else if (path.endsWith("/chat/completions")) {
            path = path.substring(0, path.length() - "/chat/completions".length()) + "/models";
        } else if (path.endsWith("/completions")) {
            path = path.substring(0, path.length() - "/completions".length()) + "/models";
        } else if (path.endsWith("/v1") || path.endsWith("/v1beta/openai")) {
            path += "/models";
        } else if (path.isEmpty() || "/".equals(path)) {
            path = gemini ? "/v1beta/openai/models" : "/v1/models";
        } else {
            path = trimTrailingSlash(path) + "/models";
        }
        return new RequestSpec(toUrl(original, path, original.getRawQuery()), null, true);
    }

    private static String trimTrailingSlash(String path) {
        while (path != null && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path == null ? "" : path;
    }

    private static String toUrl(URI original, String path, String query) {
        return original.getScheme() + "://" + original.getRawAuthority() + path
                + (query == null || query.isEmpty() ? "" : "?" + query);
    }

    private static String queryValue(String query, String key) {
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && key.equalsIgnoreCase(pair[0])) {
                try {
                    return URLDecoder.decode(pair[1], "UTF-8");
                } catch (Exception ignored) {
                    return pair[1];
                }
            }
        }
        return null;
    }

    private static String readBody(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
            if (output.size() > 2 * 1024 * 1024) {
                throw new IllegalStateException("响应内容过大");
            }
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static List<String> parseModels(JSONObject response) {
        JSONArray entries = response.optJSONArray("data");
        if (entries == null) {
            entries = response.optJSONArray("models");
        }
        List<String> models = new ArrayList<>();
        if (entries == null) {
            return models;
        }
        for (int i = 0; i < entries.length(); i++) {
            Object item = entries.opt(i);
            String name = null;
            if (item instanceof String) {
                name = ((String) item).trim();
            } else if (item instanceof JSONObject) {
                JSONObject model = (JSONObject) item;
                name = firstNonEmpty(model.optString("id", ""), model.optString("name", ""),
                        model.optString("model", ""));
                if (name.startsWith("models/")) {
                    name = name.substring("models/".length());
                }
            }
            if (name != null && !name.isEmpty() && !models.contains(name)) {
                models.add(name);
            }
        }
        return models;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class RequestSpec {
        final String url;
        final String apiKeyHeader;
        final boolean useBearer;

        RequestSpec(String url, String apiKeyHeader, boolean useBearer) {
            this.url = url;
            this.apiKeyHeader = apiKeyHeader;
            this.useBearer = useBearer;
        }
    }
}
