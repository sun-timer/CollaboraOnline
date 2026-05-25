package org.libreoffice.androidapp.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.libreoffice.androidapp.R;

public final class AiSettingsStore {
    private AiSettingsStore() {}

    public static final int MODEL_BASE = 0;
    public static final int MODEL_THINK = 1;
    public static final int MODEL_IMAGE = 2;
    public static final int MODEL_VISION = 3;

    public static final String EXTRA_MODEL_TYPE = "extra_model_type";

    public static final String KEY_PROFILE_NAME = "AI_PROFILE_NAME";
    public static final String KEY_PROFILE_AVATAR_URI = "AI_PROFILE_AVATAR_URI";

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

    public static final String RUNTIME_AI_ENDPOINT = "AI_OPENAI_ENDPOINT";
    public static final String RUNTIME_AI_API_KEY = "AI_OPENAI_API_KEY";
    public static final String RUNTIME_AI_MODEL = "AI_OPENAI_MODEL";

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(LibreOfficeUIActivity.EXPLORER_PREFS_KEY, Context.MODE_PRIVATE);
    }

    public static SharedPreferences runtimePrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
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

    public static int modelTitleRes(int modelType) {
        switch (modelType) {
            case MODEL_THINK:
                return R.string.ai_model_think;
            case MODEL_IMAGE:
                return R.string.ai_model_image;
            case MODEL_VISION:
                return R.string.ai_model_vision;
            case MODEL_BASE:
            default:
                return R.string.ai_model_basic;
        }
    }

    public static String modelKey(int modelType, String field) {
        return modelPrefix(modelType) + "_" + field;
    }

    public static void ensureDefaults(Context context, int modelType) {
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor e = p.edit();
        putIfMissing(e, p, modelType, FIELD_CONFIG_NAME, context.getString(modelTitleRes(modelType)) + "配置");
        putIfMissing(e, p, modelType, FIELD_PROVIDER, "OpenAI");
        putIfMissing(e, p, modelType, FIELD_URL, "https://api.openai.com/v1/chat/completions");
        putIfMissing(e, p, modelType, FIELD_API_KEY, "");
        putIfMissing(e, p, modelType, FIELD_MODEL_NAME, defaultModelName(modelType));
        putIfMissingFloat(e, p, modelType, FIELD_TOP_P, 0.5f);
        putIfMissingFloat(e, p, modelType, FIELD_TEMPERATURE, 0.9f);
        putIfMissingFloat(e, p, modelType, FIELD_PRESENCE_PENALTY, 0.0f);
        putIfMissingFloat(e, p, modelType, FIELD_FREQUENCY_PENALTY, 0.8f);
        putIfMissingFloat(e, p, modelType, FIELD_MAX_TOKENS_RATIO, 0.8f);
        putIfMissingFloat(e, p, modelType, FIELD_SEED_RATIO, 0.8f);
        e.apply();
    }

    private static void putIfMissing(SharedPreferences.Editor e, SharedPreferences p, int modelType, String field, String value) {
        String key = modelKey(modelType, field);
        if (!p.contains(key)) {
            e.putString(key, value);
        }
    }

    private static void putIfMissingFloat(SharedPreferences.Editor e, SharedPreferences p, int modelType, String field, float value) {
        String key = modelKey(modelType, field);
        if (!p.contains(key)) {
            e.putFloat(key, value);
        }
    }

    public static String defaultModelName(int modelType) {
        switch (modelType) {
            case MODEL_THINK:
                return "Orangepi-2.0-pro-exp";
            case MODEL_IMAGE:
                return "Orangepi-3.0-generate-001";
            case MODEL_VISION:
                return "Orangepi-2.5-flash";
            case MODEL_BASE:
            default:
                return "Orangepi-2.5-flash";
        }
    }

    public static String getModelDisplayName(Context context, int modelType, String unsetText) {
        SharedPreferences p = prefs(context);
        String name = p.getString(modelKey(modelType, FIELD_MODEL_NAME), "");
        if (name == null || name.trim().isEmpty()) {
            return unsetText;
        }
        return name.trim();
    }

    public static void syncBaseModelToRuntime(Context context) {
        SharedPreferences p = prefs(context);
        String endpoint = p.getString(modelKey(MODEL_BASE, FIELD_URL), "");
        String apiKey = p.getString(modelKey(MODEL_BASE, FIELD_API_KEY), "");
        String model = p.getString(modelKey(MODEL_BASE, FIELD_MODEL_NAME), "");

        SharedPreferences.Editor e = runtimePrefs(context).edit();
        if (endpoint != null) {
            if (endpoint.trim().isEmpty()) {
                e.remove(RUNTIME_AI_ENDPOINT);
            } else {
                e.putString(RUNTIME_AI_ENDPOINT, endpoint.trim());
            }
        }
        if (apiKey != null) {
            if (apiKey.trim().isEmpty()) {
                e.remove(RUNTIME_AI_API_KEY);
            } else {
                e.putString(RUNTIME_AI_API_KEY, apiKey.trim());
            }
        }
        if (model != null && !model.trim().isEmpty()) {
            e.putString(RUNTIME_AI_MODEL, model.trim());
        }
        e.apply();
    }
}
