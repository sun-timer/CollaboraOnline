package org.libreoffice.androidapp.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidlib.SystemUiHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-screen cache management UI matching the Figma clear-cache flow. */
public class ClearCacheActivity extends AppCompatActivity {
    private static final long TOAST_DURATION_MS = 2800L;
    private static final long REFRESH_SCROLL_DELAY_MS = 120L;

    private enum UiState {
        IDLE,
        CLEARING,
        COMPLETED
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ClearCacheHelper.CleanMode selectedMode = ClearCacheHelper.CleanMode.QUICK;
    private boolean hasSelectedMode;
    private ClearCacheHelper.StorageInfo storageInfo;
    private UiState uiState = UiState.IDLE;

    private TextView usedSpaceValue;
    private View progressApp;
    private View progressPhone;
    private LinearLayout categoryCardTop;
    private LinearLayout categoryCardBottom;
    private LinearLayout modeCard;
    private TextView actionButton;
    private TextView toastView;
    private PullRefreshScrollView scrollView;

    private View quickModeRow;
    private View deepModeRow;
    private ImageView quickModeRadio;
    private ImageView deepModeRadio;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clear_cache);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));

        View header = findViewById(R.id.clearCacheHeader);
        SystemUiHelper.applyStatusBarPadding(header, 0);

        View bottomBar = findViewById(R.id.clearCacheBottomBar);
        int footerPad = getResources().getDimensionPixelSize(R.dimen.clear_cache_bottom_bar_padding_v);
        SystemUiHelper.applyNavigationBarPadding(bottomBar, footerPad);

        bindViews();
        setupPullRefresh();
        setupModeRows();
        setupActions();
        loadStorageAsync(false);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void bindViews() {
        usedSpaceValue = findViewById(R.id.clearCacheUsedSpaceValue);
        progressApp = findViewById(R.id.clearCacheProgressApp);
        progressPhone = findViewById(R.id.clearCacheProgressPhone);
        categoryCardTop = findViewById(R.id.clearCacheCategoryCardTop);
        categoryCardBottom = findViewById(R.id.clearCacheCategoryCardBottom);
        modeCard = findViewById(R.id.clearCacheModeCard);
        actionButton = findViewById(R.id.clearCacheActionButton);
        toastView = findViewById(R.id.clearCacheToast);
        scrollView = findViewById(R.id.clearCacheScrollView);
    }

    private void setupPullRefresh() {
        View refreshHeader = findViewById(R.id.clearCacheRefreshHeader);
        ImageView refreshArrow = findViewById(R.id.clearCacheRefreshArrow);
        LinearLayout scrollContent = findViewById(R.id.clearCacheScrollContent);
        scrollView.bindRefreshViews(refreshHeader, refreshArrow, scrollContent);
        scrollView.setOnRefreshListener(this::refreshPage);
    }

    private void setupModeRows() {
        modeCard.removeAllViews();
        quickModeRow = inflateModeRow(
                getString(R.string.clear_cache_mode_quick_title),
                getString(R.string.clear_cache_mode_quick_desc),
                ClearCacheHelper.CleanMode.QUICK);
        deepModeRow = inflateModeRow(
                getString(R.string.clear_cache_mode_deep_title),
                getString(R.string.clear_cache_mode_deep_desc),
                ClearCacheHelper.CleanMode.DEEP);
        quickModeRadio = quickModeRow.findViewById(R.id.clearCacheModeRadio);
        deepModeRadio = deepModeRow.findViewById(R.id.clearCacheModeRadio);
        modeCard.addView(quickModeRow);
        modeCard.addView(deepModeRow);
        updateModeSelection();
    }

    private View inflateModeRow(String title, String desc, ClearCacheHelper.CleanMode mode) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_clear_cache_mode, modeCard, false);
        TextView titleView = row.findViewById(R.id.clearCacheModeTitle);
        TextView descView = row.findViewById(R.id.clearCacheModeDesc);
        titleView.setText(title);
        descView.setText(desc);
        row.setOnClickListener(v -> {
            if (uiState != UiState.IDLE) {
                return;
            }
            selectedMode = mode;
            hasSelectedMode = true;
            updateModeSelection();
            updateActionButton();
        });
        return row;
    }

    private void setupActions() {
        findViewById(R.id.clearCacheBackButton).setOnClickListener(v -> finish());
        actionButton.setOnClickListener(v -> {
            if (uiState != UiState.IDLE || storageInfo == null) {
                return;
            }
            long clearable = storageInfo.categories.clearableBytes(selectedMode);
            if (clearable <= 0L) {
                return;
            }
            showConfirmDialog(clearable);
        });
    }

    private void loadStorageAsync(boolean fromRefresh) {
        if (!fromRefresh) {
            actionButton.setEnabled(false);
        }
        worker.execute(() -> {
            ClearCacheHelper.StorageInfo info = ClearCacheHelper.scan(ClearCacheActivity.this);
            mainHandler.post(() -> applyStorageInfo(info, fromRefresh));
        });
    }

    private void applyStorageInfo(ClearCacheHelper.StorageInfo info, boolean fromRefresh) {
        storageInfo = info;
        renderStorage(info);
        updateModeSelection();
        updateActionButton();
        scrollView.setRefreshing(false);
        if (fromRefresh) {
            hasSelectedMode = false;
            if (uiState == UiState.COMPLETED) {
                uiState = UiState.IDLE;
            }
            updateActionButton();
            mainHandler.postDelayed(() -> scrollView.smoothScrollTo(0, 0), REFRESH_SCROLL_DELAY_MS);
        }
    }

    private void renderStorage(ClearCacheHelper.StorageInfo info) {
        ClearCacheHelper.CategorySize categories = info.categories;
        usedSpaceValue.setText(ClearCacheHelper.formatSize(categories.totalAppBytes()));
        renderProgressBar(info);
        renderCategoryRows(categories);
    }

    private void renderProgressBar(ClearCacheHelper.StorageInfo info) {
        long appBytes = Math.max(0L, info.categories.totalAppBytes());
        long phoneUsed = Math.max(appBytes, info.phoneUsedBytes);
        float appWeight = phoneUsed <= 0L ? 0f : (float) appBytes / (float) phoneUsed;
        float phoneWeight = phoneUsed <= 0L ? 0f : (float) (phoneUsed - appBytes) / (float) phoneUsed;
        if (appWeight + phoneWeight <= 0f) {
            appWeight = 0.05f;
            phoneWeight = 0.95f;
        }
        LinearLayout.LayoutParams appParams = (LinearLayout.LayoutParams) progressApp.getLayoutParams();
        LinearLayout.LayoutParams phoneParams = (LinearLayout.LayoutParams) progressPhone.getLayoutParams();
        appParams.weight = Math.max(appWeight, 0.01f);
        phoneParams.weight = Math.max(phoneWeight, 0.01f);
        progressApp.setLayoutParams(appParams);
        progressPhone.setLayoutParams(phoneParams);
    }

    private void renderCategoryRows(ClearCacheHelper.CategorySize categories) {
        categoryCardTop.removeAllViews();
        categoryCardBottom.removeAllViews();
        addCategoryRow(categoryCardTop, R.string.clear_cache_category_temp, categories.tempFilesBytes);
        addCategoryRow(categoryCardTop, R.string.clear_cache_category_image, categories.imageCacheBytes);
        addCategoryRow(categoryCardTop, R.string.clear_cache_category_ai_preview, categories.aiPreviewBytes);
        addCategoryRow(categoryCardBottom, R.string.clear_cache_category_chat, categories.chatHistoryBytes);
        addCategoryRow(categoryCardBottom, R.string.clear_cache_category_offline_model, categories.offlineModelBytes);
    }

    private void addCategoryRow(LinearLayout container, int titleRes, long bytes) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_clear_cache_row, container, false);
        TextView title = row.findViewById(R.id.clearCacheRowTitle);
        TextView size = row.findViewById(R.id.clearCacheRowSize);
        title.setText(titleRes);
        size.setText(ClearCacheHelper.formatSize(bytes));
        container.addView(row);
    }

    private void updateModeSelection() {
        boolean interactive = uiState == UiState.IDLE;
        quickModeRow.setEnabled(interactive);
        deepModeRow.setEnabled(interactive);
        quickModeRadio.setImageResource(hasSelectedMode && selectedMode == ClearCacheHelper.CleanMode.QUICK
                ? R.drawable.ic_clear_cache_radio_selected
                : R.drawable.ic_clear_cache_radio_unselected);
        deepModeRadio.setImageResource(hasSelectedMode && selectedMode == ClearCacheHelper.CleanMode.DEEP
                ? R.drawable.ic_clear_cache_radio_selected
                : R.drawable.ic_clear_cache_radio_unselected);
    }

    private void updateActionButton() {
        if (uiState == UiState.CLEARING) {
            actionButton.setText(R.string.clear_cache_action_running);
            actionButton.setBackgroundResource(R.drawable.bg_clear_cache_btn_disabled);
            actionButton.setTextColor(0xFF6A6A6A);
            actionButton.setEnabled(false);
            return;
        }
        long clearable = storageInfo == null ? 0L : storageInfo.categories.clearableBytes(selectedMode);
        boolean canClear = uiState == UiState.IDLE && hasSelectedMode && clearable > 0L;
        actionButton.setText(R.string.clear_cache_action);
        actionButton.setBackgroundResource(canClear
                ? R.drawable.bg_clear_cache_btn_primary
                : R.drawable.bg_clear_cache_btn_disabled);
        actionButton.setTextColor(canClear ? 0xFFFFFFFF : 0xFF6A6A6A);
        actionButton.setEnabled(canClear);
    }

    private void showConfirmDialog(long clearableBytes) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm_message_shell);

        TextView titleView = dialog.findViewById(R.id.confirmDialogTitle);
        TextView messageView = dialog.findViewById(R.id.confirmDialogMessage);
        View closeButton = dialog.findViewById(R.id.confirmDialogClose);
        View cancelButton = dialog.findViewById(R.id.confirmDialogCancel);
        View confirmButton = dialog.findViewById(R.id.confirmDialogConfirm);

        if (titleView != null) {
            titleView.setText(R.string.clear_cache_title);
        }
        if (messageView != null) {
            messageView.setText(getString(R.string.clear_cache_confirm_message,
                    ClearCacheHelper.formatSize(clearableBytes)));
        }
        if (confirmButton instanceof TextView) {
            ((TextView) confirmButton).setText(R.string.clear_cache_confirm_positive);
        }

        Runnable dismiss = dialog::dismiss;
        closeButton.setOnClickListener(v -> dismiss.run());
        cancelButton.setOnClickListener(v -> dismiss.run());
        confirmButton.setOnClickListener(v -> {
            dismiss.run();
            startClearing(clearableBytes);
        });

        ResponsiveUiHelper.applyOverlayDialogWindow(dialog);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void startClearing(long expectedBytes) {
        uiState = UiState.CLEARING;
        updateModeSelection();
        updateActionButton();
        final ClearCacheHelper.CleanMode mode = selectedMode;
        worker.execute(() -> {
            long freed = ClearCacheHelper.clear(ClearCacheActivity.this, mode);
            ClearCacheHelper.StorageInfo info = ClearCacheHelper.scan(ClearCacheActivity.this);
            final long reportBytes = freed > 0L ? freed : expectedBytes;
            mainHandler.post(() -> onClearCompleted(reportBytes, info));
        });
    }

    private void onClearCompleted(long freedBytes, ClearCacheHelper.StorageInfo info) {
        uiState = UiState.COMPLETED;
        storageInfo = info;
        renderStorage(info);
        updateModeSelection();
        updateActionButton();
        showCompletionToast(freedBytes);
    }

    private void showCompletionToast(long freedBytes) {
        toastView.setText(getString(R.string.clear_cache_completed_toast,
                ClearCacheHelper.formatSize(freedBytes)));
        toastView.setVisibility(View.VISIBLE);
        toastView.setAlpha(0f);
        toastView.animate().alpha(1f).setDuration(180L).start();
        mainHandler.postDelayed(() -> {
            toastView.animate().alpha(0f).setDuration(220L).withEndAction(() ->
                    toastView.setVisibility(View.GONE)).start();
        }, TOAST_DURATION_MS);
    }

    private void refreshPage() {
        loadStorageAsync(true);
    }
}
