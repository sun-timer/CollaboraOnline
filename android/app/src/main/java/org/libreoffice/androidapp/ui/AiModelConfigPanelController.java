package org.libreoffice.androidapp.ui;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidlib.ai.AiModelConfigStore;

import java.util.Locale;

final class AiModelConfigPanelController {
    interface Host {
        void onDismiss(boolean saved);
    }

    private final Context context;
    private final View root;
    private final int modelType;
    private final Host host;

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

    AiModelConfigPanelController(Context context, View root, int modelType, Host host) {
        this.context = context;
        this.root = root;
        this.modelType = modelType;
        this.host = host;
    }

    void bind() {
        // 静态布局：activity_ai_model_config.xml 已内联完整表单（aiModelConfigPanel 直接存在），无需动态 inflate。
        // 08-04 曾动态 inflate panel_ai_model_config → 真机 ScrollView 内容不渲染(灰屏)。
        android.util.Log.i("LOActivity", "ai_model_config_bind start modelType=" + modelType);
        bindViews();
        if (configNameInput == null) {
            android.util.Log.e("LOActivity", "ai_model_config_bind_fail reason=panel_views_missing root="
                    + (root != null));
            return;
        }
        bindHeader();
        bindSliders();
        loadValues();
        bindActions();

        View panel = root.findViewById(R.id.aiModelConfigPanel);
        if (panel != null) {
            panel.setOnClickListener(v -> { /* keep panel open when tapping inside */ });
        }
        View scrim = root.findViewById(R.id.aiModelConfigScrim);
        if (scrim != null) {
            scrim.setOnClickListener(v -> dismiss(false));
        } else {
            root.setOnClickListener(v -> dismiss(false));
        }
        android.util.Log.i("LOActivity", "ai_model_config_bind_ok title="
                + (root.findViewById(R.id.modelConfigTitle) != null));
    }

    private void dismiss(boolean saved) {
        if (host != null) {
            host.onDismiss(saved);
        }
    }

    private void bindViews() {
        configNameInput = root.findViewById(R.id.modelConfigNameInput);
        providerInput = root.findViewById(R.id.modelProviderInput);
        urlInput = root.findViewById(R.id.modelUrlInput);
        apiKeyInput = root.findViewById(R.id.modelApiKeyInput);
        modelNameInput = root.findViewById(R.id.modelNameInput);

        topPBar = root.findViewById(R.id.topPBar);
        temperatureBar = root.findViewById(R.id.temperatureBar);
        presencePenaltyBar = root.findViewById(R.id.presencePenaltyBar);
        frequencyPenaltyBar = root.findViewById(R.id.frequencyPenaltyBar);
        maxTokensBar = root.findViewById(R.id.maxTokensBar);
        seedBar = root.findViewById(R.id.seedBar);

        topPValue = root.findViewById(R.id.topPValue);
        temperatureValue = root.findViewById(R.id.temperatureValue);
        presencePenaltyValue = root.findViewById(R.id.presencePenaltyValue);
        frequencyPenaltyValue = root.findViewById(R.id.frequencyPenaltyValue);
        maxTokensValue = root.findViewById(R.id.maxTokensValue);
        seedValue = root.findViewById(R.id.seedValue);
    }

    private void bindHeader() {
        TextView title = root.findViewById(R.id.modelConfigTitle);
        ImageView icon = root.findViewById(R.id.modelConnectionIcon);
        ImageButton backButton = root.findViewById(R.id.modelConfigBackButton);

        if (title != null) {
            title.setText(AiSettingsStore.modelTitleRes(modelType));
        }
        if (icon != null) {
            icon.setImageResource(R.drawable.ic_ai_connection);
        }
        if (backButton != null) {
            backButton.setOnClickListener(v -> dismiss(false));
        }
    }

    private void loadValues() {
        AiModelConfigStore.Form form = AiModelConfigStore.loadForm(context, modelType,
                context.getString(AiSettingsStore.modelTitleRes(modelType)) + "配置",
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
        if (seekBar == null || valueView == null) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            seekBar.setMin(0);
        }
        seekBar.setMax(100);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                valueView.setText(formatRatio(progress / 100f));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
    }

    private void bindActions() {
        View cancelButton = root.findViewById(R.id.modelConfigCancelButton);
        View saveButton = root.findViewById(R.id.modelConfigSaveButton);

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dismiss(false));
        }
        if (saveButton != null) {
            saveButton.setOnClickListener(v -> saveAndDismiss());
        }
    }

    private void saveAndDismiss() {
        AiModelConfigStore.Form form = readForm();
        if (!AiModelConfigStore.saveForm(context, modelType, form)) {
            Toast.makeText(context, R.string.ai_model_config_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        if (modelType == AiSettingsStore.MODEL_BASE) {
            AiSettingsStore.syncBaseModelToRuntime(context);
        }

        Toast.makeText(context, R.string.ai_model_config_saved, Toast.LENGTH_SHORT).show();
        dismiss(true);
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
        if (seekBar == null || valueView == null) {
            return;
        }
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
