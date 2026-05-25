package org.libreoffice.androidapp.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.libreoffice.androidapp.R;

import java.util.Locale;

public class AiModelConfigActivity extends AppCompatActivity {
    private int modelType = AiSettingsStore.MODEL_BASE;
    private SharedPreferences prefs;

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

        modelType = getIntent().getIntExtra(AiSettingsStore.EXTRA_MODEL_TYPE, AiSettingsStore.MODEL_BASE);
        prefs = AiSettingsStore.prefs(this);

        bindViews();
        bindHeader();
        loadValues();
        bindSliders();
        bindActions();
        View scrim = findViewById(R.id.modelConfigScrim);
        if (scrim != null) {
            scrim.setOnClickListener(v -> finish());
        }
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
        icon.setImageResource(R.drawable.ic_model_base);
        backButton.setOnClickListener(v -> finish());
    }

    private void loadValues() {
        configNameInput.setText(getStringValue(AiSettingsStore.FIELD_CONFIG_NAME, getString(AiSettingsStore.modelTitleRes(modelType)) + "配置"));
        providerInput.setText(getStringValue(AiSettingsStore.FIELD_PROVIDER, "OpenAI"));
        urlInput.setText(getStringValue(AiSettingsStore.FIELD_URL, "https://api.openai.com/v1/chat/completions"));
        apiKeyInput.setText(getStringValue(AiSettingsStore.FIELD_API_KEY, ""));
        modelNameInput.setText(getStringValue(AiSettingsStore.FIELD_MODEL_NAME, AiSettingsStore.defaultModelName(modelType)));

        setSliderValue(topPBar, getFloatValue(AiSettingsStore.FIELD_TOP_P, 0.5f), topPValue);
        setSliderValue(temperatureBar, getFloatValue(AiSettingsStore.FIELD_TEMPERATURE, 0.9f), temperatureValue);
        setSliderValue(presencePenaltyBar, getFloatValue(AiSettingsStore.FIELD_PRESENCE_PENALTY, 0f), presencePenaltyValue);
        setSliderValue(frequencyPenaltyBar, getFloatValue(AiSettingsStore.FIELD_FREQUENCY_PENALTY, 0.8f), frequencyPenaltyValue);
        setSliderValue(maxTokensBar, getFloatValue(AiSettingsStore.FIELD_MAX_TOKENS_RATIO, 0.8f), maxTokensValue);
        setSliderValue(seedBar, getFloatValue(AiSettingsStore.FIELD_SEED_RATIO, 0.8f), seedValue);
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
            }
        });
    }

    private void bindActions() {
        View cancelButton = findViewById(R.id.modelConfigCancelButton);
        View saveButton = findViewById(R.id.modelConfigSaveButton);

        cancelButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveAndClose());
    }

    private void saveAndClose() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(modelKey(AiSettingsStore.FIELD_CONFIG_NAME), readInput(configNameInput));
        editor.putString(modelKey(AiSettingsStore.FIELD_PROVIDER), readInput(providerInput));
        editor.putString(modelKey(AiSettingsStore.FIELD_URL), readInput(urlInput));
        editor.putString(modelKey(AiSettingsStore.FIELD_API_KEY), readInput(apiKeyInput));
        editor.putString(modelKey(AiSettingsStore.FIELD_MODEL_NAME), readInput(modelNameInput));

        editor.putFloat(modelKey(AiSettingsStore.FIELD_TOP_P), toRatio(topPBar.getProgress()));
        editor.putFloat(modelKey(AiSettingsStore.FIELD_TEMPERATURE), toRatio(temperatureBar.getProgress()));
        editor.putFloat(modelKey(AiSettingsStore.FIELD_PRESENCE_PENALTY), toRatio(presencePenaltyBar.getProgress()));
        editor.putFloat(modelKey(AiSettingsStore.FIELD_FREQUENCY_PENALTY), toRatio(frequencyPenaltyBar.getProgress()));
        editor.putFloat(modelKey(AiSettingsStore.FIELD_MAX_TOKENS_RATIO), toRatio(maxTokensBar.getProgress()));
        editor.putFloat(modelKey(AiSettingsStore.FIELD_SEED_RATIO), toRatio(seedBar.getProgress()));
        editor.apply();

        if (modelType == AiSettingsStore.MODEL_BASE) {
            AiSettingsStore.syncBaseModelToRuntime(this);
        }

        setResult(RESULT_OK);
        finish();
    }

    private String readInput(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void setSliderValue(SeekBar seekBar, float value, TextView valueView) {
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

    private String modelKey(String field) {
        return AiSettingsStore.modelKey(modelType, field);
    }

    private String getStringValue(String field, String defaultValue) {
        return prefs.getString(modelKey(field), defaultValue);
    }

    private float getFloatValue(String field, float defaultValue) {
        return prefs.getFloat(modelKey(field), defaultValue);
    }
}
