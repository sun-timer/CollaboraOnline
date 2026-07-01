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
import android.graphics.Color;
import android.graphics.Insets;
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
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidapp.AboutDialogFragment;
import org.libreoffice.androidapp.R;
import org.libreoffice.androidapp.SettingsActivity;
import org.libreoffice.androidapp.SettingsListenerModel;
import org.libreoffice.androidlib.LOActivity;

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

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
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
    private static final String ENABLE_SHOW_HIDDEN_FILES_KEY = "ENABLE_SHOW_HIDDEN_FILES";

    public static final String NEW_FILE_PATH_KEY = "NEW_FILE_PATH_KEY";
    public static final String NEW_DOC_TYPE_KEY = "NEW_DOC_TYPE_KEY";
    public static final String NIGHT_MODE_KEY = "NIGHT_MODE";

    public static final String GRID_VIEW = "0";
    public static final String LIST_VIEW = "1";

    private DrawerLayout drawerLayout;
    private NavigationView navigationDrawer;
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
    private boolean searchModeActive = false;

    //kept package-private to use these in recyclerView's adapter
    TextView noRecentItemsTextView;

    private Animation fabOpenAnimation;
    private Animation fabCloseAnimation;
    private boolean isFabMenuOpen = false;
    private FloatingActionButton editFAB;
    private FloatingActionButton writerFAB;
    private FloatingActionButton impressFAB;
    private FloatingActionButton calcFAB;
    private LinearLayout writerLayout;
    private LinearLayout impressLayout;
    private LinearLayout calcLayout;

    /** Recent files list vs. grid switch. */
    private ImageView mRecentFilesListOrGrid;

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
    private AlertDialog aiGeneratingDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 切回正式主题（Manifest 用 Splash 主题提供冷启动欢迎图窗口背景）
        setTheme(R.style.LibreOfficeTheme);
        PreferenceManager.setDefaultValues(this, R.xml.documentprovider_preferences, false);
        readPreferences();
        int mode = prefs.getInt(NIGHT_MODE_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
        super.onCreate(savedInstanceState);

        // initialize document provider factory
        //DocumentProviderFactory.initialize(this);
        //documentProviderFactory = DocumentProviderFactory.getInstance();

        SettingsListenerModel.getInstance().setListener(this);

        // init UI and populate with contents from the provider
        createUI();

        fabOpenAnimation = AnimationUtils.loadAnimation(this, R.anim.fab_open);
        fabCloseAnimation = AnimationUtils.loadAnimation(this, R.anim.fab_close);
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
        // update also the icon switching between list and grid
        if (isViewModeList())
            mRecentFilesListOrGrid.setImageResource(R.drawable.ic_view_module_black_24dp);
        else
            mRecentFilesListOrGrid.setImageResource(R.drawable.ic_list_black_24dp);

        String[] recentFileStrings = getRecentDocuments();
        final ArrayList<Uri> recentUris = new ArrayList<Uri>();
        for (String recentFileString : recentFileStrings) {
            try {
                recentUris.add(Uri.parse(recentFileString));
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        final ArrayList<Uri> filteredUris = filterRecentUris(recentUris, currentSearchQuery);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            recentRecyclerView.setOnApplyWindowInsetsListener((v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars());

                v.setPadding(insets.left, 0, insets.right, insets.bottom);

                return WindowInsets.CONSUMED;
            });
        }

        recentRecyclerView.setLayoutManager(isViewModeList() ? new LinearLayoutManager(this) : new GridLayoutManager(this, 2));
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
            recentsHeaderRow.setVisibility(hasQuery ? View.GONE : View.VISIBLE);
        }
        if (emptyRecentState != null) {
            emptyRecentState.setVisibility(!hasQuery && totalCount == 0 ? View.VISIBLE : View.GONE);
        }
        if (emptySearchState != null) {
            emptySearchState.setVisibility(hasQuery && filteredCount == 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void updateSearchUiMode() {
        boolean hasQuery = !TextUtils.isEmpty(currentSearchQuery);
        boolean showMenu = searchModeActive || hasQuery;
        if (homeLeftIcon == null) {
            return;
        }
        if (showMenu) {
            homeLeftIcon.setImageResource(R.drawable.ic_home_menu);
            homeLeftIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            homeLeftIcon.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            homeLeftIcon.setContentDescription(getString(R.string.document_locations));
        } else {
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
            homeLeftIcon.setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2));
            homeLeftIcon.setContentDescription(getString(R.string.ai_profile_name));
        }
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
        View headerView = navigationDrawer.getHeaderView(0);
        setupAiDrawerHeader(headerView);
        Button localInstallButton = headerView.findViewById(R.id.localInstallButton);
        if (localInstallButton != null) {
            localInstallButton.setOnClickListener(v -> Toast.makeText(this, R.string.local_model_todo, Toast.LENGTH_SHORT).show());
        }
        navigationDrawer.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    /* TODO Currently we don't support sorting of the recent files
                    case R.id.menu_filter_everything:
                        item.setChecked(true);
                        filterMode = FileUtilities.ALL;
                        //openDirectory(currentDirectory);
                        break;

                    case R.id.menu_filter_documents:
                        item.setChecked(true);
                        filterMode = FileUtilities.DOC;
                        //openDirectory(currentDirectory);
                        break;

                    case R.id.menu_filter_spreadsheets:
                        item.setChecked(true);
                        filterMode = FileUtilities.CALC;
                        //openDirectory(currentDirectory);
                        break;

                    case R.id.menu_filter_presentations:
                        item.setChecked(true);
                        filterMode = FileUtilities.IMPRESS;
                        //openDirectory(currentDirectory);
                        break;

                    case R.id.menu_sort_size_asc:
                        sortMode = FileUtilities.SORT_SMALLEST;
                        this.onResume();
                        break;

                    case R.id.menu_sort_size_desc:
                        sortMode = FileUtilities.SORT_LARGEST;
                        this.onResume();
                        break;

                    case R.id.menu_sort_az:
                        sortMode = FileUtilities.SORT_AZ;
                        this.onResume();
                        break;

                    case R.id.menu_sort_za:
                        sortMode = FileUtilities.SORT_ZA;
                        this.onResume();
                        break;

                    case R.id.menu_sort_modified_newest:
                        sortMode = FileUtilities.SORT_NEWEST;
                        this.onResume();
                        break;

                    case R.id.menu_sort_modified_oldest:
                        sortMode = FileUtilities.SORT_OLDEST;
                        this.onResume();
                        break;
                    */

                    case R.id.action_clear_cache:
                        Toast.makeText(getApplicationContext(), R.string.clear_cache_todo, Toast.LENGTH_SHORT).show();
                        return true;

                    case R.id.action_about:
                        AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
                        aboutDialogFragment.show(getSupportFragmentManager(), "AboutDialogFragment");
                        return true;
                }
                return false;
            }
        });
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.document_locations, R.string.close_document_locations) {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                boolean lightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_YES) == 0;

                if (lightMode) { // Even in light mode, the status bar still has a dark background when opened... the navigation bar is unaffected
                    boolean lightStatusBar = slideOffset < 0.5;
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(lightStatusBar);
                }
            }
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
            }
        };
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
    }

    private void setupAiDrawerHeader(View headerView) {
        View profileEntry = headerView.findViewById(R.id.profileEntry);
        View aiConfigCard = headerView.findViewById(R.id.aiConfigCard);
        View aiConfigIcon = headerView.findViewById(R.id.aiConfigIcon);

        if (profileEntry != null) {
            profileEntry.setOnClickListener(v -> openAiProfileSettings());
        }
        if (aiConfigCard != null) {
            aiConfigCard.setOnClickListener(v -> openAiProfileSettings());
        }
        if (aiConfigIcon != null) {
            aiConfigIcon.setOnClickListener(v -> openAiProfileSettings());
        }

        bindModelConfigEntry(headerView, R.id.modelItemBase, R.id.modelBaseArrow, AiSettingsStore.MODEL_BASE);
        bindModelConfigEntry(headerView, R.id.modelItemThink, R.id.modelThinkArrow, AiSettingsStore.MODEL_THINK);
        bindModelConfigEntry(headerView, R.id.modelItemImage, R.id.modelImageArrow, AiSettingsStore.MODEL_IMAGE);
        bindModelConfigEntry(headerView, R.id.modelItemVision, R.id.modelVisionArrow, AiSettingsStore.MODEL_VISION);
        refreshAiDrawerHeader();
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
        Intent intent = new Intent(this, AiModelConfigActivity.class);
        intent.putExtra(AiSettingsStore.EXTRA_MODEL_TYPE, modelType);
        startActivityForResult(intent, AI_MODEL_SETTINGS_REQUEST_CODE);
    }

    private void refreshAiDrawerHeader() {
        if (navigationDrawer == null) {
            return;
        }
        View headerView = navigationDrawer.getHeaderView(0);
        if (headerView == null) {
            return;
        }

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
        if (homeLeftIcon != null && !(searchModeActive || !TextUtils.isEmpty(currentSearchQuery))) {
            if (avatarUri == null || avatarUri.isEmpty()) {
                homeLeftIcon.setImageResource(R.drawable.drawer_header);
            } else {
                try {
                    homeLeftIcon.setImageURI(Uri.parse(avatarUri));
                } catch (Exception ignored) {
                    homeLeftIcon.setImageResource(R.drawable.drawer_header);
                }
            }
        }

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
    }

    public void createUI() {
        setContentView(R.layout.activity_document_browser);
        setupNavigationDrawer();

        View topBar = findViewById(R.id.homeTopBar);
        homeLeftIcon = findViewById(R.id.homeLeftIcon);
        homeOpenFileButton = findViewById(R.id.homeOpenFileButton);
        homeSearchInput = findViewById(R.id.homeSearchInput);
        recentsHeaderRow = findViewById(R.id.recentsHeaderRow);
        emptyRecentState = findViewById(R.id.emptyRecentState);
        emptySearchState = findViewById(R.id.emptySearchState);
        retrySearchButton = findViewById(R.id.retrySearchButton);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.enableEdgeToEdge(getWindow());
            boolean lightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_YES) == 0;
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(lightMode);
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(lightMode);

            topBar.setOnApplyWindowInsetsListener((v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), insets.top + dpToPx(8), v.getPaddingRight(), v.getPaddingBottom());
                return WindowInsets.CONSUMED;
            });
        }

        homeLeftIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        homeOpenFileButton.setOnClickListener(v -> openDocument());

        homeSearchInput.setOnFocusChangeListener((v, hasFocus) -> {
            searchModeActive = hasFocus;
            updateSearchUiMode();
        });
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
                updateSearchUiMode();
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
        updateSearchUiMode();

        setupFloatingActionButton();

        recentRecyclerView = findViewById(R.id.list_recent);
        noRecentItemsTextView = findViewById(R.id.no_recent_items_msg);

        // Icon to switch showing the recent files as list vs. as grid
        mRecentFilesListOrGrid = (ImageView) findViewById(R.id.recent_list_or_grid);
        mRecentFilesListOrGrid.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleViewMode();
                updateRecentFiles();
            }
        });

        updateRecentFiles();

        // allow context menu for the various files - for Open and Share
        registerForContextMenu(recentRecyclerView);
    }

    /** Initialize the FloatingActionButton. */
    private void setupFloatingActionButton() {
        editFAB = findViewById(R.id.editFAB);
        if (LOActivity.isChromeOS(this)) {
            int dp = (int) getResources().getDisplayMetrics().density;
            ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) editFAB.getLayoutParams();
            layoutParams.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
            layoutParams.bottomMargin = dp * 24;
            editFAB.setCustomSize(dp * 70);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            editFAB.setOnApplyWindowInsetsListener((v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars() | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ? WindowInsets.Type.systemOverlays() : 0));

                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                mlp.leftMargin = insets.left;
                mlp.rightMargin = insets.right;
                int dp = (int) getResources().getDisplayMetrics().density;
                mlp.bottomMargin = insets.bottom + (LOActivity.isChromeOS(this) ? dp * 24 : 0);
                v.setLayoutParams(mlp);

                return WindowInsets.CONSUMED;
            });
        }

        editFAB.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isFabMenuOpen)
                    collapseFabMenu();
                else
                    expandFabMenu();
            }
        });
        final OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.newWriterFAB:
                    case R.id.writerLayout:
                        createNewFileInputDialog(getString(R.string.new_textdocument) + FileUtilities.DEFAULT_WRITER_EXTENSION, "application/vnd.oasis.opendocument.text", CREATE_DOCUMENT_REQUEST_CODE);
                        break;
                    case R.id.newCalcFAB:
                    case R.id.calcLayout:
                        createNewFileInputDialog(getString(R.string.new_spreadsheet) + FileUtilities.DEFAULT_SPREADSHEET_EXTENSION, "application/vnd.oasis.opendocument.spreadsheet", CREATE_SPREADSHEET_REQUEST_CODE);
                        break;
                    case R.id.newImpressFAB:
                    case R.id.impressLayout:
                        createNewFileInputDialog(getString(R.string.new_presentation) + FileUtilities.DEFAULT_IMPRESS_EXTENSION, "application/vnd.oasis.opendocument.presentation", CREATE_PRESENTATION_REQUEST_CODE);
                        break;
                }
            }
        };

        writerFAB = findViewById(R.id.newWriterFAB);
        writerFAB.setOnClickListener(clickListener);

        calcFAB = findViewById(R.id.newCalcFAB);
        calcFAB.setOnClickListener(clickListener);

        impressFAB = findViewById(R.id.newImpressFAB);
        impressFAB.setOnClickListener(clickListener);

        writerLayout = findViewById(R.id.writerLayout);
        writerLayout.setOnClickListener(clickListener);

        impressLayout = findViewById(R.id.impressLayout);
        impressLayout.setOnClickListener(clickListener);

        calcLayout = findViewById(R.id.calcLayout);
        calcLayout.setOnClickListener(clickListener);
    }

    /** Expand the Floating action button. */
    private void expandFabMenu() {
        if (isFabMenuOpen)
            return;

        ViewCompat.animate(editFAB).rotation(-45f).withLayer().setDuration(300).setInterpolator(new OvershootInterpolator(0f)).start();
        impressLayout.startAnimation(fabOpenAnimation);
        writerLayout.startAnimation(fabOpenAnimation);
        calcLayout.startAnimation(fabOpenAnimation);
        writerFAB.setClickable(true);
        impressFAB.setClickable(true);
        calcFAB.setClickable(true);
        isFabMenuOpen = true;
    }

    /** Collapse the Floating action button. */
    private void collapseFabMenu() {
        if (!isFabMenuOpen)
            return;

        ViewCompat.animate(editFAB).rotation(0f).withLayer().setDuration(300).setInterpolator(new OvershootInterpolator(0f)).start();
        writerLayout.startAnimation(fabCloseAnimation);
        impressLayout.startAnimation(fabCloseAnimation);
        calcLayout.startAnimation(fabCloseAnimation);
        writerFAB.setClickable(false);
        impressFAB.setClickable(false);
        calcFAB.setClickable(false);
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
        if (drawerLayout.isDrawerOpen(navigationDrawer)) {
            drawerLayout.closeDrawer(navigationDrawer);
            collapseFabMenu();
        } else if (isFabMenuOpen) {
            collapseFabMenu();
        } else {
            // exit the app
            super.onBackPressed();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.context_menu_open:
                open(currentlySelectedFile);
                return true;
            case R.id.context_menu_share:
                share(currentlySelectedFile);
                return true;
            case R.id.context_menu_remove_from_list:
                removeFromList(currentlySelectedFile);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    public void openContextMenu(View view, Uri uri) {

        this.currentlySelectedFile = uri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            view.showContextMenu(view.getPivotX(), view.getPivotY());
        }
        else
            view.showContextMenu();
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

        return i;
    }

    /** Start editing of the given Uri. */
    public void open(final Uri uri) {
        open(uri, false, "");
    }

    public void open(final Uri uri, boolean autoOpenAi, String aiPrompt) {
        if (uri == null)
            return;

        addDocumentToRecents(uri);

        Intent i = getIntentToEdit(uri, autoOpenAi, aiPrompt);
        startActivityForResult(i, LO_ACTIVITY_REQUEST_CODE);
    }

    /** Opens an Input dialog to get the name of new file. */
    private void createNewFileInputDialog(final String defaultFileName, final String mimeType, final int requestCode) {
        collapseFabMenu();
        showCreateFileBottomSheet(defaultFileName, mimeType, requestCode);
    }

    private void showCreateFileBottomSheet(final String defaultFileName, final String mimeType, final int requestCode) {
        final BottomSheetDialog sheetDialog = new BottomSheetDialog(this);
        final View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_file, null, false);
        sheetDialog.setContentView(dialogView);

        TextView createTitle = dialogView.findViewById(R.id.createTitle);
        EditText fileNameInput = dialogView.findViewById(R.id.fileName);
        TextView overwriteWarning = dialogView.findViewById(R.id.overwriteWarning);
        Button createButton = dialogView.findViewById(R.id.createButton);
        ImageButton closeButton = dialogView.findViewById(R.id.closeButton);
        SwitchCompat aiSwitch = dialogView.findViewById(R.id.aiSwitch);

        final String extension = getExtensionForRequestCode(requestCode);
        final String baseName = trimFileExtension(defaultFileName);
        final CreateSheetStyle style = getCreateSheetStyle(requestCode);

        createTitle.setText(style.titleResId);
        createButton.setText(style.buttonTextResId);
        createButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor(style.buttonColorHex)));
        fileNameInput.setText(baseName);
        fileNameInput.setSelection(fileNameInput.getText().length());
        overwriteWarning.setVisibility(View.GONE);

        closeButton.setOnClickListener(v -> sheetDialog.dismiss());
        createButton.setOnClickListener(v -> {
            String name = fileNameInput.getText() == null ? "" : fileNameInput.getText().toString().trim();
            if (name.isEmpty()) {
                fileNameInput.setError(getString(R.string.enter_filename));
                return;
            }

            if (aiSwitch.isChecked()) {
                pendingAutoOpenAiAfterCreate = true;
                pendingAutoAiPrompt = buildAutoAiPromptForRequestCode(requestCode, name);
            } else {
                pendingAutoOpenAiAfterCreate = false;
                pendingAutoAiPrompt = "";
            }

            String finalFileName = ensureExtension(name, extension);
            sheetDialog.dismiss();
            LOActivity.createNewFileInputDialog(this, finalFileName, mimeType, requestCode);
        });

        sheetDialog.show();
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
                        "#2E7D32"
                );
            case CREATE_PRESENTATION_REQUEST_CODE:
                return new CreateSheetStyle(
                        R.string.create_new_presentation_title,
                        R.string.create_empty_presentation,
                        "#E65100"
                );
            case CREATE_DOCUMENT_REQUEST_CODE:
            default:
                return new CreateSheetStyle(
                        R.string.create_new_text_document_title,
                        R.string.create_empty_text_document,
                        "#1976D2"
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

    private String buildAutoAiPromptForRequestCode(int requestCode, String title) {
        String titleHint = (title != null && !title.isEmpty())
                ? "主题：《" + title + "》。请围绕该主题，"
                : "请";
        switch (requestCode) {
            case CREATE_SPREADSHEET_REQUEST_CODE:
                return titleHint + "先给出一个清晰的数据表结构大纲（列名和用途），再输出可直接粘贴到电子表格的完整示例内容，至少包含10行数据。";
            case CREATE_PRESENTATION_REQUEST_CODE:
                return titleHint + "先生成一份6页演示的大纲（每页标题+要点），再输出可直接用于演示文稿的完整正文内容。";
            case CREATE_DOCUMENT_REQUEST_CODE:
            default:
                return titleHint + "先生成文档大纲（章节标题），再基于大纲输出完整正文，风格专业、结构清晰。";
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

        //prefs.edit().putInt(EXPLORER_VIEW_TYPE, viewType).commit();
        Log.d(LOGTAG, "savedInstanceState");
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
                // TODO probably kill this, we don't need to do anything here any more
                Log.d(LOGTAG, "LOActivity has finished.");
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
                    dismissAiGeneratingDialog();
                    return;
                }

                Uri uri = data.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                String extension = (requestCode == CREATE_DOCUMENT_REQUEST_CODE) ? "odt" : ((requestCode == CREATE_SPREADSHEET_REQUEST_CODE) ? "ods" : "odp");
                final boolean autoOpenAi = pendingAutoOpenAiAfterCreate;
                final String autoAiPrompt = pendingAutoAiPrompt;
                if (autoOpenAi) {
                    showAiGeneratingDialog();
                } else {
                    dismissAiGeneratingDialog();
                }
                createNewFileAsync(uri, extension, () -> {
                    dismissAiGeneratingDialog();
                    open(uri, autoOpenAi, autoAiPrompt);
                });
                pendingAutoOpenAiAfterCreate = false;
                pendingAutoAiPrompt = "";
                break;
            }
            case AI_PROFILE_SETTINGS_REQUEST_CODE:
            case AI_MODEL_SETTINGS_REQUEST_CODE: {
                refreshAiDrawerHeader();
                break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOGTAG, "onPause");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOGTAG, "onResume");
        Log.d(LOGTAG, "sortMode=" + sortMode + " filterMode=" + filterMode);
        createUI();
        refreshAiDrawerHeader();
    }

    @Override
    protected void onStart() {
        super.onStart();
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
        Log.d(LOGTAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOGTAG, "onDestroy");
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
