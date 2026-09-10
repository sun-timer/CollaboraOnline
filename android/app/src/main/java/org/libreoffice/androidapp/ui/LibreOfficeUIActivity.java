/* -*- tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.androidapp.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.PopupWindow;
import android.widget.Toast;


import org.libreoffice.androidapp.ui.AboutActivity;
import org.libreoffice.androidapp.R;
import org.libreoffice.androidapp.SettingsActivity;
import org.libreoffice.androidapp.SettingsListenerModel;
import org.libreoffice.androidlib.LOActivity;
import org.libreoffice.androidlib.SafeAreaInsets;
import org.libreoffice.androidlib.SystemUiHelper;
import org.libreoffice.androidlib.ExitDiagHelper;
import org.libreoffice.androidlib.ai.AiModelConfigStore;
import org.libreoffice.androidlib.ai.LocalModelManager;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.widget.FrameLayout;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import static androidx.core.content.pm.ShortcutManagerCompat.getMaxShortcutCountPerActivity;

public class LibreOfficeUIActivity extends AppCompatActivity implements SettingsListenerModel.OnSettingsPreferenceChangedListener {
    private String LOGTAG = LibreOfficeUIActivity.class.getSimpleName();
    private SharedPreferences prefs;
    private int filterMode = FileUtilities.ALL;
    private int sortMode;
    private boolean showHiddenFiles;
    // dynamic permissions IDs
    private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 0;

    FileFilter fileFilter;
    FilenameFilter filenameFilter;
    private Uri currentlySelectedFile;

    /** The document that is being edited - to know what to save back to cloud. */
    //private IFile mCurrentDocument;

    private static final String CURRENT_DIRECTORY_KEY = "CURRENT_DIRECTORY";
    private static final String DOC_PROVIDER_KEY = "CURRENT_DOCUMENT_PROVIDER";
    private static final String FILTER_MODE_KEY = "FILTER_MODE";
    public static final String EXPLORER_VIEW_TYPE_KEY = "EXPLORER_VIEW_TYPE";
    public static final String EXPLORER_PREFS_KEY = "EXPLORER_PREFS";
    public static final String SORT_MODE_KEY = "SORT_MODE";
    public static final String RECENT_DOCUMENTS_KEY = "RECENT_DOCUMENTS_LIST";
    private static final String RECENT_OPEN_TIME_PREFIX = "RECENT_OPEN_TIME_";
    private static final String ENABLE_SHOW_HIDDEN_FILES_KEY = "ENABLE_SHOW_HIDDEN_FILES";

    public static final String NEW_FILE_PATH_KEY = "NEW_FILE_PATH_KEY";
    public static final String NEW_DOC_TYPE_KEY = "NEW_DOC_TYPE_KEY";
    public static final String NIGHT_MODE_KEY = "NIGHT_MODE";

    public static final String GRID_VIEW = "0";
    public static final String LIST_VIEW = "1";

    private DrawerLayout drawerLayout;
    private FrameLayout navigationDrawer;
    private ActionBarDrawerToggle drawerToggle;
    private RecyclerView recentRecyclerView;
    private ImageView homeLeftIcon;
    private View homeOpenFileButton;
    private EditText homeSearchInput;
    private View recentsHeaderRow;
    private View emptyRecentState;
    private View emptySearchState;
    private View retrySearchButton;
    private String currentSearchQuery = "";

    //kept package-private to use these in recyclerView's adapter
    TextView noRecentItemsTextView;

    private boolean isFabMenuOpen = false;
    private View editFAB;
    private View homeFabAnchor;
    private View newDocOverlay;
    private View newDocMenuCard;
    private View newDocCloseButton;

    /** Recent files list vs. grid switch. */
    private ImageView mRecentFilesListOrGrid;

    /** 首页 UI 是否已在 onCreate 中初始化（避免 onResume 全量 setContentView）。 */
    private boolean homeUiInitialized = false;

    /** 诊断：区分冷启动 onCreate vs 从文档返回 onResume（开屏根因排查）。 */
    private void logHomeLifecycle(String phase, Bundle savedInstanceState) {
        Log.i(LOGTAG, "home_lifecycle " + phase
                + " savedInstanceState=" + (savedInstanceState != null)
                + " pid=" + android.os.Process.myPid()
                + " taskId=" + getTaskId()
                + " isTaskRoot=" + isTaskRoot()
                + " homeUiInitialized=" + homeUiInitialized
                + " hash=" + System.identityHashCode(this));
    }

    /** Request code to evaluate that we are returning from the LOActivity. */
    private static final int LO_ACTIVITY_REQUEST_CODE = 42;
    private static final int OPEN_FILE_REQUEST_CODE = 43;
    private static final int CREATE_DOCUMENT_REQUEST_CODE = 44;
    private static final int CREATE_SPREADSHEET_REQUEST_CODE = 45;
    private static final int CREATE_PRESENTATION_REQUEST_CODE = 46;
    private static final int AI_PROFILE_SETTINGS_REQUEST_CODE = 47;
    private static final int AI_MODEL_SETTINGS_REQUEST_CODE = 48;
    private boolean pendingAutoOpenAiAfterCreate = false;
    private String pendingAutoAiPrompt = "";
    private String pendingAutoUserDescription = "";
    private AlertDialog aiGeneratingDialog;

    // 抽屉内二级面板：activity_ai_model_config.xml overlay（勿用 panel_ai_model_config，真机 ScrollView 灰屏）
    private View modelConfigPanel;
    private int modelConfigModelType = AiSettingsStore.MODEL_BASE;
    private EditText modelConfigNameInput;
    private EditText modelProviderInput;
    private EditText modelUrlInput;
    private EditText modelApiKeyInput;
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
    public void onCreate(Bundle savedInstanceState) {
        ExitDiagHelper.installOnce();
        ExitDiagHelper.logPreviousProcessDeaths(this);
        logHomeLifecycle("onCreate_enter", savedInstanceState);
        // Manifest 为 Splash 主题（首帧欢迎图）；此处切回正式主题后手动恢复 welcome 背景直至 createUI 完成
        setTheme(R.style.LibreOfficeTheme);
        PreferenceManager.setDefaultValues(this, R.xml.documentprovider_preferences, false);
        readPreferences();
        int mode = prefs.getInt(NIGHT_MODE_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
        // 仅冷启动显示欢迎图；进程被杀后 savedInstanceState!=null 时不铺 splash，避免退出回首页闪屏
        if (savedInstanceState == null) {
            getWindow().setBackgroundDrawable(getDrawable(R.drawable.lolib_splash_bg));
        }
        super.onCreate(savedInstanceState);

        // initialize document provider factory
        //DocumentProviderFactory.initialize(this);
        //documentProviderFactory = DocumentProviderFactory.getInstance();

        SettingsListenerModel.getInstance().setListener(this);

        // init UI and populate with contents from the provider
        createUI();
        homeUiInitialized = true;

        // createUI 完成后清除 window 欢迎图，避免挡住首页内容；onResume/onStart 再次确保清除
        getWindow().setBackgroundDrawable(null);
        logHomeLifecycle("onCreate_done", savedInstanceState);
    }

    private String[] getRecentDocuments() {
        String joinedStrings = prefs.getString(RECENT_DOCUMENTS_KEY, "");
        if (joinedStrings.isEmpty())
            return new String[]{};

        // we are using \n as delimiter
        return joinedStrings.split("\n", 0);
    }

    /** Update the recent files list. */
    public void updateRecentFiles() {
        String[] recentFileStrings = getRecentDocuments();
        final ArrayList<Uri> recentUris = new ArrayList<>();
        for (String recentFileString : recentFileStrings) {
            try {
                recentUris.add(Uri.parse(recentFileString));
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        final ArrayList<Uri> filteredUris = filterRecentUris(recentUris, currentSearchQuery);

        recentRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recentRecyclerView.setAdapter(new RecentFilesAdapter(this, filteredUris));
        updateEmptyState(recentUris.size(), filteredUris.size());
    }

    private ArrayList<Uri> filterRecentUris(ArrayList<Uri> source, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return source;
        }
        ArrayList<Uri> filtered = new ArrayList<>();
        for (Uri uri : source) {
            String filename = RecentFilesAdapter.getUriFilename(this, uri);
            if (filename != null && filename.toLowerCase().contains(normalized)) {
                filtered.add(uri);
            }
        }
        return filtered;
    }

    private void updateEmptyState(int totalCount, int filteredCount) {
        boolean hasQuery = !TextUtils.isEmpty(currentSearchQuery);
        if (recentsHeaderRow != null) {
            recentsHeaderRow.setVisibility(hasQuery || totalCount == 0 ? View.GONE : View.VISIBLE);
        }
        if (emptyRecentState != null) {
            emptyRecentState.setVisibility(!hasQuery && totalCount == 0 ? View.VISIBLE : View.GONE);
        }
        if (emptySearchState != null) {
            emptySearchState.setVisibility(hasQuery && filteredCount == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void updateHomeLeftAvatar() {
        if (homeLeftIcon == null) {
            return;
        }
        SharedPreferences p = AiSettingsStore.prefs(this);
        String avatarUri = p.getString(AiSettingsStore.KEY_PROFILE_AVATAR_URI, "");
        if (avatarUri == null || avatarUri.isEmpty()) {
            homeLeftIcon.setImageResource(R.drawable.drawer_header);
        } else {
            try {
                homeLeftIcon.setImageURI(Uri.parse(avatarUri));
            } catch (Exception ignored) {
                homeLeftIcon.setImageResource(R.drawable.drawer_header);
            }
        }
        homeLeftIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        homeLeftIcon.setPadding(0, 0, 0, 0);
        homeLeftIcon.setContentDescription(getString(R.string.ai_profile_name));
    }

    /** access shared preferences from the activity instance */
    public SharedPreferences getPrefs()
    {
        return prefs;
    }

    /** Create the Navigation menu and set up the actions and everything there. */
    public void setupNavigationDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationDrawer = findViewById(R.id.navigation_drawer);
        setupAiDrawerHeader(navigationDrawer);
        View localModelRow = navigationDrawer.findViewById(R.id.localModelRow);
        View localModelInstalledCard = navigationDrawer.findViewById(R.id.localModelInstalledCard);
        View localModelListRow = navigationDrawer.findViewById(R.id.localModelListRow);
        View localModelCopyButton = navigationDrawer.findViewById(R.id.localModelCopyButton);
        View localModelInstalledPrimaryRow = navigationDrawer.findViewById(R.id.localModelInstalledPrimaryRow);
        if (localModelRow != null) {
            if (org.libreoffice.androidlib.ai.LocalModelManager.isDeviceSupported(this)) {
                localModelRow.setOnClickListener(v -> openDrawerLocalModelEntry());
            }
        }
        if (localModelInstalledPrimaryRow != null) {
            localModelInstalledPrimaryRow.setOnClickListener(v ->
                    startActivity(new Intent(this, LocalModelActivity.class)));
        }
        if (localModelListRow != null) {
            localModelListRow.setOnClickListener(v -> showLocalModelListDialog());
        }
        if (localModelCopyButton != null) {
            localModelCopyButton.setOnClickListener(v -> copyLocalModelUrlToClipboard());
        }
        View localInstallButton = navigationDrawer.findViewById(R.id.localInstallButton);
        if (localInstallButton != null) {
            if (org.libreoffice.androidlib.ai.LocalModelManager.isDeviceSupported(this)) {
                localInstallButton.setOnClickListener(v -> showLocalModelListDialog());
            }
        }

        View clearCacheAction = navigationDrawer.findViewById(R.id.action_clear_cache);
        if (clearCacheAction != null) {
            clearCacheAction.setOnClickListener(v -> {
                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                startActivity(new android.content.Intent(this, ClearCacheActivity.class));
            });
        }
        View aboutAction = navigationDrawer.findViewById(R.id.action_about);
        if (aboutAction != null) {
            aboutAction.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new android.content.Intent(this, AboutActivity.class));
            });
        }
        setupDrawerToggle();
    }

    private void setupDrawerToggle() {
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.document_locations, R.string.close_document_locations) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

                /* TODO Currently we don't support sorting of the recent files
                switch (sortMode) {
                    case FileUtilities.SORT_SMALLEST:
                        menu.findItem(R.id.menu_sort_size_asc).setChecked(true);
                        break;

                    case FileUtilities.SORT_LARGEST:
                        menu.findItem(R.id.menu_sort_size_desc).setChecked(true);
                        break;

                    case FileUtilities.SORT_AZ:
                        menu.findItem(R.id.menu_sort_az).setChecked(true);
                        break;

                    case FileUtilities.SORT_ZA:
                        menu.findItem(R.id.menu_sort_za).setChecked(true);
                        break;

                    case FileUtilities.SORT_NEWEST:
                        menu.findItem(R.id.menu_sort_modified_newest).setChecked(true);
                        break;

                    case FileUtilities.SORT_OLDEST:
                        menu.findItem(R.id.menu_sort_modified_oldest).setChecked(true);
                        break;
                }

                switch (filterMode) {
                    case FileUtilities.ALL:
                        menu.findItem(R.id.menu_filter_everything).setChecked(true);
                        break;

                    case FileUtilities.DOC:
                        menu.findItem(R.id.menu_filter_documents).setChecked(true);
                        break;

                    case FileUtilities.CALC:
                        menu.findItem(R.id.menu_filter_presentations).setChecked(true);
                        break;

                    case FileUtilities.IMPRESS:
                        menu.findItem(R.id.menu_filter_presentations).setChecked(true);
                        break;
                }
                */

                supportInvalidateOptionsMenu();
                navigationDrawer.requestFocus(); // Make keypad navigation easier
                collapseFabMenu();
                // 打开抽屉时刷新本地模型状态，避免显示过期的「下载中」
                refreshAiDrawerHeader();
                ensureLocalModelSectionVisible();
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                dismissModelConfigPanelImmediate();
            }
        };
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
    }

    private void setupAiDrawerHeader(View headerView) {
        View profileEntry = headerView.findViewById(R.id.profileEntry);
        View profileEditButton = headerView.findViewById(R.id.profileEditButton);
        ImageView profileAvatar = headerView.findViewById(R.id.profileAvatar);

        if (profileEntry != null) {
            profileEntry.setOnClickListener(v -> openAiProfileSettings());
        }
        if (profileEditButton != null) {
            profileEditButton.setOnClickListener(v -> openAiProfileSettings());
        }
        if (profileAvatar != null) {
            profileAvatar.setClipToOutline(true);
            profileAvatar.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        }

        bindModelConfigEntry(headerView, R.id.modelItemBase, R.id.modelBaseArrow, AiSettingsStore.MODEL_BASE);
        bindModelConfigEntry(headerView, R.id.modelItemThink, R.id.modelThinkArrow, AiSettingsStore.MODEL_THINK);
        bindModelConfigEntry(headerView, R.id.modelItemImage, R.id.modelImageArrow, AiSettingsStore.MODEL_IMAGE);
        bindModelConfigEntry(headerView, R.id.modelItemVision, R.id.modelVisionArrow, AiSettingsStore.MODEL_VISION);
        setupAiConfigExpandToggle(headerView);
        refreshAiDrawerHeader();
    }

    private boolean aiConfigExpanded = true;

    private void setupAiConfigExpandToggle(View headerView) {
        ImageView toggle = headerView.findViewById(R.id.aiConfigExpandToggle);
        View headerRow = headerView.findViewById(R.id.aiConfigHeaderRow);
        View body = headerView.findViewById(R.id.aiConfigExpandableBody);
        if (toggle == null || body == null) {
            return;
        }
        updateAiConfigExpandedState(toggle, body, aiConfigExpanded);
        View.OnClickListener expandListener = v -> {
            aiConfigExpanded = !aiConfigExpanded;
            updateAiConfigExpandedState(toggle, body, aiConfigExpanded);
        };
        toggle.setOnClickListener(expandListener);
        if (headerRow != null) {
            headerRow.setOnClickListener(expandListener);
        }
    }

    private void updateAiConfigExpandedState(ImageView toggle, View body, boolean expanded) {
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        toggle.setImageResource(expanded
                ? R.drawable.ic_ai_config_chevron_up
                : R.drawable.ic_ai_config_chevron_down);
    }

    private void bindModelConfigEntry(View headerView, int rowId, int arrowId, int modelType) {
        View row = headerView.findViewById(rowId);
        View arrow = headerView.findViewById(arrowId);
        if (row != null) {
            row.setOnClickListener(v -> openModelConfig(modelType));
        }
        if (arrow != null) {
            arrow.setOnClickListener(v -> openModelConfig(modelType));
        }
    }

    private void openAiProfileSettings() {
        Intent intent = new Intent(this, AiProfileSettingsActivity.class);
        startActivityForResult(intent, AI_PROFILE_SETTINGS_REQUEST_CODE);
    }

    private void openModelConfig(int modelType) {
        if (modelConfigPanel != null) {
            return; // 面板已打开
        }
        Log.i("LOActivity", "ai_model_config_open_panel modelType=" + modelType + " via=drawer-panel");
        modelConfigModelType = modelType;

        setDrawerShellInteractive(false);

        View panel = LayoutInflater.from(this).inflate(R.layout.activity_ai_model_config, navigationDrawer, false);
        panel.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        View configColumn = panel.findViewById(R.id.aiModelConfigActivityPanel);
        if (configColumn != null) {
            configColumn.setClickable(true);
            configColumn.setFocusable(true);
        }
        bindModelConfigPanel(panel, false);
        final int slideWidth = getResources().getDimensionPixelSize(R.dimen.ai_drawer_panel_width);
        // 首帧即在屏外，避免 addView 后、动画开始前闪一下
        panel.setTranslationX(-slideWidth);
        navigationDrawer.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.bringToFront();
        modelConfigPanel = panel;

        View root = findViewById(R.id.overview_coordinator_layout);
        applyHomeDrawerSafeArea(SystemUiHelper.readSafeAreaInsets(root));
        loadModelConfigValues();
        if (modelConfigNameInput == null) {
            Log.e("LOActivity", "ai_model_config_bind_fail reason=modelConfigNameInput_null");
        }
        panel.post(() -> panel.animate()
                .translationX(0f)
                .setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start());
    }

    private void setDrawerShellInteractive(boolean interactive) {
        if (navigationDrawer == null) {
            return;
        }
        View shell = navigationDrawer.findViewById(R.id.navigation_drawer_panel);
        if (shell != null) {
            shell.setEnabled(interactive);
            shell.setClickable(interactive);
        }
    }

    private void bindModelConfigPanel(View panel, boolean loadValues) {
        modelConfigNameInput = panel.findViewById(R.id.modelConfigNameInput);
        modelProviderInput = panel.findViewById(R.id.modelProviderInput);
        modelUrlInput = panel.findViewById(R.id.modelUrlInput);
        modelApiKeyInput = panel.findViewById(R.id.modelApiKeyInput);
        modelNameInput = panel.findViewById(R.id.modelNameInput);
        topPBar = panel.findViewById(R.id.topPBar);
        temperatureBar = panel.findViewById(R.id.temperatureBar);
        presencePenaltyBar = panel.findViewById(R.id.presencePenaltyBar);
        frequencyPenaltyBar = panel.findViewById(R.id.frequencyPenaltyBar);
        maxTokensBar = panel.findViewById(R.id.maxTokensBar);
        seedBar = panel.findViewById(R.id.seedBar);
        topPValue = panel.findViewById(R.id.topPValue);
        temperatureValue = panel.findViewById(R.id.temperatureValue);
        presencePenaltyValue = panel.findViewById(R.id.presencePenaltyValue);
        frequencyPenaltyValue = panel.findViewById(R.id.frequencyPenaltyValue);
        maxTokensValue = panel.findViewById(R.id.maxTokensValue);
        seedValue = panel.findViewById(R.id.seedValue);

        TextView title = panel.findViewById(R.id.modelConfigTitle);
        ImageView icon = panel.findViewById(R.id.modelConnectionIcon);
        ImageButton backButton = panel.findViewById(R.id.modelConfigBackButton);
        if (title != null) {
            title.setText(AiSettingsStore.modelTitleRes(modelConfigModelType));
        }
        if (icon != null) {
            icon.setImageResource(R.drawable.ic_ai_connection);
        }
        if (backButton != null) {
            backButton.setOnClickListener(v -> closeModelConfigPanel());
        }

        bindModelConfigSliders();
        if (loadValues) {
            loadModelConfigValues();
        }
        bindModelConfigActions(panel);

        View scrim = panel.findViewById(R.id.modelConfigScrim);
        if (scrim != null) {
            // 抽屉内 356dp 宽，scrim 本来 0 宽，显式隐藏避免残留 focusable 节点
            scrim.setVisibility(View.GONE);
            scrim.setOnClickListener(v -> closeModelConfigPanel());
        }
    }

    private void loadModelConfigValues() {
        AiModelConfigStore.Form form = AiModelConfigStore.loadForm(this, modelConfigModelType,
                getString(AiSettingsStore.modelTitleRes(modelConfigModelType)) + "配置",
                AiSettingsStore.defaultModelName(modelConfigModelType));
        modelConfigNameInput.setText(form.configName);
        modelProviderInput.setText(form.provider);
        modelUrlInput.setText(form.url);
        modelApiKeyInput.setText(form.apiKey);
        modelNameInput.setText(form.modelName);

        setModelConfigSlider(topPBar, form.topP, topPValue);
        setModelConfigSlider(temperatureBar, form.temperature, temperatureValue);
        setModelConfigSlider(presencePenaltyBar, form.presencePenalty, presencePenaltyValue);
        setModelConfigSlider(frequencyPenaltyBar, form.frequencyPenalty, frequencyPenaltyValue);
        setModelConfigSlider(maxTokensBar, form.maxTokensRatio, maxTokensValue);
        setModelConfigSlider(seedBar, form.seedRatio, seedValue);
    }

    private void bindModelConfigSliders() {
        bindModelConfigSlider(topPBar, topPValue);
        bindModelConfigSlider(temperatureBar, temperatureValue);
        bindModelConfigSlider(presencePenaltyBar, presencePenaltyValue);
        bindModelConfigSlider(frequencyPenaltyBar, frequencyPenaltyValue);
        bindModelConfigSlider(maxTokensBar, maxTokensValue);
        bindModelConfigSlider(seedBar, seedValue);
    }

    private void bindModelConfigSlider(SeekBar seekBar, TextView valueView) {
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
                valueView.setText(formatModelConfigRatio(progress / 100f));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                persistModelConfig(false);
            }
        });
    }

    private void bindModelConfigActions(View panel) {
        View cancelButton = panel.findViewById(R.id.modelConfigCancelButton);
        View saveButton = panel.findViewById(R.id.modelConfigSaveButton);
        if (cancelButton != null) {
            cancelButton.setClickable(true);
            cancelButton.setFocusable(true);
            cancelButton.setOnClickListener(v -> closeModelConfigPanel());
        } else {
            Log.w("LOActivity", "ai_model_config_bind_fail reason=missing_cancel_button");
        }
        if (saveButton != null) {
            saveButton.setClickable(true);
            saveButton.setFocusable(true);
            saveButton.setOnClickListener(v -> {
                Log.i("LOActivity", "ai_model_config_save_click modelType=" + modelConfigModelType);
                View focus = getCurrentFocus();
                if (focus != null) {
                    focus.clearFocus();
                }
                saveModelConfigAndClose();
            });
        } else {
            Log.w("LOActivity", "ai_model_config_bind_fail reason=missing_save_button");
        }
    }

    private void saveModelConfigAndClose() {
        if (persistModelConfig(true)) {
            closeModelConfigPanel();
        }
    }

    /** @return true if persisted successfully */
    private boolean persistModelConfig(boolean showToast) {
        AiModelConfigStore.Form form = readModelConfigForm();
        if (!AiModelConfigStore.saveForm(this, modelConfigModelType, form)) {
            if (showToast) {
                Toast.makeText(this, R.string.ai_model_config_save_failed, Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        Log.i("LOActivity", "ai_model_config_saved modelType=" + modelConfigModelType
                + " topP=" + form.topP + " temperature=" + form.temperature);

        if (modelConfigModelType == AiSettingsStore.MODEL_BASE) {
            AiSettingsStore.syncBaseModelToRuntime(this);
        }

        if (showToast) {
            Toast.makeText(this, R.string.ai_model_config_saved, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private AiModelConfigStore.Form readModelConfigForm() {
        AiModelConfigStore.Form form = new AiModelConfigStore.Form();
        form.configName = readModelConfigInput(modelConfigNameInput);
        form.provider = readModelConfigInput(modelProviderInput);
        form.url = readModelConfigInput(modelUrlInput);
        form.apiKey = readModelConfigInput(modelApiKeyInput);
        form.modelName = readModelConfigInput(modelNameInput);
        form.topP = toModelConfigRatio(safeSeekBarProgress(topPBar));
        form.temperature = toModelConfigRatio(safeSeekBarProgress(temperatureBar));
        form.presencePenalty = toModelConfigRatio(safeSeekBarProgress(presencePenaltyBar));
        form.frequencyPenalty = toModelConfigRatio(safeSeekBarProgress(frequencyPenaltyBar));
        form.maxTokensRatio = toModelConfigRatio(safeSeekBarProgress(maxTokensBar));
        form.seedRatio = toModelConfigRatio(safeSeekBarProgress(seedBar));
        return form;
    }

    private int safeSeekBarProgress(SeekBar seekBar) {
        return seekBar == null ? 0 : seekBar.getProgress();
    }

    private String readModelConfigInput(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void setModelConfigSlider(SeekBar seekBar, float value, TextView valueView) {
        if (seekBar == null || valueView == null) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            seekBar.setMin(0);
        }
        seekBar.setMax(100);
        int progress = Math.max(0, Math.min(100, Math.round(value * 100f)));
        seekBar.setProgress(progress);
        valueView.setText(formatModelConfigRatio(progress / 100f));
    }

    private float toModelConfigRatio(int progress) {
        return Math.max(0f, Math.min(1f, progress / 100f));
    }

    private String formatModelConfigRatio(float value) {
        if (Math.abs(value - Math.round(value)) < 0.0001f) {
            return String.format(Locale.getDefault(), "%.0f", value);
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private void closeModelConfigPanel() {
        if (modelConfigPanel == null) {
            return;
        }
        View panel = modelConfigPanel;
        modelConfigPanel = null;
        panel.animate().cancel();
        final int slideWidth = getResources().getDimensionPixelSize(R.dimen.ai_drawer_panel_width);
        panel.animate()
                .translationX(-slideWidth)
                .setDuration(300)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    if (panel.getParent() == navigationDrawer) {
                        navigationDrawer.removeView(panel);
                    }
                })
                .start();
        setDrawerShellInteractive(true);
        refreshAiDrawerHeader();
    }

    /** 抽屉被关闭时无动画移除面板（onDrawerClosed 调用）。 */
    private void dismissModelConfigPanelImmediate() {
        if (modelConfigPanel != null) {
            navigationDrawer.removeView(modelConfigPanel);
            modelConfigPanel = null;
        }
        setDrawerShellInteractive(true);
    }

    private void refreshAiDrawerHeader() {
        if (navigationDrawer == null) {
            return;
        }
        View headerView = navigationDrawer;
        TextView profileName = headerView.findViewById(R.id.profileNameText);
        ImageView profileAvatar = headerView.findViewById(R.id.profileAvatar);
        TextView baseValue = headerView.findViewById(R.id.modelBaseValue);
        TextView thinkValue = headerView.findViewById(R.id.modelThinkValue);
        TextView imageValue = headerView.findViewById(R.id.modelImageValue);
        TextView visionValue = headerView.findViewById(R.id.modelVisionValue);

        SharedPreferences p = AiSettingsStore.prefs(this);
        String nickname = p.getString(AiSettingsStore.KEY_PROFILE_NAME, getString(R.string.ai_profile_name));
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = getString(R.string.ai_profile_name);
        }
        if (profileName != null) {
            profileName.setText(nickname);
        }

        String avatarUri = p.getString(AiSettingsStore.KEY_PROFILE_AVATAR_URI, "");
        if (profileAvatar != null) {
            if (avatarUri == null || avatarUri.isEmpty()) {
                profileAvatar.setImageResource(R.drawable.drawer_header);
            } else {
                try {
                    profileAvatar.setImageURI(Uri.parse(avatarUri));
                } catch (Exception ignored) {
                    profileAvatar.setImageResource(R.drawable.drawer_header);
                }
            }
        }
        updateHomeLeftAvatar();

        String unsetText = getString(R.string.ai_model_unset);
        if (baseValue != null) {
            baseValue.setText(AiSettingsStore.getModelDisplayName(this, AiSettingsStore.MODEL_BASE, unsetText));
        }
        if (thinkValue != null) {
            thinkValue.setText(AiSettingsStore.getModelDisplayName(this, AiSettingsStore.MODEL_THINK, unsetText));
        }
        if (imageValue != null) {
            imageValue.setText(AiSettingsStore.getModelDisplayName(this, AiSettingsStore.MODEL_IMAGE, unsetText));
        }
        if (visionValue != null) {
            visionValue.setText(AiSettingsStore.getModelDisplayName(this, AiSettingsStore.MODEL_VISION, unsetText));
        }

        TextView localInstallButton = headerView.findViewById(R.id.localInstallButton);
        View localModelSection = headerView.findViewById(R.id.localModelSection);
        View localModelRow = headerView.findViewById(R.id.localModelRow);
        View localModelInstalledCard = headerView.findViewById(R.id.localModelInstalledCard);
        TextView localInstalledBadge = headerView.findViewById(R.id.localInstalledBadge);
        TextView localModelDetailSubtitle = headerView.findViewById(R.id.localModelDetailSubtitle);
        TextView localModelDetailTitle = headerView.findViewById(R.id.localModelDetailTitle);
        View localModelDetailRow = headerView.findViewById(R.id.localModelDetailRow);
        View localModelCopyButton = headerView.findViewById(R.id.localModelCopyButton);

        if (!org.libreoffice.androidlib.ai.LocalModelManager.isDeviceSupported(this)) {
            if (localModelSection != null) {
                localModelSection.setVisibility(View.GONE);
            }
            return;
        }
        if (localModelSection != null) {
            localModelSection.setVisibility(View.VISIBLE);
        }
        if (localInstallButton == null) {
            return;
        }

        org.libreoffice.androidlib.ai.LocalModelManager manager =
                org.libreoffice.androidlib.ai.LocalModelManager.getInstance(this);
        String state = manager.getDownloadState();
        org.libreoffice.androidlib.ai.LocalModelManager.CatalogEntry activeEntry =
                manager.getInstalledCatalogEntry();
        org.libreoffice.androidlib.ai.LocalModelManager.CatalogEntry displayEntry = activeEntry;
        if (displayEntry == null) {
            displayEntry = findFirstDownloadedCatalogEntry(manager);
        }

        localInstallButton.setEnabled(true);
        localInstallButton.setAlpha(1f);

        if (org.libreoffice.androidlib.ai.LocalModelManager.STATE_DOWNLOADING.equals(state)) {
            showDrawerLocalModelInstallRow(localModelRow, localModelInstalledCard);
            localInstallButton.setVisibility(View.VISIBLE);
            if (manager.isDownloadActive()) {
                localInstallButton.setText(getString(R.string.local_model_downloading_progress,
                        manager.getLastProgressPercent()));
            } else {
                localInstallButton.setText(R.string.local_model_interrupted);
            }
            return;
        }

        if (manager.isInstalled()) {
            showDrawerLocalModelInstalledCard(localModelRow, localModelInstalledCard);
            if (localInstalledBadge != null) {
                localInstalledBadge.setText(R.string.local_model_installed_short);
            }
            bindDrawerLocalModelUrlRow(localModelDetailTitle, localModelDetailSubtitle,
                    localModelDetailRow, localModelCopyButton, displayEntry);
            return;
        }

        if (manager.hasAnyDownloadedModel()) {
            showDrawerLocalModelInstalledCard(localModelRow, localModelInstalledCard);
            if (localInstalledBadge != null) {
                localInstalledBadge.setText(R.string.local_model_pending_select);
            }
            bindDrawerLocalModelUrlRow(localModelDetailTitle, localModelDetailSubtitle,
                    localModelDetailRow, localModelCopyButton, displayEntry);
            return;
        }

        showDrawerLocalModelInstallRow(localModelRow, localModelInstalledCard);
        localInstallButton.setVisibility(View.VISIBLE);
        localInstallButton.setText(R.string.install);
    }

    private void openDrawerLocalModelEntry() {
        org.libreoffice.androidlib.ai.LocalModelManager manager =
                org.libreoffice.androidlib.ai.LocalModelManager.getInstance(this);
        if (manager.isInstalled() || manager.hasAnyDownloadedModel()) {
            startActivity(new Intent(this, LocalModelActivity.class));
        } else {
            showLocalModelListDialog();
        }
    }

    private static org.libreoffice.androidlib.ai.LocalModelManager.CatalogEntry findFirstDownloadedCatalogEntry(
            org.libreoffice.androidlib.ai.LocalModelManager manager) {
        for (org.libreoffice.androidlib.ai.LocalModelManager.CatalogEntry entry
                : org.libreoffice.androidlib.ai.LocalModelManager.getCatalogEntries()) {
            if (manager.isEntryDownloaded(entry)) {
                return entry;
            }
        }
        return null;
    }

    private static void showDrawerLocalModelInstallRow(View localModelRow, View localModelInstalledCard) {
        if (localModelRow != null) {
            localModelRow.setVisibility(View.VISIBLE);
        }
        if (localModelInstalledCard != null) {
            localModelInstalledCard.setVisibility(View.GONE);
        }
    }

    private static void showDrawerLocalModelInstalledCard(View localModelRow, View localModelInstalledCard) {
        if (localModelRow != null) {
            localModelRow.setVisibility(View.GONE);
        }
        if (localModelInstalledCard != null) {
            localModelInstalledCard.setVisibility(View.VISIBLE);
        }
    }

    private void bindDrawerLocalModelUrlRow(TextView titleView, TextView subtitleView, View detailRow,
            View copyButton, org.libreoffice.androidlib.ai.LocalModelManager.CatalogEntry entry) {
        boolean hasUrl = entry != null && !TextUtils.isEmpty(entry.url);
        if (detailRow != null) {
            detailRow.setVisibility(hasUrl ? View.VISIBLE : View.GONE);
        }
        if (titleView != null) {
            titleView.setText(R.string.local_model_url_label);
        }
        if (subtitleView != null) {
            subtitleView.setText(hasUrl ? entry.url : "");
        }
        if (copyButton != null) {
            copyButton.setVisibility(hasUrl ? View.VISIBLE : View.GONE);
        }
    }

    private void ensureLocalModelSectionVisible() {
        if (navigationDrawer == null) {
            return;
        }
        android.widget.ScrollView scroll = navigationDrawer.findViewById(R.id.drawer_ai_config_scroll);
        View section = navigationDrawer.findViewById(R.id.localModelSection);
        if (scroll == null || section == null || section.getVisibility() != View.VISIBLE) {
            return;
        }
        scroll.post(() -> {
            int sectionBottom = section.getBottom();
            int scrollHeight = scroll.getHeight();
            int scrollY = scroll.getScrollY();
            if (sectionBottom - scrollY > scrollHeight - dpToPx(8)) {
                scroll.smoothScrollTo(0, Math.max(0, sectionBottom - scrollHeight + dpToPx(16)));
            }
        });
    }

    public void createUI() {
        setContentView(R.layout.activity_document_browser);
        setupNavigationDrawer();

        homeLeftIcon = findViewById(R.id.homeLeftIcon);
        homeOpenFileButton = findViewById(R.id.homeOpenFileButton);
        homeSearchInput = findViewById(R.id.homeSearchInput);
        recentsHeaderRow = findViewById(R.id.recentsHeaderRow);
        emptyRecentState = findViewById(R.id.emptyRecentState);
        emptySearchState = findViewById(R.id.emptySearchState);
        retrySearchButton = findViewById(R.id.retrySearchButton);

        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.installDrawerInsetDispatch(drawerLayout, navigationDrawer);

        View coordinator = findViewById(R.id.overview_coordinator_layout);
        SystemUiHelper.trackSafeAreaChanges(coordinator, insets -> {
            applyHomeFabSafeArea(insets);
            applyHomeDrawerSafeArea(insets);
            SystemUiHelper.applyStatusBarPlateHeight(
                    findViewById(R.id.home_top_status_plate), insets);
            int homeStartPad = getResources().getDimensionPixelSize(R.dimen.home_top_bar_padding_start);
            int homeEndPad = getResources().getDimensionPixelSize(R.dimen.home_top_bar_padding_end);
            SystemUiHelper.applyHorizontalContentPadding(
                    findViewById(R.id.home_top_content_row),
                    insets.left + homeStartPad, insets.right + homeEndPad);
        });

        homeLeftIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        homeOpenFileButton.setOnClickListener(v -> openDocument());

        homeSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                currentSearchQuery = s == null ? "" : s.toString().trim();
                updateRecentFiles();
            }
        });

        if (retrySearchButton != null) {
            retrySearchButton.setOnClickListener(v -> {
                homeSearchInput.setText("");
                homeSearchInput.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(homeSearchInput.getWindowToken(), 0);
                }
            });
        }
        updateHomeLeftAvatar();

        setupFloatingActionButton();

        recentRecyclerView = findViewById(R.id.list_recent);
        setupHomeContentInsets();
        noRecentItemsTextView = findViewById(R.id.no_recent_items_msg);

        // Icon to switch showing the recent files as list vs. as grid
        mRecentFilesListOrGrid = (ImageView) findViewById(R.id.recent_list_or_grid);
        if (mRecentFilesListOrGrid != null) {
            mRecentFilesListOrGrid.setVisibility(View.GONE);
        }

        updateRecentFiles();
    }

    private void setupHomeContentInsets() {
        if (recentRecyclerView != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(recentRecyclerView, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(insets.left, 0, insets.right, insets.bottom);
                return windowInsets;
            });
            androidx.core.view.ViewCompat.requestApplyInsets(recentRecyclerView);
        }
    }

    /** Initialize the home new-document FAB. */
    private void setupFloatingActionButton() {
        editFAB = findViewById(R.id.editFAB);
        homeFabAnchor = findViewById(R.id.homeFabAnchor);
        newDocOverlay = findViewById(R.id.newDocOverlay);
        newDocMenuCard = findViewById(R.id.newDocMenuCard);
        newDocCloseButton = findViewById(R.id.newDocCloseButton);
        View writerRow = findViewById(R.id.newDocMenuRowWriter);
        View calcRow = findViewById(R.id.newDocMenuRowCalc);
        View impressRow = findViewById(R.id.newDocMenuRowImpress);

        if (LOActivity.isChromeOS(this)) {
            int dp = (int) getResources().getDisplayMetrics().density;
            applyChromeOsFabLayout(homeFabAnchor, dp);
        }

        editFAB.setOnClickListener(v -> {
            if (isFabMenuOpen) {
                collapseFabMenu();
            } else {
                expandFabMenu();
            }
        });

        if (newDocOverlay != null) {
            newDocOverlay.setOnClickListener(v -> collapseFabMenu());
        }
        if (newDocMenuCard != null) {
            newDocMenuCard.setOnClickListener(v -> {
                // Consume clicks on the menu so the transparent overlay does not dismiss.
            });
        }
        if (newDocCloseButton != null) {
            newDocCloseButton.setOnClickListener(v -> collapseFabMenu());
        }

        if (writerRow != null) {
            writerRow.setOnClickListener(v -> {
                collapseFabMenu();
                createNewFileInputDialog(getString(R.string.new_textdocument) + FileUtilities.DEFAULT_WRITER_EXTENSION, "application/vnd.oasis.opendocument.text", CREATE_DOCUMENT_REQUEST_CODE);
            });
        }
        if (calcRow != null) {
            calcRow.setOnClickListener(v -> {
                collapseFabMenu();
                createNewFileInputDialog(getString(R.string.new_spreadsheet) + FileUtilities.DEFAULT_SPREADSHEET_EXTENSION, "application/vnd.oasis.opendocument.spreadsheet", CREATE_SPREADSHEET_REQUEST_CODE);
            });
        }
        if (impressRow != null) {
            impressRow.setOnClickListener(v -> {
                collapseFabMenu();
                createNewFileInputDialog(getString(R.string.new_presentation) + FileUtilities.DEFAULT_IMPRESS_EXTENSION, "application/vnd.oasis.opendocument.presentation", CREATE_PRESENTATION_REQUEST_CODE);
            });
        }
    }

    private void applyChromeOsFabLayout(View fab, int dp) {
        if (fab == null) {
            return;
        }
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) fab.getLayoutParams();
        layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
        layoutParams.bottomMargin = dp * 24;
        layoutParams.width = dp * 70;
        layoutParams.height = dp * 70;
        fab.setLayoutParams(layoutParams);
    }

    private void applyHomeFabSafeArea(SafeAreaInsets insets) {
        applyHomeFabMargins(homeFabAnchor, insets);
    }

    private void applyHomeDrawerSafeArea(SafeAreaInsets insets) {
        View drawerPanel = findViewById(R.id.navigation_drawer_panel);
        if (drawerPanel != null) {
            drawerPanel.setPadding(insets.left, insets.top, insets.right, 0);
        }
        SystemUiHelper.applyDrawerFooterInsets(
                findViewById(R.id.navigation_drawer_footer), insets, dpToPx(12));
        if (modelConfigPanel != null) {
            View configHeader = modelConfigPanel.findViewById(R.id.modelConfigHeader);
            SystemUiHelper.applyDrawerHeaderInsets(configHeader, insets);
            View configFooter = modelConfigPanel.findViewById(R.id.modelConfigFooter);
            SystemUiHelper.applyDrawerFooterInsets(configFooter, insets,
                    getResources().getDimensionPixelSize(
                            org.libreoffice.androidapp.R.dimen.ai_model_config_footer_padding_v));
        }
    }

    private void applyHomeFabMargins(View fab, SafeAreaInsets insets) {
        if (fab == null) {
            return;
        }
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
        mlp.leftMargin = insets.left;
        mlp.rightMargin = insets.right
                + getResources().getDimensionPixelSize(R.dimen.home_fab_margin_end);
        mlp.bottomMargin = insets.bottom
                + (LOActivity.isChromeOS(this) ? dpToPx(24)
                : getResources().getDimensionPixelSize(R.dimen.home_fab_margin_bottom));
        fab.setLayoutParams(mlp);
    }

    private void copyLocalModelUrlToClipboard() {
        LocalModelManager manager = LocalModelManager.getInstance(this);
        LocalModelManager.CatalogEntry entry = manager.getInstalledCatalogEntry();
        if (entry == null) {
            entry = LocalModelManager.getDefaultCatalogEntry();
        }
        if (entry == null || TextUtils.isEmpty(entry.url)) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("local_model_url", entry.url));
            Toast.makeText(this, R.string.local_model_url_copied, Toast.LENGTH_SHORT).show();
        }
    }

    /** Expand the new-document menu overlay. */
    private void expandFabMenu() {
        if (isFabMenuOpen || newDocOverlay == null) {
            return;
        }
        newDocOverlay.setVisibility(View.VISIBLE);
        if (newDocMenuCard != null) {
            newDocMenuCard.setVisibility(View.VISIBLE);
        }
        editFAB.setVisibility(View.GONE);
        if (newDocCloseButton != null) {
            newDocCloseButton.setVisibility(View.VISIBLE);
        }
        isFabMenuOpen = true;
    }

    /** Collapse the new-document menu overlay. */
    private void collapseFabMenu() {
        if (!isFabMenuOpen || newDocOverlay == null) {
            return;
        }
        newDocOverlay.setVisibility(View.GONE);
        if (newDocMenuCard != null) {
            newDocMenuCard.setVisibility(View.GONE);
        }
        editFAB.setVisibility(View.VISIBLE);
        if (newDocCloseButton != null) {
            newDocCloseButton.setVisibility(View.GONE);
        }
        isFabMenuOpen = false;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        drawerToggle.syncState();
    }

    private void refreshView() {
        // refresh view
        updateRecentFiles();

        // close drawer if it was open
        drawerLayout.closeDrawer(navigationDrawer);
        collapseFabMenu();
    }

    @Override
    public void onBackPressed() {
        if (modelConfigPanel != null) {
            closeModelConfigPanel();
        } else if (drawerLayout.isDrawerOpen(navigationDrawer)) {
            drawerLayout.closeDrawer(navigationDrawer);
            collapseFabMenu();
        } else if (isFabMenuOpen) {
            collapseFabMenu();
        } else {
            // exit the app
            super.onBackPressed();
        }
    }

    public void showRecentFileActionsPopup(View anchor, Uri uri) {
        currentlySelectedFile = uri;
        Runnable showPopup = () -> showRecentFileActionsPopupInternal(anchor, uri);
        if (anchor.getWidth() <= 0 || anchor.getHeight() <= 0) {
            anchor.post(showPopup);
        } else {
            showPopup.run();
        }
    }

    private void showRecentFileActionsPopupInternal(View anchor, Uri uri) {
        View content = LayoutInflater.from(this).inflate(R.layout.popup_recent_file_actions, null, false);
        int popupWidth = getResources().getDimensionPixelSize(R.dimen.home_recent_actions_popup_width);
        int popupHeight = getResources().getDimensionPixelSize(R.dimen.home_recent_actions_popup_height);
        PopupWindow popup = new PopupWindow(content, popupWidth, popupHeight, true);
        popup.setElevation(dpToPx(8));
        popup.setOutsideTouchable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popup.setClippingEnabled(false);
        }

        View renameRow = content.findViewById(R.id.actionRenameRow);
        View shareRow = content.findViewById(R.id.actionShareRow);
        View removeRow = content.findViewById(R.id.actionRemoveRow);

        renameRow.setOnClickListener(v -> {
            popup.dismiss();
            showRenameDialog(uri);
        });
        shareRow.setOnClickListener(v -> {
            popup.dismiss();
            share(uri);
        });
        removeRow.setOnClickListener(v -> {
            popup.dismiss();
            showRemoveFromListConfirmDialog(uri);
        });

        int marginEnd = getResources().getDimensionPixelSize(R.dimen.home_file_row_padding_horizontal);
        int overlapIntoRow = dpToPx(8);
        int screenMargin = getResources().getDimensionPixelSize(
                R.dimen.home_recent_actions_popup_screen_margin);

        View decor = getWindow().getDecorView();
        int availableBottom = getAvailableScreenBottom(decor);
        int minTop = getAvailableScreenTop(decor) + screenMargin;

        ensureRoomForRecentActionsPopup(anchor, popupHeight, overlapIntoRow, availableBottom, screenMargin);

        int[] anchorOnScreen = new int[2];
        anchor.getLocationOnScreen(anchorOnScreen);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        // Figma 120:9789 — popup right edge aligns with row right padding; top slightly overlaps row.
        int xOnScreen = screenWidth - marginEnd - popupWidth;
        int yBelow = anchorOnScreen[1] + anchor.getHeight() - overlapIntoRow;
        int yAbove = anchorOnScreen[1] - popupHeight + overlapIntoRow;
        int yOnScreen;
        if (yBelow + popupHeight > availableBottom - screenMargin) {
            if (yAbove >= minTop) {
                yOnScreen = yAbove;
            } else {
                yOnScreen = Math.max(minTop, availableBottom - popupHeight - screenMargin);
            }
        } else {
            yOnScreen = yBelow;
        }

        int[] decorOnScreen = new int[2];
        decor.getLocationOnScreen(decorOnScreen);

        popup.showAtLocation(
                decor,
                Gravity.NO_GRAVITY,
                xOnScreen - decorOnScreen[0],
                yOnScreen - decorOnScreen[1]);
    }

    private int getAvailableScreenBottom(View decor) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets windowInsets = decor.getRootWindowInsets();
            if (windowInsets != null) {
                return screenHeight - windowInsets.getInsets(WindowInsets.Type.systemBars()).bottom;
            }
        }
        return screenHeight;
    }

    private int getAvailableScreenTop(View decor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets windowInsets = decor.getRootWindowInsets();
            if (windowInsets != null) {
                return windowInsets.getInsets(WindowInsets.Type.systemBars()).top;
            }
        }
        return 0;
    }

    private void ensureRoomForRecentActionsPopup(
            View anchor, int popupHeight, int overlapIntoRow, int availableBottom, int screenMargin) {
        View rowView = anchor;
        while (rowView != null && !(rowView.getParent() instanceof RecyclerView)) {
            if (rowView.getParent() instanceof View) {
                rowView = (View) rowView.getParent();
            } else {
                return;
            }
        }
        if (rowView == null || !(rowView.getParent() instanceof RecyclerView)) {
            return;
        }

        int[] anchorOnScreen = new int[2];
        anchor.getLocationOnScreen(anchorOnScreen);
        int popupBottom = anchorOnScreen[1] + anchor.getHeight() - overlapIntoRow + popupHeight;
        int overflow = popupBottom - (availableBottom - screenMargin);
        if (overflow > 0) {
            ((RecyclerView) rowView.getParent()).scrollBy(0, overflow);
        }
    }

    private void showRenameDialog(Uri uri) {
        if (uri == null) {
            return;
        }
        String currentName = RecentFilesAdapter.getUriFilename(this, uri);
        if (currentName == null || currentName.isEmpty()) {
            Toast.makeText(this, R.string.rename_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_rename_file_shell);

        View card = dialog.findViewById(R.id.renameDialogCard);
        EditText input = dialog.findViewById(R.id.renameFileInput);
        View closeButton = dialog.findViewById(R.id.renameDialogClose);
        View cancelButton = dialog.findViewById(R.id.renameDialogCancel);
        View confirmButton = dialog.findViewById(R.id.renameDialogConfirm);

        input.setText(currentName);
        input.setSelection(input.getText().length());

        if (card != null) {
            card.setOnClickListener(v -> { /* keep dialog open when tapping card */ });
        }
        Runnable dismiss = dialog::dismiss;
        closeButton.setOnClickListener(v -> dismiss.run());
        cancelButton.setOnClickListener(v -> dismiss.run());
        confirmButton.setOnClickListener(v -> {
            String newName = input.getText() == null ? "" : input.getText().toString().trim();
            if (newName.isEmpty()) {
                input.setError(getString(R.string.enter_filename));
                return;
            }
            if (newName.equals(currentName)) {
                dismiss.run();
                return;
            }
            dismiss.run();
            renameRecentFile(uri, newName);
        });

        ResponsiveUiHelper.applyKeyboardFriendlyDialogWindow(dialog);

        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        dialog.show();
    }

    private void renameRecentFile(Uri uri, String newName) {
        try {
            Uri renamedUri = DocumentsContract.renameDocument(getContentResolver(), uri, newName);
            if (renamedUri == null) {
                Toast.makeText(this, R.string.rename_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            String oldKey = uri.toString();
            String newKey = renamedUri.toString();
            String[] recentFileStrings = getRecentDocuments();
            StringBuilder joined = new StringBuilder();
            for (String recentFileString : recentFileStrings) {
                if (recentFileString.equals(oldKey)) {
                    if (joined.length() > 0) {
                        joined.append('\n');
                    }
                    joined.append(newKey);
                } else if (!recentFileString.isEmpty()) {
                    if (joined.length() > 0) {
                        joined.append('\n');
                    }
                    joined.append(recentFileString);
                }
            }
            long openTime = getRecentOpenTime(uri);
            prefs.edit()
                    .putString(RECENT_DOCUMENTS_KEY, joined.toString())
                    .remove(recentOpenTimeKey(uri))
                    .apply();
            if (openTime > 0L) {
                prefs.edit().putLong(recentOpenTimeKey(renamedUri), openTime).apply();
            }
            updateRecentFiles();
        } catch (Exception e) {
            Log.w(LOGTAG, "renameRecentFile failed", e);
            Toast.makeText(this, R.string.rename_failed, Toast.LENGTH_SHORT).show();
        }
    }

    public long getRecentOpenTime(Uri uri) {
        if (uri == null) {
            return 0L;
        }
        long stored = prefs.getLong(recentOpenTimeKey(uri), 0L);
        if (stored > 0L) {
            return stored;
        }
        return queryDocumentLastModified(uri);
    }

    private long queryDocumentLastModified(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    uri,
                    new String[]{DocumentsContract.Document.COLUMN_LAST_MODIFIED},
                    null,
                    null,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0L;
    }

    private String recentOpenTimeKey(Uri uri) {
        return RECENT_OPEN_TIME_PREFIX + uri.toString().hashCode();
    }

    private void touchRecentOpenTime(Uri uri) {
        if (uri == null) {
            return;
        }
        prefs.edit().putLong(recentOpenTimeKey(uri), System.currentTimeMillis()).apply();
    }

    public boolean isViewModeList() {
        return prefs.getString(EXPLORER_VIEW_TYPE_KEY, GRID_VIEW).equals(LIST_VIEW);
    }

    /** Change the view state (without updating the UI). */
    private void toggleViewMode() {
        if (isViewModeList())
            prefs.edit().putString(EXPLORER_VIEW_TYPE_KEY, GRID_VIEW).apply();
        else
            prefs.edit().putString(EXPLORER_VIEW_TYPE_KEY, LIST_VIEW).apply();
    }

    /** Build Intent to edit a Uri. */
    public Intent getIntentToEdit(Uri uri) {
        return getIntentToEdit(uri, false, "");
    }

    /** Build Intent to edit a Uri with optional auto-generate AI context. */
    public Intent getIntentToEdit(Uri uri, boolean autoOpenAi, String aiPrompt) {
        return getIntentToEdit(uri, autoOpenAi, aiPrompt, false);
    }

    /** Build Intent to edit a Uri with optional auto-generate AI context and initial edit mode. */
    public Intent getIntentToEdit(Uri uri, boolean autoOpenAi, String aiPrompt, boolean startInEditMode) {
        Intent i = new Intent(Intent.ACTION_EDIT, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        String packageName = getApplicationContext().getPackageName();
        ComponentName componentName = new ComponentName(packageName, LOActivity.class.getName());
        i.setComponent(componentName);
        if (autoOpenAi) {
            i.putExtra(LOActivity.EXTRA_AUTO_GENERATE_AI_CONTENT, true);
            if (!TextUtils.isEmpty(aiPrompt)) {
                i.putExtra(LOActivity.EXTRA_AUTO_OPEN_AI_PROMPT, aiPrompt);
            }
        }
        i.putExtra(LOActivity.EXTRA_START_IN_EDIT_MODE, startInEditMode);

        return i;
    }

    /** Start editing of the given Uri. */
    public void open(final Uri uri) {
        open(uri, false, "", false);
    }

    public void open(final Uri uri, boolean autoOpenAi, String aiPrompt) {
        open(uri, autoOpenAi, aiPrompt, false);
    }

    public void open(final Uri uri, boolean autoOpenAi, String aiPrompt, boolean startInEditMode) {
        open(uri, autoOpenAi, aiPrompt, startInEditMode, "");
    }

    public void open(final Uri uri, boolean autoOpenAi, String aiPrompt, boolean startInEditMode, String userDescription) {
        open(uri, autoOpenAi, aiPrompt, startInEditMode, userDescription, -1);
    }

    public void open(final Uri uri, boolean autoOpenAi, String aiPrompt, boolean startInEditMode, String userDescription, int requestCode) {
        if (uri == null)
            return;

        addDocumentToRecents(uri);

        Intent i = getIntentToEdit(uri, autoOpenAi, aiPrompt, startInEditMode);
        if (!TextUtils.isEmpty(userDescription)) {
            i.putExtra(LOActivity.EXTRA_AUTO_USER_DESCRIPTION, userDescription);
        }
        if (requestCode == CREATE_SPREADSHEET_REQUEST_CODE) {
            i.putExtra(LOActivity.EXTRA_AUTO_IS_CALC_NEW_TABLE, true);
        }
        startActivityForResult(i, LO_ACTIVITY_REQUEST_CODE);
    }

    /** Opens an Input dialog to get the name of new file. */
    private void createNewFileInputDialog(final String defaultFileName, final String mimeType, final int requestCode) {
        collapseFabMenu();
        showCreateFileBottomSheet(defaultFileName, mimeType, requestCode);
    }

    private void showCreateFileBottomSheet(final String defaultFileName, final String mimeType, final int requestCode) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_create_file_shell);

        View card = dialog.findViewById(R.id.createDialogCard);
        TextView createTitle = dialog.findViewById(R.id.createTitle);
        EditText fileNameInput = dialog.findViewById(R.id.fileName);
        TextView overwriteWarning = dialog.findViewById(R.id.overwriteWarning);
        TextView createButton = dialog.findViewById(R.id.createButton);
        ImageButton closeButton = dialog.findViewById(R.id.closeButton);
        View aiSwitchTrack = dialog.findViewById(R.id.aiSwitch);
        View aiSwitchThumb = dialog.findViewById(R.id.aiSwitchThumb);
        final EditText aiPromptInput = dialog.findViewById(R.id.aiPromptInput);
        final boolean[] aiSwitchChecked = {false};
        final int aiSwitchThumbMarginPx = (int) (2.5f * getResources().getDisplayMetrics().density);

        Runnable updateAiSwitchUi = () -> {
            aiSwitchTrack.setBackgroundResource(R.drawable.bg_ai_switch_track_off);
            FrameLayout.LayoutParams thumbParams = (FrameLayout.LayoutParams) aiSwitchThumb.getLayoutParams();
            if (aiSwitchChecked[0]) {
                thumbParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
                thumbParams.setMarginStart(0);
                thumbParams.setMarginEnd(aiSwitchThumbMarginPx);
            } else {
                thumbParams.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
                thumbParams.setMarginStart(aiSwitchThumbMarginPx);
                thumbParams.setMarginEnd(0);
            }
            aiSwitchThumb.setLayoutParams(thumbParams);
        };

        aiSwitchTrack.setOnClickListener(v -> {
            aiSwitchChecked[0] = !aiSwitchChecked[0];
            updateAiSwitchUi.run();
            aiPromptInput.setVisibility(aiSwitchChecked[0] ? View.VISIBLE : View.GONE);
            if (aiSwitchChecked[0] && requestCode == CREATE_SPREADSHEET_REQUEST_CODE) {
                aiPromptInput.setHint("描述你想生成的表格内容，例如：2024年各季度销售数据表");
            } else if (aiSwitchChecked[0]) {
                aiPromptInput.setHint("描述你想生成的文档内容...");
            }
        });

        final String extension = getExtensionForRequestCode(requestCode);
        final String baseName = trimFileExtension(defaultFileName);
        final CreateSheetStyle style = getCreateSheetStyle(requestCode);

        createTitle.setText(style.titleResId);
        createButton.setText(style.buttonTextResId);
        applyCreateButtonColor(createButton, style.buttonColorHex);
        fileNameInput.setText(baseName);
        fileNameInput.setSelection(fileNameInput.getText().length());
        overwriteWarning.setVisibility(View.GONE);

        if (card != null) {
            ResponsiveUiHelper.applyAdaptiveSheetCardLayout(this, card);
            if (ResponsiveUiHelper.useBottomSheetPresentation(this)) {
                ResponsiveUiHelper.applyBottomSheetContentSafePadding(card,
                        getResources().getDimensionPixelSize(R.dimen.bottom_sheet_content_bottom_pad));
            }
            card.setOnClickListener(v -> { /* keep dialog open when tapping card */ });
        }
        closeButton.setOnClickListener(v -> dialog.dismiss());
        createButton.setOnClickListener(v -> {
            String name = fileNameInput.getText() == null ? "" : fileNameInput.getText().toString().trim();
            if (name.isEmpty()) {
                fileNameInput.setError(getString(R.string.enter_filename));
                return;
            }

            if (aiSwitchChecked[0]) {
                String userDescription = aiPromptInput.getText() == null ? "" : aiPromptInput.getText().toString().trim();
                if (requestCode == CREATE_SPREADSHEET_REQUEST_CODE && userDescription.isEmpty()) {
                    aiPromptInput.setError("请描述你想生成的表格内容");
                    return;
                }
                pendingAutoOpenAiAfterCreate = true;
                pendingAutoUserDescription = userDescription;
                pendingAutoAiPrompt = buildAutoAiPromptForRequestCode(requestCode, name, pendingAutoUserDescription);
            } else {
                pendingAutoOpenAiAfterCreate = false;
                pendingAutoUserDescription = "";
                pendingAutoAiPrompt = "";
            }

            String finalFileName = ensureExtension(name, extension);
            dialog.dismiss();
            LOActivity.createNewFileInputDialog(this, finalFileName, mimeType, requestCode);
        });

        ResponsiveUiHelper.applyAdaptiveSheetWindow(this, dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }

        View root = dialog.findViewById(R.id.createDialogRoot);
        if (root != null) {
            root.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.setCanceledOnTouchOutside(true);

        dialog.setOnShowListener(d -> {
            fileNameInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(fileNameInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        dialog.show();
    }

    private void showRemoveFromListConfirmDialog(Uri uri) {
        if (uri == null) {
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm_message_shell);

        View card = dialog.findViewById(R.id.confirmDialogCard);
        TextView titleView = dialog.findViewById(R.id.confirmDialogTitle);
        TextView messageView = dialog.findViewById(R.id.confirmDialogMessage);
        View closeButton = dialog.findViewById(R.id.confirmDialogClose);
        View cancelButton = dialog.findViewById(R.id.confirmDialogCancel);
        View confirmButton = dialog.findViewById(R.id.confirmDialogConfirm);

        if (titleView != null) {
            titleView.setText(R.string.remove_from_list);
        }
        if (messageView != null) {
            messageView.setText(R.string.remove_from_list_confirm_message);
        }

        if (card != null) {
            card.setOnClickListener(v -> { /* keep dialog open when tapping card */ });
        }
        Runnable dismiss = dialog::dismiss;
        closeButton.setOnClickListener(v -> dismiss.run());
        cancelButton.setOnClickListener(v -> dismiss.run());
        confirmButton.setOnClickListener(v -> {
            dismiss.run();
            removeFromList(uri);
        });

        ResponsiveUiHelper.applyOverlayDialogWindow(dialog);

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void showLocalModelListDialog() {
        if (!LocalModelManager.isDeviceSupported(this)) {
            Toast.makeText(this, R.string.local_model_device_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        if (drawerLayout != null && navigationDrawer != null
                && drawerLayout.isDrawerOpen(navigationDrawer)) {
            drawerLayout.closeDrawer(navigationDrawer);
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_local_model_list_shell);

        View root = dialog.findViewById(R.id.localModelListDialogRoot);
        View card = dialog.findViewById(R.id.localModelListDialogCard);
        LinearLayout container = dialog.findViewById(R.id.localModelListContainer);
        View closeButton = dialog.findViewById(R.id.localModelListCloseButton);
        View confirmButton = dialog.findViewById(R.id.localModelListConfirmButton);

        final LocalModelManager modelManager = LocalModelManager.getInstance(this);

        final LocalModelListUiHelper.Controller[] listController = { null };
        listController[0] = new LocalModelListUiHelper.Controller(
                container, this, modelManager,
                new LocalModelListUiHelper.ModelRowListener() {
                    @Override
                    public void onDownloadRequested(LocalModelManager.CatalogEntry entry) {
                        startLocalModelDownloadFromDialog(modelManager, entry, listController[0]);
                    }

                    @Override
                    public void onSelectRequested(LocalModelManager.CatalogEntry entry) {
                        selectLocalModelFromDialog(modelManager, entry, listController[0]);
                    }
                });

        Runnable dismissDialog = () -> {
            dialog.dismiss();
            refreshAiDrawerHeader();
        };

        listController[0].refreshAll();

        if (root != null) {
            root.setOnClickListener(v -> dismissDialog.run());
        }
        if (card != null) {
            ResponsiveUiHelper.applyDialogCardMaxWidth(this, card);
            card.setOnClickListener(v -> { /* keep dialog open when tapping card */ });
        }
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismissDialog.run());
        }
        if (confirmButton != null) {
            confirmButton.setOnClickListener(v -> dismissDialog.run());
        }

        ResponsiveUiHelper.applyOverlayDialogWindow(dialog);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void selectLocalModelFromDialog(LocalModelManager modelManager,
            LocalModelManager.CatalogEntry entry, LocalModelListUiHelper.Controller listController) {
        modelManager.selectActiveModel(entry);
        if (listController != null) {
            listController.refreshAll();
        }
        refreshAiDrawerHeader();
        Toast.makeText(this, getString(R.string.local_model_switched, entry.displayName),
                Toast.LENGTH_SHORT).show();
    }

    private void startLocalModelDownloadFromDialog(LocalModelManager modelManager,
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
                    refreshAiDrawerHeader();
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
                    refreshAiDrawerHeader();
                    if (success) {
                        Toast.makeText(LibreOfficeUIActivity.this, R.string.local_model_download_done,
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
                    refreshAiDrawerHeader();
                    if ("cancelled".equals(code) || "download_busy".equals(code)) {
                        return;
                    }
                    Toast.makeText(LibreOfficeUIActivity.this,
                            getString(R.string.local_model_download_failed, message),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void applyCreateButtonColor(TextView createButton, String buttonColorHex) {
        if (createButton == null) {
            return;
        }
        GradientDrawable background = (GradientDrawable) createButton.getBackground().mutate();
        background.setColor(Color.parseColor(buttonColorHex));
    }

    private String getExtensionForRequestCode(int requestCode) {
        switch (requestCode) {
            case CREATE_SPREADSHEET_REQUEST_CODE:
                return FileUtilities.DEFAULT_SPREADSHEET_EXTENSION;
            case CREATE_PRESENTATION_REQUEST_CODE:
                return FileUtilities.DEFAULT_IMPRESS_EXTENSION;
            case CREATE_DOCUMENT_REQUEST_CODE:
            default:
                return FileUtilities.DEFAULT_WRITER_EXTENSION;
        }
    }

    private String trimFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private String ensureExtension(String fileName, String extension) {
        if (extension == null || extension.isEmpty()) {
            return fileName;
        }
        String normalizedExtension = extension.startsWith(".") ? extension : "." + extension;
        if (fileName.toLowerCase().endsWith(normalizedExtension.toLowerCase())) {
            return fileName;
        }
        return fileName + normalizedExtension;
    }

    private CreateSheetStyle getCreateSheetStyle(int requestCode) {
        switch (requestCode) {
            case CREATE_SPREADSHEET_REQUEST_CODE:
                return new CreateSheetStyle(
                        R.string.create_new_spreadsheet_title,
                        R.string.create_empty_spreadsheet,
                        "#3B8040"
                );
            case CREATE_PRESENTATION_REQUEST_CODE:
                return new CreateSheetStyle(
                        R.string.create_new_presentation_title,
                        R.string.create_empty_presentation,
                        "#EC5D1F"
                );
            case CREATE_DOCUMENT_REQUEST_CODE:
            default:
                return new CreateSheetStyle(
                        R.string.create_new_text_document_title,
                        R.string.create_empty_text_document,
                        "#1278D9"
                );
        }
    }

    private static class CreateSheetStyle {
        final int titleResId;
        final int buttonTextResId;
        final String buttonColorHex;

        CreateSheetStyle(int titleResId, int buttonTextResId, String buttonColorHex) {
            this.titleResId = titleResId;
            this.buttonTextResId = buttonTextResId;
            this.buttonColorHex = buttonColorHex;
        }
    }

    private String buildAutoAiPromptForRequestCode(int requestCode, String title, String userDescription) {
        boolean hasTitle = title != null && !title.isEmpty();
        boolean hasDesc = userDescription != null && !userDescription.isEmpty();
        StringBuilder sb = new StringBuilder();
        if (hasTitle) {
            sb.append("主题：《").append(title).append("》");
        }
        if (hasDesc) {
            if (hasTitle) sb.append("，");
            sb.append("用户要求：").append(userDescription);
        }
        String prefix = (hasTitle || hasDesc)
                ? sb.append("。请围绕以上要求，").toString()
                : "请";
        switch (requestCode) {
            case CREATE_SPREADSHEET_REQUEST_CODE:
                return prefix + "先给出一个清晰的数据表结构大纲（列名和用途），再输出可直接粘贴到电子表格的完整示例内容，至少包含10行数据。";
            case CREATE_PRESENTATION_REQUEST_CODE:
                return prefix + "先生成一份6页演示的大纲（每页标题+要点），再输出可直接用于演示文稿的完整正文内容。";
            case CREATE_DOCUMENT_REQUEST_CODE:
            default:
                return prefix + "直接输出完整的纯文本正文，风格专业、结构清晰。不要使用 Markdown 格式（不要使用 #、*、- 等标记符号）。";
        }
    }

    /**
     * Creates a new file at the specified path, by copying an empty template to that location.
     *
     * @param uri       uri that we should overwrite with the new file content
     * @param extension is required to know what template should be used when creating the document
     */
    private void createNewFile(final Uri uri, final String extension) {
        createNewFileAsync(uri, extension, null);
    }

    private void createNewFileAsync(final Uri uri, final String extension, final Runnable onDone) {
        Thread createThread = new Thread(() -> {
            String normalizedExtension = extension.startsWith(".") ? extension.substring(1) : extension;
            try (InputStream templateFileStream = getAssets().open("templates/untitled." + normalizedExtension);
                 OutputStream newFileStream = getContentResolver().openOutputStream(uri)) {
                if (newFileStream == null) {
                    Log.e(LOGTAG, "Unable to open output stream for uri: " + uri);
                } else {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = templateFileStream.read(buffer)) > 0) {
                        newFileStream.write(buffer, 0, length);
                    }
                    newFileStream.flush();
                }
            } catch (IOException e) {
                Log.e(LOGTAG, "Failed to create new file", e);
            } finally {
                runOnUiThread(() -> {
                    if (onDone != null) {
                        onDone.run();
                    }
                });
            }
        }, "create-new-file");
        createThread.start();
    }

    private void showAiGeneratingDialog() {
        if (aiGeneratingDialog != null && aiGeneratingDialog.isShowing()) {
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_ai_generating, null, false);
        ProgressBar spinner = view.findViewById(R.id.aiGeneratingSpinner);
        spinner.setIndeterminate(true);
        aiGeneratingDialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .create();
        aiGeneratingDialog.show();
    }

    private void dismissAiGeneratingDialog() {
        if (aiGeneratingDialog != null && aiGeneratingDialog.isShowing()) {
            aiGeneratingDialog.dismiss();
        }
        aiGeneratingDialog = null;
    }

    /** Context menu item handling. */
    private void open(int position) {
        /*
        IFile file = filePaths.get(position);
        if (!file.isDirectory()) {
            open(file);
        } else {
            openDirectory(file);
        }
        */
    }

    /** Context menu item handling. */
    private void share(Uri uri) {
        if (uri == null)
            return;

        Intent intentShareFile = new Intent(Intent.ACTION_SEND);
        intentShareFile.putExtra(Intent.EXTRA_STREAM, uri);
        intentShareFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intentShareFile.setDataAndType(uri, LibreOfficeUIActivity.this.getContentResolver().getType(uri));
        LibreOfficeUIActivity.this.startActivity(Intent.createChooser(intentShareFile, LibreOfficeUIActivity.this.getString(R.string.share_document)));
    }

    /** Context menu item handling. */
    private void removeFromList(Uri uri) {
        if (uri == null)
            return;

        String[] recentFileStrings = getRecentDocuments();
        String joined = "";
        final ArrayList<Uri> recentUris = new ArrayList<Uri>();

        for (String recentFileString : recentFileStrings) {
            try {
                if (!uri.toString().equals(recentFileString)) {
                    recentUris.add(Uri.parse(recentFileString));
                    joined = joined.concat(recentFileString+"\n");
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }

        prefs.edit().putString(RECENT_DOCUMENTS_KEY, joined).apply();
        updateRecentFiles();
    }

    /** Setup the toolbar's menu. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.view_menu, menu);

        return true;
    }

    /** Start an ACTION_OPEN_DOCUMENT Intent to trigger opening a document. */
    private void openDocument() {
        collapseFabMenu();

        Intent i = new Intent();
        i.addCategory(Intent.CATEGORY_OPENABLE);

        // set only the allowed mime types
        // NOTE: If updating the list here, also check the AndroidManifest.xml,
        // I didn't find a way how to do it from one central place :-(
        i.setType("*/*");

        // from some reason, the file picker on ChromeOS is confused when it
        // gets the EXTRA_MIME_TYPES; to the user it looks like it is
        // impossible to choose any files, unless they notice the dropdown in
        // the bottom left and choose "All files".  Interestingly, SVG / SVGZ
        // are shown there as an option, the other mime types are just blank
        if (!LOActivity.isChromeOS(this)) {
            final String[] mimeTypes = new String[] {
                // ODF
                "application/vnd.oasis.opendocument.text",
                "application/vnd.oasis.opendocument.graphics",
                "application/vnd.oasis.opendocument.presentation",
                "application/vnd.oasis.opendocument.spreadsheet",
                "application/vnd.oasis.opendocument.text-flat-xml",
                "application/vnd.oasis.opendocument.graphics-flat-xml",
                "application/vnd.oasis.opendocument.presentation-flat-xml",
                "application/vnd.oasis.opendocument.spreadsheet-flat-xml",

                // ODF templates
                "application/vnd.oasis.opendocument.text-template",
                "application/vnd.oasis.opendocument.spreadsheet-template",
                "application/vnd.oasis.opendocument.graphics-template",
                "application/vnd.oasis.opendocument.presentation-template",

                // MS
                "application/rtf",
                "text/rtf",
                "application/msword",
                "application/vnd.ms-powerpoint",
                "application/vnd.ms-excel",
                "application/vnd.visio",
                "application/vnd.visio.xml",
                "application/x-mspublisher",
                "application/vnd.ms-excel.sheet.binary.macroenabled.12",
                "application/vnd.ms-excel.sheet.macroenabled.12",

                // OOXML
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",

                // OOXML templates
                "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
                "application/vnd.openxmlformats-officedocument.presentationml.template",

                // other
                "text/csv",
                "text/plain",
                "text/comma-separated-values",
                "application/vnd.ms-works",
                "application/vnd.apple.keynote",
                "application/x-abiword",
                "application/x-pagemaker",
                "image/x-emf",
                "image/x-svm",
                "image/x-wmf",
                "image/svg+xml",
                "application/pdf"
            };
            i.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }

        // TODO remember where the user picked the file the last time
        // TODO and that should default to Context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        //i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, previousDirectoryPath);

        try {
            i.setAction(Intent.ACTION_OPEN_DOCUMENT);
            startActivityForResult(i, OPEN_FILE_REQUEST_CODE);
            return;
        } catch (ActivityNotFoundException exception) {
            Log.w(LOGTAG, "Start of activity with ACTION_OPEN_DOCUMENT failed (no activity found). Trying the fallback.");
        }
        // Fallback
        i.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(i, OPEN_FILE_REQUEST_CODE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Will close the drawer if the home button is pressed
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.action_open_file:
                openDocument();
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    public void readPreferences() {
        prefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
        sortMode = prefs.getInt(SORT_MODE_KEY, FileUtilities.SORT_AZ);
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        filterMode = Integer.valueOf(defaultPrefs.getString(FILTER_MODE_KEY, "-1"));
        showHiddenFiles = defaultPrefs.getBoolean(ENABLE_SHOW_HIDDEN_FILES_KEY, false);

        Intent i = this.getIntent();
        if (i.hasExtra(CURRENT_DIRECTORY_KEY)) {
            /*try {
                currentDirectory = documentProvider.createFromUri(this, new URI(
                        i.getStringExtra(CURRENT_DIRECTORY_KEY)));
            } catch (URISyntaxException e) {
                currentDirectory = documentProvider.getRootDirectory(this);
            }*/
            Log.d(LOGTAG, CURRENT_DIRECTORY_KEY);
        }

        if (i.hasExtra(FILTER_MODE_KEY)) {
            filterMode = i.getIntExtra(FILTER_MODE_KEY, FileUtilities.ALL);
            Log.d(LOGTAG, FILTER_MODE_KEY);
        }
    }


    @Override
    public void settingsPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        readPreferences();
        refreshView();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub
        super.onSaveInstanceState(outState);

        outState.putInt(FILTER_MODE_KEY, filterMode);

        outState.putBoolean(ENABLE_SHOW_HIDDEN_FILES_KEY, showHiddenFiles);

        Log.i(LOGTAG, "home_lifecycle onSaveInstanceState pid=" + android.os.Process.myPid()
                + " homeUiInitialized=" + homeUiInitialized);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.isEmpty()) {
            return;
        }
        /*if (documentProvider == null) {
            Log.d(LOGTAG, "onRestoreInstanceState - documentProvider is null");
            documentProvider = DocumentProviderFactory.getInstance()
                    .getProvider(savedInstanceState.getInt(DOC_PROVIDER_KEY));
        }
        try {
            currentDirectory = documentProvider.createFromUri(this, new URI(
                    savedInstanceState.getString(CURRENT_DIRECTORY_KEY)));
        } catch (URISyntaxException e) {
            currentDirectory = documentProvider.getRootDirectory(this);
        }*/
        filterMode = savedInstanceState.getInt(FILTER_MODE_KEY, FileUtilities.ALL);
        showHiddenFiles = savedInstanceState.getBoolean(ENABLE_SHOW_HIDDEN_FILES_KEY, false);
        //openDirectory(currentDirectory);
        Log.d(LOGTAG, "onRestoreInstanceState");
        //Log.d(LOGTAG, currentDirectory.toString() + Integer.toString(filterMode));
    }

    /** Uploading back when we return from the LOActivity. */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case LO_ACTIVITY_REQUEST_CODE: {
                Log.i(LOGTAG, "home_return_from_doc resultCode=" + resultCode
                        + " pid=" + android.os.Process.myPid()
                        + " homeUiInitialized=" + homeUiInitialized);
                updateRecentFiles();
                if (resultCode == RESULT_OK) {
                    ExitDiagHelper.markDocExitToHome();
                    ExitDiagHelper.schedulePostHomeReturnWatchdog();
                }
                break;
            }
            case OPEN_FILE_REQUEST_CODE: {
                Log.d(LOGTAG, "File open chooser has finished, starting the LOActivity.");
                if (resultCode != RESULT_OK || data == null)
                    return;

                Uri uri = data.getData();
                if (uri == null)
                    return;

                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                open(uri);
                break;
            }
            case CREATE_DOCUMENT_REQUEST_CODE:
            case CREATE_SPREADSHEET_REQUEST_CODE:
            case CREATE_PRESENTATION_REQUEST_CODE: {
                if (resultCode != RESULT_OK || data == null) {
                    pendingAutoOpenAiAfterCreate = false;
                    pendingAutoAiPrompt = "";
                    pendingAutoUserDescription = "";
                    dismissAiGeneratingDialog();
                    return;
                }

                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                String extension = (requestCode == CREATE_DOCUMENT_REQUEST_CODE) ? "odt" : ((requestCode == CREATE_SPREADSHEET_REQUEST_CODE) ? "ods" : "odp");
                final boolean autoOpenAi = pendingAutoOpenAiAfterCreate;
                final String autoAiPrompt = pendingAutoAiPrompt;
                final String autoUserDescription = pendingAutoUserDescription;
                if (autoOpenAi) {
                    showAiGeneratingDialog();
                } else {
                    dismissAiGeneratingDialog();
                }
                createNewFileAsync(uri, extension, () -> {
                    dismissAiGeneratingDialog();
                    open(uri, autoOpenAi, autoAiPrompt, true, autoUserDescription, requestCode);
                });
                pendingAutoOpenAiAfterCreate = false;
                pendingAutoAiPrompt = "";
                pendingAutoUserDescription = "";
                break;
            }
            case AI_PROFILE_SETTINGS_REQUEST_CODE:
            case AI_MODEL_SETTINGS_REQUEST_CODE: {
                refreshAiDrawerHeader();
                if (resultCode == AiSettingsStore.RESULT_BACK_TO_DRAWER
                        && drawerLayout != null) {
                    drawerLayout.post(() -> drawerLayout.openDrawer(GravityCompat.START));
                }
                break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        logHomeLifecycle("onPause", null);
        ExitDiagHelper.logHomeLifecycleAfterDocExit("onPause");
    }

    @Override
    protected void onResume() {
        super.onResume();
        logHomeLifecycle("onResume_enter", null);
        getWindow().setBackgroundDrawable(null);
        final long resumeStart = System.currentTimeMillis();
        Log.d(LOGTAG, "onResume homeUiInitialized=" + homeUiInitialized);
        Log.d(LOGTAG, "sortMode=" + sortMode + " filterMode=" + filterMode);
        if (homeUiInitialized) {
            Log.i(LOGTAG, "home_resume incremental=true");
            updateRecentFiles();
            refreshAiDrawerHeader();
        } else {
            Log.i(LOGTAG, "home_resume full_createUI");
            createUI();
            homeUiInitialized = true;
            refreshAiDrawerHeader();
        }
        Log.i(LOGTAG, "home_resume done ms=" + (System.currentTimeMillis() - resumeStart));
        logHomeLifecycle("onResume_done", null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        logHomeLifecycle("onStart", null);
        getWindow().setBackgroundDrawable(null);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.i(LOGTAG, "no permission to read external storage - asking for permission");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_WRITE_EXTERNAL_STORAGE);
        }
        Log.d(LOGTAG, "onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        logHomeLifecycle("onStop", null);
        ExitDiagHelper.logHomeLifecycleAfterDocExit("onStop");
    }

    @Override
    public void finish() {
        ExitDiagHelper.logTaskFinishProbe(this, "finish", false);
        super.finish();
    }

    @Override
    public void finishAndRemoveTask() {
        ExitDiagHelper.logTaskFinishProbe(this, "finishAndRemoveTask", true);
        super.finishAndRemoveTask();
    }

    @Override
    protected void onDestroy() {
        logHomeLifecycle("onDestroy", null);
        ExitDiagHelper.logHomeLifecycleAfterDocExit("onDestroy");
        ExitDiagHelper.logPhase("home_onDestroy");
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        Log.i(LOGTAG, "exit_diag_home_trim_memory level=" + level + " pid=" + android.os.Process.myPid());
        super.onTrimMemory(level);
    }

    private int dpToPx(int dp) {
        final float scale = getApplicationContext().getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    private void addDocumentToRecents(Uri uri) {
        String newRecent = uri.toString();

        // Create array to work with (have to copy the content)
        ArrayList<String> recentsArrayList = new ArrayList<String>(Arrays.asList(getRecentDocuments()));

        //remove string if present, so that it doesn't appear multiple times
        recentsArrayList.remove(newRecent);

        //put the new value in the first place
        recentsArrayList.add(0, newRecent);
        touchRecentOpenTime(uri);

        final int RECENTS_SIZE = 30;

        while (recentsArrayList.size() > RECENTS_SIZE) {
            recentsArrayList.remove(recentsArrayList.size() - 1);
        }

        // Join the array, use \n's as delimiters
        String joined = TextUtils.join("\n", recentsArrayList);
        prefs.edit().putString(RECENT_DOCUMENTS_KEY, joined).apply();

        // Update app shortcuts (7.0 and above)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);

            // Remove all shortcuts, and apply new ones.
            shortcutManager.removeAllDynamicShortcuts();

            ArrayList<ShortcutInfo> shortcuts = new ArrayList<ShortcutInfo>();
            int i = 0;
            for (String pathString : recentsArrayList) {
                if (pathString.isEmpty())
                    continue;

                // I cannot see more than 3 anyway, and with too many we get
                // an exception, so let's limit to 3
                if (i >= 3 || i >= getMaxShortcutCountPerActivity(this))
                    break;

                ++i;

                // Find the appropriate drawable
                int drawable = 0;
                switch (FileUtilities.getType(pathString)) {
                    case FileUtilities.DOC:
                        drawable = R.drawable.writer;
                        break;
                    case FileUtilities.CALC:
                        drawable = R.drawable.calc;
                        break;
                    case FileUtilities.DRAWING:
                        drawable = R.drawable.draw;
                        break;
                    case FileUtilities.IMPRESS:
                        drawable = R.drawable.impress;
                        break;
                }

                Uri shortcutUri = Uri.parse(pathString);
                String filename = RecentFilesAdapter.getUriFilename(this, shortcutUri);

                if (filename == null)
                    continue;

                Intent intent = getIntentToEdit(shortcutUri);
                ShortcutInfo.Builder builder = new ShortcutInfo.Builder(this, filename)
                        .setShortLabel(filename)
                        .setLongLabel(filename)
                        .setIntent(intent);

                if (drawable != 0)
                    builder.setIcon(Icon.createWithResource(this, drawable));

                shortcuts.add(builder.build());
            }

            try {
                shortcutManager.setDynamicShortcuts(shortcuts);
            } catch (Exception e) {
                Log.e(LOGTAG, "Failed to set the dynamic shortcuts: " + e.getMessage());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_WRITE_EXTERNAL_STORAGE:
                if (permissions.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    return;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    boolean showRationale = shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    androidx.appcompat.app.AlertDialog.Builder rationaleDialogBuilder = new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setCancelable(false)
                            .setTitle(getString(R.string.title_permission_required))
                            .setMessage(getString(R.string.reason_required_to_read_documents));
                    if (showRationale) {
                        rationaleDialogBuilder.setPositiveButton(getString(R.string.positive_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(LibreOfficeUIActivity.this,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        PERMISSION_WRITE_EXTERNAL_STORAGE);
                            }
                        })
                                .setNegativeButton(getString(R.string.negative_im_sure), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        LibreOfficeUIActivity.this.finish();
                                    }
                                })
                                .create()
                                .show();
                    } else {
                        rationaleDialogBuilder.setPositiveButton(getString(R.string.positive_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            }
                        })
                                .setNegativeButton(R.string.negative_cancel, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        LibreOfficeUIActivity.this.finish();
                                    }
                                })
                                .create()
                                .show();
                    }
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
