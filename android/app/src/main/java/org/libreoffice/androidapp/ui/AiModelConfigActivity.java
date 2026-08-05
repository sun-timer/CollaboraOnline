package org.libreoffice.androidapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.libreoffice.androidapp.R;

public class AiModelConfigActivity extends AppCompatActivity {
    private boolean fromDrawer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_model_config);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        int modelType = getIntent().getIntExtra(AiSettingsStore.EXTRA_MODEL_TYPE, AiSettingsStore.MODEL_BASE);
        fromDrawer = getIntent().getBooleanExtra(AiSettingsStore.EXTRA_FROM_DRAWER, false);

        View root = findViewById(R.id.activity_ai_model_config_root);
        new AiModelConfigPanelController(this, root, modelType, saved -> {
            if (saved) {
                setResult(RESULT_OK);
            } else if (fromDrawer) {
                Intent data = new Intent();
                data.putExtra(AiSettingsStore.EXTRA_REOPEN_DRAWER, true);
                setResult(AiSettingsStore.RESULT_BACK_TO_DRAWER, data);
            }
            finish();
            overridePendingTransition(0, 0);
        }).bind();

        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (fromDrawer) {
            Intent data = new Intent();
            data.putExtra(AiSettingsStore.EXTRA_REOPEN_DRAWER, true);
            setResult(AiSettingsStore.RESULT_BACK_TO_DRAWER, data);
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
