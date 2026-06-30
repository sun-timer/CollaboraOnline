package org.libreoffice.androidlib.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TranslateLanguageRegistry {
    public static final class TranslateLanguage {
        public final String key;
        public final String label;
        public final boolean autoDetectable;

        public TranslateLanguage(String key, String label, boolean autoDetectable) {
            this.key = key;
            this.label = label;
            this.autoDetectable = autoDetectable;
        }
    }

    private static final TranslateLanguage[] ALL = {
            new TranslateLanguage(AiChatCoordinator.TRANSLATE_LANG_AUTO, "自动识别", true),
            new TranslateLanguage(AiChatCoordinator.TRANSLATE_LANG_ZH, "中文", false),
            new TranslateLanguage(AiChatCoordinator.TRANSLATE_LANG_EN, "English", false),
            new TranslateLanguage(AiChatCoordinator.TRANSLATE_LANG_JA, "日本語", false),
            new TranslateLanguage(AiChatCoordinator.TRANSLATE_LANG_KO, "한국어", false),
            new TranslateLanguage(AiChatCoordinator.TRANSLATE_LANG_FR, "Français", false),
            new TranslateLanguage(AiChatCoordinator.TRANSLATE_LANG_DE, "Deutsch", false),
            new TranslateLanguage(AiChatCoordinator.TRANSLATE_LANG_ES, "Español", false),
            new TranslateLanguage(AiChatCoordinator.TRANSLATE_LANG_RU, "Русский", false),
    };

    private static final Map<String, TranslateLanguage> BY_KEY;

    static {
        Map<String, TranslateLanguage> map = new LinkedHashMap<>();
        for (TranslateLanguage lang : ALL) {
            map.put(lang.key, lang);
        }
        BY_KEY = map;
    }

    private TranslateLanguageRegistry() {}

    public static TranslateLanguage[] getSourceLanguages() {
        return ALL.clone();
    }

    public static List<TranslateLanguage> getTargetLanguages() {
        List<TranslateLanguage> list = new ArrayList<>();
        for (TranslateLanguage lang : ALL) {
            if (!lang.autoDetectable) {
                list.add(lang);
            }
        }
        return list;
    }

    public static TranslateLanguage findByKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }

    public static TranslateLanguage getDefaultSource() {
        return ALL[0];
    }

    public static TranslateLanguage getDefaultTarget() {
        return BY_KEY.get(AiChatCoordinator.TRANSLATE_LANG_ZH);
    }
}
