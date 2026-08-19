package org.libreoffice.androidapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidlib.SystemUiHelper;
import org.libreoffice.androidlib.ai.AiModelConfigStore;

import java.util.Locale;

public class AiModelConfigActivity extends AppCompatActivity {
    private int modelType = AiSettingsStore.MODEL_BASE;
    private boolean fromDrawer = false;

    private EditText configNameInput;
    private EditText providerInput;
    private EditText urlInput;
    private EditText apiKeyInput;
    private EditText modelNameInput;

    private SeekBar topPBar;
    private SeekBar temperatureBar;
    private SeekBar presencePenaltyBar;
    private SeekBar frequencyPenaltyBar;
    private SeekBar maxTokensBar;
    private SeekBar seedBar;

    private TextView topPValue;
    private TextView temperatureValue;
    private TextView presencePenaltyValue;
    private TextView frequencyPenaltyValue;
    private TextView maxTokensValue;
    private TextView seedValue;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_model_config);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.modelConfigHeader), 0);
        View configFooter = findViewById(R.id.modelConfigFooter);
        SystemUiHelper.applyNavigationBarPadding(configFooter,
                getResources().getDimensionPixelSize(R.dimen.ai_model_config_footer_padding_v));

        modelType = getIntent().getIntExtra(AiSettingsStore.EXTRA_MODEL_TYPE, AiSettingsStore.MODEL_BASE);
        fromDrawer = getIntent().getBooleanExtra(AiSettingsStore.EXTRA_FROM_DRAWER, false);

        bindViews();
        bindHeader();
        bindSliders();
        loadValues();
        bindActions();
        View scrim = findViewById(R.id.modelConfigScrim);
        if (scrim != null) {
            scrim.setOnClickListener(v -> finish());
        }

        // 左侧抽屉二级面板：从左边滑入（系统默认从右边，与抽屉方向不符）
        overridePendingTransition(R.anim.slide_in_left, 0);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_out_left);
    }

    private void navigateBack() {
        if (fromDrawer) {
            Intent data = new Intent();
            data.putExtra(AiSettingsStore.EXTRA_REOPEN_DRAWER, true);
            setResult(AiSettingsStore.RESULT_BACK_TO_DRAWER, data);
        }
        finish();
    }

    private void bindViews() {
        configNameInput = findViewById(R.id.modelConfigNameInput);
        providerInput = findViewById(R.id.modelProviderInput);
        urlInput = findViewById(R.id.modelUrlInput);
        apiKeyInput = findViewById(R.id.modelApiKeyInput);
        modelNameInput = findViewById(R.id.modelNameInput);

        topPBar = findViewById(R.id.topPBar);
        temperatureBar = findViewById(R.id.temperatureBar);
        presencePenaltyBar = findViewById(R.id.presencePenaltyBar);
        frequencyPenaltyBar = findViewById(R.id.frequencyPenaltyBar);
        maxTokensBar = findViewById(R.id.maxTokensBar);
        seedBar = findViewById(R.id.seedBar);

        topPValue = findViewById(R.id.topPValue);
        temperatureValue = findViewById(R.id.temperatureValue);
        presencePenaltyValue = findViewById(R.id.presencePenaltyValue);
        frequencyPenaltyValue = findViewById(R.id.frequencyPenaltyValue);
        maxTokensValue = findViewById(R.id.maxTokensValue);
        seedValue = findViewById(R.id.seedValue);
    }

    private void bindHeader() {
        TextView title = findViewById(R.id.modelConfigTitle);
        ImageView icon = findViewById(R.id.modelConnectionIcon);
        ImageButton backButton = findViewById(R.id.modelConfigBackButton);

        title.setText(AiSettingsStore.modelTitleRes(modelType));
        icon.setImageResource(R.drawable.ic_ai_connection);
        backButton.setOnClickListener(v -> saveAndClose());
    }

    @Override
    public void onBackPressed() {
        saveAndClose();
    }

    private void loadValues() {
        AiModelConfigStore.Form form = AiModelConfigStore.loadForm(this, modelType,
                getString(AiSettingsStore.modelTitleRes(modelType)) + "配置",
                AiSettingsStore.defaultModelName(modelType));
        configNameInput.setText(form.configName);
        providerInput.setText(form.provider);
        urlInput.setText(form.url);
        apiKeyInput.setText(form.apiKey);
        modelNameInput.setText(form.modelName);

        setSliderValue(topPBar, form.topP, topPValue);
        setSliderValue(temperatureBar, form.temperature, temperatureValue);
        setSliderValue(presencePenaltyBar, form.presencePenalty, presencePenaltyValue);
        setSliderValue(frequencyPenaltyBar, form.frequencyPenalty, frequencyPenaltyValue);
        setSliderValue(maxTokensBar, form.maxTokensRatio, maxTokensValue);
        setSliderValue(seedBar, form.seedRatio, seedValue);
    }

    private void bindSliders() {
        bindSlider(topPBar, topPValue);
        bindSlider(temperatureBar, temperatureValue);
        bindSlider(presencePenaltyBar, presencePenaltyValue);
        bindSlider(frequencyPenaltyBar, frequencyPenaltyValue);
        bindSlider(maxTokensBar, maxTokensValue);
        bindSlider(seedBar, seedValue);
    }

    private void bindSlider(SeekBar seekBar, TextView valueView) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            seekBar.setMin(0);
        }
        seekBar.setMax(100);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueView.setText(formatRatio(progress / 100f));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                persistForm(false);
            }
        });
    }

    private void bindActions() {
        View cancelButton = findViewById(R.id.modelConfigCancelButton);
        View saveButton = findViewById(R.id.modelConfigSaveButton);

        if (cancelButton != null) {
            cancelButton.setClickable(true);
            cancelButton.setOnClickListener(v -> navigateBack());
        }
        if (saveButton != null) {
            saveButton.setClickable(true);
            saveButton.setOnClickListener(v -> saveAndClose());
        }
    }

    private void saveAndClose() {
        if (!persistForm(true)) {
            return;
        }
        setResult(RESULT_OK);
        finish();
    }

    private boolean persistForm(boolean showToast) {
        AiModelConfigStore.Form form = readForm();
        if (!AiModelConfigStore.saveForm(this, modelType, form)) {
            if (showToast) {
                Toast.makeText(this, R.string.ai_model_config_save_failed, Toast.LENGTH_SHORT).show();
            }
            return false;
        }

        if (modelType == AiSettingsStore.MODEL_BASE) {
            AiSettingsStore.syncBaseModelToRuntime(this);
        }

        if (showToast) {
            Toast.makeText(this, R.string.ai_model_config_saved, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private AiModelConfigStore.Form readForm() {
        AiModelConfigStore.Form form = new AiModelConfigStore.Form();
        form.configName = readInput(configNameInput);
        form.provider = readInput(providerInput);
        form.url = readInput(urlInput);
        form.apiKey = readInput(apiKeyInput);
        form.modelName = readInput(modelNameInput);
        form.topP = toRatio(topPBar.getProgress());
        form.temperature = toRatio(temperatureBar.getProgress());
        form.presencePenalty = toRatio(presencePenaltyBar.getProgress());
        form.frequencyPenalty = toRatio(frequencyPenaltyBar.getProgress());
        form.maxTokensRatio = toRatio(maxTokensBar.getProgress());
        form.seedRatio = toRatio(seedBar.getProgress());
        return form;
    }

    private String readInput(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void setSliderValue(SeekBar seekBar, float value, TextView valueView) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            seekBar.setMin(0);
        }
        seekBar.setMax(100);
        int progress = Math.max(0, Math.min(100, Math.round(value * 100f)));
        seekBar.setProgress(progress);
        valueView.setText(formatRatio(progress / 100f));
    }

    private float toRatio(int progress) {
        return Math.max(0f, Math.min(1f, progress / 100f));
    }

    private String formatRatio(float value) {
        if (Math.abs(value - Math.round(value)) < 0.0001f) {
            return String.format(Locale.getDefault(), "%.0f", value);
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }
}
