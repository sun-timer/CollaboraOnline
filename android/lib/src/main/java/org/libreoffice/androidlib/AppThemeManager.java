package org.libreoffice.androidlib;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Shared night-mode handling for Orange Office (aligned with iOS {@code AppThemeManager}).
 * Values: {@link AppCompatDelegate#MODE_NIGHT_FOLLOW_SYSTEM} (-1),
 * {@link AppCompatDelegate#MODE_NIGHT_NO} (1), {@link AppCompatDelegate#MODE_NIGHT_YES} (2).
 */
public final class AppThemeManager {

    private static volatile boolean themeChangePending;

    private AppThemeManager() {
    }

    public static void applyStoredNightMode(Context context) {
        AppCompatDelegate.setDefaultNightMode(getStoredMode(context));
    }

    public static int getStoredMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                LOActivity.EXPLORER_PREFS_KEY, Context.MODE_PRIVATE);
        return prefs.getInt(LOActivity.NIGHT_MODE_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static void setMode(Context context, int mode) {
        context.getSharedPreferences(LOActivity.EXPLORER_PREFS_KEY, Context.MODE_PRIVATE)
                .edit()
                .putInt(LOActivity.NIGHT_MODE_KEY, mode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(mode);
        themeChangePending = true;
    }

    /** Call from {@link Activity#onResume()}; returns true when a settings theme change needs recreate. */
    public static boolean consumeThemeChangePending() {
        if (themeChangePending) {
            themeChangePending = false;
            return true;
        }
        return false;
    }

    public static boolean isDarkModeActive(Context context) {
        int mode = getStoredMode(context);
        switch (mode) {
            case AppCompatDelegate.MODE_NIGHT_YES:
                return true;
            case AppCompatDelegate.MODE_NIGHT_NO:
                return false;
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
            default:
                int nightMask = context.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                return nightMask == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    /** JS snippet to sync COOL darkTheme pref and re-apply core theme (document WebView). */
    public static String buildCoolDarkThemeSyncScript(boolean dark) {
        return "(function(){try{"
                + "if(window.prefs&&typeof window.prefs.set==='function'){"
                + "window.prefs.set('darkTheme'," + dark + ");"
                + "}"
                + "if(window.app&&app.map&&app.map.uiManager){"
                + "var um=app.map.uiManager;"
                + "if(typeof um.activateDarkModeInCore==='function'){um.activateDarkModeInCore(" + dark + ");}"
                + "if(typeof um.applyInvert==='function'){um.applyInvert();}"
                + "if(typeof um.setCanvasColorAfterModeChange==='function'){um.setCanvasColorAfterModeChange();}"
                + "}"
                + "}catch(e){}})();";
    }
}
