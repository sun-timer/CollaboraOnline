package org.libreoffice.androidlib.ai;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PolishStyleRegistry {
    public static final class PolishStyle {
        public final String key;
        public final String label;

        public PolishStyle(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private static final PolishStyle[] ALL = {
            new PolishStyle(AiChatCoordinator.POLISH_STYLE_QUICK, "快速润色"),
            new PolishStyle(AiChatCoordinator.POLISH_STYLE_FORMAL, "更正式"),
            new PolishStyle(AiChatCoordinator.POLISH_STYLE_LIVELY, "更活泼"),
            new PolishStyle(AiChatCoordinator.POLISH_STYLE_PARTY_GOVT, "党政风"),
            new PolishStyle(AiChatCoordinator.POLISH_STYLE_COLLOQUIAL, "口语化"),
            new PolishStyle(AiChatCoordinator.POLISH_STYLE_ACADEMIC, "更学术"),
            new PolishStyle(AiChatCoordinator.POLISH_STYLE_INTERNET, "网络话术"),
    };

    private static final Map<String, PolishStyle> BY_KEY;

    static {
        Map<String, PolishStyle> map = new LinkedHashMap<>();
        for (PolishStyle style : ALL) {
            map.put(style.key, style);
        }
        BY_KEY = map;
    }

    private PolishStyleRegistry() {}

    public static PolishStyle[] getStyles() {
        return ALL.clone();
    }

    public static PolishStyle findByKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }

    public static PolishStyle getDefault() {
        return ALL[0];
    }
}
