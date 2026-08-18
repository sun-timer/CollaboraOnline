package org.libreoffice.androidapp.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidlib.ai.LocalDeviceCapability;
import org.libreoffice.androidlib.ai.LocalModelManager;

public class LocalModelActivity extends AppCompatActivity {
    private LocalModelManager modelManager;
    private TextView statusText;
    private ProgressBar progressBar;
    private TextView progressText;
    private TextView downloadButton;
    private SwitchCompat enableSwitch;
    private TextView deleteButton;
    private TextView cancelButton;
    private TextView chooseButton;
    private TextView deviceInfoText;
    private TextView deviceVerdictText;
    private View modelListOverlay;
    private LocalModelListUiHelper.Controller modelListController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_model);
        setupBottomSheetWindow();

        modelManager = LocalModelManager.getInstance(this);
        statusText = findViewById(R.id.localModelStatusText);
        progressBar = findViewById(R.id.localModelProgressBar);
        progressText = findViewById(R.id.localModelProgressText);
        downloadButton = findViewById(R.id.localModelDownloadButton);
        enableSwitch = findViewById(R.id.localModelEnableSwitch);
        deleteButton = findViewById(R.id.localModelDeleteButton);
        cancelButton = findViewById(R.id.localModelCancelButton);
        chooseButton = findViewById(R.id.localModelChooseButton);
        deviceInfoText = findViewById(R.id.localModelDeviceInfoText);
        deviceVerdictText = findViewById(R.id.localModelDeviceVerdictText);

        View root = findViewById(R.id.localModelRoot);
        View sheet = findViewById(R.id.localModelSheet);
        View closeButton = findViewById(R.id.localModelCloseButton);
        View confirmButton = findViewById(R.id.localModelConfirmButton);

        ResponsiveUiHelper.applyAdaptiveBottomSheetLayout(this, sheet);

        if (root != null) {
            root.setOnClickListener(v -> finish());
        }
        if (sheet != null) {
            sheet.setOnClickListener(v -> { /* keep sheet open */ });
        }
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> finish());
        }
        if (confirmButton != null) {
            confirmButton.setOnClickListener(v -> finish());
        }

        bindDeviceCapability();

        if (!modelManager.isDeviceSupported()) {
            statusText.setText(R.string.local_model_device_unsupported);
            downloadButton.setEnabled(false);
            enableSwitch.setEnabled(false);
            if (chooseButton != null) {
                chooseButton.setEnabled(false);
            }
            return;
        }

        downloadButton.setOnClickListener(v -> startDownload());
        deleteButton.setOnClickListener(v -> {
            modelManager.deleteModel();
            refreshUi();
            Toast.makeText(this, R.string.local_model_deleted, Toast.LENGTH_SHORT).show();
        });
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> {
                modelManager.cancelDownload();
                refreshUi();
                Toast.makeText(this, R.string.local_model_cancel, Toast.LENGTH_SHORT).show();
            });
        }
        if (chooseButton != null) {
            chooseButton.setOnClickListener(v -> showModelListDialog());
        }
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!modelManager.isInstalled()) {
                return;
            }
            if (!isChecked) {
                modelManager.setEnabled(false);
                return;
            }
            LocalModelManager.CatalogEntry active = modelManager.getInstalledCatalogEntry();
            LocalDeviceCapability capability = LocalDeviceCapability.assess(this);
            if (active != null && capability.isModelRamMarginal(active)) {
                Toast.makeText(this,
                        LocalModelManager.getModelCapabilityMessage(capability, active),
                        Toast.LENGTH_LONG).show();
            } else if (capability.slowCpuWarning) {
                Toast.makeText(this, R.string.local_model_device_warn_slow_cpu, Toast.LENGTH_LONG).show();
            }
            modelManager.setEnabled(true);
        });

        refreshUi();
    }

    private void setupBottomSheetWindow() {
        ResponsiveUiHelper.applyAdaptiveSheetWindow(this, getWindow());
        Window window = getWindow();
        if (window == null) {
            return;
        }
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0.3f;
        window.setAttributes(params);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindDeviceCapability();
        refreshUi();
    }

    private void bindDeviceCapability() {
        if (deviceInfoText == null || deviceVerdictText == null) {
            return;
        }
        LocalDeviceCapability capability = LocalDeviceCapability.assess(this);
        deviceInfoText.setText(getString(R.string.local_model_device_info_format,
                capability.cpuLabel,
                capability.primaryAbi,
                LocalDeviceCapability.formatBytesShort(capability.totalRamBytes),
                capability.coreCount,
                LocalDeviceCapability.formatBytesShort(capability.freeStorageBytes)));

        StringBuilder verdict = new StringBuilder();
        if (capability.isHardBlocked()) {
            verdict.append(getString(R.string.local_model_device_verdict_unsupported));
        } else if (capability.ramTier == LocalDeviceCapability.RamTier.LOW) {
            verdict.append(getString(R.string.local_model_device_verdict_supported_limited));
        } else {
            verdict.append(getString(R.string.local_model_device_verdict_supported));
        }
        if (!capability.isHardBlocked() && capability.slowCpuWarning) {
            verdict.append("\n").append(getString(R.string.local_model_device_warn_slow_cpu));
        }
        deviceVerdictText.setText(verdict.toString());
    }

    @Override
    public void onBackPressed() {
        if (modelListOverlay != null) {
            dismissModelListOverlay();
            return;
        }
        finish();
    }

    private void startDownload() {
        progressBar.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        downloadButton.setEnabled(false);
        if (cancelButton != null) {
            cancelButton.setVisibility(View.VISIBLE);
        }
        modelManager.downloadDefaultModel(new LocalModelManager.DownloadProgressCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                if (isFinishing()) {
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    progressBar.setProgress(percent);
                    progressText.setText(getString(R.string.local_model_download_progress, percent));
                });
            }

            @Override
            public void onComplete(boolean success, String message) {
                if (isFinishing()) {
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
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
                if (isFinishing()) {
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    progressBar.setVisibility(View.GONE);
                    progressText.setVisibility(View.GONE);
                    downloadButton.setEnabled(true);
                    refreshUi();
                    if (!"cancelled".equals(code) && !"download_busy".equals(code)) {
                        Toast.makeText(LocalModelActivity.this,
                                getString(R.string.local_model_download_failed, message),
                                Toast.LENGTH_LONG).show();
                    }
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
            if (modelManager.isDownloadActive()) {
                statusText.setText(R.string.local_model_downloading);
                downloadButton.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                progressText.setVisibility(View.VISIBLE);
                progressBar.setProgress(modelManager.getLastProgressPercent());
                progressText.setText(getString(R.string.local_model_download_progress,
                        modelManager.getLastProgressPercent()));
                if (cancelButton != null) {
                    cancelButton.setVisibility(View.VISIBLE);
                }
            } else {
                // 进程被杀等导致 downloading 状态残留但无运行中的任务 → 允许重新下载（Range 续传 .part）
                statusText.setText(R.string.local_model_interrupted_status);
                downloadButton.setEnabled(true);
                downloadButton.setText(R.string.local_model_redownload);
                if (cancelButton != null) {
                    cancelButton.setVisibility(View.GONE);
                }
            }
        } else if (installed) {
            LocalModelManager.CatalogEntry active = modelManager.getInstalledCatalogEntry();
            String label = active != null ? active.displayName : modelManager.getModelPath();
            statusText.setText(getString(R.string.local_model_installed, label));
            downloadButton.setText(R.string.local_model_redownload);
            deleteButton.setVisibility(View.VISIBLE);
            enableSwitch.setEnabled(true);
            enableSwitch.setChecked(modelManager.isEnabled());
            if (cancelButton != null) {
                cancelButton.setVisibility(View.GONE);
            }
        } else if (modelManager.hasAnyDownloadedModel()) {
            statusText.setText(R.string.local_model_select_from_list);
            downloadButton.setText(R.string.local_model_download);
            deleteButton.setVisibility(View.GONE);
            enableSwitch.setEnabled(false);
            enableSwitch.setChecked(false);
            if (cancelButton != null) {
                cancelButton.setVisibility(View.GONE);
            }
        } else {
            statusText.setText(R.string.local_model_not_installed);
            downloadButton.setText(R.string.local_model_download);
            deleteButton.setVisibility(View.GONE);
            enableSwitch.setEnabled(false);
            enableSwitch.setChecked(false);
            if (cancelButton != null) {
                cancelButton.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 模型列表（Qwen/Gemma）。复用 Activity 已有蒙层，避免再叠一层 Dialog Window。
     */
    private void showModelListDialog() {
        if (modelListOverlay != null) {
            return;
        }
        View root = findViewById(R.id.localModelRoot);
        View sheet = findViewById(R.id.localModelSheet);
        if (!(root instanceof ViewGroup)) {
            return;
        }
        modelListOverlay = LayoutInflater.from(this).inflate(
                R.layout.dialog_local_model_list_shell, (ViewGroup) root, false);
        ((ViewGroup) root).addView(modelListOverlay);
        if (sheet != null) {
            sheet.setVisibility(View.GONE);
        }
        bindModelListOverlay(modelListOverlay);
    }

    private void dismissModelListOverlay() {
        if (modelListOverlay == null) {
            return;
        }
        View root = findViewById(R.id.localModelRoot);
        if (root instanceof ViewGroup) {
            ((ViewGroup) root).removeView(modelListOverlay);
        }
        modelListOverlay = null;
        modelListController = null;
        View sheet = findViewById(R.id.localModelSheet);
        if (sheet != null) {
            sheet.setVisibility(View.VISIBLE);
        }
        refreshUi();
    }

    private void bindModelListOverlay(View overlay) {
        View overlayRoot = overlay.findViewById(R.id.localModelListDialogRoot);
        View card = overlay.findViewById(R.id.localModelListDialogCard);
        LinearLayout container = overlay.findViewById(R.id.localModelListContainer);
        View closeButton = overlay.findViewById(R.id.localModelListCloseButton);
        View confirmButton = overlay.findViewById(R.id.localModelListConfirmButton);

        modelListController = new LocalModelListUiHelper.Controller(
                container, this, modelManager,
                new LocalModelListUiHelper.ModelRowListener() {
                    @Override
                    public void onDownloadRequested(LocalModelManager.CatalogEntry entry) {
                        downloadModelFromDialog(modelManager, entry, modelListController);
                    }

                    @Override
                    public void onSelectRequested(LocalModelManager.CatalogEntry entry) {
                        selectModelFromDialog(modelManager, entry, modelListController);
                    }
                });

        Runnable dismissOverlay = this::dismissModelListOverlay;

        modelListController.refreshAll();

        if (overlayRoot != null) {
            overlayRoot.setOnClickListener(v -> dismissOverlay.run());
        }
        if (card != null) {
            ResponsiveUiHelper.applyDialogCardMaxWidth(this, card);
            card.setOnClickListener(v -> { /* keep list open when tapping card */ });
        }
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismissOverlay.run());
        }
        if (confirmButton != null) {
            confirmButton.setOnClickListener(v -> dismissOverlay.run());
        }
    }

    private void selectModelFromDialog(LocalModelManager modelManager,
            LocalModelManager.CatalogEntry entry, LocalModelListUiHelper.Controller listController) {
        modelManager.selectActiveModel(entry);
        if (listController != null) {
            listController.refreshAll();
        }
        refreshUi();
        Toast.makeText(this, getString(R.string.local_model_switched, entry.displayName),
                Toast.LENGTH_SHORT).show();
    }

    private void downloadModelFromDialog(LocalModelManager modelManager,
            LocalModelManager.CatalogEntry entry, LocalModelListUiHelper.Controller listController) {
        if (listController != null) {
            listController.refreshAll();
        }
        modelManager.downloadModel(entry, new LocalModelManager.DownloadProgressCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                if (isFinishing()) {
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing() || listController == null) {
                        return;
                    }
                    listController.updateProgress(percent);
                });
            }

            @Override
            public void onComplete(boolean success, String message) {
                if (isFinishing()) {
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    if (listController != null) {
                        listController.refreshAll();
                    }
                    refreshUi();
                    if (success) {
                        Toast.makeText(LocalModelActivity.this, R.string.local_model_download_done,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String code, String message) {
                if (isFinishing()) {
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    if (listController != null) {
                        listController.refreshAll();
                    }
                    refreshUi();
                    if (!"cancelled".equals(code) && !"download_busy".equals(code)) {
                        Toast.makeText(LocalModelActivity.this,
                                getString(R.string.local_model_download_failed, message),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}
