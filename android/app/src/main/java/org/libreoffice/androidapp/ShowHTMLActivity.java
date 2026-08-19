/* -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.androidapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.libreoffice.androidlib.SystemUiHelper;

/**
 * Full-screen viewer for bundled legal HTML / text assets (license, notice).
 */
public class ShowHTMLActivity extends AppCompatActivity {

    public static final String EXTRA_ASSET_PATH = "asset_path";
    /** @deprecated use {@link #EXTRA_ASSET_PATH} */
    public static final String LEGACY_EXTRA_PATH = "path";
    public static final String EXTRA_TITLE = "title";

    public static final String ASSET_LICENSE = "license.html";
    public static final String ASSET_NOTICE = "notice.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_html);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.showHtmlHeader), 0);
        SystemUiHelper.applyNavigationBarPadding(findViewById(R.id.showHtmlContent), 0);

        ImageButton backButton = findViewById(R.id.showHtmlBackButton);
        backButton.setOnClickListener(v -> finish());

        TextView titleView = findViewById(R.id.showHtmlTitle);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (TextUtils.isEmpty(title)) {
            titleView.setText(R.string.title_activity_show_html);
        } else {
            titleView.setText(title);
        }

        String assetPath = getIntent().getStringExtra(EXTRA_ASSET_PATH);
        if (TextUtils.isEmpty(assetPath)) {
            assetPath = getIntent().getStringExtra(LEGACY_EXTRA_PATH);
        }
        if (TextUtils.isEmpty(assetPath)) {
            finish();
            return;
        }

        WebView webView = findViewById(R.id.browser);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/" + assetPath);
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
