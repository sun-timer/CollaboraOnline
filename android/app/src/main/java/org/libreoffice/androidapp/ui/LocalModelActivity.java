package org.libreoffice.androidapp.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidlib.ai.LocalModelManager;

public class LocalModelActivity extends AppCompatActivity {
    private LocalModelManager modelManager;
    private TextView statusText;
    private ProgressBar progressBar;
    private TextView progressText;
    private Button downloadButton;
    private SwitchCompat enableSwitch;
    private Button deleteButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_model);

        modelManager = LocalModelManager.getInstance(this);
        statusText = findViewById(R.id.localModelStatusText);
        progressBar = findViewById(R.id.localModelProgressBar);
        progressText = findViewById(R.id.localModelProgressText);
        downloadButton = findViewById(R.id.localModelDownloadButton);
        enableSwitch = findViewById(R.id.localModelEnableSwitch);
        deleteButton = findViewById(R.id.localModelDeleteButton);

        if (!modelManager.isDeviceSupported()) {
            statusText.setText(R.string.local_model_device_unsupported);
            downloadButton.setEnabled(false);
            enableSwitch.setEnabled(false);
            return;
        }

        downloadButton.setOnClickListener(v -> startDownload());
        deleteButton.setOnClickListener(v -> {
            modelManager.deleteModel();
            refreshUi();
            Toast.makeText(this, R.string.local_model_deleted, Toast.LENGTH_SHORT).show();
        });
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (modelManager.isInstalled()) {
                modelManager.setEnabled(isChecked);
            }
        });

        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void startDownload() {
        LocalModelManager.CatalogEntry entry = LocalModelManager.getDefaultCatalogEntry();
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        downloadButton.setEnabled(false);
        modelManager.downloadDefaultModel(new LocalModelManager.DownloadProgressCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                runOnUiThread(() -> {
                    progressBar.setProgress(percent);
                    progressText.setText(getString(R.string.local_model_download_progress, percent));
                });
            }

            @Override
            public void onComplete(boolean success, String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    progressText.setVisibility(View.GONE);
                    downloadButton.setEnabled(true);
                    refreshUi();
                    if (success) {
                        Toast.makeText(LocalModelActivity.this, R.string.local_model_download_done,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String code, String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    progressText.setVisibility(View.GONE);
                    downloadButton.setEnabled(true);
                    refreshUi();
                    Toast.makeText(LocalModelActivity.this,
                            getString(R.string.local_model_download_failed, message),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void refreshUi() {
        if (!modelManager.isDeviceSupported()) {
            return;
        }
        boolean installed = modelManager.isInstalled();
        String state = modelManager.getDownloadState();
        if (LocalModelManager.STATE_DOWNLOADING.equals(state)) {
            statusText.setText(R.string.local_model_downloading);
            downloadButton.setEnabled(false);
        } else if (installed) {
            statusText.setText(getString(R.string.local_model_installed, modelManager.getModelPath()));
            downloadButton.setText(R.string.local_model_redownload);
            deleteButton.setVisibility(View.VISIBLE);
            enableSwitch.setEnabled(true);
            enableSwitch.setChecked(modelManager.isEnabled());
        } else {
            statusText.setText(R.string.local_model_not_installed);
            downloadButton.setText(R.string.local_model_download);
            deleteButton.setVisibility(View.GONE);
            enableSwitch.setEnabled(false);
            enableSwitch.setChecked(false);
        }
    }
}
