package org.libreoffice.androidapp;

import android.app.Application;

import org.libreoffice.androidlib.AppThemeManager;

/** Applies stored night mode before any Activity starts (BUG-013). */
public class OrangeOfficeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppThemeManager.applyStoredNightMode(this);
    }
}
