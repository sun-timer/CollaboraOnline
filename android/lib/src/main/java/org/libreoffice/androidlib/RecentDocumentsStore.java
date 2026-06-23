package org.libreoffice.androidlib;

import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Recent / recently-closed document URIs stored in explorer prefs. */
public final class RecentDocumentsStore {
    public static final String RECENTLY_CLOSED_KEY = "RECENTLY_CLOSED_LIST";
    private static final int MAX_RECENT = 30;
    private static final int MAX_CLOSED = 30;

    private RecentDocumentsStore() {
    }

    public static List<String> getRecentUris(SharedPreferences prefs) {
        return parseList(prefs.getString(LOActivity.RECENT_DOCUMENTS_KEY, ""));
    }

    public static List<String> getRecentlyClosedUris(SharedPreferences prefs) {
        return parseList(prefs.getString(RECENTLY_CLOSED_KEY, ""));
    }

    public static void prependRecent(SharedPreferences prefs, String uri) {
        if (TextUtils.isEmpty(uri)) {
            return;
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        items.add(uri);
        for (String existing : getRecentUris(prefs)) {
            if (!uri.equals(existing)) {
                items.add(existing);
            }
        }
        writeList(prefs, LOActivity.RECENT_DOCUMENTS_KEY, items, MAX_RECENT);
    }

    public static void removeRecent(SharedPreferences prefs, String uri) {
        if (TextUtils.isEmpty(uri)) {
            return;
        }
        LinkedHashSet<String> items = new LinkedHashSet<>(getRecentUris(prefs));
        if (!items.remove(uri)) {
            return;
        }
        writeList(prefs, LOActivity.RECENT_DOCUMENTS_KEY, items, MAX_RECENT);
    }

    public static void moveToRecentlyClosed(SharedPreferences prefs, String uri) {
        if (TextUtils.isEmpty(uri)) {
            return;
        }
        removeRecent(prefs, uri);
        LinkedHashSet<String> closed = new LinkedHashSet<>();
        closed.add(uri);
        for (String existing : getRecentlyClosedUris(prefs)) {
            if (!uri.equals(existing)) {
                closed.add(existing);
            }
        }
        writeList(prefs, RECENTLY_CLOSED_KEY, closed, MAX_CLOSED);
    }

    public static void restoreFromRecentlyClosed(SharedPreferences prefs, String uri) {
        if (TextUtils.isEmpty(uri)) {
            return;
        }
        LinkedHashSet<String> closed = new LinkedHashSet<>(getRecentlyClosedUris(prefs));
        closed.remove(uri);
        writeList(prefs, RECENTLY_CLOSED_KEY, closed, MAX_CLOSED);
        prependRecent(prefs, uri);
    }

    private static List<String> parseList(String joined) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(joined)) {
            return out;
        }
        for (String line : joined.split("\n", 0)) {
            if (!TextUtils.isEmpty(line)) {
                out.add(line.trim());
            }
        }
        return out;
    }

    private static void writeList(
            SharedPreferences prefs,
            String key,
            LinkedHashSet<String> items,
            int maxItems) {
        StringBuilder joined = new StringBuilder();
        int count = 0;
        for (String item : items) {
            if (count >= maxItems) {
                break;
            }
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(item);
            count++;
        }
        prefs.edit().putString(key, joined.toString()).apply();
    }

    public static String safeUriString(Uri uri) {
        return uri == null ? "" : uri.toString();
    }
}
