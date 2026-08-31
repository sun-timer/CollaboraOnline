package org.libreoffice.androidapp.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidapp.ShowHTMLActivity;
import org.libreoffice.androidlib.SystemUiHelper;

/** Full-screen About page (replaces legacy {@code AboutDialogFragment}). */
public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.aboutHeader), 0);
        int footerPad = getResources().getDimensionPixelSize(R.dimen.about_footer_padding_bottom);
        SystemUiHelper.applyNavigationBarPadding(findViewById(R.id.aboutFooter), footerPad);

        ImageButton backButton = findViewById(R.id.aboutBackButton);
        backButton.setOnClickListener(v -> finish());

        TextView versionRow = findViewById(R.id.aboutVersionRow);
        String versionName = getVersionName();
        if (versionName == null || versionName.isEmpty()) {
            versionRow.setText(R.string.about_version_info);
        } else {
            versionRow.setText(versionName);
        }

        View feedbackRow = findViewById(R.id.aboutFeedbackRow);
        feedbackRow.setOnClickListener(v -> {
            startActivity(new Intent(this, FeedbackActivity.class));
        });

        View licenseRow = findViewById(R.id.aboutLicenseRow);
        if (licenseRow != null) {
            licenseRow.setOnClickListener(v -> openHtmlAsset(
                    ShowHTMLActivity.ASSET_LICENSE, R.string.about_license));
        }
        View noticeRow = findViewById(R.id.aboutNoticeRow);
        if (noticeRow != null) {
            noticeRow.setOnClickListener(v -> openHtmlAsset(
                    ShowHTMLActivity.ASSET_NOTICE, R.string.about_notice));
        }
    }

    private void openHtmlAsset(String assetPath, int titleResId) {
        Intent intent = new Intent(this, ShowHTMLActivity.class);
        intent.putExtra(ShowHTMLActivity.EXTRA_ASSET_PATH, assetPath);
        intent.putExtra(ShowHTMLActivity.EXTRA_TITLE, getString(titleResId));
        startActivity(intent);
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
