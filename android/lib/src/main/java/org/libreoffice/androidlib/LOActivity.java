/* -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.androidlib;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.libreoffice.androidlib.ai.AiChatCoordinator;
import org.libreoffice.androidlib.ai.AiDocumentContextProvider;
import org.libreoffice.androidlib.ai.AiMarkdownRenderer;
import org.libreoffice.androidlib.ai.AiPanelController;
import org.libreoffice.androidlib.ai.AiRequestManager;
import org.libreoffice.androidlib.ai.AiRequestSession;
import org.libreoffice.androidlib.lok.LokClipboardData;
import org.libreoffice.androidlib.lok.LokClipboardEntry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LOActivity extends AppCompatActivity {
    final static String TAG = "LOActivity";

    private static final String ASSETS_EXTRACTED_GIT_COMMIT = "ASSETS_EXTRACTED_GIT_COMMIT";
    private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 777;
    private static final String KEY_ENABLE_SHOW_DEBUG_INFO = "ENABLE_SHOW_DEBUG_INFO";

    private static final String KEY_PROVIDER_ID = "providerID";
    private static final String KEY_DOCUMENT_URI = "documentUri";
    private static final String KEY_IS_EDITABLE = "isEditable";
    private static final String KEY_INTENT_URI = "intentUri";
    public static final String EXTRA_AUTO_OPEN_AI_PANEL = "org.libreoffice.androidlib.extra.AUTO_OPEN_AI_PANEL";
    public static final String EXTRA_AUTO_OPEN_AI_PROMPT = "org.libreoffice.androidlib.extra.AUTO_OPEN_AI_PROMPT";
    public static final String EXTRA_AUTO_GENERATE_AI_CONTENT = "org.libreoffice.androidlib.extra.AUTO_GENERATE_AI_CONTENT";
    private static final String CLIPBOARD_FILE_PATH = "LibreofficeClipboardFile.data";
    private static final String CLIPBOARD_COOL_SIGNATURE = "cool-clip-magic-4a22437e49a8-";
    private static final String AI_PREF_ENDPOINT = "AI_OPENAI_ENDPOINT";
    private static final String AI_PREF_API_KEY = "AI_OPENAI_API_KEY";
    private static final String AI_PREF_MODEL = "AI_OPENAI_MODEL";
    private static final String AI_PREF_FAB_X = "AI_FAB_X";
    private static final String AI_PREF_FAB_Y = "AI_FAB_Y";
    private static final String AI_STATE_UNCONFIGURED = "unconfigured";
    private static final String AI_STATE_LOADING = "loading";
    private static final String AI_STATE_STREAMING = "streaming";
    private static final String AI_STATE_READY = "ready";
    private static final String AI_STATE_CANCELLED = "cancelled";
    private static final String AI_STATE_ERROR = "error";
    private static final String AI_DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String AI_DEFAULT_MODEL = "gpt-4o-mini";
    private static final String AI_DEFAULT_SYSTEM_PROMPT = "You are a concise office writing assistant. Return only the rewritten or generated content.";
    private static final String AI_MODE_DOC_QA = "doc_qa";
    private static final String AI_MODE_CHAT = "chat";
    private static final String AI_STREAMING_PLACEHOLDER = "正在思考...";
    private static final int IME_VISIBLE_THRESHOLD_DP = 56;
    public static final String RECENT_DOCUMENTS_KEY = "RECENT_DOCUMENTS_LIST";
    private static String USER_NAME_KEY = "USER_NAME";
    public static final String NIGHT_MODE_KEY = "NIGHT_MODE";

    private File mTempFile = null;

    private int providerId;
    private Activity mActivity;

    /// Unique number identifying this app + document.
    private long loadDocumentMillis = 0;

    @Nullable
    private URI documentUri;

    private String urlToLoad;
    private COWebView mWebView = null;
    private MobileSocket mMobileSocket = null;
    private SharedPreferences sPrefs;
    private Handler mMainHandler = null;
    private RateAppController rateAppController;

    private boolean isDocEditable = false;
    private boolean isDocDebuggable = BuildConfig.DEBUG;
    private boolean documentLoaded = false;

    private ClipboardManager clipboardManager;
    private ClipData clipData;
    private Thread nativeMsgThread;
    private Handler nativeHandler;
    private Looper nativeLooper;
    private Bundle savedInstanceState;

    private ProgressDialog mProgressDialog = null;

    /**
     * In case the mobile-wizard is visible, we have to intercept the Android's Back
     * button.
     */
    private boolean mMobileWizardVisible = false;
    private boolean mIsEditModeActive = false;
    private static final long MOBILE_PREVIEW_ACK_TIMEOUT_MS = 2200L;
    private static final long SELECTION_SYNC_THROTTLE_MS = 450L;
    /** True while waiting for JS to finish mobile preview (readonly UI) after native toolbar switch. */
    private boolean awaitingPreviewModeJsAck = false;
    private int mobilePreviewSwitchAttempt = 0;
    private final Runnable mobilePreviewAckTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!awaitingPreviewModeJsAck) {
                return;
            }
            if (mobilePreviewSwitchAttempt < 2) {
                mobilePreviewSwitchAttempt++;
                Log.w(TAG, "mobile_preview_switch_ack timeout; retry attempt=" + mobilePreviewSwitchAttempt);
                callFakeWebsocketOnMessage("mobile: readonlymode");
                getMainHandler().postDelayed(this, MOBILE_PREVIEW_ACK_TIMEOUT_MS);
                return;
            }
            Log.e(TAG, "mobile_preview_switch_ack failed after retries; applying soft resync");
            awaitingPreviewModeJsAck = false;
            mobilePreviewSwitchAttempt = 0;
            nudgePreviewModeOnWebLayer();
        }
    };

    private ValueCallback<Uri[]> valueCallback;
    private final Map<String, AiRequestSession> aiRequestSessions = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> aiTextByRequestId = new ConcurrentHashMap<>();
    private final Map<String, String> aiRequestModeById = new ConcurrentHashMap<>();
    private final Map<String, Boolean> aiDocQaFirstTurnByRequestId = new ConcurrentHashMap<>();
    private final Map<String, TextView> aiStreamingViewByRequestId = new ConcurrentHashMap<>();
    private boolean aiBridgeInjected = false;
    private String aiActiveRequestId = "";
    private BottomSheetDialog aiPanelDialog;
    private BottomSheetDialog functionPanelDialog;
    private EditText aiPromptInput;
    private TextView aiStatusText;
    private TextView aiOutputText;
    private View aiRunButton;
    private Button aiCancelButton;
    private Button aiAcceptButton;
    private ImageButton aiCloseButton;
    private TextView aiTabDocQa;
    private TextView aiTabChat;
    private LinearLayout aiMessagesContainer;
    private ScrollView aiMessagesScroll;
    private boolean aiDocQaMode = true;
    private TextView aiStreamingMessageView;
    private String aiStreamingRequestId = "";
    private AiChatCoordinator aiChatCoordinator;
    private AiDocumentContextProvider aiDocumentContextProvider;
    private final AiPanelController aiPanelController = new AiPanelController();
    private final AiRequestManager aiRequestManager = new AiRequestManager();
    private boolean aiFabDragging = false;
    private boolean aiFabDragged = false;
    private float aiFabDragOffsetX = 0f;
    private float aiFabDragOffsetY = 0f;
    private boolean pendingAutoOpenAiPanel = false;
    private boolean pendingAutoGenerateAiContent = false;
    private String pendingAutoOpenAiPrompt = "";
    private String autoGenerateAcceptRequestId = "";
    private boolean imagePickerInFlight = false;
    private DrawerLayout docDrawerLayout;
    private View docDrawerHeaderView;
    private final AtomicBoolean mobileSocketDrainScheduled = new AtomicBoolean(false);
    private boolean docGestureGuardEnabled = false;
    private long lastDocGestureGuardLogAt = 0L;
    private long lastSelectionSyncAt = 0L;
    private int bottomToolbarImeInsetPx = 0;
    private boolean isImeVisibleForToolbar = false;
    private BottomToolbarController bottomToolbarController;
    private TopToolbarController topToolbarController;
    private SelectionMenuController selectionMenuController;
    private Runnable pendingAfterEditMode;

    private static final int MODEL_TYPE_BASE = 0;
    private static final int MODEL_TYPE_THINK = 1;
    private static final int MODEL_TYPE_IMAGE = 2;
    private static final int MODEL_TYPE_VISION = 3;
    private static final String MODEL_CONFIG_EXTRA_KEY = "extra_model_type";
    private static final String KEY_PROFILE_NAME = "AI_PROFILE_NAME";
    private static final String KEY_PROFILE_AVATAR_URI = "AI_PROFILE_AVATAR_URI";
    private static final String KEY_MODEL_NAME_FIELD = "model_name";

    public static final int REQUEST_SELECT_IMAGE_FILE = 500;
    public static final int REQUEST_SAVEAS_PDF = 501;
    public static final int REQUEST_SAVEAS_RTF = 502;
    public static final int REQUEST_SAVEAS_ODT = 503;
    public static final int REQUEST_SAVEAS_ODP = 504;
    public static final int REQUEST_SAVEAS_ODS = 505;
    public static final int REQUEST_SAVEAS_DOCX = 506;
    public static final int REQUEST_SAVEAS_PPTX = 507;
    public static final int REQUEST_SAVEAS_XLSX = 508;
    public static final int REQUEST_SAVEAS_DOC = 509;
    public static final int REQUEST_SAVEAS_PPT = 510;
    public static final int REQUEST_SAVEAS_XLS = 511;
    public static final int REQUEST_SAVEAS_EPUB = 512;
    public static final int REQUEST_COPY = 600;

    /** Broadcasting event for passing info back to the shell. */
    public static final String LO_ACTIVITY_BROADCAST = "LOActivityBroadcast";

    /** Event description for passing info back to the shell. */
    public static final String LO_ACTION_EVENT = "LOEvent";

    /** Data description for passing info back to the shell. */
    public static final String LO_ACTION_DATA = "LOData";

    /** shared pref key for recent files. */
    public static final String EXPLORER_PREFS_KEY = "EXPLORER_PREFS";

    private static boolean copyFromAssets(AssetManager assetManager,
            String fromAssetPath, String targetDir) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            boolean res = true;
            for (String file : files) {
                String[] dirOrFile = assetManager.list(fromAssetPath + "/" + file);
                if (dirOrFile.length == 0) {
                    // noinspection ResultOfMethodCallIgnored
                    new File(targetDir).mkdirs();
                    res &= copyAsset(assetManager,
                            fromAssetPath + "/" + file,
                            targetDir + "/" + file);
                } else
                    res &= copyFromAssets(assetManager,
                            fromAssetPath + "/" + file,
                            targetDir + "/" + file);
            }
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "copyFromAssets failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean copyAsset(AssetManager assetManager, String fromAssetPath, String toPath) {
        ReadableByteChannel source = null;
        FileChannel dest = null;
        try {
            try {
                source = Channels.newChannel(assetManager.open(fromAssetPath));
                dest = new FileOutputStream(toPath).getChannel();
                long bytesTransferred = 0;
                // might not copy all at once, so make sure everything gets copied....
                ByteBuffer buffer = ByteBuffer.allocate(4096);
                while (source.read(buffer) > 0) {
                    buffer.flip();
                    bytesTransferred += dest.write(buffer);
                    buffer.clear();
                }
                Log.v(TAG, "Success copying " + fromAssetPath + " to " + toPath + " bytes: " + bytesTransferred);
                return true;
            } finally {
                if (dest != null)
                    dest.close();
                if (source != null)
                    source.close();
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "file " + fromAssetPath + " not found! " + e.getMessage());
            return false;
        } catch (IOException e) {
            Log.e(TAG, "failed to copy file " + fromAssetPath + " from assets to " + toPath + " - " + e.getMessage());
            return false;
        }
    }

    private Handler getMainHandler() {
        if (mMainHandler == null) {
            mMainHandler = new Handler(getMainLooper());
        }
        return mMainHandler;
    }

    /** True if the App is running under ChromeOS. */
    public static boolean isChromeOS(Context context) {
        return context.getPackageManager().hasSystemFeature("org.chromium.arc.device_management");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.savedInstanceState = savedInstanceState;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        setContentView(R.layout.lolib_activity_main);
        initDocumentSettingsDrawer();
        mProgressDialog = new ProgressDialog(this);
        if (BuildConfig.GOOGLE_PLAY_ENABLED)
            this.rateAppController = new RateAppController(this);
        else
            this.rateAppController = null;
        this.mActivity = this;

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (docDrawerLayout != null && docDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    docDrawerLayout.closeDrawer(GravityCompat.START);
                    return;
                }
                if (!documentLoaded) {
                    finishAndRemoveTask();
                    return;
                }

                if (mMobileWizardVisible) {
                    // just return one level up in the mobile-wizard (or close it)
                    callFakeWebsocketOnMessage("mobile: mobilewizardback");
                    return;
                } else if (mIsEditModeActive) {
                    switchToViewingMode();
                    return;
                }

                finishWithProgress();
            }
        });

        init();
        readAutoOpenAiIntentExtras();
    }

    /** Initialize the app - copy the assets and create the UI. */
    private void init() {
        if (sPrefs.getString(ASSETS_EXTRACTED_GIT_COMMIT, "").equals(BuildConfig.GIT_COMMIT)) {
            // all is fine, we have already copied the assets
            initUI();
            return;
        }

        mProgressDialog.indeterminate(R.string.preparing_for_the_first_start_after_an_update);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                // copy the new assets
                if (copyFromAssets(getAssets(), "unpack", getApplicationInfo().dataDir)) {
                    sPrefs.edit().putString(ASSETS_EXTRACTED_GIT_COMMIT, BuildConfig.GIT_COMMIT).apply();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                initUI();
            }
        }.execute();
    }

    /** Actual initialization of the UI. */
    private void initUI() {
        isDocDebuggable = sPrefs.getBoolean(KEY_ENABLE_SHOW_DEBUG_INFO, false) && BuildConfig.DEBUG;

        if (getIntent().getData() != null) {

            if (getIntent().getData().getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                isDocEditable = true;

                // is it read-only?
                if ((getIntent().getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
                    isDocEditable = false;
                    Log.d(TAG, "Disabled editing: Read-only");
                    Toast.makeText(this, getResources().getString(R.string.temp_file_saving_disabled),
                            Toast.LENGTH_SHORT).show();
                }

                // turns out that on ChromeOS, it is not possible to save back
                // to Google Drive; detect it already here to avoid disappointment
                // also the volumeprovider does not work for saving back,
                // which is much more serious :-(
                if (isDocEditable && (getIntent().getData().toString()
                        .startsWith("content://org.chromium.arc.chromecontentprovider/externalfile") ||
                        getIntent().getData().toString().startsWith("content://org.chromium.arc.volumeprovider/"))) {
                    isDocEditable = false;
                    Log.d(TAG, "Disabled editing: Chrome OS unsupported content providers");
                    Toast.makeText(this, getResources().getString(R.string.file_chromeos_read_only), Toast.LENGTH_LONG)
                            .show();
                }

                if (copyFileToTemp() && mTempFile != null) {
                    documentUri = mTempFile.toURI();
                    urlToLoad = documentUri.toString();
                    Log.d(TAG, "SCHEME_CONTENT: getPath(): " + getIntent().getData().getPath());
                } else {
                    Log.e(TAG, "couldn't create temporary file from " + getIntent().getData());
                    Toast.makeText(this, R.string.cant_open_the_document, Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else if (getIntent().getData().getScheme().equals(ContentResolver.SCHEME_FILE)) {
                isDocEditable = true;
                urlToLoad = getIntent().getData().toString();
                Log.d(TAG, "SCHEME_FILE: getPath(): " + getIntent().getData().getPath());
                // Gather data to rebuild IFile object later
                providerId = getIntent().getIntExtra(
                        "org.libreoffice.document_provider_id", 0);
                documentUri = (URI) getIntent().getSerializableExtra(
                        "org.libreoffice.document_uri");
            }
        } else if (savedInstanceState != null) {
            getIntent().setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse(savedInstanceState.getString(KEY_INTENT_URI)));
            urlToLoad = getIntent().getData().toString();
            providerId = savedInstanceState.getInt(KEY_PROVIDER_ID);
            if (savedInstanceState.getString(KEY_DOCUMENT_URI) != null) {
                try {
                    documentUri = new URI(savedInstanceState.getString(KEY_DOCUMENT_URI));
                    urlToLoad = documentUri.toString();
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
            isDocEditable = savedInstanceState.getBoolean(KEY_IS_EDITABLE);
        } else {
            // User can't reach here but if he/she does then
            Toast.makeText(this, getString(R.string.failed_to_load_file), Toast.LENGTH_SHORT).show();
            finish();
        }
        // some types don't have export filter so we cannot edit them
        // only set it to false if it returns false otherwise it can break previous
        // controls
        if (!canDocumentBeExported())
            isDocEditable = false;
        if (mTempFile != null) {
            mWebView = (COWebView) findViewById(R.id.browser);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mWebView.setOnApplyWindowInsetsListener((v, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime()
                            | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                                    ? WindowInsets.Type.systemOverlays()
                                    : 0));
                    Insets navInsets = windowInsets.getInsets(WindowInsets.Type.navigationBars());
                    boolean imeVisible = windowInsets.isVisible(WindowInsets.Type.ime());
                    int imeInsetBottom = Math.max(0, insets.bottom - navInsets.bottom);
                    if (imeVisible && imeInsetBottom < dpToPx(IME_VISIBLE_THRESHOLD_DP)) {
                        imeVisible = false;
                        imeInsetBottom = 0;
                    }
                    applyBottomToolbarImeState(imeVisible, imeInsetBottom);

                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                    mlp.leftMargin = insets.left;
                    mlp.topMargin = 0;
                    mlp.rightMargin = insets.right;
                    // IME lift is handled by native bottom toolbar margin; do not
                    // subtract keyboard height here or WebView collapses to 0.
                    mlp.bottomMargin = navInsets.bottom;
                    v.setLayoutParams(mlp);

                    return WindowInsets.CONSUMED;
                });

                boolean lightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_YES) == 0;
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                        .setAppearanceLightStatusBars(lightMode);
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                        .setAppearanceLightNavigationBars(lightMode);
            }

            mMobileSocket = mWebView.getWebViewClient().getMobileSocket();

            WebSettings webSettings = mWebView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            mWebView.addJavascriptInterface(this, "COOLMessageHandler");
            setupAiFab();
            setupTopToolbar();
            setupBottomToolbar();
            setupSelectionMenu();
            mWebView.setOnDocumentLongPressListener(new COWebView.OnDocumentLongPressListener() {
                @Override
                public void onDocumentLongPress(float viewX, float viewY) {
                    LOActivity.this.onDocumentLongPress(viewX, viewY);
                }

                @Override
                public void onDocumentSelectionDrag(float viewX, float viewY) {
                    LOActivity.this.onDocumentSelectionDrag(viewX, viewY);
                }

                @Override
                public void onDocumentSelectionDragEnd(float viewX, float viewY) {
                    LOActivity.this.onDocumentSelectionDragEnd(viewX, viewY);
                }

                @Override
                public void onDocumentSelectionDragCancel() {
                    LOActivity.this.onDocumentSelectionDragCancel();
                }
            });
            mWebView.setConsumeWebViewLongClick(!mIsEditModeActive);

            webSettings.setDomStorageEnabled(true);

            // allow debugging (when building the debug version); see details in
            // https://developers.google.com/web/tools/chrome-devtools/remote-debugging/webviews
            boolean isChromeDebugEnabled = sPrefs.getBoolean("ENABLE_CHROME_DEBUGGING", false);
            if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 || isChromeDebugEnabled) {
                WebView.setWebContentsDebuggingEnabled(true);
            }

            getMainHandler();

            clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            nativeMsgThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Looper.prepare();
                    nativeLooper = Looper.myLooper();
                    nativeHandler = new Handler(nativeLooper);
                    Looper.loop();
                }
            });
            nativeMsgThread.start();

            mWebView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback,
                        WebChromeClient.FileChooserParams fileChooserParams) {
                    if (valueCallback != null) {
                        valueCallback.onReceiveValue(null);
                        valueCallback = null;
                    }

                    valueCallback = filePathCallback;
                    Intent intent = fileChooserParams.createIntent();

                    try {
                        imagePickerInFlight = true;
                        intent.setType("image/*");
                        startActivityForResult(intent, REQUEST_SELECT_IMAGE_FILE);
                    } catch (ActivityNotFoundException e) {
                        valueCallback = null;
                        imagePickerInFlight = false;
                        Toast.makeText(LOActivity.this, getString(R.string.cannot_open_file_chooser), Toast.LENGTH_LONG)
                                .show();
                        return false;
                    }
                    return true;
                }
            });

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "asking for read storage permission");
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                        PERMISSION_WRITE_EXTERNAL_STORAGE);
            } else {
                loadDocument();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {

        Log.i(TAG, "onNewIntent");
        if (imagePickerInFlight) {
            Log.i(TAG, "onNewIntent ignored while image picker is in-flight");
            super.onNewIntent(intent);
            return;
        }

        if (documentLoaded) {
            postMobileMessageNative("save dontTerminateEdit=1 dontSaveIfUnmodified=1");
        }

        final Intent finalIntent = intent;
        mProgressDialog.indeterminate(R.string.exiting);
        getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                documentLoaded = false;
                cancelAllAiRequests();
                postMobileMessageNative("BYE");
                // copyTempBackToIntent();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgressDialog.dismiss();
                        setIntent(finalIntent);
                        init();
                    }
                });
            }
        });
        super.onNewIntent(intent);
        readAutoOpenAiIntentExtras();
    }

    private void readAutoOpenAiIntentExtras() {
        Intent intent = getIntent();
        if (intent == null) {
            pendingAutoOpenAiPanel = false;
            pendingAutoGenerateAiContent = false;
            pendingAutoOpenAiPrompt = "";
            return;
        }
        pendingAutoGenerateAiContent = intent.getBooleanExtra(EXTRA_AUTO_GENERATE_AI_CONTENT, false);
        pendingAutoOpenAiPanel = intent.getBooleanExtra(EXTRA_AUTO_OPEN_AI_PANEL, false);
        if (pendingAutoGenerateAiContent) {
            pendingAutoOpenAiPanel = false;
        }
        pendingAutoOpenAiPrompt = intent.getStringExtra(EXTRA_AUTO_OPEN_AI_PROMPT);
        if (pendingAutoOpenAiPrompt == null) {
            pendingAutoOpenAiPrompt = "";
        }
    }

    private void initDocumentSettingsDrawer() {
        docDrawerLayout = findViewById(R.id.doc_drawer_layout);
        if (docDrawerLayout == null) {
            return;
        }
        docDrawerLayout.setScrimColor(0x99000000);

        FrameLayout drawerContent = findViewById(R.id.doc_settings_drawer_content);
        if (drawerContent != null) {
            int headerLayoutId = getResources().getIdentifier("navigation_header", "layout", getPackageName());
            if (headerLayoutId != 0) {
                View header = LayoutInflater.from(this).inflate(headerLayoutId, drawerContent, false);
                drawerContent.removeAllViews();
                drawerContent.addView(header);
                docDrawerHeaderView = header;
                bindDocumentSettingsHeaderClicks(header);
                refreshDocumentSettingsDrawer();
            }
        }

        View clearCache = findViewById(R.id.doc_settings_clear_cache);
        if (clearCache instanceof TextView) {
            ((TextView) clearCache).setText(getStringByName("action_clear_cache", "Clear cache"));
        }
        if (clearCache != null) {
            clearCache.setOnClickListener(v ->
                    Toast.makeText(this, getStringByName("clear_cache_todo", "Clear cache is not implemented yet."), Toast.LENGTH_SHORT).show()
            );
        }

        View about = findViewById(R.id.doc_settings_about);
        if (about instanceof TextView) {
            ((TextView) about).setText(getStringByName("action_about", "About"));
        }
        if (about != null) {
            about.setOnClickListener(v ->
                    Toast.makeText(this, getStringByName("action_about", "About"), Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void bindDocumentSettingsHeaderClicks(View headerView) {
        bindClickByName(headerView, "profileEntry", v -> openProfileSettingsActivity());
        bindClickByName(headerView, "aiConfigCard", v -> openProfileSettingsActivity());
        bindClickByName(headerView, "aiConfigIcon", v -> openProfileSettingsActivity());
        bindModelEntry(headerView, "modelItemBase", "modelBaseArrow", MODEL_TYPE_BASE);
        bindModelEntry(headerView, "modelItemThink", "modelThinkArrow", MODEL_TYPE_THINK);
        bindModelEntry(headerView, "modelItemImage", "modelImageArrow", MODEL_TYPE_IMAGE);
        bindModelEntry(headerView, "modelItemVision", "modelVisionArrow", MODEL_TYPE_VISION);
    }

    private void bindModelEntry(View headerView, String rowIdName, String arrowIdName, int modelType) {
        View row = findViewByName(headerView, rowIdName);
        if (row != null) {
            row.setOnClickListener(v -> openModelSettingsActivity(modelType));
        }
        View arrow = findViewByName(headerView, arrowIdName);
        if (arrow != null) {
            arrow.setOnClickListener(v -> openModelSettingsActivity(modelType));
        }
    }

    private void openProfileSettingsActivity() {
        Intent intent = new Intent();
        intent.setClassName(getPackageName(), "org.libreoffice.androidapp.ui.AiProfileSettingsActivity");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to open profile settings activity", e);
        }
    }

    private void openModelSettingsActivity(int modelType) {
        Intent intent = new Intent();
        intent.setClassName(getPackageName(), "org.libreoffice.androidapp.ui.AiModelConfigActivity");
        intent.putExtra(MODEL_CONFIG_EXTRA_KEY, modelType);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to open model settings activity", e);
        }
    }

    private void refreshDocumentSettingsDrawer() {
        if (docDrawerHeaderView == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
        TextView profileName = asTextView(findViewByName(docDrawerHeaderView, "profileNameText"));
        ImageView profileAvatar = asImageView(findViewByName(docDrawerHeaderView, "profileAvatar"));
        TextView baseValue = asTextView(findViewByName(docDrawerHeaderView, "modelBaseValue"));
        TextView thinkValue = asTextView(findViewByName(docDrawerHeaderView, "modelThinkValue"));
        TextView imageValue = asTextView(findViewByName(docDrawerHeaderView, "modelImageValue"));
        TextView visionValue = asTextView(findViewByName(docDrawerHeaderView, "modelVisionValue"));

        String defaultNickname = getStringByName("ai_profile_name", "Nickname");
        String nickname = prefs.getString(KEY_PROFILE_NAME, defaultNickname);
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = defaultNickname;
        }
        if (profileName != null) {
            profileName.setText(nickname);
        }

        String avatarUri = prefs.getString(KEY_PROFILE_AVATAR_URI, "");
        if (profileAvatar != null) {
            if (avatarUri == null || avatarUri.isEmpty()) {
                int fallbackId = getResources().getIdentifier("drawer_header", "drawable", getPackageName());
                if (fallbackId != 0) {
                    profileAvatar.setImageResource(fallbackId);
                }
            } else {
                try {
                    profileAvatar.setImageURI(Uri.parse(avatarUri));
                } catch (Exception ignored) {
                    int fallbackId = getResources().getIdentifier("drawer_header", "drawable", getPackageName());
                    if (fallbackId != 0) {
                        profileAvatar.setImageResource(fallbackId);
                    }
                }
            }
        }

        String unsetText = getStringByName("ai_model_unset", "Not configured yet");
        if (baseValue != null) {
            baseValue.setText(getModelDisplayName(prefs, MODEL_TYPE_BASE, unsetText));
        }
        if (thinkValue != null) {
            thinkValue.setText(getModelDisplayName(prefs, MODEL_TYPE_THINK, unsetText));
        }
        if (imageValue != null) {
            imageValue.setText(getModelDisplayName(prefs, MODEL_TYPE_IMAGE, unsetText));
        }
        if (visionValue != null) {
            visionValue.setText(getModelDisplayName(prefs, MODEL_TYPE_VISION, unsetText));
        }
    }

    private String getModelDisplayName(SharedPreferences prefs, int modelType, String fallback) {
        String key = getModelPrefix(modelType) + "_" + KEY_MODEL_NAME_FIELD;
        String value = prefs.getString(key, "");
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private String getModelPrefix(int modelType) {
        switch (modelType) {
            case MODEL_TYPE_THINK:
                return "AI_MODEL_THINK";
            case MODEL_TYPE_IMAGE:
                return "AI_MODEL_IMAGE";
            case MODEL_TYPE_VISION:
                return "AI_MODEL_VISION";
            case MODEL_TYPE_BASE:
            default:
                return "AI_MODEL_BASE";
        }
    }

    private void bindClickByName(View parent, String idName, View.OnClickListener listener) {
        View target = findViewByName(parent, idName);
        if (target != null) {
            target.setOnClickListener(listener);
        }
    }

    private View findViewByName(View parent, String idName) {
        int id = getResources().getIdentifier(idName, "id", getPackageName());
        if (id == 0 || parent == null) {
            return null;
        }
        return parent.findViewById(id);
    }

    private String getStringByName(String name, String fallback) {
        int resId = getResources().getIdentifier(name, "string", getPackageName());
        if (resId == 0) {
            return fallback;
        }
        return getString(resId);
    }

    private TextView asTextView(View view) {
        return view instanceof TextView ? (TextView) view : null;
    }

    private ImageView asImageView(View view) {
        return view instanceof ImageView ? (ImageView) view : null;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_INTENT_URI, getIntent().getData().toString());
        outState.putInt(KEY_PROVIDER_ID, providerId);
        if (documentUri != null) {
            outState.putString(KEY_DOCUMENT_URI, documentUri.toString());
        }
        // If this activity was opened via contentUri
        outState.putBoolean(KEY_IS_EDITABLE, isDocEditable);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_WRITE_EXTERNAL_STORAGE:
                if (permissions.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    loadDocument();
                } else {
                    Toast.makeText(this, getString(R.string.storage_permission_required), Toast.LENGTH_SHORT).show();
                    finishAndRemoveTask();
                    break;
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * When we get the file via a content: URI, we need to put it to a temp file.
     */
    private boolean copyFileToTemp() {
        final ContentResolver contentResolver = getContentResolver();
        class CopyThread extends Thread {
            /** Whether copy operation was successful. */
            private boolean result = false;

            @Override
            public void run() {
                InputStream inputStream = null;
                OutputStream outputStream = null;
                // CSV files need a .csv suffix to be opened in Calc.
                String suffix = null;
                @Nullable
                String intentType = mActivity.getIntent().getType();
                if (mActivity.getIntent().getType() == null) {
                    intentType = getMimeType();
                }
                // K-9 mail uses the first, GMail uses the second variant.
                if ("text/comma-separated-values".equals(intentType) || "text/csv".equals(intentType))
                    suffix = ".csv";
                else if ("application/pdf".equals(intentType))
                    suffix = ".pdf";
                else if ("application/vnd.ms-excel".equals(intentType))
                    suffix = ".xls";
                else if ("application/vnd.ms-powerpoint".equals(intentType))
                    suffix = ".ppt";
                try {
                    try {
                        Uri uri = mActivity.getIntent().getData();
                        inputStream = contentResolver.openInputStream(uri);

                        mTempFile = File.createTempFile("LibreOffice", suffix, mActivity.getCacheDir());
                        outputStream = new FileOutputStream(mTempFile);

                        byte[] buffer = new byte[1024];
                        int length;
                        long bytes = 0;
                        while ((length = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, length);
                            bytes += length;
                        }

                        Log.i(TAG, "Success copying " + bytes + " bytes from " + uri + " to " + mTempFile);
                    } finally {
                        if (inputStream != null)
                            inputStream.close();
                        if (outputStream != null)
                            outputStream.close();
                        result = true;
                    }
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "file not found: " + e.getMessage());
                    result = false;
                } catch (IOException e) {
                    Log.e(TAG, "exception: " + e.getMessage());
                    result = false;
                }
            }
        }
        CopyThread copyThread = new CopyThread();
        copyThread.start();
        try {
            // wait for copy operation to finish
            // NOTE: might be useful to add some indicator in UI for long copy operations
            // involving network...
            copyThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return copyThread.result;
    }

    /**
     * Check that we have created a temp file, and if yes, copy it back to the
     * content: URI.
     */
    private void copyTempBackToIntent() {
        if (!isDocEditable || mTempFile == null || getIntent().getData() == null
                || !getIntent().getData().getScheme().equals(ContentResolver.SCHEME_CONTENT))
            return;

        final ContentResolver contentResolver = getContentResolver();
        try {
            Thread copyThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    InputStream inputStream = null;
                    OutputStream outputStream = null;
                    try {
                        try {
                            inputStream = new FileInputStream(mTempFile);

                            int len = inputStream.available();
                            if (len <= 0)
                                // empty for some reason & do not write it back
                                return;

                            Uri uri = getIntent().getData();
                            try {
                                outputStream = contentResolver.openOutputStream(uri, "wt");
                            } catch (FileNotFoundException e) {
                                Log.i(TAG, "failed with the 'wt' mode, trying without: " + e.getMessage());
                                outputStream = contentResolver.openOutputStream(uri);
                            }

                            byte[] buffer = new byte[1024];
                            int length;
                            long bytes = 0;
                            while ((length = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, length);
                                bytes += length;
                            }

                            Log.i(TAG, "Success copying " + bytes + " bytes from " + mTempFile + " to " + uri);
                        } finally {
                            if (inputStream != null)
                                inputStream.close();
                            if (outputStream != null)
                                outputStream.close();
                        }
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, "file not found: " + e.getMessage());
                    } catch (Exception e) {
                        Log.e(TAG, "exception: " + e.getMessage());
                    }
                }
            });
            copyThread.start();
            copyThread.join();
        } catch (Exception e) {
            Log.i(TAG, "copyTempBackToIntent: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDocumentSettingsDrawer();
        Log.i(TAG, "onResume..");
    }

    @Override
    protected void onPause() {
        // A Save similar to an autosave
        if (documentLoaded)
            postMobileMessageNative("save dontTerminateEdit=1 dontSaveIfUnmodified=1");

        super.onPause();
        Log.d(TAG, "onPause() - hinting to save, we might need to return to the doc");
    }

    @Override
    protected void onDestroy() {
        if (!documentLoaded) {
            resetAiSessionState(true);
            super.onDestroy();
            return;
        }
        resetAiSessionState(true);
        nativeLooper.quit();

        // Remove the webview from the hierarchy & destroy
        final ViewGroup viewGroup = (ViewGroup) mWebView.getParent();
        if (viewGroup != null)
            viewGroup.removeView(mWebView);
        mWebView.destroy();
        mWebView = null;
        mMobileSocket = null;
        mobileSocketDrainScheduled.set(false);

        // Most probably the native part has already got a 'BYE' from
        // finishWithProgress(), but it is actually better to send it twice
        // than never, so let's call it from here too anyway
        documentLoaded = false;
        postMobileMessageNative("BYE");

        mProgressDialog.dismiss();

        super.onDestroy();
        Log.i(TAG, "onDestroy() - we know we are leaving the document");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQUEST_SELECT_IMAGE_FILE) {
            imagePickerInFlight = false;
        }
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_SELECT_IMAGE_FILE) {
                if (valueCallback != null) {
                    valueCallback.onReceiveValue(null);
                    valueCallback = null;
                }
            }
            return;
        }

        /*
         * Copy is just save-as in general but with TakeOwnership.
         * Meaning that we will switch to the copied (saved-as) document in the bg
         * this way we don't need to reload the activity.
         */
        boolean requestCopy = false;
        if (requestCode == REQUEST_COPY) {
            requestCopy = true;
            if (Objects.equals(getMimeType(), "text/plain")) {
                requestCode = REQUEST_SAVEAS_ODT;
            } else if (Objects.equals(getMimeType(), "text/comma-separated-values")) {
                requestCode = REQUEST_SAVEAS_ODS;
            } else if (Objects.equals(getMimeType(), "application/vnd.ms-excel.sheet.binary.macroenabled.12")) {
                requestCode = REQUEST_SAVEAS_ODS;
            } else {
                String filename = getFileName(true);
                String extension = filename.substring(filename.lastIndexOf('.') + 1);
                requestCode = getRequestIDForFormat(extension);
                assert (requestCode != 0);
            }
        }
        switch (requestCode) {
            case REQUEST_SELECT_IMAGE_FILE:
                if (valueCallback == null)
                    return;
                valueCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
                valueCallback = null;
                return;
            case REQUEST_SAVEAS_PDF:
            case REQUEST_SAVEAS_RTF:
            case REQUEST_SAVEAS_ODT:
            case REQUEST_SAVEAS_ODP:
            case REQUEST_SAVEAS_ODS:
            case REQUEST_SAVEAS_DOCX:
            case REQUEST_SAVEAS_PPTX:
            case REQUEST_SAVEAS_XLSX:
            case REQUEST_SAVEAS_DOC:
            case REQUEST_SAVEAS_PPT:
            case REQUEST_SAVEAS_XLS:
            case REQUEST_SAVEAS_EPUB:
                if (intent == null) {
                    return;
                }
                String format = getFormatForRequestCode(requestCode);
                File _tempFile = null;
                if (format != null) {
                    InputStream inputStream = null;
                    OutputStream outputStream = null;
                    try {
                        final File tempFile = File.createTempFile("LibreOffice", "." + format, this.getCacheDir());
                        LOActivity.this.saveAs(tempFile.toURI().toString(), format,
                                requestCopy ? "TakeOwnership" : null);

                        inputStream = new FileInputStream(tempFile);
                        try {
                            outputStream = getContentResolver().openOutputStream(intent.getData(), "wt");
                        } catch (FileNotFoundException e) {
                            Log.i(TAG, "failed with the 'wt' mode, trying without: " + e.getMessage());
                            outputStream = getContentResolver().openOutputStream(intent.getData());
                        }

                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, len);
                        }
                        outputStream.flush();
                        _tempFile = tempFile;
                    } catch (Exception e) {
                        Toast.makeText(this, "Something went wrong while Saving as: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    } finally {
                        try {
                            if (inputStream != null)
                                inputStream.close();
                            if (outputStream != null)
                                outputStream.close();
                        } catch (Exception e) {
                        }
                    }
                    if (requestCopy == true) {
                        assert (_tempFile != null);
                        mTempFile = _tempFile;
                        getIntent().setData(intent.getData());
                        /** add the document to recents */
                        addIntentToRecents(intent);
                        // This will actually change the doc permission to write
                        // It's a toggle for blue edit button, but also changes permission
                        // Toggle is achieved by calling setPermission('edit') in javascript
                        callFakeWebsocketOnMessage("mobile: readonlymode");
                        isDocEditable = true;
                    }
                    return;
                }
                break;
        }
        Toast.makeText(this, "Unknown request", Toast.LENGTH_LONG).show();
    }

    private void addIntentToRecents(Intent intent) {
        Uri treeFileUri = intent.getData();
        SharedPreferences recentPrefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
        String recentList = recentPrefs.getString(RECENT_DOCUMENTS_KEY, "");
        recentList = treeFileUri.toString() + "\n" + recentList;
        recentPrefs.edit().putString(RECENT_DOCUMENTS_KEY, recentList).apply();
    }

    private String getFormatForRequestCode(int requestCode) {
        switch (requestCode) {
            case REQUEST_SAVEAS_PDF:
                return "pdf";
            case REQUEST_SAVEAS_RTF:
                return "rtf";
            case REQUEST_SAVEAS_ODT:
                return "odt";
            case REQUEST_SAVEAS_ODP:
                return "odp";
            case REQUEST_SAVEAS_ODS:
                return "ods";
            case REQUEST_SAVEAS_DOCX:
                return "docx";
            case REQUEST_SAVEAS_PPTX:
                return "pptx";
            case REQUEST_SAVEAS_XLSX:
                return "xlsx";
            case REQUEST_SAVEAS_DOC:
                return "doc";
            case REQUEST_SAVEAS_PPT:
                return "ppt";
            case REQUEST_SAVEAS_XLS:
                return "xls";
            case REQUEST_SAVEAS_EPUB:
                return "epub";
        }
        return null;
    }

    /** Show the Saving progress and finish the app. */
    private void finishWithProgress() {
        if (!documentLoaded) {
            finishAndRemoveTask();
            return;
        }
        mProgressDialog.indeterminate(R.string.exiting);

        // The 'BYE' takes a considerable amount of time, we need to post it
        // so that it starts after the saving progress is actually shown
        getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                documentLoaded = false;
                cancelAllAiRequests();
                postMobileMessageNative("BYE");
                // copyTempBackToIntent();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mProgressDialog.dismiss();
                    }
                });
                finishAndRemoveTask();
            }
        });
    }

    private void loadDocument() {
        mProgressDialog.determinate(R.string.loading);
        aiBridgeInjected = false;

        // setup the COOLWSD
        ApplicationInfo applicationInfo = getApplicationInfo();
        String dataDir = applicationInfo.dataDir;
        Log.i(TAG, String.format("Initializing LibreOfficeKit, dataDir=%s\n", dataDir));

        String cacheDir = getApplication().getCacheDir().getAbsolutePath();
        String apkFile = getApplication().getPackageResourcePath();
        AssetManager assetManager = getResources().getAssets();
        String uiMode = (isLargeScreen() && !isChromeOS()) ? "notebookbar" : "classic";
        String userName = getPrefs().getString(USER_NAME_KEY, "Guest User");
        createCOOLWSD(dataDir, cacheDir, apkFile, assetManager, urlToLoad, uiMode, userName);

        // trigger the load of the document
        String finalUrlToLoad = "file:///android_asset/dist/cool.html?file_path=" +
                urlToLoad + "&closebutton=1";

        // set the language
        String language = getResources().getConfiguration().locale.toLanguageTag();

        Log.i(TAG, "Loading with language:  " + language);

        finalUrlToLoad += "&lang=" + language;

        if (isDocEditable) {
            finalUrlToLoad += "&permission=edit";
        } else {
            finalUrlToLoad += "&permission=readonly";
        }

        if (isDocDebuggable) {
            finalUrlToLoad += "&debug=true";
        }

        if (isLargeScreen() && !isChromeOS())
            finalUrlToLoad += "&userinterfacemode=notebookbar";

        if (isDarkMode()) {
            finalUrlToLoad += "&darkTheme=true";
        }

        // load the page
        mWebView.loadUrl(finalUrlToLoad);

        documentLoaded = true;
        ensureTopToolbarController().refreshDocumentTitle();

        loadDocumentMillis = android.os.SystemClock.uptimeMillis();
    }

    private boolean isDarkMode() {
        SharedPreferences recentPrefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
        int mode = recentPrefs.getInt(NIGHT_MODE_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        switch (mode) {
            case -1:
                int darkModeFlag = getBaseContext().getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;
                return darkModeFlag == Configuration.UI_MODE_NIGHT_YES;
            case 1:
                return false;
            case 2:
                return true;
        }
        return false;
    }

    static {
        System.loadLibrary("androidapp");
    }

    /**
     * Used for determining tablets
     */
    public boolean isLargeScreen() {
        return getResources().getBoolean(R.bool.isLargeScreen);
    }

    public SharedPreferences getPrefs() {
        return sPrefs;
    }

    /**
     * Initialize the COOLWSD to load 'loadFileURL'.
     */
    public native void createCOOLWSD(String dataDir, String cacheDir, String apkFile, AssetManager assetManager,
            String loadFileURL, String uiMode, String userName);

    /**
     * Passing messages from JS (instead of the websocket communication).
     */
    @JavascriptInterface
    public void postMobileMessage(String message) {
        Log.d(TAG, "postMobileMessage: " + message);

        String[] messageAndParameterArray = message.split(" ", 2); // the command and the rest (that can potentially
                                                                   // contain spaces too)

        if (beforeMessageFromWebView(messageAndParameterArray)) {
            postMobileMessageNative(message);
            afterMessageFromWebView(messageAndParameterArray);
        }
    }

    /**
     * Call the post method form C++
     */
    public native void postMobileMessageNative(String message);

    /**
     * Passing messages from JS (instead of the websocket communication).
     */
    @JavascriptInterface
    public void postMobileError(String message) {
        // TODO handle this
        Log.d(TAG, "postMobileError: " + message);
    }

    /**
     * Passing messages from JS (instead of the websocket communication).
     */
    @JavascriptInterface
    public void postMobileDebug(String message) {
        // TODO handle this
        Log.d(TAG, "postMobileDebug: " + message);
    }

    /**
     * Provide the info that this app is actually running under ChromeOS - so
     * has to mostly look like on desktop.
     */
    @JavascriptInterface
    public boolean isChromeOS() {
        return isChromeOS(this);
    }

    /**
     * Passing message the other way around - from Java to the FakeWebSocket in JS.
     */
    void callFakeWebsocketOnMessage(final String message) {
        rawCallFakeWebsocketOnMessage(message.getBytes());
    }

    /**
     * Similar to callFakeWebsocketOnMessage but 'message' is instead any expression
     * evaluable as
     * JavaScript. For example, you should use this to pass Base64ToArrayBuffer
     * invocations to
     * the fake websocket
     */
    void rawCallFakeWebsocketOnMessage(final byte[] message) {
        try {
            if (mMobileSocket == null) {
                return;
            }
            mMobileSocket.queueSend(message, this::scheduleMobileSocketDrain);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // update progress bar when loading
        if (messageStartsWith(message, "progress")) {
            runOnUiThread(() -> {
                JSONObject messageJSON;
                String messageID;
                String messageString;
                try {
                    messageString = new String(message, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }

                int jsonStart = messageString.indexOf("{");
                if (jsonStart == -1) {
                    return;
                }

                try {
                    messageJSON = new JSONObject(messageString.substring(jsonStart));
                    messageID = messageJSON.getString("id");
                } catch (JSONException e) {
                    return;
                }

                if (messageID.equals("finish")) {
                    mProgressDialog.dismiss();
                    injectAiBridgeIfNeeded();
                    if (BuildConfig.GOOGLE_PLAY_ENABLED && rateAppController != null)
                        rateAppController.askUserForRating();
                    return;
                }

                try {
                    String text = messageJSON.getString("text");
                    mProgressDialog.mTextView.setText(text);
                } catch (JSONException ignored) {
                }

                try {
                    int progress = messageJSON.getInt("value");
                    mProgressDialog.determinateProgress(progress);
                } catch (JSONException ignored) {
                }
            });
        } else if (messageStartsWith(message, "error:")) {
            runOnUiThread(() -> mProgressDialog.dismiss());
        }
    }

    private void scheduleMobileSocketDrain() {
        if (mWebView == null || mMobileSocket == null || !documentLoaded) {
            return;
        }
        if (!mobileSocketDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        mWebView.post(() -> {
            try {
                if (mWebView == null || mMobileSocket == null || !documentLoaded) {
                    return;
                }
                mWebView.loadUrl("javascript:window.socket.doSend();");
            } finally {
                mobileSocketDrainScheduled.set(false);
                if (mMobileSocket != null && mMobileSocket.hasPendingMessages()) {
                    scheduleMobileSocketDrain();
                }
            }
        });
    }

    /**
     * @param message The message to test for the prefix
     * @param prefix  The prefix to test for
     * @return true if the decoded message starts with the prefix, else false
     */
    private static boolean messageStartsWith(byte[] message, String prefix) {
        byte[] prefixBytes = prefix.getBytes();
        for (int i = 0; i < prefixBytes.length; i++) {
            if (message[i] != prefixBytes[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * return true to pass the message to the native part or false to block the
     * message
     */
    private boolean beforeMessageFromWebView(String[] messageAndParam) {
        switch (messageAndParam[0]) {
            case "BYE":
                finishWithProgress();
                return false;
            case "PRINT":
                getMainHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        LOActivity.this.initiatePrint();
                    }
                });
                return false;
            case "SAVE":
                copyTempBackToIntent();
                sendBroadcast(messageAndParam[0], messageAndParam[1]);
                return false;
            case "downloadas":
                initiateSaveAs(messageAndParam[1]);
                return false;
            case "uno":
                switch (messageAndParam[1]) {
                    case ".uno:Paste":
                        return performPaste();
                    default:
                        break;
                }
                break;
            case "DIM_SCREEN": {
                getMainHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                });
                return false;
            }
            case "LIGHT_SCREEN": {
                getMainHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    }
                });
                return false;
            }
            case "MOBILEWIZARD": {
                switch (messageAndParam[1]) {
                    case "show":
                        mMobileWizardVisible = true;
                        break;
                    case "hide":
                        mMobileWizardVisible = false;
                        break;
                }
                return false;
            }
            case "HYPERLINK": {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(messageAndParam[1]));
                startActivity(intent);
                return false;
            }
            case "EDITMODE": {
                switch (messageAndParam[1]) {
                    case "on":
                        cancelPreviewModeSwitchAck("editmode_on");
                        updateEditModeState(true, "js_editmode_on");
                        // prompt for file conversion
                        requestForOdf();
                        // Hide the soft keyboard so it doesn't auto-popup on edit mode entry.
                        hideKeyboard();
                        runPendingAfterEditMode();
                        break;
                    case "off":
                        updateEditModeState(false, "js_editmode_off");
                        completePreviewModeSwitchAck("editmode_off");
                        break;
                }
                return false;
            }
            case "SELECTIONMENU": {
                if (messageAndParam.length > 1 && "hide".equals(messageAndParam[1])) {
                    getMainHandler().post(() -> ensureSelectionMenuController().hide());
                    return false;
                }
                if (messageAndParam.length > 1 && messageAndParam[1] != null &&
                        messageAndParam[1].startsWith("show ")) {
                    try {
                        String[] parts = messageAndParam[1].split(" ");
                        if (parts.length >= 3) {
                            final float anchorX = Float.parseFloat(parts[1]);
                            final float anchorY = Float.parseFloat(parts[2]);
                            getMainHandler().post(() ->
                                    ensureSelectionMenuController().showAtWindow(anchorX, anchorY));
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "selection_menu_bad_anchor", e);
                    }
                    return false;
                }
                return false;
            }
            case "DOC_GESTURE_GUARD": {
                boolean enable = messageAndParam.length > 1
                        && messageAndParam[1] != null
                        && messageAndParam[1].trim().toLowerCase(Locale.ROOT).startsWith("on");
                boolean changed = docGestureGuardEnabled != enable;
                docGestureGuardEnabled = enable;
                if (mWebView != null) {
                    mWebView.setDocumentGestureGuardEnabled(enable);
                    if (!enable) {
                        mWebView.abortDocumentScroll();
                    }
                }
                long now = android.os.SystemClock.uptimeMillis();
                if (changed || now - lastDocGestureGuardLogAt > 1200) {
                    Log.i(TAG, "doc_gesture_guard " + (enable ? "on" : "off"));
                    lastDocGestureGuardLogAt = now;
                }
                if (!enable && changed) {
                    triggerSelectionStateSync("reconnect_done", mIsEditModeActive);
                }
                return false;
            }
            case "hideProgressbar": {
                if (mProgressDialog != null)
                    mProgressDialog.dismiss();
                maybeAutoGenerateAiContentAfterLoad();
                maybeAutoOpenAiPanelAfterLoad();
                return false;
            }
            case "loadwithpassword": {
                mProgressDialog.determinate(R.string.loading);
                return true;
            }
            case "REQUESTFILECOPY": {
                requestForCopy();
                return false;
            }
            case "ai.request": {
                handleAiRequestFromWeb(messageAndParam.length > 1 ? messageAndParam[1] : "{}");
                return false;
            }
            case "ai.cancel": {
                handleAiCancelFromWeb(messageAndParam.length > 1 ? messageAndParam[1] : "{}");
                return false;
            }
            case "ai.accept": {
                handleAiAcceptFromWeb(messageAndParam.length > 1 ? messageAndParam[1] : "{}");
                return false;
            }
        }
        return true;
    }

    private void handleAiRequestFromWeb(String payload) {
        try {
            JSONObject request = new JSONObject(payload);
            startAiRequestSession(request, payload.length());
        } catch (JSONException e) {
            dispatchAiError("", "invalid_payload", "Invalid ai.request payload");
            Log.e(TAG, "Invalid ai.request payload", e);
        }
    }

    private void startAiRequestSession(JSONObject request, int payloadChars) {
        try {
            String requestId = request.optString("requestId", "");
            if (requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
                request.put("requestId", requestId);
            }

            final String finalRequestId = requestId;
            String requestMode = request.optString("taskType", AI_MODE_CHAT);
            Log.i(TAG, "ai_request_handle_start requestId=" + finalRequestId
                    + " mode=" + requestMode
                    + " payloadChars=" + payloadChars);
            persistAiConfigFromRequest(request);
            cancelAiRequest(finalRequestId);
            dispatchAiState(finalRequestId, AI_STATE_LOADING, "AI request queued");

            AiRequestSession session = new AiRequestSession();
            aiRequestSessions.put(finalRequestId, session);
            aiRequestModeById.put(finalRequestId, requestMode);

            Thread requestThread = new Thread(() -> {
                Log.i(TAG, "ai_request_thread_start requestId=" + finalRequestId + " mode=" + requestMode);
                try {
                    runAiRequest(finalRequestId, request, session);
                } catch (Throwable t) {
                    if (!session.isCancelled()) {
                        dispatchAiState(finalRequestId, AI_STATE_ERROR, "AI request failed");
                        dispatchAiError(finalRequestId, "request_failed",
                                t.getMessage() == null ? "AI request failed" : t.getMessage());
                    }
                    Log.e(TAG, "ai_request_thread_uncaught requestId=" + finalRequestId, t);
                } finally {
                    aiRequestSessions.remove(finalRequestId);
                    Log.i(TAG, "ai_request_thread_finish requestId=" + finalRequestId);
                }
            }, "cool-ai-" + finalRequestId);
            requestThread.start();
            Log.i(TAG, "ai_request_thread_dispatched requestId=" + finalRequestId);
        } catch (JSONException e) {
            dispatchAiError("", "invalid_payload", "Invalid ai.request payload");
            Log.e(TAG, "Invalid ai.request payload", e);
        }
    }

    private void handleAiCancelFromWeb(String payload) {
        try {
            JSONObject request = new JSONObject(payload);
            String requestId = request.optString("requestId", "");
            if (requestId.isEmpty()) {
                dispatchAiError("", "invalid_payload", "requestId is required for ai.cancel");
                return;
            }
            cancelAiRequest(requestId);
            aiRequestModeById.remove(requestId);
            cleanupRequestUiState(requestId);
            dispatchAiState(requestId, AI_STATE_CANCELLED, "AI request cancelled");
        } catch (JSONException e) {
            dispatchAiError("", "invalid_payload", "Invalid ai.cancel payload");
            Log.e(TAG, "Invalid ai.cancel payload", e);
        }
    }

    private void handleAiAcceptFromWeb(String payload) {
        try {
            JSONObject request = new JSONObject(payload);
            String requestId = request.optString("requestId", "");
            String text = request.optString("text", "");
            if (text.isEmpty()) {
                dispatchAiError(requestId, "empty_text", "Nothing to insert");
                return;
            }

            final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            runOnUiThread(() -> paste("text/plain;charset=utf-8", bytes));
        } catch (JSONException e) {
            dispatchAiError("", "invalid_payload", "Invalid ai.accept payload");
            Log.e(TAG, "Invalid ai.accept payload", e);
        }
    }

    private void runAiRequest(String requestId, JSONObject request, AiRequestSession session) {
        Log.i(TAG, "ai_request_run_start requestId=" + requestId
                + " mode=" + request.optString("taskType", AI_MODE_CHAT));
        JSONObject context = request.optJSONObject("context");
        String taskType = request.optString("taskType", AI_MODE_CHAT);
        JSONArray history = request.optJSONArray("history");
        boolean firstDocQaTurn = request.optBoolean("docQaFirstTurn", false);
        boolean hasEndpoint = context != null && context.has("endpoint");
        boolean hasApiKey = context != null && context.has("apiKey");
        String endpoint = context != null ? context.optString("endpoint", "") : "";
        String apiKey = context != null ? context.optString("apiKey", "") : "";
        String model = context != null ? context.optString("model", "") : "";
        String modelMode = request.optString("modelMode", "cloud");
        endpoint = endpoint == null ? "" : endpoint.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null ? "" : model.trim();

        if ("base".equalsIgnoreCase(modelMode)) {
            SharedPreferences modelPrefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
            endpoint = modelPrefs.getString("AI_MODEL_BASE_url", endpoint);
            apiKey = modelPrefs.getString("AI_MODEL_BASE_api_key", apiKey);
            model = modelPrefs.getString("AI_MODEL_BASE_model_name", model);
            endpoint = endpoint == null ? "" : endpoint.trim();
            apiKey = apiKey == null ? "" : apiKey.trim();
            model = model == null ? "" : model.trim();
        }

        // Only fallback to persisted values when the field is absent in the request.
        // If the caller explicitly sends an empty field, treat it as unconfigured.
        if (!hasEndpoint && endpoint.isEmpty()) {
            endpoint = getPrefs().getString(AI_PREF_ENDPOINT, AI_DEFAULT_ENDPOINT);
        }
        if (!hasApiKey && apiKey.isEmpty()) {
            apiKey = getPrefs().getString(AI_PREF_API_KEY, "");
        }
        if (model.isEmpty()) {
            model = getPrefs().getString(AI_PREF_MODEL, AI_DEFAULT_MODEL);
        }

        if (endpoint == null || endpoint.isEmpty()) {
            String message = "请先在设置中配置基础模型的接口地址。";
            Log.w(TAG, "ai_config_missing requestId=" + requestId + " field=endpoint modelMode=" + modelMode);
            dispatchAiState(requestId, AI_STATE_UNCONFIGURED, message);
            dispatchAiError(requestId, "config_missing", message);
            return;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            String message = "请先在设置中配置基础模型的 API Key。";
            Log.w(TAG, "ai_config_missing requestId=" + requestId + " field=apiKey modelMode=" + modelMode);
            dispatchAiState(requestId, AI_STATE_UNCONFIGURED, message);
            dispatchAiError(requestId, "config_missing", message);
            return;
        }

        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", AI_DEFAULT_SYSTEM_PROMPT));
            Log.i(TAG, "ai_prompt_mode=" + taskType + " requestId=" + requestId);
            if (AI_MODE_DOC_QA.equals(taskType)) {
                if (firstDocQaTurn) {
                    String docText = extractDocumentTextForDocQaFirstTurn(requestId);
                    if (docText.isEmpty()) {
                        dispatchAiState(requestId, AI_STATE_ERROR, "文档全文提取失败");
                        dispatchAiError(requestId, "doc_extract_failed", "文档全文提取失败，请稍后重试");
                        return;
                    }
                    String question = extractLatestUserQuestion(history, context, request);
                    String combinedPrompt = "你是文档问答助手，请只基于以下文档内容回答问题；若文档未包含答案，请明确说明。\n\n"
                            + "【全文内容】\n" + docText + "\n\n"
                            + "【用户问题】\n" + question;
                    Log.i(TAG, "doc_qa_first_turn_context_chars requestId=" + requestId + " chars=" + docText.length());
                    messages.put(new JSONObject().put("role", "user").put("content", combinedPrompt));
                } else {
                    JSONArray historyMessages = buildAiMessagesFromHistory(history);
                    if (historyMessages.length() == 0) {
                        messages.put(new JSONObject().put("role", "user").put("content", buildAiUserPrompt(request)));
                    } else {
                        for (int i = 0; i < historyMessages.length(); i++) {
                            messages.put(historyMessages.getJSONObject(i));
                        }
                    }
                }
            } else if (AI_MODE_CHAT.equals(taskType)) {
                JSONArray historyMessages = buildAiMessagesFromHistory(history);
                if (historyMessages.length() == 0) {
                    messages.put(new JSONObject().put("role", "user").put("content", buildAiUserPrompt(request)));
                } else {
                    for (int i = 0; i < historyMessages.length(); i++) {
                        messages.put(historyMessages.getJSONObject(i));
                    }
                }
            } else {
                messages.put(new JSONObject().put("role", "user").put("content", buildAiUserPrompt(request)));
            }
            aiRequestManager.execute(requestId, endpoint, apiKey, model, messages, session,
                    new AiRequestManager.Callback() {
                        @Override
                        public String sanitizePayload(String callbackRequestId, Object raw, String stage) {
                            return sanitizeAiTextPayload(callbackRequestId, raw, stage);
                        }

                        @Override
                        public void onStreamingState(String callbackRequestId) {
                            dispatchAiState(callbackRequestId, AI_STATE_STREAMING, "AI response streaming");
                        }

                        @Override
                        public void onStreamDelta(String callbackRequestId, String delta) throws JSONException {
                            JSONObject streamPayload = new JSONObject();
                            streamPayload.put("requestId", callbackRequestId);
                            streamPayload.put("delta", delta);
                            dispatchAiEvent("ai.stream", streamPayload);
                        }

                        @Override
                        public void onDone(String callbackRequestId, String fullText) throws JSONException {
                            JSONObject donePayload = new JSONObject();
                            donePayload.put("requestId", callbackRequestId);
                            donePayload.put("fullText", fullText);
                            dispatchAiEvent("ai.done", donePayload);
                        }

                        @Override
                        public void onError(String callbackRequestId, String code, String message) {
                            dispatchAiError(callbackRequestId, code, message);
                        }
                    });
        } catch (Exception e) {
            if (!session.isCancelled()) {
                dispatchAiState(requestId, AI_STATE_ERROR, "AI request failed");
                dispatchAiError(requestId, "request_failed",
                        e.getMessage() == null ? "AI request failed" : e.getMessage());
                Log.e(TAG, "runAiRequest failed", e);
            }
        }
    }

    private String buildAiUserPrompt(JSONObject request) {
        String taskType = request.optString("taskType", "rewrite");
        String selection = request.optString("selection", "");
        String modelMode = request.optString("modelMode", "cloud");
        String contextString = "";
        String question = "";

        JSONObject context = request.optJSONObject("context");
        if (context != null) {
            contextString = context.toString();
            question = context.optString("question", context.optString("prompt", ""));
        }

        if (selection.isEmpty() && context != null) {
            selection = context.optString("selection", "");
        }

        if (question == null || question.trim().isEmpty()) {
            question = request.optString("prompt", "");
        }
        question = question == null ? "" : question.trim();

        if ("doc_qa".equalsIgnoreCase(taskType)) {
            return "你是文档问答助手。请优先依据【选中文本】回答问题。\n"
                    + "如果选中文本为空，请明确告知“未检测到选中文本”，再给出尽量保守的回答。\n\n"
                    + "【用户问题】\n" + question + "\n\n"
                    + "【选中文本】\n" + selection;
        }

        if ("chat".equalsIgnoreCase(taskType)) {
            return "你是简洁的中文助手，请直接回答用户问题。\n\n"
                    + "【用户问题】\n" + question
                    + (selection.isEmpty() ? "" : ("\n\n【参考选中文本】\n" + selection));
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("TaskType: ").append(taskType).append('\n');
        prompt.append("ModelMode: ").append(modelMode).append('\n');
        if (!contextString.isEmpty()) {
            prompt.append("Context: ").append(contextString).append('\n');
        }
        prompt.append("SelectedText:\n").append(selection);
        return prompt.toString();
    }

    private void cancelAiRequest(String requestId) {
        AiRequestSession session = aiRequestSessions.remove(requestId);
        if (session == null) {
            return;
        }
        session.cancel();
    }

    private void cancelAllAiRequests() {
        for (Map.Entry<String, AiRequestSession> entry : aiRequestSessions.entrySet()) {
            entry.getValue().cancel();
        }
        aiRequestSessions.clear();
    }

    private void dispatchAiError(String requestId, String code, String message) {
        try {
            JSONObject errorPayload = new JSONObject();
            errorPayload.put("requestId", requestId);
            errorPayload.put("code", code);
            errorPayload.put("message", message);
            dispatchAiEvent("ai.error", errorPayload);
        } catch (JSONException ignored) {
            Log.e(TAG, "Failed to dispatch ai.error");
        }
    }

    private void dispatchAiState(String requestId, String state, String message) {
        try {
            JSONObject statePayload = new JSONObject();
            statePayload.put("requestId", requestId);
            statePayload.put("state", state);
            statePayload.put("message", message);
            dispatchAiEvent("ai.state", statePayload);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to dispatch ai.state", e);
        }
    }

    private void persistAiConfigFromRequest(JSONObject request) {
        JSONObject context = request.optJSONObject("context");
        if (context == null) {
            return;
        }

        boolean hasEndpoint = context.has("endpoint");
        boolean hasApiKey = context.has("apiKey");
        String endpoint = context.optString("endpoint", "").trim();
        String model = context.optString("model", "").trim();
        String apiKey = context.optString("apiKey", "").trim();

        SharedPreferences.Editor editor = getPrefs().edit();
        boolean changed = false;
        if (hasEndpoint) {
            if (endpoint.isEmpty()) {
                editor.remove(AI_PREF_ENDPOINT);
            } else {
                editor.putString(AI_PREF_ENDPOINT, endpoint);
            }
            changed = true;
        }
        if (!model.isEmpty()) {
            editor.putString(AI_PREF_MODEL, model);
            changed = true;
        }
        if (hasApiKey) {
            if (apiKey.isEmpty()) {
                editor.remove(AI_PREF_API_KEY);
            } else {
                editor.putString(AI_PREF_API_KEY, apiKey);
            }
            changed = true;
        }
        if (changed) {
            editor.apply();
        }
    }

    private void dispatchAiEvent(String type, JSONObject payload) {
        JSONObject event = new JSONObject();
        try {
            event.put("type", type);
            if (payload != null) {
                Iterator<String> keys = payload.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    event.put(key, payload.get(key));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build AI event payload", e);
            return;
        }

        handleAiNativeEvent(event);

        final String script = "(function(){" +
                "var data=JSON.parse(" + JSONObject.quote(event.toString()) + ");" +
                "if(window.__coolAiBridge&&typeof window.__coolAiBridge.onNativeEvent==='function'){window.__coolAiBridge.onNativeEvent(data);}"
                +
                "window.dispatchEvent(new CustomEvent('cool.ai',{detail:data}));" +
                "})();";

        runOnUiThread(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script, null);
            }
        });
    }

    private void setupAiFab() {
        View aiFab = findViewById(R.id.ai_fab);
        if (aiFab == null) {
            return;
        }
        aiFab.post(() -> restoreAiFabPosition(aiFab));
        aiFab.setOnLongClickListener(v -> {
            aiFabDragging = true;
            aiFabDragged = false;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        });
        aiFab.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    aiFabDragOffsetX = event.getRawX() - v.getX();
                    aiFabDragOffsetY = event.getRawY() - v.getY();
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!aiFabDragging) {
                        return false;
                    }
                    View parent = (View) v.getParent();
                    if (parent == null) {
                        return false;
                    }
                    float rawX = event.getRawX() - aiFabDragOffsetX;
                    float rawY = event.getRawY() - aiFabDragOffsetY;
                    float maxX = Math.max(0f, parent.getWidth() - v.getWidth());
                    float maxY = getFabMaxY(parent, v);
                    v.setX(clamp(rawX, 0f, maxX));
                    v.setY(clamp(rawY, 0f, maxY));
                    aiFabDragged = true;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (aiFabDragging) {
                        aiFabDragging = false;
                        if (aiFabDragged) {
                            persistAiFabPosition(v);
                            return true;
                        }
                    }
                    return false;
                default:
                    return false;
            }
        });
        aiFab.setOnClickListener(v -> showNativeAiPanel());
    }

    private void persistAiFabPosition(View fab) {
        getPrefs().edit()
                .putFloat(AI_PREF_FAB_X, fab.getX())
                .putFloat(AI_PREF_FAB_Y, fab.getY())
                .apply();
    }

    private void restoreAiFabPosition(View fab) {
        SharedPreferences prefs = getPrefs();
        if (!prefs.contains(AI_PREF_FAB_X) || !prefs.contains(AI_PREF_FAB_Y)) {
            return;
        }
        View parent = (View) fab.getParent();
        if (parent == null) {
            return;
        }
        float savedX = prefs.getFloat(AI_PREF_FAB_X, fab.getX());
        float savedY = prefs.getFloat(AI_PREF_FAB_Y, fab.getY());
        float maxX = Math.max(0f, parent.getWidth() - fab.getWidth());
        float maxY = getFabMaxY(parent, fab);
        fab.setX(clamp(savedX, 0f, maxX));
        fab.setY(clamp(savedY, 0f, maxY));
    }

    private float getFabMaxY(View parent, View fab) {
        int reservedBottom = ensureBottomToolbarController().getReservedBottomHeightPx();
        return Math.max(0f, parent.getHeight() - fab.getHeight() - reservedBottom);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private void onDocumentLongPress(float viewX, float viewY) {
        if (!documentLoaded || mWebView == null || mIsEditModeActive) {
            return;
        }
        mWebView.evaluateJavascript(
                "(function(){try{if(window.AndroidSelectionMenu){"
                        + "window.AndroidSelectionMenu.markNativeLongPress();"
                        + "window.AndroidSelectionMenu.onLongPressAt(" + viewX + "," + viewY + ");"
                        + "}}catch(e){if(window.console&&console.warn){"
                        + "console.warn('selection_menu_long_press_failed',e);}}"
                        + "return true;})();",
                null);
    }

    private void onDocumentSelectionDrag(float viewX, float viewY) {
        if (!documentLoaded || mWebView == null || mIsEditModeActive) {
            return;
        }
        mWebView.evaluateJavascript(
                "(function(){try{if(window.AndroidSelectionMenu){"
                        + "window.AndroidSelectionMenu.updateTextSelectionEndAt(" + viewX + "," + viewY + ");"
                        + "}}catch(e){if(window.console&&console.warn){"
                        + "console.warn('selection_menu_drag_failed',e);}}"
                        + "return true;})();",
                null);
    }

    private void onDocumentSelectionDragEnd(float viewX, float viewY) {
        if (!documentLoaded || mWebView == null || mIsEditModeActive) {
            return;
        }
        mWebView.evaluateJavascript(
                "(function(){try{if(window.AndroidSelectionMenu){"
                        + "window.AndroidSelectionMenu.finishTextSelectionDrag(" + viewX + "," + viewY + ");"
                        + "}}catch(e){if(window.console&&console.warn){"
                        + "console.warn('selection_menu_drag_end_failed',e);}}"
                        + "return true;})();",
                null);
    }

    private void onDocumentSelectionDragCancel() {
        if (!documentLoaded || mWebView == null || mIsEditModeActive) {
            return;
        }
        mWebView.evaluateJavascript(
                "(function(){try{if(window.AndroidSelectionMenu){"
                        + "window.AndroidSelectionMenu.cancelGesture();"
                        + "}}catch(e){if(window.console&&console.warn){"
                        + "console.warn('selection_menu_drag_cancel_failed',e);}}"
                        + "return true;})();",
                null);
    }

    private void setupSelectionMenu() {
        if (selectionMenuController == null) {
            selectionMenuController = new SelectionMenuController(new SelectionMenuController.Host() {
                @Override
                public Context getContext() {
                    return LOActivity.this;
                }

                @Override
                public View findViewById(int id) {
                    return LOActivity.this.findViewById(id);
                }

                @Override
                public boolean isDocEditable() {
                    return LOActivity.this.isDocEditable;
                }

                @Override
                public boolean isEditModeActive() {
                    return LOActivity.this.mIsEditModeActive;
                }

                @Override
                public void ensureEditModeThen(Runnable action) {
                    LOActivity.this.ensureEditModeThen(action);
                }

                @Override
                public void executeUnoCommand(String command) {
                    LOActivity.this.executeUnoCommand(command);
                }

                @Override
                public void performPasteCommand() {
                    LOActivity.this.performPasteCommand();
                }

                @Override
                public void hideQuickActionPanel() {
                    LOActivity.this.hideQuickActionPanel();
                }
            });
        }
        selectionMenuController.setup();
    }

    private SelectionMenuController ensureSelectionMenuController() {
        if (selectionMenuController == null) {
            setupSelectionMenu();
        }
        return selectionMenuController;
    }

    private void ensureEditModeThen(Runnable action) {
        if (action == null) {
            return;
        }
        if (!isDocEditable) {
            Toast.makeText(this, "当前文档为只读，无法粘贴或剪切", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mIsEditModeActive) {
            getMainHandler().post(action);
            return;
        }
        pendingAfterEditMode = action;
        if (mWebView != null) {
            mWebView.evaluateJavascript(
                    "(function(){try{if(window.app&&app.map&&typeof app.map._switchToEditMode==='function')"
                            + "{app.map._switchToEditMode();}}catch(e){}"
                            + "return true;})();",
                    null);
        }
        getMainHandler().postDelayed(() -> {
            if (pendingAfterEditMode != action) {
                return;
            }
            if (mIsEditModeActive) {
                runPendingAfterEditMode();
            } else {
                pendingAfterEditMode = null;
                Toast.makeText(LOActivity.this, "无法进入编辑模式", Toast.LENGTH_SHORT).show();
            }
        }, 2500L);
    }

    private void runPendingAfterEditMode() {
        if (pendingAfterEditMode == null) {
            return;
        }
        Runnable action = pendingAfterEditMode;
        pendingAfterEditMode = null;
        getMainHandler().postDelayed(action, 120L);
    }

    private void performPasteCommand() {
        postMobileMessage("uno .uno:Paste");
    }

    private void setupTopToolbar() {
        ensureTopToolbarController().setup();
    }

    private TopToolbarController ensureTopToolbarController() {
        if (topToolbarController == null) {
            topToolbarController = new TopToolbarController(new TopToolbarController.Host() {
                @Override
                public View findViewById(int id) {
                    return LOActivity.this.findViewById(id);
                }

                @Override
                public void runOnUiThread(Runnable runnable) {
                    LOActivity.this.runOnUiThread(runnable);
                }

                @Override
                public void onTopToolbarBack() {
                    if (mIsEditModeActive) {
                        switchToViewingMode();
                    } else {
                        finishWithProgress();
                    }
                }

                @Override
                public void switchToViewingMode() {
                    LOActivity.this.switchToViewingMode();
                }

                @Override
                public void executeUnoCommand(String command) {
                    LOActivity.this.executeUnoCommand(command);
                }

                @Override
                public void toastTodo(String text) {
                    LOActivity.this.toastTodo(text);
                }

                @Override
                public String getDocumentTitle() {
                    return LOActivity.this.getDocumentDisplayTitle();
                }
            });
        }
        return topToolbarController;
    }

    private String getDocumentDisplayTitle() {
        try {
            String filename = getFileName(false);
            if (filename != null && !filename.trim().isEmpty()) {
                return filename.trim();
            }
        } catch (Exception e) {
            Log.w(TAG, "document_title_unavailable", e);
        }
        return "文档";
    }

    private void setupBottomToolbar() {
        ensureBottomToolbarController().setup();
    }

    private BottomToolbarController ensureBottomToolbarController() {
        if (bottomToolbarController == null) {
            bottomToolbarController = new BottomToolbarController(new BottomToolbarController.Host() {
                @Override
                public Context getContext() {
                    return LOActivity.this;
                }

                @Override
                public View findViewById(int id) {
                    return LOActivity.this.findViewById(id);
                }

                @Override
                public int dpToPx(int dp) {
                    return LOActivity.this.dpToPx(dp);
                }

                @Override
                public void runOnUiThread(Runnable runnable) {
                    LOActivity.this.runOnUiThread(runnable);
                }

                @Override
                public void showFunctionPanel() {
                    LOActivity.this.showFunctionPanel();
                }

                @Override
                public void switchToViewingMode() {
                    LOActivity.this.switchToViewingMode();
                }

                @Override
                public void showNativeAiPanel() {
                    LOActivity.this.showNativeAiPanel();
                }

                @Override
                public void toastTodo(String text) {
                    LOActivity.this.toastTodo(text);
                }

                @Override
                public void focusDocumentAndShowIme() {
                    LOActivity.this.focusDocumentAndShowIme();
                }

                @Override
                public void openLocalImagePickerFromWeb() {
                    LOActivity.this.openLocalImagePickerFromWeb();
                }

                @Override
                public void executeUnoCommand(String command) {
                    LOActivity.this.executeUnoCommand(command);
                }
            });
        }
        return bottomToolbarController;
    }

    private void updateEditModeState(boolean isEditMode, String reason) {
        mIsEditModeActive = isEditMode;
        if (mWebView != null) {
            mWebView.setConsumeWebViewLongClick(!isEditMode);
        }
        ensureBottomToolbarController().updateEditModeState(isEditMode, reason);
        ensureTopToolbarController().updateEditModeState(isEditMode, reason);
    }

    private void hideQuickActionPanel() {
        ensureBottomToolbarController().hideQuickActionPanel();
    }

    private void applyBottomToolbarImeState(boolean imeVisible, int imeInsetBottom) {
        isImeVisibleForToolbar = imeVisible;
        bottomToolbarImeInsetPx = Math.max(0, imeInsetBottom);
        ensureBottomToolbarController().applyImeState(imeVisible, imeInsetBottom);
    }

    private void showFunctionPanel() {
        if (functionPanelDialog != null && functionPanelDialog.isShowing()) {
            return;
        }
        View panel = LayoutInflater.from(this).inflate(R.layout.lolib_sheet_functions, null, false);
        TextView tabFile = panel.findViewById(R.id.function_tab_file);
        TextView tabReview = panel.findViewById(R.id.function_tab_review);
        View fileList = panel.findViewById(R.id.function_file_list);
        View reviewList = panel.findViewById(R.id.function_review_list);
        ImageButton closeButton = panel.findViewById(R.id.function_sheet_close);
        View saveAction = panel.findViewById(R.id.function_action_save);
        View downloadAction = panel.findViewById(R.id.function_action_download);
        View printAction = panel.findViewById(R.id.function_action_print);
        View countAction = panel.findViewById(R.id.function_action_word_count);
        View findAction = panel.findViewById(R.id.function_action_find_replace);

        View.OnClickListener showFileTab = v -> {
            tabFile.setBackgroundColor(Color.parseColor("#F4F5F7"));
            tabReview.setBackgroundColor(Color.parseColor("#E4E4E6"));
            tabFile.setTextColor(Color.parseColor("#202124"));
            tabReview.setTextColor(Color.parseColor("#80868B"));
            fileList.setVisibility(View.VISIBLE);
            reviewList.setVisibility(View.GONE);
        };
        View.OnClickListener showReviewTab = v -> {
            tabReview.setBackgroundColor(Color.parseColor("#F4F5F7"));
            tabFile.setBackgroundColor(Color.parseColor("#E4E4E6"));
            tabReview.setTextColor(Color.parseColor("#202124"));
            tabFile.setTextColor(Color.parseColor("#80868B"));
            reviewList.setVisibility(View.VISIBLE);
            fileList.setVisibility(View.GONE);
        };
        tabFile.setOnClickListener(showFileTab);
        tabReview.setOnClickListener(showReviewTab);
        showFileTab.onClick(tabFile);

        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                if (functionPanelDialog != null) {
                    functionPanelDialog.dismiss();
                }
            });
        }
        if (saveAction != null) saveAction.setOnClickListener(v -> runFunctionAction(() ->
                postMobileMessageNative("save dontTerminateEdit=1 dontSaveIfUnmodified=1")));
        if (downloadAction != null) downloadAction.setOnClickListener(v -> runFunctionAction(this::downloadCurrentTextDocumentAsPdf));
        if (printAction != null) printAction.setOnClickListener(v -> runFunctionAction(this::initiatePrint));
        if (countAction != null) countAction.setOnClickListener(v -> runFunctionAction(() ->
                executeUnoCommand(".uno:WordCountDialog")));
        if (findAction != null) findAction.setOnClickListener(v -> runFunctionAction(() ->
                executeUnoCommand(".uno:SearchDialog?InitialFocusReplace:bool=true")));

        functionPanelDialog = new BottomSheetDialog(this);
        functionPanelDialog.setContentView(panel);
        functionPanelDialog.setOnDismissListener(dialog -> functionPanelDialog = null);
        functionPanelDialog.show();
        expandFunctionPanelSheet();
    }

    private void expandFunctionPanelSheet() {
        if (functionPanelDialog == null) {
            return;
        }
        FrameLayout bottomSheet = functionPanelDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setHideable(false);
        behavior.setDraggable(false);
        bottomSheet.post(() -> {
            behavior.setPeekHeight(bottomSheet.getHeight(), false);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            Log.i(TAG, "function_sheet_force_expanded height=" + bottomSheet.getHeight());
        });
    }

    private void runFunctionAction(Runnable action) {
        if (functionPanelDialog != null) {
            functionPanelDialog.dismiss();
        }
        action.run();
    }

    private void openLocalImagePickerFromWeb() {
        if (mWebView == null) {
            return;
        }
        mWebView.requestFocus();
        final String script = "(function(){try{"
                + "if(window.app&&app.dispatcher&&typeof app.dispatcher.dispatch==='function'){app.dispatcher.dispatch('localgraphic');return 'dispatcher';}"
                + "var el=window.L&&window.L.DomUtil&&window.L.DomUtil.get('insertgraphic');"
                + "if(el&&typeof el.click==='function'){el.click();return 'input';}"
                + "}catch(e){if(window.console&&console.warn){console.warn('android_insert_image_open_failed',e);}}"
                + "return 'none';})();";
        mWebView.evaluateJavascript(script, value -> {
            String result = value == null ? "" : value.replace("\"", "");
            Log.i(TAG, "open_local_image_picker result=" + result);
            if ("none".equals(result)) {
                Log.w(TAG, "open_local_image_picker fallback to uno insert graphic");
                executeUnoCommand(".uno:InsertGraphic");
            }
        });
        nudgeSocketIfStalled("insert_image_click");
    }

    private void executeUnoCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        String normalizedCommand = command.trim();
        Log.i(TAG, "dispatch_uno_from_native_panel command=" + normalizedCommand);

        // For insert-graphic, ensure the WebView is focused so the JS layer
        // can process the subsequent file-picker interaction.
        if (normalizedCommand.contains("InsertGraphic") && mWebView != null) {
            mWebView.requestFocus();
            Log.i(TAG, "dispatch_uno_focus_webview_for command=" + normalizedCommand);
        }

        postMobileMessage("uno " + normalizedCommand);
        nudgeSocketIfStalled("uno_dispatch");
    }

    private void downloadCurrentTextDocumentAsPdf() {
        String filename = getFileName(true);
        String baseName = filename;
        if (baseName != null) {
            int dotIndex = baseName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = baseName.substring(0, dotIndex);
            }
        }
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "document";
        }
        baseName = baseName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        initiateSaveAs("format=pdf name=" + baseName + ".pdf");
    }

    private void switchToViewingMode() {
        ensureSelectionMenuController().hide();
        pendingAfterEditMode = null;
        updateEditModeState(false, "manual_preview_switch");
        awaitingPreviewModeJsAck = true;
        mobilePreviewSwitchAttempt = 1;
        getMainHandler().removeCallbacks(mobilePreviewAckTimeoutRunnable);
        callFakeWebsocketOnMessage("mobile: readonlymode");
        getMainHandler().postDelayed(mobilePreviewAckTimeoutRunnable, MOBILE_PREVIEW_ACK_TIMEOUT_MS);
    }

    private void completePreviewModeSwitchAck(String reason) {
        if (!awaitingPreviewModeJsAck) {
            return;
        }
        awaitingPreviewModeJsAck = false;
        mobilePreviewSwitchAttempt = 0;
        getMainHandler().removeCallbacks(mobilePreviewAckTimeoutRunnable);
        Log.i(TAG, "mobile_preview_switch_ack success reason=" + reason);
    }

    private void cancelPreviewModeSwitchAck(String reason) {
        if (!awaitingPreviewModeJsAck) {
            return;
        }
        awaitingPreviewModeJsAck = false;
        mobilePreviewSwitchAttempt = 0;
        getMainHandler().removeCallbacks(mobilePreviewAckTimeoutRunnable);
        Log.i(TAG, "mobile_preview_switch_ack cancelled reason=" + reason);
    }

    /** Ask the Web layer to enter mobile read-only UI without resetting the document socket. */
    private void nudgePreviewModeOnWebLayer() {
        if (mWebView == null) {
            return;
        }
        mWebView.evaluateJavascript(
                "(function(){try{"
                        + "if(window.app&&app.map){"
                        + "if(typeof app.map.isEditMode==='function'&&app.map.isEditMode()){"
                        + "if(typeof app.map.setPermission==='function'){app.map.setPermission('edit');}"
                        + "}else if(typeof app.map.fire==='function'){app.map.fire('readonlymode');}"
                        + "}"
                        + "}catch(e){if(window.console&&console.warn){console.warn('android_preview_resync_failed',e);}}"
                        + "return true;})();",
                null);
    }

    private void focusDocumentAndShowIme() {
        if (mWebView == null) {
            return;
        }
        mWebView.requestFocus();
        mWebView.evaluateJavascript(
                "(function(){if(window.app&&app.map){app.map.focus();}return true;})();",
                null);
        // Explicitly show the soft keyboard.
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mWebView, InputMethodManager.SHOW_IMPLICIT);
        }
        nudgeSocketIfStalled("show_ime_focus_doc");
    }

    /** Hide the soft keyboard and reset IME state on the WebView. */
    private void hideKeyboard() {
        if (mWebView == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mWebView.getWindowToken(), 0);
        }
    }

    private boolean canAiMessagesScrollConsume(float deltaY) {
        if (aiMessagesScroll == null) {
            return false;
        }
        if (deltaY < -0.5f) {
            return aiMessagesScroll.canScrollVertically(1);
        }
        if (deltaY > 0.5f) {
            return aiMessagesScroll.canScrollVertically(-1);
        }
        return aiMessagesScroll.canScrollVertically(1) || aiMessagesScroll.canScrollVertically(-1);
    }

    private void triggerSelectionStateSync(String reason) {
        triggerSelectionStateSync(reason, true);
    }

    private void triggerSelectionStateSync(String reason, boolean resetSelection) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastSelectionSyncAt < SELECTION_SYNC_THROTTLE_MS) {
            return;
        }
        lastSelectionSyncAt = now;
        Log.i(TAG, "selection_sync reason=" + reason + " resetSelection=" + resetSelection);

        if (resetSelection) {
            postMobileMessage("resetselection");
        }

        if (mWebView == null) {
            return;
        }

        final String script = "(function(){"
                + "var reason=" + JSONObject.quote(reason) + ";"
                + "var resetSelection=" + resetSelection + ";"
                + "function sync(tag){try{"
                + "if(resetSelection){"
                + "if(window.app&&app.activeDocument&&app.activeDocument.activeView&&typeof app.activeDocument.activeView.clearTextSelection==='function'){app.activeDocument.activeView.clearTextSelection();}"
                + "if(window.TextSelections&&typeof window.TextSelections.deactivate==='function'){window.TextSelections.deactivate();}"
                + "if(window.getSelection){var s=window.getSelection();if(s&&typeof s.removeAllRanges==='function'){s.removeAllRanges();}}"
                + "}"
                + "if(window.app&&app.map&&app.map._docLayer&&typeof app.map._docLayer._onUpdateCursor==='function'){app.map._docLayer._onUpdateCursor();}"
                + "if(window.app&&app.events&&typeof app.events.fire==='function'&&window.app.map){var perm=(typeof app.map.isEditMode==='function'&&app.map.isEditMode())?'edit':'readonly';app.events.fire('updatepermission',{perm:perm});}"
                + "if(window.ThisIsTheAndroidApp&&typeof window.postMobileMessage==='function'){"
                + "var reconnectingNow=!!(window.app&&app.socket&&typeof app.socket.isTemporarilyReconnecting==='function'&&app.socket.isTemporarilyReconnecting());"
                + "if(!reconnectingNow){"
                + "window.__androidEditModeSync=window.__androidEditModeSync||{last:null,ts:0};"
                + "if(window.__androidEditModeSync.last!==perm||Date.now()-window.__androidEditModeSync.ts>1200){"
                + "window.postMobileMessage('EDITMODE '+(perm==='edit'?'on':'off'));"
                + "window.__androidEditModeSync.last=perm;"
                + "window.__androidEditModeSync.ts=Date.now();"
                + "}"
                + "}"
                + "}"
                + "if(window.app&&app.console&&typeof app.console.debug==='function'){app.console.debug('selection_sync applied reason='+tag);}"
                + "}catch(e){if(window.console&&typeof window.console.warn==='function'){console.warn('selection_sync_failed',tag,e);}}}"
                + "sync(reason);"
                + "var reconnecting=!!(window.app&&app.socket&&typeof app.socket.isTemporarilyReconnecting==='function'&&app.socket.isTemporarilyReconnecting());"
                + "if(reconnecting){setTimeout(function(){sync(reason+'_postreconnect');},700);}"
                + "return true;})();";
        runOnUiThread(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script, null);
            }
        });
    }

    private void nudgeSocketIfStalled(String reason) {
        if (mWebView == null) {
            return;
        }
        final String script = "(function(){try{"
                + "if(!window.socket){return 'no_socket';}"
                + "var now=performance.now?performance.now():Date.now();"
                + "var last=window.socket.lastDataTimestamp||0;"
                + "var inflight=window.socket.msgInflight||0;"
                + "if(inflight>=1&&now-last>12000&&typeof window.socket._signalErrorClose==='function'){window.socket._signalErrorClose();}"
                + "if(typeof window.socket.doSend==='function'){window.socket.doSend();return 'nudged';}"
                + "return 'no_dosend';"
                + "}catch(e){if(window.console&&console.warn){console.warn('android_socket_nudge_failed',e);}return 'err';}})();";
        mWebView.evaluateJavascript(script, value -> Log.i(TAG, "socket_nudge reason=" + reason + " result=" + value));
    }

    private void toastTodo(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void showNativeAiPanel() {
        if (aiPanelDialog != null && aiPanelDialog.isShowing()) {
            return;
        }

        View panel = LayoutInflater.from(this).inflate(R.layout.lolib_dialog_ai_panel, null, false);
        aiPromptInput = panel.findViewById(R.id.ai_prompt);
        aiStatusText = panel.findViewById(R.id.ai_status);
        aiOutputText = panel.findViewById(R.id.ai_output);
        aiRunButton = panel.findViewById(R.id.ai_run);
        aiCancelButton = panel.findViewById(R.id.ai_cancel);
        aiAcceptButton = panel.findViewById(R.id.ai_accept);
        aiCloseButton = panel.findViewById(R.id.ai_close);
        aiTabDocQa = panel.findViewById(R.id.ai_tab_doc_qa);
        aiTabChat = panel.findViewById(R.id.ai_tab_chat);
        aiMessagesContainer = panel.findViewById(R.id.ai_messages_container);
        aiMessagesScroll = panel.findViewById(R.id.ai_messages_scroll);
        ImageView headerLogo = panel.findViewById(R.id.ai_header_logo);
        if (headerLogo != null) {
            int logoId = getResources().getIdentifier("lo_icon", "drawable", getPackageName());
            if (logoId != 0) {
                headerLogo.setImageResource(logoId);
            }
        }

        String initialPrompt = pendingAutoOpenAiPrompt == null || pendingAutoOpenAiPrompt.isEmpty()
                ? ""
                : pendingAutoOpenAiPrompt;
        aiPromptInput.setText(initialPrompt);
        aiPromptInput.setHint("发消息...");
        aiStatusText.setText("Ready");
        aiOutputText.setText("");
        aiDocQaMode = true;
        loadAiHistoriesForCurrentDocument();
        aiStreamingRequestId = "";
        aiStreamingMessageView = null;
        renderAiHistoryForCurrentMode();
        setNativeAiPanelState(AI_STATE_READY, "Ready");
        updateAiPanelTabStyle();

        aiRunButton.setOnClickListener(v -> runAiFromNativePanel());
        aiCancelButton.setOnClickListener(v -> cancelAiFromNativePanel());
        aiAcceptButton.setOnClickListener(v -> acceptAiFromNativePanel());
        if (aiTabDocQa != null) {
            aiTabDocQa.setOnClickListener(v -> {
                aiDocQaMode = true;
                updateAiPanelTabStyle();
                renderAiHistoryForCurrentMode();
            });
        }
        if (aiTabChat != null) {
            aiTabChat.setOnClickListener(v -> {
                aiDocQaMode = false;
                updateAiPanelTabStyle();
                renderAiHistoryForCurrentMode();
            });
        }
        aiCloseButton.setOnClickListener(v -> {
            if (aiPanelDialog != null) {
                aiPanelDialog.dismiss();
            }
        });

        aiPanelDialog = new BottomSheetDialog(this);
        aiPanelDialog.setContentView(panel);
        aiPanelDialog.setOnDismissListener(dialog -> {
            cancelAiFromNativePanel();
            triggerSelectionStateSync("ai_dialog_dismiss");
            aiPanelDialog = null;
            aiPromptInput = null;
            aiStatusText = null;
            aiOutputText = null;
            aiRunButton = null;
            aiCancelButton = null;
            aiAcceptButton = null;
            aiCloseButton = null;
            aiTabDocQa = null;
            aiTabChat = null;
            aiMessagesContainer = null;
            aiMessagesScroll = null;
            aiStreamingMessageView = null;
            aiStreamingRequestId = "";
            aiStreamingViewByRequestId.clear();
            aiPanelController.resetTransientState();
        });
        aiPanelDialog.show();

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int orientation = getResources().getConfiguration().orientation;
        aiPanelController.configureBottomSheet(aiPanelDialog, screenHeight, screenWidth, orientation);

        aiPanelController.installMessageScrollTouchPolicy(aiMessagesScroll, new AiPanelController.ScrollCallbacks() {
            @Override
            public boolean canMessagesScrollConsume(float deltaY) {
                return canAiMessagesScrollConsume(deltaY);
            }

            @Override
            public void onTouchCancelled() {
                if (mWebView != null) {
                    mWebView.abortDocumentScroll();
                }
                triggerSelectionStateSync("action_cancel");
            }
        });
    }

    private void maybeAutoOpenAiPanelAfterLoad() {
        if (!pendingAutoOpenAiPanel || pendingAutoGenerateAiContent) {
            return;
        }
        pendingAutoOpenAiPanel = false;
        runOnUiThread(this::showNativeAiPanel);
    }

    private void maybeAutoGenerateAiContentAfterLoad() {
        if (!pendingAutoGenerateAiContent) {
            return;
        }
        pendingAutoGenerateAiContent = false;
        runOnUiThread(this::startAutoGenerateForNewDocument);
    }

    private void startAutoGenerateForNewDocument() {
        String prompt = pendingAutoOpenAiPrompt == null || pendingAutoOpenAiPrompt.isEmpty()
                ? "Generate an outline first, then expand it into complete document content."
                : pendingAutoOpenAiPrompt;
        startNativeAiRequest("", prompt, true, "rewrite", false);
    }

    private void runAiFromNativePanel() {
        if (aiPromptInput == null) {
            Log.w(TAG, "ai_native_send_ignored reason=missing_prompt_input");
            return;
        }
        String prompt = aiPromptInput.getText() == null ? "" : aiPromptInput.getText().toString().trim();
        if (prompt.isEmpty()) {
            Log.i(TAG, "ai_native_send_ignored reason=empty_prompt");
            return;
        }
        String mode = getActiveAiMode();
        boolean firstDocQaTurn = ensureAiChatCoordinator().isFirstDocQaTurn(mode);
        Log.i(TAG, "ai_native_send_start mode=" + mode
                + " firstDocQaTurn=" + firstDocQaTurn
                + " promptChars=" + prompt.length());
        String configError = validateBaseModelConfigured();
        if (!configError.isEmpty()) {
            Log.w(TAG, "ai_native_send_blocked reason=config_missing mode=" + mode);
            showBaseModelConfigRequiredDialog(configError);
            setNativeAiPanelState(AI_STATE_UNCONFIGURED, configError);
            return;
        }
        appendAiMessage(prompt, true, false);
        appendAiHistoryMessage(mode, "user", prompt);
        aiPromptInput.setText("");
        setNativeAiPanelState(AI_STATE_LOADING, "AI request queued");
        String taskType = aiDocQaMode ? AI_MODE_DOC_QA : AI_MODE_CHAT;
        startNativeAiRequest("", prompt, false, taskType, firstDocQaTurn);
    }

    private void startNativeAiRequest(String selection, @Nullable String promptOverride, boolean autoAcceptWhenDone,
            String taskType, boolean firstDocQaTurn) {
        try {
            JSONObject context = new JSONObject();
            String promptValue;
            if (promptOverride != null) {
                promptValue = promptOverride;
            } else if (aiPromptInput != null) {
                promptValue = aiPromptInput.getText().toString();
            } else {
                promptValue = "";
            }
            context.put("prompt", promptValue);
            context.put("question", promptValue);
            context.put("source", "android-native-panel");
            context.put("selection", selection == null ? "" : selection);

            JSONObject request = new JSONObject();
            String requestId = "req-" + UUID.randomUUID();
            request.put("requestId", requestId);
            request.put("taskType", taskType == null || taskType.trim().isEmpty() ? "chat" : taskType);
            request.put("selection", selection == null ? "" : selection);
            request.put("context", context);
            request.put("modelMode", "base");
            request.put("docQaFirstTurn", firstDocQaTurn);
            request.put("history", ensureAiChatCoordinator().cloneHistory(request.optString("taskType", AI_MODE_CHAT)));

            aiActiveRequestId = requestId;
            aiStreamingRequestId = requestId;
            aiRequestModeById.put(requestId, request.optString("taskType", AI_MODE_CHAT));
            aiDocQaFirstTurnByRequestId.put(requestId, firstDocQaTurn);
            if (autoAcceptWhenDone) {
                autoGenerateAcceptRequestId = requestId;
            }
            aiTextByRequestId.put(requestId, new StringBuilder());
            if (aiOutputText != null) {
                aiOutputText.setText("");
            }
            Log.i(TAG, "ai_native_request_prepare requestId=" + requestId
                    + " taskType=" + request.optString("taskType", AI_MODE_CHAT)
                    + " firstDocQaTurn=" + firstDocQaTurn
                    + " promptChars=" + promptValue.length()
                    + " historyItems=" + request.optJSONArray("history").length());
            TextView streamView = appendAiMessage(AI_STREAMING_PLACEHOLDER, false, true);
            aiStreamingMessageView = streamView;
            if (streamView != null) {
                aiStreamingViewByRequestId.put(requestId, streamView);
            }

            Log.i(TAG, "ai_native_request_dispatch requestId=" + requestId);
            startAiRequestSession(request, -1);
            Log.i(TAG, "ai_native_request_dispatched requestId=" + requestId);
        } catch (JSONException e) {
            dispatchAiError("", "invalid_payload", "Failed to build native ai.request payload");
            Log.e(TAG, "Failed to build native ai.request payload", e);
        }
    }

    private String validateBaseModelConfigured() {
        SharedPreferences modelPrefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
        String endpoint = modelPrefs.getString("AI_MODEL_BASE_url", "");
        String apiKey = modelPrefs.getString("AI_MODEL_BASE_api_key", "");
        endpoint = endpoint == null ? "" : endpoint.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        if (endpoint.isEmpty()) {
            return "请先在设置中配置基础模型的接口地址。";
        }
        if (apiKey.isEmpty()) {
            return "请先在设置中配置基础模型的 API Key。";
        }
        return "";
    }

    private void showBaseModelConfigRequiredDialog(String message) {
        if (isFinishing()) {
            return;
        }
        new AlertDialog.Builder(LOActivity.this)
                .setTitle("需要配置基础模型")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void updateAiPanelTabStyle() {
        if (aiTabDocQa == null || aiTabChat == null) {
            return;
        }
        if (aiDocQaMode) {
            aiTabDocQa.setBackgroundColor(Color.parseColor("#F4F5F7"));
            aiTabChat.setBackgroundColor(Color.parseColor("#E2E3E5"));
            aiTabDocQa.setTextColor(Color.parseColor("#202124"));
            aiTabChat.setTextColor(Color.parseColor("#80868B"));
        } else {
            aiTabChat.setBackgroundColor(Color.parseColor("#F4F5F7"));
            aiTabDocQa.setBackgroundColor(Color.parseColor("#E2E3E5"));
            aiTabChat.setTextColor(Color.parseColor("#202124"));
            aiTabDocQa.setTextColor(Color.parseColor("#80868B"));
        }
    }

    private TextView appendAiMessage(String text, boolean userMessage, boolean streaming) {
        if (aiMessagesContainer == null) {
            return null;
        }
        TextView bubble = new TextView(this);
        bubble.setTextSize(16f);
        bubble.setTextColor(Color.parseColor("#202124"));
        bubble.setPadding(22, 16, 22, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = 10;
        params.gravity = userMessage ? Gravity.END : Gravity.START;
        bubble.setLayoutParams(params);
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.75f));
        bubble.setBackgroundColor(userMessage ? Color.parseColor("#E7ECF3") : Color.parseColor("#FFFFFF"));
        renderAiMessageContent(normalizeAiText(text), bubble, userMessage, streaming);
        aiMessagesContainer.addView(bubble);
        if (aiMessagesScroll != null) {
            aiMessagesScroll.post(() -> aiMessagesScroll.fullScroll(View.FOCUS_DOWN));
        }
        return bubble;
    }

    private void renderAiMessageContent(String rawText, TextView target, boolean userMessage, boolean streaming) {
        AiMarkdownRenderer.render(rawText, target, userMessage || streaming);
    }

    private void cancelAiFromNativePanel() {
        if (aiActiveRequestId == null || aiActiveRequestId.isEmpty()) {
            return;
        }
        try {
            JSONObject request = new JSONObject();
            request.put("requestId", aiActiveRequestId);
            handleAiCancelFromWeb(request.toString());
        } catch (JSONException ignored) {
        }
    }

    private void acceptAiFromNativePanel() {
        if (aiActiveRequestId == null || aiActiveRequestId.isEmpty()) {
            return;
        }

        StringBuilder textBuilder = aiTextByRequestId.get(aiActiveRequestId);
        String text = textBuilder == null ? "" : textBuilder.toString();
        if (text.isEmpty() && aiOutputText != null) {
            text = aiOutputText.getText().toString();
        }
        if (text.isEmpty()) {
            return;
        }

        try {
            JSONObject request = new JSONObject();
            request.put("requestId", aiActiveRequestId);
            request.put("text", text);
            handleAiAcceptFromWeb(request.toString());
            setNativeAiPanelState(AI_STATE_READY, "Inserted into document");
        } catch (JSONException ignored) {
        }
    }

    private String parseJsString(String jsResult) {
        if (jsResult == null || jsResult.equals("null")) {
            return "";
        }
        try {
            Object value = new JSONTokener(jsResult).nextValue();
            if (value instanceof String) {
                return (String) value;
            }
        } catch (Exception ignored) {
        }
        return jsResult;
    }

    private String getActiveAiMode() {
        return aiDocQaMode ? AI_MODE_DOC_QA : AI_MODE_CHAT;
    }

    private AiChatCoordinator ensureAiChatCoordinator() {
        if (aiChatCoordinator == null) {
            aiChatCoordinator = new AiChatCoordinator(this, documentUri, urlToLoad, loadDocumentMillis);
            aiChatCoordinator.load();
        }
        return aiChatCoordinator;
    }

    private AiDocumentContextProvider ensureAiDocumentContextProvider() {
        if (aiDocumentContextProvider == null) {
            aiDocumentContextProvider = new AiDocumentContextProvider(new AiDocumentContextProvider.Bridge() {
                @Override
                public void postUnoCommand(String command, String args, boolean notify) {
                    LOActivity.this.postUnoCommand(command, args, notify);
                }

                @Override
                public void runOnUiThread(Runnable runnable) {
                    LOActivity.this.runOnUiThread(runnable);
                }

                @Override
                public void copyViaWebsocketFallback() {
                    LOActivity.this.callFakeWebsocketOnMessage("uno .uno:Copy");
                }

                @Override
                public boolean getClipboardContent(LokClipboardData clipboardData) {
                    return LOActivity.this.getClipboardContent(clipboardData);
                }

                @Override
                public String normalizeAiText(String text) {
                    return LOActivity.this.normalizeAiText(text);
                }
            });
        }
        return aiDocumentContextProvider;
    }

    private JSONArray getAiHistoryForMode(String mode) {
        return ensureAiChatCoordinator().getHistory(mode);
    }

    private void loadAiHistoriesForCurrentDocument() {
        ensureAiChatCoordinator().load();
    }

    private void renderAiHistoryForCurrentMode() {
        if (aiMessagesContainer == null) {
            return;
        }
        aiMessagesContainer.removeAllViews();
        JSONArray history = getAiHistoryForMode(getActiveAiMode());
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String role = item.optString("role", "");
            String content = item.optString("content", "");
            if (content.trim().isEmpty()) {
                continue;
            }
            appendAiMessage(content, "user".equals(role), false);
        }
    }

    private void appendAiHistoryMessage(String mode, String role, String content) {
        try {
            ensureAiChatCoordinator().appendHistoryMessage(mode, role, content);
        } catch (JSONException e) {
            Log.e(TAG, "appendAiHistoryMessage failed", e);
        }
    }

    private void clearAiHistoryFilesForCurrentDocument() {
        ensureAiChatCoordinator().clearHistoriesForCurrentDocument();
    }

    private void resetAiSessionState(boolean clearHistoryFiles) {
        cancelAllAiRequests();
        aiTextByRequestId.clear();
        aiRequestModeById.clear();
        aiDocQaFirstTurnByRequestId.clear();
        aiStreamingViewByRequestId.clear();
        aiActiveRequestId = "";
        aiStreamingRequestId = "";
        aiStreamingMessageView = null;
        autoGenerateAcceptRequestId = "";
        if (clearHistoryFiles) {
            clearAiHistoryFilesForCurrentDocument();
        }
        if (aiChatCoordinator != null) {
            aiChatCoordinator.reset(false);
        }
    }

    private JSONArray buildAiMessagesFromHistory(JSONArray history) {
        JSONArray messages = new JSONArray();
        if (history == null) {
            return messages;
        }
        for (int i = 0; i < history.length(); i++) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String role = item.optString("role", "");
            if (!"assistant".equals(role) && !"user".equals(role) && !"system".equals(role)) {
                continue;
            }
            String content = normalizeAiText(item.optString("content", ""));
            if (content.isEmpty()) {
                continue;
            }
            try {
                messages.put(new JSONObject().put("role", role).put("content", content));
            } catch (JSONException ignored) {
            }
        }
        return messages;
    }

    private String extractLatestUserQuestion(JSONArray history, JSONObject context, JSONObject request) {
        if (history != null) {
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                if ("user".equals(item.optString("role", ""))) {
                    String content = normalizeAiText(item.optString("content", ""));
                    if (!content.isEmpty()) {
                        return content;
                    }
                }
            }
        }
        String fromContext = context == null ? "" : normalizeAiText(context.optString("question", ""));
        if (!fromContext.isEmpty()) {
            return fromContext;
        }
        fromContext = context == null ? "" : normalizeAiText(context.optString("prompt", ""));
        if (!fromContext.isEmpty()) {
            return fromContext;
        }
        return normalizeAiText(request.optString("prompt", ""));
    }

    private String extractDocumentTextForDocQaFirstTurn(String requestId) {
        return ensureAiDocumentContextProvider().extractFullTextForDocQaFirstTurn(requestId);
    }

    private String normalizeAiText(String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
    }

    private String sanitizeAiTextPayload(String requestId, Object raw, String stage) {
        if (raw == null || raw == JSONObject.NULL) {
            Log.i(TAG, "ai_delta_null_filtered requestId=" + requestId + " stage=" + stage);
            return "";
        }
        String text = raw instanceof String ? (String) raw : String.valueOf(raw);
        if (text == null) {
            Log.i(TAG, "ai_delta_null_filtered requestId=" + requestId + " stage=" + stage);
            return "";
        }
        String trimmed = text.trim();
        if ("null".equalsIgnoreCase(trimmed)) {
            Log.i(TAG, "ai_delta_null_filtered requestId=" + requestId + " stage=" + stage);
            return "";
        }
        return "stream_delta".equals(stage) || "native_stream_event".equals(stage) ? text : trimmed;
    }

    private boolean isStaleAiUiEvent(String requestId, String type) {
        boolean stale = requestId == null || requestId.isEmpty() || !requestId.equals(aiActiveRequestId);
        if (stale) {
            // allow post-done late state events without noisy stale logs
            if (!"ai.state".equals(type) || (aiActiveRequestId != null && !aiActiveRequestId.isEmpty())) {
                Log.i(TAG, "ai_stream_drop_stale_request requestId=" + requestId + " active=" + aiActiveRequestId
                        + " type=" + type);
            }
        }
        return stale;
    }

    private void cleanupRequestUiState(String requestId) {
        aiStreamingViewByRequestId.remove(requestId);
        aiDocQaFirstTurnByRequestId.remove(requestId);
        if (requestId.equals(aiStreamingRequestId)) {
            aiStreamingRequestId = "";
            aiStreamingMessageView = null;
        }
        if (requestId.equals(aiActiveRequestId)) {
            aiActiveRequestId = "";
        }
    }

    private void handleAiNativeEvent(JSONObject event) {
        String type = event.optString("type", "");
        String requestId = event.optString("requestId", "");
        if ("ai.stream".equals(type)) {
            if (isStaleAiUiEvent(requestId, type)) {
                return;
            }
            final String delta = sanitizeAiTextPayload(requestId, event.opt("delta"), "native_stream_event");
            if (delta.isEmpty()) {
                return;
            }
            StringBuilder currentText = aiTextByRequestId.computeIfAbsent(requestId, ignored -> new StringBuilder());
            currentText.append(delta);
            final String accumulatedText = currentText.toString();
            if (currentText.length() == delta.length()) {
                Log.i(TAG, "ai_stream_render_mode=plaintext requestId=" + requestId);
            }
            final TextView outputSnapshot = aiOutputText;
            final TextView streamViewSnapshot = aiStreamingViewByRequestId.get(requestId);
            runOnUiThread(() -> {
                if (!requestId.equals(aiActiveRequestId)) {
                    return;
                }
                if (outputSnapshot != null && outputSnapshot.isAttachedToWindow()) {
                    outputSnapshot.append(delta);
                }
                if (streamViewSnapshot != null && streamViewSnapshot.isAttachedToWindow()) {
                    renderAiMessageContent(accumulatedText, streamViewSnapshot, false, true);
                }
            });
            setNativeAiPanelState(AI_STATE_STREAMING, "AI response streaming");
            return;
        }

        if ("ai.done".equals(type)) {
            if (isStaleAiUiEvent(requestId, type)) {
                return;
            }
            final String fullText = sanitizeAiTextPayload(requestId, event.opt("fullText"), "native_done_event");
            aiTextByRequestId.put(requestId, new StringBuilder(fullText));
            String mode = aiRequestModeById.remove(requestId);
            boolean completedFirstDocQaTurn = Boolean.TRUE.equals(aiDocQaFirstTurnByRequestId.get(requestId));
            if (mode == null || mode.isEmpty()) {
                mode = getActiveAiMode();
            }
            if (!fullText.isEmpty() && (AI_MODE_DOC_QA.equals(mode) || AI_MODE_CHAT.equals(mode))) {
                appendAiHistoryMessage(mode, "assistant", fullText);
            }
            if (completedFirstDocQaTurn && AI_MODE_DOC_QA.equals(mode) && !fullText.isEmpty()) {
                ensureAiChatCoordinator().markDocQaContextInjected();
                Log.i(TAG, "doc_qa_first_turn_context_marked_injected requestId=" + requestId);
            }
            if (requestId.equals(autoGenerateAcceptRequestId) && !fullText.isEmpty()) {
                try {
                    JSONObject autoAcceptPayload = new JSONObject();
                    autoAcceptPayload.put("requestId", requestId);
                    autoAcceptPayload.put("text", fullText);
                    handleAiAcceptFromWeb(autoAcceptPayload.toString());
                } catch (JSONException ignored) {
                }
                autoGenerateAcceptRequestId = "";
            }
            final TextView outputSnapshot = aiOutputText;
            final TextView streamViewSnapshot = aiStreamingViewByRequestId.get(requestId);
            runOnUiThread(() -> {
                if (outputSnapshot != null && outputSnapshot.isAttachedToWindow()) {
                    outputSnapshot.setText(fullText);
                }
                if (streamViewSnapshot != null && streamViewSnapshot.isAttachedToWindow()) {
                    renderAiMessageContent(fullText, streamViewSnapshot, false, false);
                    Log.i(TAG, "ai_done_render_markdown requestId=" + requestId + " chars=" + fullText.length());
                }
                cleanupRequestUiState(requestId);
                setNativeAiPanelState(AI_STATE_READY, "AI response completed");
            });
            return;
        }

        if ("ai.error".equals(type)) {
            final String errorCode = event.optString("code", "");
            final String errorMessage = event.optString("message", "AI request failed");
            if (!requestId.isEmpty() && requestId.equals(autoGenerateAcceptRequestId)) {
                autoGenerateAcceptRequestId = "";
                runOnUiThread(() -> Toast.makeText(
                        LOActivity.this,
                        errorMessage,
                        Toast.LENGTH_SHORT).show());
            }
            if (isStaleAiUiEvent(requestId, type)) {
                return;
            }
            final TextView streamViewSnapshot = aiStreamingViewByRequestId.get(requestId);
            runOnUiThread(() -> {
                if (!requestId.equals(aiActiveRequestId)) {
                    return;
                }
                if (streamViewSnapshot != null && streamViewSnapshot.isAttachedToWindow()) {
                    renderAiMessageContent(errorMessage, streamViewSnapshot, false, false);
                }
                if ("config_missing".equals(errorCode) && !isFinishing()) {
                    new AlertDialog.Builder(LOActivity.this)
                            .setTitle("需要配置基础模型")
                            .setMessage(errorMessage)
                            .setPositiveButton("知道了", null)
                            .show();
                }
                aiRequestModeById.remove(requestId);
                cleanupRequestUiState(requestId);
                setNativeAiPanelState(AI_STATE_UNCONFIGURED, errorMessage);
            });
            return;
        }

        if ("ai.state".equals(type)) {
            if (isStaleAiUiEvent(requestId, type)) {
                return;
            }
            setNativeAiPanelState(event.optString("state", AI_STATE_READY), event.optString("message", ""));
        }
    }

    private void setNativeAiPanelState(String state, String message) {
        runOnUiThread(() -> {
            if (aiStatusText == null || aiRunButton == null || aiCancelButton == null || aiAcceptButton == null) {
                return;
            }

            boolean busy = AI_STATE_LOADING.equals(state) || AI_STATE_STREAMING.equals(state);
            aiRunButton.setEnabled(!busy);
            aiCancelButton.setEnabled(busy);
            aiAcceptButton.setEnabled(!busy);
            aiRunButton.setAlpha(busy ? 0.65f : 1.0f);
            aiCancelButton.setAlpha(busy ? 1.0f : 0.65f);
            aiAcceptButton.setAlpha(busy ? 0.65f : 1.0f);

            String finalMessage = (message == null || message.isEmpty()) ? state : message;
            aiStatusText.setText("State: " + state + " - " + finalMessage);
            if (AI_STATE_ERROR.equals(state) || AI_STATE_UNCONFIGURED.equals(state)) {
                aiStatusText.setTextColor(Color.parseColor("#B3261E"));
            } else if (AI_STATE_STREAMING.equals(state) || AI_STATE_LOADING.equals(state)) {
                aiStatusText.setTextColor(Color.parseColor("#0B57D0"));
            } else {
                aiStatusText.setTextColor(Color.parseColor("#2E7D32"));
            }
        });
    }

    private void injectAiBridgeIfNeeded() {
        if (aiBridgeInjected || mWebView == null) {
            return;
        }
        aiBridgeInjected = true;

        final String script = "(function(){" +
                "if(window.__coolAiBridge){return;}" +
                "var bridge={activeRequestId:null,lastTextByRequestId:{},onNativeEvent:function(evt){" +
                "if(!evt||!evt.type){return;}" +
                "var id=evt.requestId||'';" +
                "if(evt.type==='ai.stream'&&id){this.lastTextByRequestId[id]=(this.lastTextByRequestId[id]||'')+(evt.delta||'');}"
                +
                "if(evt.type==='ai.done'&&id&&typeof evt.fullText==='string'){this.lastTextByRequestId[id]=evt.fullText;}"
                +
                "window.dispatchEvent(new CustomEvent(evt.type,{detail:evt}));" +
                "},request:function(payload){" +
                "payload=payload||{};" +
                "if(!payload.requestId){payload.requestId='req-'+Date.now()+'-'+Math.random().toString(16).slice(2);}" +
                "this.activeRequestId=payload.requestId;" +
                "window.postMobileMessage('ai.request '+JSON.stringify(payload));" +
                "return payload.requestId;" +
                "},cancel:function(requestId){" +
                "if(!requestId){return;}" +
                "window.postMobileMessage('ai.cancel '+JSON.stringify({requestId:requestId}));" +
                "},accept:function(requestId,text){" +
                "if(!requestId||!text){return;}" +
                "window.postMobileMessage('ai.accept '+JSON.stringify({requestId:requestId,text:text}));" +
                "}};" +
                "window.__coolAiBridge=bridge;" +
                "})();";

        runOnUiThread(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script, null);
            }
        });
    }

    public static void createNewFileInputDialog(Activity activity, final String defaultFileName,
            final @Nullable String mimeType, final int requestCode) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);

        // The mime type and category must be set
        i.setType(mimeType);
        i.addCategory(Intent.CATEGORY_OPENABLE);

        i.putExtra(Intent.EXTRA_TITLE, defaultFileName);

        // Try to default to the Documents folder
        Uri documentsUri = Uri.parse("content://com.android.externalstorage.documents/document/home%3A");
        i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentsUri);

        activity.startActivityForResult(i, requestCode);
    }

    private AlertDialog.Builder buildPrompt(final String mTitle, final String mMessage, final String mPositiveBtnText,
            final String mNegativeBtnText, DialogInterface.OnClickListener callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(mTitle);
        if (mMessage.length() > 0)
            builder.setMessage(mMessage);
        builder.setPositiveButton(mPositiveBtnText, callback);
        builder.setNegativeButton(mNegativeBtnText, null);
        builder.setCancelable(false);
        return builder;
    }

    private @Nullable String getMimeType() {
        ContentResolver cR = getContentResolver();

        Uri data = getIntent().getData();
        if (data == null)
            return null;

        return cR.getType(data);
    }

    private String getFileName(boolean withExtension) {
        Cursor cursor = null;
        String filename = null;
        try {
            cursor = getContentResolver().query(getIntent().getData(), null, null, null, null);
            if (cursor != null && cursor.moveToFirst())
                filename = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
        } catch (Exception e) {
            return null;
        }
        if (!withExtension)
            filename = filename.substring(0, filename.lastIndexOf("."));
        return filename;
    }

    private void requestForCopy() {
        final boolean canBeExported = canDocumentBeExported();
        buildPrompt(getString(R.string.ask_for_copy), "",
                canBeExported ? getString(R.string.edit_copy) : getString(R.string.use_odf),
                getString(R.string.view_only), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (canBeExported)
                            createNewFileInputDialog(mActivity, getFileName(true), getMimeType(), REQUEST_COPY);
                        else {
                            String extension = getOdfExtensionForDocType(getMimeType());
                            createNewFileInputDialog(mActivity, getFileName(false) + "." + extension,
                                    getMimeForFormat(extension), REQUEST_COPY);
                        }

                    }
                }).show();
    }

    // readonly formats here
    private boolean canDocumentBeExported() {
        if (Objects.equals(getMimeType(), "application/vnd.ms-excel.sheet.binary.macroenabled.12")) {
            return false;
        }
        return true;
    }

    private String getOdfExtensionForDocType(@Nullable String mimeType) {
        String extTemp = null;
        if (Objects.equals(mimeType, "text/plain")) {
            extTemp = "odt";
        } else if (Objects.equals(mimeType, "text/comma-separated-values")) {
            extTemp = "ods";
        } else if (Objects.equals(mimeType, "application/vnd.ms-excel.sheet.binary.macroenabled.12")) {
            extTemp = "ods";
        }
        return extTemp;
    }

    private void requestForOdf() {
        String extTemp = getOdfExtensionForDocType(getMimeType());
        if (extTemp == null)
            // this means we don't need to request for odf type.
            return;
        final String ext = extTemp;
        buildPrompt(getString(R.string.ask_for_convert_odf), getString(R.string.convert_odf_message),
                getString(R.string.use_odf), getString(R.string.use_text), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        createNewFileInputDialog(mActivity, getFileName(false) + "." + ext, getMimeForFormat(ext),
                                REQUEST_COPY);
                    }
                }).show();
    }

    private void initiateSaveAs(String optionsString) {
        Map<String, String> optionsMap = new HashMap<>();
        String[] options = optionsString.split(" ");
        for (String option : options) {
            String[] keyValue = option.split("=", 2);
            if (keyValue.length == 2)
                optionsMap.put(keyValue[0], keyValue[1]);
        }
        String format = optionsMap.get("format");
        String mime = getMimeForFormat(format);
        if (format != null && mime != null) {
            String filename = optionsMap.get("name");
            if (filename == null)
                filename = "document." + format;
            int requestID = getRequestIDForFormat(format);

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_TITLE, filename);
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, false);
            File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(folder).toString());
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
            startActivityForResult(intent, requestID);
        }
    }

    private int getRequestIDForFormat(String format) {
        switch (format) {
            case "pdf":
                return REQUEST_SAVEAS_PDF;
            case "rtf":
                return REQUEST_SAVEAS_RTF;
            case "odt":
                return REQUEST_SAVEAS_ODT;
            case "odp":
                return REQUEST_SAVEAS_ODP;
            case "ods":
                return REQUEST_SAVEAS_ODS;
            case "docx":
                return REQUEST_SAVEAS_DOCX;
            case "pptx":
                return REQUEST_SAVEAS_PPTX;
            case "xlsx":
                return REQUEST_SAVEAS_XLSX;
            case "doc":
                return REQUEST_SAVEAS_DOC;
            case "ppt":
                return REQUEST_SAVEAS_PPT;
            case "xls":
                return REQUEST_SAVEAS_XLS;
            case "epub":
                return REQUEST_SAVEAS_EPUB;
        }
        return 0;
    }

    private String getMimeForFormat(String format) {
        switch (format) {
            case "pdf":
                return "application/pdf";
            case "rtf":
                return "text/rtf";
            case "odt":
                return "application/vnd.oasis.opendocument.text";
            case "odp":
                return "application/vnd.oasis.opendocument.presentation";
            case "ods":
                return "application/vnd.oasis.opendocument.spreadsheet";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "doc":
                return "application/msword";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "xls":
                return "application/vnd.ms-excel";
            case "epub":
                return "application/epub+zip";
        }
        return null;
    }

    private void afterMessageFromWebView(String[] messageAndParameterArray) {
        switch (messageAndParameterArray[0]) {
            case "uno":
                switch (messageAndParameterArray[1]) {
                    case ".uno:Copy":
                    case ".uno:Cut":
                        populateClipboard();
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
    }

    private void initiatePrint() {
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        PrintDocumentAdapter printAdapter = new PrintAdapter(LOActivity.this);
        printManager.print("Document", printAdapter, new PrintAttributes.Builder().build());
    }

    /** Send message back to the shell (for example for the cloud save). */
    public void sendBroadcast(String event, String data) {
        Intent intent = new Intent(LO_ACTIVITY_BROADCAST);
        intent.putExtra(LO_ACTION_EVENT, event);
        intent.putExtra(LO_ACTION_DATA, data);
    }

    public native void saveAs(String fileUri, String format, String options);

    public native boolean getClipboardContent(LokClipboardData aData);

    public native void setClipboardContent(LokClipboardData aData);

    public native void paste(String mimeType, byte[] data);

    public native void postUnoCommand(String command, String arguments, boolean bNotifyWhenFinished);

    /// Returns a magic that specifies this application - and this document.
    private final String getClipboardMagic() {
        return CLIPBOARD_COOL_SIGNATURE + Long.toString(loadDocumentMillis);
    }

    /// Needs to be executed after the .uno:Copy / Paste has executed
    public final void populateClipboard() {
        File clipboardFile = new File(getApplicationContext().getCacheDir(), CLIPBOARD_FILE_PATH);
        if (clipboardFile.exists())
            clipboardFile.delete();

        LokClipboardData clipboardData = new LokClipboardData();
        if (!LOActivity.this.getClipboardContent(clipboardData))
            Log.e(TAG, "no clipboard to copy");
        else {
            clipboardData.writeToFile(clipboardFile);

            String text = clipboardData.getText();
            String html = clipboardData.getHtml();

            if (html != null) {
                int idx = html.indexOf("<meta name=\"generator\" content=\"");

                if (idx < 0)
                    idx = html.indexOf("<meta http-equiv=\"content-type\" content=\"text/html;");

                if (idx >= 0) { // inject our magic
                    StringBuffer newHtml = new StringBuffer(html);
                    newHtml.insert(idx, "<meta name=\"origin\" content=\"" + getClipboardMagic() + "\"/>\n");
                    html = newHtml.toString();
                }

                if (text == null || text.length() == 0)
                    Log.i(TAG, "set text to clipoard with: text '" + text + "' and html '" + html + "'");

                clipData = ClipData.newHtmlText(ClipDescription.MIMETYPE_TEXT_HTML, text, html);
                clipboardManager.setPrimaryClip(clipData);
            }
        }
    }

    /// Do the paste, and return true if we should short-circuit the paste locally
    /// (ie. let the core handle that)
    private final boolean performPaste() {
        clipData = clipboardManager.getPrimaryClip();
        if (clipData == null)
            return false;

        ClipDescription clipDesc = clipData.getDescription();
        if (clipDesc == null)
            return false;

        for (int i = 0; i < clipDesc.getMimeTypeCount(); ++i) {
            Log.d(TAG, "Pasting mime " + i + ": " + clipDesc.getMimeType(i));

            if (clipDesc.getMimeType(i).equals(ClipDescription.MIMETYPE_TEXT_HTML)) {
                final String html = clipData.getItemAt(i).getHtmlText();
                // Check if the clipboard content was made with the app
                if (html.contains(CLIPBOARD_COOL_SIGNATURE)) {
                    // Check if the clipboard content is from the same app instance
                    if (html.contains(getClipboardMagic())) {
                        Log.i(TAG, "clipboard comes from us - same instance: short circuit it " + html);
                        return true;
                    } else {
                        Log.i(TAG, "clipboard comes from us - other instance: paste from clipboard file");

                        File clipboardFile = new File(getApplicationContext().getCacheDir(), CLIPBOARD_FILE_PATH);
                        LokClipboardData clipboardData = null;
                        if (clipboardFile.exists())
                            clipboardData = LokClipboardData.createFromFile(clipboardFile);

                        if (clipboardData != null) {
                            LOActivity.this.setClipboardContent(clipboardData);
                            return true;
                        } else {
                            // Couldn't get data from the clipboard file, but we can still paste html
                            byte[] htmlByteArray = html.getBytes(Charset.forName("UTF-8"));
                            LOActivity.this.paste("text/html", htmlByteArray);
                        }
                        return false;
                    }
                } else {
                    Log.i(TAG, "foreign html '" + html + "'");
                    byte[] htmlByteArray = html.getBytes(Charset.forName("UTF-8"));
                    LOActivity.this.paste("text/html", htmlByteArray);
                    return false;
                }
            } else if (clipDesc.getMimeType(i).startsWith("image/")) {
                ClipData.Item item = clipData.getItemAt(i);
                Uri uri = item.getUri();
                try {
                    InputStream imageStream = getContentResolver().openInputStream(uri);
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                    int nRead;
                    byte[] data = new byte[16384];
                    while ((nRead = imageStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }

                    LOActivity.this.paste(clipDesc.getMimeType(i), buffer.toByteArray());
                    return false;
                } catch (Exception e) {
                    Log.d(TAG, "Failed to paste image: " + e.getMessage());
                }
            }
        }

        // try the plaintext as the last resort
        for (int i = 0; i < clipDesc.getMimeTypeCount(); ++i) {
            Log.d(TAG, "Plain text paste attempt " + i + ": " + clipDesc.getMimeType(i));

            if (clipDesc.getMimeType(i).equals(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                final ClipData.Item clipItem = clipData.getItemAt(i);
                String text = clipItem.getText().toString();
                byte[] textByteArray = text.getBytes(Charset.forName("UTF-8"));
                LOActivity.this.paste("text/plain;charset=utf-8", textByteArray);
            }
        }

        return false;
    }
}

/*
 * vim:set shiftwidth=4 softtabstop=4 expandtab cinoptions=b1,g0,N-s
 * cinkeys+=0=break:
 */
