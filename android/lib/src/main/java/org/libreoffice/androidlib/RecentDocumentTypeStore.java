/*
 * Persists LOKit document type per URI for home/recent list icons (strategy A).
 */

package org.libreoffice.androidlib;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Caches {@code app.map.getDocType()} results keyed by document URI string.
 * Home/recent UI prefers cached type over filename extension (BUG-001/002).
 */
public final class RecentDocumentTypeStore {

    private static final String PREFS_NAME = "recent_document_types";
    private static final String KEY_MAP = "uri_type_map";

    private RecentDocumentTypeStore() {
    }

    /** Save normalized type: text | spreadsheet | presentation | drawing. */
    public static void save(Context context, Uri uri, String jsDocTypeRaw) {
        if (context == null || uri == null) {
            return;
        }
        String normalized = normalizeJsDocType(jsDocTypeRaw);
        if (normalized == null) {
            return;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONObject map = readMap(prefs);
        try {
            map.put(uri.toString(), normalized);
            prefs.edit().putString(KEY_MAP, map.toString()).apply();
        } catch (JSONException ignored) {
            // keep previous map
        }
    }

    public static String getNormalizedType(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }
        JSONObject map = readMap(context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE));
        String value = map.optString(uri.toString(), null);
        return TextUtils.isEmpty(value) ? null : value;
    }

    static String normalizeJsDocType(String jsDocTypeRaw) {
        if (jsDocTypeRaw == null) {
            return null;
        }
        String trimmed = jsDocTypeRaw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if ("text".equals(trimmed) || "spreadsheet".equals(trimmed)
                || "presentation".equals(trimmed) || "drawing".equals(trimmed)) {
            return trimmed;
        }
        return null;
    }

    private static JSONObject readMap(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_MAP, "{}");
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }
}
