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
import android.content.ComponentName;
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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.DisplayMetrics;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.NestedScrollView;
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
import org.libreoffice.androidlib.ai.ArticleTemplate;
import org.libreoffice.androidlib.ai.ArticleTemplateRegistry;
import org.libreoffice.androidlib.ai.FormatBatchProcessor;
import org.libreoffice.androidlib.ai.PolishStyleRegistry;
import org.libreoffice.androidlib.ai.TranslateLanguageRegistry;
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
    private static final long MOBILE_WIZARD_COMMAND_BLOCK_MS = 4000L;
    private static final long PREVIEW_SELECTION_TILE_RECOVER_THROTTLE_MS = 1200L;
    private long lastPreviewSelectionTileRecoverAt = 0L;
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
    private View aiProgressBar;
    private TextView aiProgressLabel;
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
    private BottomSheetDialog aiOperationSheet;
    private View aiOperationSheetPanel;
    private TextView aiOpSelectionHint;
    private String aiOpPendingSelection = "";
    // AI排版相关
    private BottomSheetDialog typesetSelectSheet;
    private BottomSheetDialog typesetPreviewSheet;
    private String pendingTypesetType;  // "paper" | "gov" | "contract" | "general"
    private String pendingTypesetHtml;  // AI 返回的排版结果
    // 生成大纲相关
    private AlertDialog outlineDialog;
    private View outlineDialogRoot;
    private TextView outlineTypeLabel;
    private EditText outlineDescEdit;
    private TextView outlineResultText;
    private View outlineDescCard;
    private NestedScrollView outlineResultCard;
    private View outlineGenerateBtn;
    private View outlineDoneRow;
    private View outlineCopyRow;  // 结果区下方的复制横条
    private String outlineContextText;  // 入口A=选区文字，入口B=null→生成时提取全文
    private String pendingOutlineType = AiChatCoordinator.OUTLINE_TYPE_GENERAL;
    private String pendingOutlineDesc;
    private String pendingOutlineResult;
    private String outlineActiveRequestId = "";  // 当前大纲请求 id（流式注册/清理用）
    // 文案生成相关
    private static final int ARTICLE_STAGE_SELECT = 1;
    private static final int ARTICLE_STAGE_FORM = 2;
    private static final int ARTICLE_STAGE_RESULT = 3;
    private AlertDialog articleDialog;
    private View articleDialogRoot;
    private TextView articleCategoryLabel;
    private TextView articleSubTypeLabel;
    private View articleSubTypeCard;
    private View articleStageHint;
    private View articleStageForm;
    private LinearLayout articleFormContainer;
    private TextView articleGenerateBtnText;
    private NestedScrollView articleResultCard;
    private TextView articleResultText;
    private View articleCopyRow;
    private View articleDoneRow;
    private String pendingArticleCategory;
    private ArticleTemplate pendingArticleTemplate;
    private String[] pendingArticleValues;
    private String pendingArticleResult;
    private String articleActiveRequestId = "";
    // 扩写/缩写/润色弹窗
    private static final int TEXT_OP_STAGE_INPUT = 1;
    private static final int TEXT_OP_STAGE_RESULT = 2;
    private AlertDialog textOperateDialog;
    private View textOperateDialogRoot;
    private String textOperateMode;
    private String textOperateSelection = "";
    private TextView textOperateTitle;
    private FrameLayout textOperateInputContainer;
    private EditText textOperateRequirementEdit;
    private TextView textOperatePolishStyleLabel;
    private View textOperatePolishStyleCard;
    private NestedScrollView textOperateResultCard;
    private TextView textOperateResultText;
    private View textOperateGenerateBtn;
    private View textOperateCopyRow;
    private View textOperateDoneRow;
    private String textOperateActiveRequestId = "";
    private String pendingTextOperateResult;
    private String pendingPolishStyle = AiChatCoordinator.POLISH_STYLE_QUICK;
    private String pendingTextOperateRequirement = "";
    // 格式批量处理弹窗
    private static final int FORMAT_BATCH_STAGE_INPUT = 1;
    private static final int FORMAT_BATCH_STAGE_RESULT = 2;
    private AlertDialog formatBatchDialog;
    private View formatBatchDialogRoot;
    private String formatBatchSelection = "";
    private String pendingFormatBatchResult;
    private View formatBatchOptionsContainer;
    private View formatBatchExecuteBtn;
    private NestedScrollView formatBatchResultCard;
    private TextView formatBatchResultText;
    private View formatBatchCopyRow;
    private View formatBatchDoneRow;
    private CheckBox[] formatBatchCheckBoxes = new CheckBox[FormatBatchProcessor.RULE_COUNT];
    // 文字提取弹窗
    private static final int TEXT_EXTRACT_STAGE_INPUT = 1;
    private static final int TEXT_EXTRACT_STAGE_RESULT = 2;
    private AlertDialog textExtractDialog;
    private View textExtractDialogRoot;
    private View textExtractInputContainer;
    private NestedScrollView textExtractResultCard;
    private TextView textExtractResultText;
    private View textExtractCopyRow;
    private View textExtractDoneRow;
    private String textExtractActiveRequestId = "";
    private String pendingTextExtractResult;
    private Uri pendingTextExtractCameraUri;
    // AI图片弹窗
    private static final int AI_IMAGE_STAGE_INPUT = 1;
    private static final int AI_IMAGE_STAGE_RESULT = 2;
    private AlertDialog aiImageDialog;
    private View aiImageDialogRoot;
    private View aiImageInputContainer;
    private EditText aiImagePromptEdit;
    private Spinner aiImageRatioSpinner;
    private View aiImageGenerateBtn;
    private View aiImageGalleryContainer;
    private ImageView aiImageMainView;
    private ImageView[] aiImageThumbViews = new ImageView[3];
    private View aiImageLoading;
    private View aiImageDoneRow;
    private java.util.List<String> aiImageBase64List = new java.util.ArrayList<>();
    private int aiImageSelectedIndex = 0;
    private String aiImageActiveRequestId = "";
    private java.util.List<AiRequestSession> aiImageSessions = new java.util.ArrayList<>();
    private AlertDialog aiImagePreviewDialog;
    private int aiImagePreviewCurrentIndex = 0;
    // 翻译弹窗
    private static final int TRANSLATE_STAGE_INPUT = 1;
    private static final int TRANSLATE_STAGE_RESULT = 2;
    private AlertDialog translateDialog;
    private View translateDialogRoot;
    private TextView translateSourceLabel;
    private TextView translateTargetLabel;
    private EditText translateSourceEdit;
    private NestedScrollView translateResultCard;
    private TextView translateResultText;
    private View translateGenerateBtn;
    private View translateCopyRow;
    private View translateDoneRow;
    private String translateActiveRequestId = "";
    private String pendingTranslateResult;
    private String pendingTranslateSourceLang = AiChatCoordinator.TRANSLATE_LANG_AUTO;
    private String pendingTranslateTargetLang = AiChatCoordinator.TRANSLATE_LANG_ZH;
    // AI续写浮层（弹窗式续写：生成中态/完成态，复用 aiStreamingViewByRequestId 流式接入）
    private View continueDialogOverlay;
    private View continueDialogPanel;
    private TextView continueContentView;
    private View continueCopyBar;
    private View continueStopBtn;
    private View continueCompletedGroup;
    private View continueRegenBtn;
    private View continueInsertBtn;
    private String continueSelection = "";        // 打开弹窗时缓存的选区上下文，供重写生成复用
    private String continueActiveRequestId = "";  // 当前续写请求 id（""=无在途）
    private String continueResultText = "";       // 完成/停止时捕获的全文，供插入文档使用
    // 所有续写请求 id（含已结束/被取代的），用于在 onDone/onError 抑制 operate-mode 自动粘贴：
    // AiRequestManager 流自然结束时 onDone 无 cancel 守卫，dismiss/regenerate 后漏出的 onDone 不能误触粘贴。
    private final java.util.Set<String> continueWriteRequestIds =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());
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
    private long lastBlockedMobileWizardAt = 0L;
    private int bottomToolbarImeInsetPx = 0;
    private boolean isImeVisibleForToolbar = false;
    private BottomToolbarController bottomToolbarController;
    private FunctionPanelController functionPanelController;
    private TopToolbarController topToolbarController;
    private FindReplaceSheetController findReplaceSheetController;
    private DocumentTabsSheetController documentTabsSheetController;
    private SelectionMenuController selectionMenuController;
    private Runnable pendingAfterEditMode;
    private boolean documentModified = false;
    private boolean closeAfterSaveRequested = false;
    private boolean documentStateBridgeInjected = false;

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
    public static final int REQUEST_TEXT_EXTRACT_ALBUM = 700;
    public static final int REQUEST_TEXT_EXTRACT_CAMERA = 701;
    public static final int PERMISSION_TEXT_EXTRACT_CAMERA = 720;

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
                    Insets imeInsets = windowInsets.getInsets(WindowInsets.Type.ime());
                    boolean imeVisible = windowInsets.isVisible(WindowInsets.Type.ime());
                    int imeInsetBottom = imeVisible ? imeInsets.bottom : 0;
                    if (imeVisible && imeInsetBottom < dpToPx(IME_VISIBLE_THRESHOLD_DP)) {
                        imeVisible = false;
                        imeInsetBottom = 0;
                    }
                    applyBottomToolbarImeState(imeVisible, imeInsetBottom, navInsets.bottom);

                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                    mlp.leftMargin = insets.left;
                    mlp.topMargin = 0;
                    mlp.rightMargin = insets.right;
                    // Navigation bar inset is applied to the native bottom toolbar margin;
                    // keep WebView flush against the toolbar to avoid a blank strip above it.
                    mlp.bottomMargin = 0;
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
            setupContinueWriteDialog();
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
            mWebView.setConsumeWebViewLongClick(true);

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
            case PERMISSION_TEXT_EXTRACT_CAMERA:
                if (permissions.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCameraForTextExtract();
                } else {
                    toastTodo("需要相机权限才能拍照识别");
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
        if (documentLoaded) {
            recoverVisibleTilesAfterEditMode("activity_resume");
        }
        Log.i(TAG, "onResume..");
    }

    @Override
    protected void onPause() {
        // 注意：BottomSheetDialog 弹起时 Activity 会 onPause（FLAG_WORKSPACE 或系统行为），
        // onPause 里的 save 消息会让 core 退出编辑态，导致"跳回主页"现象。
        // 修复：彻底移除这里的 save，文档保存由其他机制（auto-save / 用户手动保存）负责。
        Log.i(TAG, "onPause.. documentLoaded=" + documentLoaded
                + " aiSheetShowing=" + (aiOperationSheet != null && aiOperationSheet.isShowing()));
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        Log.i(TAG, "onBackPressed.. isFinishing=" + isFinishing() + " aiSheetShowing="
                + (aiOperationSheet != null && aiOperationSheet.isShowing()));
        super.onBackPressed();
    }

    @Override
    public void onUserLeaveHint() {
        Log.i(TAG, "onUserLeaveHint..");
        super.onUserLeaveHint();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy.. documentLoaded=" + documentLoaded + " isFinishing=" + isFinishing()
                + " aiSheetShowing=" + (aiOperationSheet != null && aiOperationSheet.isShowing()));
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
        Log.i(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode
                + " aiSheetShowing=" + (aiOperationSheet != null && aiOperationSheet.isShowing()));
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
            case REQUEST_TEXT_EXTRACT_ALBUM: {
                if (intent == null || intent.getData() == null) {
                    return;
                }
                handleTextExtractImageUri(intent.getData());
                return;
            }
            case REQUEST_TEXT_EXTRACT_CAMERA: {
                if (pendingTextExtractCameraUri != null) {
                    handleTextExtractImageUri(pendingTextExtractCameraUri);
                }
                return;
            }
            case REQUEST_SELECT_IMAGE_FILE:
                if (valueCallback == null)
                    return;
                valueCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
                valueCallback = null;
                return;
            case DocumentTabsSheetController.REQUEST_OPEN_DOCUMENT:
                if (intent == null || intent.getData() == null) {
                    return;
                }
                Uri openedUri = intent.getData();
                final int takeFlags = intent.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(openedUri, takeFlags);
                } catch (SecurityException e) {
                    Log.w(TAG, "takePersistableUriPermission failed: " + e.getMessage());
                }
                openDocumentUri(openedUri);
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
        if (intent == null || intent.getData() == null) {
            return;
        }
        RecentDocumentsStore.prependRecent(
                getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE),
                intent.getData().toString());
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
        documentStateBridgeInjected = false;
        documentModified = false;
        closeAfterSaveRequested = false;

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
        ensureTopToolbarController().resetUndoRedoState("document_loaded");
        Uri currentData = getIntent().getData();
        if (currentData != null) {
            RecentDocumentsStore.prependRecent(
                    getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE),
                    currentData.toString());
        }

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
                    injectDocumentStateBridgeIfNeeded();
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
                documentModified = false;
                copyTempBackToIntent();
                sendBroadcast(messageAndParam[0], messageAndParam.length > 1 ? messageAndParam[1] : "");
                if (closeAfterSaveRequested) {
                    closeAfterSaveRequested = false;
                    finishWithProgress();
                }
                return false;
            case "DOC_MODIFIED_STATUS":
                documentModified = messageAndParam.length > 1 && "true".equalsIgnoreCase(messageAndParam[1]);
                Log.d(TAG, "doc_modified_status modified=" + documentModified);
                return false;
            case "downloadas":
                initiateSaveAs(messageAndParam[1]);
                return false;
            case "uno":
                if (messageAndParam.length > 1 && shouldBlockUnexpectedMobileWizardUno(messageAndParam[1])) {
                    Log.w(TAG, "blocked_mobile_wizard_uno command=" + messageAndParam[1]);
                    return false;
                }
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
                        if (mIsEditModeActive) {
                            lastBlockedMobileWizardAt = android.os.SystemClock.uptimeMillis();
                            closeMobileWizardFromAndroid("edit_mode_web_long_press");
                            Log.w(TAG, "blocked_mobile_wizard_show_in_edit_mode");
                        }
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
                        recoverVisibleTilesAfterEditMode("js_editmode_on");
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
            case "UNDOREDO": {
                handleUndoRedoStateFromWeb(messageAndParam.length > 1 ? messageAndParam[1] : "");
                return false;
            }
            case "SELECTIONMENU": {
                if (messageAndParam.length > 1 && "hide".equals(messageAndParam[1])) {
                    getMainHandler().post(() -> ensureSelectionMenuController().hide());
                    return false;
                }
                if (messageAndParam.length > 1 && messageAndParam[1] != null) {
                    if (messageAndParam[1].startsWith("show ")) {
                        // show with coordinates: "show x y" or "show x y bottomY"
                        try {
                            String[] parts = messageAndParam[1].split(" ");
                            if (parts.length >= 3) {
                                final float anchorX = Float.parseFloat(parts[1]);
                                final float anchorY = Float.parseFloat(parts[2]);
                                final float anchorBottomY = parts.length >= 4
                                        ? Float.parseFloat(parts[3])
                                        : anchorY;
                                getMainHandler().post(() -> {
                                    ensureSelectionMenuController().showAtWindow(
                                            anchorX, anchorY, anchorBottomY);
                                    getMainHandler().postDelayed(() ->
                                            recoverVisibleTilesAfterPreviewSelection("selection_menu_show"), 180);
                                });
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "selection_menu_bad_anchor", e);
                        }
                    } else if ("show".equals(messageAndParam[1])) {
                        // show without coordinates - fixed center position
                        getMainHandler().post(() -> {
                            ensureSelectionMenuController().showAtWindow(0, 0);
                            getMainHandler().postDelayed(() ->
                                    recoverVisibleTilesAfterPreviewSelection("selection_menu_show"), 180);
                        });
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
                    Log.d(TAG, "doc_gesture_guard " + (enable ? "on" : "off"));
                    lastDocGestureGuardLogAt = now;
                }
                if (!enable && changed) {
                    Log.i(TAG, "reconnect_trigger editMode=" + mIsEditModeActive
                            + " pid=" + android.os.Process.myPid());
                    triggerSelectionStateSync("reconnect_done", mIsEditModeActive);
                    recoverVisibleTilesAfterEditMode("reconnect_done_editmode");
                }
                return false;
            }
            case "DEBUG_HTML_PROBE": {
                // 诊断：paste 测试 HTML 实测 Writer HTML Import Filter 保留哪些属性（color/hr/font size/align/table/CSS）。
                // 从 Chrome 远程调试控制台触发：window.postMobileMessage("DEBUG_HTML_PROBE")，须在编辑模式下。
                probeHtmlFilterCapability();
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

    private boolean shouldBlockUnexpectedMobileWizardUno(String command) {
        long now = android.os.SystemClock.uptimeMillis();
        if (!mIsEditModeActive || now - lastBlockedMobileWizardAt > MOBILE_WIZARD_COMMAND_BLOCK_MS) {
            return false;
        }
        if (command == null) {
            return false;
        }
        return command.startsWith(".uno:ResetAttributes")
                || command.startsWith(".uno:FormatPaintbrush")
                || command.startsWith(".uno:InsertAnnotation")
                || command.startsWith(".uno:Paste");
    }

    private void closeMobileWizardFromAndroid(String reason) {
        if (mWebView == null) {
            return;
        }
        getMainHandler().post(() -> {
            if (mWebView == null) {
                return;
            }
            final String escapedReason = escapeForJsString(reason == null ? "android" : reason);
            mWebView.evaluateJavascript(
                    "(function(){try{"
                            + "if(window.app&&app.map&&typeof app.map.fire==='function'){app.map.fire('closemobilewizard');}"
                            + "if(window.console&&console.info){console.info('android close mobile wizard reason=" + escapedReason + "');}"
                            + "}catch(e){if(window.console&&console.warn){console.warn('android_close_mobile_wizard_failed',e);}}"
                            + "return true;})();",
                    null);
        });
    }

    private void handleUndoRedoStateFromWeb(String payload) {
        boolean canUndo = false;
        boolean canRedo = false;
        if (payload != null) {
            String[] tokens = payload.trim().split("\\s+");
            for (String token : tokens) {
                if (token.startsWith("undo=")) {
                    canUndo = "1".equals(token.substring("undo=".length()));
                } else if (token.startsWith("redo=")) {
                    canRedo = "1".equals(token.substring("redo=".length()));
                }
            }
        }
        ensureTopToolbarController().updateUndoRedoState(canUndo, canRedo, "web_commandstate");
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
        } else if ("vision".equalsIgnoreCase(modelMode)) {
            SharedPreferences modelPrefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
            endpoint = modelPrefs.getString("AI_MODEL_VISION_url", endpoint);
            apiKey = modelPrefs.getString("AI_MODEL_VISION_api_key", apiKey);
            model = modelPrefs.getString("AI_MODEL_VISION_model_name", model);
            endpoint = endpoint == null ? "" : endpoint.trim();
            apiKey = apiKey == null ? "" : apiKey.trim();
            model = model == null ? "" : model.trim();
        } else if ("image".equalsIgnoreCase(modelMode)) {
            SharedPreferences modelPrefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
            endpoint = modelPrefs.getString("AI_MODEL_IMAGE_url", endpoint);
            apiKey = modelPrefs.getString("AI_MODEL_IMAGE_api_key", apiKey);
            model = modelPrefs.getString("AI_MODEL_IMAGE_model_name", model);
            endpoint = endpoint == null ? "" : endpoint.trim();
            apiKey = apiKey == null ? "" : apiKey.trim();
            model = model == null ? "" : model.trim();
        }

        // endpoint 兜底规范化：按协议补全路径后缀，避免配址错配（如图片模型错配 chat/completions）
        if ("image".equalsIgnoreCase(modelMode)) {
            endpoint = normalizeEndpoint(endpoint, "/images/generations");
        } else if ("base".equalsIgnoreCase(modelMode) || "vision".equalsIgnoreCase(modelMode)) {
            endpoint = normalizeEndpoint(endpoint, "/chat/completions");
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
            String modelLabel = "vision".equalsIgnoreCase(modelMode) ? "视觉模型"
                    : "image".equalsIgnoreCase(modelMode) ? "图片生成模型" : "基础模型";
            String message = "请先在设置中配置" + modelLabel + "的接口地址。";
            Log.w(TAG, "ai_config_missing requestId=" + requestId + " field=endpoint modelMode=" + modelMode);
            dispatchAiState(requestId, AI_STATE_UNCONFIGURED, message);
            dispatchAiError(requestId, "config_missing", message);
            return;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            String modelLabel = "vision".equalsIgnoreCase(modelMode) ? "视觉模型"
                    : "image".equalsIgnoreCase(modelMode) ? "图片生成模型" : "基础模型";
            String message = "请先在设置中配置" + modelLabel + "的 API Key。";
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
            } else if (AiChatCoordinator.isOperateMode(taskType)) {
                String selection = request.optString("selection", "");
                messages = AiChatCoordinator.buildOperateMessages(taskType, selection);
                Log.i(TAG, "ai_operate_mode requestId=" + requestId + " mode=" + taskType
                        + " selectionChars=" + selection.length());
            } else if (AiChatCoordinator.MODE_TYPESET.equals(taskType)) {
                String typesetType = request.optString("typesetType", "general");
                String fullText = request.optString("selection", "");
                messages = AiChatCoordinator.buildTypesetMessages(typesetType, fullText);
                Log.i(TAG, "ai_typeset_mode requestId=" + requestId + " typesetType=" + typesetType
                        + " docChars=" + fullText.length());
            } else if (AiChatCoordinator.MODE_OUTLINE.equals(taskType)) {
                String outlineType = request.optString("outlineType", AiChatCoordinator.OUTLINE_TYPE_GENERAL);
                JSONObject ctxObj = request.optJSONObject("context");
                String ctxText = request.optString("selection", "");
                String desc = ctxObj != null ? ctxObj.optString("description", "") : "";
                messages = AiChatCoordinator.buildOutlineMessages(outlineType, ctxText, desc);
                Log.i(TAG, "ai_outline_mode requestId=" + requestId + " outlineType=" + outlineType
                        + " contextChars=" + ctxText.length() + " descChars=" + desc.length());
            } else if (AiChatCoordinator.MODE_ARTICLE_GENERATE.equals(taskType)) {
                String templateKey = request.optString("articleTemplateKey", "");
                ArticleTemplate template = ArticleTemplateRegistry.findByKey(templateKey);
                JSONObject ctxObj = request.optJSONObject("context");
                JSONArray valuesArr = ctxObj != null ? ctxObj.optJSONArray("articleValues") : null;
                String[] values = new String[0];
                if (valuesArr != null) {
                    values = new String[valuesArr.length()];
                    for (int i = 0; i < valuesArr.length(); i++) {
                        values[i] = valuesArr.optString(i, "");
                    }
                }
                if (template == null) {
                    throw new JSONException("Unknown article template: " + templateKey);
                }
                messages = AiChatCoordinator.buildArticleMessages(template, values);
                Log.i(TAG, "ai_article_mode requestId=" + requestId + " template=" + templateKey
                        + " vars=" + values.length);
            } else if (AiChatCoordinator.MODE_EXPAND.equals(taskType)) {
                JSONObject ctxObj = request.optJSONObject("context");
                String selection = request.optString("selection", "");
                String requirement = ctxObj != null ? ctxObj.optString("requirement", "") : "";
                messages = AiChatCoordinator.buildExpandMessages(selection, requirement);
                Log.i(TAG, "ai_expand_mode requestId=" + requestId + " selectionChars=" + selection.length());
            } else if (AiChatCoordinator.MODE_CONDENSE.equals(taskType)) {
                JSONObject ctxObj = request.optJSONObject("context");
                String selection = request.optString("selection", "");
                String requirement = ctxObj != null ? ctxObj.optString("requirement", "") : "";
                messages = AiChatCoordinator.buildCondenseMessages(selection, requirement);
                Log.i(TAG, "ai_condense_mode requestId=" + requestId + " selectionChars=" + selection.length());
            } else if (AiChatCoordinator.MODE_POLISH.equals(taskType)) {
                JSONObject ctxObj = request.optJSONObject("context");
                String selection = request.optString("selection", "");
                String polishStyle = ctxObj != null ? ctxObj.optString("polishStyle",
                        AiChatCoordinator.POLISH_STYLE_QUICK) : AiChatCoordinator.POLISH_STYLE_QUICK;
                messages = AiChatCoordinator.buildPolishMessages(polishStyle, selection);
                Log.i(TAG, "ai_polish_mode requestId=" + requestId + " style=" + polishStyle
                        + " selectionChars=" + selection.length());
            } else if (AiChatCoordinator.MODE_TRANSLATE.equals(taskType)) {
                JSONObject ctxObj = request.optJSONObject("context");
                String text = request.optString("selection", "");
                String sourceLang = ctxObj != null ? ctxObj.optString("sourceLang",
                        AiChatCoordinator.TRANSLATE_LANG_AUTO) : AiChatCoordinator.TRANSLATE_LANG_AUTO;
                String targetLang = ctxObj != null ? ctxObj.optString("targetLang",
                        AiChatCoordinator.TRANSLATE_LANG_ZH) : AiChatCoordinator.TRANSLATE_LANG_ZH;
                messages = AiChatCoordinator.buildTranslateMessages(sourceLang, targetLang, text);
                Log.i(TAG, "ai_translate_mode requestId=" + requestId + " src=" + sourceLang
                        + " tgt=" + targetLang + " textChars=" + text.length());
            } else if (AiChatCoordinator.MODE_REWRITE.equals(taskType)) {
                JSONObject ctxObj = request.optJSONObject("context");
                String selection = request.optString("selection", "");
                String requirement = ctxObj != null ? ctxObj.optString("requirement", "") : "";
                messages = AiChatCoordinator.buildRewriteMessages(selection, requirement);
                Log.i(TAG, "ai_rewrite_mode requestId=" + requestId + " selectionChars=" + selection.length());
            } else if (AiChatCoordinator.MODE_TEXT_EXTRACT.equals(taskType)) {
                JSONObject ctxObj = request.optJSONObject("context");
                String imageBase64 = ctxObj != null ? ctxObj.optString("image", "") : "";
                messages = AiChatCoordinator.buildTextExtractMessages(imageBase64);
                Log.i(TAG, "ai_text_extract_mode requestId=" + requestId + " imageChars=" + imageBase64.length());
            } else {
                messages.put(new JSONObject().put("role", "user").put("content", buildAiUserPrompt(request)));
            }
            Log.i(TAG, "ai_execute_start requestId=" + requestId + " endpoint=" + endpoint + " model=" + model);
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
                            if (callbackRequestId.equals(continueActiveRequestId)) {
                                // 续写弹窗请求：不自动粘贴，切到完成态（重写生成/插入文档）
                                onContinueWriteDone(callbackRequestId, fullText);
                            } else if (continueWriteRequestIds.contains(callbackRequestId)) {
                                // 已结束/被取代的续写请求（dismiss 或 regenerate 后漏出的 onDone）：抑制 operate-mode 自动粘贴
                                Log.i(TAG, "continue_write_done_suppressed requestId=" + callbackRequestId);
                            } else if (AiChatCoordinator.isOperateMode(taskType)) {
                                onAiOperationDone(callbackRequestId, fullText);
                            } else if (AiChatCoordinator.MODE_TYPESET.equals(taskType)) {
                                // typeset 模式：显示预览，不直接粘贴
                                Log.i(TAG, "ai_typeset_done requestId=" + callbackRequestId + " htmlChars=" + fullText.length());
                                runOnUiThread(() -> showTypesetPreviewSheet(fullText));
                            } else if (AiChatCoordinator.MODE_OUTLINE.equals(taskType)) {
                                // 生成大纲：在弹窗结果区展示，不自动粘贴
                                Log.i(TAG, "ai_outline_done requestId=" + callbackRequestId + " chars=" + fullText.length());
                                runOnUiThread(() -> showOutlineResult(fullText));
                            } else if (AiChatCoordinator.MODE_ARTICLE_GENERATE.equals(taskType)) {
                                Log.i(TAG, "ai_article_done requestId=" + callbackRequestId + " chars=" + fullText.length());
                                runOnUiThread(() -> showArticleGenerateResult(fullText));
                            } else if (AiChatCoordinator.MODE_EXPAND.equals(taskType)
                                    || AiChatCoordinator.MODE_CONDENSE.equals(taskType)
                                    || AiChatCoordinator.MODE_POLISH.equals(taskType)
                                    || AiChatCoordinator.MODE_REWRITE.equals(taskType)) {
                                Log.i(TAG, "ai_text_op_done requestId=" + callbackRequestId
                                        + " mode=" + taskType + " chars=" + fullText.length());
                                runOnUiThread(() -> showTextOperateResult(fullText));
                            } else if (AiChatCoordinator.MODE_TRANSLATE.equals(taskType)) {
                                Log.i(TAG, "ai_translate_done requestId=" + callbackRequestId
                                        + " chars=" + fullText.length());
                                runOnUiThread(() -> showTranslateResult(fullText));
                            } else if (AiChatCoordinator.MODE_TEXT_EXTRACT.equals(taskType)) {
                                Log.i(TAG, "ai_text_extract_done requestId=" + callbackRequestId
                                        + " chars=" + fullText.length());
                                runOnUiThread(() -> showTextExtractResult(fullText));
                            }
                            JSONObject donePayload = new JSONObject();
                            donePayload.put("requestId", callbackRequestId);
                            donePayload.put("fullText", fullText);
                            dispatchAiEvent("ai.done", donePayload);
                        }

                        @Override
                        public void onError(String callbackRequestId, String code, String message) {
                            String safeMsg = message == null ? "" : (message.length() > 120 ? message.substring(0, 120) + "..." : message);
                            Log.i(TAG, "ai_operation_error requestId=" + callbackRequestId + " code=" + code + " msg=" + safeMsg);
                            if (continueWriteRequestIds.contains(callbackRequestId)) {
                                // 续写请求失败：提示并关闭浮层，不走 operate-mode "AI 操作失败" 分支
                                runOnUiThread(() -> {
                                    toastTodo("续写失败：" + safeMsg);
                                    dismissContinueWriteDialog();
                                });
                            } else if (AiChatCoordinator.isOperateMode(taskType)) {
                                cleanupOperationSheet();
                                runOnUiThread(() -> toastTodo("AI 操作失败：" + message));
                            } else if (AiChatCoordinator.MODE_TYPESET.equals(taskType)) {
                                runOnUiThread(() -> toastTodo("AI排版失败：" + message));
                            } else if (AiChatCoordinator.MODE_OUTLINE.equals(taskType)) {
                                runOnUiThread(() -> {
                                    toastTodo("大纲生成失败：" + message);
                                    switchOutlineDialogState(false);
                                });
                            } else if (AiChatCoordinator.MODE_ARTICLE_GENERATE.equals(taskType)) {
                                runOnUiThread(() -> {
                                    toastTodo("文案生成失败：" + message);
                                    if (pendingArticleTemplate != null) {
                                        switchArticleDialogStage(ARTICLE_STAGE_FORM);
                                    } else {
                                        switchArticleDialogStage(ARTICLE_STAGE_SELECT);
                                    }
                                });
                            } else if (AiChatCoordinator.MODE_EXPAND.equals(taskType)
                                    || AiChatCoordinator.MODE_CONDENSE.equals(taskType)
                                    || AiChatCoordinator.MODE_POLISH.equals(taskType)
                                    || AiChatCoordinator.MODE_REWRITE.equals(taskType)) {
                                runOnUiThread(() -> {
                                    toastTodo("生成失败：" + message);
                                    switchTextOperateStage(TEXT_OP_STAGE_INPUT);
                                });
                            } else if (AiChatCoordinator.MODE_TRANSLATE.equals(taskType)) {
                                runOnUiThread(() -> {
                                    toastTodo("翻译失败：" + message);
                                    switchTranslateStage(TRANSLATE_STAGE_INPUT);
                                });
                            } else if (AiChatCoordinator.MODE_TEXT_EXTRACT.equals(taskType)) {
                                runOnUiThread(() -> {
                                    toastTodo("识别失败：" + message);
                                    switchTextExtractStage(TEXT_EXTRACT_STAGE_INPUT);
                                });
                            }
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

                @Override
                public boolean onAiOperation(String taskType) {
                    return LOActivity.this.startAiOperationFromSelection(taskType);
                }

                @Override
                public void onSelectionPopupShown() {
                    LOActivity.this.preReadSelectionForPopup();
                }

                @Override
                public View getBrowserView() {
                    return LOActivity.this.mWebView;
                }
            });
        }
        selectionMenuController.setup();
    }

    // ==================== AI续写浮层（弹窗式续写）====================

    /**
     * 绑定续写浮层视图与监听。浮层挂在 lolib_activity_main.xml 的 doc_main_content 内
     * （overlay + include panel），仿选区浮层用 setVisibility 切换，避免 BottomSheetDialog
     * dismiss 触发 socket 重连（CLAUDE.md issue #1 Step 4）。
     */
    private void setupContinueWriteDialog() {
        continueDialogOverlay = findViewById(R.id.continue_dialog_overlay);
        continueDialogPanel = findViewById(R.id.continue_write_dialog_panel);
        if (continueDialogOverlay == null || continueDialogPanel == null) {
            return;
        }
        continueContentView = continueDialogPanel.findViewById(R.id.continue_content_text);
        continueStopBtn = continueDialogPanel.findViewById(R.id.continue_stop_button);
        continueCompletedGroup = continueDialogPanel.findViewById(R.id.continue_completed_group);
        continueRegenBtn = continueDialogPanel.findViewById(R.id.continue_regenerate_button);
        continueInsertBtn = continueDialogPanel.findViewById(R.id.continue_insert_button);
        continueCopyBar = continueDialogPanel.findViewById(R.id.continue_copy_bar);
        if (continueCopyBar != null) {
            continueCopyBar.setOnClickListener(v -> onContinueWriteCopy());
        }

        continueDialogOverlay.setOnClickListener(v -> dismissContinueWriteDialog());
        // 右上角关闭×（设计稿顶部横栏，两态都有）
        View closeBtn = continueDialogPanel.findViewById(R.id.continue_close_button);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismissContinueWriteDialog());
        }
        if (continueStopBtn != null) {
            continueStopBtn.setOnClickListener(v -> onContinueWriteStop());
        }
        if (continueRegenBtn != null) {
            continueRegenBtn.setOnClickListener(v -> onContinueWriteRegenerate());
        }
        if (continueInsertBtn != null) {
            continueInsertBtn.setOnClickListener(v -> onContinueWriteInsert());
        }
    }

    /**
     * 打开续写浮层并自动发起续写请求。由 runAiOperation 在 mode=continue_write 且浮层已初始化时
     * divert 调用，覆盖选区弹窗 + AI 功能面板两个入口。
     */
    private void openContinueWriteDialog(String selection) {
        continueSelection = selection == null ? "" : selection;
        if (selectionMenuController != null) {
            selectionMenuController.hide();
        }
        setContinueDialogState(true);
        continueDialogOverlay.setVisibility(View.VISIBLE);
        continueDialogPanel.setVisibility(View.VISIBLE);
        continueDialogPanel.post(this::positionContinueDialogCenter);
        Log.i(TAG, "continue_write_dialog_open chars=" + continueSelection.length());
        startContinueWriteRequest();
    }

    /**
     * 居中定位续写浮层（仿 SelectionMenuController.positionPopupAtCenter）。
     * 宽≈屏宽-48dp、高≈屏高 80%，水平+垂直居中。
     */
    private void positionContinueDialogCenter() {
        if (continueDialogPanel == null) {
            return;
        }
        View parent = (View) continueDialogPanel.getParent();
        if (!(parent instanceof ConstraintLayout)) {
            return;
        }
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        if (parentWidth <= 0 || parentHeight <= 0) {
            return;
        }
        int targetWidth = parentWidth - dpToPx(48);
        int targetHeight = Math.max(dpToPx(400), (int) (parentHeight * 0.8));
        int x = Math.max(0, (parentWidth - targetWidth) / 2);
        int y = Math.max(0, (parentHeight - targetHeight) / 2);

        ConstraintLayout.LayoutParams lp =
                (ConstraintLayout.LayoutParams) continueDialogPanel.getLayoutParams();
        lp.width = targetWidth;
        lp.height = targetHeight;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.UNSET;
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        lp.horizontalBias = 0f;
        lp.verticalBias = 0f;
        lp.leftMargin = x;
        lp.topMargin = y;
        continueDialogPanel.setLayoutParams(lp);
    }

    /**
     * 构建并发起续写请求。复用 operate-mode continue_write prompt（AiChatCoordinator.buildOperateMessages），
     * 关键差异：把流式目标注册到续写浮层的内容 TextView（aiStreamingViewByRequestId），
     * 由 handleAiNativeEvent 的 ai.stream/ai.done 自动渲染进来，无需另写流式代码。
     */
    private void startContinueWriteRequest() {
        String selection = continueSelection;
        if (selection == null || selection.trim().isEmpty()) {
            Toast.makeText(this, "请先选择文本", Toast.LENGTH_SHORT).show();
            dismissContinueWriteDialog();
            return;
        }
        try {
            JSONObject context = new JSONObject();
            context.put("prompt", "");
            context.put("question", "");
            context.put("source", "android-continue-write-dialog");
            context.put("selection", selection);

            JSONObject request = new JSONObject();
            String requestId = "cw-" + UUID.randomUUID().toString();
            request.put("requestId", requestId);
            request.put("taskType", AiChatCoordinator.MODE_CONTINUE);
            request.put("selection", selection);
            request.put("context", context);
            request.put("modelMode", "base");
            request.put("history", new JSONArray());

            aiActiveRequestId = requestId;
            aiStreamingRequestId = requestId;
            aiRequestModeById.put(requestId, AiChatCoordinator.MODE_CONTINUE);
            aiTextByRequestId.put(requestId, new StringBuilder());
            if (continueContentView != null) {
                aiStreamingViewByRequestId.put(requestId, continueContentView);
            }
            continueActiveRequestId = requestId;
            continueWriteRequestIds.add(requestId);

            Log.i(TAG, "continue_write_request requestId=" + requestId
                    + " selectionChars=" + selection.length());
            startAiRequestSession(request, -1);
        } catch (JSONException e) {
            dispatchAiError("", "invalid_payload", "Failed to build continue-write request");
            Log.e(TAG, "Failed to build continue-write request", e);
            dismissContinueWriteDialog();
        }
    }

    /**
     * 切换续写浮层两态。generating=true：显示停止按钮、隐藏完成胶囊组、清空内容；
     * false：隐藏停止按钮、显示完成胶囊组（内容保留，已由 handleAiNativeEvent 渲染）。
     */
    private void setContinueDialogState(boolean generating) {
        if (continueStopBtn != null) {
            continueStopBtn.setVisibility(generating ? View.VISIBLE : View.GONE);
        }
        if (continueCompletedGroup != null) {
            continueCompletedGroup.setVisibility(generating ? View.GONE : View.VISIBLE);
        }
        if (continueCopyBar != null) {
            continueCopyBar.setVisibility(generating ? View.GONE : View.VISIBLE);
        }
        if (generating) {
            if (continueContentView != null) {
                continueContentView.setText("");
            }
            continueResultText = "";
        }
    }

    /**
     * 续写请求自然完成（onDone 回调在 requestId==continueActiveRequestId 时 divert，请求线程触发）。
     * 内容已由 handleAiNativeEvent 的 ai.done 分支重渲进 continueContentView，这里只切到完成态。
     */
    private void onContinueWriteDone(String requestId, String fullText) {
        final String text = fullText == null ? "" : fullText;
        runOnUiThread(() -> {
            continueResultText = text;
            setContinueDialogState(false);
            Log.i(TAG, "continue_write_done requestId=" + requestId + " chars=" + text.length());
        });
    }

    /**
     * 点红色停止按钮：取消在途请求，保留已流式部分，切到完成态。
     */
    private void onContinueWriteStop() {
        String rid = continueActiveRequestId;
        if (!rid.isEmpty()) {
            cancelAiRequest(rid);
        }
        StringBuilder partial = aiTextByRequestId.get(rid);
        String text = partial == null ? "" : partial.toString();
        if (text.isEmpty() && continueContentView != null) {
            text = continueContentView.getText().toString();
        }
        continueResultText = text;
        if (!rid.isEmpty()) {
            aiStreamingViewByRequestId.remove(rid);
        }
        setContinueDialogState(false);
        Log.i(TAG, "continue_write_stopped requestId=" + rid + " chars=" + text.length());
    }

    /**
     * 点「重写生成」：取消在途请求，清空内容，用同一选区上下文重新发一轮。
     */
    private void onContinueWriteRegenerate() {
        if (!continueActiveRequestId.isEmpty()) {
            cancelAiRequest(continueActiveRequestId);
            aiStreamingViewByRequestId.remove(continueActiveRequestId);
        }
        setContinueDialogState(true);
        Log.i(TAG, "continue_write_regenerate");
        startContinueWriteRequest();
    }

    /**
     * 点「插入文档」：把续写结果写入文档（ensureEditModeThen 包裹，兼容预览模式触发）。
     */
    private void onContinueWriteInsert() {
        final String text;
        if (continueResultText != null && !continueResultText.isEmpty()) {
            text = continueResultText;
        } else if (continueContentView != null) {
            text = continueContentView.getText().toString();
        } else {
            text = "";
        }
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "没有可插入的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.i(TAG, "continue_write_insert chars=" + text.length() + " format=html");
        dismissContinueWriteDialog();
        ensureEditModeThen(() -> pasteAiTextAsHtml(text));
    }

    /**
     * 点复制栏：将续写内容复制到剪贴板。
     */
    private void onContinueWriteCopy() {
        String text = continueResultText;
        if ((text == null || text.isEmpty()) && continueContentView != null) {
            text = continueContentView.getText().toString();
        }
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("continue_write", text));
        }
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "continue_write_copy chars=" + text.length());
    }

    /**
     * 关闭续写浮层：取消在途请求、清理流式注册、隐藏 overlay+panel。
     */
    private void dismissContinueWriteDialog() {
        if (!continueActiveRequestId.isEmpty()) {
            cancelAiRequest(continueActiveRequestId);
            aiStreamingViewByRequestId.remove(continueActiveRequestId);
            continueActiveRequestId = "";
        }
        if (continueDialogOverlay != null) {
            continueDialogOverlay.setVisibility(View.GONE);
        }
        if (continueDialogPanel != null) {
            continueDialogPanel.setVisibility(View.GONE);
        }
        Log.i(TAG, "continue_write_dialog_dismiss");
    }

    // ==================== AI续写浮层结束 ====================

    /**
     * 选中弹窗显示后预读当前选区，填充 aiOpPendingSelection，
     * 使 AI 按钮点击时能立即拿到选中文本（编辑模式 JNI getTextSelection 主路径）。
     */
    /*package*/ void preReadSelectionForPopup() {
        getSelectedTextFromJs(selection -> {
            aiOpPendingSelection = selection == null ? "" : selection;
            Log.i(TAG, "selection_popup_preread chars=" + aiOpPendingSelection.length());
        });
    }

    /*package*/ boolean startAiOperationFromSelection(String taskType) {
        // 生成大纲：弹出生成大纲对话框（入口 A，使用选区文字）
        if (AiChatCoordinator.MODE_OUTLINE.equals(taskType)) {
            showOutlineDialog(aiOpPendingSelection);
            return true;
        }
        // 文案生成：弹出文案生成对话框（不依赖选区）
        if (AiChatCoordinator.MODE_ARTICLE_GENERATE.equals(taskType)) {
            if (selectionMenuController != null) {
                selectionMenuController.hide();
            }
            showArticleGenerateDialog();
            return true;
        }
        // 扩写/缩写/润色/重写：弹窗流程
        if (AiChatCoordinator.MODE_EXPAND.equals(taskType)
                || AiChatCoordinator.MODE_CONDENSE.equals(taskType)
                || AiChatCoordinator.MODE_POLISH.equals(taskType)
                || AiChatCoordinator.MODE_REWRITE.equals(taskType)) {
            if (selectionMenuController != null) {
                selectionMenuController.hide();
            }
            String selection = aiOpPendingSelection == null ? "" : aiOpPendingSelection;
            if (selection.trim().isEmpty()) {
                Toast.makeText(this, "请先选择文本", Toast.LENGTH_SHORT).show();
                return true;
            }
            showTextOperateDialog(taskType, selection);
            return true;
        }
        // 翻译：弹窗流程
        if (AiChatCoordinator.MODE_TRANSLATE.equals(taskType)) {
            if (selectionMenuController != null) {
                selectionMenuController.hide();
            }
            String selection = aiOpPendingSelection == null ? "" : aiOpPendingSelection;
            if (selection.trim().isEmpty()) {
                Toast.makeText(this, "请先选择文本", Toast.LENGTH_SHORT).show();
                return true;
            }
            showTranslateDialog(selection);
            return true;
        }
        // 选区已在弹窗显示时预读缓存，优先使用
        if (aiOpPendingSelection != null && !aiOpPendingSelection.isEmpty()) {
            runAiOperation(taskType);
            return true;
        }
        // 兜底：异步读取当前选区后再执行
        getSelectedTextFromJs(selection -> {
            aiOpPendingSelection = selection == null ? "" : selection;
            if (aiOpPendingSelection.trim().isEmpty()) {
                runOnUiThread(() ->
                        Toast.makeText(this, "请先选择文本", Toast.LENGTH_SHORT).show());
                return;
            }
            runAiOperation(taskType);
        });
        return true;
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
                public void requestCloseDocument() {
                    LOActivity.this.requestCloseDocument();
                }

                @Override
                public void executeUnoCommand(String command) {
                    LOActivity.this.executeUnoCommand(command);
                }

                @Override
                public void showFindReplaceSheet() {
                    LOActivity.this.showFindReplaceSheet();
                }

                @Override
                public void shareCurrentDocument() {
                    LOActivity.this.shareCurrentDocument();
                }

                @Override
                public void showDocumentTabsSheet() {
                    LOActivity.this.showDocumentTabsSheet();
                }

                @Override
                public String getDocumentTitle() {
                    return LOActivity.this.getDocumentDisplayTitle();
                }
            });
        }
        return topToolbarController;
    }

    private FindReplaceSheetController ensureFindReplaceSheetController() {
        if (findReplaceSheetController == null) {
            findReplaceSheetController = new FindReplaceSheetController(new FindReplaceSheetController.Host() {
                @Override
                public Context getContext() {
                    return LOActivity.this;
                }

                @Override
                public void runFindBridge(String js) {
                    LOActivity.this.runFindBridge(js);
                }

                @Override
                public void ensureEditModeThen(Runnable action) {
                    LOActivity.this.ensureEditModeThen(action);
                }

                @Override
                public boolean isEditModeActive() {
                    return LOActivity.this.mIsEditModeActive;
                }

                @Override
                public void onFindReplaceEditDispatched(boolean replaceAll) {
                    LOActivity.this.onFindReplaceEditDispatched(replaceAll);
                }
            });
        }
        return findReplaceSheetController;
    }

    private DocumentTabsSheetController ensureDocumentTabsSheetController() {
        if (documentTabsSheetController == null) {
            documentTabsSheetController = new DocumentTabsSheetController(new DocumentTabsSheetController.Host() {
                @Override
                public Context getContext() {
                    return LOActivity.this;
                }

                @Override
                public SharedPreferences getExplorerPrefs() {
                    return getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
                }

                @Override
                public String getCurrentDocumentUri() {
                    if (getIntent().getData() == null) {
                        return "";
                    }
                    return getIntent().getData().toString();
                }

                @Override
                public void startActivityForResult(Intent intent, int requestCode) {
                    LOActivity.this.startActivityForResult(intent, requestCode);
                }

                @Override
                public void openDocumentUri(Uri uri) {
                    LOActivity.this.openDocumentUri(uri);
                }
            });
        }
        return documentTabsSheetController;
    }

    private void showFindReplaceSheet() {
        ensureFindReplaceSheetController().show();
    }

    private void shareCurrentDocument() {
        Uri sourceUri = getIntent().getData();
        if (sourceUri == null) {
            Toast.makeText(this, "无法分享当前文档", Toast.LENGTH_SHORT).show();
            return;
        }
        DocumentShareHelper.shareDocument(this, sourceUri, mTempFile);
    }

    private void showDocumentTabsSheet() {
        ensureDocumentTabsSheetController().show();
    }

    private void runFindBridge(String js) {
        if (mWebView == null || js == null || js.isEmpty()) {
            return;
        }
        String script = "(function(){try{" + js + ";}catch(e){console.log(e);}return true;})();";
        mWebView.evaluateJavascript(script, null);
    }

    private void onFindReplaceEditDispatched(boolean replaceAll) {
        ensureTopToolbarController().recordUndoableNativeEdit(
                replaceAll ? "find_replace_all" : "find_replace_one");
    }

    private Intent buildEditIntent(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_EDIT, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setComponent(new ComponentName(getPackageName(), LOActivity.class.getName()));
        return intent;
    }

    private void openDocumentUri(Uri uri) {
        if (uri == null) {
            return;
        }
        RecentDocumentsStore.prependRecent(
                getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE),
                uri.toString());
        startActivity(buildEditIntent(uri));
        finish();
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
                public void showNativeAiOperationSheet() {
                    LOActivity.this.showNativeAiOperationSheet();
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
            mWebView.setConsumeWebViewLongClick(true);
        }
        ensureBottomToolbarController().updateEditModeState(isEditMode, reason);
        ensureTopToolbarController().updateEditModeState(isEditMode, reason);
    }

    private void hideQuickActionPanel() {
        ensureBottomToolbarController().hideQuickActionPanel();
    }

    private void requestCloseDocument() {
        runOnUiThread(() -> {
            hideKeyboard();
            if (functionPanelDialog != null) {
                functionPanelDialog.dismiss();
            }
            if (functionPanelController != null) {
                functionPanelController.dismiss();
            }
            if (!documentLoaded || !documentModified) {
                closeAfterSaveRequested = false;
                finishWithProgress();
                return;
            }

            showSaveModifiedDialog();
        });
    }

    private void showSaveModifiedDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(LOActivity.this).create();

        FrameLayout root = new FrameLayout(LOActivity.this);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dpToPx(24));
        root.setBackground(background);
        root.setPadding(dpToPx(40), dpToPx(28), dpToPx(40), dpToPx(40));

        LinearLayout content = new LinearLayout(LOActivity.this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(LOActivity.this);
        title.setText("保存文件");
        title.setTextColor(Color.parseColor("#202124"));
        title.setTextSize(34);
        title.setGravity(Gravity.CENTER);
        title.getPaint().setFakeBoldText(true);
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView close = new TextView(LOActivity.this);
        close.setText("×");
        close.setTextColor(Color.parseColor("#202124"));
        close.setTextSize(44);
        close.setGravity(Gravity.CENTER);
        close.setOnClickListener(v -> {
            closeAfterSaveRequested = false;
            dialog.dismiss();
        });
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(dpToPx(64), dpToPx(64),
                Gravity.TOP | Gravity.END);
        closeLp.topMargin = dpToPx(10);
        closeLp.rightMargin = dpToPx(18);
        root.addView(close, closeLp);

        TextView message = new TextView(LOActivity.this);
        message.setText("关闭文件前要保存修改吗");
        message.setTextColor(Color.parseColor("#333333"));
        message.setTextSize(26);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageLp.topMargin = dpToPx(86);
        content.addView(message, messageLp);

        LinearLayout buttons = new LinearLayout(LOActivity.this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsLp.topMargin = dpToPx(72);
        content.addView(buttons, buttonsLp);

        ImageButton discard = createSaveDialogImageButton(R.drawable.lolib_ic_dialog_discard);
        discard.setContentDescription("不保存");
        discard.setOnClickListener(v -> {
            dialog.dismiss();
            closeAfterSaveRequested = false;
            documentModified = false;
            finishWithProgress();
        });
        LinearLayout.LayoutParams discardLp = new LinearLayout.LayoutParams(0, dpToPx(70), 1f);
        discardLp.rightMargin = dpToPx(28);
        buttons.addView(discard, discardLp);

        ImageButton saveExit = createSaveDialogImageButton(R.drawable.lolib_ic_dialog_save_exit);
        saveExit.setContentDescription("保存并退出");
        saveExit.setOnClickListener(v -> {
            dialog.dismiss();
            saveAndCloseDocument();
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dpToPx(70), 1f);
        saveLp.leftMargin = dpToPx(28);
        buttons.addView(saveExit, saveLp);

        dialog.setView(root);
        dialog.setOnCancelListener(d -> closeAfterSaveRequested = false);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dpToPx(96),
                    dpToPx(760)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private ImageButton createSaveDialogImageButton(int resId) {
        ImageButton button = new ImageButton(LOActivity.this);
        button.setImageResource(resId);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setAdjustViewBounds(true);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private void saveAndCloseDocument() {
        if (!documentLoaded) {
            finishWithProgress();
            return;
        }
        closeAfterSaveRequested = true;
        mProgressDialog.indeterminate(R.string.exiting);
        postMobileMessageNative("save dontTerminateEdit=1 dontSaveIfUnmodified=0");
    }

    private void applyBottomToolbarImeState(boolean imeVisible, int imeInsetBottom, int navigationBarInsetBottom) {
        isImeVisibleForToolbar = imeVisible;
        bottomToolbarImeInsetPx = Math.max(0, imeInsetBottom);
        ensureBottomToolbarController().applyImeState(imeVisible, imeInsetBottom, navigationBarInsetBottom);
        if (!imeVisible && mWebView != null) {
            mWebView.setImeAllowedByUser(false);
        }
    }

    private void showFunctionPanel() {
        if (mIsEditModeActive) {
            ensureFunctionPanelController().show();
            return;
        }
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
        if (functionPanelController != null) {
            functionPanelController.dismiss();
        }
        action.run();
    }

    private FunctionPanelController ensureFunctionPanelController() {
        if (functionPanelController == null) {
            functionPanelController = new FunctionPanelController(new FunctionPanelController.Host() {
                @Override
                public Context getContext() {
                    return LOActivity.this;
                }

                @Override
                public int dpToPx(int dp) {
                    return LOActivity.this.dpToPx(dp);
                }

                @Override
                public void executeUnoCommand(String command) {
                    LOActivity.this.executeUnoCommand(command);
                }

                @Override
                public void saveDocument() {
                    postMobileMessageNative("save dontTerminateEdit=1 dontSaveIfUnmodified=1");
                }

                @Override
                public void saveDocumentAs() {
                    LOActivity.this.showSaveAsFormatDialog();
                }

                @Override
                public void exportDocumentAsPdf() {
                    LOActivity.this.downloadCurrentTextDocumentAsPdf();
                }

                @Override
                public void initiatePrint() {
                    LOActivity.this.initiatePrint();
                }

                @Override
                public void openLocalImagePickerFromWeb() {
                    LOActivity.this.openLocalImagePickerFromWeb();
                }

                @Override
                public void toastTodo(String text) {
                    LOActivity.this.toastTodo(text);
                }

                @Override
                public void showWatermarkDialog(boolean enabled) {
                    LOActivity.this.showWatermarkDialog(enabled);
                }

                @Override
                public void applyParagraphStyle(String styleName) {
                    LOActivity.this.applyParagraphStyleFromPanel(styleName);
                }

                @Override
                public void applyFont(String fontName) {
                    LOActivity.this.applyFontFromPanel(fontName);
                }

                @Override
                public void applyFontSize(String fontSizePt) {
                    LOActivity.this.applyFontSizeFromPanel(fontSizePt);
                }

                @Override
                public void insertComment() {
                    LOActivity.this.insertCommentFromPanel();
                }

                @Override
                public void fetchStyleList(FunctionPanelController.StringListCallback callback) {
                    LOActivity.this.fetchStyleListAsync(callback);
                }

                @Override
                public void fetchFontList(FunctionPanelController.StringListCallback callback) {
                    LOActivity.this.fetchFontListAsync(callback);
                }

                @Override
                public void fetchCurrentFormatting(FunctionPanelController.FormattingCallback callback) {
                    LOActivity.this.fetchCurrentFormattingAsync(callback);
                }
            });
        }
        return functionPanelController;
    }

    private void showSaveAsFormatDialog() {
        final String[] labels = new String[] { "ODT 文档", "DOCX 文档" };
        final String[] formats = new String[] { "odt", "docx" };
        new AlertDialog.Builder(this)
                .setTitle("另存为")
                .setItems(labels, (dialog, which) -> {
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
                    initiateSaveAs("format=" + formats[which] + " name=" + baseName + "." + formats[which]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showWatermarkDialog(boolean enabled) {
        if (!enabled) {
            applyDocumentWatermark("");
            return;
        }
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("请输入水印文字");
        input.setText("水印");
        input.setSelectAllOnFocus(true);

        int padding = dpToPx(20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, dpToPx(8), padding, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("水印")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    applyDocumentWatermark(text);
                })
                .show();
    }

    private void applyDocumentWatermark(String text) {
        if (mWebView == null) {
            return;
        }
        final String safeText = JSONObject.quote(text == null ? "" : text);
        final String script = "(function(){try{"
                + "if(!(window.app&&app.map&&typeof app.map.sendUnoCommand==='function')){return 'no_map';}"
                + "var args={"
                + "Text:{type:'string',value:" + safeText + "},"
                + "Font:{type:'string',value:'Noto Serif CJK SC'},"
                + "Angle:{type:'long',value:45},"
                + "Transparency:{type:'long',value:50},"
                + "Color:{type:'long',value:12632256}"
                + "};"
                + "app.map.sendUnoCommand('.uno:Watermark',args);"
                + "return 'sent';"
                + "}catch(e){if(window.console&&console.warn){console.warn('android_watermark_failed',e);}return 'err';}})();";
        runOnUiThread(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script,
                        value -> Log.i(TAG, "watermark_apply result=" + value));
            }
        });
        documentModified = true;
        nudgeSocketIfStalled("watermark_apply");
        forceVisibleTileRedrawFromAndroid("watermark_apply");
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
        requestWebViewFocusForPanelAction("uno_dispatch");
        postMobileMessage("uno " + normalizedCommand);
        nudgeSocketIfStalled("uno_dispatch");
        if (isLayoutChangingUnoCommand(normalizedCommand)) {
            forceVisibleTileRedrawFromAndroid("uno_dispatch");
        }
    }

    private boolean isLayoutChangingUnoCommand(String command) {
        if (command == null) {
            return false;
        }
        return command.startsWith(".uno:Delete")
                || command.startsWith(".uno:ResetAttributes")
                || command.startsWith(".uno:StyleApply")
                || command.startsWith(".uno:CharFontName")
                || command.startsWith(".uno:FontHeight")
                || command.startsWith(".uno:Grow")
                || command.startsWith(".uno:Shrink")
                || command.startsWith(".uno:PageLRMargin")
                || command.startsWith(".uno:PageULMargin")
                || command.startsWith(".uno:AttributePageSize")
                || command.startsWith(".uno:Orientation")
                || command.startsWith(".uno:LeftPara")
                || command.startsWith(".uno:CenterPara")
                || command.startsWith(".uno:RightPara")
                || command.startsWith(".uno:JustifyPara")
                || command.startsWith(".uno:DefaultBullet")
                || command.startsWith(".uno:DefaultNumbering")
                || command.startsWith(".uno:TrackChanges")
                || command.startsWith(".uno:TrackChangesInAllViews")
                || command.startsWith(".uno:TrackChangesInThisView")
                || command.startsWith(".uno:ShowTrackedChanges")
                || command.startsWith(".uno:AcceptTrackedChange")
                || command.startsWith(".uno:RejectTrackedChange")
                || command.startsWith(".uno:AcceptTrackedChanges")
                || command.startsWith(".uno:RejectTrackedChanges")
                || command.startsWith(".uno:InsertTable")
                || command.startsWith(".uno:InsertPageNumberField")
                || command.startsWith(".uno:InsertPagebreak")
                || command.startsWith(".uno:BasicShapes");
    }

    private void requestWebViewFocusForPanelAction(String reason) {
        if (mWebView != null) {
            mWebView.requestFocus();
            Log.i(TAG, "function_panel_webview_focus reason=" + reason);
        }
    }

    private String escapeForJsString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "");
    }

    private void runWebJs(String script, ValueCallback<String> callback) {
        if (mWebView == null) {
            if (callback != null) {
                callback.onReceiveValue(null);
            }
            return;
        }
        requestWebViewFocusForPanelAction("run_web_js");
        mWebView.evaluateJavascript(script, callback);
    }

    void applyParagraphStyleFromPanel(String styleName) {
        if (styleName == null || styleName.trim().isEmpty()) {
            return;
        }
        final String trimmedStyle = styleName.trim();
        Log.i(TAG, "function_apply_style style=" + trimmedStyle);
        if (trimmedStyle.startsWith(".uno:")) {
            postMobileMessage("uno " + trimmedStyle);
            nudgeSocketIfStalled("function_apply_uno_style");
            forceVisibleTileRedrawFromAndroid("function_apply_uno_style");
            return;
        }
        final String escaped = escapeForJsString(trimmedStyle);
        runWebJs("(function(){try{"
                + "if(window.app&&app.map&&typeof app.map.applyStyle==='function'){"
                + "app.map.applyStyle('" + escaped + "','ParagraphStyles');"
                + "if(typeof app.map.focus==='function'){app.map.focus();}"
                + "return 'ok';}"
                + "}catch(e){if(window.console&&console.warn){console.warn('function_apply_style_failed',e);}}"
                + "return 'fail';})();", value -> Log.i(TAG, "function_apply_style_result=" + value));
        nudgeSocketIfStalled("function_apply_style");
        forceVisibleTileRedrawFromAndroid("function_apply_style");
    }

    void applyFontFromPanel(String fontName) {
        if (fontName == null || fontName.trim().isEmpty()) {
            return;
        }
        final String escaped = escapeForJsString(fontName.trim());
        Log.i(TAG, "function_apply_font font=" + fontName);
        runWebJs("(function(){try{"
                + "if(window.app&&app.map&&typeof app.map.applyFont==='function'){"
                + "app.map.applyFont('" + escaped + "');"
                + "if(typeof app.map.focus==='function'){app.map.focus();}"
                + "return 'ok';}"
                + "}catch(e){if(window.console&&console.warn){console.warn('function_apply_font_failed',e);}}"
                + "return 'fail';})();", value -> Log.i(TAG, "function_apply_font_result=" + value));
        nudgeSocketIfStalled("function_apply_font");
        forceVisibleTileRedrawFromAndroid("function_apply_font");
    }

    void applyFontSizeFromPanel(String fontSizePt) {
        if (fontSizePt == null || fontSizePt.trim().isEmpty()) {
            return;
        }
        final String escaped = escapeForJsString(fontSizePt.trim());
        Log.i(TAG, "function_apply_font_size pt=" + fontSizePt);
        runWebJs("(function(){try{"
                + "if(window.app&&app.map&&typeof app.map.applyFontSize==='function'){"
                + "app.map.applyFontSize('" + escaped + "');"
                + "if(typeof app.map.focus==='function'){app.map.focus();}"
                + "return 'ok';}"
                + "}catch(e){if(window.console&&console.warn){console.warn('function_apply_font_size_failed',e);}}"
                + "return 'fail';})();", value -> Log.i(TAG, "function_apply_font_size_result=" + value));
        nudgeSocketIfStalled("function_apply_font_size");
        forceVisibleTileRedrawFromAndroid("function_apply_font_size");
    }

    void insertCommentFromPanel() {
        Log.i(TAG, "function_insert_comment");
        runWebJs("(function(){try{"
                + "if(window.app&&app.map&&typeof app.map.insertComment==='function'){"
                + "app.map.insertComment();"
                + "if(typeof app.map.focus==='function'){app.map.focus();}"
                + "return 'map';}"
                + "if(window.app&&app.dispatcher&&typeof app.dispatcher.dispatch==='function'){"
                + "app.dispatcher.dispatch('insertcomment');"
                + "return 'dispatcher';}"
                + "}catch(e){if(window.console&&console.warn){console.warn('function_insert_comment_failed',e);}}"
                + "return 'fail';})();", value -> Log.i(TAG, "function_insert_comment_result=" + value));
        nudgeSocketIfStalled("function_insert_comment");
    }

    void fetchStyleListAsync(FunctionPanelController.StringListCallback callback) {
        runWebJs("(function(){try{"
                + "if(!window.app||!app.map||typeof app.map.getToolbarCommandValues!=='function'){return '[]';}"
                + "var cv=app.map.getToolbarCommandValues('.uno:StyleApply');"
                + "if(!cv){return '[]';}"
                + "var out=[];"
                + "var mappings=(window.L&&window.L.Styles&&window.L.Styles.styleMappings)?window.L.Styles.styleMappings:{};"
                + "function localize(style){"
                + "if(mappings[style]){try{return mappings[style].toLocaleString();}catch(e){return style;}}"
                + "if(style.indexOf('outline')===0){return 'Outline '+style.split('outline')[1];}"
                + "return style;}"
                + "if(cv.Commands&&cv.Commands.length){"
                + "cv.Commands.forEach(function(cmd){"
                + "var text=cmd.text;"
                + "if(mappings[cmd.text]){try{text=mappings[cmd.text].toLocaleString();}catch(e){}}"
                + "out.push({id:cmd.id,label:text});"
                + "});}"
                + "if(app.map.getDocType&&app.map.getDocType()==='text'&&cv.ParagraphStyles){"
                + "var top=cv.ParagraphStyles.slice(0,7);"
                + "var more=cv.ParagraphStyles.slice(7);"
                + "top.forEach(function(s){out.push({id:s,label:localize(s)});});"
                + "more.forEach(function(s){out.push({id:s,label:localize(s)});});"
                + "}"
                + "return JSON.stringify(out);"
                + "}catch(e){return '[]';}})();", value -> {
            List<String> labels = new ArrayList<>();
            List<String> values = new ArrayList<>();
            parseStyleListJson(value, labels, values);
            Log.i(TAG, "function_fetch_styles_count=" + labels.size());
            if (callback != null) {
                runOnUiThread(() -> callback.onResult(labels, values));
            }
        });
    }

    private void parseStyleListJson(String json, List<String> labels, List<String> values) {
        if (json == null || json.isEmpty() || "null".equals(json)) {
            return;
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            trimmed = trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        try {
            JSONArray array = new JSONArray(trimmed);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String id = obj.optString("id", "");
                String label = obj.optString("label", id);
                if (!id.isEmpty()) {
                    values.add(id);
                    labels.add(label.isEmpty() ? id : label);
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "function_fetch_styles_parse_failed", e);
        }
    }

    void fetchFontListAsync(FunctionPanelController.StringListCallback callback) {
        runWebJs("(function(){try{"
                + "if(!window.app||!app.map||typeof app.map.getToolbarCommandValues!=='function'){return '[]';}"
                + "var cv=app.map.getToolbarCommandValues('.uno:CharFontName');"
                + "if(!cv||typeof cv!=='object'){return '[]';}"
                + "var fonts=Object.keys(cv).filter(function(k){return !!k;});"
                + "return JSON.stringify(fonts);"
                + "}catch(e){return '[]';}})();", value -> {
            List<String> labels = new ArrayList<>();
            List<String> values = new ArrayList<>();
            parseStringArrayJson(value, labels);
            values.addAll(labels);
            Log.i(TAG, "function_fetch_fonts_count=" + labels.size());
            if (callback != null) {
                runOnUiThread(() -> callback.onResult(labels, values));
            }
        });
    }

    private void parseStringArrayJson(String json, List<String> out) {
        if (json == null || json.isEmpty() || "null".equals(json)) {
            return;
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            trimmed = trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        try {
            JSONArray array = new JSONArray(trimmed);
            for (int i = 0; i < array.length(); i++) {
                String item = array.optString(i, "");
                if (!item.isEmpty()) {
                    out.add(item);
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "function_fetch_fonts_parse_failed", e);
        }
    }

    void fetchCurrentFormattingAsync(FunctionPanelController.FormattingCallback callback) {
        runWebJs("(function(){try{"
                + "if(!window.app||!app.map){return '{}';}"
                + "var sch=app.map.stateChangeHandler||app.map['stateChangeHandler'];"
                + "function state(cmd){try{return sch&&typeof sch.getItemValue==='function'?sch.getItemValue(cmd):'';}catch(e){return '';}}"
                + "function scalar(v){"
                + "if(v===undefined||v===null){return '';}"
                + "if(typeof v==='string'){return v;}"
                + "if(typeof v==='number'){return String(v);}"
                + "if(typeof v==='object'){"
                + "if(v.value!==undefined){return String(v.value);}"
                + "if(v.text!==undefined){return String(v.text);}"
                + "if(v.family!==undefined){return String(v.family);}"
                + "}"
                + "return String(v);}"
                + "function active(cmd){var v=state(cmd);"
                + "if(v===true){return true;}"
                + "if(typeof v==='string'){v=v.toLowerCase();return v==='true'||v==='1'||v==='checked'||v==='selected';}"
                + "if(typeof v==='number'){return v!==0;}"
                + "if(v&&typeof v==='object'){return v.value===true||v.checked===true||v.selected===true||v.state==='checked';}"
                + "return false;}"
                + "var style=scalar(state('.uno:StyleApply'));"
                + "var font=scalar(state('.uno:CharFontName'));"
                + "if(!font&&typeof app.map._getCurrentFontName==='function'){font=app.map._getCurrentFontName()||'';}"
                + "var size=scalar(state('.uno:FontHeight'));"
                + "var align='';"
                + "if(active('.uno:LeftPara')){align='para_left';}"
                + "else if(active('.uno:CenterPara')){align='para_center';}"
                + "else if(active('.uno:RightPara')){align='para_right';}"
                + "else if(active('.uno:JustifyPara')){align='para_justify';}"
                + "font=font.split(';')[0].trim();"
                + "size=size.replace('pt','').trim();"
                + "return JSON.stringify({style:style,font:font,size:size,align:align});"
                + "}catch(e){return '{}';}})();", value -> {
            String style = "";
            String font = "";
            String size = "";
            String align = "";
            String trimmed = value == null ? "" : value.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
                trimmed = trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
            }
            try {
                JSONObject obj = new JSONObject(trimmed);
                style = obj.optString("style", "");
                font = obj.optString("font", "");
                size = obj.optString("size", "");
                align = obj.optString("align", "");
            } catch (JSONException e) {
                Log.w(TAG, "function_current_format_parse_failed", e);
            }
            Log.i(TAG, "function_current_format style=" + style + " font=" + font
                    + " size=" + size + " align=" + align);
            if (callback != null) {
                final String finalStyle = style;
                final String finalFont = font;
                final String finalSize = size;
                final String finalAlign = align;
                runOnUiThread(() -> callback.onResult(finalStyle, finalFont, finalSize, finalAlign));
            }
        });
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
        SelectionMenuController menuController = ensureSelectionMenuController();
        boolean selectionMenuVisible = menuController != null && menuController.isVisible();
        Log.i(TAG, "focus_ime_start selectionMenuVisible=" + selectionMenuVisible
                + " webViewFocused=" + mWebView.isFocused()
                + " imeAllowed=" + mWebView.isImeAllowedByUser());
        // 呼出键盘前先关闭选区浮层，释放全屏 overlay 的焦点/触摸拦截
        if (selectionMenuVisible) {
            Log.i(TAG, "focus_ime_hide_selection_menu");
            menuController.hide();
        }
        mWebView.setImeAllowedByUser(true);
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
        mWebView.setImeAllowedByUser(false);
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
        runOnUiThread(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script,
                        value -> Log.d(TAG, "socket_nudge reason=" + reason + " result=" + value));
            }
        });
    }

    private void forceVisibleTileRedrawFromAndroid(String reason) {
        if (mWebView == null) {
            return;
        }
        final String escapedReason = escapeForJsString(reason == null ? "android_panel_action" : reason);
        final String script = "(function(){try{"
                + "var reason='" + escapedReason + "';"
                + "var tm=window.TileManager||(typeof TileManager!=='undefined'?TileManager:null);"
                + "if(!(window.ThisIsTheAndroidApp&&window.app&&app.map)){return 'skip';}"
                + "setTimeout(function(){try{"
                + "if(window.app&&app.map&&app.map._docLayer&&typeof app.map._docLayer._resetClientVisArea==='function'){app.map._docLayer._resetClientVisArea();}"
                + "var currentTm=window.TileManager||(typeof TileManager!=='undefined'?TileManager:null);"
                + "if(currentTm&&typeof currentTm.refreshTilesInBackground==='function'){currentTm.refreshTilesInBackground();}"
                + "if(currentTm&&typeof currentTm.update==='function'){currentTm.update();}"
                + "if(app.console&&typeof app.console.debug==='function'){app.console.debug('android visible tile refresh reason='+reason);}"
                + "}catch(e){if(window.console&&console.warn){console.warn('android_visible_tile_refresh_deferred_failed',e);}}},250);"
                + "return 'scheduled';"
                + "}catch(e){if(window.console&&console.warn){console.warn('android_visible_tile_redraw_failed',e);}return 'err';}})();";
        runOnUiThread(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script,
                        value -> Log.i(TAG, "visible_tile_redraw reason=" + reason + " result=" + value));
            }
        });
    }

    private void recoverVisibleTilesAfterEditMode(String reason) {
        if (mWebView == null) {
            return;
        }
        final String escapedReason = escapeForJsString(reason == null ? "editmode" : reason);
        final String script = "(function(){try{"
                + "var reason='" + escapedReason + "';"
                + "var maxDeferRetries=24;"
                + "function isReconnecting(){"
                + "return!!(window.app&&app.socket&&typeof app.socket.isTemporarilyReconnecting==='function'&&app.socket.isTemporarilyReconnecting());"
                + "}"
                + "function hasValidMapSize(){var s=app.map&&typeof app.map.getSize==='function'?app.map.getSize():null;return!!(s&&s.x>0&&s.y>0);}"
                + "function applyRecover(tag,hard){"
                + "var tm=window.TileManager||(typeof TileManager!=='undefined'?TileManager:null);"
                + "if(!hasValidMapSize()){if(app.console&&typeof app.console.debug==='function'){app.console.debug('android editmode tile recover skipped invalid map size tag='+tag+' reason='+reason);}return;}"
                // Re-assert dark/light theme to core BEFORE requesting tiles.
                // On the docalreadyoaded reconnect path, Socket.ts enqueues ChangeTheme AFTER
                // the tile requests, so tiles can render with the stale theme (white text on
                // white bg in dark mode). Calling the UIManager theme trio here — same JS turn,
                // before _requestNewTiles — guarantees core switches theme first (ChangeTheme is
                // enqueued ahead of the tile request). refreshTheme() is NOT used because it is a
                // no-op here (window.initializedUI is never defined in this build). Throttled via
                // a window-level timestamp so the 4 retry ticks + nearby callers don't spam core.
                + "try{var _ts=Date.now();if(!window.__coolThemeReassertTs||_ts-window.__coolThemeReassertTs>1200){window.__coolThemeReassertTs=_ts;if(window.app&&app.map&&app.map.uiManager&&window.prefs&&typeof window.prefs.getBoolean==='function'){var _dt=window.prefs.getBoolean('darkTheme');if(typeof app.map.uiManager.activateDarkModeInCore==='function'){app.map.uiManager.activateDarkModeInCore(_dt);}if(typeof app.map.uiManager.applyInvert==='function'){app.map.uiManager.applyInvert();}if(typeof app.map.uiManager.setCanvasColorAfterModeChange==='function'){app.map.uiManager.setCanvasColorAfterModeChange();}if(app.console&&typeof app.console.debug==='function'){app.console.debug('android theme reassert dark='+_dt+' tag='+tag+' reason='+reason);}}}}catch(_te){}"
                + "if(typeof app.map.invalidateSize==='function'){app.map.invalidateSize(false);}"
                + "if(app.map._docLayer&&typeof app.map._docLayer._resetClientVisArea==='function'){app.map._docLayer._resetClientVisArea();}"
                + "if(app.map._docLayer&&typeof app.map._docLayer._sendClientZoom==='function'){app.map._docLayer._sendClientZoom(true);}"
                + "if(app.map._docLayer&&typeof app.map._docLayer._requestNewTiles==='function'){app.map._docLayer._requestNewTiles();}"
                + "if(tm&&typeof tm.refreshTilesInBackground==='function'){tm.refreshTilesInBackground();}"
                + "if(tm&&typeof tm.update==='function'){tm.update();}"
                + "if(hard&&tm&&typeof tm.redraw==='function'){tm.redraw();}"
                + "if(app.console&&typeof app.console.debug==='function'){app.console.debug('android editmode tile recover applied tag='+tag+' hard='+hard+' reason='+reason);}"
                + "}"
                + "function recoverWithReconnectGuard(tag,hard,deferCount){try{"
                + "if(!(window.ThisIsTheAndroidApp&&window.app&&app.map)){return;}"
                + "if(isReconnecting()){"
                + "if(deferCount<maxDeferRetries){"
                + "if(app.console&&typeof app.console.debug==='function'){app.console.debug('android editmode tile recover deferred reconnect tag='+tag+' defer='+deferCount+' reason='+reason);}"
                + "setTimeout(function(){recoverWithReconnectGuard(tag,hard,deferCount+1);},120);"
                + "}else if(app.console&&typeof app.console.warn==='function'){app.console.warn('android editmode tile recover reconnect limit tag='+tag+' reason='+reason);}"
                + "return;"
                + "}"
                + "applyRecover(tag,hard);"
                + "}catch(e){if(window.console&&console.warn){console.warn('android_editmode_tile_recover_failed',tag,e);}}}"
                + "setTimeout(function(){recoverWithReconnectGuard('soon',false,0);},120);"
                + "setTimeout(function(){recoverWithReconnectGuard('mid',false,0);},420);"
                + "setTimeout(function(){recoverWithReconnectGuard('late',false,0);},900);"
                + "setTimeout(function(){recoverWithReconnectGuard('final',true,0);},1700);"
                + "return 'scheduled';"
                + "}catch(e){if(window.console&&console.warn){console.warn('android_editmode_tile_recover_failed',e);}return 'err';}})();";
        runOnUiThread(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script,
                        value -> Log.d(TAG, "editmode_tile_recover reason=" + reason + " result=" + value));
            }
            nudgeSocketIfStalled("editmode_tile_recover");
        });
    }

    private void recoverVisibleTilesAfterPreviewSelection(String reason) {
        if (mWebView == null || mIsEditModeActive) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastPreviewSelectionTileRecoverAt < PREVIEW_SELECTION_TILE_RECOVER_THROTTLE_MS) {
            return;
        }
        lastPreviewSelectionTileRecoverAt = now;
        final String escapedReason = escapeForJsString(reason == null ? "preview_selection" : reason);
        final String script = "(function(){try{"
                + "var reason='" + escapedReason + "';"
                + "var maxDeferRetries=24;"
                + "function isReconnecting(){return!!(window.app&&app.socket&&typeof app.socket.isTemporarilyReconnecting==='function'&&app.socket.isTemporarilyReconnecting());}"
                + "function isPreviewMode(){return!!(window.app&&app.map&&typeof app.map.isReadOnlyMode==='function'&&app.map.isReadOnlyMode()&&!(typeof app.map.isEditMode==='function'&&app.map.isEditMode()));}"
                + "function hasValidCanvas(){var c=document.getElementById('canvas-container');if(!c){return false;}var r=c.getBoundingClientRect();return r.width>0&&r.height>0;}"
                + "function applyRecover(tag,hard){"
                + "var tm=window.TileManager||(typeof TileManager!=='undefined'?TileManager:null);"
                + "if(!isPreviewMode()){if(app.console&&typeof app.console.debug==='function'){app.console.debug('android preview tile recover skipped non-preview tag='+tag+' reason='+reason);}return;}"
                + "if(!hasValidCanvas()){if(app.console&&typeof app.console.warn==='function'){app.console.warn('android preview tile recover skipped invalid canvas tag='+tag+' reason='+reason);}return;}"
                + "if(typeof app.map.invalidateSize==='function'){app.map.invalidateSize(false);}"
                + "if(app.map._docLayer&&typeof app.map._docLayer._resetClientVisArea==='function'){app.map._docLayer._resetClientVisArea();}"
                + "if(app.map._docLayer&&typeof app.map._docLayer._requestNewTiles==='function'){app.map._docLayer._requestNewTiles();}"
                + "if(tm&&typeof tm.update==='function'){tm.update();}"
                + "if(hard&&tm&&typeof tm.redraw==='function'){tm.redraw();}"
                + "if(app.console&&typeof app.console.debug==='function'){app.console.debug('android preview tile recover applied tag='+tag+' hard='+hard+' reason='+reason);}"
                + "}"
                + "function recoverWithReconnectGuard(tag,hard,deferCount){try{"
                + "if(!(window.ThisIsTheAndroidApp&&window.app&&app.map)){return;}"
                + "if(!isPreviewMode()){if(app.console&&typeof app.console.debug==='function'){app.console.debug('android preview tile recover cancelled non-preview tag='+tag+' reason='+reason);}return;}"
                + "if(isReconnecting()){"
                + "if(deferCount<maxDeferRetries){"
                + "if(app.console&&typeof app.console.debug==='function'){app.console.debug('android preview tile recover deferred reconnect tag='+tag+' defer='+deferCount+' reason='+reason);}"
                + "setTimeout(function(){recoverWithReconnectGuard(tag,hard,deferCount+1);},120);"
                + "}else if(app.console&&typeof app.console.warn==='function'){app.console.warn('android preview tile recover reconnect limit tag='+tag+' reason='+reason);}"
                + "return;"
                + "}"
                + "applyRecover(tag,hard);"
                + "}catch(e){if(window.console&&console.warn){console.warn('android_preview_tile_recover_failed',tag,e);}}}"
                + "setTimeout(function(){recoverWithReconnectGuard('soon',false,0);},120);"
                + "setTimeout(function(){recoverWithReconnectGuard('mid',false,0);},420);"
                + "setTimeout(function(){recoverWithReconnectGuard('late',false,0);},900);"
                + "setTimeout(function(){recoverWithReconnectGuard('final',true,0);},1700);"
                + "return 'scheduled';"
                + "}catch(e){if(window.console&&console.warn){console.warn('android_preview_tile_recover_failed',e);}return 'err';}})();";
        runOnUiThread(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script,
                        value -> Log.i(TAG, "preview_tile_recover reason=" + reason + " result=" + value));
            }
        });
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
        aiProgressBar = panel.findViewById(R.id.ai_progress_bar);
        aiProgressLabel = panel.findViewById(R.id.ai_progress_label);
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
            aiProgressBar = null;
            aiProgressLabel = null;
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

    private void showNativeAiOperationSheet() {
        Log.i(TAG, "ai_op_show_entry isFinishing=" + isFinishing() + " isEditMode=" + mIsEditModeActive);
        if (isFinishing()) {
            return;
        }
        if (aiOperationSheet != null && aiOperationSheet.isShowing()) {
            return;
        }
        View panel = getLayoutInflater().inflate(R.layout.lolib_sheet_ai_operations, null);
        aiOperationSheetPanel = panel;
        View closeButton = panel.findViewById(R.id.ai_op_close);
        aiOpSelectionHint = panel.findViewById(R.id.ai_op_selection_hint);
        View cancelButton = panel.findViewById(R.id.ai_op_cancel);

        // Bind operation buttons with their modes
        // 6 个有 AI 链路的 operate mode 按钮（依赖选区）
        bindAiOpButton(panel, R.id.ai_op_continue_write, AiChatCoordinator.MODE_CONTINUE);
        bindAiOpButton(panel, R.id.ai_op_expand, AiChatCoordinator.MODE_EXPAND);
        bindAiOpButton(panel, R.id.ai_op_polish, AiChatCoordinator.MODE_POLISH);
        bindAiOpButton(panel, R.id.ai_op_condense, AiChatCoordinator.MODE_CONDENSE);
        bindAiOpButton(panel, R.id.ai_op_rewrite, AiChatCoordinator.MODE_REWRITE);
        bindAiOpButton(panel, R.id.ai_op_translate, AiChatCoordinator.MODE_TRANSLATE);

        // 5 个新功能占位按钮（暂未接入 AI 链路，点击弹 toast）
        // 生成大纲：弹出大纲生成对话框（入口 B，使用全文）
        LinearLayout aiOpOutline = panel.findViewById(R.id.ai_op_outline);
        if (aiOpOutline != null) {
            aiOpOutline.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_outline_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                showOutlineDialog(null);
            });
        }
        LinearLayout aiOpArticle = panel.findViewById(R.id.ai_op_article_generate);
        if (aiOpArticle != null) {
            aiOpArticle.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_article_generate_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                showArticleGenerateDialog();
            });
        }
        // 文字提取：系统选图/相机 + 视觉模型 OCR
        LinearLayout aiOpTextExtract = panel.findViewById(R.id.ai_op_text_extract);
        if (aiOpTextExtract != null) {
            aiOpTextExtract.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_text_extract_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                showTextExtractDialog();
            });
        }
        // AI图片：图片生成大模型
        LinearLayout aiOpImageGenerate = panel.findViewById(R.id.ai_op_image_generate);
        if (aiOpImageGenerate != null) {
            aiOpImageGenerate.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_image_generate_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                showAiImageDialog();
            });
        }

        // 格式批量处理：需选中文本，本地正则处理
        LinearLayout aiOpFormatBatch = panel.findViewById(R.id.ai_op_format_batch);
        if (aiOpFormatBatch != null) {
            aiOpFormatBatch.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_format_batch_clicked");
                String selection = aiOpPendingSelection == null ? "" : aiOpPendingSelection;
                if (selection.isEmpty()) {
                    Toast.makeText(LOActivity.this, "请先在文档中选择文本", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                showFormatBatchDialog(selection);
            });
        }

        // AI排版按钮：点击后弹出类型选择 BottomSheet
        LinearLayout aiOpTypeset = panel.findViewById(R.id.ai_op_typeset);
        if (aiOpTypeset != null) {
            aiOpTypeset.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_typeset_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                showTypesetSelectSheet();
            });
        }

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> cancelAiOperation());
        }

        if (closeButton != null) {
            closeButton.setOnClickListener(v -> {
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
            });
        }

        aiOperationSheet = new BottomSheetDialog(this);
        aiOperationSheet.setContentView(panel);
        aiOperationSheet.setCanceledOnTouchOutside(true);
        aiOperationSheet.setOnDismissListener(dialog -> {
            Log.i(TAG, "ai_op_sheet_dismissed");
            aiOperationSheet = null;
            aiOperationSheetPanel = null;
            aiOpSelectionHint = null;
            aiOpPendingSelection = "";
        });
        aiOperationSheet.setOnCancelListener(dialog -> Log.i(TAG, "ai_op_sheet_canceled"));
        aiOperationSheet.setOnShowListener(dialog -> Log.i(TAG, "ai_op_sheet_onshow"));

        // Apply same configuration as AI panel
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        aiPanelController.configureBottomSheet(aiOperationSheet,
                screenHeight, screenWidth,
                getResources().getConfiguration().orientation);

        aiOperationSheet.show();
        Log.i(TAG, "ai_op_sheet_shown");

        // 隐藏 IME：BottomSheet 弹起时 IME 可能仍处于显示态（编辑模式下尤其）。
        // 注意：不能 requestFocus() —— 那会反过来触发 IME 弹起。直接 hide 即可。
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }

        // Read selection from JS and update hint
        getSelectedTextFromJs(selection -> {
            aiOpPendingSelection = selection;
            runOnUiThread(() -> {
                if (aiOpSelectionHint != null) {
                    if (selection.isEmpty()) {
                        aiOpSelectionHint.setText("请先在文档中选择文本");
                        aiOpSelectionHint.setTextColor(Color.parseColor("#E53935"));
                        setAiOpButtonsEnabled(panel, false);
                    } else {
                        aiOpSelectionHint.setText("已选中 " + selection.length() + " 字");
                        aiOpSelectionHint.setTextColor(Color.parseColor("#43A047"));
                        setAiOpButtonsEnabled(panel, true);
                    }
                }
            });
        });
    }

    private void bindAiOpButton(View panel, int viewId, String mode) {
        View button = panel.findViewById(viewId);
        if (button != null) {
            button.setOnClickListener(v -> {
                if (aiOpPendingSelection == null || aiOpPendingSelection.isEmpty()) {
                    return;
                }
                // 弹窗流程：直接打开对话框，不走 operate-mode 加载条
                if (AiChatCoordinator.MODE_EXPAND.equals(mode)
                        || AiChatCoordinator.MODE_CONDENSE.equals(mode)
                        || AiChatCoordinator.MODE_POLISH.equals(mode)
                        || AiChatCoordinator.MODE_TRANSLATE.equals(mode)
                        || AiChatCoordinator.MODE_REWRITE.equals(mode)) {
                    runAiOperation(mode);
                    return;
                }
                // Show loading bar
                View loadingBar = panel.findViewById(R.id.ai_op_loading_bar);
                if (loadingBar != null) {
                    loadingBar.setVisibility(View.VISIBLE);
                }
                // Disable all buttons during request
                setAiOpButtonsEnabled(panel, false);
                runAiOperation(mode);
            });
        }
    }

    private void setAiOpButtonsEnabled(View panel, boolean enabled) {
        // 仅控制 6 个 operate mode 按钮（依赖选区）。
        // 5 个新功能占位按钮不依赖选区，保留常亮常可点。
        int[] buttonIds = {
                R.id.ai_op_continue_write, R.id.ai_op_expand, R.id.ai_op_polish,
                R.id.ai_op_condense, R.id.ai_op_rewrite,
                R.id.ai_op_translate
        };
        for (int id : buttonIds) {
            View button = panel.findViewById(id);
            if (button != null) {
                button.setEnabled(enabled);
                button.setAlpha(enabled ? 1.0f : 0.4f);
            }
        }
    }

    /**
     * 新功能按钮占位绑定：点击后弹 toast「功能开发中」，不触发 AI 请求。
     * 等对应 AiChatCoordinator.MODE_* 与 buildOperateMessages prompt 落地后再替换为 bindAiOpButton。
     */
    private void bindAiOpPlaceholderButton(View panel, int viewId, String featureLabel) {
        View button = panel.findViewById(viewId);
        if (button != null) {
            button.setOnClickListener(v ->
                    Toast.makeText(LOActivity.this,
                            "「" + featureLabel + "」功能开发中",
                            Toast.LENGTH_SHORT).show());
        }
    }

    // ==================== AI排版相关方法 ====================

    /**
     * 显示AI排版类型选择 BottomSheet
     */
    private void showTypesetSelectSheet() {
        Log.i(TAG, "ai_typeset_select_show");
        if (isFinishing()) {
            return;
        }
        typesetSelectSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.lolib_sheet_typeset_select, null);
        typesetSelectSheet.setContentView(sheetView);

        // 配置 BottomSheet
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        aiPanelController.configureBottomSheet(typesetSelectSheet,
                screenHeight, screenWidth,
                getResources().getConfiguration().orientation);

        // 关闭按钮
        sheetView.findViewById(R.id.typeset_select_close).setOnClickListener(v -> {
            Log.i(TAG, "ai_typeset_select_dismissed");
            typesetSelectSheet.dismiss();
        });

        // 4 个类型选项
        sheetView.findViewById(R.id.typeset_type_paper).setOnClickListener(v -> startTypeset("paper"));
        sheetView.findViewById(R.id.typeset_type_gov).setOnClickListener(v -> startTypeset("gov"));
        sheetView.findViewById(R.id.typeset_type_contract).setOnClickListener(v -> startTypeset("contract"));
        sheetView.findViewById(R.id.typeset_type_general).setOnClickListener(v -> startTypeset("general"));

        typesetSelectSheet.setOnDismissListener(dialog -> {
            Log.i(TAG, "ai_typeset_select_dismissed");
            typesetSelectSheet = null;
        });

        typesetSelectSheet.show();
        Log.i(TAG, "ai_typeset_select_shown");
    }

    /**
     * 原生全文提取（AI排版用）——替代旧剪贴板链路（SelectAll→Copy→剪贴板轮询）。
     * 用 JNI postUnoCommand 发 SelectAll（底层逻辑，非剪贴板），再轮询 JNI getTextSelection
     * 直到非空且长度稳定（core 异步应用 SelectAll）。须在后台线程调用（LOK JNI 子线程安全）。
     * 返回全文纯文本，失败返回 null/空。
     */
    private String extractFullTextNative(String tag) {
        try {
            postUnoCommand(".uno:SelectAll", "{}", false);
            String prev = null;
            int stableCount = 0;
            long deadline = System.currentTimeMillis() + 2500;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(120);
                } catch (InterruptedException ie) {
                    break;
                }
                String cur = getTextSelection("text/plain;charset=utf-8");
                if (cur != null && !cur.isEmpty()) {
                    if (prev != null && prev.length() == cur.length()) {
                        stableCount++;
                        if (stableCount >= 1) {
                            return cur;
                        }
                    } else {
                        stableCount = 0;
                    }
                    prev = cur;
                }
            }
            return prev;
        } catch (Exception e) {
            Log.w(TAG, "extractFullTextNative_failed tag=" + tag, e);
            return null;
        }
    }

    /**
     * 诊断：paste 一段测试 HTML，实测 Writer HTML Import Filter 保留哪些属性（color / hr /
     * font size / align / table / CSS）。从 Chrome 远程调试控制台触发（须编辑模式）：
     *   window.postMobileMessage("DEBUG_HTML_PROBE")
     * 结果看真机 Writer 文档里哪些格式生效——决定 AI排版可用 HTML 词汇（尤其 color / 党政公文红线）。
     */
    private void probeHtmlFilterCapability() {
        if (mWebView == null) {
            return;
        }
        final String html =
                "<h1>HTML Filter 能力实测</h1>"
                + "<p>普通段落黑字 (default)</p>"
                + "<p><font color=\"red\">红色字 font color=red（若红→color 支持）</font></p>"
                + "<p><font color=\"#0000FF\">蓝色字 font color=#0000FF</font></p>"
                + "<p><font size=\"6\">大字号 font size=6</font></p>"
                + "<p><u>下划线 u</u> <strong>加粗 strong</strong> <em>斜体 em</em></p>"
                + "<div align=\"center\">居中 div align=center</div>"
                + "<hr>"
                + "<p>↑ 上方应为横线 hr（默认色）</p>"
                + "<p><font color=\"red\">━━━━━━━━━━━━━━━━━━━━━━</font></p>"
                + "<p>↑ 红色字模拟红线（若 color 支持→可见红线，是党政公文红线 fallback 方案）</p>"
                + "<table border=\"1\"><tr><th>表头A</th><th>表头B</th></tr>"
                + "<tr><td>单元格1</td><td>单元格2</td></tr></table>"
                + "<div style=\"color:green\">CSS 绿色字 div style=color（若绿→CSS 也支持；预期不支持）</div>";
        Log.i(TAG, "htmlfilter_probe start bytes=" + html.length());
        new Thread(() -> {
            try {
                byte[] htmlBytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                paste("text/html", htmlBytes);
                Log.i(TAG, "htmlfilter_probe pasted bytes=" + htmlBytes.length
                        + " — 查看文档：红字?hr?红线?字号?表格?");
                runOnUiThread(() -> Toast.makeText(this,
                        "已 paste 测试 HTML，查看文档里哪些格式生效", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Log.w(TAG, "htmlfilter_probe_failed", e);
            }
        }).start();
    }

    /**
     * 启动AI排版：提取全文 → 发送AI请求
     * @param typesetType 排版类型：paper | gov | contract | general
     */
    private void startTypeset(String typesetType) {
        Log.i(TAG, "ai_typeset_start type=" + typesetType);
        if (typesetSelectSheet != null) {
            typesetSelectSheet.dismiss();
        }
        pendingTypesetType = typesetType;

        // 显示 loading 提示
        toastTodo("正在提取文档全文...");

        // 原生全文提取（SelectAll → JNI getTextSelection，弃用旧剪贴板链路）
        new Thread(() -> {
            String docText = extractFullTextNative("typeset-" + typesetType);
            if (docText == null || docText.isEmpty()) {
                runOnUiThread(() -> {
                    toastTodo("文档全文提取失败，请稍后重试");
                    Log.w(TAG, "ai_typeset_doc_extract_failed");
                });
                return;
            }
            Log.i(TAG, "ai_typeset_doc_extracted chars=" + docText.length());

            runOnUiThread(() -> {
                toastTodo("正在排版...");
                try {
                    JSONObject request = new JSONObject();
                    String requestId = "typeset-" + UUID.randomUUID().toString();
                    request.put("requestId", requestId);
                    request.put("taskType", AiChatCoordinator.MODE_TYPESET);
                    request.put("typesetType", typesetType);  // 新增字段
                    request.put("selection", docText);  // 全文作为 selection
                    request.put("source", "android-typeset");

                    JSONObject context = new JSONObject();
                    context.put("modelMode", "base");
                    request.put("context", context);
                    request.put("history", new JSONArray());

                    aiActiveRequestId = requestId;
                    aiStreamingRequestId = requestId;
                    aiRequestModeById.put(requestId, AiChatCoordinator.MODE_TYPESET);
                    aiTextByRequestId.put(requestId, new StringBuilder());

                    startAiRequestSession(request, -1);
                } catch (JSONException e) {
                    Log.e(TAG, "ai_typeset_request_error", e);
                    toastTodo("启动排版失败");
                }
            });
        }, "cool-ai-typeset-extract").start();
    }

    /**
     * 显示AI排版结果预览 BottomSheet
     * @param htmlContent AI返回的排版后的HTML内容
     */
    /** 清洗 AI 排版返回的 HTML：剥离 markdown 代码块围栏（```html / ```）、前后空白。
     *  AI 有时把 HTML 包在 ```html ... ``` 里返回，导致字面符号泄漏进文档。 */
    private static String sanitizeTypesetHtml(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl >= 0 && nl <= 12) {
                s = s.substring(nl + 1);
            } else if (nl < 0 && s.length() <= 12) {
                s = "";
            } else {
                s = s.substring(3);
            }
            s = s.trim();
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3).trim();
            }
        }
        return s;
    }

    /** 判断字符串是否像有效 HTML（含至少一个常见标签），拦截 AI 返回纯文本/垃圾的情况。 */
    private static boolean isLikelyHtml(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "<\\s*(html|body|h[1-6]|p|div|ul|ol|li|table|tr|td|span|font|br|hr|strong|em|a|blockquote|pre)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        return p.matcher(s).find();
    }

    private void showTypesetPreviewSheet(String htmlContent) {
        String cleaned = sanitizeTypesetHtml(htmlContent);
        Log.i(TAG, "ai_typeset_preview_show htmlChars=" + (cleaned != null ? cleaned.length() : 0)
                + " rawChars=" + (htmlContent != null ? htmlContent.length() : 0));
        if (!isLikelyHtml(cleaned)) {
            Log.w(TAG, "ai_typeset_preview_not_html — AI 未返回有效 HTML，放弃预览");
            Toast.makeText(this, "AI 未返回有效的排版 HTML，请重试", Toast.LENGTH_LONG).show();
            return;
        }
        if (isFinishing()) {
            return;
        }
        pendingTypesetHtml = cleaned;

        typesetPreviewSheet = new BottomSheetDialog(this);
        View sheetView = getLayoutInflater().inflate(R.layout.lolib_sheet_typeset_preview, null);
        typesetPreviewSheet.setContentView(sheetView);

        // 配置 BottomSheet
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        aiPanelController.configureBottomSheet(typesetPreviewSheet,
                screenHeight, screenWidth,
                getResources().getConfiguration().orientation);

        WebView webView = sheetView.findViewById(R.id.typeset_preview_webview);
        if (webView != null) {
            // 配置 WebView
            webView.getSettings().setJavaScriptEnabled(false);
            webView.getSettings().setSupportZoom(true);
            webView.setBackgroundColor(Color.WHITE);

            // 渲染 HTML（添加基础样式以提升预览效果）
            String wrappedHtml = "<html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>" +
                    "body { font-family: sans-serif; padding: 16px; line-height: 1.6; margin: 0; } " +
                    "h1, h2, h3 { margin-top: 1em; margin-bottom: 0.5em; } " +
                    "p { margin: 0.5em 0; } " +
                    "table { border-collapse: collapse; width: 100%; margin: 1em 0; } " +
                    "td, th { border: 1px solid #ccc; padding: 8px; } " +
                    "ul, ol { margin: 0.5em 0; padding-left: 2em; } " +
                    "</style></head><body>" + (htmlContent != null ? htmlContent : "") + "</body></html>";
            webView.loadDataWithBaseURL(null, wrappedHtml, "text/html", "UTF-8", null);
        }

        // 关闭按钮
        sheetView.findViewById(R.id.typeset_preview_close).setOnClickListener(v -> {
            Log.i(TAG, "ai_typeset_preview_dismissed");
            typesetPreviewSheet.dismiss();
        });

        // 取消按钮
        sheetView.findViewById(R.id.typeset_preview_cancel).setOnClickListener(v -> {
            typesetPreviewSheet.dismiss();
            toastTodo("已取消排版");
            Log.i(TAG, "ai_typeset_preview_cancelled");
        });

        // 应用按钮
        sheetView.findViewById(R.id.typeset_preview_apply).setOnClickListener(v -> {
            typesetPreviewSheet.dismiss();
            applyTypesetResult();
        });

        typesetPreviewSheet.setOnDismissListener(dialog -> {
            Log.i(TAG, "ai_typeset_preview_dismissed");
            typesetPreviewSheet = null;
        });

        typesetPreviewSheet.show();
        Log.i(TAG, "ai_typeset_preview_shown");
    }

    /**
     * 应用AI排版结果：将HTML粘贴到文档
     */
    private void applyTypesetResult() {
        Log.i(TAG, "ai_typeset_apply htmlChars=" + (pendingTypesetHtml != null ? pendingTypesetHtml.length() : 0));
        if (pendingTypesetHtml == null || pendingTypesetHtml.isEmpty()) {
            toastTodo("排版结果为空");
            return;
        }

        final byte[] htmlBytes = pendingTypesetHtml.getBytes(StandardCharsets.UTF_8);
        runOnUiThread(() -> {
            paste("text/html", htmlBytes);
            toastTodo("AI排版已应用");
        });

        pendingTypesetHtml = null;
        pendingTypesetType = null;
    }

    // ==================== AI排版相关方法结束 ====================

    // ==================== 生成大纲相关方法 ====================

    private static final String[] OUTLINE_TYPE_KEYS = {
            AiChatCoordinator.OUTLINE_TYPE_PAPER,
            AiChatCoordinator.OUTLINE_TYPE_REPORT,
            AiChatCoordinator.OUTLINE_TYPE_SPEECH,
            AiChatCoordinator.OUTLINE_TYPE_EVENT,
            AiChatCoordinator.OUTLINE_TYPE_GENERAL
    };
    private static final String[] OUTLINE_TYPE_LABELS = {
            "论文", "工作报告", "演讲稿", "活动策划", "通用文档"
    };

    /**
     * 弹出生成大纲对话框。
     * @param selectionText 入口 A 传选区文字；入口 B 传 null（生成时提取全文）
     */
    private void showOutlineDialog(String selectionText) {
        if (outlineDialog != null && outlineDialog.isShowing()) {
            outlineDialog.dismiss();
        }
        outlineContextText = selectionText;
        pendingOutlineType = AiChatCoordinator.OUTLINE_TYPE_GENERAL;
        pendingOutlineResult = null;

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_outline, null);
        outlineDialogRoot = root;

        outlineTypeLabel = root.findViewById(R.id.outline_type_label);
        outlineDescEdit = root.findViewById(R.id.outline_desc_edit);
        outlineResultText = root.findViewById(R.id.outline_result_text);
        outlineDescCard = root.findViewById(R.id.outline_desc_card);
        outlineResultCard = root.findViewById(R.id.outline_result_card);
        outlineGenerateBtn = root.findViewById(R.id.outline_generate_btn);
        outlineDoneRow = root.findViewById(R.id.outline_done_row);
        outlineCopyRow = root.findViewById(R.id.outline_copy_row);

        root.findViewById(R.id.outline_close_btn).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.outline_type_card).setOnClickListener(v -> showOutlineTypePicker());
        outlineGenerateBtn.setOnClickListener(v -> startOutlineGeneration());
        root.findViewById(R.id.outline_regenerate_btn).setOnClickListener(v -> startOutlineGeneration());
        root.findViewById(R.id.outline_apply_btn).setOnClickListener(v -> applyOutlineResult());
        outlineCopyRow.setOnClickListener(v -> copyOutlineResult());

        dialog.setOnDismissListener(d -> {
            Log.i(TAG, "outline_dialog_dismissed");
            aiStreamingViewByRequestId.remove(outlineActiveRequestId);
            outlineActiveRequestId = "";
            outlineDialog = null;
            outlineDialogRoot = null;
        });
        dialog.setView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        outlineDialog = dialog;
        switchOutlineDialogState(false);
        root.post(this::applyOutlineDialogSize);
    }

    /**
     * 约束大纲弹窗尺寸：宽 min(670dp, 屏宽-48dp)，高 min(756dp, 屏高 80%)，输入/完成态共用。
     */
    private void applyOutlineDialogSize() {
        if (outlineDialog == null || outlineDialog.getWindow() == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = dpToPx(48);
        int targetWidth = Math.min(dpToPx(670), dm.widthPixels - margin);
        targetWidth = Math.max(targetWidth, dpToPx(280));

        int targetHeight = Math.min(dpToPx(756), (int) (dm.heightPixels * 0.80f));
        targetHeight = Math.max(targetHeight, dpToPx(320));
        targetHeight = Math.min(targetHeight, dm.heightPixels - dpToPx(24));

        outlineDialog.getWindow().setLayout(targetWidth, targetHeight);
        if (outlineDialogRoot != null) {
            ViewGroup.LayoutParams lp = outlineDialogRoot.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(targetWidth, targetHeight);
            } else {
                lp.width = targetWidth;
                lp.height = targetHeight;
            }
            outlineDialogRoot.setLayoutParams(lp);
        }
        Log.d(TAG, "outline_dialog_size w=" + targetWidth + " h=" + targetHeight
                + " screen=" + dm.widthPixels + "x" + dm.heightPixels);
    }

    private void scrollOutlineResultToBottom() {
        if (outlineResultCard == null || outlineResultCard.getVisibility() != View.VISIBLE) {
            return;
        }
        outlineResultCard.post(() -> outlineResultCard.fullScroll(View.FOCUS_DOWN));
    }

    private void showOutlineTypePicker() {
        PopupMenu popup = new PopupMenu(this, outlineTypeLabel);
        for (int i = 0; i < OUTLINE_TYPE_LABELS.length; i++) {
            popup.getMenu().add(0, i, i, OUTLINE_TYPE_LABELS[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            int idx = item.getItemId();
            if (idx >= 0 && idx < OUTLINE_TYPE_KEYS.length) {
                pendingOutlineType = OUTLINE_TYPE_KEYS[idx];
                outlineTypeLabel.setText(OUTLINE_TYPE_LABELS[idx]);
                Log.i(TAG, "outline_type_selected type=" + pendingOutlineType);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void startOutlineGeneration() {
        pendingOutlineDesc = outlineDescEdit.getText().toString().trim();
        Log.i(TAG, "ai_outline_start type=" + pendingOutlineType
                + " hasContext=" + (outlineContextText != null && !outlineContextText.isEmpty())
                + " descChars=" + pendingOutlineDesc.length());

        // 入口 B（无选区文字）→ 先提取全文
        if (outlineContextText == null || outlineContextText.isEmpty()) {
            toastTodo("正在提取文档全文...");
            new Thread(() -> {
                String full = extractFullTextNative("outline");
                if (full == null || full.isEmpty()) {
                    runOnUiThread(() -> {
                        toastTodo("文档全文提取失败，请稍后重试");
                        Log.w(TAG, "ai_outline_doc_extract_failed");
                    });
                    return;
                }
                Log.i(TAG, "ai_outline_doc_extracted chars=" + full.length());
                outlineContextText = full;
                runOnUiThread(this::sendOutlineRequest);
            }, "cool-ai-outline-extract").start();
        } else {
            sendOutlineRequest();
        }
    }

    private void sendOutlineRequest() {
        toastTodo("正在生成大纲...");
        if (outlineResultText != null) {
            outlineResultText.setText("正在生成大纲...");
        }
        switchOutlineDialogState(true);  // 切到完成态骨架

        try {
            JSONObject request = new JSONObject();
            String requestId = "outline-" + UUID.randomUUID().toString();
            request.put("requestId", requestId);
            request.put("taskType", AiChatCoordinator.MODE_OUTLINE);
            request.put("outlineType", pendingOutlineType);
            request.put("selection", outlineContextText != null ? outlineContextText : "");
            request.put("source", "android-outline");

            JSONObject context = new JSONObject();
            context.put("description", pendingOutlineDesc != null ? pendingOutlineDesc : "");
            context.put("modelMode", "base");
            request.put("context", context);
            request.put("history", new JSONArray());

            aiActiveRequestId = requestId;
            aiStreamingRequestId = requestId;
            aiRequestModeById.put(requestId, AiChatCoordinator.MODE_OUTLINE);
            aiTextByRequestId.put(requestId, new StringBuilder());
            // 注册流式目标：ai.stream 事件经 dispatchAiEvent→handleAiNativeEvent 自动渲染到该 TextView
            // （streaming=true → AiMarkdownRenderer 走纯文本分支，"一、/1./(1)" 编号不会被转 markdown 列表）
            aiStreamingViewByRequestId.remove(outlineActiveRequestId);
            outlineActiveRequestId = requestId;
            if (outlineResultText != null) {
                aiStreamingViewByRequestId.put(requestId, outlineResultText);
            }

            startAiRequestSession(request, -1);
        } catch (JSONException e) {
            Log.e(TAG, "ai_outline_request_error", e);
            toastTodo("启动大纲生成失败");
            switchOutlineDialogState(false);
        }
    }

    private void showOutlineResult(String text) {
        pendingOutlineResult = text;
        if (outlineResultText != null) {
            outlineResultText.setText(text);
        }
        switchOutlineDialogState(true);
        scrollOutlineResultToBottom();
    }

    private void switchOutlineDialogState(boolean completed) {
        if (outlineDescCard != null) {
            outlineDescCard.setVisibility(completed ? View.GONE : View.VISIBLE);
        }
        if (outlineGenerateBtn != null) {
            outlineGenerateBtn.setVisibility(completed ? View.GONE : View.VISIBLE);
        }
        if (outlineResultCard != null) {
            outlineResultCard.setVisibility(completed ? View.VISIBLE : View.GONE);
        }
        if (outlineDoneRow != null) {
            outlineDoneRow.setVisibility(completed ? View.VISIBLE : View.GONE);
        }
        if (outlineCopyRow != null) {
            outlineCopyRow.setVisibility(completed ? View.VISIBLE : View.GONE);
        }
        if (completed) {
            if (outlineDialogRoot != null) {
                outlineDialogRoot.post(this::applyOutlineDialogSize);
            } else {
                applyOutlineDialogSize();
            }
        }
    }

    private void applyOutlineResult() {
        Log.i(TAG, "ai_outline_apply chars=" + (pendingOutlineResult != null ? pendingOutlineResult.length() : 0));
        if (pendingOutlineResult == null || pendingOutlineResult.isEmpty()) {
            toastTodo("大纲为空");
            return;
        }
        final String text = pendingOutlineResult;
        // 先跳到文末（同时取消任何选区），再粘贴——避免替换当前选中的内容（入口 A 可能仍有选区处于选中状态）
        runOnUiThread(() -> postUnoCommand(".uno:GoToEndOfDoc", "{}", false));
        new Thread(() -> {
            try {
                Thread.sleep(300);  // 等待 GoToEndOfDoc 生效
            } catch (InterruptedException ignored) {
            }
            pasteAiTextAsHtml(text);
            Log.i(TAG, "ai_outline_inserted_at_end chars=" + text.length() + " format=html");
            runOnUiThread(() -> {
                toastTodo("大纲已插入到文末");
                if (outlineDialog != null) {
                    outlineDialog.dismiss();
                }
            });
        }, "cool-ai-outline-apply").start();
        pendingOutlineResult = null;
    }

    private void copyOutlineResult() {
        if (pendingOutlineResult == null || pendingOutlineResult.isEmpty()) {
            toastTodo("暂无大纲可复制");
            return;
        }
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("outline", pendingOutlineResult));
            toastTodo("大纲已复制，可粘贴到任意位置");
            Log.i(TAG, "ai_outline_copied chars=" + pendingOutlineResult.length());
        }
    }

    // ==================== 文案生成相关方法 ====================

    private void showArticleGenerateDialog() {
        if (articleDialog != null && articleDialog.isShowing()) {
            articleDialog.dismiss();
        }
        pendingArticleCategory = null;
        pendingArticleTemplate = null;
        pendingArticleValues = null;
        pendingArticleResult = null;

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_article_generate, null);
        articleDialogRoot = root;

        articleCategoryLabel = root.findViewById(R.id.article_category_label);
        articleSubTypeLabel = root.findViewById(R.id.article_subtype_label);
        articleSubTypeCard = root.findViewById(R.id.article_subtype_card);
        articleStageHint = root.findViewById(R.id.article_stage_hint);
        articleStageForm = root.findViewById(R.id.article_stage_form);
        articleFormContainer = root.findViewById(R.id.article_form_container);
        articleGenerateBtnText = root.findViewById(R.id.article_generate_btn_text);
        articleResultCard = root.findViewById(R.id.article_result_card);
        articleResultText = root.findViewById(R.id.article_result_text);
        articleCopyRow = root.findViewById(R.id.article_copy_row);
        articleDoneRow = root.findViewById(R.id.article_done_row);

        root.findViewById(R.id.article_close_btn).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.article_category_card).setOnClickListener(v -> showArticleCategoryPicker());
        articleSubTypeCard.setOnClickListener(v -> showArticleSubTypePicker());
        root.findViewById(R.id.article_generate_btn).setOnClickListener(v -> startArticleGeneration());
        root.findViewById(R.id.article_regenerate_btn).setOnClickListener(v -> {
            if (pendingArticleTemplate != null) {
                switchArticleDialogStage(ARTICLE_STAGE_FORM);
            }
        });
        root.findViewById(R.id.article_apply_btn).setOnClickListener(v -> applyArticleResult());
        articleCopyRow.setOnClickListener(v -> copyArticleResult());

        dialog.setOnDismissListener(d -> {
            Log.i(TAG, "article_dialog_dismissed");
            aiStreamingViewByRequestId.remove(articleActiveRequestId);
            articleActiveRequestId = "";
            articleDialog = null;
            articleDialogRoot = null;
        });
        dialog.setView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        articleDialog = dialog;
        switchArticleDialogStage(ARTICLE_STAGE_SELECT);
        root.post(this::applyArticleDialogSize);
    }

    private void applyArticleDialogSize() {
        if (articleDialog == null || articleDialog.getWindow() == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = dpToPx(48);
        int targetWidth = Math.min(dpToPx(670), dm.widthPixels - margin);
        targetWidth = Math.max(targetWidth, dpToPx(280));

        int targetHeight = Math.min(dpToPx(756), (int) (dm.heightPixels * 0.80f));
        targetHeight = Math.max(targetHeight, dpToPx(320));
        targetHeight = Math.min(targetHeight, dm.heightPixels - dpToPx(24));

        articleDialog.getWindow().setLayout(targetWidth, targetHeight);
        if (articleDialogRoot != null) {
            ViewGroup.LayoutParams lp = articleDialogRoot.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(targetWidth, targetHeight);
            } else {
                lp.width = targetWidth;
                lp.height = targetHeight;
            }
            articleDialogRoot.setLayoutParams(lp);
        }
        Log.d(TAG, "article_dialog_size w=" + targetWidth + " h=" + targetHeight
                + " screen=" + dm.widthPixels + "x" + dm.heightPixels);
    }

    private void showArticleCategoryPicker() {
        PopupMenu popup = new PopupMenu(this, articleCategoryLabel);
        String[] categories = ArticleTemplateRegistry.getCategories();
        for (int i = 0; i < categories.length; i++) {
            popup.getMenu().add(0, i, i, categories[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            int idx = item.getItemId();
            if (idx >= 0 && idx < categories.length) {
                pendingArticleCategory = categories[idx];
                articleCategoryLabel.setText(pendingArticleCategory);
                articleSubTypeLabel.setText("请选择子类");
                pendingArticleTemplate = null;
                pendingArticleValues = null;
                articleSubTypeCard.setVisibility(View.VISIBLE);
                switchArticleDialogStage(ARTICLE_STAGE_SELECT);
                Log.i(TAG, "article_category_selected category=" + pendingArticleCategory);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showArticleSubTypePicker() {
        if (pendingArticleCategory == null || pendingArticleCategory.isEmpty()) {
            toastTodo("请先选择分类");
            return;
        }
        java.util.List<ArticleTemplate> templates =
                ArticleTemplateRegistry.getByCategory(pendingArticleCategory);
        if (templates.isEmpty()) {
            return;
        }
        PopupMenu popup = new PopupMenu(this, articleSubTypeLabel);
        for (int i = 0; i < templates.size(); i++) {
            popup.getMenu().add(0, i, i, templates.get(i).subTypeLabel);
        }
        popup.setOnMenuItemClickListener(item -> {
            int idx = item.getItemId();
            if (idx >= 0 && idx < templates.size()) {
                ArticleTemplate tmpl = templates.get(idx);
                pendingArticleTemplate = tmpl;
                articleSubTypeLabel.setText(tmpl.subTypeLabel);
                renderArticleForm(tmpl);
                Log.i(TAG, "article_subtype_selected key=" + tmpl.key);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void renderArticleForm(ArticleTemplate tmpl) {
        if (articleFormContainer == null || tmpl == null) {
            return;
        }
        articleFormContainer.removeAllViews();
        pendingArticleValues = new String[tmpl.variables.length];
        float density = getResources().getDisplayMetrics().density;
        int labelTop = (int) (12 * density);
        int fieldBottom = (int) (8 * density);
        int fieldPadding = (int) (16 * density);

        for (int i = 0; i < tmpl.variables.length; i++) {
            ArticleTemplate.Variable variable = tmpl.variables[i];

            TextView label = new TextView(this);
            label.setText(variable.label);
            label.setTextColor(Color.parseColor("#999999"));
            label.setTextSize(14);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                labelLp.topMargin = labelTop;
            }
            label.setLayoutParams(labelLp);
            articleFormContainer.addView(label);

            EditText field = new EditText(this);
            field.setTag("article_field_" + i);
            field.setHint(variable.hint);
            field.setTextColor(Color.parseColor("#333333"));
            field.setHintTextColor(Color.parseColor("#999999"));
            field.setTextSize(16);
            field.setBackgroundResource(R.drawable.lolib_bg_outline_edit);
            field.setPadding(fieldPadding, fieldPadding, fieldPadding, fieldPadding);
            field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            field.setMaxLines(3);
            LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fieldLp.bottomMargin = fieldBottom;
            field.setLayoutParams(fieldLp);
            articleFormContainer.addView(field);
        }

        if (articleGenerateBtnText != null) {
            articleGenerateBtnText.setText("开始生成");
        }
        switchArticleDialogStage(ARTICLE_STAGE_FORM);
    }

    private void startArticleGeneration() {
        if (pendingArticleTemplate == null || articleFormContainer == null) {
            toastTodo("请先选择文案类型");
            return;
        }
        ArticleTemplate tmpl = pendingArticleTemplate;
        String[] values = new String[tmpl.variables.length];
        for (int i = 0; i < tmpl.variables.length; i++) {
            View child = articleFormContainer.findViewWithTag("article_field_" + i);
            if (child instanceof EditText) {
                values[i] = ((EditText) child).getText().toString().trim();
            } else {
                values[i] = "";
            }
        }
        pendingArticleValues = values;
        sendArticleRequest(tmpl, values);
    }

    private void sendArticleRequest(ArticleTemplate tmpl, String[] values) {
        toastTodo("正在生成文案...");
        if (articleResultText != null) {
            articleResultText.setText("正在生成文案...");
        }
        switchArticleDialogStage(ARTICLE_STAGE_RESULT);

        try {
            JSONObject request = new JSONObject();
            String requestId = "article-" + UUID.randomUUID().toString();
            request.put("requestId", requestId);
            request.put("taskType", AiChatCoordinator.MODE_ARTICLE_GENERATE);
            request.put("articleTemplateKey", tmpl.key);
            request.put("selection", "");
            request.put("source", "android-article");

            JSONObject context = new JSONObject();
            JSONArray valuesArr = new JSONArray();
            for (String v : values) {
                valuesArr.put(v == null ? "" : v);
            }
            context.put("articleValues", valuesArr);
            context.put("modelMode", "base");
            request.put("context", context);
            request.put("history", new JSONArray());

            aiActiveRequestId = requestId;
            aiStreamingRequestId = requestId;
            aiRequestModeById.put(requestId, AiChatCoordinator.MODE_ARTICLE_GENERATE);
            aiTextByRequestId.put(requestId, new StringBuilder());
            aiStreamingViewByRequestId.remove(articleActiveRequestId);
            articleActiveRequestId = requestId;
            if (articleResultText != null) {
                aiStreamingViewByRequestId.put(requestId, articleResultText);
            }

            Log.i(TAG, "ai_article_start requestId=" + requestId + " template=" + tmpl.key);
            startAiRequestSession(request, -1);
        } catch (JSONException e) {
            Log.e(TAG, "ai_article_request_error", e);
            toastTodo("启动文案生成失败");
            switchArticleDialogStage(ARTICLE_STAGE_FORM);
        }
    }

    private void showArticleGenerateResult(String text) {
        pendingArticleResult = text;
        if (articleResultText != null) {
            articleResultText.setText(text);
        }
        switchArticleDialogStage(ARTICLE_STAGE_RESULT);
        scrollArticleResultToBottom();
    }

    private void scrollArticleResultToBottom() {
        if (articleResultCard == null || articleResultCard.getVisibility() != View.VISIBLE) {
            return;
        }
        articleResultCard.post(() -> articleResultCard.fullScroll(View.FOCUS_DOWN));
    }

    private void switchArticleDialogStage(int stage) {
        boolean select = stage == ARTICLE_STAGE_SELECT;
        boolean form = stage == ARTICLE_STAGE_FORM;
        boolean result = stage == ARTICLE_STAGE_RESULT;

        View categoryCard = articleDialogRoot != null
                ? articleDialogRoot.findViewById(R.id.article_category_card) : null;
        if (categoryCard != null) {
            categoryCard.setVisibility(result ? View.GONE : View.VISIBLE);
        }
        if (articleSubTypeCard != null) {
            articleSubTypeCard.setVisibility((select && pendingArticleCategory != null)
                    || form ? View.VISIBLE : View.GONE);
            if (result) {
                articleSubTypeCard.setVisibility(View.GONE);
            }
        }

        if (articleStageHint != null) {
            articleStageHint.setVisibility(select ? View.VISIBLE : View.GONE);
        }
        if (articleStageForm != null) {
            articleStageForm.setVisibility(form ? View.VISIBLE : View.GONE);
        }
        if (articleResultCard != null) {
            articleResultCard.setVisibility(result ? View.VISIBLE : View.GONE);
        }
        if (articleCopyRow != null) {
            articleCopyRow.setVisibility(result ? View.VISIBLE : View.GONE);
        }
        if (articleDoneRow != null) {
            articleDoneRow.setVisibility(result ? View.VISIBLE : View.GONE);
        }

        if (result && articleDialogRoot != null) {
            articleDialogRoot.post(this::applyArticleDialogSize);
        } else if (form && articleDialogRoot != null) {
            articleDialogRoot.post(this::applyArticleDialogSize);
        }
    }

    private void applyArticleResult() {
        Log.i(TAG, "ai_article_apply chars=" + (pendingArticleResult != null ? pendingArticleResult.length() : 0));
        if (pendingArticleResult == null || pendingArticleResult.isEmpty()) {
            toastTodo("文案为空");
            return;
        }
        final String text = pendingArticleResult;
        runOnUiThread(() -> postUnoCommand(".uno:GoToEndOfDoc", "{}", false));
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            pasteAiTextAsHtml(text);
            Log.i(TAG, "ai_article_inserted_at_end chars=" + text.length() + " format=html");
            runOnUiThread(() -> {
                toastTodo("文案已插入到文末");
                if (articleDialog != null) {
                    articleDialog.dismiss();
                }
            });
        }, "cool-ai-article-apply").start();
        pendingArticleResult = null;
    }

    private void copyArticleResult() {
        if (pendingArticleResult == null || pendingArticleResult.isEmpty()) {
            toastTodo("暂无文案可复制");
            return;
        }
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("article", pendingArticleResult));
            toastTodo("文案已复制，可粘贴到任意位置");
            Log.i(TAG, "ai_article_copied chars=" + pendingArticleResult.length());
        }
    }

    // ==================== 扩写/缩写/润色弹窗相关方法 ====================

    /** 把 Markdown 文本转 HTML 后粘贴，触发 Writer HTML Import Filter，标题/加粗/列表落地为文档样式。 */
    private void pasteAiTextAsHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return;
        }
        String html = AiMarkdownRenderer.markdownToHtml(markdown);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        paste("text/html", bytes);
    }

    // ==================== 图片插入 / base64 工具 ====================

    /** 把 base64 图片通过 insertfile 消息插入文档（复用 kit 层 .uno:InsertGraphic 链路）。 */
    private void insertImageBase64(String base64, String fileName) {
        if (base64 == null || base64.isEmpty()) {
            return;
        }
        ensureEditModeThen(() -> sendInsertFileWhenSocketOpen(base64, fileName, 0));
    }

    /**
     * 等待文档 socket 处于 OPEN(1) 再发 insertfile，避免在 reconnect/closed 状态下
     * 原生发送命中 "sending on closed socket" 导致图片丢失。
     * 轮询 readyState，OPEN 时走高效原生路径；超时则退回 JS sendMessage 队列兜底。
     */
    private void sendInsertFileWhenSocketOpen(String base64, String fileName, int attempt) {
        if (mWebView == null) {
            Log.w(TAG, "ai_image_insert_abort no_webview name=" + fileName);
            return;
        }
        final String script = "(function(){try{return window.socket?window.socket.readyState:-1;}catch(e){return -1;}})()";
        mWebView.evaluateJavascript(script, value -> {
            int state = parseSocketReadyState(value);
            if (state == 1) {
                String message = "insertfile name=" + fileName + " type=image/png data=" + base64;
                postMobileMessage(message);
                nudgeSocketIfStalled("insert_ai_image");
                Log.i(TAG, "ai_image_inserted name=" + fileName + " bytes=" + base64.length()
                        + " socket=open attempt=" + attempt);
            } else if (attempt < 40) {
                // socket 未就绪（reconnecting/closed），150ms 后重试，最长约 6s
                if (attempt == 0 || attempt % 5 == 0) {
                    Log.i(TAG, "ai_image_insert_wait name=" + fileName + " state=" + state
                            + " attempt=" + attempt);
                }
                getMainHandler().postDelayed(
                        () -> sendInsertFileWhenSocketOpen(base64, fileName, attempt + 1), 150L);
            } else {
                // 超时：退回 JS sendMessage 队列，由 Socket.ts 在重连完成后 flush
                Log.w(TAG, "ai_image_insert_timeout name=" + fileName + " fallback=js_queue");
                sendInsertFileViaJsQueue(base64, fileName);
            }
        });
    }

    /** 通过 JS app.socket.sendMessage 发送，利用 Socket.ts 的重连队列保证送达。 */
    private void sendInsertFileViaJsQueue(String base64, String fileName) {
        if (mWebView == null) {
            return;
        }
        // base64 字符集为 [A-Za-z0-9+/=]，无引号/反斜杠/换行，可安全嵌入双引号 JS 字符串
        final String js = "(function(){try{"
                + "if(window.app&&window.app.socket&&typeof window.app.socket.sendMessage==='function'){"
                + "window.app.socket.sendMessage(\"insertfile name=" + fileName
                + " type=image/png data=" + base64 + "\");return 'queued';}"
                + "return 'no_socket';}catch(e){return 'err';}})()";
        mWebView.evaluateJavascript(js, value -> {
            Log.i(TAG, "ai_image_insert_jsqueue name=" + fileName + " result=" + value);
            nudgeSocketIfStalled("insert_ai_image_jsqueue");
        });
    }

    /** 解析 evaluateJavascript 返回的 readyState（形如 "1" 或 "1\n"）。 */
    private int parseSocketReadyState(String value) {
        if (value == null) {
            return -1;
        }
        String s = value.trim();
        if (s.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 读取图片 Uri 为 base64 字符串（NO_WRAP，不含 data: 前缀）。 */
    private String readImageUriAsBase64(Uri uri) {
        if (uri == null) {
            return "";
        }
        InputStream is = null;
        try {
            is = getContentResolver().openInputStream(uri);
            if (is == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return android.util.Base64.encodeToString(buffer.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (IOException e) {
            Log.e(TAG, "readImageUriAsBase64 failed uri=" + uri, e);
            return "";
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** base64 字符串转 Bitmap。 */
    private android.graphics.Bitmap base64ToBitmap(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            Log.e(TAG, "base64ToBitmap failed", e);
            return null;
        }
    }

    /**
     * endpoint 兜底规范化：确保 url 以 targetSuffix 结尾。
     * 若已含目标后缀则原样返回；否则截到版本段（/v1、/v2…），拼接 targetSuffix；
     * 无版本段则在域名根后拼 /v1 + targetSuffix。
     * 例：chat/completions 配址错配到 images 模型时，自动改成 images/generations。
     */
    private String normalizeEndpoint(String rawUrl, String targetSuffix) {
        if (rawUrl == null) return "";
        String url = rawUrl.trim();
        if (url.isEmpty()) return "";
        if (url.contains(targetSuffix)) return url;
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        // 形如 .../v1 结尾
        if (java.util.regex.Pattern.matches(".*?/v\\d+$", url)) {
            return url + targetSuffix;
        }
        // 中间含 /vN/，截到版本段
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/v\\d+/").matcher(url);
        if (m.find()) {
            return url.substring(0, m.end() - 1) + targetSuffix;
        }
        // 无版本段，拼 /v1
        return url + "/v1" + targetSuffix;
    }

    /** 生成"生成中"灰底占位图（无 Glide，用 Canvas 绘制文字）。 */
    private android.graphics.Bitmap createGeneratingPlaceholder() {
        int w = 240, h = 240;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        canvas.drawColor(0xFFE8E8E8);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(0xFF888888);
        paint.setTextSize(30);
        paint.setAntiAlias(true);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        canvas.drawText("生成中", w / 2f, h / 2f + 10, paint);
        return bmp;
    }

    // ==================== 格式批量处理弹窗 ====================

    private void showFormatBatchDialog(String selection) {
        if (formatBatchDialog != null && formatBatchDialog.isShowing()) {
            formatBatchDialog.dismiss();
        }
        formatBatchSelection = selection != null ? selection : "";
        pendingFormatBatchResult = null;

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_format_batch, null);
        formatBatchDialogRoot = root;

        formatBatchOptionsContainer = root.findViewById(R.id.format_batch_options_container);
        formatBatchExecuteBtn = root.findViewById(R.id.format_batch_execute_btn);
        formatBatchResultCard = root.findViewById(R.id.format_batch_result_card);
        formatBatchResultText = root.findViewById(R.id.format_batch_result_text);
        formatBatchCopyRow = root.findViewById(R.id.format_batch_copy_row);
        formatBatchDoneRow = root.findViewById(R.id.format_batch_done_row);

        formatBatchCheckBoxes[FormatBatchProcessor.RULE_EN_TO_ZH_PUNCT] = root.findViewById(R.id.fb_option_en_to_zh);
        formatBatchCheckBoxes[FormatBatchProcessor.RULE_ZH_TO_EN_PUNCT] = root.findViewById(R.id.fb_option_zh_to_en);
        formatBatchCheckBoxes[FormatBatchProcessor.RULE_GHOST_TO_SPACE] = root.findViewById(R.id.fb_option_ghost);
        formatBatchCheckBoxes[FormatBatchProcessor.RULE_REMOVE_EXTRA_BLANK_LINES] = root.findViewById(R.id.fb_option_blank_lines);
        formatBatchCheckBoxes[FormatBatchProcessor.RULE_REMOVE_WAVY_UNDERLINE] = root.findViewById(R.id.fb_option_wavy);
        formatBatchCheckBoxes[FormatBatchProcessor.RULE_REMOVE_HYPERLINK] = root.findViewById(R.id.fb_option_hyperlink);

        // 默认勾选 #4 #5（对应图3：删除多余空行、消除下滑波浪线）
        if (formatBatchCheckBoxes[FormatBatchProcessor.RULE_REMOVE_EXTRA_BLANK_LINES] != null) {
            formatBatchCheckBoxes[FormatBatchProcessor.RULE_REMOVE_EXTRA_BLANK_LINES].setChecked(true);
        }
        if (formatBatchCheckBoxes[FormatBatchProcessor.RULE_REMOVE_WAVY_UNDERLINE] != null) {
            formatBatchCheckBoxes[FormatBatchProcessor.RULE_REMOVE_WAVY_UNDERLINE].setChecked(true);
        }

        root.findViewById(R.id.format_batch_close_btn).setOnClickListener(v -> dialog.dismiss());
        formatBatchExecuteBtn.setOnClickListener(v -> executeFormatBatch());
        root.findViewById(R.id.format_batch_regenerate_btn).setOnClickListener(v -> switchFormatBatchStage(FORMAT_BATCH_STAGE_INPUT));
        root.findViewById(R.id.format_batch_apply_btn).setOnClickListener(v -> applyFormatBatchResult());
        formatBatchCopyRow.setOnClickListener(v -> copyFormatBatchResult());

        dialog.setOnDismissListener(d -> {
            Log.i(TAG, "format_batch_dialog_dismissed");
            formatBatchDialog = null;
            formatBatchDialogRoot = null;
        });
        dialog.setView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        formatBatchDialog = dialog;
        switchFormatBatchStage(FORMAT_BATCH_STAGE_INPUT);
        root.post(this::applyFormatBatchDialogSize);
        Log.i(TAG, "format_batch_dialog_show selectionChars=" + formatBatchSelection.length());
    }

    private void applyFormatBatchDialogSize() {
        if (formatBatchDialog == null || formatBatchDialog.getWindow() == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = dpToPx(48);
        int targetWidth = Math.min(dpToPx(670), dm.widthPixels - margin);
        targetWidth = Math.max(targetWidth, dpToPx(280));
        boolean resultStage = formatBatchResultCard != null && formatBatchResultCard.getVisibility() == View.VISIBLE;
        int targetHeight;
        if (resultStage) {
            targetHeight = Math.min(dpToPx(756), (int) (dm.heightPixels * 0.80f));
            targetHeight = Math.max(targetHeight, dpToPx(320));
            targetHeight = Math.min(targetHeight, dm.heightPixels - dpToPx(24));
        } else {
            targetHeight = dpToPx(440);
        }
        formatBatchDialog.getWindow().setLayout(targetWidth, targetHeight);
        if (formatBatchDialogRoot != null) {
            ViewGroup.LayoutParams lp = formatBatchDialogRoot.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(targetWidth, targetHeight);
            } else {
                lp.width = targetWidth;
                lp.height = targetHeight;
            }
            formatBatchDialogRoot.setLayoutParams(lp);
        }
    }

    private void switchFormatBatchStage(int stage) {
        boolean input = stage == FORMAT_BATCH_STAGE_INPUT;
        if (formatBatchOptionsContainer != null) {
            formatBatchOptionsContainer.setVisibility(input ? View.VISIBLE : View.GONE);
        }
        if (formatBatchExecuteBtn != null) {
            formatBatchExecuteBtn.setVisibility(input ? View.VISIBLE : View.GONE);
        }
        if (formatBatchResultCard != null) {
            formatBatchResultCard.setVisibility(input ? View.GONE : View.VISIBLE);
        }
        if (formatBatchCopyRow != null) {
            formatBatchCopyRow.setVisibility(input ? View.GONE : View.VISIBLE);
        }
        if (formatBatchDoneRow != null) {
            formatBatchDoneRow.setVisibility(input ? View.GONE : View.VISIBLE);
        }
        if (formatBatchDialogRoot != null) {
            formatBatchDialogRoot.post(this::applyFormatBatchDialogSize);
        }
    }

    private void executeFormatBatch() {
        boolean[] options = new boolean[FormatBatchProcessor.RULE_COUNT];
        for (int i = 0; i < FormatBatchProcessor.RULE_COUNT; i++) {
            if (formatBatchCheckBoxes[i] != null) {
                options[i] = formatBatchCheckBoxes[i].isChecked();
            }
        }
        String result = FormatBatchProcessor.process(formatBatchSelection, options);
        pendingFormatBatchResult = result;
        if (formatBatchResultText != null) {
            formatBatchResultText.setText(result);
        }
        switchFormatBatchStage(FORMAT_BATCH_STAGE_RESULT);
        Log.i(TAG, "format_batch_executed inChars=" + formatBatchSelection.length() + " outChars=" + result.length());
    }

    private void applyFormatBatchResult() {
        if (pendingFormatBatchResult == null || pendingFormatBatchResult.isEmpty()) {
            toastTodo("没有可插入的内容");
            return;
        }
        Log.i(TAG, "format_batch_apply chars=" + pendingFormatBatchResult.length());
        final String text = pendingFormatBatchResult;
        if (formatBatchDialog != null) {
            formatBatchDialog.dismiss();
        }
        ensureEditModeThen(() -> pasteAiTextAsHtml(text));
    }

    private void copyFormatBatchResult() {
        if (pendingFormatBatchResult == null || pendingFormatBatchResult.isEmpty()) {
            toastTodo("没有可复制的内容");
            return;
        }
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("format_batch", pendingFormatBatchResult));
            toastTodo("已复制");
            Log.i(TAG, "format_batch_copied chars=" + pendingFormatBatchResult.length());
        }
    }

    // ==================== 文字提取弹窗 ====================

    private void showTextExtractDialog() {
        if (textExtractDialog != null && textExtractDialog.isShowing()) {
            textExtractDialog.dismiss();
        }
        pendingTextExtractResult = null;
        textExtractActiveRequestId = "";

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_text_extract, null);
        textExtractDialogRoot = root;

        textExtractInputContainer = root.findViewById(R.id.text_extract_input_container);
        textExtractResultCard = root.findViewById(R.id.text_extract_result_card);
        textExtractResultText = root.findViewById(R.id.text_extract_result_text);
        textExtractCopyRow = root.findViewById(R.id.text_extract_copy_row);
        textExtractDoneRow = root.findViewById(R.id.text_extract_done_row);

        root.findViewById(R.id.text_extract_close_btn).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.text_extract_album_btn).setOnClickListener(v -> launchTextExtractAlbum());
        root.findViewById(R.id.text_extract_camera_btn).setOnClickListener(v -> launchTextExtractCamera());
        root.findViewById(R.id.text_extract_re_recognize_btn).setOnClickListener(v -> switchTextExtractStage(TEXT_EXTRACT_STAGE_INPUT));
        root.findViewById(R.id.text_extract_apply_btn).setOnClickListener(v -> applyTextExtractResult());
        textExtractCopyRow.setOnClickListener(v -> copyTextExtractResult());

        dialog.setOnDismissListener(d -> {
            Log.i(TAG, "text_extract_dialog_dismissed");
            aiStreamingViewByRequestId.remove(textExtractActiveRequestId);
            if (!textExtractActiveRequestId.isEmpty()) {
                cancelAiRequest(textExtractActiveRequestId);
            }
            textExtractActiveRequestId = "";
            textExtractDialog = null;
            textExtractDialogRoot = null;
        });
        dialog.setView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        textExtractDialog = dialog;
        switchTextExtractStage(TEXT_EXTRACT_STAGE_INPUT);
        root.post(this::applyTextExtractDialogSize);
        Log.i(TAG, "text_extract_dialog_show");
    }

    private void applyTextExtractDialogSize() {
        if (textExtractDialog == null || textExtractDialog.getWindow() == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = dpToPx(48);
        int targetWidth = Math.min(dpToPx(670), dm.widthPixels - margin);
        targetWidth = Math.max(targetWidth, dpToPx(280));
        boolean resultStage = textExtractResultCard != null && textExtractResultCard.getVisibility() == View.VISIBLE;
        int targetHeight;
        if (resultStage) {
            targetHeight = Math.min(dpToPx(756), (int) (dm.heightPixels * 0.80f));
            targetHeight = Math.max(targetHeight, dpToPx(320));
            targetHeight = Math.min(targetHeight, dm.heightPixels - dpToPx(24));
        } else {
            targetHeight = dpToPx(340);
        }
        textExtractDialog.getWindow().setLayout(targetWidth, targetHeight);
        if (textExtractDialogRoot != null) {
            ViewGroup.LayoutParams lp = textExtractDialogRoot.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(targetWidth, targetHeight);
            } else {
                lp.width = targetWidth;
                lp.height = targetHeight;
            }
            textExtractDialogRoot.setLayoutParams(lp);
        }
    }

    private void switchTextExtractStage(int stage) {
        boolean input = stage == TEXT_EXTRACT_STAGE_INPUT;
        if (textExtractInputContainer != null) {
            textExtractInputContainer.setVisibility(input ? View.VISIBLE : View.GONE);
        }
        if (textExtractResultCard != null) {
            textExtractResultCard.setVisibility(input ? View.GONE : View.VISIBLE);
        }
        if (textExtractCopyRow != null) {
            textExtractCopyRow.setVisibility(input ? View.GONE : View.VISIBLE);
        }
        if (textExtractDoneRow != null) {
            textExtractDoneRow.setVisibility(input ? View.GONE : View.VISIBLE);
        }
        if (textExtractDialogRoot != null) {
            textExtractDialogRoot.post(this::applyTextExtractDialogSize);
        }
    }

    private void launchTextExtractAlbum() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "选择图片"), REQUEST_TEXT_EXTRACT_ALBUM);
        } catch (ActivityNotFoundException e) {
            toastTodo("未找到相册应用");
        }
    }

    private void launchTextExtractCamera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, PERMISSION_TEXT_EXTRACT_CAMERA);
            return;
        }
        startCameraForTextExtract();
    }

    private void startCameraForTextExtract() {
        try {
            File photoFile = new File(getCacheDir(), "text_extract_" + System.currentTimeMillis() + ".jpg");
            pendingTextExtractCameraUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingTextExtractCameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_TEXT_EXTRACT_CAMERA);
        } catch (Exception e) {
            toastTodo("无法启动相机");
            Log.e(TAG, "startCameraForTextExtract failed", e);
        }
    }

    /** onActivityResult 回调入口：读取图片为 base64 并发起 OCR 请求。 */
    private void handleTextExtractImageUri(Uri uri) {
        if (uri == null) {
            toastTodo("未获取到图片");
            return;
        }
        switchTextExtractStage(TEXT_EXTRACT_STAGE_RESULT);
        if (textExtractResultText != null) {
            textExtractResultText.setText("正在识别...");
        }
        new Thread(() -> {
            final String base64 = readImageUriAsBase64(uri);
            runOnUiThread(() -> {
                if (base64.isEmpty()) {
                    toastTodo("图片读取失败");
                    switchTextExtractStage(TEXT_EXTRACT_STAGE_INPUT);
                } else {
                    startTextExtractRequest(base64);
                }
            });
        }, "text-extract-read").start();
    }

    private void startTextExtractRequest(String base64Image) {
        try {
            JSONObject request = new JSONObject();
            String requestId = "textextract-" + java.util.UUID.randomUUID().toString();
            request.put("requestId", requestId);
            request.put("taskType", AiChatCoordinator.MODE_TEXT_EXTRACT);
            request.put("source", "android-text-extract");

            JSONObject context = new JSONObject();
            context.put("image", base64Image);
            request.put("context", context);
            request.put("modelMode", "vision");
            request.put("history", new JSONArray());

            // 取消上一次识别请求
            if (!textExtractActiveRequestId.isEmpty()) {
                cancelAiRequest(textExtractActiveRequestId);
            }
            textExtractActiveRequestId = requestId;
            aiStreamingViewByRequestId.remove(requestId);
            if (textExtractResultText != null) {
                aiStreamingViewByRequestId.put(requestId, textExtractResultText);
            }
            Log.i(TAG, "text_extract_request_start requestId=" + requestId);
            startAiRequestSession(request, -1);
        } catch (JSONException e) {
            Log.e(TAG, "startTextExtractRequest error", e);
            toastTodo("启动识别失败");
            switchTextExtractStage(TEXT_EXTRACT_STAGE_INPUT);
        }
    }

    private void showTextExtractResult(String text) {
        pendingTextExtractResult = text;
        if (textExtractResultText != null) {
            textExtractResultText.setText(text);
        }
        switchTextExtractStage(TEXT_EXTRACT_STAGE_RESULT);
        Log.i(TAG, "text_extract_result_shown chars=" + (text == null ? 0 : text.length()));
    }

    private void applyTextExtractResult() {
        if (pendingTextExtractResult == null || pendingTextExtractResult.isEmpty()) {
            toastTodo("没有可插入的内容");
            return;
        }
        Log.i(TAG, "text_extract_apply chars=" + pendingTextExtractResult.length());
        final String text = pendingTextExtractResult;
        if (textExtractDialog != null) {
            textExtractDialog.dismiss();
        }
        ensureEditModeThen(() -> pasteAiTextAsHtml(text));
    }

    private void copyTextExtractResult() {
        if (pendingTextExtractResult == null || pendingTextExtractResult.isEmpty()) {
            toastTodo("没有可复制的内容");
            return;
        }
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("text_extract", pendingTextExtractResult));
            toastTodo("已复制");
            Log.i(TAG, "text_extract_copied chars=" + pendingTextExtractResult.length());
        }
    }

    // ==================== AI图片弹窗 ====================

    private static final String[] AI_IMAGE_RATIOS = {"1:1", "9:16", "16:9"};
    private static final String[] AI_IMAGE_SIZES = {"1024x1024", "720x1280", "1280x720"};

    private void showAiImageDialog() {
        if (aiImageDialog != null && aiImageDialog.isShowing()) {
            aiImageDialog.dismiss();
        }
        aiImageBase64List.clear();
        aiImageSelectedIndex = 0;

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_ai_image, null);
        aiImageDialogRoot = root;

        aiImageInputContainer = root.findViewById(R.id.ai_image_input_container);
        aiImagePromptEdit = root.findViewById(R.id.ai_image_prompt_edit);
        aiImageRatioSpinner = root.findViewById(R.id.ai_image_ratio_spinner);
        aiImageGenerateBtn = root.findViewById(R.id.ai_image_generate_btn);
        aiImageGalleryContainer = root.findViewById(R.id.ai_image_gallery_container);
        aiImageMainView = root.findViewById(R.id.ai_image_main);
        aiImageThumbViews[0] = root.findViewById(R.id.ai_image_thumb_1);
        aiImageThumbViews[1] = root.findViewById(R.id.ai_image_thumb_2);
        aiImageThumbViews[2] = root.findViewById(R.id.ai_image_thumb_3);
        aiImageLoading = root.findViewById(R.id.ai_image_loading);
        aiImageDoneRow = root.findViewById(R.id.ai_image_done_row);

        if (aiImageRatioSpinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, AI_IMAGE_RATIOS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            aiImageRatioSpinner.setAdapter(adapter);
        }

        root.findViewById(R.id.ai_image_close_btn).setOnClickListener(v -> dialog.dismiss());
        aiImageGenerateBtn.setOnClickListener(v -> startAiImageGeneration());
        root.findViewById(R.id.ai_image_regenerate_btn).setOnClickListener(v -> startAiImageGeneration());
        if (aiImageMainView != null) {
            aiImageMainView.setOnClickListener(v -> showAiImagePreview(0));
        }
        for (int i = 0; i < aiImageThumbViews.length; i++) {
            final int idx = i + 1;
            if (aiImageThumbViews[i] != null) {
                aiImageThumbViews[i].setOnClickListener(v -> showAiImagePreview(idx));
            }
        }

        dialog.setOnDismissListener(d -> {
            Log.i(TAG, "ai_image_dialog_dismissed");
            cancelAiImageRequest();
            aiImageDialog = null;
            aiImageDialogRoot = null;
        });
        dialog.setView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        aiImageDialog = dialog;
        switchAiImageStage(AI_IMAGE_STAGE_INPUT);
        root.post(this::applyAiImageDialogSize);
        Log.i(TAG, "ai_image_dialog_show");
    }

    private void applyAiImageDialogSize() {
        if (aiImageDialog == null || aiImageDialog.getWindow() == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = dpToPx(48);
        int targetWidth = Math.min(dpToPx(670), dm.widthPixels - margin);
        targetWidth = Math.max(targetWidth, dpToPx(280));
        boolean resultStage = aiImageGalleryContainer != null
                && aiImageGalleryContainer.getVisibility() == View.VISIBLE;
        int targetHeight;
        if (resultStage) {
            targetHeight = Math.min(dpToPx(756), (int) (dm.heightPixels * 0.80f));
            targetHeight = Math.max(targetHeight, dpToPx(360));
            targetHeight = Math.min(targetHeight, dm.heightPixels - dpToPx(24));
        } else {
            targetHeight = dpToPx(360);
        }
        aiImageDialog.getWindow().setLayout(targetWidth, targetHeight);
        if (aiImageDialogRoot != null) {
            ViewGroup.LayoutParams lp = aiImageDialogRoot.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(targetWidth, targetHeight);
            } else {
                lp.width = targetWidth;
                lp.height = targetHeight;
            }
            aiImageDialogRoot.setLayoutParams(lp);
        }
    }

    private void switchAiImageStage(int stage) {
        boolean input = stage == AI_IMAGE_STAGE_INPUT;
        if (aiImageInputContainer != null) {
            aiImageInputContainer.setVisibility(input ? View.VISIBLE : View.GONE);
        }
        if (aiImageGenerateBtn != null) {
            aiImageGenerateBtn.setVisibility(input ? View.VISIBLE : View.GONE);
        }
        if (aiImageGalleryContainer != null) {
            aiImageGalleryContainer.setVisibility(input ? View.GONE : View.VISIBLE);
        }
        if (aiImageDoneRow != null) {
            aiImageDoneRow.setVisibility(input ? View.GONE : View.VISIBLE);
        }
        if (aiImageDialogRoot != null) {
            aiImageDialogRoot.post(this::applyAiImageDialogSize);
        }
    }

    private void startAiImageGeneration() {
        String prompt = aiImagePromptEdit != null ? aiImagePromptEdit.getText().toString().trim() : "";
        if (prompt.isEmpty()) {
            toastTodo("请输入图片的引导词");
            return;
        }
        int ratioIdx = aiImageRatioSpinner != null ? aiImageRatioSpinner.getSelectedItemPosition() : 0;
        if (ratioIdx < 0 || ratioIdx >= AI_IMAGE_SIZES.length) {
            ratioIdx = 0;
        }
        String size = AI_IMAGE_SIZES[ratioIdx];

        // 校验 MODEL_IMAGE 配置
        SharedPreferences modelPrefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
        String endpoint = modelPrefs.getString("AI_MODEL_IMAGE_url", "");
        String apiKey = modelPrefs.getString("AI_MODEL_IMAGE_api_key", "");
        String model = modelPrefs.getString("AI_MODEL_IMAGE_model_name", "");
        endpoint = endpoint == null ? "" : endpoint.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null ? "" : model.trim();
        // endpoint 兜底规范化：图片模型确保以 /images/generations 结尾
        endpoint = normalizeEndpoint(endpoint, "/images/generations");
        if (endpoint.isEmpty()) {
            toastTodo("请先在设置中配置图片生成模型的接口地址");
            return;
        }
        if (apiKey.isEmpty()) {
            toastTodo("请先在设置中配置图片生成模型的 API Key");
            return;
        }

        cancelAiImageRequest();
        final String requestId = "aiimage-" + System.currentTimeMillis();
        aiImageActiveRequestId = requestId;
        Log.i(TAG, "ai_image_request_start requestId=" + requestId + " size=" + size + " promptChars=" + prompt.length());

        final String finalModel = model;
        final String finalEndpoint = endpoint;
        final String finalApiKey = apiKey;
        final String finalSize = size;
        final int expectedCount = 4;

        // 预占位：4 个槽位先填 null（生成中），gallery 显示占位图
        aiImageBase64List.clear();
        for (int i = 0; i < expectedCount; i++) {
            aiImageBase64List.add(null);
        }
        aiImageSelectedIndex = 0;

        // 立即进入结果页：4 格显示「生成中」占位，不再在输入页转圈
        renderAiImageGallery();
        switchAiImageStage(AI_IMAGE_STAGE_RESULT);

        // 先发一次 n=4 的请求；若接口不支持 n 只返回 <4 张，再并发补齐
        requestAiImageOnce(finalEndpoint, finalApiKey, finalModel, prompt, finalSize, expectedCount,
                new AiRequestManager.ImageGenCallback() {
                    @Override
                    public void onImages(java.util.List<String> base64List) {
                        runOnUiThread(() -> {
                            if (base64List == null || base64List.isEmpty()) {
                                toastTodo("图片生成返回为空");
                                switchAiImageStage(AI_IMAGE_STAGE_INPUT);
                                return;
                            }
                            // 填充已返回的图到前 N 个槽位
                            int filled = Math.min(base64List.size(), expectedCount);
                            for (int i = 0; i < filled; i++) {
                                aiImageBase64List.set(i, base64List.get(i));
                            }
                            aiImageSelectedIndex = 0;
                            renderAiImageGallery();
                            Log.i(TAG, "ai_image_done count=" + base64List.size());

                            // 兜底：返回不足 4 张，并发补齐剩余槽位
                            if (filled < expectedCount) {
                                int remaining = expectedCount - filled;
                                Log.i(TAG, "ai_image_fallback_concurrent remaining=" + remaining
                                        + " returned=" + base64List.size());
                                for (int i = 0; i < remaining; i++) {
                                    final int slot = filled + i;
                                    requestAiImageOnce(finalEndpoint, finalApiKey, finalModel, prompt,
                                            finalSize, 1, new AiRequestManager.ImageGenCallback() {
                                                @Override
                                                public void onImages(java.util.List<String> extra) {
                                                    runOnUiThread(() -> {
                                                        if (extra != null && !extra.isEmpty()
                                                                && slot < aiImageBase64List.size()) {
                                                            aiImageBase64List.set(slot, extra.get(0));
                                                            renderAiImageGallery();
                                                            Log.i(TAG, "ai_image_fallback_slot_done slot=" + slot);
                                                        }
                                                    });
                                                }

                                                @Override
                                                public void onError(String code, String message) {
                                                    runOnUiThread(() -> {
                                                        Log.w(TAG, "ai_image_fallback_slot_error slot=" + slot
                                                                + " code=" + code);
                                                        // 该槽位失败保留占位，不阻断其它
                                                    });
                                                }
                                            });
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(String code, String message) {
                        runOnUiThread(() -> {
                            String safeMsg = message == null ? "" : (message.length() > 80 ? message.substring(0, 80) + "..." : message);
                            toastTodo("图片生成失败：" + safeMsg);
                            switchAiImageStage(AI_IMAGE_STAGE_INPUT);
                            Log.i(TAG, "ai_image_error code=" + code + " msg=" + safeMsg);
                        });
                    }
                });
    }

    /** 发起一次图片生成请求（独立线程 + 独立 session，便于并发与取消）。 */
    private void requestAiImageOnce(String endpoint, String apiKey, String model, String prompt,
                                    String size, int n, AiRequestManager.ImageGenCallback callback) {
        AiRequestSession session = new AiRequestSession();
        aiImageSessions.add(session);
        new Thread(() -> aiRequestManager.executeImageGen(endpoint, apiKey, model, prompt,
                size, n, session, callback), "ai-image-gen-" + n).start();
    }

    private void renderAiImageGallery() {
        // 主图：第 0 个槽位；为 null 时显示生成中占位
        if (aiImageMainView != null) {
            if (aiImageBase64List.size() > 0 && aiImageBase64List.get(0) != null) {
                aiImageMainView.setImageBitmap(base64ToBitmap(aiImageBase64List.get(0)));
            } else {
                aiImageMainView.setImageBitmap(createGeneratingPlaceholder());
            }
        }
        for (int i = 0; i < aiImageThumbViews.length; i++) {
            if (aiImageThumbViews[i] == null) {
                continue;
            }
            int idx = i + 1;
            if (idx < aiImageBase64List.size()) {
                if (aiImageBase64List.get(idx) != null) {
                    aiImageThumbViews[i].setVisibility(View.VISIBLE);
                    aiImageThumbViews[i].setImageBitmap(base64ToBitmap(aiImageBase64List.get(idx)));
                } else {
                    // 生成中占位
                    aiImageThumbViews[i].setVisibility(View.VISIBLE);
                    aiImageThumbViews[i].setImageBitmap(createGeneratingPlaceholder());
                }
            } else {
                aiImageThumbViews[i].setVisibility(View.INVISIBLE);
            }
        }
    }

    private void showAiImagePreview(int index) {
        if (index < 0 || index >= aiImageBase64List.size()) {
            return;
        }
        // 生成中的槽位不支持预览
        if (aiImageBase64List.get(index) == null) {
            toastTodo("图片生成中，请稍候");
            return;
        }
        aiImagePreviewCurrentIndex = index;
        aiImageSelectedIndex = index;
        if (aiImagePreviewDialog != null && aiImagePreviewDialog.isShowing()) {
            aiImagePreviewDialog.dismiss();
        }
        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_ai_image_preview, null);
        final ImageView previewImg = root.findViewById(R.id.ai_image_preview_img);
        final LinearLayout dotsContainer = root.findViewById(R.id.ai_image_preview_dots);

        // 初始渲染当前大图 + 圆点
        renderPreviewImage(previewImg, aiImagePreviewCurrentIndex);
        renderPreviewDots(dotsContainer, aiImagePreviewCurrentIndex, aiImageBase64List.size());

        // 返回箭头：回到 gallery
        root.findViewById(R.id.ai_image_preview_back_btn).setOnClickListener(v -> dialog.dismiss());
        // 关闭 X：整体退出 AI 图片
        root.findViewById(R.id.ai_image_preview_close_btn).setOnClickListener(v -> {
            dialog.dismiss();
            if (aiImageDialog != null && aiImageDialog.isShowing()) {
                aiImageDialog.dismiss();
            }
        });
        // 重新生成（当前单张）
        root.findViewById(R.id.ai_image_preview_regenerate_btn).setOnClickListener(v -> {
            regenerateSingleAiImage(aiImagePreviewCurrentIndex, previewImg, dotsContainer);
        });
        // 插入文档（当前单张）
        root.findViewById(R.id.ai_image_preview_apply_btn).setOnClickListener(v -> {
            aiImageSelectedIndex = aiImagePreviewCurrentIndex;
            applyAiImageResult();
        });

        // 左右滑动切换
        final android.view.GestureDetector detector = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (Math.abs(velocityX) < Math.abs(velocityY)) {
                            return false;
                        }
                        int total = aiImageBase64List.size();
                        if (total == 0) return false;
                        int next = aiImagePreviewCurrentIndex;
                        if (velocityX < -300) {
                            next = Math.min(total - 1, aiImagePreviewCurrentIndex + 1);
                        } else if (velocityX > 300) {
                            next = Math.max(0, aiImagePreviewCurrentIndex - 1);
                        }
                        if (next != aiImagePreviewCurrentIndex) {
                            aiImagePreviewCurrentIndex = next;
                            aiImageSelectedIndex = next;
                            renderPreviewImage(previewImg, next);
                            renderPreviewDots(dotsContainer, next, total);
                        }
                        return true;
                    }
                });
        if (previewImg != null) {
            previewImg.setOnTouchListener((v, ev) -> {
                detector.onTouchEvent(ev);
                return true;
            });
        }

        dialog.setOnDismissListener(d -> {
            aiImagePreviewDialog = null;
            Log.i(TAG, "ai_image_preview_dismissed index=" + index);
        });
        dialog.setView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            // 窗口化：居中白色圆角卡片，非整屏覆盖
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int margin = dpToPx(48);
            int targetWidth = Math.min(dpToPx(670), dm.widthPixels - margin);
            targetWidth = Math.max(targetWidth, dpToPx(280));
            int targetHeight = Math.min(dpToPx(756), (int) (dm.heightPixels * 0.80f));
            targetHeight = Math.max(targetHeight, dpToPx(360));
            targetHeight = Math.min(targetHeight, dm.heightPixels - dpToPx(24));
            dialog.getWindow().setLayout(targetWidth, targetHeight);
        }
        aiImagePreviewDialog = dialog;
        Log.i(TAG, "ai_image_preview_show index=" + index);
    }

    /** 渲染预览大图：null 槽位显示生成中占位。 */
    private void renderPreviewImage(ImageView previewImg, int index) {
        if (previewImg == null) return;
        if (index < 0 || index >= aiImageBase64List.size() || aiImageBase64List.get(index) == null) {
            previewImg.setImageBitmap(createGeneratingPlaceholder());
        } else {
            previewImg.setImageBitmap(base64ToBitmap(aiImageBase64List.get(index)));
        }
    }

    /** 渲染分页圆点：当前为蓝色横条，其余灰色小圆。 */
    private void renderPreviewDots(LinearLayout container, int current, int total) {
        if (container == null) return;
        container.removeAllViews();
        int dp4 = dpToPx(4);
        int dp8 = dpToPx(8);
        for (int i = 0; i < total; i++) {
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            if (i == current) {
                dot.setColor(0xFF3399FF);
                dot.setSize(dpToPx(18), dp4);
                dot.setCornerRadius(dp4 / 2f);
            } else {
                dot.setColor(0xFFCCCCCC);
                dot.setSize(dp8, dp8);
                dot.setCornerRadius(dp8 / 2f);
            }
            android.view.View item = new android.view.View(this);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    i == current ? dpToPx(18) : dp8,
                    i == current ? dp4 : dp8);
            if (i > 0) lp.setMarginStart(dp4);
            item.setLayoutParams(lp);
            item.setBackground(dot);
            container.addView(item);
        }
    }

    /** 重新生成当前单张图片（仅替换该槽位）。 */
    private void regenerateSingleAiImage(int slot, ImageView previewImg, View dotsContainer) {
        if (slot < 0 || slot >= aiImageBase64List.size()) {
            toastTodo("无效的图片位置");
            return;
        }
        String prompt = aiImagePromptEdit != null ? aiImagePromptEdit.getText().toString().trim() : "";
        if (prompt.isEmpty()) {
            toastTodo("引导词为空，无法重新生成");
            return;
        }
        int ratioIdx = aiImageRatioSpinner != null ? aiImageRatioSpinner.getSelectedItemPosition() : 0;
        if (ratioIdx < 0 || ratioIdx >= AI_IMAGE_SIZES.length) {
            ratioIdx = 0;
        }
        String size = AI_IMAGE_SIZES[ratioIdx];

        SharedPreferences modelPrefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
        String endpoint = modelPrefs.getString("AI_MODEL_IMAGE_url", "");
        String apiKey = modelPrefs.getString("AI_MODEL_IMAGE_api_key", "");
        String model = modelPrefs.getString("AI_MODEL_IMAGE_model_name", "");
        endpoint = endpoint == null ? "" : endpoint.trim();
        apiKey = apiKey == null ? "" : apiKey.trim();
        model = model == null ? "" : model.trim();
        endpoint = normalizeEndpoint(endpoint, "/images/generations");
        if (endpoint.isEmpty()) {
            toastTodo("请先在设置中配置图片生成模型的接口地址");
            return;
        }
        if (apiKey.isEmpty()) {
            toastTodo("请先在设置中配置图片生成模型的 API Key");
            return;
        }

        // 该槽位置空，预览与 gallery 显示生成中占位
        aiImageBase64List.set(slot, null);
        renderPreviewImage(previewImg, slot);
        renderAiImageGallery();
        Log.i(TAG, "ai_image_regen_single_start slot=" + slot);

        final ImageView finalPreviewImg = previewImg;
        final View finalDots = dotsContainer;
        final int finalSlot = slot;
        requestAiImageOnce(endpoint, apiKey, model, prompt, size, 1,
                new AiRequestManager.ImageGenCallback() {
                    @Override
                    public void onImages(java.util.List<String> base64List) {
                        runOnUiThread(() -> {
                            if (base64List != null && !base64List.isEmpty()
                                    && finalSlot < aiImageBase64List.size()) {
                                aiImageBase64List.set(finalSlot, base64List.get(0));
                                // 若当前预览仍在该槽位，刷新大图
                                if (aiImagePreviewCurrentIndex == finalSlot) {
                                    renderPreviewImage(finalPreviewImg, finalSlot);
                                }
                                renderAiImageGallery();
                                Log.i(TAG, "ai_image_regen_single_done slot=" + finalSlot);
                            } else {
                                toastTodo("重新生成返回为空");
                            }
                        });
                    }

                    @Override
                    public void onError(String code, String message) {
                        runOnUiThread(() -> {
                            String safeMsg = message == null ? "" : (message.length() > 80 ? message.substring(0, 80) + "..." : message);
                            toastTodo("重新生成失败：" + safeMsg);
                            Log.w(TAG, "ai_image_regen_single_error slot=" + finalSlot + " code=" + code);
                        });
                    }
                });
    }

    private void applyAiImageResult() {
        if (aiImageSelectedIndex < 0 || aiImageSelectedIndex >= aiImageBase64List.size()) {
            toastTodo("请先选择一张图片");
            return;
        }
        String base64 = aiImageBase64List.get(aiImageSelectedIndex);
        if (base64 == null) {
            toastTodo("该图片生成中，请稍候");
            return;
        }
        String fileName = "ai_image_" + System.currentTimeMillis() + ".png";
        Log.i(TAG, "ai_image_apply index=" + aiImageSelectedIndex + " name=" + fileName);
        // 先关闭预览弹窗，避免盖在主弹窗之上导致看不到插入效果
        if (aiImagePreviewDialog != null && aiImagePreviewDialog.isShowing()) {
            aiImagePreviewDialog.dismiss();
            aiImagePreviewDialog = null;
        }
        if (aiImageDialog != null) {
            aiImageDialog.dismiss();
        }
        insertImageBase64(base64, fileName);
    }

    private void cancelAiImageRequest() {
        for (AiRequestSession s : aiImageSessions) {
            if (s != null) {
                s.cancel();
            }
        }
        aiImageSessions.clear();
        aiImageActiveRequestId = "";
    }

    private String getTextOperateTitle(String mode) {
        if (AiChatCoordinator.MODE_EXPAND.equals(mode)) {
            return "文案扩写";
        }
        if (AiChatCoordinator.MODE_CONDENSE.equals(mode)) {
            return "文案缩写";
        }
        if (AiChatCoordinator.MODE_POLISH.equals(mode)) {
            return "文案润色";
        }
        if (AiChatCoordinator.MODE_REWRITE.equals(mode)) {
            return "文案重写";
        }
        return "文案处理";
    }

    private void showTextOperateDialog(String mode, String selection) {
        if (textOperateDialog != null && textOperateDialog.isShowing()) {
            textOperateDialog.dismiss();
        }
        textOperateMode = mode;
        textOperateSelection = selection != null ? selection : "";
        pendingTextOperateResult = null;
        pendingPolishStyle = AiChatCoordinator.POLISH_STYLE_QUICK;
        pendingTextOperateRequirement = "";

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_text_operate, null);
        textOperateDialogRoot = root;

        textOperateTitle = root.findViewById(R.id.text_op_title);
        textOperateInputContainer = root.findViewById(R.id.text_op_input_container);
        textOperateResultCard = root.findViewById(R.id.text_op_result_card);
        textOperateResultText = root.findViewById(R.id.text_op_result_text);
        textOperateGenerateBtn = root.findViewById(R.id.text_op_generate_btn);
        textOperateCopyRow = root.findViewById(R.id.text_op_copy_row);
        textOperateDoneRow = root.findViewById(R.id.text_op_done_row);
        textOperateRequirementEdit = null;
        textOperatePolishStyleLabel = null;
        textOperatePolishStyleCard = null;

        if (textOperateTitle != null) {
            textOperateTitle.setText(getTextOperateTitle(mode));
        }

        textOperateInputContainer.removeAllViews();
        if (AiChatCoordinator.MODE_POLISH.equals(mode)) {
            View polishInput = getLayoutInflater().inflate(R.layout.lolib_text_op_polish, textOperateInputContainer, false);
            textOperateInputContainer.addView(polishInput);
            textOperatePolishStyleCard = polishInput.findViewById(R.id.text_op_polish_style_card);
            textOperatePolishStyleLabel = polishInput.findViewById(R.id.text_op_polish_style_label);
            if (textOperatePolishStyleLabel != null) {
                PolishStyleRegistry.PolishStyle style = PolishStyleRegistry.getDefault();
                textOperatePolishStyleLabel.setText(style.label);
            }
            if (textOperatePolishStyleCard != null) {
                textOperatePolishStyleCard.setOnClickListener(v -> showPolishStylePicker());
            }
        } else {
            View reqInput = getLayoutInflater().inflate(R.layout.lolib_text_op_requirement, textOperateInputContainer, false);
            textOperateInputContainer.addView(reqInput);
            textOperateRequirementEdit = reqInput.findViewById(R.id.text_op_requirement_edit);
            if (textOperateRequirementEdit != null) {
                String hint;
                if (AiChatCoordinator.MODE_CONDENSE.equals(mode)) {
                    hint = "请输入文案缩写要求";
                } else if (AiChatCoordinator.MODE_REWRITE.equals(mode)) {
                    hint = "请输入文案重写要求";
                } else {
                    hint = "请输入文案扩写要求";
                }
                textOperateRequirementEdit.setHint(hint);
            }
        }

        root.findViewById(R.id.text_op_close_btn).setOnClickListener(v -> dialog.dismiss());
        textOperateGenerateBtn.setOnClickListener(v -> startTextOperateGeneration());
        root.findViewById(R.id.text_op_regenerate_btn).setOnClickListener(v -> startTextOperateGeneration());
        root.findViewById(R.id.text_op_apply_btn).setOnClickListener(v -> applyTextOperateResult());
        textOperateCopyRow.setOnClickListener(v -> copyTextOperateResult());

        dialog.setOnDismissListener(d -> {
            Log.i(TAG, "text_op_dialog_dismissed mode=" + textOperateMode);
            aiStreamingViewByRequestId.remove(textOperateActiveRequestId);
            textOperateActiveRequestId = "";
            textOperateDialog = null;
            textOperateDialogRoot = null;
        });
        dialog.setView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        textOperateDialog = dialog;
        switchTextOperateStage(TEXT_OP_STAGE_INPUT);
        root.post(this::applyTextOperateDialogSize);
        Log.i(TAG, "text_op_dialog_show mode=" + mode + " selectionChars=" + textOperateSelection.length());
    }

    private void applyTextOperateDialogSize() {
        if (textOperateDialog == null || textOperateDialog.getWindow() == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = dpToPx(48);
        int targetWidth = Math.min(dpToPx(670), dm.widthPixels - margin);
        targetWidth = Math.max(targetWidth, dpToPx(280));

        boolean compactInputStage = AiChatCoordinator.MODE_POLISH.equals(textOperateMode)
                && textOperateResultCard != null
                && textOperateResultCard.getVisibility() != View.VISIBLE;
        int targetHeight;
        if (compactInputStage) {
            // 润色输入态仅需少量控件 + 按钮，使用紧凑高度避免大面积留白
            targetHeight = dpToPx(340);
        } else {
            targetHeight = Math.min(dpToPx(756), (int) (dm.heightPixels * 0.80f));
            targetHeight = Math.max(targetHeight, dpToPx(320));
            targetHeight = Math.min(targetHeight, dm.heightPixels - dpToPx(24));
        }

        textOperateDialog.getWindow().setLayout(targetWidth, targetHeight);
        if (textOperateDialogRoot != null) {
            ViewGroup.LayoutParams lp = textOperateDialogRoot.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(targetWidth, targetHeight);
            } else {
                lp.width = targetWidth;
                lp.height = targetHeight;
            }
            textOperateDialogRoot.setLayoutParams(lp);
        }
        applyTextOperateInputLayout();
        Log.d(TAG, "text_op_dialog_size w=" + targetWidth + " h=" + targetHeight
                + " compactInput=" + compactInputStage);
    }

    /** 润色输入态：输入区垂直居中；扩写/缩写/重写：输入区占满剩余空间 */
    private void applyTextOperateInputLayout() {
        if (textOperateInputContainer == null) {
            return;
        }
        ViewGroup.LayoutParams rawLp = textOperateInputContainer.getLayoutParams();
        if (!(rawLp instanceof LinearLayout.LayoutParams)) {
            return;
        }
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawLp;
        boolean compactInput = AiChatCoordinator.MODE_POLISH.equals(textOperateMode)
                && textOperateResultCard != null
                && textOperateResultCard.getVisibility() != View.VISIBLE;
        if (compactInput) {
            lp.height = 0;
            lp.weight = 1f;
            lp.topMargin = 0;
            lp.bottomMargin = 0;
        } else {
            lp.height = 0;
            lp.weight = 1f;
            lp.topMargin = dpToPx(16);
            lp.bottomMargin = 0;
        }
        textOperateInputContainer.setLayoutParams(lp);

        if (textOperateInputContainer.getChildCount() > 0) {
            View child = textOperateInputContainer.getChildAt(0);
            FrameLayout.LayoutParams childLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    compactInput ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
                    compactInput ? Gravity.CENTER : (Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            child.setLayoutParams(childLp);
        }
    }

    private void scrollTextOperateResultToBottom() {
        if (textOperateResultCard == null || textOperateResultCard.getVisibility() != View.VISIBLE) {
            return;
        }
        textOperateResultCard.post(() -> textOperateResultCard.fullScroll(View.FOCUS_DOWN));
    }

    private void showPolishStylePicker() {
        if (textOperatePolishStyleLabel == null) {
            return;
        }
        PopupMenu popup = new PopupMenu(this, textOperatePolishStyleLabel);
        PolishStyleRegistry.PolishStyle[] styles = PolishStyleRegistry.getStyles();
        for (int i = 0; i < styles.length; i++) {
            popup.getMenu().add(0, i, i, styles[i].label);
        }
        popup.setOnMenuItemClickListener(item -> {
            int idx = item.getItemId();
            if (idx >= 0 && idx < styles.length) {
                pendingPolishStyle = styles[idx].key;
                textOperatePolishStyleLabel.setText(styles[idx].label);
                Log.i(TAG, "text_op_polish_style_selected style=" + pendingPolishStyle);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void startTextOperateGeneration() {
        if (textOperateMode == null || textOperateSelection == null || textOperateSelection.isEmpty()) {
            toastTodo("请先选择文本");
            return;
        }
        if (AiChatCoordinator.MODE_POLISH.equals(textOperateMode) && textOperateSelection.length() > 5000) {
            Toast.makeText(this, "选中内容不可超过5000字符", Toast.LENGTH_SHORT).show();
            return;
        }
        if (AiChatCoordinator.MODE_POLISH.equals(textOperateMode)) {
            pendingTextOperateRequirement = "";
        } else if (textOperateRequirementEdit != null) {
            pendingTextOperateRequirement = textOperateRequirementEdit.getText().toString().trim();
        } else {
            pendingTextOperateRequirement = "";
        }
        sendTextOperateRequest();
    }

    private void sendTextOperateRequest() {
        toastTodo("正在生成...");
        if (textOperateResultText != null) {
            textOperateResultText.setText("正在生成...");
        }
        switchTextOperateStage(TEXT_OP_STAGE_RESULT);

        try {
            JSONObject request = new JSONObject();
            String requestId = "textop-" + UUID.randomUUID().toString();
            request.put("requestId", requestId);
            request.put("taskType", textOperateMode);
            request.put("selection", textOperateSelection);
            request.put("source", "android-text-op");

            JSONObject context = new JSONObject();
            context.put("modelMode", "base");
            if (AiChatCoordinator.MODE_POLISH.equals(textOperateMode)) {
                context.put("polishStyle", pendingPolishStyle);
            } else {
                context.put("requirement", pendingTextOperateRequirement);
            }
            request.put("context", context);
            request.put("history", new JSONArray());

            aiActiveRequestId = requestId;
            aiStreamingRequestId = requestId;
            aiRequestModeById.put(requestId, textOperateMode);
            aiTextByRequestId.put(requestId, new StringBuilder());
            aiStreamingViewByRequestId.remove(textOperateActiveRequestId);
            textOperateActiveRequestId = requestId;
            if (textOperateResultText != null) {
                aiStreamingViewByRequestId.put(requestId, textOperateResultText);
            }

            Log.i(TAG, "ai_text_op_start requestId=" + requestId + " mode=" + textOperateMode);
            startAiRequestSession(request, -1);
        } catch (JSONException e) {
            Log.e(TAG, "ai_text_op_request_error", e);
            toastTodo("启动生成失败");
            switchTextOperateStage(TEXT_OP_STAGE_INPUT);
        }
    }

    private void showTextOperateResult(String text) {
        pendingTextOperateResult = text;
        if (textOperateResultText != null) {
            textOperateResultText.setText(text);
        }
        switchTextOperateStage(TEXT_OP_STAGE_RESULT);
        scrollTextOperateResultToBottom();
    }

    private void switchTextOperateStage(int stage) {
        boolean input = stage == TEXT_OP_STAGE_INPUT;
        boolean result = stage == TEXT_OP_STAGE_RESULT;

        if (textOperateInputContainer != null) {
            textOperateInputContainer.setVisibility(input ? View.VISIBLE : View.GONE);
        }
        if (textOperateGenerateBtn != null) {
            textOperateGenerateBtn.setVisibility(input ? View.VISIBLE : View.GONE);
        }
        if (textOperateResultCard != null) {
            textOperateResultCard.setVisibility(result ? View.VISIBLE : View.GONE);
        }
        if (textOperateCopyRow != null) {
            textOperateCopyRow.setVisibility(result ? View.VISIBLE : View.GONE);
        }
        if (textOperateDoneRow != null) {
            textOperateDoneRow.setVisibility(result ? View.VISIBLE : View.GONE);
        }
        if (textOperateDialogRoot != null) {
            textOperateDialogRoot.post(this::applyTextOperateDialogSize);
        }
    }

    private void applyTextOperateResult() {
        Log.i(TAG, "ai_text_op_apply mode=" + textOperateMode
                + " chars=" + (pendingTextOperateResult != null ? pendingTextOperateResult.length() : 0));
        if (pendingTextOperateResult == null || pendingTextOperateResult.isEmpty()) {
            toastTodo("结果为空");
            return;
        }
        final String text = pendingTextOperateResult;
        ensureEditModeThen(() -> {
            pasteAiTextAsHtml(text);
            Log.i(TAG, "ai_text_op_inserted mode=" + textOperateMode
                    + " chars=" + text.length() + " format=html");
            toastTodo("已插入文档");
            if (textOperateDialog != null) {
                textOperateDialog.dismiss();
            }
        });
        pendingTextOperateResult = null;
    }

    private void copyTextOperateResult() {
        if (pendingTextOperateResult == null || pendingTextOperateResult.isEmpty()) {
            toastTodo("暂无结果可复制");
            return;
        }
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("text_op", pendingTextOperateResult));
            toastTodo("已复制到剪贴板");
            Log.i(TAG, "ai_text_op_copied chars=" + pendingTextOperateResult.length());
        }
    }

    // ==================== 翻译弹窗相关方法 ====================

    private void showTranslateDialog(String selection) {
        if (translateDialog != null && translateDialog.isShowing()) {
            translateDialog.dismiss();
        }
        pendingTranslateResult = null;
        pendingTranslateSourceLang = AiChatCoordinator.TRANSLATE_LANG_AUTO;
        pendingTranslateTargetLang = AiChatCoordinator.TRANSLATE_LANG_ZH;

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_translate, null);
        translateDialogRoot = root;

        translateSourceLabel = root.findViewById(R.id.translate_source_label);
        translateTargetLabel = root.findViewById(R.id.translate_target_label);
        translateSourceEdit = root.findViewById(R.id.translate_source_edit);
        translateResultCard = root.findViewById(R.id.translate_result_card);
        translateResultText = root.findViewById(R.id.translate_result_text);
        translateGenerateBtn = root.findViewById(R.id.translate_generate_btn);
        translateCopyRow = root.findViewById(R.id.translate_copy_row);
        translateDoneRow = root.findViewById(R.id.translate_done_row);

        if (translateSourceEdit != null) {
            translateSourceEdit.setText(selection != null ? selection : "");
        }
        updateTranslateLanguageLabels();

        root.findViewById(R.id.translate_close_btn).setOnClickListener(v -> dialog.dismiss());
        root.findViewById(R.id.translate_source_card).setOnClickListener(v -> showTranslateSourcePicker());
        root.findViewById(R.id.translate_target_card).setOnClickListener(v -> showTranslateTargetPicker());
        root.findViewById(R.id.translate_swap_btn).setOnClickListener(v -> swapTranslateLanguages());
        translateGenerateBtn.setOnClickListener(v -> startTranslateGeneration());
        root.findViewById(R.id.translate_regenerate_btn).setOnClickListener(v -> startTranslateGeneration());
        root.findViewById(R.id.translate_apply_btn).setOnClickListener(v -> applyTranslateResult());
        translateCopyRow.setOnClickListener(v -> copyTranslateResult());

        dialog.setOnDismissListener(d -> {
            Log.i(TAG, "translate_dialog_dismissed");
            aiStreamingViewByRequestId.remove(translateActiveRequestId);
            translateActiveRequestId = "";
            translateDialog = null;
            translateDialogRoot = null;
        });
        dialog.setView(root);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        translateDialog = dialog;
        switchTranslateStage(TRANSLATE_STAGE_INPUT);
        root.post(this::applyTranslateDialogSize);
        Log.i(TAG, "translate_dialog_show selectionChars=" + (selection != null ? selection.length() : 0));
    }

    private void updateTranslateLanguageLabels() {
        TranslateLanguageRegistry.TranslateLanguage source =
                TranslateLanguageRegistry.findByKey(pendingTranslateSourceLang);
        TranslateLanguageRegistry.TranslateLanguage target =
                TranslateLanguageRegistry.findByKey(pendingTranslateTargetLang);
        if (translateSourceLabel != null && source != null) {
            translateSourceLabel.setText(source.label);
        }
        if (translateTargetLabel != null && target != null) {
            translateTargetLabel.setText(target.label);
        }
    }

    private void applyTranslateDialogSize() {
        if (translateDialog == null || translateDialog.getWindow() == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = dpToPx(48);
        int targetWidth = Math.min(dpToPx(670), dm.widthPixels - margin);
        targetWidth = Math.max(targetWidth, dpToPx(280));

        int targetHeight = Math.min(dpToPx(756), (int) (dm.heightPixels * 0.80f));
        targetHeight = Math.max(targetHeight, dpToPx(320));
        targetHeight = Math.min(targetHeight, dm.heightPixels - dpToPx(24));

        translateDialog.getWindow().setLayout(targetWidth, targetHeight);
        if (translateDialogRoot != null) {
            ViewGroup.LayoutParams lp = translateDialogRoot.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(targetWidth, targetHeight);
            } else {
                lp.width = targetWidth;
                lp.height = targetHeight;
            }
            translateDialogRoot.setLayoutParams(lp);
        }
        Log.d(TAG, "translate_dialog_size w=" + targetWidth + " h=" + targetHeight);
    }

    private void scrollTranslateResultToBottom() {
        if (translateResultCard == null || translateResultCard.getVisibility() != View.VISIBLE) {
            return;
        }
        translateResultCard.post(() -> translateResultCard.fullScroll(View.FOCUS_DOWN));
    }

    private void showTranslateSourcePicker() {
        if (translateSourceLabel == null) {
            return;
        }
        PopupMenu popup = new PopupMenu(this, translateSourceLabel);
        TranslateLanguageRegistry.TranslateLanguage[] langs = TranslateLanguageRegistry.getSourceLanguages();
        for (int i = 0; i < langs.length; i++) {
            popup.getMenu().add(0, i, i, langs[i].label);
        }
        popup.setOnMenuItemClickListener(item -> {
            int idx = item.getItemId();
            if (idx >= 0 && idx < langs.length) {
                pendingTranslateSourceLang = langs[idx].key;
                if (pendingTranslateSourceLang.equals(pendingTranslateTargetLang)) {
                    pendingTranslateTargetLang = AiChatCoordinator.TRANSLATE_LANG_EN;
                }
                updateTranslateLanguageLabels();
                Log.i(TAG, "translate_source_selected lang=" + pendingTranslateSourceLang);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showTranslateTargetPicker() {
        if (translateTargetLabel == null) {
            return;
        }
        PopupMenu popup = new PopupMenu(this, translateTargetLabel);
        java.util.List<TranslateLanguageRegistry.TranslateLanguage> langs =
                TranslateLanguageRegistry.getTargetLanguages();
        for (int i = 0; i < langs.size(); i++) {
            popup.getMenu().add(0, i, i, langs.get(i).label);
        }
        popup.setOnMenuItemClickListener(item -> {
            int idx = item.getItemId();
            if (idx >= 0 && idx < langs.size()) {
                pendingTranslateTargetLang = langs.get(idx).key;
                if (pendingTranslateTargetLang.equals(pendingTranslateSourceLang)) {
                    pendingTranslateSourceLang = AiChatCoordinator.TRANSLATE_LANG_AUTO;
                }
                updateTranslateLanguageLabels();
                Log.i(TAG, "translate_target_selected lang=" + pendingTranslateTargetLang);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void swapTranslateLanguages() {
        if (AiChatCoordinator.TRANSLATE_LANG_AUTO.equals(pendingTranslateSourceLang)) {
            Toast.makeText(this, "自动识别源语言无法交换", Toast.LENGTH_SHORT).show();
            return;
        }
        String tmp = pendingTranslateSourceLang;
        pendingTranslateSourceLang = pendingTranslateTargetLang;
        pendingTranslateTargetLang = tmp;
        updateTranslateLanguageLabels();
        Log.i(TAG, "translate_lang_swapped src=" + pendingTranslateSourceLang
                + " tgt=" + pendingTranslateTargetLang);
    }

    private void startTranslateGeneration() {
        if (translateSourceEdit == null) {
            return;
        }
        String text = translateSourceEdit.getText().toString().trim();
        if (text.isEmpty()) {
            toastTodo("请输入原文");
            return;
        }
        sendTranslateRequest(text);
    }

    private void sendTranslateRequest(String text) {
        toastTodo("正在翻译...");
        if (translateResultText != null) {
            translateResultText.setText("正在翻译...");
        }
        switchTranslateStage(TRANSLATE_STAGE_RESULT);

        try {
            JSONObject request = new JSONObject();
            String requestId = "translate-" + UUID.randomUUID().toString();
            request.put("requestId", requestId);
            request.put("taskType", AiChatCoordinator.MODE_TRANSLATE);
            request.put("selection", text);
            request.put("source", "android-translate");

            JSONObject context = new JSONObject();
            context.put("modelMode", "base");
            context.put("sourceLang", pendingTranslateSourceLang);
            context.put("targetLang", pendingTranslateTargetLang);
            request.put("context", context);
            request.put("history", new JSONArray());

            aiActiveRequestId = requestId;
            aiStreamingRequestId = requestId;
            aiRequestModeById.put(requestId, AiChatCoordinator.MODE_TRANSLATE);
            aiTextByRequestId.put(requestId, new StringBuilder());
            aiStreamingViewByRequestId.remove(translateActiveRequestId);
            translateActiveRequestId = requestId;
            if (translateResultText != null) {
                aiStreamingViewByRequestId.put(requestId, translateResultText);
            }

            Log.i(TAG, "ai_translate_start requestId=" + requestId
                    + " src=" + pendingTranslateSourceLang + " tgt=" + pendingTranslateTargetLang);
            startAiRequestSession(request, -1);
        } catch (JSONException e) {
            Log.e(TAG, "ai_translate_request_error", e);
            toastTodo("启动翻译失败");
            switchTranslateStage(TRANSLATE_STAGE_INPUT);
        }
    }

    private void showTranslateResult(String text) {
        pendingTranslateResult = text;
        if (translateResultText != null) {
            translateResultText.setText(text);
        }
        switchTranslateStage(TRANSLATE_STAGE_RESULT);
        scrollTranslateResultToBottom();
    }

    private void switchTranslateStage(int stage) {
        boolean input = stage == TRANSLATE_STAGE_INPUT;
        boolean result = stage == TRANSLATE_STAGE_RESULT;

        if (translateGenerateBtn != null) {
            translateGenerateBtn.setVisibility(input ? View.VISIBLE : View.GONE);
        }
        if (translateResultCard != null) {
            translateResultCard.setVisibility(result ? View.VISIBLE : View.GONE);
        }
        if (translateCopyRow != null) {
            translateCopyRow.setVisibility(result ? View.VISIBLE : View.GONE);
        }
        if (translateDoneRow != null) {
            translateDoneRow.setVisibility(result ? View.VISIBLE : View.GONE);
        }
        if (result && translateDialogRoot != null) {
            translateDialogRoot.post(this::applyTranslateDialogSize);
        }
    }

    private void applyTranslateResult() {
        Log.i(TAG, "ai_translate_apply chars="
                + (pendingTranslateResult != null ? pendingTranslateResult.length() : 0));
        if (pendingTranslateResult == null || pendingTranslateResult.isEmpty()) {
            toastTodo("译文为空");
            return;
        }
        final String text = pendingTranslateResult;
        ensureEditModeThen(() -> {
            pasteAiTextAsHtml(text);
            Log.i(TAG, "ai_translate_inserted chars=" + text.length() + " format=html");
            toastTodo("已插入文档");
            if (translateDialog != null) {
                translateDialog.dismiss();
            }
        });
        pendingTranslateResult = null;
    }

    private void copyTranslateResult() {
        if (pendingTranslateResult == null || pendingTranslateResult.isEmpty()) {
            toastTodo("暂无译文可复制");
            return;
        }
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("translate", pendingTranslateResult));
            toastTodo("已复制到剪贴板");
            Log.i(TAG, "ai_translate_copied chars=" + pendingTranslateResult.length());
        }
    }

    // ==================== 扩写/缩写/润色/翻译弹窗相关方法结束 ====================

    // ==================== 文案生成相关方法结束 ====================

    // ==================== 生成大纲相关方法结束 ====================

    private void runAiOperation(String mode) {
        String selection = aiOpPendingSelection;
        if (selection == null || selection.trim().isEmpty()) {
            return;
        }
        // continue_write 走弹窗式续写（浮层初始化成功时 divert；否则回退到下方 operate-mode 自动粘贴）
        if (AiChatCoordinator.MODE_CONTINUE.equals(mode) && continueDialogPanel != null) {
            openContinueWriteDialog(selection);
            return;
        }
        // 扩写/缩写/润色/重写/翻译 走弹窗流程
        if (AiChatCoordinator.MODE_EXPAND.equals(mode)
                || AiChatCoordinator.MODE_CONDENSE.equals(mode)
                || AiChatCoordinator.MODE_POLISH.equals(mode)
                || AiChatCoordinator.MODE_REWRITE.equals(mode)) {
            showTextOperateDialog(mode, selection);
            return;
        }
        if (AiChatCoordinator.MODE_TRANSLATE.equals(mode)) {
            showTranslateDialog(selection);
            return;
        }
        try {
            JSONObject context = new JSONObject();
            context.put("prompt", "");
            context.put("question", "");
            context.put("source", "android-operation-sheet");
            context.put("selection", selection);

            JSONObject request = new JSONObject();
            String requestId = "op-" + UUID.randomUUID().toString();
            request.put("requestId", requestId);
            request.put("taskType", mode);
            request.put("selection", selection);
            request.put("context", context);
            request.put("modelMode", "base");
            request.put("history", new JSONArray());

            aiActiveRequestId = requestId;
            aiStreamingRequestId = requestId;
            aiRequestModeById.put(requestId, mode);
            aiTextByRequestId.put(requestId, new StringBuilder());
            if (aiOutputText != null) {
                aiOutputText.setText("");
            }

            Log.i(TAG, "ai_operation_request requestId=" + requestId
                    + " mode=" + mode
                    + " selectionChars=" + selection.length());

            startAiRequestSession(request, -1);
        } catch (JSONException e) {
            dispatchAiError("", "invalid_payload", "Failed to build ai operation request");
            Log.e(TAG, "Failed to build ai operation request", e);
            cleanupOperationSheet();
        }
    }

    private void cleanupOperationSheet() {
        runOnUiThread(() -> {
            if (aiOperationSheet != null && aiOperationSheet.isShowing()) {
                aiOperationSheet.dismiss();
            }
        });
    }

    private void cancelAiOperation() {
        if (!aiActiveRequestId.isEmpty()) {
            cancelAiRequest(aiActiveRequestId);
            aiRequestModeById.remove(aiActiveRequestId);
        }
        cleanupOperationSheet();
        runOnUiThread(() -> toastTodo("AI 操作已取消"));
    }

    private void getSelectedTextFromJs(final Consumer<String> callback) {
        if (mWebView == null) {
            callback.accept("");
            return;
        }
        injectAiBridgeIfNeeded();

        // Primary: JNI direct call to LOK getTextSelection — synchronous, no clipboard, no polling
        new Thread(() -> {
            String text = getTextSelection("text/plain;charset=utf-8");
            if (text != null && !text.trim().isEmpty()) {
                Log.i(TAG, "ai_op_selection_native_lok chars=" + text.length());
                runOnUiThread(() -> callback.accept(text));
                return;
            }
            // Fallback: JS bridge (preview mode _selectedTextContent / edit mode _selectionPlainTextContent)
            // ⚠️ WebView.evaluateJavascript 必须在主线程调用，否则 checkThread() 抛 RuntimeException，
            // 致子线程未捕获异常 → 进程崩溃（"跳回主页"闪退）。这里切回主线程执行。
            runOnUiThread(() -> {
                if (mWebView == null) {
                    callback.accept("");
                    return;
                }
                mWebView.evaluateJavascript(
                    "window.__coolAiBridge?window.__coolAiBridge.getSelectedText():''",
                    value -> {
                        String jsText = "";
                        if (value != null && !"null".equals(value)) {
                            try {
                                jsText = new JSONObject("{\"v\":" + value + "}").optString("v", "");
                            } catch (JSONException e) {
                                jsText = "";
                            }
                        }
                        if (!jsText.isEmpty()) {
                            Log.i(TAG, "ai_op_selection_js_bridge chars=" + jsText.length());
                        }
                        callback.accept(jsText);
                    });
            });
        }, "cool-ai-op-selection").start();
    }

    private void onAiOperationDone(String requestId, String fullText) {
        Log.i(TAG, "ai_operation_done requestId=" + requestId + " textChars=" + fullText.length());
        cleanupOperationSheet();
        if (fullText == null || fullText.trim().isEmpty()) {
            runOnUiThread(() -> toastTodo("AI 未返回有效内容"));
            return;
        }
        final byte[] bytes = fullText.getBytes(StandardCharsets.UTF_8);
        runOnUiThread(() -> {
            paste("text/plain;charset=utf-8", bytes);
            toastTodo("AI 操作完成");
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
                ? "请先生成文档大纲（章节标题），再基于大纲输出完整正文，风格专业、结构清晰。"
                : pendingAutoOpenAiPrompt;
        Log.i(TAG, "ai_auto_generate_start promptChars=" + prompt.length()
                + " promptPreview=" + (prompt.length() > 80 ? prompt.substring(0, 80) + "..." : prompt));
        showNativeAiPanel();
        startNativeAiRequest("", prompt, true, "chat", false);
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
            Log.d(TAG, "ai_delta_null_filtered requestId=" + requestId + " stage=" + stage);
            return "";
        }
        String text = raw instanceof String ? (String) raw : String.valueOf(raw);
        if (text == null) {
            Log.d(TAG, "ai_delta_null_filtered requestId=" + requestId + " stage=" + stage);
            return "";
        }
        String trimmed = text.trim();
        if ("null".equalsIgnoreCase(trimmed)) {
            Log.d(TAG, "ai_delta_null_filtered requestId=" + requestId + " stage=" + stage);
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
                    if (requestId.equals(outlineActiveRequestId)) {
                        scrollOutlineResultToBottom();
                    }
                    if (requestId.equals(articleActiveRequestId)) {
                        scrollArticleResultToBottom();
                    }
                    if (requestId.equals(textOperateActiveRequestId)) {
                        scrollTextOperateResultToBottom();
                    }
                    if (requestId.equals(translateActiveRequestId)) {
                        scrollTranslateResultToBottom();
                    }
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
                    if (requestId.equals(outlineActiveRequestId)) {
                        scrollOutlineResultToBottom();
                    }
                    if (requestId.equals(articleActiveRequestId)) {
                        scrollArticleResultToBottom();
                    }
                    if (requestId.equals(textOperateActiveRequestId)) {
                        scrollTextOperateResultToBottom();
                    }
                    if (requestId.equals(translateActiveRequestId)) {
                        scrollTranslateResultToBottom();
                    }
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

            // Progress bar: visible only while generating
            if (aiProgressBar != null) {
                aiProgressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            }
            if (aiProgressLabel != null) {
                aiProgressLabel.setText(AI_STATE_LOADING.equals(state) ? "正在连接..." : "AI 正在生成...");
            }

            // Human-friendly status text
            String friendlyText;
            if (AI_STATE_LOADING.equals(state)) {
                friendlyText = "正在连接...";
            } else if (AI_STATE_STREAMING.equals(state)) {
                friendlyText = "AI 正在生成...";
            } else if (AI_STATE_READY.equals(state)) {
                friendlyText = "生成完成";
            } else if (AI_STATE_ERROR.equals(state)) {
                friendlyText = "生成失败";
            } else if (AI_STATE_UNCONFIGURED.equals(state)) {
                friendlyText = "请先配置 AI 模型";
            } else if (AI_STATE_CANCELLED.equals(state)) {
                friendlyText = "已取消";
            } else {
                friendlyText = message == null || message.isEmpty() ? state : message;
            }
            aiStatusText.setText(friendlyText);
            if (AI_STATE_ERROR.equals(state) || AI_STATE_UNCONFIGURED.equals(state)) {
                aiStatusText.setTextColor(Color.parseColor("#B3261E"));
            } else if (AI_STATE_STREAMING.equals(state) || AI_STATE_LOADING.equals(state)) {
                aiStatusText.setTextColor(Color.parseColor("#0B57D0"));
            } else {
                aiStatusText.setTextColor(Color.parseColor("#2E7D32"));
            }
        });
    }

    private void injectDocumentStateBridgeIfNeeded() {
        if (documentStateBridgeInjected || mWebView == null) {
            return;
        }
        documentStateBridgeInjected = true;

        final String script = "(function(){try{"
                + "if(window.__androidDocStateBridge){return 'exists';}"
                + "window.__androidDocStateBridge=true;"
                + "function hook(){try{"
                + "if(!(window.app&&app.map&&typeof app.map.on==='function')){setTimeout(hook,250);return;}"
                + "app.map.on('postMessage',function(e){try{"
                + "if(e&&e.msgId==='Doc_ModifiedStatus'){var modified=!!(e.args&&e.args.Modified);"
                + "window.postMobileMessage('DOC_MODIFIED_STATUS '+(modified?'true':'false'));}"
                + "}catch(ignore){}});"
                + "}catch(e){setTimeout(hook,250);}}"
                + "hook();"
                + "return 'installed';"
                + "}catch(e){if(window.console&&console.warn){console.warn('android_doc_state_bridge_failed',e);}return 'err';}})();";
        runOnUiThread(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script,
                        value -> Log.d(TAG, "doc_state_bridge result=" + value));
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
                "},getSelectedText:function(){" +
                "try{" +
                "var docLayer=window.app&&window.app.map&&window.app.map._docLayer;" +
                "if(docLayer&&typeof docLayer._selectedTextContent==='string'&&docLayer._selectedTextContent)return docLayer._selectedTextContent;" +
                "var clip=window.app&&window.app.map&&window.app.map._clip;" +
                "if(clip&&typeof clip._selectionPlainTextContent==='string'&&clip._selectionPlainTextContent)return clip._selectionPlainTextContent;" +
                // trigger gettextselection in edit mode where clipboardApiAvailable suppresses it
                "if(clip&&!clip._selectionPlainTextContent&&window.app&&window.app.socket){" +
                "try{window.app.socket.sendMessage('gettextselection mimetype=text/plain;charset=utf-8');}catch(e2){}}" +
                "var sel=window.getSelection();" +
                "if(sel&&sel.toString&&sel.toString().trim())return sel.toString().trim();" +
                "return '';" +
                "}catch(e){return '';}" +
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

    public native String getTextSelection(String mimeType);

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
