package org.libreoffice.androidlib.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.libreoffice.androidlib.LOActivity;

/** 云端模型配置读写（EXPLORER_PREFS）及 chat/completions 采样参数。 */
public final class AiModelConfigStore {
    public static final int MODEL_BASE = 0;
    public static final int MODEL_THINK = 1;
    public static final int MODEL_IMAGE = 2;
    public static final int MODEL_VISION = 3;

    public static final String FIELD_CONFIG_NAME = "config_name";
    public static final String FIELD_PROVIDER = "provider";
    public static final String FIELD_URL = "url";
    public static final String FIELD_API_KEY = "api_key";
    public static final String FIELD_MODEL_NAME = "model_name";
    public static final String FIELD_TOP_P = "top_p";
    public static final String FIELD_TEMPERATURE = "temperature";
    public static final String FIELD_PRESENCE_PENALTY = "presence_penalty";
    public static final String FIELD_FREQUENCY_PENALTY = "frequency_penalty";
    public static final String FIELD_MAX_TOKENS_RATIO = "max_tokens_ratio";
    public static final String FIELD_SEED_RATIO = "seed_ratio";

    private static final int MAX_TOKENS_CAP = 8192;
    private static final int MAX_SEED = 999_999;

    public static final class Form {
        public String configName = "";
        public String provider = "";
        public String url = "";
        public String apiKey = "";
        public String modelName = "";
        public float topP = 0.5f;
        public float temperature = 0.9f;
        public float presencePenalty = 0f;
        public float frequencyPenalty = 0.8f;
        public float maxTokensRatio = 0.8f;
        public float seedRatio = 0.8f;
    }

    public static final class SamplingParams {
        public final float topP;
        public final float temperature;
        public final float presencePenalty;
        public final float frequencyPenalty;
        public final int maxTokens;
        public final Integer seed;

        SamplingParams(float topP, float temperature, float presencePenalty, float frequencyPenalty,
                int maxTokens, Integer seed) {
            this.topP = topP;
            this.temperature = temperature;
            this.presencePenalty = presencePenalty;
            this.frequencyPenalty = frequencyPenalty;
            this.maxTokens = maxTokens;
            this.seed = seed;
        }
    }

    private AiModelConfigStore() {}

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(LOActivity.EXPLORER_PREFS_KEY, Context.MODE_PRIVATE);
    }

    public static String modelPrefix(int modelType) {
        switch (modelType) {
            case MODEL_THINK:
                return "AI_MODEL_THINK";
            case MODEL_IMAGE:
                return "AI_MODEL_IMAGE";
            case MODEL_VISION:
                return "AI_MODEL_VISION";
            case MODEL_BASE:
            default:
                return "AI_MODEL_BASE";
        }
    }

    public static String modelKey(int modelType, String field) {
        return modelPrefix(modelType) + "_" + field;
    }

    public static void ensureDefaults(Context context, int modelType, String defaultConfigName,
            String defaultModelName) {
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor e = p.edit();
        putStringIfMissing(e, p, modelType, FIELD_CONFIG_NAME, defaultConfigName);
        putStringIfMissing(e, p, modelType, FIELD_PROVIDER, "OpenAI");
        putStringIfMissing(e, p, modelType, FIELD_URL, "https://api.openai.com/v1/chat/completions");
        putStringIfMissing(e, p, modelType, FIELD_API_KEY, "");
        putStringIfMissing(e, p, modelType, FIELD_MODEL_NAME, defaultModelName);
        putFloatIfMissing(e, p, modelType, FIELD_TOP_P, 0.5f);
        putFloatIfMissing(e, p, modelType, FIELD_TEMPERATURE, 0.9f);
        putFloatIfMissing(e, p, modelType, FIELD_PRESENCE_PENALTY, 0.0f);
        putFloatIfMissing(e, p, modelType, FIELD_FREQUENCY_PENALTY, 0.8f);
        putFloatIfMissing(e, p, modelType, FIELD_MAX_TOKENS_RATIO, 0.8f);
        putFloatIfMissing(e, p, modelType, FIELD_SEED_RATIO, 0.8f);
        e.commit();
    }

    public static Form loadForm(Context context, int modelType, String defaultConfigName,
            String defaultModelName) {
        SharedPreferences p = prefs(context);
        Form form = new Form();
        form.configName = p.getString(modelKey(modelType, FIELD_CONFIG_NAME), defaultConfigName);
        form.provider = p.getString(modelKey(modelType, FIELD_PROVIDER), "OpenAI");
        form.url = p.getString(modelKey(modelType, FIELD_URL), "https://api.openai.com/v1/chat/completions");
        form.apiKey = p.getString(modelKey(modelType, FIELD_API_KEY), "");
        form.modelName = p.getString(modelKey(modelType, FIELD_MODEL_NAME), defaultModelName);
        form.topP = p.getFloat(modelKey(modelType, FIELD_TOP_P), 0.5f);
        form.temperature = p.getFloat(modelKey(modelType, FIELD_TEMPERATURE), 0.9f);
        form.presencePenalty = p.getFloat(modelKey(modelType, FIELD_PRESENCE_PENALTY), 0f);
        form.frequencyPenalty = p.getFloat(modelKey(modelType, FIELD_FREQUENCY_PENALTY), 0.8f);
        form.maxTokensRatio = p.getFloat(modelKey(modelType, FIELD_MAX_TOKENS_RATIO), 0.8f);
        form.seedRatio = p.getFloat(modelKey(modelType, FIELD_SEED_RATIO), 0.8f);
        return form;
    }

    public static boolean saveForm(Context context, int modelType, Form form) {
        if (form == null) {
            return false;
        }
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString(modelKey(modelType, FIELD_CONFIG_NAME), safeTrim(form.configName));
        editor.putString(modelKey(modelType, FIELD_PROVIDER), safeTrim(form.provider));
        editor.putString(modelKey(modelType, FIELD_URL), safeTrim(form.url));
        editor.putString(modelKey(modelType, FIELD_API_KEY), safeTrim(form.apiKey));
        editor.putString(modelKey(modelType, FIELD_MODEL_NAME), safeTrim(form.modelName));
        editor.putFloat(modelKey(modelType, FIELD_TOP_P), clampRatio(form.topP));
        editor.putFloat(modelKey(modelType, FIELD_TEMPERATURE), clampRatio(form.temperature));
        editor.putFloat(modelKey(modelType, FIELD_PRESENCE_PENALTY), clampRatio(form.presencePenalty));
        editor.putFloat(modelKey(modelType, FIELD_FREQUENCY_PENALTY), clampRatio(form.frequencyPenalty));
        editor.putFloat(modelKey(modelType, FIELD_MAX_TOKENS_RATIO), clampRatio(form.maxTokensRatio));
        editor.putFloat(modelKey(modelType, FIELD_SEED_RATIO), clampRatio(form.seedRatio));
        return editor.commit();
    }

    public static SamplingParams loadSamplingParams(Context context, int modelType) {
        SharedPreferences p = prefs(context);
        float topP = p.getFloat(modelKey(modelType, FIELD_TOP_P), 0.5f);
        float temperature = p.getFloat(modelKey(modelType, FIELD_TEMPERATURE), 0.9f);
        float presencePenalty = p.getFloat(modelKey(modelType, FIELD_PRESENCE_PENALTY), 0f);
        float frequencyPenalty = p.getFloat(modelKey(modelType, FIELD_FREQUENCY_PENALTY), 0.8f);
        float maxTokensRatio = p.getFloat(modelKey(modelType, FIELD_MAX_TOKENS_RATIO), 0.8f);
        float seedRatio = p.getFloat(modelKey(modelType, FIELD_SEED_RATIO), 0.8f);
        int maxTokens = Math.max(1, Math.round(clampRatio(maxTokensRatio) * MAX_TOKENS_CAP));
        Integer seed = seedRatio <= 0.001f ? null : Math.max(1, Math.round(clampRatio(seedRatio) * MAX_SEED));
        return new SamplingParams(clampRatio(topP), clampRatio(temperature), clampRatio(presencePenalty),
                clampRatio(frequencyPenalty), maxTokens, seed);
    }

    public static int resolveModelTypeFromMode(String modelMode) {
        if ("vision".equalsIgnoreCase(modelMode)) {
            return MODEL_VISION;
        }
        if ("think".equalsIgnoreCase(modelMode)) {
            return MODEL_THINK;
        }
        if ("image".equalsIgnoreCase(modelMode)) {
            return MODEL_IMAGE;
        }
        return MODEL_BASE;
    }

    public static void applySamplingToBody(JSONObject body, SamplingParams params) throws JSONException {
        if (body == null || params == null) {
            return;
        }
        body.put("top_p", params.topP);
        body.put("temperature", params.temperature);
        body.put("presence_penalty", params.presencePenalty);
        body.put("frequency_penalty", params.frequencyPenalty);
        body.put("max_tokens", params.maxTokens);
        if (params.seed != null) {
            body.put("seed", params.seed);
        }
    }

    private static void putStringIfMissing(SharedPreferences.Editor e, SharedPreferences p, int modelType,
            String field, String value) {
        String key = modelKey(modelType, field);
        if (!p.contains(key)) {
            e.putString(key, value);
        }
    }

    private static void putFloatIfMissing(SharedPreferences.Editor e, SharedPreferences p, int modelType,
            String field, float value) {
        String key = modelKey(modelType, field);
        if (!p.contains(key)) {
            e.putFloat(key, value);
        }
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static float clampRatio(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
