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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Spinner;
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
import java.io.ByteArrayInputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import androidx.core.content.FileProvider;

import org.libreoffice.androidlib.template.PptxTemplateFiller;
import org.libreoffice.androidlib.template.TemplateIndex;
import org.libreoffice.androidlib.typeset.DocxImageInserter;
import org.libreoffice.androidlib.typeset.DocxTemplateFiller;
import org.libreoffice.androidlib.typeset.TemplateSectionMap;
import org.libreoffice.androidlib.typeset.TypesetImageEntry;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
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
import org.libreoffice.androidlib.ai.CondFormatApplier;
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
    public static final String EXTRA_AUTO_USER_DESCRIPTION = "org.libreoffice.androidlib.extra.AUTO_USER_DESCRIPTION";
    public static final String EXTRA_AUTO_IS_CALC_NEW_TABLE = "org.libreoffice.androidlib.extra.AUTO_IS_CALC_NEW_TABLE";
    public static final String EXTRA_START_IN_EDIT_MODE = "org.libreoffice.androidlib.extra.START_IN_EDIT_MODE";
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
    private File mOriginalTypesetDocx = null;  // 原始 docx 备份（LOKit 转换后 mTempFile 不再是 docx 格式，排版用此文件提取图片）
    private List<String> pendingTypesetParagraphs = null;  // 源文档逐段原文（含 [图N]）
    private List<List<String>> pendingParaImageMarkers = null; // 每段对应的图片标记名列表

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
    /** onNewIntent 换文档期间忽略 JS 侧 BYE，避免 finishWithProgress 闪回首页开屏。 */
    private volatile boolean documentSwitchInProgress = false;
    /** onDestroy 开始后拒绝 WebView / native 再回调 Java。 */
    private volatile boolean documentBridgeEnabled = true;

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
    private NativeJSDialogController nativeJSDialogController;
    private boolean mIsEditModeActive = false;
    private static final long MOBILE_PREVIEW_ACK_TIMEOUT_MS = 2200L;
    private static final long SELECTION_SYNC_THROTTLE_MS = 450L;
    private static final long MOBILE_WIZARD_COMMAND_BLOCK_MS = 4000L;
    private static final long PREVIEW_SELECTION_TILE_RECOVER_THROTTLE_MS = 1200L;
    private long lastPreviewSelectionTileRecoverAt = 0L;
    /** True while waiting for JS to finish mobile preview (readonly UI) after native toolbar switch. */
    private boolean awaitingPreviewModeJsAck = false;
    private int mobilePreviewSwitchAttempt = 0;
    /** Timestamp of the last manual switch to preview mode.
        EDITMODE on messages arriving within STALE_EDITMODE_GUARD_MS are ignored,
        preventing stale state from reconnect or delayed JS permission sync. */
    private long lastPreviewModeSwitchMs = 0L;
    private static final long STALE_EDITMODE_GUARD_MS = 3000L;
    /** Set when user explicitly taps Edit; bypasses stale EDITMODE-on guard once. */
    private boolean manualEditModeSwitchPending = false;
    /** Brief window to establish IME; sustained allow is driven by keyboard visibility. */
    private static final long IME_ALLOW_RESET_MS = 400L;
    private static final long IME_CLEAR_DEFER_MS = 200L;
    private Runnable imeAllowResetRunnable = null;
    private Runnable imeClearDeferredRunnable = null;
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
    private boolean typesetInProgress = false;  // 排版进行中，抑制选区弹窗
    private BottomSheetDialog typesetSelectSheet;
    private BottomSheetDialog typesetPreviewSheet;
    private String pendingTypesetType;  // "paper" | "gov" | "contract" | "general"
    private String pendingTypesetHtml;  // AI 返回的排版结果（旧 HTML 路径，fallback）
    // AI排版 V2 — docx 模板填充
    private View typesetPreviewOverlay;
    private View typesetPreviewCard;
    private WebView typesetPreviewWebView;
    private File pendingTypesetDocx;    // 填充后的 docx 临时文件
    private Map<String, String> pendingTypesetSections;  // AI 返回的 sections
    private Map<String, TypesetImageEntry> pendingTypesetImages;  // 源文档图片（排版后插入）
    private boolean pendingTypesetParagraphMode = false;  // true=段落分类模式（LLM 只做分类不改写原文）
    // 生成大纲相关
    private AlertDialog outlineDialog;
    private View outlineDialogRoot;
    private TextView outlineTypeLabel;
    private EditText outlineDescEdit;
    private TextView outlineResultText;
    private View outlineDescCard;
    private View outlineResultCard;
    private NestedScrollView outlineResultScroll;
    private View outlineTypeCard;
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
    private View articleResultCard;
    private NestedScrollView articleResultScroll;
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
    private TextView aiImageRatioLabel;
    private View aiImageRatioCard;
    private int aiImageSelectedRatioIndex = 0;
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
    // Calc AI公式生成浮层（弹窗式公式生成：输入态/生成中态/完成态，复用 aiStreamingViewByRequestId 流式接入）
    private boolean mIsCalcDocument = false;
    private boolean mIsImpressDocument = false;
    private View calcFormulaOverlay;
    private View calcFormulaPanel;
    private EditText calcFormulaInput;
    private View calcFormulaInputGroup;
    private TextView calcFormulaGenerateBtn;
    private View calcFormulaContentScroll;
    private TextView calcFormulaUserInputDisplay;
    private TextView calcFormulaContentText;
    private View calcFormulaCopyBar;
    private View calcFormulaStopBtn;
    private View calcFormulaCompletedGroup;
    private View calcFormulaRegenBtn;
    private View calcFormulaInsertBtn;
    private TextView calcFormulaCellHint;
    private String calcFormulaCellAddress = "";
    private String calcFormulaActiveRequestId = "";
    private String calcFormulaResultText = "";
    private final java.util.Set<String> calcFormulaRequestIds =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    // Calc AI条件格式浮层（三态：输入态/生成中态/完成态）
    private View condFormatOverlay;
    private View condFormatPanel;
    private EditText condFormatInput;
    private View condFormatInputGroup;
    private TextView condFormatGenerateBtn;
    private View condFormatContentScroll;
    private TextView condFormatUserInputDisplay;
    private TextView condFormatContentText;
    private View condFormatCopyBar;
    private View condFormatStopBtn;
    private View condFormatCompletedGroup;
    private View condFormatRegenBtn;
    private View condFormatApplyBtn;
    private TextView condFormatRangeHint;
    private String condFormatCellRange = "";
    private String condFormatActiveRequestId = "";
    private String condFormatResultText = "";
    private CondFormatApplier.CondFormatPlan condFormatPlan = null;
    private volatile boolean condFormatApplying = false;
    private CondFormatApplier.ApplyResultCallback condFormatApplyCallback = null;
    private final Runnable condFormatApplyTimeoutRunnable = () -> {
        CondFormatApplier.ApplyResultCallback cb = condFormatApplyCallback;
        if (cb == null) return;
        condFormatApplyCallback = null;
        condFormatApplying = false;
        Log.w(TAG, "cond_format_apply_timeout");
        runOnUiThread(() -> cb.onResult(false));
    };
    private final java.util.Set<String> condFormatRequestIds =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    // Calc AI数据处理浮层（四态：输入态/生成中态/完成态/执行中态）
    private View dpOverlay;
    private View dpPanel;
    private EditText dpInput;
    private View dpInputGroup;
    private TextView dpGenerateBtn;
    private View dpContentScroll;
    private TextView dpUserInputDisplay;
    private TextView dpContentText;
    private View dpStopBtn;
    private TextView dpGeneratingStatus;
    private View dpCompletedGroup;
    private TextView dpTitle;
    private View dpRegenerateBtn;
    private View dpExecuteBtn;
    private TextView dpExecutingStatus;
    private TextView dpRangeHint;
    private String dpCellRange = "";
    private String dpActiveRequestId = "";
    private String dpResultText = "";
    private final java.util.Set<String> dpRequestIds =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private org.json.JSONArray dpPendingOperations = null;
    private boolean dpIsAnalysisMode = false;

    // Calc AI图表生成弹窗（四态：输入态/生成中态/完成态/执行中态）
    private View chartOverlay;
    private View chartPanel;
    private EditText chartInput;
    private TextView chartGenerateBtn;
    private TextView chartLoadingStatus;
    private TextView chartResultText;
    private View chartCompletedGroup;
    private TextView chartRegenerateBtn;
    private TextView chartInsertBtn;
    private TextView chartExecutingStatus;
    private String chartActiveRequestId = "";
    private String chartResultJson = "";
    private String chartSelectedRange = "";
    // Calc 通用：在 AI 功能面板弹出时缓存选区范围（避免 dismiss 后读取为空）
    private String calcSelectedRange = "";
    private static final int CHART_STATE_INPUT = 0;
    private static final int CHART_STATE_GENERATING = 1;
    private static final int CHART_STATE_COMPLETED = 2;
    private static final int CHART_STATE_EXECUTING = 3;

    // ========== Impress PPT 大纲生成弹窗 ==========
    private View impressOutlineOverlay;
    private View impressOutlinePanel;
    private LinearLayout impressOutlineInputGroup;
    private EditText impressOutlineQuickInput;
    private LinearLayout impressOutlineDocGroup;
    private Button impressOutlineDocSelectBtn;
    private TextView impressOutlineDocFileName;
    private EditText impressOutlinePasteInput;
    private TextView impressOutlineTabQuick, impressOutlineTabDoc, impressOutlineTabPaste;
    private Spinner impressOutlinePageSpinner, impressOutlineAudienceSpinner, impressOutlineStyleSpinner;
    private TextView impressOutlineGenerateBtn;
    private TextView impressOutlineLoadingText;
    private LinearLayout impressOutlineCompletedGroup;
    private LinearLayout impressOutlineCardContainer;
    private TextView impressOutlineRegenerateBtn;
    private TextView impressOutlineTemplateBtn;
    private LinearLayout impressOutlineErrorGroup;
    private TextView impressOutlineErrorText;
    private TextView impressOutlineErrorRetryBtn;
    private LinearLayout impressOutlineGeneratingPptGroup;
    private TextView impressOutlineGeneratingPptText;
    private TextView impressOutlineGeneratingPptDetail;
    private ProgressBar impressOutlineGeneratingPptProgress;
    private TextView impressOutlineTitle;
    private LinearLayout impressOutlineTemplateGroup;
    private TextView impressOutlineTemplateBackBtn;

    private String impressOutlineActiveRequestId = "";
    private String impressOutlineInputType = "quick";
    private String impressOutlineDocFileContent = "";

    private static final int IMPRESS_OUTLINE_STATE_INPUT = 0;
    private static final int IMPRESS_OUTLINE_STATE_GENERATING = 1;
    private static final int IMPRESS_OUTLINE_STATE_COMPLETED = 2;
    private static final int IMPRESS_OUTLINE_STATE_ERROR = 3;
    private static final int REQUEST_CODE_IMPRESS_PICK_DOC = 9002;

    // ========== Impress PPT 模板选择 + 生成 ==========
    private LinearLayout impressTemplateGridContainer;
    private String selectedTemplateId = "";
    private org.libreoffice.androidlib.template.TemplateIndex templateIndex;

    // PPT generation state
    private static final int IMPRESS_OUTLINE_STATE_TEMPLATE_SELECT = 5;
    private static final int IMPRESS_OUTLINE_STATE_GENERATING_PPT = 4;
    /** 单批失败后额外重试次数（首次 + 2 次重试 = 共 3 次尝试） */
    private static final int PPT_GENERATE_BATCH_MAX_RETRIES = 2;
    private int generateTotalBatches;
    private int generateCurrentBatch;
    private int generateBatchAttempt;
    private int generateFailedBatchCount;
    private String generateActiveRequestId = "";
    private org.json.JSONArray impressOutlineSlidesJson; // saved from outline result
    private final Map<Integer, JSONObject> generateAccumulatedByOutlineIndex = new HashMap<>();
    private File pendingGeneratedPptxFile;
    private android.app.AlertDialog impressGenerationSuccessDialog;
    private android.app.AlertDialog impressGenerationErrorDialog;

    // Calc 新建表格 AI 生成
    private View calcNewTableOverlay;
    private View calcNewTablePanel;
    private ProgressBar calcNewTableSpinner;
    private TextView calcNewTableStatus;
    private TextView calcNewTableDetail;
    private String calcNewTableRequestId = "";
    private final StringBuilder calcNewTableStreamBuffer = new StringBuilder();
    private boolean calcNewTableActive = false;

    private float aiFabDragOffsetY = 0f;
    private boolean pendingAutoOpenAiPanel = false;
    private boolean startInEditMode = false;
    private boolean pendingAutoGenerateAiContent = false;
    private String pendingAutoOpenAiPrompt = "";
    private String pendingAutoUserDescription = "";
    private boolean pendingAutoIsCalcNewTable = false;
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
    private CalcFunctionPanelController calcFunctionPanelController;
    private ImpressFunctionPanelController impressFunctionPanelController;
    private TopToolbarController topToolbarController;
    private FindReplaceSheetController findReplaceSheetController;
    private DocumentTabsSheetController documentTabsSheetController;
    private SelectionMenuController selectionMenuController;
    private CalcHyperlinkCellPopupController calcHyperlinkCellPopupController;
    private android.app.AlertDialog externalLinkConfirmDialog;
    private CalcObjectBarController calcObjectBarController;
    private Runnable pendingAfterEditMode;
    private boolean documentModified = false;
    private boolean closeAfterSaveRequested = false;
    private boolean documentStateBridgeInjected = false;

    private static final int MODEL_TYPE_BASE = 0;
    private static final int MODEL_TYPE_THINK = 1;
    private static final int MODEL_TYPE_IMAGE = 2;
    private static final int MODEL_TYPE_VISION = 3;
    private static final String MODEL_CONFIG_EXTRA_KEY = "extra_model_type";
    private static final String MODEL_CONFIG_FROM_DRAWER_KEY = "extra_from_drawer";
    private static final int RESULT_BACK_TO_DRAWER = 100;
    private static final int REQUEST_AI_MODEL_CONFIG = 750;
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
                DocumentTabsSheetController tabsController = documentTabsSheetController;
                if (tabsController != null && tabsController.isVisible()) {
                    tabsController.dismiss();
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
        documentBridgeEnabled = true;
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

        // Save original file backup for typeset image extraction.
        // LOKit converts the file to ODF format internally — unconditionally backup
        // so extractFullTextWithImagesNative can read the original format.
        if (mTempFile != null && mTempFile.exists()) {
            try {
                mOriginalTypesetDocx = File.createTempFile("LO_orig_docx_", ".docx", getCacheDir());
                copyFileStream(mTempFile, mOriginalTypesetDocx);
                Log.i(TAG, "original_docx_saved path=" + mOriginalTypesetDocx + " size=" + mTempFile.length());
            } catch (Exception e) {
                Log.w(TAG, "original_docx_save_failed", e);
                mOriginalTypesetDocx = null;
            }
        }
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
            ensureDocumentTabsSheetController().bindOverlayViews();
            setupSelectionMenu();
            setupContinueWriteDialog();
            setupCalcFormulaDialog();
            setupCondFormatDialog();
            setupDataProcessDialog();
            setupChartDialog();
            setupImpressOutlineDialog();
            setupCalcObjectBar();
            setupTypesetPreviewOverlay();
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
            mWebView.setOnDocumentTapListener((viewX, viewY) -> LOActivity.this.onDocumentTap(viewX, viewY));
            mWebView.setConsumeWebViewLongClick(true);
            mWebView.setTouchEndImeRestoreCallback(() -> {
                if (mWebView == null) {
                    return;
                }
                if (isImeVisibleForToolbar || (mIsEditModeActive && mIsCalcDocument)) {
                    setImeAllowedByUserSustained(true);
                }
            });

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

        // Capture original doc references BEFORE setIntent/init overwrite them,
        // so we can save the current document state back to its source URI.
        final Uri originalDataUri = getIntent().getData();
        final File originalTempFile = mTempFile;
        final boolean wasEditable = isDocEditable;

        if (documentLoaded) {
            postMobileMessageNative("save dontTerminateEdit=1 dontSaveIfUnmodified=1");
        }

        final Intent finalIntent = intent;
        documentSwitchInProgress = true;
        mProgressDialog.indeterminate(R.string.exiting);
        getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                cancelAllAiRequests();

                // Save original doc back to its content:// URI BEFORE BYE and
                // setIntent — after those, mTempFile and getIntent().getData()
                // point to the new document and the original is unreachable.
                saveOriginalDocBeforeSwitch(originalTempFile, originalDataUri, wasEditable);

                final Runnable afterBye = new Runnable() {
                    @Override
                    public void run() {
                        mProgressDialog.dismiss();
                        setIntent(finalIntent);
                        documentLoaded = false;
                        init();
                        documentSwitchInProgress = false;
                        Log.i(TAG, "doc_switch_done pid=" + android.os.Process.myPid());
                    }
                };

                Log.i(TAG, "doc_switch_bye_start pid=" + android.os.Process.myPid());
                final Handler handler = nativeHandler;
                if (handler != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            final long byeStart = System.currentTimeMillis();
                            postMobileMessageNative("BYE");
                            Log.i(TAG, "doc_switch_bye_done ms=" + (System.currentTimeMillis() - byeStart)
                                    + " pid=" + android.os.Process.myPid());
                            runOnUiThread(afterBye);
                        }
                    });
                } else {
                    postMobileMessageNative("BYE");
                    afterBye.run();
                }
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
            pendingAutoUserDescription = "";
            startInEditMode = false;
            return;
        }
        pendingAutoGenerateAiContent = intent.getBooleanExtra(EXTRA_AUTO_GENERATE_AI_CONTENT, false);
        pendingAutoOpenAiPanel = intent.getBooleanExtra(EXTRA_AUTO_OPEN_AI_PANEL, false);
        startInEditMode = intent.getBooleanExtra(EXTRA_START_IN_EDIT_MODE, false);
        if (pendingAutoGenerateAiContent) {
            pendingAutoOpenAiPanel = false;
        }
        pendingAutoOpenAiPrompt = intent.getStringExtra(EXTRA_AUTO_OPEN_AI_PROMPT);
        if (pendingAutoOpenAiPrompt == null) {
            pendingAutoOpenAiPrompt = "";
        }
        pendingAutoUserDescription = intent.getStringExtra(EXTRA_AUTO_USER_DESCRIPTION);
        if (pendingAutoUserDescription == null) {
            pendingAutoUserDescription = "";
        }
        pendingAutoIsCalcNewTable = intent.getBooleanExtra(EXTRA_AUTO_IS_CALC_NEW_TABLE, false);
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
        intent.putExtra(MODEL_CONFIG_FROM_DRAWER_KEY, true);
        try {
            startActivityForResult(intent, REQUEST_AI_MODEL_CONFIG);
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

    /**
     * Save the current document state back to its content:// source URI
     * before switching to a new document. Uses pre-captured references so
     * the write is correct even after mTempFile and getIntent().getData()
     * are overwritten by the new document's initUI().
     */
    private void saveOriginalDocBeforeSwitch(File tempFile, Uri originalUri, boolean editable) {
        if (!editable || tempFile == null || !tempFile.exists()
                || originalUri == null
                || !ContentResolver.SCHEME_CONTENT.equals(originalUri.getScheme()))
            return;

        try {
            ContentResolver cr = getContentResolver();
            try (InputStream in = new FileInputStream(tempFile);
                 OutputStream out = cr.openOutputStream(originalUri, "wt")) {
                if (in.available() <= 0) return;
                byte[] buf = new byte[1024];
                int len;
                long total = 0;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                    total += len;
                }
                Log.i(TAG, "save_original_before_switch bytes=" + total + " uri=" + originalUri);
            }
        } catch (Exception e) {
            Log.w(TAG, "save_original_before_switch_failed", e);
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

    private void destroyWebViewNow() {
        if (mWebView == null) {
            return;
        }
        final ViewGroup viewGroup = (ViewGroup) mWebView.getParent();
        if (viewGroup != null) {
            viewGroup.removeView(mWebView);
        }
        mWebView.destroy();
        mWebView = null;
        mMobileSocket = null;
        mobileSocketDrainScheduled.set(false);
    }

    @Override
    protected void onDestroy() {
        documentBridgeEnabled = false;
        if (mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
        }
        Log.i(TAG, "onDestroy.. documentLoaded=" + documentLoaded + " isFinishing=" + isFinishing()
                + " aiSheetShowing=" + (aiOperationSheet != null && aiOperationSheet.isShowing())
                + " pid=" + android.os.Process.myPid()
                + " callingActivity=" + (getCallingActivity() != null ? getCallingActivity().getClassName() : "null"));
        resetAiSessionState(true);

        final boolean needsBye = documentLoaded || isFinishing();
        documentLoaded = false;
        mProgressDialog.dismiss();

        if (needsBye) {
            // BYE must finish while WebView is still alive; await on nativeHandler, then tear down.
            runNativeByeBlocking("home_exit");
        }
        // Stop native→Java first; then tear down WebView (Chromium may still callback during destroy).
        clearNativeActivityCallbacks();
        destroyWebViewNow();
        quitNativeMsgLooperSafely();

        super.onDestroy();
        Log.i(TAG, "onDestroy done pid=" + android.os.Process.myPid());
    }

    private void quitNativeMsgLooperSafely() {
        final Handler handler = nativeHandler;
        final Looper looper = nativeLooper;
        if (handler != null && looper != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    looper.quitSafely();
                }
            });
        }
    }

    /**
     * Run BYE on nativeHandler while WebView remains alive. Blocks the calling thread until BYE
     * completes (typically ~2–3s). Safe to call from main thread during onDestroy after finish().
     */
    private void runNativeByeBlocking(String logPrefix) {
        setExpectCoolwsdRun(false);
        if ("home_exit".equals(logPrefix)) {
            Log.i(TAG, "home_exit_expect_coolwsd_idle pid=" + android.os.Process.myPid());
        }
        final Handler handler = nativeHandler;
        if (handler != null) {
            final CountDownLatch latch = new CountDownLatch(1);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final long byeStart = System.currentTimeMillis();
                    Log.i(TAG, logPrefix + "_bye_start pid=" + android.os.Process.myPid());
                    try {
                        postMobileMessageNative("BYE");
                    } finally {
                        Log.i(TAG, logPrefix + "_bye_done ms=" + (System.currentTimeMillis() - byeStart)
                                + " pid=" + android.os.Process.myPid());
                        latch.countDown();
                    }
                }
            });
            try {
                if (!latch.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    Log.w(TAG, logPrefix + "_bye_timeout pid=" + android.os.Process.myPid());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, logPrefix + "_bye_interrupted pid=" + android.os.Process.myPid());
            }
        } else {
            final long byeStart = System.currentTimeMillis();
            Log.i(TAG, logPrefix + "_bye_start no_native_handler pid=" + android.os.Process.myPid());
            postMobileMessageNative("BYE");
            Log.i(TAG, logPrefix + "_bye_done ms=" + (System.currentTimeMillis() - byeStart)
                    + " pid=" + android.os.Process.myPid());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.i(TAG, "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode
                + " aiSheetShowing=" + (aiOperationSheet != null && aiOperationSheet.isShowing()));
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQUEST_AI_MODEL_CONFIG) {
            refreshDocumentSettingsDrawer();
            if (resultCode == RESULT_BACK_TO_DRAWER && docDrawerLayout != null) {
                docDrawerLayout.post(() -> docDrawerLayout.openDrawer(GravityCompat.START));
            }
            return;
        }
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
            case REQUEST_CODE_IMPRESS_PICK_DOC:
                if (intent != null && intent.getData() != null) {
                    handleImpressOutlineDocPicked(intent.getData());
                }
                return;
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

    /** Copy a file using stream I/O (safe for all API levels). */
    private static void copyFileStream(File src, File dst) throws java.io.IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(src);
             java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    /** Show the Saving progress and finish the app. */
    private void finishWithProgress() {
        if (!documentLoaded) {
            finishAndRemoveTask();
            return;
        }
        mProgressDialog.indeterminate(R.string.exiting);

        // Queue BYE on nativeHandler while WebView is still alive, then finish() immediately
        // so home resumes without blocking main thread on BYE (~2–3s).
        getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "home_exit_start callingActivity="
                        + (getCallingActivity() != null ? getCallingActivity().getClassName() : "null")
                        + " isTaskRoot=" + isTaskRoot()
                        + " pid=" + android.os.Process.myPid());
                cancelAllAiRequests();
                if (getCallingActivity() != null) {
                    setResult(RESULT_OK);
                    finish();
                    Log.i(TAG, "home_exit_finish_done pid=" + android.os.Process.myPid());
                } else {
                    finishAndRemoveTask();
                }
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
        setExpectCoolwsdRun(true);
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

        if (startInEditMode) {
            finalUrlToLoad += "&android_start_edit=1";
        }

        // load the page
        mWebView.loadUrl(finalUrlToLoad);

        documentLoaded = true;
        if (startInEditMode) {
            updateEditModeState(true, "intent_start_edit");
            manualEditModeSwitchPending = true;
        }
        ensureTopToolbarController().refreshDocumentTitle();
        ensureTopToolbarController().refreshOpenDocumentCount();
        ensureTopToolbarController().resetUndoRedoState("document_loaded");
        // 延迟检测文档类型（等 WebView 加载完成后 JS 才可用）
        getMainHandler().postDelayed(() -> detectDocumentType(), 3000L);
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

    /** false = 回首页时 COOLWSD 线程 idle；true = 即将打开/切换文档。 */
    public native void setExpectCoolwsdRun(boolean expect);

    /** WebView 销毁后切断 native→Java 回调，防止 BYE 收尾时 use-after-destroy。 */
    private native void clearNativeActivityCallbacks();

    /**
     * Passing messages from JS (instead of the websocket communication).
     */
    @JavascriptInterface
    public void postMobileMessage(String message) {
        if (!documentBridgeEnabled || isFinishing()) {
            return;
        }
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
        if (!documentBridgeEnabled || isFinishing()) {
            return;
        }
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
        } else if (messageStartsWith(message, "unocommandresult:")) {
            handleCondFormatUnoCommandResult(message);
        }
    }

    private void handleCondFormatUnoCommandResult(byte[] message) {
        if (condFormatApplyCallback == null) return;
        try {
            String text = new String(message, StandardCharsets.UTF_8);
            int brace = text.indexOf('{');
            if (brace < 0) return;
            JSONObject obj = new JSONObject(text.substring(brace));
            String commandName = obj.optString("commandName", "");
            // LOK 回传的 commandName 含 URL 查询串（如 ?FormatRule:short=2&...），不能用精确匹配
            if (commandName.isEmpty()
                    || (!commandName.startsWith(".uno:ApplyConditionalFormat")
                        && !commandName.startsWith(".uno:ClearConditionalFormat"))) {
                Log.d(TAG, "cond_format_uno_result_ignored commandName=" + commandName);
                return;
            }

            boolean success = obj.optBoolean("success", false);
            JSONObject result = obj.optJSONObject("result");
            String resultType = result != null ? result.optString("type", "") : "";
            if (result != null && "boolean".equals(resultType)) {
                success = result.optBoolean("value", success);
            }
            String payloadLog = text.length() > 500 ? text.substring(0, 500) + "..." : text;
            Log.i(TAG, "cond_format_uno_result success=" + success
                    + " resultType=" + resultType + " payload=" + payloadLog);
            if (!success) {
                Log.w(TAG, "cond_format_uno_result FAILED — 查找同秒内 cond_format_core_fail / cond_format_core_diag 日志定位 Core 原因");
            }

            CondFormatApplier.ApplyResultCallback cb = condFormatApplyCallback;
            condFormatApplyCallback = null;
            getMainHandler().removeCallbacks(condFormatApplyTimeoutRunnable);
            boolean finalSuccess = success;
            runOnUiThread(() -> {
                if (finalSuccess) {
                    forceVisibleTileRedrawFromAndroid("cond_format_apply");
                }
                cb.onResult(finalSuccess);
            });
        } catch (JSONException e) {
            Log.w(TAG, "cond_format_uno_result_parse_failed", e);
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
                if (documentSwitchInProgress) {
                    Log.i(TAG, "bye_ignored reason=document_switch_in_progress");
                    return false;
                }
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
            case "JSDIALOG": {
                if (messageAndParam.length > 1) {
                    getMainHandler().post(() -> ensureNativeJSDialogController()
                            .handlePayload(messageAndParam[1]));
                }
                return false;
            }
            case "MOBILEWIZARD": {
                switch (messageAndParam[1]) {
                    case "show":
                        if (ensureNativeJSDialogController().isActive()) {
                            closeMobileWizardFromAndroid("native_jsdialog_active");
                            Log.i(TAG, "mobile_wizard_suppressed_native_jsdialog");
                            return false;
                        }
                        mMobileWizardVisible = true;
                        if (mIsEditModeActive && !mIsCalcDocument) {
                            lastBlockedMobileWizardAt = android.os.SystemClock.uptimeMillis();
                            closeMobileWizardFromAndroid("edit_mode_web_long_press");
                            Log.w(TAG, "blocked_mobile_wizard_show_in_edit_mode");
                        } else if (mIsEditModeActive && mIsCalcDocument) {
                            Log.i(TAG, "mobile_wizard_allowed_in_calc_edit");
                        }
                        break;
                    case "hide":
                        mMobileWizardVisible = false;
                        break;
                }
                return false;
            }
            case "OPENLINK": {
                if (messageAndParam.length > 1) {
                    final String url = messageAndParam[1];
                    getMainHandler().post(() -> showExternalLinkConfirm(url));
                }
                return false;
            }
            case "HYPERLINK_POPUP": {
                if (messageAndParam.length > 1 && "hide".equals(messageAndParam[1])) {
                    getMainHandler().post(() -> ensureCalcHyperlinkCellPopupController().hide());
                    return false;
                }
                if (messageAndParam.length > 1 && messageAndParam[1].startsWith("show ")) {
                    final String json = messageAndParam[1].substring("show ".length());
                    getMainHandler().post(() -> handleHyperlinkCellPopupShow(json));
                    return false;
                }
                return false;
            }
            case "HYPERLINK": {
                if (messageAndParam.length > 1) {
                    final String url = messageAndParam[1];
                    getMainHandler().post(() -> openExternalUrl(url));
                }
                return false;
            }
            case "IMEALLOW": {
                if (mWebView == null) {
                    return false;
                }
                if (messageAndParam.length > 1 && "on".equalsIgnoreCase(messageAndParam[1])) {
                    if (isImeVisibleForToolbar) {
                        setImeAllowedByUserSustained(true);
                    } else {
                        setImeAllowedByUserTransient(true);
                    }
                } else if (!isImeVisibleForToolbar) {
                    clearImeAllowedByUserTransient("imeallow_off");
                }
                return false;
            }
            case "CALC_CELL_TAP": {
                Log.i(TAG, "calc_cell_tap " + (messageAndParam.length > 1 ? messageAndParam[1] : ""));
                return false;
            }
            case "EDITMODE": {
                switch (messageAndParam[1]) {
                    case "on":
                        if (awaitingPreviewModeJsAck) {
                            Log.w(TAG, "stale_editmode_on_ignored during_preview_switch_ack");
                            return false;
                        }
                        manualEditModeSwitchPending = false;
                        cancelPreviewModeSwitchAck("editmode_on");
                        updateEditModeState(true, "js_editmode_on");
                        Log.i(TAG, "editmode_on_accepted calc=" + mIsCalcDocument);
                        recoverVisibleTilesAfterEditMode("js_editmode_on");
                        // prompt for file conversion
                        requestForOdf();
                        // Calc edit mode: show keyboard once, but do not keep WebView in text-editor
                        // mode — that blocks single-tap cell selection on the canvas.
                        if (mIsCalcDocument && mWebView != null) {
                            getMainHandler().post(() -> showCalcEditModeKeyboardOnEntry());
                        }
                        runPendingAfterEditMode();
                        break;
                    case "off":
                        clearImeAllowedByUserTransient("editmode_off");
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
                // 排版进行中（SelectAll 触发了 textselection），抑制选区弹窗
                if (typesetInProgress) {
                    return false;
                }
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
                                    ensureSelectionMenuController().setGraphicMode(false);
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
                            ensureSelectionMenuController().setGraphicMode(false);
                            ensureSelectionMenuController().showAtWindow(0, 0);
                            getMainHandler().postDelayed(() ->
                                    recoverVisibleTilesAfterPreviewSelection("selection_menu_show"), 180);
                        });
                    }
                    return false;
                }
                return false;
            }
            case "GRAPHICSELECTION": {
                if (messageAndParam.length > 1 && "hide".equals(messageAndParam[1])) {
                    getMainHandler().post(() -> {
                        if (mIsCalcDocument) {
                            hideCalcObjectBar();
                        } else {
                            ensureSelectionMenuController().hide();
                        }
                    });
                    return false;
                }
                if (messageAndParam.length > 1 && messageAndParam[1] != null && messageAndParam[1].startsWith("show ")) {
                    try {
                        String[] parts = messageAndParam[1].split(" ");
                        if (parts.length >= 3) {
                            final float anchorX = Float.parseFloat(parts[1]);
                            final float anchorY = Float.parseFloat(parts[2]);
                            getMainHandler().post(() -> {
                                if (mIsCalcDocument) {
                                    showCalcObjectBar();
                                } else {
                                    SelectionMenuController smc = ensureSelectionMenuController();
                                    smc.setGraphicMode(true);
                                    smc.showAtWindow(anchorX, anchorY);
                                }
                            });
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "graphic_selection_bad_anchor", e);
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

    void closeMobileWizardFromNative(String reason) {
        closeMobileWizardFromAndroid(reason);
    }

    NativeJSDialogController ensureNativeJSDialogController() {
        if (nativeJSDialogController == null) {
            nativeJSDialogController = new NativeJSDialogController(this);
        }
        return nativeJSDialogController;
    }

    void evaluateJavascript(String script, android.webkit.ValueCallback<String> callback) {
        if (mWebView == null) {
            return;
        }
        getMainHandler().post(() -> {
            if (mWebView != null) {
                mWebView.evaluateJavascript(script, callback);
            }
        });
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
                // 段落分类模式：LLM 只做段落分类，不改写原文
                if (pendingTypesetParagraphMode
                        && pendingTypesetParagraphs != null && !pendingTypesetParagraphs.isEmpty()) {
                    messages = AiChatCoordinator.buildTypesetParagraphMessages(
                            typesetType, pendingTypesetParagraphs, pendingParaImageMarkers);
                    Log.i(TAG, "ai_typeset_paragraph_mode requestId=" + requestId
                            + " typesetType=" + typesetType
                            + " paragraphs=" + pendingTypesetParagraphs.size());
                } else {
                    String fullText = request.optString("selection", "");
                    messages = AiChatCoordinator.buildTypesetMessagesV2(typesetType, fullText);
                    Log.i(TAG, "ai_typeset_mode requestId=" + requestId + " typesetType=" + typesetType
                            + " docChars=" + fullText.length());
                }
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
            } else if (AiChatCoordinator.MODE_CALC_FORMULA.equals(taskType)) {
                String userInput = request.optString("selection", "");
                String cellAddress = request.optString("cellAddress", "");
                messages = AiChatCoordinator.buildCalcFormulaMessages(userInput, cellAddress);
                Log.i(TAG, "ai_calc_formula_mode requestId=" + requestId
                        + " input=" + userInput + " cell=" + cellAddress);
            } else if (AiChatCoordinator.MODE_CALC_COND_FORMAT.equals(taskType)) {
                String userInput = request.optString("selection", "");
                String cellRange = request.optString("cellRange", "");
                String cellData = request.optString("cellData", "");
                messages = AiChatCoordinator.buildCondFormatMessages(userInput, cellRange, cellData);
                Log.i(TAG, "ai_calc_cond_format_mode requestId=" + requestId
                        + " input=" + userInput + " range=" + cellRange
                        + " dataChars=" + cellData.length());
            } else if (AiChatCoordinator.MODE_CALC_NEW_TABLE.equals(taskType)) {
                String userInput = request.optString("selection", "");
                messages = AiChatCoordinator.buildNewCalcTableMessages(userInput);
                Log.i(TAG, "ai_calc_new_table_mode requestId=" + requestId
                        + " input=" + userInput);
            } else if (AiChatCoordinator.MODE_CALC_DATA_PROCESS.equals(taskType)) {
                String userInput = request.optString("selection", "");
                String cellRange = request.optString("cellRange", "");
                String cellData = request.optString("cellData", "");
                messages = AiChatCoordinator.buildDataProcessMessages(userInput, cellRange, cellData);
                Log.i(TAG, "ai_calc_data_process_mode requestId=" + requestId
                        + " input=" + userInput + " range=" + cellRange);
            } else if (AiChatCoordinator.MODE_CALC_DATA_ANALYSIS.equals(taskType)) {
                String userInput = request.optString("selection", "");
                String cellRange = request.optString("cellRange", "");
                String cellData = request.optString("cellData", "");
                messages = AiChatCoordinator.buildDataAnalysisMessages(userInput, cellRange, cellData);
                Log.i(TAG, "ai_calc_data_analysis_mode requestId=" + requestId
                        + " input=" + userInput + " range=" + cellRange);
            } else if (AiChatCoordinator.MODE_CALC_CHART.equals(taskType)) {
                String userInput = request.optString("selection", "");
                String cellRange = request.optString("cellRange", "");
                String cellData = request.optString("cellData", "");
                messages = AiChatCoordinator.buildChartMessages(userInput, cellRange, cellData);
                Log.i(TAG, "ai_calc_chart_mode requestId=" + requestId
                        + " input=" + userInput + " range=" + cellRange);
            } else if (AiChatCoordinator.MODE_IMPRESS_OUTLINE.equals(taskType)) {
                String inputType = request.optString("inputType", "quick");
                String userInput = request.optString("userInput", "");
                int pageRange = request.optInt("pageRange", 10);
                String audience = request.optString("audience", "大众");
                String style = request.optString("style", "通用");
                messages = AiChatCoordinator.buildImpressOutlineMessages(inputType, userInput, pageRange, audience, style);
                Log.i(TAG, "ai_impress_outline_start requestId=" + requestId
                        + " inputType=" + inputType + " pageRange=" + pageRange);
            } else if (AiChatCoordinator.MODE_IMPRESS_GENERATE.equals(taskType)) {
                String templateId = request.optString("templateId", "");
                int batchIndex = request.optInt("batchIndex", 0);
                int totalBatches = request.optInt("totalBatches", 1);
                JSONArray batchSlides = request.optJSONArray("batchSlides");
                JSONArray outlineSlides = request.optJSONArray("outlineSlides");
                if (batchSlides == null) batchSlides = new JSONArray();
                if (outlineSlides == null) outlineSlides = batchSlides;
                messages = AiChatCoordinator.buildImpressGenerateMessages(
                        batchSlides, outlineSlides, templateId, batchIndex, totalBatches);
                Log.i(TAG, "ai_impress_generate_start requestId=" + requestId
                        + " templateId=" + templateId
                        + " batch=" + (batchIndex + 1) + "/" + totalBatches
                        + " slides=" + batchSlides.length());
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
                            } else if (callbackRequestId.equals(calcFormulaActiveRequestId)) {
                                onCalcFormulaDone(callbackRequestId, fullText);
                            } else if (calcFormulaRequestIds.contains(callbackRequestId)) {
                                Log.i(TAG, "calc_formula_done_suppressed requestId=" + callbackRequestId);
                            } else if (callbackRequestId.equals(condFormatActiveRequestId)) {
                                onCondFormatDone(callbackRequestId, fullText);
                            } else if (condFormatRequestIds.contains(callbackRequestId)) {
                                Log.i(TAG, "cond_format_done_suppressed requestId=" + callbackRequestId);
                            } else if (callbackRequestId.equals(dpActiveRequestId)) {
                                onDataProcessDone(callbackRequestId, fullText);
                            } else if (dpRequestIds.contains(callbackRequestId)) {
                                Log.i(TAG, "calc_data_process_done_suppressed requestId=" + callbackRequestId);
                            } else if (callbackRequestId.equals(chartActiveRequestId)) {
                                onChartDone(callbackRequestId, fullText);
                            } else if (callbackRequestId.equals(impressOutlineActiveRequestId)) {
                                onImpressOutlineDone(callbackRequestId, fullText);
                            } else if (callbackRequestId.equals(generateActiveRequestId)) {
                                runOnUiThread(() -> onImpressGenerateDone(callbackRequestId, fullText));
                            } else if (AiChatCoordinator.isOperateMode(taskType)) {
                                onAiOperationDone(callbackRequestId, fullText);
                            } else if (AiChatCoordinator.MODE_TYPESET.equals(taskType)) {
                                String responsePrefix = fullText.length() > 200 ? fullText.substring(0, 200) : fullText;
                                Log.i(TAG, "ai_typeset_done requestId=" + callbackRequestId + " chars=" + fullText.length()
                                        + " prefix=" + responsePrefix);
                                Map<String, String> sections = null;
                                // 段落分类模式：LLM 只分类不改写 → build sections from original paragraphs
                                if (pendingTypesetParagraphMode) {
                                    Log.i(TAG, "ai_typeset_para_mode_done requestId=" + callbackRequestId);
                                    sections = buildTypesetSectionsFromParagraphs(fullText);
                                }
                                // 标准 V2 JSON 解析
                                if (sections == null || sections.isEmpty()) {
                                    sections = AiChatCoordinator.parseTypesetSections(fullText);
                                }
                                final Map<String, String> finalSections = sections;
                                if (finalSections != null && !finalSections.isEmpty()) {
                                    Log.i(TAG, "ai_typeset_v2_parsed sections=" + finalSections.size()
                                            + " keys=" + finalSections.keySet());
                                    pendingTypesetSections = finalSections;
                                    runOnUiThread(() -> handleTypesetV2Result(finalSections));
                                } else {
                                    Log.i(TAG, "ai_typeset_fallback_to_v1 — JSON parse failed, showing raw"
                                            + " prefix=" + responsePrefix);
                                    // Save for handleTypesetV2Result fallback
                                    pendingTypesetHtml = fullText;
                                    runOnUiThread(() -> showTypesetPreviewSheet(fullText));
                                }
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
                            } else if (calcFormulaRequestIds.contains(callbackRequestId)) {
                                // 公式生成请求失败：提示并关闭浮层
                                runOnUiThread(() -> {
                                    toastTodo("公式生成失败：" + safeMsg);
                                    dismissCalcFormulaDialog();
                                });
                            } else if (condFormatRequestIds.contains(callbackRequestId)) {
                                // 条件格式请求失败：提示并关闭浮层
                                runOnUiThread(() -> {
                                    toastTodo("条件格式生成失败：" + safeMsg);
                                    dismissCondFormatDialog();
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
                            } else if (callbackRequestId.equals(generateActiveRequestId)) {
                                runOnUiThread(() -> handlePptGenerateBatchFailure("network_error:" + code));
                            } else if (AiChatCoordinator.MODE_IMPRESS_OUTLINE.equals(taskType)) {
                                runOnUiThread(() -> {
                                    impressOutlineErrorText.setText("大纲生成失败：" + safeMsg);
                                    setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_ERROR);
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

    /**
     * 检测当前文档类型（Writer / Calc），通过 JS 读取 app.map.getDocType()。
     * 延迟检测（页面加载完成后调用），结果存到 mIsCalcDocument。
     */
    private void detectDocumentType() {
        if (mWebView == null) {
            return;
        }
        mWebView.evaluateJavascript(
            "(function(){try{return (window.app&&app.map&&app.map.getDocType)?app.map.getDocType():'';}catch(e){return '';}})();",
            value -> {
                mIsCalcDocument = "\"spreadsheet\"".equals(value);
                mIsImpressDocument = "\"presentation\"".equals(value);
                Log.i(TAG, "doc_type_detected isCalc=" + mIsCalcDocument
                        + " isImpress=" + mIsImpressDocument + " raw=" + value);
                ensureBottomToolbarController().updateDocumentType(mIsCalcDocument);
            });
    }

    int dpToPx(int dp) {
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

    private void onDocumentTap(float viewX, float viewY) {
        if (!documentLoaded || mWebView == null || !mIsEditModeActive || !mIsCalcDocument) {
            return;
        }
        Log.i(TAG, "calc_cell_tap_native viewX=" + Math.round(viewX) + " viewY=" + Math.round(viewY));
        mWebView.evaluateJavascript(
                "(function(){try{"
                        + "if(!window.AndroidCalcTap||typeof window.AndroidCalcTap.dispatchTapAt!=='function'){"
                        + "if(window.postMobileMessage){window.postMobileMessage('CALC_CELL_TAP failed=no_bridge');}"
                        + "return;}"
                        + "window.AndroidCalcTap.dispatchTapAt(" + viewX + "," + viewY + ");"
                        + "}catch(e){"
                        + "var m=(e&&e.message)?e.message:String(e);"
                        + "if(window.postMobileMessage){window.postMobileMessage('CALC_CELL_TAP failed=js '+m);}"
                        + "}})();",
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
        setupCalcHyperlinkCellPopup();
    }

    private void setupCalcHyperlinkCellPopup() {
        calcHyperlinkCellPopupController = new CalcHyperlinkCellPopupController(
                new CalcHyperlinkCellPopupController.Host() {
                    @Override
                    public android.content.Context getContext() {
                        return LOActivity.this;
                    }

                    @Override
                    public View findViewById(int id) {
                        return LOActivity.this.findViewById(id);
                    }

                    @Override
                    public View getBrowserView() {
                        return mWebView;
                    }

                    @Override
                    public float dpToPx(float dp) {
                        return LOActivity.this.dpToPx(Math.round(dp));
                    }

                    @Override
                    public void executeUnoCommand(String command) {
                        LOActivity.this.executeUnoCommand(command);
                    }

                    @Override
                    public void showExternalLinkConfirm(String url) {
                        LOActivity.this.showExternalLinkConfirm(url);
                    }
                });
        calcHyperlinkCellPopupController.setup();
    }

    private CalcHyperlinkCellPopupController ensureCalcHyperlinkCellPopupController() {
        if (calcHyperlinkCellPopupController == null) {
            setupCalcHyperlinkCellPopup();
        }
        return calcHyperlinkCellPopupController;
    }

    private void handleHyperlinkCellPopupShow(String json) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            String url = obj.optString("url", "");
            String text = obj.optString("text", url);
            float anchorX = (float) obj.optDouble("anchorX", 0);
            float anchorY = (float) obj.optDouble("anchorY", 0);
            ensureCalcHyperlinkCellPopupController().show(url, text, anchorX, anchorY);
        } catch (org.json.JSONException e) {
            Log.w(TAG, "hyperlink_popup_parse_failed", e);
        }
    }

    void showExternalLinkConfirm(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        if (externalLinkConfirmDialog != null && externalLinkConfirmDialog.isShowing()) {
            externalLinkConfirmDialog.dismiss();
        }
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_native_confirm, null, false);
        TextView titleView = root.findViewById(R.id.ai_dialog_header_title);
        TextView messageView = root.findViewById(R.id.native_confirm_message);
        LinearLayout buttonRow = root.findViewById(R.id.native_confirm_button_row);
        if (titleView != null) {
            titleView.setText("外部链接");
        }
        if (messageView != null) {
            messageView.setText("您正要离开文档。接下来的页面将在浏览器中打开：\n" + url);
        }
        View closeBtn = root.findViewById(R.id.ai_dialog_header_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> {
                if (externalLinkConfirmDialog != null) {
                    externalLinkConfirmDialog.dismiss();
                }
            });
        }
        if (buttonRow != null) {
            buttonRow.removeAllViews();
            TextView cancelBtn = buildExternalLinkDialogButton("取消", false);
            TextView openBtn = buildExternalLinkDialogButton("打开链接", true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(52), 1f);
            cancelBtn.setLayoutParams(lp);
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(0, dpToPx(52), 1f);
            openLp.setMarginStart(dpToPx(12));
            openBtn.setLayoutParams(openLp);
            cancelBtn.setOnClickListener(v -> {
                if (externalLinkConfirmDialog != null) {
                    externalLinkConfirmDialog.dismiss();
                }
            });
            final String targetUrl = url;
            openBtn.setOnClickListener(v -> {
                if (externalLinkConfirmDialog != null) {
                    externalLinkConfirmDialog.dismiss();
                }
                openExternalUrl(targetUrl);
            });
            buttonRow.addView(cancelBtn);
            buttonRow.addView(openBtn);
        }
        externalLinkConfirmDialog = new android.app.AlertDialog.Builder(this).create();
        externalLinkConfirmDialog.setView(root);
        org.libreoffice.androidlib.ai.AiDialogHelper.applyCloseOnlyDismiss(externalLinkConfirmDialog);
        org.libreoffice.androidlib.ai.AiDialogHelper.applyTransparentWindow(externalLinkConfirmDialog);
        externalLinkConfirmDialog.show();
        Log.i(TAG, "external_link_confirm url=" + url);
    }

    private TextView buildExternalLinkDialogButton(String label, boolean primary) {
        return buildThemedDialogButton(label, primary, false);
    }

    private TextView buildImpressDialogButton(String label, boolean primary) {
        return buildThemedDialogButton(label, primary, true);
    }

    private TextView buildThemedDialogButton(String label, boolean primary, boolean impressTheme) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setGravity(android.view.Gravity.CENTER);
        button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        button.setTypeface(null, primary ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        button.setTextColor(primary ? android.graphics.Color.WHITE : android.graphics.Color.parseColor("#333333"));
        if (primary) {
            button.setBackgroundResource(impressTheme
                    ? R.drawable.lolib_bg_impress_primary_button
                    : R.drawable.lolib_bg_calc_primary_button);
        } else {
            button.setBackgroundResource(R.drawable.lolib_bg_hyperlink_segment_track);
        }
        button.setClickable(true);
        button.setFocusable(true);
        int hPad = dpToPx(12);
        button.setPadding(hPad, dpToPx(14), hPad, dpToPx(14));
        return button;
    }

    private void openExternalUrl(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            Log.i(TAG, "external_link_open url=" + url);
        } catch (Exception e) {
            Log.w(TAG, "external_link_open_failed url=" + url, e);
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
        }
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

    // ==================== Calc AI公式生成浮层 ====================

    /**
     * 绑定公式生成浮层视图与监听。结构与 setupContinueWriteDialog 一致。
     */
    private void setupCalcFormulaDialog() {
        calcFormulaOverlay = findViewById(R.id.calc_formula_overlay);
        calcFormulaPanel = findViewById(R.id.calc_formula_dialog_panel);
        if (calcFormulaOverlay == null || calcFormulaPanel == null) {
            return;
        }
        calcFormulaInput = calcFormulaPanel.findViewById(R.id.calc_formula_input);
        calcFormulaInputGroup = calcFormulaPanel.findViewById(R.id.calc_formula_input_group);
        calcFormulaGenerateBtn = calcFormulaPanel.findViewById(R.id.calc_formula_generate_btn);
        calcFormulaContentScroll = calcFormulaPanel.findViewById(R.id.calc_formula_content_scroll);
        calcFormulaUserInputDisplay = calcFormulaPanel.findViewById(R.id.calc_formula_user_input_display);
        calcFormulaContentText = calcFormulaPanel.findViewById(R.id.calc_formula_content_text);
        calcFormulaCopyBar = calcFormulaPanel.findViewById(R.id.calc_formula_copy_bar);
        calcFormulaStopBtn = calcFormulaPanel.findViewById(R.id.calc_formula_stop_button);
        calcFormulaCompletedGroup = calcFormulaPanel.findViewById(R.id.calc_formula_completed_group);
        calcFormulaRegenBtn = calcFormulaPanel.findViewById(R.id.calc_formula_regenerate_button);
        calcFormulaInsertBtn = calcFormulaPanel.findViewById(R.id.calc_formula_insert_button);
        calcFormulaCellHint = calcFormulaPanel.findViewById(R.id.calc_formula_cell_hint);

        if (calcFormulaCopyBar != null) {
            calcFormulaCopyBar.setOnClickListener(v -> onCalcFormulaCopy());
        }
        calcFormulaOverlay.setOnClickListener(v -> dismissCalcFormulaDialog());
        View closeBtn = calcFormulaPanel.findViewById(R.id.calc_formula_close_button);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismissCalcFormulaDialog());
        }
        if (calcFormulaGenerateBtn != null) {
            calcFormulaGenerateBtn.setOnClickListener(v -> onCalcFormulaGenerate());
        }
        if (calcFormulaStopBtn != null) {
            calcFormulaStopBtn.setOnClickListener(v -> onCalcFormulaStop());
        }
        if (calcFormulaRegenBtn != null) {
            calcFormulaRegenBtn.setOnClickListener(v -> onCalcFormulaRegenerate());
        }
        if (calcFormulaInsertBtn != null) {
            calcFormulaInsertBtn.setOnClickListener(v -> onCalcFormulaInsertOrCopy());
        }
    }

    /**
     * 打开公式生成浮层。先从 JS 读取当前选中单元格地址，再以输入态展示。
     */
    private void openCalcFormulaDialog() {
        if (calcSelectedRange != null && !calcSelectedRange.isEmpty()) {
            String[] parts = calcSelectedRange.split(":");
            openCalcFormulaDialogWithAddress(parts[0]);
            Log.i(TAG, "formula_open addr=" + calcFormulaCellAddress + " source=cached");
        } else if (mWebView != null) {
            openCalcFormulaDialogWithAddressNow();
        } else {
            openCalcFormulaDialogWithAddress("");
        }
    }

    private void openCalcFormulaDialogWithAddressNow() {
        if (mWebView == null) { openCalcFormulaDialogWithAddress(""); return; }
        mWebView.evaluateJavascript(
            "(function(){var cl=function(c){var s='';while(c>=0){s=String.fromCharCode(65+(c%26))+s;c=Math.floor(c/26)-1;}return s;};var a=app.calc&&app.calc.cellAddress;if(!a)return'';return cl(a.x)+(a.y+1);})()",
            new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    String addr = "";
                    if (value != null && !value.equals("null") && value.length() > 2) {
                        try {
                            Object p = new org.json.JSONTokener(value).nextValue();
                            addr = (p instanceof String) ? (String) p : "";
                        } catch (org.json.JSONException e) { /* fallback */ }
                    }
                    calcFormulaCellAddress = addr;
                    final String r = addr;
                    runOnUiThread(() -> openCalcFormulaDialogWithAddress(r));
                    Log.i(TAG, "formula_open addr=" + r + " source=direct");
                }
            });
    }

    private void openCalcFormulaDialogWithAddress(String cellAddress) {
        setCalcFormulaDialogState(STATE_INPUT);
        if (calcFormulaCellHint != null) {
            if (cellAddress != null && !cellAddress.isEmpty()) {
                calcFormulaCellHint.setText("当前单元格：" + cellAddress);
                calcFormulaCellHint.setVisibility(View.VISIBLE);
            } else {
                calcFormulaCellHint.setVisibility(View.GONE);
            }
        }
        calcFormulaOverlay.setVisibility(View.VISIBLE);
        calcFormulaPanel.setVisibility(View.VISIBLE);
        calcFormulaPanel.post(this::positionCalcFormulaDialogCenter);
        Log.i(TAG, "calc_formula_dialog_open cellAddress=" + cellAddress);
    }

    /**
     * 居中定位公式生成浮层，复用续写浮层的定位逻辑。
     */
    private void positionCalcFormulaDialogCenter() {
        if (calcFormulaPanel == null) {
            return;
        }
        View parent = (View) calcFormulaPanel.getParent();
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
                (ConstraintLayout.LayoutParams) calcFormulaPanel.getLayoutParams();
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
        calcFormulaPanel.setLayoutParams(lp);
    }

    // 三态常量
    private static final int STATE_INPUT = 0;
    private static final int STATE_GENERATING = 1;
    private static final int STATE_COMPLETED = 2;

    /**
     * 切换公式生成浮层三态。
     * INPUT：显示输入区 + 生成按钮，隐藏内容/停止/完成组
     * GENERATING：显示流式输出 + 停止按钮，隐藏输入区/完成组
     * COMPLETED：显示流式输出 + 完成组（重新生成+复制到单元格）+ 复制栏
     */
    private void setCalcFormulaDialogState(int state) {
        boolean input = state == STATE_INPUT;
        boolean generating = state == STATE_GENERATING;
        boolean completed = state == STATE_COMPLETED;

        if (calcFormulaInputGroup != null) {
            calcFormulaInputGroup.setVisibility(input ? View.VISIBLE : View.GONE);
        }
        if (calcFormulaContentScroll != null) {
            calcFormulaContentScroll.setVisibility((generating || completed) ? View.VISIBLE : View.GONE);
        }
        if (calcFormulaStopBtn != null) {
            calcFormulaStopBtn.setVisibility(generating ? View.VISIBLE : View.GONE);
        }
        if (calcFormulaCompletedGroup != null) {
            calcFormulaCompletedGroup.setVisibility(completed ? View.VISIBLE : View.GONE);
        }
        if (calcFormulaCopyBar != null) {
            calcFormulaCopyBar.setVisibility(completed ? View.VISIBLE : View.GONE);
        }
        if (generating || input) {
            if (calcFormulaContentText != null) {
                calcFormulaContentText.setText("");
            }
            calcFormulaResultText = "";
        }
    }

    /**
     * 用户点击"AI生成公式"：取输入框内容，构建请求并发起。
     */
    private void onCalcFormulaGenerate() {
        String input = calcFormulaInput != null ? calcFormulaInput.getText().toString().trim() : "";
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入公式需求", Toast.LENGTH_SHORT).show();
            return;
        }
        // Hide keyboard
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && calcFormulaInput != null) {
            imm.hideSoftInputFromWindow(calcFormulaInput.getWindowToken(), 0);
        }
        // Show user input in the display area
        if (calcFormulaUserInputDisplay != null) {
            calcFormulaUserInputDisplay.setText("需求：" + input);
            calcFormulaUserInputDisplay.setVisibility(View.VISIBLE);
        }
        setCalcFormulaDialogState(STATE_GENERATING);
        startCalcFormulaRequest(input);
    }

    /**
     * 构建并发起公式生成请求。取用户输入和选中单元格地址，构建 AiChatCoordinator 的 calc_formula messages，
     * 将流式目标注册到公式显示 TextView。
     */
    private void startCalcFormulaRequest(String userInput) {
        try {
            JSONObject context = new JSONObject();
            context.put("prompt", "");
            context.put("question", "");

            JSONObject request = new JSONObject();
            String requestId = "cf-" + UUID.randomUUID().toString();
            request.put("requestId", requestId);
            request.put("taskType", AiChatCoordinator.MODE_CALC_FORMULA);
            request.put("selection", userInput);
            request.put("context", context);
            request.put("modelMode", "base");
            request.put("history", new JSONArray());
            // Store cell address for prompt building
            request.put("cellAddress", calcFormulaCellAddress == null ? "" : calcFormulaCellAddress);

            aiActiveRequestId = requestId;
            aiStreamingRequestId = requestId;
            aiRequestModeById.put(requestId, AiChatCoordinator.MODE_CALC_FORMULA);
            aiTextByRequestId.put(requestId, new StringBuilder());
            if (calcFormulaContentText != null) {
                aiStreamingViewByRequestId.put(requestId, calcFormulaContentText);
            }
            calcFormulaActiveRequestId = requestId;
            calcFormulaRequestIds.add(requestId);

            Log.i(TAG, "calc_formula_request requestId=" + requestId
                    + " input=" + userInput + " cell=" + calcFormulaCellAddress);
            startAiRequestSession(request, -1);
        } catch (JSONException e) {
            dispatchAiError("", "invalid_payload", "Failed to build calc-formula request");
            Log.e(TAG, "Failed to build calc-formula request", e);
            dismissCalcFormulaDialog();
        }
    }

    /**
     * 公式生成请求自然完成。切到完成态。
     */
    private void onCalcFormulaDone(String requestId, String fullText) {
        final String text = fullText == null ? "" : fullText;
        runOnUiThread(() -> {
            calcFormulaResultText = text;
            setCalcFormulaDialogState(STATE_COMPLETED);
            Log.i(TAG, "calc_formula_done requestId=" + requestId + " chars=" + text.length());
        });
    }

    /**
     * 点停止按钮：取消在途请求，保留已流式部分，切到完成态。
     */
    private void onCalcFormulaStop() {
        String rid = calcFormulaActiveRequestId;
        if (!rid.isEmpty()) {
            cancelAiRequest(rid);
        }
        StringBuilder partial = aiTextByRequestId.get(rid);
        String text = partial == null ? "" : partial.toString();
        if (text.isEmpty() && calcFormulaContentText != null) {
            text = calcFormulaContentText.getText().toString();
        }
        calcFormulaResultText = text;
        if (!rid.isEmpty()) {
            aiStreamingViewByRequestId.remove(rid);
        }
        setCalcFormulaDialogState(STATE_COMPLETED);
        Log.i(TAG, "calc_formula_stopped requestId=" + rid + " chars=" + text.length());
    }

    /**
     * 点「重新生成」：取消旧请求，回到输入态让用户修改后重新生成。
     */
    private void onCalcFormulaRegenerate() {
        if (!calcFormulaActiveRequestId.isEmpty()) {
            cancelAiRequest(calcFormulaActiveRequestId);
            aiStreamingViewByRequestId.remove(calcFormulaActiveRequestId);
        }
        // Return to input state, keep the previous input text
        setCalcFormulaDialogState(STATE_INPUT);
        if (calcFormulaUserInputDisplay != null) {
            calcFormulaUserInputDisplay.setVisibility(View.GONE);
        }
        Log.i(TAG, "calc_formula_regenerate");
    }

    /**
     * 点「复制到单元格」（暂做复制）：将公式结果复制到剪贴板。
     * 后面改为真实插入单元格逻辑。
     */
    private void onCalcFormulaInsertOrCopy() {
        String text = calcFormulaResultText;
        if ((text == null || text.isEmpty()) && calcFormulaContentText != null) {
            text = calcFormulaContentText.getText().toString();
        }
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "没有可复制的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("calc_formula", text));
        }
        Toast.makeText(this, "公式已复制", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "calc_formula_insert_copy chars=" + text.length());
    }

    /**
     * 点复制栏：将公式结果复制到剪贴板。
     */
    private void onCalcFormulaCopy() {
        String text = calcFormulaResultText;
        if ((text == null || text.isEmpty()) && calcFormulaContentText != null) {
            text = calcFormulaContentText.getText().toString();
        }
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("calc_formula", text));
        }
        Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "calc_formula_copy chars=" + text.length());
    }

    /**
     * 关闭公式生成浮层：取消在途请求、清理流式注册、隐藏 overlay+panel。
     */
    private void dismissCalcFormulaDialog() {
        if (!calcFormulaActiveRequestId.isEmpty()) {
            cancelAiRequest(calcFormulaActiveRequestId);
            aiStreamingViewByRequestId.remove(calcFormulaActiveRequestId);
            calcFormulaActiveRequestId = "";
        }
        if (calcFormulaOverlay != null) {
            calcFormulaOverlay.setVisibility(View.GONE);
        }
        if (calcFormulaPanel != null) {
            calcFormulaPanel.setVisibility(View.GONE);
        }
        Log.i(TAG, "calc_formula_dialog_dismiss");
    }

    // ==================== Calc AI公式生成浮层结束 ====================

    // ==================== Calc AI条件格式浮层 ====================
    // 三态：STATE_INPUT=0 / STATE_GENERATING=1 / STATE_COMPLETED=2
    private static final int COND_FORMAT_STATE_INPUT = 0;
    private static final int COND_FORMAT_STATE_GENERATING = 1;
    private static final int COND_FORMAT_STATE_COMPLETED = 2;

    // 数据处理四态
    private static final int DP_STATE_INPUT = 0;
    private static final int DP_STATE_GENERATING = 1;
    private static final int DP_STATE_COMPLETED = 2;
    private static final int DP_STATE_EXECUTING = 3;

    private void setupCondFormatDialog() {
        condFormatOverlay = findViewById(R.id.cond_format_overlay);
        condFormatPanel = findViewById(R.id.cond_format_dialog_panel);
        if (condFormatOverlay == null || condFormatPanel == null) {
            return;
        }
        condFormatInput = condFormatPanel.findViewById(R.id.cond_format_input);
        condFormatInputGroup = condFormatPanel.findViewById(R.id.cond_format_input_group);
        condFormatGenerateBtn = condFormatPanel.findViewById(R.id.cond_format_generate_btn);
        condFormatContentScroll = condFormatPanel.findViewById(R.id.cond_format_content_scroll);
        condFormatUserInputDisplay = condFormatPanel.findViewById(R.id.cond_format_user_input_display);
        condFormatContentText = condFormatPanel.findViewById(R.id.cond_format_content_text);
        condFormatCopyBar = condFormatPanel.findViewById(R.id.cond_format_copy_bar);
        condFormatStopBtn = condFormatPanel.findViewById(R.id.cond_format_stop_button);
        condFormatCompletedGroup = condFormatPanel.findViewById(R.id.cond_format_completed_group);
        condFormatRegenBtn = condFormatPanel.findViewById(R.id.cond_format_regenerate_button);
        condFormatApplyBtn = condFormatPanel.findViewById(R.id.cond_format_apply_button);
        condFormatRangeHint = condFormatPanel.findViewById(R.id.cond_format_range_hint);

        if (condFormatCopyBar != null) {
            condFormatCopyBar.setOnClickListener(v -> onCondFormatCopy());
        }
        condFormatOverlay.setOnClickListener(v -> dismissCondFormatDialog());
        View closeBtn = condFormatPanel.findViewById(R.id.cond_format_close_button);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismissCondFormatDialog());
        }
        if (condFormatGenerateBtn != null) {
            condFormatGenerateBtn.setOnClickListener(v -> onCondFormatGenerate());
        }
        if (condFormatStopBtn != null) {
            condFormatStopBtn.setOnClickListener(v -> onCondFormatStop());
        }
        if (condFormatRegenBtn != null) {
            condFormatRegenBtn.setOnClickListener(v -> onCondFormatRegenerate());
        }
        if (condFormatApplyBtn != null) {
            condFormatApplyBtn.setOnClickListener(v -> onCondFormatApply());
        }
    }

    private void positionCondFormatDialogCenter() {
        if (condFormatPanel == null) return;
        ViewGroup parent = (ViewGroup) condFormatPanel.getParent();
        if (parent == null) return;
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        if (parentWidth <= 0 || parentHeight <= 0) return;
        int targetWidth = Math.max(300, parentWidth - (int) (48 * getResources().getDisplayMetrics().density));
        int targetHeight = Math.max(400, (int) (parentHeight * 0.8));
        int x = (parentWidth - targetWidth) / 2;
        int y = (parentHeight - targetHeight) / 3;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) condFormatPanel.getLayoutParams();
        if (lp != null) {
            lp.width = targetWidth;
            lp.height = targetHeight;
            lp.setMargins(x, y, x, 0);
            condFormatPanel.setLayoutParams(lp);
            condFormatPanel.requestLayout();
        }
    }

    private void openCondFormatDialog() {
        // 打开时实时读取选区，避免 preRead 缓存的 A1:A3 与真实选区 A1:E14 不一致
        if (mWebView != null) {
            openCondFormatDialogWithRangeNow();
        } else if (calcSelectedRange != null && !calcSelectedRange.isEmpty()) {
            openCondFormatDialogWithRange(calcSelectedRange);
            Log.i(TAG, "cond_format_open range=" + condFormatCellRange + " source=cached_fallback");
        } else {
            openCondFormatDialogWithRange("");
            Log.w(TAG, "cond_format_open range= source=nowebview");
        }
    }

    private void openCondFormatDialogWithRangeNow() {
        if (mWebView == null) { openCondFormatDialogWithRange(""); return; }
        readCalcSelectionRangeAsync(range -> runOnUiThread(() -> {
            calcSelectedRange = range;
            openCondFormatDialogWithRange(range);
            Log.i(TAG, "cond_format_open range=" + range + " source=direct");
        }));
    }

    private void openCondFormatDialogWithRange(String range) {
        setCondFormatDialogState(COND_FORMAT_STATE_INPUT);
        condFormatCellRange = (range == null) ? "" : range;
        if (!condFormatCellRange.isEmpty()) {
            if (condFormatRangeHint != null) {
                condFormatRangeHint.setText("选中范围: " + condFormatCellRange);
                condFormatRangeHint.setVisibility(View.VISIBLE);
            }
        } else {
            if (condFormatRangeHint != null) {
                condFormatRangeHint.setVisibility(View.GONE);
            }
        }
        if (condFormatOverlay != null) condFormatOverlay.setVisibility(View.VISIBLE);
        if (condFormatPanel != null) {
            condFormatPanel.setVisibility(View.VISIBLE);
            condFormatPanel.post(this::positionCondFormatDialogCenter);
        }
    }

    private void setCondFormatDialogState(int state) {
        boolean isInput = (state == COND_FORMAT_STATE_INPUT);
        boolean isGenerating = (state == COND_FORMAT_STATE_GENERATING);
        boolean isCompleted = (state == COND_FORMAT_STATE_COMPLETED);
        if (condFormatInputGroup != null) {
            condFormatInputGroup.setVisibility(isInput ? View.VISIBLE : View.GONE);
        }
        if (condFormatContentScroll != null) {
            condFormatContentScroll.setVisibility((isGenerating || isCompleted) ? View.VISIBLE : View.GONE);
        }
        if (condFormatStopBtn != null) {
            condFormatStopBtn.setVisibility(isGenerating ? View.VISIBLE : View.GONE);
        }
        if (condFormatCompletedGroup != null) {
            condFormatCompletedGroup.setVisibility(isCompleted ? View.VISIBLE : View.GONE);
        }
        if (condFormatCopyBar != null) {
            condFormatCopyBar.setVisibility(isCompleted ? View.VISIBLE : View.GONE);
        }
        if (isInput || isGenerating) {
            if (condFormatContentText != null) condFormatContentText.setText("");
            condFormatResultText = "";
        }
    }

    private void onCondFormatGenerate() {
        if (condFormatInput == null) return;
        String input = condFormatInput.getText().toString().trim();
        if (input.isEmpty()) {
            toastTodo("请输入条件格式需求");
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && condFormatInput.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(condFormatInput.getWindowToken(), 0);
        }
        if (condFormatUserInputDisplay != null) {
            condFormatUserInputDisplay.setText(input);
            condFormatUserInputDisplay.setVisibility(View.VISIBLE);
        }
        setCondFormatDialogState(COND_FORMAT_STATE_GENERATING);
        startCondFormatRequest(input);
    }

    private void startCondFormatRequest(String userInput) {
        try {
            // Read selected cell data via native JNI getTextSelection (sub-thread safe)
            String cellData = "";
            try {
                cellData = getTextSelection("text/plain;charset=utf-8");
            } catch (Exception e) {
                Log.w(TAG, "cond_format_getTextSelection_failed", e);
            }
            if (cellData == null) cellData = "";
            // 大表采样：前 50 行 + 后 5 行（便于 AI 识别表头/样例与尾部汇总行）
            String dataSample = cellData;
            final int headLineLimit = 50;
            final int tailLineLimit = 5;
            int lineCount = cellData.isEmpty() ? 0 : cellData.split("\n", -1).length;
            if (lineCount > headLineLimit) {
                String[] lines = cellData.split("\n", -1);
                StringBuilder sb = new StringBuilder();
                sb.append("（选中区域共 ").append(lineCount).append(" 行，以下展示前 ")
                        .append(headLineLimit).append(" 行与最后 ").append(tailLineLimit).append(" 行）\n");
                for (int i = 0; i < headLineLimit && i < lines.length; i++) {
                    sb.append(lines[i]).append("\n");
                }
                int tailStart = Math.max(0, lines.length - tailLineLimit);
                if (tailStart > headLineLimit) {
                    sb.append("……（中间省略 ").append(tailStart - headLineLimit).append(" 行）……\n");
                    for (int i = tailStart; i < lines.length; i++) {
                        sb.append(lines[i]).append("\n");
                    }
                } else if (lines.length > headLineLimit) {
                    for (int i = headLineLimit; i < lines.length; i++) {
                        sb.append(lines[i]).append("\n");
                    }
                }
                dataSample = sb.toString();
            }
            Log.i(TAG, "cond_format_data_read chars=" + cellData.length() + " rows=" + lineCount
                    + (lineCount > headLineLimit ? " sampled=head" + headLineLimit + "+tail" + tailLineLimit : ""));

            JSONObject request = new JSONObject();
            String requestId = java.util.UUID.randomUUID().toString();
            request.put("requestId", requestId);
            request.put("taskType", AiChatCoordinator.MODE_CALC_COND_FORMAT);
            request.put("selection", userInput);
            request.put("cellRange", condFormatCellRange);
            request.put("cellData", dataSample);
            request.put("modelMode", "base");
            request.put("history", new JSONArray());
            JSONObject ctxObj = new JSONObject();
            ctxObj.put("cellRange", condFormatCellRange);
            request.put("context", ctxObj);

            aiActiveRequestId = requestId;
            aiStreamingRequestId = requestId;
            aiRequestModeById.put(requestId, AiChatCoordinator.MODE_CALC_COND_FORMAT);
            aiTextByRequestId.put(requestId, new StringBuilder());
            if (condFormatContentText != null) {
                aiStreamingViewByRequestId.put(requestId, condFormatContentText);
            }
            condFormatActiveRequestId = requestId;
            condFormatRequestIds.add(requestId);

            Log.i(TAG, "cond_format_request_start requestId=" + requestId
                    + " input=" + userInput + " range=" + condFormatCellRange);
            startAiRequestSession(request, -1);
        } catch (JSONException e) {
            Log.e(TAG, "cond_format_request_json_error", e);
            toastTodo("请求构建失败");
        }
    }

    private void onCondFormatDone(String requestId, String fullText) {
        runOnUiThread(() -> {
            String text = fullText != null ? fullText : "";
            condFormatResultText = text;

            // 尝试 JSON 解析（新流程：AI 输出结构化 JSON）
            condFormatPlan = null;
            CondFormatApplier.CondFormatPlan plan = CondFormatApplier.parseFromJson(text);
            if (plan != null && plan.isValid()) {
                condFormatPlan = plan;
                String displayText = "✅ " + plan.description;
                if (!plan.range.isEmpty()) {
                    displayText += "\n范围: " + plan.range;
                }
                String formatSummary = CondFormatApplier.buildFormatSummary(plan.formatJson);
                if (!formatSummary.isEmpty()) {
                    displayText += "\n样式: " + formatSummary;
                } else if (plan.style != null && !plan.style.isEmpty()) {
                    displayText += "\n样式: " + plan.style;
                }
                condFormatContentText.setText(displayText);
                // AI 返回的 range 通常比 _cellSelectionArea 更准确，更新弹窗显示
                String aiRange = plan.range;
                if (aiRange != null && !aiRange.isEmpty()
                        && condFormatRangeHint != null
                        && !aiRange.equals(condFormatCellRange)) {
                    condFormatCellRange = aiRange;
                    condFormatRangeHint.setText("选中范围: " + condFormatCellRange);
                    Log.i(TAG, "cond_format_range_ai_updated range=" + condFormatCellRange);
                }
                Log.i(TAG, "cond_format_json_parsed type=" + plan.conditionType
                        + " value=" + plan.value + " range=" + plan.range
                        + " format=" + (plan.formatJson != null ? plan.formatJson : plan.style));
            } else {
                // 回退：直接显示 AI 回复文本
                condFormatContentText.setText(text);
                condFormatPlan = null;
                // 输出 AI 原始响应前 200 字符，便于调试 JSON 解析失败
                String rawPreview = text.length() > 200 ? text.substring(0, 200) + "..." : text;
                Log.i(TAG, "cond_format_fallback_text chars=" + text.length()
                        + " preview=" + rawPreview);
            }
            setCondFormatDialogState(COND_FORMAT_STATE_COMPLETED);
            Log.i(TAG, "cond_format_done requestId=" + requestId + " chars=" + condFormatResultText.length());
        });
    }

    private void onCondFormatStop() {
        String rid = condFormatActiveRequestId;
        if (rid == null || rid.isEmpty()) return;
        cancelAiRequest(rid);
        StringBuilder partialSb = aiTextByRequestId.remove(rid);
        String partial = partialSb != null ? partialSb.toString() : "";
        if (partial == null || partial.isEmpty()) {
            partial = condFormatContentText != null ? condFormatContentText.getText().toString() : "";
        }
        condFormatResultText = partial;
        condFormatPlan = null;
        aiStreamingViewByRequestId.remove(rid);
        condFormatActiveRequestId = "";
        setCondFormatDialogState(COND_FORMAT_STATE_COMPLETED);
        Log.i(TAG, "cond_format_stop requestId=" + rid + " chars=" + condFormatResultText.length());
    }

    private void onCondFormatRegenerate() {
        String rid = condFormatActiveRequestId;
        if (!rid.isEmpty()) {
            cancelAiRequest(rid);
            aiStreamingViewByRequestId.remove(rid);
            condFormatActiveRequestId = "";
        }
        condFormatPlan = null;
        setCondFormatDialogState(COND_FORMAT_STATE_INPUT);
        if (condFormatUserInputDisplay != null) {
            condFormatUserInputDisplay.setVisibility(View.GONE);
        }
    }

    private void onCondFormatCopy() {
        String text = condFormatResultText;
        if (text == null || text.isEmpty()) {
            text = condFormatContentText != null ? condFormatContentText.getText().toString() : "";
        }
        if (text.isEmpty()) {
            toastTodo("没有可复制的内容");
            return;
        }
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("cond_format", text));
            toastTodo("规则已复制");
        }
    }

    private void onCondFormatApply() {
        if (condFormatApplying) {
            Log.w(TAG, "cond_format_apply_skip_already_applying");
            return;
        }
        condFormatApplying = true;
        CondFormatApplier.Host host = new CondFormatApplier.Host() {
            @Override
            public void postMobileUnoCommand(String cmd, String args) {
                Log.i(TAG, "cond_format_post_mobile_uno cmd=" + cmd + " args=" + args);
                if (args == null || args.isEmpty() || "{}".equals(args.trim())) {
                    postMobileMessage("uno " + cmd);
                } else {
                    postMobileMessage("uno " + cmd + " " + args);
                }
                nudgeSocketIfStalled("cond_format_apply");
            }
            @Override
            public void registerApplyResultCallback(CondFormatApplier.ApplyResultCallback callback) {
                condFormatApplyCallback = callback;
                getMainHandler().removeCallbacks(condFormatApplyTimeoutRunnable);
                if (callback != null) {
                    getMainHandler().postDelayed(condFormatApplyTimeoutRunnable, 5000);
                }
            }
            @Override
            public void postUnoCommand(String cmd, String args, boolean notify) {
                Log.i(TAG, "cond_format_post_uno cmd=" + cmd + " notify=" + notify);
                LOActivity.this.postUnoCommand(cmd, args, notify);
            }
            @Override
            public void evaluateJavascript(String script) {
                if (mWebView != null) mWebView.evaluateJavascript(script, null);
            }
            @Override
            public void evaluateJavascript(String script, android.webkit.ValueCallback<String> callback) {
                if (mWebView != null) mWebView.evaluateJavascript(script, callback);
            }
            @Override
            public void runOnUiThread(Runnable r) {
                LOActivity.this.runOnUiThread(r);
            }
        };
        CondFormatApplier applier = new CondFormatApplier(host);

        // 优先使用 JSON plan（新流程）
        if (condFormatPlan != null && condFormatPlan.isValid()) {
            CondFormatApplier.CondFormatPlan plan = condFormatPlan;
            condFormatPlan = null;
            Log.i(TAG, "cond_format_apply_path=json plan=" + plan.conditionType
                    + " value=" + plan.value + " range=" + plan.range
                    + " format=" + (plan.formatJson != null ? plan.formatJson : plan.style));
            // 如果 plan 里没 range，用光标位置作为 fallback
            if ((plan.range == null || plan.range.isEmpty())
                    && condFormatCellRange != null && !condFormatCellRange.isEmpty()) {
                plan = new CondFormatApplier.CondFormatPlan(
                    plan.conditionType, plan.value, plan.value2,
                    condFormatCellRange, plan.style, plan.formatJson, plan.description);
            }
            if (plan.range == null || plan.range.isEmpty()) {
                toastTodo("无法获取单元格范围，请先选择数据区域");
                condFormatApplying = false;
                return;
            }
            String validationError = CondFormatApplier.validatePlan(plan);
            if (validationError != null) {
                toastTodo(validationError);
                condFormatApplying = false;
                return;
            }
            // 清除条件格式 / 恢复默认样式（同步 JNI 路径，不依赖 unocommandresult 回调）
            if ("clear".equals(plan.conditionType)) {
                final String clearRange = plan.range;
                Log.i(TAG, "cond_format_apply_clear range=" + clearRange);
                new Thread(() -> {
                    try {
                        clearFormatInRange(clearRange);
                        runOnUiThread(() -> {
                            toastTodo("已清除格式");
                            Log.i(TAG, "cond_format_clear_success range=" + clearRange);
                            forceVisibleTileRedrawFromAndroid("cond_format_clear");
                            dismissCondFormatDialog();
                            condFormatApplying = false;
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "cond_format_clear_exception", e);
                        runOnUiThread(() -> {
                            toastTodo("清除格式时出错: " + e.getMessage());
                            condFormatApplying = false;
                        });
                    }
                }, "cond-format-clear").start();
                return;
            }
            // formula 类型走 Core Direct 模式（布尔公式，按行求值）
            if ("formula".equals(plan.conditionType)) {
                String formula = CondFormatApplier.normalizeFormula(plan.value);
                plan = new CondFormatApplier.CondFormatPlan(
                        "formula", formula, "",
                        plan.range, plan.style, plan.formatJson, plan.description);
                Log.i(TAG, "cond_format_formula_direct formula=" + formula + " range=" + plan.range);
            }

            String applyRange = plan.range;
            Log.i(TAG, "cond_format_apply_json type=" + plan.conditionType
                    + " value=" + plan.value + " range=" + applyRange
                    + " fmtRule=" + CondFormatApplier.getFormatRule(plan.conditionType)
                    + " style=" + plan.style);
            try {
                applier.applyDirect(plan, success -> runOnUiThread(() -> {
                    if (success) {
                        toastTodo("条件格式已应用");
                        Log.i(TAG, "cond_format_apply_success");
                    } else {
                        toastTodo("条件格式应用失败，请检查范围或样式");
                        Log.w(TAG, "cond_format_apply_failed");
                    }
                    dismissCondFormatDialog();
                    condFormatApplying = false;
                }));
                Log.i(TAG, "cond_format_apply_direct_dispatched");
            } catch (Exception e) {
                Log.e(TAG, "cond_format_apply_exception", e);
                toastTodo("应用条件格式时出错: " + e.getMessage());
                condFormatApplying = false;
            }
            return;
        }

        // JSON 解析失败时不走 legacy 弹窗注入（规则不可靠，易误应用）
        Log.w(TAG, "cond_format_apply_path=blocked fallback_reason=plan_null_or_invalid");
        String text = condFormatResultText;
        if (text == null || text.isEmpty()) {
            text = condFormatContentText != null ? condFormatContentText.getText().toString() : "";
        }
        if (text.isEmpty()) {
            toastTodo("没有可应用的规则");
        } else if (text.contains("conditionType")) {
            toastTodo("规则解析失败，请点击重新生成");
        } else {
            toastTodo("无法识别规则格式，请重新描述需求");
        }
        condFormatApplying = false;
    }

    private void dismissCondFormatDialog() {
        if (!condFormatActiveRequestId.isEmpty()) {
            cancelAiRequest(condFormatActiveRequestId);
            aiStreamingViewByRequestId.remove(condFormatActiveRequestId);
            condFormatActiveRequestId = "";
        }
        condFormatPlan = null;
        condFormatApplying = false;
        if (condFormatOverlay != null) {
            condFormatOverlay.setVisibility(View.GONE);
        }
        if (condFormatPanel != null) {
            condFormatPanel.setVisibility(View.GONE);
        }
        Log.i(TAG, "cond_format_dialog_dismiss");
    }
    // ==================== Calc AI条件格式浮层结束 ====================

    // ==================== Calc AI数据处理浮层 ====================

    private void setupDataProcessDialog() {
        dpOverlay = findViewById(R.id.dp_overlay);
        dpPanel = findViewById(R.id.dp_dialog_panel);
        if (dpOverlay == null || dpPanel == null) return;

        dpInput = dpPanel.findViewById(R.id.dp_input);
        dpInputGroup = dpPanel.findViewById(R.id.dp_input_group);
        dpGenerateBtn = dpPanel.findViewById(R.id.dp_generate_btn);
        dpContentScroll = dpPanel.findViewById(R.id.dp_content_scroll);
        dpUserInputDisplay = dpPanel.findViewById(R.id.dp_user_input_display);
        dpContentText = dpPanel.findViewById(R.id.dp_content_text);
        dpStopBtn = dpPanel.findViewById(R.id.dp_stop_button);
        dpGeneratingStatus = dpPanel.findViewById(R.id.dp_generating_status);
        dpCompletedGroup = dpPanel.findViewById(R.id.dp_completed_group);
        dpRegenerateBtn = dpPanel.findViewById(R.id.dp_regenerate_button);
        dpExecuteBtn = dpPanel.findViewById(R.id.dp_execute_button);
        dpExecutingStatus = dpPanel.findViewById(R.id.dp_executing_status);
        dpRangeHint = dpPanel.findViewById(R.id.dp_range_hint);
        dpTitle = dpPanel.findViewById(R.id.dp_title);

        dpOverlay.setOnClickListener(v -> dismissDataProcessDialog());
        View closeBtn = dpPanel.findViewById(R.id.dp_close_button);
        if (closeBtn != null) closeBtn.setOnClickListener(v -> dismissDataProcessDialog());
        if (dpGenerateBtn != null) dpGenerateBtn.setOnClickListener(v -> onDataProcessGenerate());
        if (dpStopBtn != null) dpStopBtn.setOnClickListener(v -> onDataProcessStop());
        if (dpRegenerateBtn != null) dpRegenerateBtn.setOnClickListener(v -> onDataProcessRegenerate());
        if (dpExecuteBtn != null) dpExecuteBtn.setOnClickListener(v -> onDataProcessExecute());
    }

    private void setDataProcessDialogState(int state) {
        boolean input = state == DP_STATE_INPUT;
        boolean generating = state == DP_STATE_GENERATING;
        boolean completed = state == DP_STATE_COMPLETED;
        boolean executing = state == DP_STATE_EXECUTING;

        if (dpInputGroup != null) dpInputGroup.setVisibility(input ? View.VISIBLE : View.GONE);
        if (dpContentScroll != null)
            dpContentScroll.setVisibility((generating || completed) ? View.VISIBLE : View.GONE);
        if (dpStopBtn != null) dpStopBtn.setVisibility(generating ? View.VISIBLE : View.GONE);
        if (dpGeneratingStatus != null)
            dpGeneratingStatus.setVisibility(generating ? View.VISIBLE : View.GONE);
        if (dpCompletedGroup != null) dpCompletedGroup.setVisibility(completed ? View.VISIBLE : View.GONE);
        // 分析模式或无操作时隐藏"执行操作"按钮
        if (dpExecuteBtn != null) {
            boolean hasOps = dpPendingOperations != null && dpPendingOperations.length() > 0;
            boolean showExecute = completed && !dpIsAnalysisMode && hasOps;
            dpExecuteBtn.setVisibility(showExecute ? View.VISIBLE : View.GONE);
        }
        if (dpExecutingStatus != null) {
            dpExecutingStatus.setVisibility(executing ? View.VISIBLE : View.GONE);
            if (executing) dpExecutingStatus.setText("正在执行操作...");
        }
        if (input || generating) {
            if (dpContentText != null) dpContentText.setText("");
            dpResultText = "";
            dpPendingOperations = null;
        }
    }

    private void positionDataProcessDialogCenter() {
        if (dpPanel == null) return;
        View parent = (View) dpPanel.getParent();
        if (!(parent instanceof ConstraintLayout)) return;
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        if (parentWidth <= 0 || parentHeight <= 0) return;
        int targetWidth = parentWidth - dpToPx(48);
        int targetHeight = Math.max(dpToPx(400), (int) (parentHeight * 0.8));

        ConstraintLayout.LayoutParams lp =
                (ConstraintLayout.LayoutParams) dpPanel.getLayoutParams();
        lp.width = targetWidth;
        lp.height = targetHeight;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.UNSET;
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        lp.horizontalBias = 0f;
        lp.verticalBias = 0f;
        lp.leftMargin = Math.max(0, (parentWidth - targetWidth) / 2);
        lp.topMargin = Math.max(0, (parentHeight - targetHeight) / 2);
        dpPanel.setLayoutParams(lp);
    }

    private void openDataProcessDialog() {
        if (mWebView != null) {
            openDataProcessDialogWithRangeNow();
        } else if (calcSelectedRange != null && !calcSelectedRange.isEmpty()) {
            openDataProcessDialogWithRange(calcSelectedRange);
            Log.i(TAG, "dp_open range=" + dpCellRange + " source=cached_fallback");
        } else {
            openDataProcessDialogWithRange("");
        }
    }

    private void openDataProcessDialogWithRangeNow() {
        if (mWebView == null) { openDataProcessDialogWithRange(""); return; }
        readCalcSelectionRangeAsync(range -> runOnUiThread(() -> {
            calcSelectedRange = range;
            openDataProcessDialogWithRange(range);
            Log.i(TAG, "dp_open range=" + range + " source=direct");
        }));
    }

    private void openDataProcessDialogWithRange(String cellRange) {
        dpCellRange = cellRange;
        setDataProcessDialogState(DP_STATE_INPUT);
        if (dpRangeHint != null) {
            if (cellRange != null && !cellRange.isEmpty()) {
                dpRangeHint.setText("已选中 " + cellRange);
                dpRangeHint.setVisibility(View.VISIBLE);
            } else {
                dpRangeHint.setVisibility(View.GONE);
            }
        }
        dpOverlay.setVisibility(View.VISIBLE);
        dpPanel.setVisibility(View.VISIBLE);
        dpPanel.post(this::positionDataProcessDialogCenter);
        Log.i(TAG, "calc_data_process_open range=" + cellRange);
    }

    private void onDataProcessGenerate() {
        String input = dpInput != null ? dpInput.getText().toString().trim() : "";
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入数据处理需求", Toast.LENGTH_SHORT).show();
            return;
        }
        // 如果 range 为空，提示用户但继续（让 AI 根据光标位置推断）
        if (dpCellRange == null || dpCellRange.isEmpty()) {
            Log.w(TAG, "calc_data_process_no_range input=" + input + " — 允许继续，但 AI 可能推断错误");
        }
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && dpInput != null && dpInput.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(dpInput.getWindowToken(), 0);
        }
        if (dpUserInputDisplay != null) {
            dpUserInputDisplay.setText(input);
            dpUserInputDisplay.setVisibility(View.VISIBLE);
        }
        setDataProcessDialogState(DP_STATE_GENERATING);
        startDataProcessRequest(input);
    }

    private void startDataProcessRequest(String userInput) {
        try {
            // Read selected cell data via native JNI getTextSelection (sub-thread safe)
            String cellData = "";
            try {
                cellData = getTextSelection("text/plain;charset=utf-8");
            } catch (Exception e) {
                Log.w(TAG, "calc_data_process_getTextSelection_failed", e);
            }
            if (cellData == null) cellData = "";
            // Mixed strategy: if >200 rows, trim to first 20
            String dataSample = cellData;
            int lineCount = cellData.isEmpty() ? 0 : cellData.split("\n").length;
            if (lineCount > 200) {
                String[] lines = cellData.split("\n");
                StringBuilder sb = new StringBuilder();
                sb.append("（选中区域共 ").append(lineCount).append(" 行，以下仅展示前 20 行样例）\n");
                for (int i = 0; i < Math.min(20, lines.length); i++) {
                    sb.append(lines[i]).append("\n");
                }
                dataSample = sb.toString();
            }

            String mode = dpIsAnalysisMode ? AiChatCoordinator.MODE_CALC_DATA_ANALYSIS
                    : AiChatCoordinator.MODE_CALC_DATA_PROCESS;

            org.json.JSONObject request = new org.json.JSONObject();
            String requestId = (dpIsAnalysisMode ? "da-" : "dp-") + java.util.UUID.randomUUID().toString();
            request.put("requestId", requestId);
            request.put("taskType", mode);
            request.put("selection", userInput);
            request.put("cellRange", dpCellRange == null ? "" : dpCellRange);
            request.put("cellData", dataSample == null ? "" : dataSample);
            request.put("modelMode", "base");
            request.put("history", new org.json.JSONArray());

            dpActiveRequestId = requestId;
            aiActiveRequestId = requestId;
            aiStreamingRequestId = requestId;
            aiRequestModeById.put(requestId, mode);
            aiTextByRequestId.put(requestId, new StringBuilder());
            // 分析模式：注册 dpContentText 到 aiStreamingViewByRequestId 以显示流式文本
            // 处理模式：不注册，因为返回 JSON 等待 onDataProcessDone 解析
            if (dpIsAnalysisMode && dpContentText != null) {
                aiStreamingViewByRequestId.put(requestId, dpContentText);
            }
            dpRequestIds.add(requestId);

            String logTag = dpIsAnalysisMode ? "calc_data_analysis_request" : "calc_data_process_request";
            Log.i(TAG, logTag + " requestId=" + requestId
                    + " input=" + userInput + " range=" + dpCellRange);
            startAiRequestSession(request, -1);
        } catch (org.json.JSONException e) {
            Log.e(TAG, "calc_data_process_request_failed", e);
            Toast.makeText(this, "请求构建失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void onDataProcessDone(String requestId, String fullText) {
        final String text = fullText == null ? "" : fullText;
        runOnUiThread(() -> {
            dpResultText = text;

            // 数据分析模式：直接显示文本，不解析 JSON
            if (dpIsAnalysisMode) {
                dpContentText.setText(text);
                dpPendingOperations = null;
                setDataProcessDialogState(DP_STATE_COMPLETED);
                Log.i(TAG, "calc_data_analysis_done requestId=" + requestId + " chars=" + text.length());
                return;
            }

            // 数据处理模式：解析 JSON 操作列表
            String json = text.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf('\n');
                int end = json.lastIndexOf("```");
                if (start > 0 && end > start) {
                    json = json.substring(start, end).trim();
                }
            }
            try {
                org.json.JSONObject result = new org.json.JSONObject(json);
                Log.i(TAG, "calc_data_process_ai_raw requestId=" + requestId + " json=" + json);
                // System prompt in buildDataProcessMessages() tells AI to use "actions" field
                org.json.JSONArray ops = result.optJSONArray("actions");
                if (ops != null && ops.length() > 0) {
                    dpPendingOperations = ops;
                    String desc = result.optString("description", "");
                    StringBuilder display = new StringBuilder();
                    if (!desc.isEmpty()) {
                        display.append("📋 ").append(desc).append("\n\n");
                    }
                    display.append("操作清单：\n");
                    for (int i = 0; i < ops.length(); i++) {
                        org.json.JSONObject op = ops.getJSONObject(i);
                        String type = op.optString("type", "");
                        String range = op.optString("range", "");
                        display.append("  ").append(i + 1).append(". [")
                               .append(getOperationTypeLabel(type)).append("] ");
                        if (!range.isEmpty()) display.append(range).append(" ");
                        if ("add_column".equals(type)) {
                            display.append("→ 追加列: ").append(op.optString("header", "新列"));
                        }
                        display.append("\n");
                    }
                    dpContentText.setText(display.toString());
                } else {
                    dpContentText.setText("⚠️ AI 无法为当前需求生成可执行的操作。\n\n请尝试用不同方式描述。\n\n---\n原始返回：\n" + text);
                    dpPendingOperations = null;
                }
            } catch (org.json.JSONException e) {
                dpContentText.setText("⚠️ 解析失败，请重试。\n\n---\n原始返回：\n" + text);
                dpPendingOperations = null;
            }
            setDataProcessDialogState(DP_STATE_COMPLETED);
            Log.i(TAG, "calc_data_process_done requestId=" + requestId + " ops=" + (dpPendingOperations != null ? dpPendingOperations.length() : 0));
        });
    }

    private String getOperationTypeLabel(String type) {
        switch (type) {
            case "uno": return "格式化";
            case "set_value": return "写值";
            case "set_formula": return "写公式";
            case "add_column": return "追加列";
            case "sort": return "排序";
            case "filter": return "筛选";
            case "clear_formatting": return "清除格式";
            case "delete_rows": return "删除行";
            case "delete_columns": return "删除列";
            case "insert_rows": return "插入行";
            case "insert_columns": return "插入列";
            case "format_number": return "数字格式";
            case "set_column_width": return "列宽";
            case "merge_cells": return "合并";
            case "bold": return "加粗";
            case "calculate": return "重算";
            default: return type;
        }
    }

    private void onDataProcessStop() {
        String rid = dpActiveRequestId;
        if (!rid.isEmpty()) {
            cancelAiRequest(rid);
        }
        StringBuilder partial = aiTextByRequestId.get(rid);
        String text = partial == null ? "" : partial.toString();
        if (text.isEmpty() && dpContentText != null) {
            text = dpContentText.getText().toString();
        }
        dpResultText = text;
        if (!rid.isEmpty()) {
            aiStreamingViewByRequestId.remove(rid);
        }
        setDataProcessDialogState(DP_STATE_COMPLETED);
        Log.i(TAG, "calc_data_process_stopped requestId=" + rid);
    }

    private void onDataProcessRegenerate() {
        if (!dpActiveRequestId.isEmpty()) {
            cancelAiRequest(dpActiveRequestId);
            aiStreamingViewByRequestId.remove(dpActiveRequestId);
        }
        dpPendingOperations = null;
        setDataProcessDialogState(DP_STATE_INPUT);
        if (dpUserInputDisplay != null) dpUserInputDisplay.setVisibility(View.GONE);
        Log.i(TAG, "calc_data_process_regenerate");
    }

    private void dismissDataProcessDialog() {
        if (!dpActiveRequestId.isEmpty()) {
            cancelAiRequest(dpActiveRequestId);
            aiStreamingViewByRequestId.remove(dpActiveRequestId);
            dpActiveRequestId = "";
        }
        dpPendingOperations = null;
        dpIsAnalysisMode = false;
        if (dpOverlay != null) dpOverlay.setVisibility(View.GONE);
        if (dpPanel != null) dpPanel.setVisibility(View.GONE);
        Log.i(TAG, "calc_data_process_dialog_dismiss");
    }

    // ==================== Calc AI数据分析（复用数据处理弹窗） ====================

    private void openDataAnalysisDialog() {
        // 复用数据处理弹窗，但设置为分析模式
        dpIsAnalysisMode = true;
        if (dpTitle != null) dpTitle.setText("AI数据分析");
        if (dpInput != null) dpInput.setHint("分析这批数据的趋势...");
        openDataProcessDialog();
    }

    // startDataProcessRequest 内部会用 dpIsAnalysisMode 判断，
    // 分析模式下 AI 返回纯文本而非 JSON 操作列表。
    // ========================================================================
    // Calc AI Chart Generation Dialog
    // ========================================================================

    private void setupChartDialog() {
        chartOverlay = findViewById(R.id.chart_overlay);
        chartPanel = findViewById(R.id.chart_dialog_panel);

        chartInput = chartPanel.findViewById(R.id.chart_input);
        chartGenerateBtn = chartPanel.findViewById(R.id.chart_generate_btn);
        chartLoadingStatus = chartPanel.findViewById(R.id.chart_loading_status);
        chartResultText = chartPanel.findViewById(R.id.chart_result_text);
        chartCompletedGroup = chartPanel.findViewById(R.id.chart_completed_group);
        chartRegenerateBtn = chartPanel.findViewById(R.id.chart_regenerate_btn);
        chartInsertBtn = chartPanel.findViewById(R.id.chart_insert_btn);
        chartExecutingStatus = chartPanel.findViewById(R.id.chart_executing_status);

        View closeBtn = chartPanel.findViewById(R.id.chart_close_button);
        closeBtn.setOnClickListener(v -> dismissChartDialog());

        chartGenerateBtn.setOnClickListener(v -> onChartGenerate());
        chartRegenerateBtn.setOnClickListener(v -> onChartRegenerate());
        chartInsertBtn.setOnClickListener(v -> onChartInsert());
    }

    private void setChartDialogState(int state) {
        chartInput.setVisibility(state == CHART_STATE_INPUT ? View.VISIBLE : View.GONE);
        chartGenerateBtn.setVisibility(state == CHART_STATE_INPUT ? View.VISIBLE : View.GONE);
        chartLoadingStatus.setVisibility(state == CHART_STATE_GENERATING ? View.VISIBLE : View.GONE);
        chartCompletedGroup.setVisibility(state == CHART_STATE_COMPLETED ? View.VISIBLE : View.GONE);
        chartExecutingStatus.setVisibility(state == CHART_STATE_EXECUTING ? View.VISIBLE : View.GONE);
    }

    private void positionChartDialogCenter() {
        if (chartPanel == null) return;
        View parent = (View) chartPanel.getParent();
        if (!(parent instanceof ConstraintLayout)) return;
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        if (parentWidth <= 0 || parentHeight <= 0) return;
        int targetWidth = parentWidth - dpToPx(48);
        int targetHeight = Math.max(dpToPx(400), (int) (parentHeight * 0.8));

        ConstraintLayout.LayoutParams lp =
                (ConstraintLayout.LayoutParams) chartPanel.getLayoutParams();
        lp.width = targetWidth;
        lp.height = targetHeight;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.UNSET;
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        lp.horizontalBias = 0f;
        lp.verticalBias = 0f;
        lp.leftMargin = Math.max(0, (parentWidth - targetWidth) / 2);
        lp.topMargin = Math.max(0, (parentHeight - targetHeight) / 2);
        chartPanel.setLayoutParams(lp);
    }

    private void openChartDialog() {
        if (calcSelectedRange != null && !calcSelectedRange.isEmpty()) {
            openChartDialogWithRange(calcSelectedRange);
            Log.i(TAG, "chart_open range=" + chartSelectedRange + " source=cached");
        } else if (mWebView != null) {
            openChartDialogWithRangeNow();
        } else {
            openChartDialogWithRange("");
        }
    }

    private void openChartDialogWithRangeNow() {
        if (mWebView == null) { openChartDialogWithRange(""); return; }
        mWebView.evaluateJavascript(
            "(function(){try{var d=app.map._docLayer;if(!d)return'';var s=d._cellSelectionArea,g=d.sheetGeometry;if(g&&s){var cx=g._columns,ry=g._rows;if(cx&&ry&&typeof cx.getIndexFromPos==='function'){var ci=cx.getIndexFromPos(s.pX1,'tiletwips'),ri=ry.getIndexFromPos(s.pY1,'tiletwips'),cj=cx.getIndexFromPos(s.pX2,'tiletwips'),rj=ry.getIndexFromPos(s.pY2,'tiletwips');function cl(c){var s='';while(c>=0){s=String.fromCharCode(65+(c%26))+s;c=Math.floor(c/26)-1;}return s;}return cl(ci)+(ri+1)+':'+cl(cj)+(rj+1);}}var a=app.calc&&app.calc.cellAddress;if(!a)return'';function cl(c){var s='';while(c>=0){s=String.fromCharCode(65+(c%26))+s;c=Math.floor(c/26)-1;}return s;}return cl(a.x)+(a.y+1);}catch(e){return'';}})()",
            new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    String range = "";
                    if (value != null && !value.equals("null") && value.length() > 2) {
                        try { Object p = new org.json.JSONTokener(value).nextValue();
                            if (p instanceof String) range = (String) p;
                        } catch (org.json.JSONException e) {}
                    }
                    calcSelectedRange = range;
                    final String r = range;
                    runOnUiThread(() -> openChartDialogWithRange(r));
                    Log.i(TAG, "chart_open range=" + r + " source=direct");
                }
            });
    }

    private void openChartDialogWithRange(String cellRange) {
        chartSelectedRange = cellRange;
        setChartDialogState(CHART_STATE_INPUT);
        TextView rangeHint = chartPanel.findViewById(R.id.chart_range_hint);
        if (rangeHint != null) {
            if (cellRange != null && !cellRange.isEmpty()) {
                rangeHint.setText("已选中 " + cellRange);
                rangeHint.setVisibility(View.VISIBLE);
            } else {
                rangeHint.setVisibility(View.GONE);
            }
        }
        chartOverlay.setVisibility(View.VISIBLE);
        chartPanel.setVisibility(View.VISIBLE);
        chartPanel.post(this::positionChartDialogCenter);
        Log.i(TAG, "calc_chart_open range=" + cellRange);
    }

    private void onChartGenerate() {
        String input = chartInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入图表需求", Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        setChartDialogState(CHART_STATE_GENERATING);
        startChartRequest(input);
    }

    private void startChartRequest(String input) {
        String requestId = "chart-" + java.util.UUID.randomUUID().toString();
        chartActiveRequestId = requestId;
        Log.i(TAG, "calc_chart_request_start id=" + requestId + " input=" + input + " range=" + chartSelectedRange);

        try {
            org.json.JSONObject request = new org.json.JSONObject();
            request.put("requestId", requestId);
            request.put("taskType", AiChatCoordinator.MODE_CALC_CHART);
            request.put("selection", input);
            request.put("cellRange", chartSelectedRange != null ? chartSelectedRange : "");
            // Read cell data for AI context
            if (mWebView != null) {
                mWebView.evaluateJavascript(
                    "(function(){ try { var r = app.calc && app.calc.cellAddress; if (!r) return ''; " +
                    "var sheet = 'Sheet1'; var row = r.y; var col = r.x; " +
                    "var data = ''; " +
                    "for (var i = 0; i < 20 && row + i < 10000; i++) { " +
                    "  var cell = app.calc.getCellText(sheet, row + i, col); " +
                    "  if (cell === '') break; " +
                    "  data += cell + '\\n'; " +
                    "} return JSON.stringify({sample: data, range: 'col ' + col + ' rows ' + row + '-' + (row + i - 1)}); " +
                    "} catch(e) { return ''; } })()",
                    dataResult -> {
                        try {
                            String cellData = "";
                            if (dataResult != null && !dataResult.equals("null") && dataResult.length() > 2) {
                                org.json.JSONObject dataJson = new org.json.JSONObject(dataResult);
                                cellData = dataJson.optString("sample", "");
                            }
                            request.put("cellData", cellData);
                        } catch (Exception e) {
                            Log.e(TAG, "calc_chart_cell_data_error", e);
                        }
                        startAiRequestSession(request, -1);
                    });
            } else {
                request.put("cellData", "");
                startAiRequestSession(request, -1);
            }
        } catch (org.json.JSONException e) {
            Log.e(TAG, "calc_chart_request_error", e);
            setChartDialogState(CHART_STATE_INPUT);
            Toast.makeText(this, "请求构建失败", Toast.LENGTH_SHORT).show();
        }
    }

    public void onChartDone(String requestId, String text) {
        Log.i(TAG, "calc_chart_done id=" + requestId + " textChars=" + (text != null ? text.length() : 0));
        if (!requestId.equals(chartActiveRequestId)) return;

        chartResultJson = text != null ? text : "";

        if (chartResultJson.startsWith("```")) {
            // Strip markdown code fences
            chartResultJson = chartResultJson.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        String displayText;
        // Try to parse and display nicely
        try {
            org.json.JSONObject result = new org.json.JSONObject(chartResultJson);
            StringBuilder display = new StringBuilder();
            display.append("分析结果：\n\n");

            // Show preprocessing info
            org.json.JSONArray preprocess = result.optJSONArray("preprocess");
            if (preprocess != null && preprocess.length() > 0) {
                display.append("数据预处理：\n");
                for (int i = 0; i < preprocess.length(); i++) {
                    org.json.JSONObject op = preprocess.getJSONObject(i);
                    display.append("  · ").append(op.optString("address", "")).append(": ").append(op.optString("value", "")).append("\n");
                }
                display.append("\n");
            }

            // Show chart info
            org.json.JSONObject chart = result.optJSONObject("chart");
            if (chart != null) {
                display.append("图表配置：\n");
                display.append("  · 数据范围：").append(chart.optString("dataRange", "")).append("\n");
                String chartType = chart.optString("chartType", "column");
                String chartTypeLabel = chartType;
                if ("pie".equals(chartType)) chartTypeLabel = "饼图";
                else if ("bar".equals(chartType)) chartTypeLabel = "条形图";
                else if ("column".equals(chartType)) chartTypeLabel = "柱状图";
                else if ("line".equals(chartType)) chartTypeLabel = "折线图";
                display.append("  · 图表类型：").append(chartTypeLabel).append("\n");
                display.append("  · 标题：").append(chart.optString("title", ""));
            }

            displayText = display.toString();
        } catch (org.json.JSONException e) {
            // If JSON parsing fails, show raw text
            displayText = chartResultJson;
        }

        final String finalDisplayText = displayText;
        runOnUiThread(() -> {
            chartResultText.setText(finalDisplayText);
            setChartDialogState(CHART_STATE_COMPLETED);
        });
    }

    private void onChartRegenerate() {
        if (!chartActiveRequestId.isEmpty()) {
            cancelAiRequest(chartActiveRequestId);
            aiStreamingViewByRequestId.remove(chartActiveRequestId);
            chartActiveRequestId = "";
        }
        chartResultJson = "";
        chartInput.setText("");
        setChartDialogState(CHART_STATE_INPUT);
    }

    private void onChartInsert() {
        setChartDialogState(CHART_STATE_EXECUTING);
        chartExecutingStatus.setText("正在插入图表...");
        final String requestId = chartActiveRequestId;

        new Thread(() -> {
            try {
                org.json.JSONObject result = new org.json.JSONObject(chartResultJson);

                // Step 1: Execute preprocessing operations
                org.json.JSONArray preprocess = result.optJSONArray("preprocess");
                if (preprocess != null && preprocess.length() > 0) {
                    for (int i = 0; i < preprocess.length(); i++) {
                        final int step = i;
                        org.json.JSONObject op = preprocess.getJSONObject(i);
                        String type = op.optString("type", "");
                        String address = op.optString("address", "");
                        String value = op.optString("value", "");

                        runOnUiThread(() -> {
                            if (chartExecutingStatus != null) {
                                chartExecutingStatus.setText("正在执行第 " + (step + 1) + "/" + preprocess.length() + " 步...");
                            }
                        });

                        if ("formula".equals(type) && !address.isEmpty() && !value.isEmpty()) {
                            // Set formula via GoToCell + paste（typed JSON 格式，参考 gtv-signal-handlers.cxx）
                            String cellRef = address.split("\\.")[1];
                            postUnoCommand(".uno:GoToCell",
                                "{\"ToPoint\":{\"type\":\"string\",\"value\":\"" + cellRef + "\"}}",
                                false);
                            Thread.sleep(100);
                            paste("text/plain;charset=utf-8", value.getBytes("UTF-8"));
                            Thread.sleep(100);
                        }
                    }
                }

                // Step 2: Insert chart
                runOnUiThread(() -> {
                    if (chartExecutingStatus != null) {
                        chartExecutingStatus.setText("正在插入图表...");
                    }
                });

                org.json.JSONObject chart = result.optJSONObject("chart");
                String dataRange = chart != null ? chart.optString("dataRange", "") : "";
                String chartType = chart != null ? chart.optString("chartType", "column") : "column";
                String templateService = CalcChartTypeMapper.needsCustomTemplate(chartType)
                        ? CalcChartTypeMapper.toTemplateService(chartType) : "";
                int curveStyle = CalcChartTypeMapper.toCurveStyle(chartType);
                String insertArgs = CalcChartTypeMapper.buildInsertChartJson(dataRange, templateService, curveStyle);

                postUnoCommand(".uno:InsertObjectChart", insertArgs, false);

                runOnUiThread(() -> {
                    Log.i(TAG, "calc_chart_insert_done id=" + requestId
                            + " type=" + chartType + " template=" + templateService);
                    dismissChartDialog();
                    Toast.makeText(LOActivity.this, "图表已生成", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "calc_chart_insert_error", e);
                runOnUiThread(() -> {
                    Toast.makeText(LOActivity.this, "图表插入失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    getMainHandler().postDelayed(() -> setChartDialogState(CHART_STATE_COMPLETED), 1500L);
                });
            }
        }, "chart-executor-" + requestId).start();
    }

    private void dismissChartDialog() {
        if (!chartActiveRequestId.isEmpty()) {
            cancelAiRequest(chartActiveRequestId);
            aiStreamingViewByRequestId.remove(chartActiveRequestId);
            chartActiveRequestId = "";
        }
        chartResultJson = "";
        if (chartOverlay != null) chartOverlay.setVisibility(View.GONE);
        if (chartPanel != null) chartPanel.setVisibility(View.GONE);
        Log.i(TAG, "calc_chart_dialog_dismiss");
    }

    // ========================================================================
    // Impress PPT Outline Generation Dialog
    // ========================================================================

    private void setupImpressOutlineDialog() {
        impressOutlineOverlay = findViewById(R.id.impress_outline_overlay);
        impressOutlinePanel = findViewById(R.id.impress_outline_panel);

        impressOutlineInputGroup = impressOutlinePanel.findViewById(R.id.impress_outline_input_group);
        impressOutlineQuickInput = impressOutlinePanel.findViewById(R.id.impress_outline_quick_input);
        impressOutlineDocGroup = impressOutlinePanel.findViewById(R.id.impress_outline_doc_group);
        impressOutlineDocSelectBtn = impressOutlinePanel.findViewById(R.id.impress_outline_doc_select_btn);
        impressOutlineDocFileName = impressOutlinePanel.findViewById(R.id.impress_outline_doc_file_name);
        impressOutlinePasteInput = impressOutlinePanel.findViewById(R.id.impress_outline_paste_input);

        impressOutlineTabQuick = impressOutlinePanel.findViewById(R.id.impress_outline_tab_quick);
        impressOutlineTabDoc = impressOutlinePanel.findViewById(R.id.impress_outline_tab_doc);
        impressOutlineTabPaste = impressOutlinePanel.findViewById(R.id.impress_outline_tab_paste);

        impressOutlinePageSpinner = impressOutlinePanel.findViewById(R.id.impress_outline_page_spinner);
        impressOutlineAudienceSpinner = impressOutlinePanel.findViewById(R.id.impress_outline_audience_spinner);
        impressOutlineStyleSpinner = impressOutlinePanel.findViewById(R.id.impress_outline_style_spinner);

        impressOutlineGenerateBtn = impressOutlinePanel.findViewById(R.id.impress_outline_generate_btn);
        impressOutlineLoadingText = impressOutlinePanel.findViewById(R.id.impress_outline_loading_text);
        impressOutlineCompletedGroup = impressOutlinePanel.findViewById(R.id.impress_outline_completed_group);
        impressOutlineCardContainer = impressOutlinePanel.findViewById(R.id.impress_outline_card_container);
        impressOutlineRegenerateBtn = impressOutlinePanel.findViewById(R.id.impress_outline_regenerate_btn);
        impressOutlineTemplateBtn = impressOutlinePanel.findViewById(R.id.impress_outline_template_btn);
        impressOutlineErrorGroup = impressOutlinePanel.findViewById(R.id.impress_outline_error_group);
        impressOutlineErrorText = impressOutlinePanel.findViewById(R.id.impress_outline_error_text);
        impressOutlineErrorRetryBtn = impressOutlinePanel.findViewById(R.id.impress_outline_error_retry_btn);
        impressOutlineGeneratingPptGroup = impressOutlinePanel.findViewById(R.id.impress_outline_generating_ppt_group);
        impressOutlineGeneratingPptText = impressOutlinePanel.findViewById(R.id.impress_outline_generating_ppt_text);
        impressOutlineGeneratingPptDetail = impressOutlinePanel.findViewById(R.id.impress_outline_generating_ppt_detail);
        impressOutlineGeneratingPptProgress = impressOutlinePanel.findViewById(R.id.impress_outline_generating_ppt_progress);
        impressOutlineTitle = impressOutlinePanel.findViewById(R.id.impress_outline_title);
        impressOutlineTemplateGroup = impressOutlinePanel.findViewById(R.id.impress_outline_template_group);
        impressOutlineTemplateBackBtn = impressOutlinePanel.findViewById(R.id.impress_outline_template_back_btn);
        View templateGrid = impressOutlinePanel.findViewById(R.id.template_grid_container);
        if (templateGrid instanceof LinearLayout) {
            impressTemplateGridContainer = (LinearLayout) templateGrid;
        }

        View closeBtn = impressOutlinePanel.findViewById(R.id.impress_outline_close_button);
        closeBtn.setOnClickListener(v -> dismissImpressOutlineDialog());

        impressOutlineTabQuick.setOnClickListener(v -> switchImpressOutlineInputType("quick"));
        impressOutlineTabDoc.setOnClickListener(v -> switchImpressOutlineInputType("document"));
        impressOutlineTabPaste.setOnClickListener(v -> switchImpressOutlineInputType("outline"));

        impressOutlineDocSelectBtn.setOnClickListener(v -> pickImpressOutlineDocument());

        impressOutlineGenerateBtn.setOnClickListener(v -> onImpressOutlineGenerate());
        impressOutlineRegenerateBtn.setOnClickListener(v -> onImpressOutlineGenerate());
        impressOutlineErrorRetryBtn.setOnClickListener(v -> onImpressOutlineGenerate());
        impressOutlineTemplateBtn.setOnClickListener(v -> openImpressTemplateSelectSheet());
        if (impressOutlineTemplateBackBtn != null) {
            impressOutlineTemplateBackBtn.setOnClickListener(v -> setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_COMPLETED));
        }

        loadImpressTemplateIndex();

        ArrayAdapter<CharSequence> pageAdapter = ArrayAdapter.createFromResource(this,
                R.array.impress_outline_page_options, android.R.layout.simple_spinner_item);
        pageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        impressOutlinePageSpinner.setAdapter(pageAdapter);

        ArrayAdapter<CharSequence> audienceAdapter = ArrayAdapter.createFromResource(this,
                R.array.impress_outline_audience_options, android.R.layout.simple_spinner_item);
        audienceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        impressOutlineAudienceSpinner.setAdapter(audienceAdapter);

        ArrayAdapter<CharSequence> styleAdapter = ArrayAdapter.createFromResource(this,
                R.array.impress_outline_style_options, android.R.layout.simple_spinner_item);
        styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        impressOutlineStyleSpinner.setAdapter(styleAdapter);
    }

    private void setImpressOutlineDialogState(int state) {
        impressOutlineInputGroup.setVisibility(state == IMPRESS_OUTLINE_STATE_INPUT ? View.VISIBLE : View.GONE);
        impressOutlineLoadingText.setVisibility(state == IMPRESS_OUTLINE_STATE_GENERATING ? View.VISIBLE : View.GONE);
        impressOutlineCompletedGroup.setVisibility(state == IMPRESS_OUTLINE_STATE_COMPLETED ? View.VISIBLE : View.GONE);
        impressOutlineErrorGroup.setVisibility(state == IMPRESS_OUTLINE_STATE_ERROR ? View.VISIBLE : View.GONE);
        impressOutlineGeneratingPptGroup.setVisibility(
                state == IMPRESS_OUTLINE_STATE_GENERATING_PPT ? View.VISIBLE : View.GONE);
        if (impressOutlineTemplateGroup != null) {
            impressOutlineTemplateGroup.setVisibility(
                    state == IMPRESS_OUTLINE_STATE_TEMPLATE_SELECT ? View.VISIBLE : View.GONE);
        }
        if (impressOutlineTitle != null) {
            if (state == IMPRESS_OUTLINE_STATE_TEMPLATE_SELECT) {
                impressOutlineTitle.setText(R.string.impress_template_select_title);
            } else if (state == IMPRESS_OUTLINE_STATE_GENERATING_PPT) {
                impressOutlineTitle.setText(R.string.impress_generating_ppt);
            } else {
                impressOutlineTitle.setText("AI生成PPT");
            }
        }
    }

    private void positionImpressOutlineDialogCenter() {
        if (impressOutlinePanel == null) return;
        View parent = (View) impressOutlinePanel.getParent();
        if (!(parent instanceof ConstraintLayout)) return;
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        if (parentWidth <= 0 || parentHeight <= 0) return;
        int targetWidth = parentWidth - dpToPx(48);
        int targetHeight = Math.max(dpToPx(400), (int) (parentHeight * 0.8));

        ConstraintLayout.LayoutParams lp =
                (ConstraintLayout.LayoutParams) impressOutlinePanel.getLayoutParams();
        lp.width = targetWidth;
        lp.height = targetHeight;
        lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        lp.endToEnd = ConstraintLayout.LayoutParams.UNSET;
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET;
        lp.horizontalBias = 0f;
        lp.verticalBias = 0f;
        lp.leftMargin = Math.max(0, (parentWidth - targetWidth) / 2);
        lp.topMargin = Math.max(0, (parentHeight - targetHeight) / 2);
        impressOutlinePanel.setLayoutParams(lp);
    }

    private void switchImpressOutlineInputType(String type) {
        impressOutlineInputType = type;
        impressOutlineTabQuick.setBackground(null);
        impressOutlineTabDoc.setBackground(null);
        impressOutlineTabPaste.setBackground(null);
        impressOutlineTabQuick.setTextColor(0xFF666666);
        impressOutlineTabDoc.setTextColor(0xFF666666);
        impressOutlineTabPaste.setTextColor(0xFF666666);

        impressOutlineQuickInput.setVisibility(View.GONE);
        impressOutlineDocGroup.setVisibility(View.GONE);
        impressOutlinePasteInput.setVisibility(View.GONE);

        switch (type) {
            case "quick":
                impressOutlineTabQuick.setBackgroundResource(R.drawable.lolib_bg_continue_pill_primary);
                impressOutlineTabQuick.setTextColor(0xFFFFFFFF);
                impressOutlineQuickInput.setVisibility(View.VISIBLE);
                break;
            case "document":
                impressOutlineTabDoc.setBackgroundResource(R.drawable.lolib_bg_continue_pill_primary);
                impressOutlineTabDoc.setTextColor(0xFFFFFFFF);
                impressOutlineDocGroup.setVisibility(View.VISIBLE);
                break;
            case "outline":
                impressOutlineTabPaste.setBackgroundResource(R.drawable.lolib_bg_continue_pill_primary);
                impressOutlineTabPaste.setTextColor(0xFFFFFFFF);
                impressOutlinePasteInput.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void openImpressOutlineDialog() {
        impressOutlineInputType = "quick";
        impressOutlineDocFileContent = "";
        impressOutlineDocFileName.setVisibility(View.GONE);
        switchImpressOutlineInputType("quick");
        setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_INPUT);
        impressOutlineOverlay.setVisibility(View.VISIBLE);
        impressOutlinePanel.setVisibility(View.VISIBLE);
        impressOutlinePanel.post(this::positionImpressOutlineDialogCenter);
        Log.i(TAG, "impress_outline_open");
    }

    private void dismissImpressOutlineDialog() {
        if (!impressOutlineActiveRequestId.isEmpty()) {
            cancelAiRequest(impressOutlineActiveRequestId);
            aiStreamingViewByRequestId.remove(impressOutlineActiveRequestId);
            impressOutlineActiveRequestId = "";
        }
        if (!generateActiveRequestId.isEmpty()) {
            cancelAiRequest(generateActiveRequestId);
            aiStreamingViewByRequestId.remove(generateActiveRequestId);
            generateActiveRequestId = "";
        }
        generateAccumulatedByOutlineIndex.clear();
        pendingGeneratedPptxFile = null;
        if (impressOutlineOverlay != null) impressOutlineOverlay.setVisibility(View.GONE);
        if (impressOutlinePanel != null) impressOutlinePanel.setVisibility(View.GONE);
        Log.i(TAG, "impress_outline_dismiss");
    }

    private void onImpressOutlineGenerate() {
        String userInput = "";
        if ("quick".equals(impressOutlineInputType)) {
            userInput = impressOutlineQuickInput.getText().toString().trim();
        } else if ("document".equals(impressOutlineInputType)) {
            userInput = impressOutlineDocFileContent;
        } else {
            userInput = impressOutlinePasteInput.getText().toString().trim();
        }

        if (userInput.isEmpty()) {
            Toast.makeText(this,
                    "document".equals(impressOutlineInputType) ? "请先选择文件" : "请输入内容",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        hideKeyboard();
        setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_GENERATING);
        startImpressOutlineRequest(userInput);
    }

    private void startImpressOutlineRequest(String userInput) {
        String requestId = "impress-outline-" + java.util.UUID.randomUUID().toString();
        impressOutlineActiveRequestId = requestId;

        String pageItem = (String) impressOutlinePageSpinner.getSelectedItem();
        int pageRange = 10;
        if (pageItem != null) {
            if (pageItem.startsWith("11")) pageRange = 20;
            else if (pageItem.startsWith("21")) pageRange = 30;
        }
        String audience = impressOutlineAudienceSpinner.getSelectedItem() != null
                ? (String) impressOutlineAudienceSpinner.getSelectedItem() : "大众";
        String style = impressOutlineStyleSpinner.getSelectedItem() != null
                ? (String) impressOutlineStyleSpinner.getSelectedItem() : "通用";

        try {
            JSONObject request = new JSONObject();
            request.put("requestId", requestId);
            request.put("taskType", AiChatCoordinator.MODE_IMPRESS_OUTLINE);
            request.put("inputType", impressOutlineInputType);
            request.put("userInput", userInput);
            request.put("pageRange", pageRange);
            request.put("audience", audience);
            request.put("style", style);
            startAiRequestSession(request, -1);
        } catch (JSONException e) {
            setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_INPUT);
            Toast.makeText(this, "请求构建失败", Toast.LENGTH_SHORT).show();
        }
    }

    public void onImpressOutlineDone(String requestId, String text) {
        if (!requestId.equals(impressOutlineActiveRequestId)) return;
        if (text == null || text.isEmpty()) {
            runOnUiThread(() -> {
                impressOutlineErrorText.setText("AI 返回为空，请重试");
                setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_ERROR);
            });
            return;
        }
        runOnUiThread(() -> parseAndShowImpressOutline(text));
    }

    private void parseAndShowImpressOutline(String jsonText) {
        String cleanJson = jsonText;
        if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        try {
            JSONObject result = new JSONObject(cleanJson);
            JSONArray slides = result.optJSONArray("slides");
            if (slides == null || slides.length() == 0) {
                throw new JSONException("slides array is empty");
            }

            // Save slides JSON for template generation
            impressOutlineSlidesJson = slides;

            impressOutlineCardContainer.removeAllViews();
            for (int i = 0; i < slides.length(); i++) {
                JSONObject slide = slides.getJSONObject(i);
                View card = getLayoutInflater().inflate(
                        R.layout.lolib_item_impress_outline_card, impressOutlineCardContainer, false);

                TextView pageLabel = card.findViewById(R.id.impress_outline_card_page_label);
                EditText titleInput = card.findViewById(R.id.impress_outline_card_title);
                EditText contentInput = card.findViewById(R.id.impress_outline_card_content);

                String type = slide.optString("type", "section");
                String typeLabel;
                switch (type) {
                    case "cover": typeLabel = "封面"; break;
                    case "toc":   typeLabel = "目录"; break;
                    case "section_divider": typeLabel = "章节页"; break;
                    case "end":   typeLabel = "结尾"; break;
                    default:      typeLabel = "章节";
                }
                pageLabel.setText("P" + slide.optInt("page", i + 1) + "  " + typeLabel);
                titleInput.setText(slide.optString("title", ""));
                contentInput.setText(slide.optString("content", ""));

                if (i < slides.length() - 1) {
                    View divider = new View(this);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(0xFFF0F0F0);
                    ((LinearLayout) card).addView(divider);
                }

                impressOutlineCardContainer.addView(card);
            }
            setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_COMPLETED);
        } catch (JSONException e) {
            impressOutlineErrorText.setText("大纲解析失败，AI返回原始文本：\n\n" + cleanJson);
            setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_ERROR);
        }
    }

    private void pickImpressOutlineDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"text/plain", "application/pdf"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, REQUEST_CODE_IMPRESS_PICK_DOC);
    }

    private void handleImpressOutlineDocPicked(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        String fileName = uri.getLastPathSegment();
        if (fileName != null && fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        impressOutlineDocFileName.setText(fileName != null ? fileName : "未知文件");
        impressOutlineDocFileName.setVisibility(View.VISIBLE);

        try {
            if ("text/plain".equals(mimeType)) {
                try (InputStream is = getContentResolver().openInputStream(uri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    int totalChars = 0;
                    while ((line = reader.readLine()) != null && totalChars < 150000) {
                        sb.append(line).append("\n");
                        totalChars += line.length() + 1;
                    }
                    impressOutlineDocFileContent = sb.toString();
                    Toast.makeText(this, "文本已加载（" + sb.length() + "字符）", Toast.LENGTH_SHORT).show();
                }
            } else if ("application/pdf".equals(mimeType)) {
                extractPdfText(uri);
            } else {
                Toast.makeText(this, "不支持的文件类型，请使用TXT或PDF", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "impress_outline_doc_read_error", e);
            Toast.makeText(this, "文件读取失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void extractPdfText(Uri uri) {
        // TODO: PDF text extraction - requires a PDF parsing library
        Toast.makeText(this, "PDF文本提取暂不支持，请先转为TXT格式", Toast.LENGTH_LONG).show();
    }

    // ========================================================================
    // Impress PPT Template Selection + Generation
    // ========================================================================

    private void loadImpressTemplateIndex() {
        try {
            templateIndex = TemplateIndex.load(this);
            Log.i(TAG, "template_index_loaded count="
                    + (templateIndex != null ? templateIndex.getAllTemplates().size() : 0));
        } catch (Exception e) {
            Log.e(TAG, "template_index_load_failed", e);
            templateIndex = null;
        }
    }

    private void openImpressTemplateSelectSheet() {
        if (templateIndex == null) {
            loadImpressTemplateIndex();
        }
        if (templateIndex == null) {
            Toast.makeText(this, "模板加载失败", Toast.LENGTH_SHORT).show();
            return;
        }

        List<org.libreoffice.androidlib.template.TemplateIndex.Template> templates
                = templateIndex.getAllTemplates();
        if (templates == null || templates.isEmpty()) {
            Toast.makeText(this, "暂无可用模板", Toast.LENGTH_SHORT).show();
            return;
        }
        if (impressTemplateGridContainer == null) {
            Toast.makeText(this, "模板面板未就绪", Toast.LENGTH_SHORT).show();
            return;
        }

        impressTemplateGridContainer.removeAllViews();
        selectedTemplateId = "";

        for (int i = 0; i < templates.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            addTemplateCard(row, templates.get(i));
            if (i + 1 < templates.size()) {
                addTemplateCard(row, templates.get(i + 1));
            }
            impressTemplateGridContainer.addView(row);
        }

        setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_TEMPLATE_SELECT);
        Log.i(TAG, "impress_template_select_open templates=" + templates.size());
    }

    private void addTemplateCard(LinearLayout row,
                                 org.libreoffice.androidlib.template.TemplateIndex.Template template) {
        View card = getLayoutInflater().inflate(
                R.layout.lolib_item_impress_template_card, row, false);
        if (card == null) return;

        ImageView coverImage = card.findViewById(R.id.template_cover_image);
        TextView nameText = card.findViewById(R.id.template_name);

        nameText.setText(template.name);

        // Load cover image from assets
        String coverPath = "templates/impress/" + template.coverImage;
        try {
            InputStream is = getAssets().open(coverPath);
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
            is.close();
            if (bmp != null) {
                coverImage.setImageBitmap(bmp);
            }
        } catch (IOException e) {
            Log.w(TAG, "template_cover_load_failed path=" + coverPath, e);
        }

        // Card selection highlight
        card.setOnClickListener(v -> {
            // Deselect all cards
            deselectAllTemplateCards();
            // Select this card
            card.setBackgroundResource(R.drawable.lolib_bg_gradient_button_outline);
            selectedTemplateId = template.id;
            Log.i(TAG, "template_selected id=" + template.id + " name=" + template.name);
            // Auto-proceed after selection
            onTemplateSelected();
        });

        row.addView(card);
    }

    private void deselectAllTemplateCards() {
        if (impressTemplateGridContainer == null) return;
        for (int r = 0; r < impressTemplateGridContainer.getChildCount(); r++) {
            View row = impressTemplateGridContainer.getChildAt(r);
            if (row instanceof LinearLayout) {
                for (int c = 0; c < ((LinearLayout) row).getChildCount(); c++) {
                    View card = ((LinearLayout) row).getChildAt(c);
                    card.setBackgroundResource(R.drawable.lolib_bg_typeset_card);
                }
            }
        }
    }

    private void onTemplateSelected() {
        if (selectedTemplateId.isEmpty()) return;

        if (impressOutlineSlidesJson == null || impressOutlineSlidesJson.length() == 0) {
            impressOutlineSlidesJson = collectOutlineCardsJson();
        }

        if (impressOutlineSlidesJson == null || impressOutlineSlidesJson.length() == 0) {
            Toast.makeText(this, "没有可生成的大纲", Toast.LENGTH_SHORT).show();
            return;
        }

        startPptGeneration();
    }

    private JSONArray collectOutlineCardsJson() {
        JSONArray slides = new JSONArray();
        LinearLayout container = impressOutlineCardContainer;
        if (container == null) return slides;

        try {
            for (int i = 0; i < container.getChildCount(); i++) {
                View card = container.getChildAt(i);
                if (card == null) continue;

                TextView pageLabel = card.findViewById(R.id.impress_outline_card_page_label);
                EditText titleInput = card.findViewById(R.id.impress_outline_card_title);
                EditText contentInput = card.findViewById(R.id.impress_outline_card_content);

                if (titleInput == null) continue;

                JSONObject slide = new JSONObject();
                slide.put("page", i + 1);
                slide.put("title", titleInput.getText() != null ? titleInput.getText().toString() : "");
                slide.put("content", contentInput != null && contentInput.getText() != null
                        ? contentInput.getText().toString() : "");

                // Determine type from page label
                String label = pageLabel != null && pageLabel.getText() != null
                        ? pageLabel.getText().toString() : "";
                if (label.contains("封面")) slide.put("type", "cover");
                else if (label.contains("目录")) slide.put("type", "toc");
                else if (label.contains("章节页")) slide.put("type", "section_divider");
                else if (label.contains("结尾")) slide.put("type", "end");
                else slide.put("type", "section");

                slides.put(slide);
            }
        } catch (JSONException e) {
            Log.e(TAG, "collect_outline_cards_json_error", e);
        }
        return slides;
    }

    private void startPptGeneration() {
        if (impressOutlineSlidesJson == null || impressOutlineSlidesJson.length() == 0) {
            Toast.makeText(this, "没有可生成的大纲", Toast.LENGTH_SHORT).show();
            return;
        }

        // Calculate batches:
        //   batch 0: cover (slide 0)
        //   batch 1: toc (slide 1, if present)
        //   batch 2..N-2: each section (section slides)
        //   batch N-1: end (last slide)
        int slideCount = impressOutlineSlidesJson.length();
        generateTotalBatches = slideCount;
        generateCurrentBatch = 0;
        generateBatchAttempt = 0;
        generateFailedBatchCount = 0;
        generateAccumulatedByOutlineIndex.clear();
        pendingGeneratedPptxFile = null;

        setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_GENERATING_PPT);
        updatePptGenerationProgress(0, generateTotalBatches, "");

        Log.i(TAG, "ppt_generation_start slides=" + slideCount
                + " batches=" + generateTotalBatches
                + " template=" + selectedTemplateId);

        startPptGenerationBatch(0);
    }

    private void updatePptGenerationProgress(int batchIndex, int totalBatches, String slideTitle) {
        if (impressOutlineGeneratingPptText != null) {
            String title = slideTitle != null ? slideTitle.trim() : "";
            if (!title.isEmpty()) {
                impressOutlineGeneratingPptText.setText(
                        getString(R.string.impress_generate_progress_title,
                                batchIndex + 1, totalBatches, title));
            } else {
                impressOutlineGeneratingPptText.setText(
                        getString(R.string.impress_generate_progress, batchIndex + 1, totalBatches));
            }
        }
        if (impressOutlineGeneratingPptProgress != null && totalBatches > 0) {
            impressOutlineGeneratingPptProgress.setIndeterminate(false);
            int progress = Math.min(100, (batchIndex * 100) / totalBatches);
            impressOutlineGeneratingPptProgress.setProgress(progress);
        }
    }

    private void startPptGenerationBatch(int batchIndex) {
        if (batchIndex >= generateTotalBatches) {
            onPptGenerationComplete();
            return;
        }

        String requestId = "impress-generate-" + java.util.UUID.randomUUID().toString();
        generateActiveRequestId = requestId;
        generateCurrentBatch = batchIndex;

        String slideTitle = "";
        try {
            JSONObject slide = impressOutlineSlidesJson.getJSONObject(batchIndex);
            slideTitle = slide.optString("title", "");
            updatePptGenerationProgress(batchIndex, generateTotalBatches, slideTitle);
            if (impressOutlineGeneratingPptDetail != null) {
                impressOutlineGeneratingPptDetail.setText(R.string.impress_generating_ppt_detail);
            }

            String slideType = slide.optString("type", "section");
            if ("section_divider".equals(slideType)) {
                accumulateSectionDividerFromOutline(batchIndex);
                Log.i(TAG, "ppt_generation_batch_skip_ai type=section_divider batch="
                        + (batchIndex + 1) + "/" + generateTotalBatches);
                advanceToNextBatch();
                return;
            }

            JSONArray batchSlides = new JSONArray();
            batchSlides.put(slide);

            JSONObject request = new JSONObject();
            request.put("requestId", requestId);
            request.put("taskType", AiChatCoordinator.MODE_IMPRESS_GENERATE);
            request.put("templateId", selectedTemplateId);
            request.put("batchIndex", batchIndex);
            request.put("totalBatches", generateTotalBatches);
            request.put("batchSlides", batchSlides);

            // Also include full outline slides for AI context
            request.put("outlineSlides", impressOutlineSlidesJson);

            startAiRequestSession(request, -1);
            Log.i(TAG, "ppt_generation_batch_start requestId=" + requestId
                    + " batch=" + (batchIndex + 1) + "/" + generateTotalBatches
                    + " slide=" + slide.optString("type", "") + " " + slide.optString("title", ""));
        } catch (JSONException e) {
            Log.e(TAG, "ppt_generation_batch_error", e);
            Toast.makeText(this, "批次请求构建失败", Toast.LENGTH_SHORT).show();
            onPptGenerationComplete();
        }
    }

    public void onImpressGenerateDone(String requestId, String text) {
        if (!requestId.equals(generateActiveRequestId)) return;

        if (text == null || text.isEmpty()) {
            StringBuilder cached = aiTextByRequestId.get(requestId);
            if (cached != null && cached.length() > 0) {
                text = cached.toString();
                Log.i(TAG, "ppt_generate_recovered_from_cache requestId=" + requestId
                        + " chars=" + text.length());
            }
        }

        if (text == null || text.isEmpty()) {
            Log.w(TAG, "ppt_generate_done_empty requestId=" + requestId
                    + " batch=" + (generateCurrentBatch + 1) + "/" + generateTotalBatches);
            runOnUiThread(() -> handlePptGenerateBatchFailure("empty_response"));
            return;
        }

        Log.i(TAG, "ppt_generate_done requestId=" + requestId
                + " batch=" + (generateCurrentBatch + 1) + "/" + generateTotalBatches
                + " responseChars=" + text.length());

        try {
            String cleanJson = text.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("(?s)^```(?:json)?\\s*", "")
                        .replaceAll("\\s*```$", "");
            }
            cleanJson = sanitizeImpressGenerateJson(cleanJson);
            JSONObject aiResult = new JSONObject(cleanJson);
            JSONArray aiSlides = aiResult.optJSONArray("slides");

            if (aiSlides == null || aiSlides.length() == 0) {
                Log.w(TAG, "ppt_generate_no_slides_in_response batch=" + (generateCurrentBatch + 1));
                runOnUiThread(() -> handlePptGenerateBatchFailure("no_slides"));
                return;
            }

            if (aiSlides.length() > 1) {
                Log.w(TAG, "ppt_generate_multi_slides_truncated count=" + aiSlides.length()
                        + " batch=" + (generateCurrentBatch + 1));
            }

            accumulateGeneratedSlide(aiSlides.getJSONObject(0));
            runOnUiThread(this::advanceToNextBatch);
        } catch (JSONException e) {
            Log.e(TAG, "ppt_generate_parse_error reason=" + e.getMessage()
                    + " batch=" + (generateCurrentBatch + 1) + "/" + generateTotalBatches);
            runOnUiThread(() -> handlePptGenerateBatchFailure("parse_error"));
        }
    }

    /**
     * 单批 AI 失败时不中断整体生成：用大纲原始内容兜底，继续下一批。
     */
    private void handlePptGenerateBatchFailure(String reason) {
        if (generateCurrentBatch < 0 || generateCurrentBatch >= generateTotalBatches) {
            advanceToNextBatch();
            return;
        }
        try {
            accumulateOutlineSlideFallback(generateCurrentBatch);
            Log.w(TAG, "ppt_generate_batch_fallback reason=" + reason
                    + " batch=" + (generateCurrentBatch + 1) + "/" + generateTotalBatches);
        } catch (JSONException e) {
            Log.e(TAG, "ppt_generate_fallback_error batch=" + (generateCurrentBatch + 1), e);
        }
        // 保持生成中状态，不要退回大纲完成页
        setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_GENERATING_PPT);
        advanceToNextBatch();
    }

    private void accumulateOutlineSlideFallback(int batchIndex) throws JSONException {
        if (impressOutlineSlidesJson == null || batchIndex >= impressOutlineSlidesJson.length()) {
            throw new JSONException("outline slide missing at index " + batchIndex);
        }
        JSONObject outlineSlide = impressOutlineSlidesJson.getJSONObject(batchIndex);
        if ("section_divider".equals(outlineSlide.optString("type", ""))) {
            accumulateSectionDividerFromOutline(batchIndex);
            return;
        }
        JSONObject fallback = new JSONObject();
        fallback.put("page", batchIndex + 1);
        fallback.put("type", outlineSlide.optString("type", "section"));
        fallback.put("title", outlineSlide.optString("title", ""));
        fallback.put("subtitle", "");

        String content = outlineSlide.optString("content", "");
        fallback.put("content", content);

        JSONArray points = new JSONArray();
        JSONArray detailed = new JSONArray();
        if (!content.isEmpty()) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                points.put(trimmed);
                detailed.put("");
            }
        }
        fallback.put("content_points", points);
        fallback.put("detailed_content", detailed);

        accumulateGeneratedSlide(fallback);
        Log.i(TAG, "ppt_slide_outline_fallback index=" + (batchIndex + 1)
                + " title=" + outlineSlide.optString("title", ""));
    }

    /**
     * 修复 AI 常见非法 JSON，如 {@code "subtitle":,}。
     */
    private static String sanitizeImpressGenerateJson(String json) {
        if (json == null || json.isEmpty()) return json;
        return json.replaceAll("\"\\s*:\\s*,", "\":\"\",")
                .replaceAll("\"\\s*:\\s*\\}", "\":\"\"}")
                .replaceAll("\"\\s*:\\s*\n\\s*,", "\":\"\",");
    }

    private void accumulateSectionDividerFromOutline(int batchIndex) throws JSONException {
        if (impressOutlineSlidesJson == null || batchIndex >= impressOutlineSlidesJson.length()) {
            throw new JSONException("outline slide missing at index " + batchIndex);
        }
        JSONObject outlineSlide = impressOutlineSlidesJson.getJSONObject(batchIndex);
        JSONObject divider = new JSONObject();
        divider.put("page", batchIndex + 1);
        divider.put("type", "section_divider");
        divider.put("title", outlineSlide.optString("title", ""));
        divider.put("content", outlineSlide.optString("content", ""));
        generateAccumulatedByOutlineIndex.put(batchIndex, divider);
        Log.i(TAG, "ppt_slide_divider_accumulated index=" + (batchIndex + 1)
                + " title=" + outlineSlide.optString("title", ""));
    }

    private void accumulateGeneratedSlide(JSONObject slide) throws JSONException {
        generateAccumulatedByOutlineIndex.put(generateCurrentBatch, slide);
        Log.i(TAG, "ppt_slide_accumulated index=" + (generateCurrentBatch + 1)
                + " type=" + slide.optString("type", "section")
                + " title=" + slide.optString("title", ""));
    }

    private void fillAndOpenGeneratedPpt() {
        if (templateIndex == null || selectedTemplateId.isEmpty()) {
            Log.e(TAG, "ppt_template_fill_error reason=no_template");
            runOnUiThread(() -> {
                Toast.makeText(this, "未选择模板", Toast.LENGTH_SHORT).show();
                setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_COMPLETED);
            });
            return;
        }
        if (generateAccumulatedByOutlineIndex.isEmpty()) {
            Log.e(TAG, "ppt_template_fill_error reason=no_slide_content");
            runOnUiThread(() -> {
                Toast.makeText(this, "没有可写入的幻灯片内容", Toast.LENGTH_SHORT).show();
                setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_COMPLETED);
            });
            return;
        }

        org.libreoffice.androidlib.template.TemplateIndex.Template tmpl =
                templateIndex.findById(selectedTemplateId);
        if (tmpl == null) {
            Log.e(TAG, "ppt_template_fill_error reason=template_not_found id=" + selectedTemplateId);
            runOnUiThread(() -> {
                Toast.makeText(this, "模板不存在", Toast.LENGTH_SHORT).show();
                setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_COMPLETED);
            });
            return;
        }

        runOnUiThread(() -> {
            if (impressOutlineGeneratingPptDetail != null) {
                impressOutlineGeneratingPptDetail.setText(R.string.impress_ppt_filling);
            }
            if (impressOutlineGeneratingPptProgress != null) {
                impressOutlineGeneratingPptProgress.setProgress(100);
            }
        });

        final String assetPath = "templates/impress/" + tmpl.file;
        final String coverTitle = extractImpressCoverTitle();
        new Thread(() -> {
            try {
                org.libreoffice.androidlib.template.TemplateSlideCatalog catalog =
                        org.libreoffice.androidlib.template.TemplateSlideCatalog.load(
                                LOActivity.this, assetPath);
                java.util.List<org.libreoffice.androidlib.template.ImpressSlidePlanner.PlannedSlide> plan =
                        org.libreoffice.androidlib.template.ImpressSlidePlanner.build(
                                catalog, templateIndex, selectedTemplateId,
                                impressOutlineSlidesJson, generateAccumulatedByOutlineIndex);
                if (plan.isEmpty()) {
                    throw new IOException("slide plan is empty");
                }
                File outputFile = buildImpressOutputFile(coverTitle, tmpl.name);
                org.libreoffice.androidlib.template.PptxTemplateFiller.fillAndAssemble(
                        LOActivity.this, assetPath, plan, outputFile,
                        catalog.getOriginalSlideCount());
                Log.i(TAG, "ppt_template_filled path=" + outputFile.getAbsolutePath()
                        + " slides=" + plan.size());
                runOnUiThread(() -> showImpressGenerationSuccessDialog(outputFile));
            } catch (Exception e) {
                Log.e(TAG, "ppt_template_fill_error reason=" + e.getMessage(), e);
                runOnUiThread(() -> showImpressGenerationErrorDialog(e));
            }
        }, "cool-ai-ppt-fill").start();
    }

    private String extractImpressCoverTitle() {
        try {
            if (impressOutlineSlidesJson != null && impressOutlineSlidesJson.length() > 0) {
                JSONObject first = impressOutlineSlidesJson.getJSONObject(0);
                String title = first.optString("title", "");
                if (!title.isEmpty()) return title;
            }
            JSONObject generated = generateAccumulatedByOutlineIndex.get(0);
            if (generated != null) {
                return generated.optString("title", "AI生成PPT");
            }
        } catch (JSONException ignored) {
        }
        return "AI生成PPT";
    }

    private File buildImpressOutputFile(String coverTitle, String templateName) throws IOException {
        String safeTitle = sanitizeImpressFileName(coverTitle);
        if (safeTitle.isEmpty()) safeTitle = "AI生成PPT";
        String safeTemplate = sanitizeImpressFileName(templateName);
        if (safeTemplate.isEmpty()) safeTemplate = "模板";
        String baseName = safeTitle + "_" + safeTemplate;
        File dir = getCacheDir();
        File candidate = new File(dir, baseName + ".pptx");
        int suffix = 2;
        while (candidate.exists()) {
            candidate = new File(dir, baseName + "_" + suffix + ".pptx");
            suffix++;
        }
        return candidate;
    }

    private static String sanitizeImpressFileName(String name) {
        if (name == null) return "";
        String cleaned = name.trim()
                .replace("：", "-")
                .replace("？", "")
                .replace("?", "")
                .replace(":", "-")
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .replaceAll("\\s+", " ");
        if (cleaned.length() > 40) {
            cleaned = cleaned.substring(0, 40);
        }
        return cleaned;
    }

    private void showImpressGenerationSuccessDialog(File pptxFile) {
        if (pptxFile == null || !pptxFile.exists()) {
            Toast.makeText(this, "生成的 PPT 文件不存在", Toast.LENGTH_SHORT).show();
            setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_COMPLETED);
            return;
        }
        pendingGeneratedPptxFile = pptxFile;

        if (impressGenerationSuccessDialog != null && impressGenerationSuccessDialog.isShowing()) {
            impressGenerationSuccessDialog.dismiss();
        }

        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_native_confirm, null, false);
        TextView titleView = root.findViewById(R.id.ai_dialog_header_title);
        TextView messageView = root.findViewById(R.id.native_confirm_message);
        LinearLayout buttonRow = root.findViewById(R.id.native_confirm_button_row);
        if (titleView != null) {
            titleView.setText(R.string.impress_ppt_success_title);
        }
        if (messageView != null) {
            messageView.setText(getString(R.string.impress_ppt_success_message, pptxFile.getName()));
        }
        View closeBtn = root.findViewById(R.id.ai_dialog_header_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismissImpressGenerationSuccessDialog(false));
        }
        if (buttonRow != null) {
            buttonRow.removeAllViews();
            TextView stayBtn = buildImpressDialogButton(getString(R.string.impress_ppt_stay), false);
            TextView openBtn = buildImpressDialogButton(getString(R.string.impress_ppt_open), true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(52), 1f);
            stayBtn.setLayoutParams(lp);
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(0, dpToPx(52), 1f);
            openLp.setMarginStart(dpToPx(12));
            openBtn.setLayoutParams(openLp);
            stayBtn.setOnClickListener(v -> dismissImpressGenerationSuccessDialog(false));
            openBtn.setOnClickListener(v -> {
                dismissImpressGenerationSuccessDialog(true);
                openImpressGeneratedDocument(pendingGeneratedPptxFile);
            });
            buttonRow.addView(stayBtn);
            buttonRow.addView(openBtn);
        }

        impressGenerationSuccessDialog = new android.app.AlertDialog.Builder(this).create();
        impressGenerationSuccessDialog.setView(root);
        org.libreoffice.androidlib.ai.AiDialogHelper.applyCloseOnlyDismiss(impressGenerationSuccessDialog);
        org.libreoffice.androidlib.ai.AiDialogHelper.applyTransparentWindow(impressGenerationSuccessDialog);
        impressGenerationSuccessDialog.show();
        setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_COMPLETED);
        Log.i(TAG, "ppt_generation_success_dialog shown file=" + pptxFile.getName());
    }

    private void showImpressGenerationErrorDialog(Exception error) {
        String reason = (error != null && error.getMessage() != null && !error.getMessage().isEmpty())
                ? error.getMessage() : "unknown";
        if (impressGenerationErrorDialog != null && impressGenerationErrorDialog.isShowing()) {
            impressGenerationErrorDialog.dismiss();
        }

        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_native_confirm, null, false);
        TextView titleView = root.findViewById(R.id.ai_dialog_header_title);
        TextView messageView = root.findViewById(R.id.native_confirm_message);
        LinearLayout buttonRow = root.findViewById(R.id.native_confirm_button_row);
        if (titleView != null) {
            titleView.setText(R.string.impress_ppt_error_title);
        }
        if (messageView != null) {
            messageView.setText(getString(R.string.impress_ppt_error_message, reason));
        }
        View closeBtn = root.findViewById(R.id.ai_dialog_header_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismissImpressGenerationErrorDialog());
        }
        if (buttonRow != null) {
            buttonRow.removeAllViews();
            TextView okBtn = buildImpressDialogButton(getString(R.string.impress_ppt_error_ok), true);
            okBtn.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(52)));
            okBtn.setOnClickListener(v -> dismissImpressGenerationErrorDialog());
            buttonRow.addView(okBtn);
        }

        impressGenerationErrorDialog = new android.app.AlertDialog.Builder(this).create();
        impressGenerationErrorDialog.setView(root);
        org.libreoffice.androidlib.ai.AiDialogHelper.applyCloseOnlyDismiss(impressGenerationErrorDialog);
        org.libreoffice.androidlib.ai.AiDialogHelper.applyTransparentWindow(impressGenerationErrorDialog);
        impressGenerationErrorDialog.show();
        setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_COMPLETED);
        Log.i(TAG, "ppt_generation_error_dialog shown reason=" + reason);
    }

    private void dismissImpressGenerationErrorDialog() {
        if (impressGenerationErrorDialog != null && impressGenerationErrorDialog.isShowing()) {
            impressGenerationErrorDialog.dismiss();
        }
        impressGenerationErrorDialog = null;
    }

    private void dismissImpressGenerationSuccessDialog(boolean openingDocument) {
        if (impressGenerationSuccessDialog != null && impressGenerationSuccessDialog.isShowing()) {
            impressGenerationSuccessDialog.dismiss();
        }
        impressGenerationSuccessDialog = null;
        if (!openingDocument) {
            dismissImpressOutlineDialog();
        }
    }

    private void openImpressGeneratedDocument(File pptxFile) {
        if (pptxFile == null || !pptxFile.exists()) {
            Log.e(TAG, "ppt_reload_failed reason=file_missing");
            Toast.makeText(this, "生成的 PPT 文件不存在", Toast.LENGTH_SHORT).show();
            setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_COMPLETED);
            return;
        }
        try {
            Uri pptxUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", pptxFile);
            Log.i(TAG, "ppt_open_document uri=" + pptxUri + " path=" + pptxFile.getAbsolutePath());

            dismissImpressOutlineDialog();
            Toast.makeText(this, R.string.impress_ppt_generated, Toast.LENGTH_SHORT).show();

            RecentDocumentsStore.prependRecent(
                    getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE),
                    pptxUri.toString());
            startActivity(buildEditIntent(pptxUri));
        } catch (Exception e) {
            Log.e(TAG, "ppt_reload_failed reason=" + e.getMessage(), e);
            Toast.makeText(this, "打开 PPT 失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            setImpressOutlineDialogState(IMPRESS_OUTLINE_STATE_COMPLETED);
        }
    }

    private void advanceToNextBatch() {
        generateBatchAttempt = 0;
        int nextBatch = generateCurrentBatch + 1;
        if (nextBatch < generateTotalBatches) {
            startPptGenerationBatch(nextBatch);
        } else {
            onPptGenerationComplete();
        }
    }

    private void onPptGenerationComplete() {
        generateActiveRequestId = "";
        Log.i(TAG, "ppt_generation_complete accumulated="
                + generateAccumulatedByOutlineIndex.size()
                + " failedBatches=" + generateFailedBatchCount);
        if (generateFailedBatchCount > 0) {
            Toast.makeText(this,
                    getString(R.string.impress_generate_partial_done, generateFailedBatchCount),
                    Toast.LENGTH_LONG).show();
        }
        fillAndOpenGeneratedPpt();
    }

    // ==================== Calc 对象（图表/图片/形状）选中底部操作栏 ====================

    private void setupCalcObjectBar() {
        if (calcObjectBarController == null) {
            calcObjectBarController = new CalcObjectBarController(new CalcObjectBarController.Host() {
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
                public void hideQuickActionPanel() {
                    LOActivity.this.hideQuickActionPanel();
                }

                @Override
                public View findViewById(int id) {
                    return LOActivity.this.findViewById(id);
                }
            });
        }
        calcObjectBarController.setup();
    }

    private void showCalcObjectBar() {
        if (calcObjectBarController != null) {
            calcObjectBarController.show();
        }
    }

    private void hideCalcObjectBar() {
        if (calcObjectBarController != null) {
            calcObjectBarController.hide();
        }
    }

    private void onDataProcessExecute() {
        final org.json.JSONArray ops = dpPendingOperations;
        if (ops == null || ops.length() == 0) {
            Toast.makeText(this, "没有可执行的操作", Toast.LENGTH_SHORT).show();
            return;
        }
        setDataProcessDialogState(DP_STATE_EXECUTING);
        final String requestId = dpActiveRequestId;
        try {
            String firstOp = ops.length() > 0 ? ops.getJSONObject(0).optString("type", "") : "none";
            String firstRange = ops.length() > 0 ? ops.getJSONObject(0).optString("range", "") : "";
            Log.i(TAG, "calc_data_process_execute_start id=" + requestId + " ops=" + ops.length()
                    + " first=" + firstOp + " range=" + firstRange);
        } catch (Exception e) {
            Log.i(TAG, "calc_data_process_execute_start id=" + requestId + " ops=" + ops.length());
        }

        new Thread(() -> {
            try {
                int total = ops.length();
                for (int i = 0; i < total; i++) {
                    final int step = i;
                    final org.json.JSONObject op = ops.getJSONObject(i);
                    final String type = op.optString("type", "");
                    final String command = op.optString("command", "");
                    final String range = op.optString("range", "");
                    final String header = op.optString("header", "");
                    String valueData = op.optString("value", "");
                    if (valueData.isEmpty()) valueData = op.optString("valueFormula", "");
                    final String valueFormula = valueData;
                    final org.json.JSONArray values = op.optJSONArray("values");

                    Log.i(TAG, "calc_data_process_execute step=" + step + "/" + total + " type=" + type
                            + " range=" + range + " value=" + valueFormula + " header=" + header
                            + " raw=" + op.toString().replaceAll("\\s+", " "));

                    runOnUiThread(() -> {
                        if (dpExecutingStatus != null) {
                            dpExecutingStatus.setText("正在执行第 " + (step + 1) + "/" + total + " 步...\n"
                                    + getOperationTypeLabel(type) + " " + range);
                        }
                    });

                    switch (type) {
                        case "uno":
                            if (!command.isEmpty()) {
                                postUnoCommand(command, "{}", false);
                                Thread.sleep(200);
                            }
                            break;

                        case "set_value": // fall through — same GoToCell + confirm + paste pattern
                        case "set_formula":
                            writeCellValue(range, valueFormula);
                            break;

                        case "add_column":
                            postUnoCommand(".uno:InsertColumnsAfter", "{}", false);
                            Thread.sleep(200);
                            String addColBase = range.isEmpty() ? "A" : extractColLetter(range.split(":")[0]);
                            String newColLetter = nextColumnLetter(addColBase);
                            if (!range.isEmpty() && range.contains(":")) {
                                String endCol = extractColLetter(range.split(":")[1]);
                                newColLetter = nextColumnLetter(endCol);
                            }
                            stripInheritedFormatFromColumn(newColLetter);
                            if (!header.isEmpty() && !range.isEmpty()) {
                                String[] rangeParts = range.split(":");
                                String startRef = rangeParts[0];
                                int startRow = extractRowNumber(startRef);
                                String colLetter = extractColLetter(startRef);
                                String newCol = nextColumnLetter(colLetter);
                                if (range.contains(":")) {
                                    newCol = nextColumnLetter(extractColLetter(range.split(":")[1]));
                                }
                                String headerCell = newCol + startRow;
                                writeCellValue(headerCell, header);
                            }
                            if (values != null && values.length() > 0 && !range.isEmpty()) {
                                String newCol = nextColumnLetter(extractColLetter(range.split(":")[0]));
                                if (range.contains(":")) {
                                    newCol = nextColumnLetter(extractColLetter(range.split(":")[1]));
                                }
                                int baseRow = extractRowNumber(range.split(":")[0]);
                                for (int r = 0; r < values.length(); r++) {
                                    String v = values.optString(r, "");
                                    if (v == null || v.isEmpty()) continue;
                                    writeCellValue(newCol + (baseRow + r + 1), v);
                                }
                            }
                            break;

                        case "sort": {
                            String keyColumn = op.optString("keyColumn", "");
                            boolean ascending = op.optBoolean("ascending", true);
                            boolean hasHeader = op.optBoolean("hasHeader", true);
                            if (!keyColumn.isEmpty()) {
                                String sortCell = keyColumn + "1";
                                long tSort0 = System.currentTimeMillis();
                                waitForReconnectIfNeeded("sort_pre", 3000);
                                postUnoCommand(".uno:GoToCell",
                                    "{\"ToPoint\":{\"type\":\"string\",\"value\":\"" + sortCell + "\"}}",
                                    false);
                                long tSort1 = System.currentTimeMillis();
                                waitForReconnectIfNeeded("sort_post", 3000);
                                String sortParams = hasHeader ? "{\"HasHeader\":true}" : "{}";
                                postUnoCommand(ascending ? ".uno:SortAscending" : ".uno:SortDescending", sortParams, false);
                                Log.i(TAG, "calc_data_process_sort cell=" + sortCell
                                        + " goto=" + (tSort1 - tSort0) + "ms");
                                Thread.sleep(200);
                            }
                            break;
                        }

                        case "filter":
                            postUnoCommand(".uno:DataFilterAutoFilter", "{}", false);
                            Thread.sleep(200);
                            break;

                        case "clear_formatting":
                            if (!range.isEmpty()) {
                                String[] parts = range.split(":");
                                confirmAndExecCellOp(parts[0], ".uno:ResetAttributes", "{}", "clear_formatting");
                            }
                            break;

                        case "delete_rows":
                            if (!range.isEmpty()) {
                                confirmAndExecRowOp(range, ".uno:DeleteRows", "{}", "delete_rows");
                            }
                            break;

                        case "delete_columns":
                            if (!range.isEmpty()) {
                                confirmAndExecColumnOp(range, ".uno:DeleteColumns", "{}", "delete_columns");
                            }
                            break;

                        case "insert_rows":
                            if (!range.isEmpty()) {
                                confirmAndExecRowOp(range, ".uno:InsertRowsBefore", "{}", "insert_rows");
                            }
                            break;

                        case "insert_columns":
                            if (!range.isEmpty()) {
                                confirmAndExecColumnOp(range, ".uno:InsertColumnsAfter", "{}", "insert_columns");
                            }
                            break;

                        case "format_number": {
                            String fmtStyle = op.optString("style", "decimal");
                            int decimals = op.optInt("decimals", -1);
                            if (!range.isEmpty()) {
                                String fmtCmd;
                                switch (fmtStyle) {
                                    case "percent": fmtCmd = ".uno:NumberFormatPercent"; break;
                                    case "currency": fmtCmd = ".uno:NumberFormatCurrency"; break;
                                    case "date": fmtCmd = ".uno:NumberFormatDate"; break;
                                    default:
                                        fmtCmd = decimals > 0
                                            ? ".uno:NumberFormatDecimal?Decimals:short=" + decimals
                                            : ".uno:NumberFormatDecimal";
                                        break;
                                }
                                confirmAndExecCellOp(range, fmtCmd, "{}", "format_number");
                            }
                            break;
                        }

                        case "set_column_width":
                            postUnoCommand(".uno:SetOptimalColumnWidth", "{}", false);
                            Thread.sleep(200);
                            break;

                        case "merge_cells":
                            postUnoCommand(".uno:ToggleMergeCells", "{}", false);
                            Thread.sleep(200);
                            break;

                        case "bold":
                            postUnoCommand(".uno:Bold", "{}", false);
                            Thread.sleep(200);
                            break;

                        case "calculate":
                            postUnoCommand(".uno:Calculate", "{}", false);
                            Thread.sleep(200);
                            break;

                        default:
                            Log.w(TAG, "calc_data_process_unknown_type type=" + type);
                            break;
                    }
                }

                runOnUiThread(() -> {
                    if (dpExecutingStatus != null) {
                        dpExecutingStatus.setText("数据处理完成 ✓");
                    }
                    Log.i(TAG, "calc_data_process_execute_done id=" + requestId);
                    forceRefreshTilesAfterDpExec();
                    getMainHandler().postDelayed(() -> {
                        dismissDataProcessDialog();
                        Toast.makeText(LOActivity.this, "数据处理完成", Toast.LENGTH_SHORT).show();
                    }, 1000L);
                });

            } catch (Exception e) {
                Log.e(TAG, "calc_data_process_execute_error", e);
                runOnUiThread(() -> {
                    if (dpExecutingStatus != null) {
                        dpExecutingStatus.setText("执行失败：" + e.getMessage());
                    }
                    Toast.makeText(LOActivity.this, "数据处理执行失败", Toast.LENGTH_SHORT).show();
                    getMainHandler().postDelayed(() -> setDataProcessDialogState(DP_STATE_COMPLETED), 1500L);
                });
            }
        }, "dp-executor-" + requestId).start();
    }

    private int extractRowNumber(String cellRef) {
        if (cellRef == null) return 0;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
        }
        try {
            return digits.length() > 0 ? Integer.parseInt(digits.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractColLetter(String cellRef) {
        if (cellRef == null) return "A";
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (Character.isLetter(c)) letters.append(c);
            else break;
        }
        String col = letters.toString().toUpperCase();
        return col.isEmpty() ? "A" : col;
    }

    private String nextColumnLetter(String col) {
        if (col == null || col.isEmpty()) return "B";
        char[] chars = col.toUpperCase().toCharArray();
        int i = chars.length - 1;
        while (i >= 0) {
            if (chars[i] < 'Z') {
                chars[i]++;
                return new String(chars);
            }
            chars[i] = 'A';
            i--;
        }
        return "A" + new String(chars);
    }

        /**
     * 检查 JS 侧 socket 是否处于重连状态（_reconnecting || _interactionBlockedForReconnect）。
     * 如果是，轮询等待直到重连完成（_reconnecting 变 false 且 readyState=OPEN）。
     * 在 dp-executor 后台线程中调用。
     *
     * @param tag 日志标签
     * @param timeoutMs 最大等待时间
     * @return true 表示 socket 就绪可写，false 表示超时
     */
    private boolean waitForReconnectIfNeeded(String tag, int timeoutMs) {
        if (mWebView == null) return false;
        final int maxAttempts = Math.max(1, timeoutMs / 150);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] isStable = {false};
            runOnUiThread(() -> {
                if (mWebView == null) { latch.countDown(); return; }
                mWebView.evaluateJavascript(
                    "(function(){try{"
                    + "var s=window.socket;"
                    + "if(!s)return 'no_socket';"
                    + "if(s.isTemporarilyReconnecting&&s.isTemporarilyReconnecting()){"
                    + "return 'reconnecting';"
                    + "}"
                    + "if(s.readyState===1)return 'open';"
                    + "return 'state:'+s.readyState;"
                    + "}catch(e){return 'err';}})()",
                    value -> {
                        isStable[0] = (value != null && value.contains("open"));
                        latch.countDown();
                    });
            });
            try {
                latch.await(150, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (isStable[0]) {
                if (attempt > 0) {
                    Log.i(TAG, "calc_data_process_reconnect_done tag=" + tag + " attempt=" + attempt);
                }
                return true;
            }
            try { Thread.sleep(150); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        Log.w(TAG, "calc_data_process_reconnect_timeout tag=" + tag + " attempts=" + maxAttempts);
        return false;
    }

    /** 简化的 tile 刷新：只触发 resize 重绘，不做光标跳转 */
    private void forceRefreshTilesAfterDpExec() {
        if (mWebView == null) return;
        runOnUiThread(() -> {
            mWebView.evaluateJavascript(
                "(function(){try{"
                + "if(window.app&&app.events)app.events.fire('resize');"
                + "return 'ok';"
                + "}catch(e){return 'err';}})()",
                value -> Log.i(TAG, "calc_data_process_tile_refresh result=" + value));
        });
    }

    /**
     * 安全的单元格写入：检测重连 → GoToCell → 确认无重连 → paste。
     * paste() 通过 native JNI 直接写入 core 内部光标位置，不依赖 JS 侧 cellAddress。
     * 关键防护：每次 GoToCell 后检查 socket 是否因该操作触发了重连（如 close: idle），
     * 如果重连了则等它完成，避免 paste 写到被重置后的错误位置。
     */
    private void writeCellValue(String range, String valueFormula) throws Exception {
        if (range == null || range.isEmpty() || valueFormula == null) {
            return;
        }
        if (valueFormula.isEmpty()) {
            clearCellContentsInRange(range);
            return;
        }
        String[] parts = range.split(":");
        String startRef = parts[0];
        String endRef = parts.length > 1 ? parts[1] : startRef;
        int startRow = extractRowNumber(startRef);
        int endRow = extractRowNumber(endRef);
        String colLetter = extractColLetter(startRef);
        if (colLetter.isEmpty()) {
            Log.w(TAG, "calc_data_process_writeCellValue_invalid_range range=" + range);
            return;
        }
        for (int r = startRow; r <= endRow; r++) {
            String cellRef = colLetter + r;
            String val = valueFormula.replace("{row}", String.valueOf(r));
            long t0 = System.currentTimeMillis();
            waitForReconnectIfNeeded(cellRef, 3000);
            long t1 = System.currentTimeMillis();
            // typed JSON 格式（参考 gtv-signal-handlers.cxx），确保 ToPoint 被正确解析
            postUnoCommand(".uno:GoToCell",
                "{\"ToPoint\":{\"type\":\"string\",\"value\":\"" + cellRef + "\"}}",
                false);
            long t2 = System.currentTimeMillis();
            waitForReconnectIfNeeded(cellRef + "_post", 3000);
            paste("text/plain;charset=utf-8", val.getBytes("UTF-8"));
            long t3 = System.currentTimeMillis();
            Log.i(TAG, "calc_data_process_write_cell cell=" + cellRef
                    + " wait1=" + (t1 - t0) + "ms goto=" + (t2 - t1) + "ms paste=" + (t3 - t2) + "ms");
        }
    }

    /** 选中单列或多列（0-based Col index，与 Web Control.Header.ts 一致）。 */
    private void selectCalcColumn(int colIndex, int modifier) throws Exception {
        waitForReconnectIfNeeded("select_col_" + colIndex, 3000);
        postUnoCommand(".uno:SelectColumn ",
                "{\"Col\":{\"type\":\"unsigned short\",\"value\":" + colIndex + "},"
                        + "\"Modifier\":{\"type\":\"unsigned short\",\"value\":" + modifier + "}}",
                false);
        Log.i(TAG, "calc_data_process_select_column index=" + colIndex
                + " col=" + columnIndexToLetters(colIndex) + " modifier=" + modifier);
    }

    /** 从 range 解析列字母并选中（支持 A:A、A1、A1:C100）。 */
    private void selectCalcColumnsInRange(String range) throws Exception {
        String[] parts = range.split(":", 2);
        String startCol = extractColLetter(parts[0]);
        String endCol = parts.length > 1 ? extractColLetter(parts[1]) : startCol;
        int colStart = columnLettersToIndex(startCol);
        int colEnd = columnLettersToIndex(endCol);
        if (colStart > colEnd) {
            int tmp = colStart;
            colStart = colEnd;
            colEnd = tmp;
        }
        selectCalcColumn(colStart, 0);
        if (colEnd > colStart) {
            selectCalcColumn(colEnd, 1);
        }
    }

    /** 选中单行或多行（0-based Row index，与 Web Control.Header.ts 一致）。 */
    private void selectCalcRow(int rowIndex, int modifier) throws Exception {
        waitForReconnectIfNeeded("select_row_" + rowIndex, 3000);
        postUnoCommand(".uno:SelectRow ",
                "{\"Row\":{\"type\":\"long\",\"value\":" + rowIndex + "},"
                        + "\"Modifier\":{\"type\":\"unsigned short\",\"value\":" + modifier + "}}",
                false);
        Log.i(TAG, "calc_data_process_select_row index=" + rowIndex
                + " row=" + (rowIndex + 1) + " modifier=" + modifier);
    }

    private static int parseRowFromRangePart(String part) {
        if (part == null || part.isEmpty()) return 0;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (Character.isDigit(c)) digits.append(c);
        }
        if (digits.length() == 0) return 0;
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 从 range 解析行号并选中（支持 5:5、3:7、A5、A5:A10）。 */
    private void selectCalcRowsInRange(String range) throws Exception {
        String[] parts = range.split(":", 2);
        int rowStart = parseRowFromRangePart(parts[0]);
        int rowEnd = parts.length > 1 ? parseRowFromRangePart(parts[1]) : rowStart;
        if (rowStart <= 0 || rowEnd <= 0) {
            Log.w(TAG, "calc_data_process_select_row_invalid range=" + range);
            return;
        }
        if (rowStart > rowEnd) {
            int tmp = rowStart;
            rowStart = rowEnd;
            rowEnd = tmp;
        }
        selectCalcRow(rowStart - 1, 0);
        if (rowEnd > rowStart) {
            selectCalcRow(rowEnd - 1, 1);
        }
    }

    /**
     * SelectColumn + 列级 UNO（delete/insert columns）。
     * DeleteColumns 作用于 Core 当前列选区，必须先 SelectColumn。
     */
    private void confirmAndExecColumnOp(String range, String unoCmd, String unoArgs, String logTag) throws Exception {
        String[] parts = range.split(":", 2);
        String startCol = extractColLetter(parts[0]);
        String endCol = parts.length > 1 ? extractColLetter(parts[1]) : startCol;
        Log.i(TAG, "calc_data_process_exec_pre type=" + logTag + " aiRange=" + range
                + " cols=" + startCol + ":" + endCol + " dpCellRange=" + dpCellRange);
        long t0 = System.currentTimeMillis();
        selectCalcColumnsInRange(range);
        long t1 = System.currentTimeMillis();
        waitForReconnectIfNeeded(logTag + "_post", 3000);
        postUnoCommand(unoCmd, unoArgs, false);
        long t2 = System.currentTimeMillis();
        Log.i(TAG, "calc_data_process_" + logTag + " cols=" + startCol + ":" + endCol
                + " select=" + (t1 - t0) + "ms exec=" + (t2 - t1) + "ms");
        if (".uno:InsertColumnsAfter".equals(unoCmd)) {
            stripInheritedFormatFromColumn(nextColumnLetter(endCol));
        } else if (".uno:InsertColumnsBefore".equals(unoCmd)) {
            stripInheritedFormatFromColumn(startCol);
        }
    }

    /**
     * SelectRow + 行级 UNO（delete/insert rows）。
     */
    private void confirmAndExecRowOp(String range, String unoCmd, String unoArgs, String logTag) throws Exception {
        String[] parts = range.split(":", 2);
        int rowStart = parseRowFromRangePart(parts[0]);
        int rowEnd = parts.length > 1 ? parseRowFromRangePart(parts[1]) : rowStart;
        Log.i(TAG, "calc_data_process_exec_pre type=" + logTag + " aiRange=" + range
                + " rows=" + rowStart + ":" + rowEnd + " dpCellRange=" + dpCellRange);
        long t0 = System.currentTimeMillis();
        selectCalcRowsInRange(range);
        long t1 = System.currentTimeMillis();
        waitForReconnectIfNeeded(logTag + "_post", 3000);
        postUnoCommand(unoCmd, unoArgs, false);
        long t2 = System.currentTimeMillis();
        Log.i(TAG, "calc_data_process_" + logTag + " rows=" + rowStart + ":" + rowEnd
                + " select=" + (t1 - t0) + "ms exec=" + (t2 - t1) + "ms");
    }

    /**
     * GoToCell + 结构性 UNO 命令（format/clear 等），带重连检测。
     */
    private void confirmAndExecCellOp(String range, String unoCmd, String unoArgs, String logTag) throws Exception {
        String colLetter = extractColLetter(range);
        if (colLetter == null || colLetter.isEmpty()) {
            Log.w(TAG, "calc_data_process_" + logTag + "_invalid_range range=" + range);
            return;
        }
        String cellRef = range.split(":")[0];
        long t0 = System.currentTimeMillis();
        waitForReconnectIfNeeded(cellRef, 3000);
        long t1 = System.currentTimeMillis();
        // typed JSON 格式（参考 gtv-signal-handlers.cxx），确保 ToPoint 被正确解析
        postUnoCommand(".uno:GoToCell",
            "{\"ToPoint\":{\"type\":\"string\",\"value\":\"" + cellRef + "\"}}",
            false);
        long t2 = System.currentTimeMillis();
        waitForReconnectIfNeeded(cellRef + "_post", 3000);
        postUnoCommand(unoCmd, unoArgs, false);
        long t3 = System.currentTimeMillis();
        Log.i(TAG, "calc_data_process_cell_op tag=" + logTag + " cell=" + cellRef
                + " wait1=" + (t1 - t0) + "ms goto=" + (t2 - t1) + "ms exec=" + (t3 - t2) + "ms");
    }

    /** 选中 range 对应区域（单列用 SelectColumn，否则 GoToCell 到起始格）。 */
    private void selectRangeFromCellRange(String range) throws Exception {
        String[] parts = range.split(":", 2);
        String startCol = extractColLetter(parts[0]);
        String endCol = parts.length > 1 ? extractColLetter(parts[1]) : startCol;
        if (startCol.equals(endCol)) {
            selectCalcColumnsInRange(startCol + ":" + endCol);
        } else {
            waitForReconnectIfNeeded("select_range_goto", 3000);
            postUnoCommand(".uno:GoToCell",
                    "{\"ToPoint\":{\"type\":\"string\",\"value\":\"" + parts[0] + "\"}}",
                    false);
        }
    }

    /** 清空单元格内容（range 参数 UNO，不依赖当前选区）。 */
    private void clearCellContentsInRange(String range) throws Exception {
        Log.i(TAG, "calc_data_process_clear_contents range=" + range + " dpCellRange=" + dpCellRange
                + " method=ClearContentsInRange");
        String args = "{\"Range\":{\"type\":\"string\",\"value\":\"" + range + "\"}}";
        postUnoCommand(".uno:ClearContentsInRange", args, false);
    }

    /** 清除条件格式规则（range 参数 UNO，不依赖选区；不调用 ResetAttributes）。 */
    private void clearFormatInRange(String range) throws Exception {
        Log.i(TAG, "cond_format_clear_range_start range=" + range + " method=ClearConditionalFormat_only");
        String args = "{\"Range\":{\"type\":\"string\",\"value\":\"" + range + "\"}}";
        postUnoCommand(".uno:ClearConditionalFormat", args, false);
        Log.i(TAG, "cond_format_clear_range_done range=" + range);
    }

    /** 插入列后 Calc 会复制相邻列条件格式，对新列按 range 清除继承的 CF 规则。 */
    private void stripInheritedFormatFromColumn(String colLetter) {
        if (colLetter == null || colLetter.isEmpty()) return;
        try {
            String range = colLetter + "1:" + colLetter + "1048576";
            postUnoCommand(".uno:ClearConditionalFormat",
                    "{\"Range\":{\"type\":\"string\",\"value\":\"" + range + "\"}}", false);
            Log.i(TAG, "calc_data_process_strip_cf col=" + colLetter + " range=" + range);
        } catch (Exception e) {
            Log.w(TAG, "calc_data_process_strip_cf_failed col=" + colLetter, e);
        }
    }

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

    private static final String JS_CALC_RANGE_FROM_LAYER =
            "(function(){try{var d=app.map._docLayer;if(!d)return'';var s=d._cellSelectionArea,g=d.sheetGeometry;"
            + "var ca=app.calc&&app.calc.cellAddress;var r='';"
            + "if(g&&s){var cx=g._columns,ry=g._rows;"
            + "if(cx&&ry&&typeof cx.getIndexFromPos==='function'){"
            + "var ci=cx.getIndexFromPos(s.pX1,'tiletwips'),ri=ry.getIndexFromPos(s.pY1,'tiletwips'),"
            + "cj=cx.getIndexFromPos(s.pX2,'tiletwips'),rj=ry.getIndexFromPos(s.pY2,'tiletwips');"
            + "function cl(c){var s='';while(c>=0){s=String.fromCharCode(65+(c%26))+s;c=Math.floor(c/26)-1;}return s;}"
            + "r=cl(ci)+(ri+1)+':'+cl(cj)+(rj+1);}}"
            + "if(!r&&ca){function cl(c){var s='';while(c>=0){s=String.fromCharCode(65+(c%26))+s;c=Math.floor(c/26)-1;}return s;}"
            + "r=cl(ca.x)+(ca.y+1);}return r;}catch(e){return'';}})()";

    private static final String JS_CALC_HYPERLINK_CONTEXT =
            "(function(){try{var d=app.map._docLayer;if(!d)return'';"
            + "var part=d._selectedPart||0,sheet=(d._partNames&&d._partNames[part])?d._partNames[part]:'',"
            + "sheets=d._partNames?d._partNames.slice():[],range='';"
            + "var s=d._cellSelectionArea,g=d.sheetGeometry,ca=app.calc&&app.calc.cellAddress;"
            + "if(g&&s){var cx=g._columns,ry=g._rows;"
            + "if(cx&&ry&&typeof cx.getIndexFromPos==='function'){"
            + "var ci=cx.getIndexFromPos(s.pX1,'tiletwips'),ri=ry.getIndexFromPos(s.pY1,'tiletwips'),"
            + "cj=cx.getIndexFromPos(s.pX2,'tiletwips'),rj=ry.getIndexFromPos(s.pY2,'tiletwips');"
            + "function cl(c){var t='';while(c>=0){t=String.fromCharCode(65+(c%26))+t;c=Math.floor(c/26)-1;}return t;}"
            + "range=cl(ci)+(ri+1)+':'+cl(cj)+(rj+1);}}"
            + "if(!range&&ca){function cl(c){var t='';while(c>=0){t=String.fromCharCode(65+(c%26))+t;c=Math.floor(c/26)-1;}return t;}"
            + "range=cl(ca.x)+(ca.y+1);}"
            + "return JSON.stringify({range:range,sheet:sheet,sheets:sheets});"
            + "}catch(e){return JSON.stringify({range:'',sheet:'',sheets:[]});}})()";

    /** 原生插入超链接后：关闭 URL 浮层、抑制自动弹出、关闭 mobile wizard。 */
    private static final String JS_AFTER_NATIVE_HYPERLINK_INSERT =
            "(function(){try{"
            + "if(window.URLPopUpSection&&URLPopUpSection.closeURLPopUp){URLPopUpSection.closeURLPopUp();}"
            + "if(window.app&&app.map){app.map._suppressHyperlinkPopupUntil=Date.now()+3000;}"
            + "if(window.app&&app.map&&typeof app.map.fire==='function'){app.map.fire('closemobilewizard');}"
            + "}catch(e){}})()";

    private static String parseJsStringResult(String value) {
        if (value == null || value.equals("null") || value.length() <= 2) return "";
        try {
            Object p = new org.json.JSONTokener(value).nextValue();
            return (p instanceof String) ? (String) p : "";
        } catch (org.json.JSONException e) {
            return "";
        }
    }

    private static int countCalcSelectionColumns(String selText) {
        if (selText == null || selText.isEmpty()) return 0;
        String firstLine = selText.split("\n", 2)[0];
        if (!firstLine.contains("\t")) return 1;
        return firstLine.split("\t", -1).length;
    }

    private static int columnLettersToIndex(String letters) {
        int col = 0;
        for (int i = 0; i < letters.length(); i++) {
            col = col * 26 + (Character.toUpperCase(letters.charAt(i)) - 'A' + 1);
        }
        return col - 1;
    }

    private static String columnIndexToLetters(int index) {
        StringBuilder sb = new StringBuilder();
        int c = index;
        while (c >= 0) {
            sb.insert(0, (char) ('A' + (c % 26)));
            c = c / 26 - 1;
        }
        return sb.toString();
    }

    /**
     * _cellSelectionArea 在 Calc 上常只反映部分选区（如 A1:A3），
     * 而 getTextSelection 含完整数据行数/列数；用后者扩展范围。
     */
    private static String refineCalcRangeFromSelectionText(String jsRange, String selText) {
        if (selText == null || selText.isEmpty()) return jsRange == null ? "" : jsRange;
        int selLines = selText.split("\n", -1).length;
        int selCols = countCalcSelectionColumns(selText);
        if (selLines <= 0) return jsRange == null ? "" : jsRange;

        String range = (jsRange == null) ? "" : jsRange.trim();
        String startRef;
        String endRef;
        if (range.isEmpty()) {
            return "";
        }
        if (range.contains(":")) {
            String[] parts = range.split(":", 2);
            startRef = parts[0];
            endRef = parts[1];
        } else {
            startRef = range;
            endRef = range;
        }

        java.util.regex.Matcher startM = java.util.regex.Pattern
                .compile("([A-Za-z]+)(\\d+)").matcher(startRef);
        java.util.regex.Matcher endM = java.util.regex.Pattern
                .compile("([A-Za-z]+)(\\d+)").matcher(endRef);
        if (!startM.matches() || !endM.matches()) return range;

        int startCol = columnLettersToIndex(startM.group(1));
        int startRow = Integer.parseInt(startM.group(2)) - 1;
        int endCol = columnLettersToIndex(endM.group(1));
        int endRow = Integer.parseInt(endM.group(2)) - 1;

        int jsRows = endRow - startRow + 1;
        int jsCols = endCol - startCol + 1;
        if (selLines > jsRows) {
            endRow = startRow + selLines - 1;
        }
        if (selCols > jsCols) {
            endCol = startCol + selCols - 1;
        }

        String refined = columnIndexToLetters(startCol) + (startRow + 1);
        if (endCol != startCol || endRow != startRow) {
            refined += ":" + columnIndexToLetters(endCol) + (endRow + 1);
        }
        if (!refined.equals(range)) {
            Log.i(TAG, "calc_range_refined from=" + range + " to=" + refined
                    + " selLines=" + selLines + " selCols=" + selCols);
        }
        return refined;
    }

    private interface CalcRangeCallback {
        void onRange(String range);
    }

    private void readCalcSelectionRangeAsync(CalcRangeCallback callback) {
        if (mWebView == null || callback == null) {
            if (callback != null) callback.onRange("");
            return;
        }
        String selText = "";
        try {
            selText = getTextSelection("text/plain;charset=utf-8");
        } catch (Exception ignored) {}
        if (selText == null) selText = "";
        final String selTextFinal = selText;
        final int selLines = selText.isEmpty() ? 0 : selText.split("\n", -1).length;
        mWebView.evaluateJavascript(JS_CALC_RANGE_FROM_LAYER, value -> {
            String jsRange = parseJsStringResult(value);
            String refined = refineCalcRangeFromSelectionText(jsRange, selTextFinal);
            Log.i(TAG, "calc_range_read js=" + jsRange + " refined=" + refined + " selLines=" + selLines);
            callback.onRange(refined);
        });
    }

    /*package*/ void preReadCalcSelectionForSheet() {
        if (mWebView == null || !mIsCalcDocument) { calcSelectedRange = ""; return; }
        readCalcSelectionRangeAsync(range -> calcSelectedRange = range);
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
                public void switchToEditMode() {
                    LOActivity.this.switchToEditMode();
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

                @Override
                public int getOpenDocumentCount() {
                    return LOActivity.this.getOpenDocumentCount();
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
                public View findViewById(int id) {
                    return LOActivity.this.findViewById(id);
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

                @Override
                public void onOpenDocumentListChanged() {
                    ensureTopToolbarController().refreshOpenDocumentCount();
                }
            });
            documentTabsSheetController.bindOverlayViews();
        }
        return documentTabsSheetController;
    }

    private void showFindReplaceSheet() {
        ensureFindReplaceSheetController().show();
    }

    private void shareCurrentDocument() {
        final Uri sourceUri = getIntent().getData();
        if (sourceUri == null) {
            Toast.makeText(this, "无法分享当前文档", Toast.LENGTH_SHORT).show();
            return;
        }
        // Export to docx on background thread so we share a standard format
        new Thread(() -> {
            try {
                String baseName = getFileName(false);
                if (baseName == null || baseName.trim().isEmpty()) baseName = "document";
                else baseName = baseName.replaceAll("[\\\\/:*?\"<>|]+", "_");
                File shareFile = new File(getCacheDir(), baseName + ".docx");
                // Avoid overwriting if a previous export exists at the same path
                if (shareFile.exists()) shareFile.delete();
                saveAs(Uri.fromFile(shareFile).toString(), "docx", null);
                if (shareFile.exists() && shareFile.length() > 0) {
                    Uri shareUri = FileProvider.getUriForFile(LOActivity.this,
                            getPackageName() + ".fileprovider", shareFile);
                    runOnUiThread(() -> shareFileViaIntent(shareUri));
                    return;
                }
            } catch (Exception e) {
                Log.w(TAG, "share_export_docx_failed", e);
            }
            // Fallback: share original temp file via existing helper
            runOnUiThread(() -> DocumentShareHelper.shareDocument(this, sourceUri, mTempFile));
        }, "cool-share-export").start();
    }

    private void shareFileViaIntent(Uri shareUri) {
        String mimeType = getContentResolver().getType(shareUri);
        if (mimeType == null) {
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "分享文档"));
    }

    private void showDocumentTabsSheet() {
        ensureTopToolbarController().refreshOpenDocumentCount();
        ensureDocumentTabsSheetController().show();
    }

    int getOpenDocumentCount() {
        int count = RecentDocumentsStore.getRecentUris(
                getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE)).size();
        return Math.max(1, count);
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
        ensureBottomToolbarController().updateDocumentType(mIsCalcDocument);
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
                public void switchToEditMode() {
                    LOActivity.this.switchToEditMode();
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
            // Always consume WebView long-click so native bridge handles selection,
            // and WebView default text-selection doesn't interfere with touch events.
            // MobileWizard (for Calc) is triggered by core MOBILEWIZARD show messages,
            // not by WebView long-click — so we don't need to toggle this.
            mWebView.setConsumeWebViewLongClick(true);
        }
        ensureBottomToolbarController().updateEditModeState(isEditMode, reason);
        ensureBottomToolbarController().updateDocumentType(mIsCalcDocument);
        ensureTopToolbarController().updateEditModeState(isEditMode, reason);
        if (isEditMode) {
            detectDocumentType();
        }
    }

    private void hideQuickActionPanel() {
        ensureBottomToolbarController().hideQuickActionPanel();
        hideCalcObjectBar();
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
            if (impressFunctionPanelController != null) {
                impressFunctionPanelController.dismiss();
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
        if (mWebView == null) {
            return;
        }
        if (imeVisible) {
            // Keyboard visible: keep editor mode so keystrokes reach the cell editor.
            setImeAllowedByUserSustained(true);
            Log.d(TAG, "ime_allowed_sustained keyboard_visible");
        } else {
            // Defer clear to avoid racing showSoftInput() during Calc edit-mode entry.
            scheduleImeAllowedClear("ime_hidden");
        }
    }

    private void showFunctionPanel() {
        if (mIsEditModeActive && mIsCalcDocument) {
            if (calcFunctionPanelController != null) {
                calcFunctionPanelController.dismiss();
                calcFunctionPanelController = null;
            }
            dismissImpressFunctionPanel();
            ensureCalcFunctionPanelController().show();
            return;
        }
        if (mIsEditModeActive && mIsImpressDocument) {
            dismissCalcFunctionPanel();
            dismissWriterFunctionPanel();
            ensureImpressFunctionPanelController().show();
            return;
        }
        if (mIsEditModeActive && !mIsCalcDocument) {
            dismissImpressFunctionPanel();
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
        View printAction = panel.findViewById(R.id.function_action_export_pdf);
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
        if (printAction != null) printAction.setOnClickListener(v -> runFunctionAction(this::downloadCurrentTextDocumentAsPdf));
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
        if (calcFunctionPanelController != null) {
            calcFunctionPanelController.dismiss();
        }
        if (impressFunctionPanelController != null) {
            impressFunctionPanelController.dismiss();
        }
        action.run();
    }

    private void dismissCalcFunctionPanel() {
        if (calcFunctionPanelController != null) {
            calcFunctionPanelController.dismiss();
            calcFunctionPanelController = null;
        }
    }

    private void dismissWriterFunctionPanel() {
        if (functionPanelController != null) {
            functionPanelController.dismiss();
        }
    }

    private void dismissImpressFunctionPanel() {
        if (impressFunctionPanelController != null) {
            impressFunctionPanelController.dismiss();
            impressFunctionPanelController = null;
        }
    }

    private CalcFunctionPanelController ensureCalcFunctionPanelController() {
        if (calcFunctionPanelController == null) {
            calcFunctionPanelController = new CalcFunctionPanelController(new CalcFunctionPanelController.Host() {
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
                public void insertComment() {
                    LOActivity.this.insertCommentFromPanel();
                }

                @Override
                public void toastTodo(String text) {
                    LOActivity.this.toastTodo(text);
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
                public void fetchFontList(FunctionPanelController.StringListCallback callback) {
                    LOActivity.this.fetchFontListAsync(callback);
                }

                @Override
                public void fetchCurrentFormatting(FunctionPanelController.FormattingCallback callback) {
                    LOActivity.this.fetchCurrentFormattingAsync(callback);
                }

                @Override
                public void showAiOperationSheet() {
                    LOActivity.this.showNativeAiOperationSheet();
                }

                @Override
                public void focusDocumentAndShowIme() {
                    LOActivity.this.focusDocumentAndShowIme();
                }

                @Override
                public void runAfterFunctionPanelDismiss(Runnable action) {
                    LOActivity.this.runAfterFunctionPanelDismiss(action);
                }

                @Override
                public void insertChartWithType(String unoChartType) {
                    LOActivity.this.insertChartWithType(unoChartType);
                }

                @Override
                public void insertHyperlink(String displayText, String url) {
                    LOActivity.this.insertHyperlink(displayText, url);
                }

                @Override
                public void fetchCalcHyperlinkContext(
                        CalcHyperlinkPickerController.HyperlinkContextCallback callback) {
                    LOActivity.this.fetchCalcHyperlinkContext(callback);
                }
            });
        }
        return calcFunctionPanelController;
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

    private ImpressFunctionPanelController ensureImpressFunctionPanelController() {
        if (impressFunctionPanelController == null) {
            impressFunctionPanelController = new ImpressFunctionPanelController(
                    new ImpressFunctionPanelController.Host() {
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
                public void toastTodo(String text) {
                    LOActivity.this.toastTodo(text);
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
                public void fetchFontList(FunctionPanelController.StringListCallback callback) {
                    LOActivity.this.fetchFontListAsync(callback);
                }

                @Override
                public void fetchCurrentFormatting(FunctionPanelController.FormattingCallback callback) {
                    LOActivity.this.fetchCurrentFormattingAsync(callback);
                }

                @Override
                public void showAiOperationSheet() {
                    LOActivity.this.showNativeAiOperationSheet();
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
                public void runAfterFunctionPanelDismiss(Runnable action) {
                    LOActivity.this.runAfterFunctionPanelDismiss(action);
                }
            });
        }
        return impressFunctionPanelController;
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

    void insertChartWithType(String unoChartType) {
        if (unoChartType == null || unoChartType.isEmpty()) {
            return;
        }
        final String templateService = CalcChartTypeMapper.needsCustomTemplate(unoChartType)
                ? CalcChartTypeMapper.toTemplateService(unoChartType) : "";
        final int curveStyle = CalcChartTypeMapper.toCurveStyle(unoChartType);
        final String insertArgs = CalcChartTypeMapper.buildInsertChartJson("", templateService, curveStyle);
        Log.i(TAG, "function_insert_chart type=" + unoChartType
                + " template=" + templateService + " curveStyle=" + curveStyle);
        requestWebViewFocusForPanelAction("insert_chart");
        new Thread(() -> {
            try {
                postUnoCommand(".uno:InsertObjectChart", insertArgs, false);
                runOnUiThread(() -> {
                    nudgeSocketIfStalled("insert_chart");
                    forceVisibleTileRedrawFromAndroid("insert_chart");
                });
            } catch (Exception e) {
                Log.e(TAG, "function_insert_chart_error type=" + unoChartType, e);
            }
        }, "insert-chart-" + unoChartType).start();
    }

    void insertHyperlink(String displayText, String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        String text = displayText != null ? displayText.trim() : "";
        if (text.isEmpty()) {
            text = url.trim();
        }
        final String label = text;
        final String targetUrl = url.trim();
        Log.i(TAG, "function_insert_hyperlink text=" + label + " url=" + targetUrl);
        Runnable apply = () -> {
            requestWebViewFocusForPanelAction("insert_hyperlink");
            try {
                JSONObject textArg = new JSONObject();
                textArg.put("type", "string");
                textArg.put("value", label);
                JSONObject urlArg = new JSONObject();
                urlArg.put("type", "string");
                urlArg.put("value", targetUrl);
                JSONObject root = new JSONObject();
                root.put("Hyperlink.Text", textArg);
                root.put("Hyperlink.URL", urlArg);
                if (!label.equals(targetUrl)) {
                    JSONObject replacementArg = new JSONObject();
                    replacementArg.put("type", "string");
                    replacementArg.put("value", label);
                    root.put("Hyperlink.ReplacementText", replacementArg);
                }
                postUnoCommand(".uno:SetHyperlink", root.toString(), false);
            } catch (Exception e) {
                Log.e(TAG, "function_insert_hyperlink_json_failed", e);
                return;
            }
            if (mWebView != null) {
                mWebView.evaluateJavascript(JS_AFTER_NATIVE_HYPERLINK_INSERT, null);
            }
            nudgeSocketIfStalled("insert_hyperlink");
            forceVisibleTileRedrawFromAndroid("insert_hyperlink");
        };
        if (mIsCalcDocument) {
            ensureEditModeThen(apply);
        } else {
            apply.run();
        }
    }

    void fetchCalcHyperlinkContext(CalcHyperlinkPickerController.HyperlinkContextCallback callback) {
        if (callback == null) {
            return;
        }
        if (mWebView == null || !mIsCalcDocument) {
            runOnUiThread(() -> callback.onContext("", "", new String[0]));
            return;
        }
        readCalcSelectionRangeAsync(range -> mWebView.evaluateJavascript(
                JS_CALC_HYPERLINK_CONTEXT,
                value -> runOnUiThread(() -> {
                    String cellRange = range != null ? range : "";
                    String activeSheet = "";
                    String[] sheets = new String[0];
                    try {
                        org.json.JSONObject obj = new org.json.JSONObject(parseJsStringResult(value));
                        if (cellRange.isEmpty()) {
                            cellRange = obj.optString("range", "");
                        }
                        activeSheet = obj.optString("sheet", "");
                        org.json.JSONArray arr = obj.optJSONArray("sheets");
                        if (arr != null && arr.length() > 0) {
                            sheets = new String[arr.length()];
                            for (int i = 0; i < arr.length(); i++) {
                                sheets[i] = arr.optString(i, "");
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "fetch_calc_hyperlink_context_parse_failed", e);
                    }
                    Log.i(TAG, "fetch_calc_hyperlink_context range=" + cellRange
                            + " sheet=" + activeSheet + " sheets=" + sheets.length);
                    callback.onContext(cellRange, activeSheet, sheets);
                })));
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
        if (calcHyperlinkCellPopupController != null) {
            calcHyperlinkCellPopupController.hide();
        }
        pendingAfterEditMode = null;
        lastPreviewModeSwitchMs = android.os.SystemClock.uptimeMillis();
        updateEditModeState(false, "manual_preview_switch");
        awaitingPreviewModeJsAck = true;
        mobilePreviewSwitchAttempt = 1;
        getMainHandler().removeCallbacks(mobilePreviewAckTimeoutRunnable);
        callFakeWebsocketOnMessage("mobile: readonlymode");
        getMainHandler().postDelayed(mobilePreviewAckTimeoutRunnable, MOBILE_PREVIEW_ACK_TIMEOUT_MS);
    }

    private void switchToEditMode() {
        if (!isDocEditable) {
            Toast.makeText(this, "当前文档为只读，无法编辑", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mIsEditModeActive) {
            return;
        }
        cancelPreviewModeSwitchAck("manual_edit_switch");
        manualEditModeSwitchPending = true;
        lastPreviewModeSwitchMs = 0L;
        updateEditModeState(true, "manual_edit_switch");
        if (mIsCalcDocument && mWebView != null) {
            getMainHandler().post(this::showCalcEditModeKeyboardOnEntry);
        }
        if (mWebView != null) {
            mWebView.evaluateJavascript(
                    "(function(){try{if(window.app&&app.map&&typeof app.map._switchToEditMode==='function')"
                            + "{app.map._switchToEditMode();}}catch(e){}"
                            + "return true;})();",
                    null);
        }
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

    private void cancelImeAllowResetRunnable() {
        if (imeAllowResetRunnable != null) {
            getMainHandler().removeCallbacks(imeAllowResetRunnable);
            imeAllowResetRunnable = null;
        }
    }

    private void cancelImeClearDeferredRunnable() {
        if (imeClearDeferredRunnable != null) {
            getMainHandler().removeCallbacks(imeClearDeferredRunnable);
            imeClearDeferredRunnable = null;
        }
    }

    private void setImeAllowedByUserSustained(boolean allowed) {
        cancelImeAllowResetRunnable();
        cancelImeClearDeferredRunnable();
        if (mWebView != null) {
            mWebView.setImeAllowedByUser(allowed);
        }
    }

    private void scheduleImeAllowedClear(String reason) {
        cancelImeClearDeferredRunnable();
        imeClearDeferredRunnable = () -> {
            imeClearDeferredRunnable = null;
            if (!isImeVisibleForToolbar) {
                clearImeAllowedByUserTransient(reason);
            }
        };
        getMainHandler().postDelayed(imeClearDeferredRunnable, IME_CLEAR_DEFER_MS);
    }

    private void setImeAllowedByUserTransient(boolean allowed) {
        if (mWebView == null) {
            return;
        }
        cancelImeClearDeferredRunnable();
        clearImeAllowedByUserTransient("replace_transient");
        mWebView.setImeAllowedByUser(allowed);
        if (!allowed) {
            return;
        }
        imeAllowResetRunnable = () -> {
            imeAllowResetRunnable = null;
            if (mWebView != null && !isImeVisibleForToolbar) {
                mWebView.setImeAllowedByUser(false);
            }
        };
        getMainHandler().postDelayed(imeAllowResetRunnable, IME_ALLOW_RESET_MS);
    }

    private void clearImeAllowedByUserTransient(String reason) {
        cancelImeAllowResetRunnable();
        cancelImeClearDeferredRunnable();
        if (mWebView != null) {
            mWebView.setImeAllowedByUser(false);
            Log.d(TAG, "ime_allowed_cleared reason=" + reason);
        }
    }

    private void showCalcEditModeKeyboardOnEntry() {
        if (mWebView == null || !mIsCalcDocument || !mIsEditModeActive) {
            return;
        }
        mWebView.evaluateJavascript(
                "(function(){if(window.app&&app.map){app.map.focus(true);}return true;})();",
                null);
        setImeAllowedByUserSustained(true);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mWebView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    void runAfterFunctionPanelDismiss(Runnable action) {
        if (action == null) {
            return;
        }
        getMainHandler().postDelayed(action, 200L);
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
        setImeAllowedByUserSustained(true);
        mWebView.requestFocus();
        mWebView.evaluateJavascript(
                "(function(){if(window.app&&app.map){app.map.focus(true);}return true;})();",
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
        clearImeAllowedByUserTransient("hide_keyboard");
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
        if (aiTabDocQa != null) {
            aiTabDocQa.setText(mIsCalcDocument ? "表格Q&A" : "文档Q&A");
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
        View closeButton = panel.findViewById(R.id.ai_sheet_header_close);
        aiOpSelectionHint = panel.findViewById(R.id.ai_op_selection_hint);
        View cancelButton = panel.findViewById(R.id.ai_op_cancel);

        // ============= 根据文档类型切换按钮可见性 =============
        // Calc 文档：隐藏 Writer 按钮，显示 Calc 按钮
        // Writer 文档：隐藏 Calc 按钮，显示 Writer 按钮
        View calcBlock = panel.findViewById(R.id.ai_op_calc_block);
        if (mIsCalcDocument) {
            if (calcBlock != null) calcBlock.setVisibility(View.VISIBLE);
            // Hide Writer sections
            View writerSection1 = panel.findViewById(R.id.ai_op_writer_section_1_title);
            View writerSection1Row = panel.findViewById(R.id.ai_op_writer_section_1_row);
            if (writerSection1 != null) writerSection1.setVisibility(View.GONE);
            if (writerSection1Row != null) writerSection1Row.setVisibility(View.GONE);
            // Hide Section 2 (文案处理)
            View[] writerSections = {
                panel.findViewById(R.id.ai_op_writer_section_2_title),
                panel.findViewById(R.id.ai_op_writer_section_2_row1),
                panel.findViewById(R.id.ai_op_writer_section_2_row2)
            };
            for (View v : writerSections) {
                if (v != null) v.setVisibility(View.GONE);
            }
            // Hide Section 3 (其他)
            View[] otherSections = {
                panel.findViewById(R.id.ai_op_other_title),
                panel.findViewById(R.id.ai_op_other_row1),
                panel.findViewById(R.id.ai_op_other_row2)
            };
            for (View v : otherSections) {
                if (v != null) v.setVisibility(View.GONE);
            }
            // Hide loading bar (not used for Calc AI yet)
            View loadingBar = panel.findViewById(R.id.ai_op_loading_bar);
            if (loadingBar != null) loadingBar.setVisibility(View.GONE);
            // Hide selection hint (Calc doesn't need text selection for formula)
            View selHint = panel.findViewById(R.id.ai_op_selection_hint);
            if (selHint != null) selHint.setVisibility(View.GONE);
        } else {
            if (calcBlock != null) calcBlock.setVisibility(View.GONE);
        }

        // Impress 文档：显示 Impress 区块，隐藏 Writer
        View impressBlock = panel.findViewById(R.id.ai_op_impress_block);
        if (mIsImpressDocument) {
            if (impressBlock != null) impressBlock.setVisibility(View.VISIBLE);
            View writerSection1 = panel.findViewById(R.id.ai_op_writer_section_1_title);
            View writerSection1Row = panel.findViewById(R.id.ai_op_writer_section_1_row);
            if (writerSection1 != null) writerSection1.setVisibility(View.GONE);
            if (writerSection1Row != null) writerSection1Row.setVisibility(View.GONE);
            View[] writerSections = {
                panel.findViewById(R.id.ai_op_writer_section_2_title),
                panel.findViewById(R.id.ai_op_writer_section_2_row1),
                panel.findViewById(R.id.ai_op_writer_section_2_row2)
            };
            for (View v : writerSections) {
                if (v != null) v.setVisibility(View.GONE);
            }
            View[] otherSections = {
                panel.findViewById(R.id.ai_op_other_title),
                panel.findViewById(R.id.ai_op_other_row1),
                panel.findViewById(R.id.ai_op_other_row2)
            };
            for (View v : otherSections) {
                if (v != null) v.setVisibility(View.GONE);
            }
            View loadingBar = panel.findViewById(R.id.ai_op_loading_bar);
            if (loadingBar != null) loadingBar.setVisibility(View.GONE);
            View selHint = panel.findViewById(R.id.ai_op_selection_hint);
            if (selHint != null) selHint.setVisibility(View.GONE);
        } else {
            if (impressBlock != null) impressBlock.setVisibility(View.GONE);
        }

        Log.i(TAG, "ai_op_sheet_doc_type isCalc=" + mIsCalcDocument
                + " calcBlockVisible=" + (calcBlock != null && calcBlock.getVisibility() == View.VISIBLE));

        normalizeAiFunctionSheetLayout(panel);

        // Bind Calc AI formula button
        View calcFormulaBtn = panel.findViewById(R.id.ai_op_calc_formula);
        if (calcFormulaBtn != null) {
            calcFormulaBtn.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_calc_formula_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                openCalcFormulaDialog();
            });
        }

        // Bind Calc AI cond format button
        View calcCondFormatBtn = panel.findViewById(R.id.ai_op_calc_cond_format);
        if (calcCondFormatBtn != null) {
            calcCondFormatBtn.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_calc_cond_format_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                openCondFormatDialog();
            });
        }

        // Bind Calc AI data process button
        View dpBtn = panel.findViewById(R.id.ai_op_calc_data_process);
        if (dpBtn != null) {
            dpBtn.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_calc_data_process_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                dpIsAnalysisMode = false;
                if (dpTitle != null) dpTitle.setText("AI数据处理");
                if (dpInput != null) dpInput.setHint("重复数据全部清除");
                openDataProcessDialog();
            });
        }

        // Bind Calc AI data analysis button
        View calcDataAnalysisBtn = panel.findViewById(R.id.ai_op_calc_data_analysis);
        if (calcDataAnalysisBtn != null) {
            calcDataAnalysisBtn.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_calc_data_analysis_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                openDataAnalysisDialog();
            });
        }

        // Bind Calc AI chart button
        View calcChartBtn = panel.findViewById(R.id.ai_op_calc_chart);
        if (calcChartBtn != null) {
            calcChartBtn.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_calc_chart_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                openChartDialog();
            });
        }

        // Bind Impress AI PPT button
        View impressPptBtn = panel.findViewById(R.id.ai_op_impress_ppt);
        if (impressPptBtn != null) {
            impressPptBtn.setOnClickListener(v -> {
                Log.i(TAG, "ai_op_impress_ppt_clicked");
                if (aiOperationSheet != null) {
                    aiOperationSheet.dismiss();
                }
                openImpressOutlineDialog();
            });
        }

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
        aiOperationSheet.setOnShowListener(dialog -> {
            Log.i(TAG, "ai_op_sheet_onshow");
            // 预读 Calc 选区范围（延迟 200ms 确保焦点回到 calc）
            if (mIsCalcDocument) {
                getMainHandler().postDelayed(() -> preReadCalcSelectionForSheet(), 200);
            }
        });

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

    private static final int AI_FUNCTION_GRID_COLUMNS = 3;
    private static final int AI_FUNCTION_ICON_DP = 48;
    private static final int AI_FUNCTION_CARD_HEIGHT_DP = 160;
    private static final int AI_FUNCTION_CARD_GAP_DP = 10;

    /** 统一 AI 功能面板网格：每行固定 3 列等宽，图标固定 48dp 不随列数拉伸。 */
    private void normalizeAiFunctionSheetLayout(View panel) {
        if (panel == null) {
            return;
        }
        int[] rowIds = {
                R.id.ai_op_calc_section_row,
                R.id.ai_op_calc_section_row2,
                R.id.ai_op_writer_section_1_row,
                R.id.ai_op_writer_section_2_row1,
                R.id.ai_op_writer_section_2_row2,
                R.id.ai_op_other_row1,
                R.id.ai_op_other_row2,
        };
        for (int rowId : rowIds) {
            normalizeAiFunctionGridRow(panel.findViewById(rowId), AI_FUNCTION_GRID_COLUMNS);
        }
    }

    private void normalizeAiFunctionGridRow(View rowView, int columnCount) {
        if (!(rowView instanceof LinearLayout)) {
            return;
        }
        LinearLayout row = (LinearLayout) rowView;
        if (row.getOrientation() != LinearLayout.HORIZONTAL || row.getVisibility() != View.VISIBLE) {
            return;
        }

        int visibleCards = 0;
        java.util.List<View> spaces = new java.util.ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof Space) {
                spaces.add(child);
                continue;
            }
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            visibleCards++;
            enforceAiFunctionCardIconSize(child);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
            lp.width = 0;
            lp.height = dpToPx(AI_FUNCTION_CARD_HEIGHT_DP);
            lp.weight = 1f;
            child.setLayoutParams(lp);
        }

        for (View space : spaces) {
            row.removeView(space);
        }
        int neededSpaces = Math.max(0, columnCount - visibleCards);
        for (int i = 0; i < neededSpaces; i++) {
            Space space = new Space(this);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    0, dpToPx(AI_FUNCTION_CARD_HEIGHT_DP), 1f);
            space.setLayoutParams(slp);
            row.addView(space);
        }

        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof Space) {
                continue;
            }
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) child.getLayoutParams();
            boolean hasVisibleSiblingAfter = false;
            for (int j = i + 1; j < row.getChildCount(); j++) {
                View next = row.getChildAt(j);
                if (next instanceof Space) {
                    continue;
                }
                if (next.getVisibility() == View.VISIBLE) {
                    hasVisibleSiblingAfter = true;
                    break;
                }
            }
            lp.setMarginEnd(hasVisibleSiblingAfter ? dpToPx(AI_FUNCTION_CARD_GAP_DP) : 0);
            child.setLayoutParams(lp);
        }
    }

    private void enforceAiFunctionCardIconSize(View card) {
        if (!(card instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) card;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ImageView) {
                ImageView icon = (ImageView) child;
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                int iconPx = dpToPx(AI_FUNCTION_ICON_DP);
                ViewGroup.LayoutParams raw = icon.getLayoutParams();
                if (raw instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
                    lp.width = iconPx;
                    lp.height = iconPx;
                    lp.weight = 0f;
                    icon.setLayoutParams(lp);
                } else {
                    raw.width = iconPx;
                    raw.height = iconPx;
                    icon.setLayoutParams(raw);
                }
                return;
            }
        }
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
     * 从源文档的 mTempFile（docx）提取全文文本 + 图片标记 + 图片数据。
     * 图片在文本中标记为 [图1]、[图2]…，以在排版后保持原图位置。
     * 需要后台线程调用（涉及解压/解析，非 UI 线程）。
     *
     * @return 带 [图N] 标记的全文（失败/null 回退到 extractFullTextNative）
     */
    @Nullable
    private String extractFullTextWithImagesNative() {
        if (mTempFile == null || !mTempFile.exists()) {
            Log.i(TAG, "extract_image_text_skipped no mTempFile");
            return null;
        }
        try {
            // Prefer current mTempFile (has latest user edits including inserted images).
            // Fall back to mOriginalTypesetDocx (original format backup) if mTempFile
            // is unavailable, e.g. if LOKit hasn't saved yet.
            File docxFile = (mTempFile != null && mTempFile.exists())
                    ? mTempFile
                    : (mOriginalTypesetDocx != null && mOriginalTypesetDocx.exists())
                        ? mOriginalTypesetDocx
                        : null;
            if (docxFile == null) {
                Log.i(TAG, "extract_image_text_no_source_file");
                return null;
            }
            // 1. Open and parse the docx
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                    new java.io.FileInputStream(docxFile));
            Map<String, byte[]> rawEntries = new HashMap<>();
            Map<String, Document> xmlEntries = new HashMap<>();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            byte[] buffer = new byte[8192];
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int len;
                while ((len = zis.read(buffer)) > 0) baos.write(buffer, 0, len);
                byte[] data = baos.toByteArray();
                if (name.endsWith(".xml") || name.endsWith(".rels")) {
                    try {
                        xmlEntries.put(name, builder.parse(new ByteArrayInputStream(data)));
                    } catch (Exception e) {
                        rawEntries.put(name, data);
                    }
                } else {
                    rawEntries.put(name, data);
                }
            }
            zis.close();

            Document docXml = xmlEntries.get("word/document.xml");
            Document relsDoc = xmlEntries.get("word/_rels/document.xml.rels");
            if (docXml == null || relsDoc == null) {
                Log.i(TAG, "extract_image_text_try_odf");
                return extractFromOdf(rawEntries, xmlEntries);
            }

            // 2. Build rId → media path mapping from rels
            Map<String, String> relMap = new HashMap<>();
            NodeList relChildren = relsDoc.getDocumentElement().getChildNodes();
            for (int i = 0; i < relChildren.getLength(); i++) {
                Node n = relChildren.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    Element e = (Element) n;
                    String id = e.getAttribute("Id");
                    String type = e.getAttribute("Type");
                    String target = e.getAttribute("Target");
                    if (id != null && target != null
                            && type != null && type.contains("image")) {
                        relMap.put(id, target);
                    }
                }
            }

            if (relMap.isEmpty()) {
                Log.i(TAG, "extract_image_text_no_images_in_docx");
                return null; // No images → return null to use old path
            }

            // 3. Walk paragraphs, build marked text + image map
            Element body = docXml.getDocumentElement();
            NodeList bodies = docXml.getElementsByTagNameNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "body");
            if (bodies.getLength() == 0) {
                Log.w(TAG, "extract_image_text_no_body");
                return null;
            }
            Element bodyEl = (Element) bodies.item(0);

            StringBuilder markedText = new StringBuilder();
            Map<String, TypesetImageEntry> imageMap = new LinkedHashMap<>();
            int imgCounter = 0;
            // Save per-paragraph data for paragraph-level classification
            java.util.List<String> paraTexts = new java.util.ArrayList<>();
            java.util.List<java.util.List<String>> paraMarkersList = new java.util.ArrayList<>();

            NodeList pList = bodyEl.getChildNodes();
            for (int i = 0; i < pList.getLength(); i++) {
                Node node = pList.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                Element pElem = (Element) node;
                String localName = pElem.getLocalName();
                if (localName == null || !localName.equals("p")) continue;

                // Collect text runs, check for drawings
                StringBuilder paraText = new StringBuilder();
                java.util.List<String> paraImages = new java.util.ArrayList<>();
                java.util.List<long[]> paraSizes = new java.util.ArrayList<>();

                NodeList runs = pElem.getChildNodes();
                for (int j = 0; j < runs.getLength(); j++) {
                    Node rNode = runs.item(j);
                    if (rNode.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element rElem = (Element) rNode;
                    String rLocal = rElem.getLocalName();
                    if (rLocal == null) continue;

                    if ("r".equals(rLocal)) {
                        // Collect text from <w:t>
                        NodeList tList = rElem.getElementsByTagNameNS(
                                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "t");
                        for (int t = 0; t < tList.getLength(); t++) {
                            Node tNode = tList.item(t);
                            if (tNode.getTextContent() != null) {
                                paraText.append(tNode.getTextContent());
                            }
                        }
                    } else if ("drawing".equals(rLocal)) {
                        // Check for image drawing
                        String imgRId = findImageRId(rElem);
                        if (imgRId != null && relMap.containsKey(imgRId)) {
                            imgCounter++;
                            String marker = "图" + imgCounter;
                            long[] size = findImageSize(rElem);

                            // Get image data from raw entries
                            String mediaPath = relMap.get(imgRId);
                            byte[] imgData = rawEntries.get(mediaPath);
                            if (imgData == null) {
                                // Try with "word/" prefix if target is relative
                                String fullPath = mediaPath.startsWith("word/") ? mediaPath : "word/" + mediaPath;
                                imgData = rawEntries.get(fullPath);
                                // Also try without "word/" prefix
                                if (imgData == null && mediaPath.startsWith("word/media/")) {
                                    imgData = rawEntries.get(mediaPath);
                                }
                            }

                            if (imgData != null) {
                                String ext = getExtension(mediaPath);
                                String mime = getMimeFromExtension(ext);
                                imageMap.put(marker, new TypesetImageEntry(
                                        marker, imgData, mime, ext, size[0], size[1]));
                                paraImages.add(marker);
                                paraSizes.add(size);
                            } else {
                                Log.w(TAG, "extract_image_data_missing rId=" + imgRId + " path=" + mediaPath);
                            }
                        } else {
                            // Non-image drawing (shape etc.) — keep text as-is
                            NodeList tList = rElem.getElementsByTagNameNS(
                                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "t");
                            for (int t = 0; t < tList.getLength(); t++) {
                                Node tNode = tList.item(t);
                                if (tNode.getTextContent() != null) {
                                    paraText.append(tNode.getTextContent());
                                }
                            }
                        }
                    }
                }

                // Append paragraph: text then image markers
                String para = paraText.toString().trim();
                if (para.isEmpty() && paraImages.isEmpty()) continue;

                // Save per-paragraph data for paragraph-level classification
                paraTexts.add(para);
                paraMarkersList.add(new java.util.ArrayList<>(paraImages));

                if (!para.isEmpty()) {
                    markedText.append(para);
                }
                for (String marker : paraImages) {
                    if (markedText.length() > 0
                            && markedText.charAt(markedText.length() - 1) != '\n') {
                        // Small separator between trailing text and marker
                    }
                    markedText.append("[").append(marker).append("]");
                }
                markedText.append("\n\n");
            }

            if (imageMap.isEmpty()) {
                if (markedText.length() == 0) {
                    Log.i(TAG, "extract_image_text_no_content");
                    return null;
                }
                Log.i(TAG, "extract_image_text_text_only paragraphs="
                        + (paraTexts != null ? paraTexts.size() : 0));
                // No images found in body w:drawing elements (may be in headers/
                // footers/VML etc.) — proceed with text-only paragraph data.
            }

            // Store image map and paragraph data as fields
            pendingTypesetImages = imageMap;
            pendingTypesetParagraphs = paraTexts.isEmpty() ? null : paraTexts;
            pendingParaImageMarkers = paraMarkersList.isEmpty() ? null : paraMarkersList;
            String result = markedText.toString().trim();
            Log.i(TAG, "extract_image_text_done markers=" + imageMap.size()
                    + " textChars=" + result.length()
                    + " paragraphs=" + (paraTexts != null ? paraTexts.size() : 0));
            return result;

        } catch (Exception e) {
            Log.w(TAG, "extract_image_text_failed", e);
            return null;
        }
    }

    // ---- Helpers for extractFullTextWithImagesNative ----

    /**
     * Find the r:embed attribute value inside a &lt;w:drawing&gt; element.
     * Looks for {@code <a:blip r:embed="rIdX">}.
     */
    private static String findImageRId(Element drawingElem) {
        NodeList blips = drawingElem.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/drawingml/2006/main", "blip");
        for (int i = 0; i < blips.getLength(); i++) {
            Element blip = (Element) blips.item(i);
            String embed = blip.getAttributeNS(
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed");
            if (embed != null && !embed.isEmpty()) return embed;
            // Fallback: try non-namespace qualified
            String rEmbed = blip.getAttribute("r:embed");
            if (rEmbed != null && !rEmbed.isEmpty()) return rEmbed;
        }
        return null;
    }

    /**
     * Find image size from &lt;wp:extent&gt; inside a &lt;w:drawing&gt;.
     * Returns {@code [cx, cy]} in EMU, or {@code [0, 0]} if not found.
     */
    private static long[] findImageSize(Element drawingElem) {
        NodeList extents = drawingElem.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing", "extent");
        for (int i = 0; i < extents.getLength(); i++) {
            Element ext = (Element) extents.item(i);
            try {
                long cx = Long.parseLong(ext.getAttribute("cx"));
                long cy = Long.parseLong(ext.getAttribute("cy"));
                return new long[]{cx, cy};
            } catch (NumberFormatException ignored) {}
        }
        return new long[]{0, 0};
    }

    private static String getExtension(String mediaPath) {
        if (mediaPath == null) return "png";
        int dot = mediaPath.lastIndexOf('.');
        if (dot < 0) return "png";
        String ext = mediaPath.substring(dot + 1).toLowerCase();
        if (ext.isEmpty()) return "png";
        return ext;
    }

    private static String getMimeFromExtension(String ext) {
        switch (ext) {
            case "png":  return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif":  return "image/gif";
            case "bmp":  return "image/bmp";
            case "svg":  return "image/svg+xml";
            case "webp": return "image/webp";
            case "wmf":  return "image/x-wmf";
            case "emf":  return "image/x-emf";
            default:     return "image/png";
        }
    }

    // ==================== ODF extraction for typeset ====================

    /**
     * Extract text + images from an ODF format document (ZIP containing content.xml + Pictures/).
     * Called when docx format detection fails (word/document.xml not found).
     *
     * ODF structure:
     *   <text:p> - paragraph with text content
     *   <draw:frame svg:width="13.776cm" svg:height="13.751cm">
     *     <draw:image xlink:href="Pictures/10000000.jpg"/>
     *   </draw:frame>
     *
     * @param rawEntries  all non-XML ZIP entries (contains Pictures/ files)
     * @param xmlEntries  parsed XML ZIP entries (contains content.xml)
     * @return marked text with [图N] placeholders, or null on failure
     */
    @Nullable
    private String extractFromOdf(Map<String, byte[]> rawEntries, Map<String, Document> xmlEntries) {
        // 1. Collect Pictures/ images
        Map<String, byte[]> pictures = new HashMap<>();
        for (Map.Entry<String, byte[]> e : rawEntries.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("Pictures/") || key.toLowerCase(Locale.ROOT).startsWith("pictures/")) {
                pictures.put(key, e.getValue());
            }
        }

        // 2. Parse content.xml
        Document contentXml = xmlEntries.get("content.xml");
        if (contentXml == null) {
            Log.i(TAG, "extract_image_text_no_content_xml");
            return null;
        }

        // 3. Walk paragraphs — try namespace-aware then fallback
        final String TEXT_NS = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
        final String DRAW_NS = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0";
        final String XLINK_NS = "http://www.w3.org/1999/xlink";
        final String SVG_NS = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0";

        NodeList pList = contentXml.getElementsByTagNameNS(TEXT_NS, "p");
        // Fallback to non-namespace tag name (some parsers strip ns)
        if (pList.getLength() == 0) {
            pList = contentXml.getElementsByTagName("text:p");
            if (pList.getLength() == 0) {
                Log.i(TAG, "extract_image_text_no_odf_paragraphs");
                return null;
            }
        }

        StringBuilder markedText = new StringBuilder();
        Map<String, TypesetImageEntry> imageMap = new LinkedHashMap<>();
        java.util.List<String> paraTexts = new java.util.ArrayList<>();
        java.util.List<java.util.List<String>> paraMarkersList = new java.util.ArrayList<>();
        int imgCounter = 0;

        for (int i = 0; i < pList.getLength(); i++) {
            Element pElem = (Element) pList.item(i);
            StringBuilder paraText = new StringBuilder();
            java.util.List<String> paraImages = new java.util.ArrayList<>();

            NodeList children = pElem.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() == Node.TEXT_NODE) {
                    String txt = child.getTextContent();
                    if (txt != null) paraText.append(txt);
                } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childEl = (Element) child;
                    String ns = childEl.getNamespaceURI();
                    String localName = childEl.getLocalName();

                    if (TEXT_NS.equals(ns) && "span".equals(localName)) {
                        if (childEl.getTextContent() != null)
                            paraText.append(childEl.getTextContent());
                    } else if (DRAW_NS.equals(ns) && "frame".equals(localName)) {
                        // Extract image dimensions from draw:frame
                        long[] size = parseOdfFrameSize(childEl, SVG_NS);
                        // Find draw:image inside
                        NodeList images = childEl.getElementsByTagNameNS(DRAW_NS, "image");
                        if (images.getLength() == 0)
                            images = childEl.getElementsByTagName("draw:image");
                        for (int k = 0; k < images.getLength(); k++) {
                            Element imgEl = (Element) images.item(k);
                            String href = imgEl.getAttributeNS(XLINK_NS, "href");
                            if (href == null) href = imgEl.getAttribute("xlink:href");
                            if (href == null || href.isEmpty()) continue;

                            byte[] imgData = findPictureData(pictures, href);
                            if (imgData != null) {
                                imgCounter++;
                                String marker = "图" + imgCounter;
                                String ext = getExtension(href);
                                String mime = getMimeFromExtension(ext);
                                imageMap.put(marker,
                                        new TypesetImageEntry(marker, imgData, mime, ext, size[0], size[1]));
                                paraImages.add(marker);
                            } else {
                                Log.w(TAG, "extract_odf_image_not_found href=" + href);
                            }
                        }
                    }
                }
            }

            String para = paraText.toString().trim();
            if (para.isEmpty() && paraImages.isEmpty()) continue;

            paraTexts.add(para);
            paraMarkersList.add(new java.util.ArrayList<>(paraImages));

            if (!para.isEmpty()) {
                markedText.append(para);
            }
            for (String marker : paraImages) {
                markedText.append("[").append(marker).append("]");
            }
            markedText.append("\n\n");
        }

        if (paraTexts.isEmpty()) {
            Log.i(TAG, "extract_image_text_odf_empty");
            return null;
        }

        pendingTypesetImages = imageMap.isEmpty() ? null : imageMap;
        pendingTypesetParagraphs = paraTexts;
        pendingParaImageMarkers = paraMarkersList;

        String result = markedText.toString().trim();
        Log.i(TAG, "extract_image_text_done markers=" + imageMap.size()
                + " textChars=" + result.length()
                + " paragraphs=" + paraTexts.size());
        return result;
    }

    /** Parse ODF dimension string like "13.776cm" to EMU. Returns 0 on failure. */
    private static long parseOdfDimension(String dim) {
        if (dim == null || dim.isEmpty()) return 0;
        dim = dim.trim().toLowerCase(Locale.ROOT);
        try {
            if (dim.endsWith("cm")) {
                return (long) (Double.parseDouble(dim.substring(0, dim.length() - 2)) * 360000L);
            } else if (dim.endsWith("in")) {
                return (long) (Double.parseDouble(dim.substring(0, dim.length() - 2)) * 914400L);
            } else if (dim.endsWith("mm")) {
                return (long) (Double.parseDouble(dim.substring(0, dim.length() - 2)) * 36000L);
            } else if (dim.endsWith("pt")) {
                return (long) (Double.parseDouble(dim.substring(0, dim.length() - 2)) * 12700L);
            } else if (dim.endsWith("px")) {
                return (long) (Double.parseDouble(dim.substring(0, dim.length() - 2)) * 9144L);
            } else {
                return (long) Double.parseDouble(dim);
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Get [cx, cy] in EMU from draw:frame svg:width / svg:height attributes. */
    private static long[] parseOdfFrameSize(Element frameEl, String svgNs) {
        long cx = 0, cy = 0;
        String w = frameEl.getAttributeNS(svgNs, "width");
        String h = frameEl.getAttributeNS(svgNs, "height");
        if (w == null) w = frameEl.getAttribute("svg:width");
        if (h == null) h = frameEl.getAttribute("svg:height");
        if (w != null && !w.isEmpty()) cx = parseOdfDimension(w);
        if (h != null && !h.isEmpty()) cy = parseOdfDimension(h);
        return new long[]{cx, cy};
    }

    /** Look up image data by href, handling case variations and path prefix differences. */
    @Nullable
    private static byte[] findPictureData(Map<String, byte[]> pictures, String href) {
        if (href == null) return null;
        byte[] data = pictures.get(href);
        if (data != null) return data;
        // Normalise and retry
        String normalised = href.replace('\\', '/');
        String lowerHref = normalised.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, byte[]> e : pictures.entrySet()) {
            String keyLower = e.getKey().toLowerCase(Locale.ROOT);
            if (keyLower.equals(lowerHref) || keyLower.endsWith(lowerHref)) {
                return e.getValue();
            }
        }
        return null;
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
        typesetInProgress = true;

        // 立即弹出预览弹窗（加载态），避免用户等待无反馈
        showTypesetLoadingOverlay();

        // 全文提取：优先从 docx 提取（含图片标记），退化到 JNI 纯文本
        new Thread(() -> {
            // Force LOKit to flush current in-memory state (including images user
            // inserted via open_local_image_picker) to mTempFile on disk, so
            // extractFullTextWithImagesNative reads the latest document state.
            runOnUiThread(() -> postUnoCommand(".uno:Save", "{}", false));
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            pendingTypesetImages = null;
            pendingTypesetParagraphs = null;
            pendingParaImageMarkers = null;
            pendingTypesetParagraphMode = false;
            String extracted = extractFullTextWithImagesNative();
            if (extracted == null || extracted.isEmpty()) {
                extracted = extractFullTextNative("typeset-" + typesetType);
            }
            // 段落分类模式：extractFullTextWithImagesNative 成功且含多段内容时使用
            final boolean hasParagraphs = (pendingTypesetParagraphs != null
                    && pendingTypesetParagraphs.size() > 1);
            pendingTypesetParagraphMode = hasParagraphs;
            if (hasParagraphs) {
                Log.i(TAG, "ai_typeset_paragraph_mode paragraphs=" + pendingTypesetParagraphs.size());
            }
            final String docText = extracted;
            if (docText == null || docText.isEmpty()) {
                runOnUiThread(() -> {
                    typesetInProgress = false;
                    dismissTypesetPreviewOverlay();
                    toastTodo("文档全文提取失败，请稍后重试");
                    Log.w(TAG, "ai_typeset_doc_extract_failed");
                });
                return;
            }
            Log.i(TAG, "ai_typeset_doc_extracted chars=" + docText.length());

            runOnUiThread(() -> {
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
                    typesetInProgress = false;
                    dismissTypesetPreviewOverlay();
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
        if (isFinishing()) {
            return;
        }
        pendingTypesetHtml = cleaned;

        // If content is not HTML, wrap in <pre> so user can still see the result
        String displayContent = cleaned;
        if (!isLikelyHtml(cleaned)) {
            Log.w(TAG, "ai_typeset_preview_not_html — wrapping in <pre>");
            displayContent = "<pre style=\"white-space:pre-wrap;word-break:break-all;font-size:13px;\">"
                    + escapeHtml(cleaned) + "</pre>";
        }

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
                    "</style></head><body>" + (displayContent != null ? displayContent : "") + "</body></html>";
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

    // ==================== AI排版 V2 — docx 模板填充方法 ====================

    /**
     * Build sections map from paragraph classification response.
     * Groups original paragraphs by their assigned section key.
     */
    @Nullable
    private Map<String, String> buildTypesetSectionsFromParagraphs(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isEmpty()) return null;
        java.util.List<AiChatCoordinator.ParaSection> paraSections =
                AiChatCoordinator.parseTypesetParagraphResult(jsonResponse);
        if (paraSections == null || paraSections.isEmpty()) {
            Log.w(TAG, "typeset_paragraph_parse_failed");
            return null;
        }
        if (pendingTypesetParagraphs == null || pendingTypesetParagraphs.isEmpty()) {
            Log.w(TAG, "typeset_paragraph_no_para_data");
            return null;
        }

        // Group paragraph indices by section key
        java.util.Map<String, java.util.List<Integer>> sectionToParas = new java.util.LinkedHashMap<>();
        for (AiChatCoordinator.ParaSection ps : paraSections) {
            if (ps.paraIndex < 0 || ps.paraIndex >= pendingTypesetParagraphs.size()) continue;
            sectionToParas.computeIfAbsent(ps.section, k -> new java.util.ArrayList<>())
                    .add(ps.paraIndex);
        }

        // Build section content from original paragraph text (with [图N] markers)
        java.util.Map<String, String> sections = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, java.util.List<Integer>> entry : sectionToParas.entrySet()) {
            StringBuilder content = new StringBuilder();
            for (int pi : entry.getValue()) {
                String paraText = pendingTypesetParagraphs.get(pi);
                // Prepend image markers to paragraph text
                java.util.List<String> markers = (pendingParaImageMarkers != null
                        && pi < pendingParaImageMarkers.size())
                        ? pendingParaImageMarkers.get(pi) : new java.util.ArrayList<>();
                StringBuilder fullPara = new StringBuilder();
                for (String m : markers) {
                    fullPara.append("[").append(m).append("]");
                }
                fullPara.append(paraText);
                String trimmed = fullPara.toString().trim();
                if (trimmed.isEmpty()) continue;
                if (content.length() > 0) content.append("\n\n");
                content.append(trimmed);
            }
            String sectionContent = content.toString().trim();
            if (!sectionContent.isEmpty()) {
                sections.put(entry.getKey(), sectionContent);
            }
        }

        Log.i(TAG, "typeset_paragraph_sections_built sections=" + sections.size());
        return sections.isEmpty() ? null : sections;
    }

    /**
     * Handle V2 typeset result: fill docx template with AI sections, show preview overlay.
     */
    private void handleTypesetV2Result(Map<String, String> sections) {
        if (sections == null || sections.isEmpty()) {
            Log.e(TAG, "ai_typeset_v2_empty_sections");
            toastTodo("排版结果为空");
            return;
        }
        String type = pendingTypesetType != null ? pendingTypesetType : "general";
        Log.i(TAG, "ai_typeset_v2_filling type=" + type + " sections=" + sections.size());

        // Resolve template resource ID
        int resId = getTypesetTemplateResId(type);

        // Fill template docx on background thread
        new Thread(() -> {
            String srcName = getFileName(false);
            File filledDocx = DocxTemplateFiller.fillTemplate(resId, type, sections, LOActivity.this, srcName);
            // Post-process: insert source images if any
            if (filledDocx != null && pendingTypesetImages != null
                    && !pendingTypesetImages.isEmpty()) {
                DocxImageInserter.insertImages(filledDocx, pendingTypesetImages);
            }
            runOnUiThread(() -> {
                if (filledDocx != null && filledDocx.exists()) {
                    pendingTypesetDocx = filledDocx;
                    pendingTypesetSections = sections;
                    typesetInProgress = false;
                    Log.i(TAG, "ai_typeset_v2_filled path=" + filledDocx.getAbsolutePath()
                            + " size=" + filledDocx.length());
                    // Overlay already visible from loading state — just update WebView content
                    String previewHtml = buildPreviewHtml(type, sections);
                    if (typesetPreviewWebView != null && previewHtml != null) {
                        typesetPreviewWebView.loadDataWithBaseURL(null, previewHtml,
                                "text/html", "UTF-8", null);
                    }
                } else {
                    typesetInProgress = false;
                    Log.e(TAG, "ai_typeset_v2_fill_failed — falling back to HTML paste");
                    toastTodo("模板填充失败，使用HTML方式");
                    // Fallback: if we have pendingTypesetHtml, use old flow
                    if (pendingTypesetHtml != null && !pendingTypesetHtml.isEmpty()) {
                        showTypesetPreviewSheet(pendingTypesetHtml);
                    }
                }
            });
        }, "cool-ai-typeset-fill").start();
    }

    private int getTypesetTemplateResId(String type) {
        switch (type) {
            case "paper":    return R.raw.typeset_template_paper;
            case "gov":      return R.raw.typeset_template_gov;
            case "contract": return R.raw.typeset_template_contract;
            case "general":  return R.raw.typeset_template_general;
            default:         return R.raw.typeset_template_general;
        }
    }

    /**
     * Show the typeset preview overlay immediately with a loading message.
     * Called at the start of typeset so the user sees immediate feedback.
     */
    private void showTypesetLoadingOverlay() {
        if (typesetPreviewOverlay == null) {
            Log.w(TAG, "ai_typeset_v2_overlay_null — cannot show loading");
            return;
        }
        // Load "正在排版" placeholder into WebView
        if (typesetPreviewWebView != null) {
            String loadingHtml = "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                    + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">"
                    + "<style>body{font-family:sans-serif;display:flex;align-items:center;"
                    + "justify-content:center;height:100vh;margin:0;color:#999;font-size:16px;}"
                    + ".spinner{width:32px;height:32px;border:3px solid #e0e0e0;border-top:3px solid #2B7AFF;"
                    + "border-radius:50%;animation:spin 0.8s linear infinite;margin-bottom:12px;}"
                    + "@keyframes spin{to{transform:rotate(360deg)}}"
                    + ".wrap{text-align:center;}</style></head><body>"
                    + "<div class=\"wrap\"><div class=\"spinner\"></div>"
                    + "<p>AI 正在排版，请稍候…</p></div></body></html>";
            typesetPreviewWebView.loadDataWithBaseURL(null, loadingHtml, "text/html", "UTF-8", null);
        }
        typesetPreviewOverlay.post(() -> {
            positionTypesetPreviewOverlay();
            typesetPreviewOverlay.setVisibility(View.VISIBLE);
        });
        Log.i(TAG, "ai_typeset_v2_loading_shown");
    }

    /**
     * Initialize the typeset preview overlay — find views, wire listeners.
     */
    private void setupTypesetPreviewOverlay() {
        // The <include> tag id overrides the root view id of the included layout.
        // So we find by the include id, which IS the overlay FrameLayout.
        typesetPreviewOverlay = findViewById(R.id.typeset_preview_overlay_include);
        if (typesetPreviewOverlay == null) {
            Log.w(TAG, "ai_typeset_v2_overlay_not_found — layout include missing?");
            return;
        }

        // Find children within the overlay
        typesetPreviewCard = typesetPreviewOverlay.findViewById(R.id.typeset_preview_card);
        typesetPreviewWebView = typesetPreviewOverlay.findViewById(R.id.typeset_preview_webview);

        // Configure WebView
        if (typesetPreviewWebView != null) {
            WebSettings ws = typesetPreviewWebView.getSettings();
            ws.setJavaScriptEnabled(false);
            ws.setBuiltInZoomControls(true);
            ws.setDisplayZoomControls(false);
            ws.setLoadWithOverviewMode(true);
            ws.setUseWideViewPort(false);
        }

        // Overlay background is NOT dismissible on click —
        // accidental taps outside the card could close it before the user
        // has reviewed or inserted the result. Use the close button instead.
        typesetPreviewOverlay.setClickable(true);

        // Prevent card click from propagating to overlay
        if (typesetPreviewCard != null) {
            typesetPreviewCard.setClickable(true);
        }

        // Close button
        View closeBtn = typesetPreviewOverlay.findViewById(R.id.typeset_preview_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> dismissTypesetPreviewOverlay());
        }

        // Regenerate button
        View regenBtn = typesetPreviewOverlay.findViewById(R.id.typeset_preview_regenerate);
        if (regenBtn != null) {
            regenBtn.setOnClickListener(v -> {
                Log.i(TAG, "ai_typeset_v2_regenerate");
                dismissTypesetPreviewOverlay();
                String type = pendingTypesetType != null ? pendingTypesetType : "general";
                startTypeset(type);
            });
        }

        // Insert document button
        View insertBtn = typesetPreviewOverlay.findViewById(R.id.typeset_preview_insert);
        if (insertBtn != null) {
            insertBtn.setOnClickListener(v -> {
                Log.i(TAG, "ai_typeset_v2_insert_document");
                if (pendingTypesetDocx != null && pendingTypesetDocx.exists()) {
                    openTypesetDocument(pendingTypesetDocx);
                } else {
                    toastTodo("排版文档不存在，请重新生成");
                }
            });
        }
    }

    /**
     * Show the typeset preview overlay with the filled template content.
     */
    private void showTypesetPreviewOverlay(String typesetType, Map<String, String> sections,
                                            File docxFile) {
        if (typesetPreviewOverlay == null) {
            Log.e(TAG, "ai_typeset_v2_overlay_null");
            return;
        }

        // Build preview HTML and load into WebView
        String previewHtml = buildPreviewHtml(typesetType, sections);
        if (typesetPreviewWebView != null && previewHtml != null) {
            typesetPreviewWebView.loadDataWithBaseURL(null, previewHtml,
                    "text/html", "UTF-8", null);
        }

        // Position and show
        typesetPreviewOverlay.post(() -> {
            positionTypesetPreviewOverlay();
            typesetPreviewOverlay.setVisibility(View.VISIBLE);
        });

        Log.i(TAG, "ai_typeset_v2_preview_shown type=" + typesetType
                + " docxSize=" + docxFile.length());
    }

    /**
     * Dismiss the typeset preview overlay.
     */
    private void dismissTypesetPreviewOverlay() {
        typesetInProgress = false;
        pendingTypesetImages = null;
        pendingTypesetParagraphs = null;
        pendingParaImageMarkers = null;
        pendingTypesetParagraphMode = false;
        if (typesetPreviewOverlay != null) {
            typesetPreviewOverlay.setVisibility(View.GONE);
        }
        // Clean up WebView content
        if (typesetPreviewWebView != null) {
            typesetPreviewWebView.loadUrl("about:blank");
        }
        Log.i(TAG, "ai_typeset_v2_preview_dismissed");
    }

    /**
     * Size and center the preview card within the overlay.
     */
    private void positionTypesetPreviewOverlay() {
        if (typesetPreviewCard == null || typesetPreviewOverlay == null) return;

        int parentWidth = typesetPreviewOverlay.getWidth();
        int parentHeight = typesetPreviewOverlay.getHeight();
        if (parentWidth == 0 || parentHeight == 0) return;

        int dp48 = (int) (48 * getResources().getDisplayMetrics().density);
        int dp16 = (int) (16 * getResources().getDisplayMetrics().density);

        int cardWidth = Math.min(parentWidth - dp48, (int) (670 * getResources().getDisplayMetrics().density));
        int maxCardHeight = parentHeight - dp16 * 2;
        int cardHeight = Math.min(maxCardHeight, (int) (1320 * getResources().getDisplayMetrics().density));
        // Ensure minimum height, but never exceed the container
        cardHeight = Math.max(cardHeight, (int) (400 * getResources().getDisplayMetrics().density));
        cardHeight = Math.min(cardHeight, maxCardHeight);

        ViewGroup.LayoutParams lp = typesetPreviewCard.getLayoutParams();
        lp.width = cardWidth;
        lp.height = cardHeight;
        typesetPreviewCard.setLayoutParams(lp);
    }

    /**
     * Build an HTML preview page from the AI-generated sections map.
     * This is an approximation for preview only — the real formatting comes
     * from the docx template when opened in LOActivity.
     */
    private String buildPreviewHtml(String typesetType, Map<String, String> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">");
        sb.append("<style>");
        sb.append("body{font-family:'Noto Sans SC','PingFang SC','Microsoft YaHei',sans-serif;");
        sb.append("padding:0 16px;color:#333;line-height:1.8;font-size:15px;word-break:break-word;}");
        sb.append("h2{text-align:center;font-size:20px;margin:16px 0;}");
        sb.append("h3{font-size:17px;margin:12px 0;border-bottom:1px solid #eee;padding-bottom:6px;}");
        sb.append("h4{font-size:15px;margin:8px 0;}");
        sb.append("p{margin:6px 0;text-indent:0;}");
        sb.append(".abstract{background:#f5f5f5;padding:12px;border-radius:8px;margin:8px 0;font-size:14px;}");
        sb.append(".gov-header{text-align:center;color:#c00;font-size:22px;font-weight:bold;margin:12px 0;}");
        sb.append(".gov-subheader{text-align:center;font-size:16px;font-weight:bold;margin:8px 0;}");
        sb.append(".contract-label{font-weight:bold;color:#555;}");
        sb.append(".signature{text-align:right;margin-top:24px;}");
        sb.append("</style></head><body>");

        String title = sections.getOrDefault("title", "");
        if (!title.isEmpty()) {
            sb.append("<h2>").append(escapeHtml(title)).append("</h2>");
        }

        java.util.Set<String> rendered = new java.util.HashSet<>();
        if (!title.isEmpty()) rendered.add("title");

        switch (typesetType) {
            case "paper":
                appendPaperSections(sb, sections, rendered);
                break;
            case "gov":
                appendGovSections(sb, sections, rendered);
                break;
            case "contract":
                appendContractSections(sb, sections, rendered);
                break;
            case "general":
            default:
                appendGeneralSections(sb, sections, rendered);
                break;
        }

        // Append any AI-generated extra sections not in the template map
        for (Map.Entry<String, String> e : sections.entrySet()) {
            if (!rendered.contains(e.getKey())) {
                sb.append("<p>").append(escapeHtml(e.getValue())).append("</p>");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendPaperSections(StringBuilder sb, Map<String, String> s, Set<String> rendered) {
        String abs = s.get("abstract");
        if (abs != null && !abs.isEmpty()) {
            sb.append("<div class=\"abstract\"><strong>摘要：</strong>")
                    .append(escapeHtml(abs)).append("</div>");
            rendered.add("abstract");
        }
        String kw = s.get("keywords");
        if (kw != null && !kw.isEmpty()) {
            sb.append("<p><strong>关键词：</strong>").append(escapeHtml(kw)).append("</p>");
            rendered.add("keywords");
        }
        appendSection(sb, s, "introduction", "引言", rendered);
        appendSection(sb, s, "heading1", null, rendered);
        appendSection(sb, s, "heading2", null, rendered);
        appendSection(sb, s, "heading3", null, rendered);
        appendSection(sb, s, "body", null, rendered);
        appendSection(sb, s, "conclusion_body", "结语", rendered);
        appendSection(sb, s, "ack_body", "致谢", rendered);
    }

    private void appendGovSections(StringBuilder sb, Map<String, String> s, Set<String> rendered) {
        String recipient = s.get("recipient");
        if (recipient != null && !recipient.isEmpty()) {
            sb.append("<p>").append(escapeHtml(recipient)).append("</p>");
            rendered.add("recipient");
        }
        appendSection(sb, s, "body", null, rendered);
        String sigOrg = s.get("signature_org");
        String sigDate = s.get("signature_date");
        if ((sigOrg != null && !sigOrg.isEmpty()) || (sigDate != null && !sigDate.isEmpty())) {
            sb.append("<div class=\"signature\">");
            if (sigOrg != null) sb.append("<p>").append(escapeHtml(sigOrg)).append("</p>");
            if (sigDate != null) sb.append("<p>").append(escapeHtml(sigDate)).append("</p>");
            sb.append("</div>");
        }
        if (sigOrg != null && !sigOrg.isEmpty()) rendered.add("signature_org");
        if (sigDate != null && !sigDate.isEmpty()) rendered.add("signature_date");
        String notes = s.get("notes");
        if (notes != null && !notes.isEmpty()) {
            sb.append("<p style=\"font-size:13px;color:#888;\">").append(escapeHtml(notes)).append("</p>");
            rendered.add("notes");
        }
    }

    private void appendContractSections(StringBuilder sb, Map<String, String> s, Set<String> rendered) {
        String contractNum = s.get("contract_number");
        if (contractNum != null && !contractNum.isEmpty()) {
            sb.append("<p>").append(escapeHtml(contractNum)).append("</p>");
            rendered.add("contract_number");
        }
        appendContractParty(sb, s, "party_a", "甲方", rendered);
        appendContractParty(sb, s, "party_a_id", null, rendered);
        appendContractParty(sb, s, "party_b", "乙方", rendered);
        appendContractParty(sb, s, "party_b_id", null, rendered);
        appendSection(sb, s, "preamble", null, rendered);
        appendSection(sb, s, "clause_title", null, rendered);
        appendSection(sb, s, "clause_subtitle", null, rendered);
        appendSection(sb, s, "clause_body", null, rendered);
    }

    private void appendContractParty(StringBuilder sb, Map<String, String> s, String key, String label, Set<String> rendered) {
        String val = s.get(key);
        if (val != null && !val.isEmpty()) {
            sb.append("<p>").append(escapeHtml(val)).append("</p>");
        }
        rendered.add(key);
    }

    private void appendGeneralSections(StringBuilder sb, Map<String, String> s, Set<String> rendered) {
        appendSection(sb, s, "heading1", null, rendered);
        appendSection(sb, s, "heading2", null, rendered);
        appendSection(sb, s, "heading3", null, rendered);
        appendSection(sb, s, "body", null, rendered);
    }

    private void appendSection(StringBuilder sb, Map<String, String> s, String key, String fallbackLabel, Set<String> rendered) {
        String val = s.get(key);
        if (val == null || val.isEmpty()) return;
        rendered.add(key);
        // If value looks like a heading (short, single line), render as h3/h4
        String trimmed = val.trim();
        if (!trimmed.contains("\n") && trimmed.length() < 60) {
            sb.append("<h4>").append(escapeHtml(trimmed)).append("</h4>");
        } else {
            // Split by double newline into paragraphs
            String[] paras = trimmed.split("\n\n");
            for (String para : paras) {
                String p = para.trim();
                if (!p.isEmpty()) {
                    sb.append("<p>").append(escapeHtml(p)).append("</p>");
                }
            }
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Open the filled docx file by switching the current LOActivity
     * to the new document via the standard onNewIntent path.
     *
     * <p>The standard document-switch flow:
     * startActivity → onNewIntent (singleTask) → save old doc → BYE → init() →
     * initUI() → loadDocument() → createCOOLWSD.</p>
     *
     * <p>We do NOT send BYE ourselves — that would trigger the native BYE
     * response → beforeMessageFromWebView("BYE") → finishWithProgress() →
     * finishAndRemoveTask(), killing the Activity before onNewIntent runs.</p>
     */
    private void openTypesetDocument(File docxFile) {
        if (docxFile == null || !docxFile.exists()) {
            toastTodo("排版文档不存在");
            return;
        }
        try {
            Uri typesetUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", docxFile);

            Log.i(TAG, "ai_typeset_v2_open_document uri=" + typesetUri.toString()
                    + " path=" + docxFile.getAbsolutePath());

            RecentDocumentsStore.prependRecent(
                    getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE),
                    typesetUri.toString());

            // Dismiss the preview overlay before starting the switch
            typesetInProgress = false;
            if (typesetPreviewOverlay != null) {
                typesetPreviewOverlay.setVisibility(View.GONE);
            }
            if (typesetPreviewWebView != null) {
                typesetPreviewWebView.loadUrl("about:blank");
            }

            // startActivity triggers onNewIntent (singleTask), which handles
            // save → BYE → init → loadDocument — the standard document switch.
            // No finish() — the same LOActivity instance handles the switch.
            startActivity(buildEditIntent(typesetUri));

        } catch (Exception e) {
            Log.e(TAG, "ai_typeset_v2_open_failed: " + e.getMessage(), e);
            toastTodo("打开排版文档失败：" + e.getMessage());
        }
    }

    // ==================== AI排版 V2 方法结束 ====================

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
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_outline_v2, null);
        outlineDialogRoot = root;

        outlineTypeLabel = root.findViewById(R.id.outline_type_label);
        outlineTypeCard = root.findViewById(R.id.outline_type_card);
        outlineDescEdit = root.findViewById(R.id.outline_desc_edit);
        outlineResultText = root.findViewById(R.id.outline_result_text);
        outlineDescCard = root.findViewById(R.id.outline_desc_card);
        outlineResultCard = root.findViewById(R.id.outline_result_card);
        outlineResultScroll = root.findViewById(R.id.outline_result_scroll);
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
     * 约束弹窗宽度，高度随内容变化（上限屏高 80%）。
     */
    private void applyFlexibleDialogSize(AlertDialog dialog, View root, String logTag) {
        if (dialog == null || dialog.getWindow() == null || root == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int margin = dpToPx(48);
        int targetWidth = Math.min(dpToPx(670), dm.widthPixels - margin);
        targetWidth = Math.max(targetWidth, dpToPx(280));

        int maxHeight = Math.min(dpToPx(756), (int) (dm.heightPixels * 0.80f));
        maxHeight = Math.max(maxHeight, dpToPx(200));
        maxHeight = Math.min(maxHeight, dm.heightPixels - dpToPx(24));

        int widthSpec = View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        root.measure(widthSpec, heightSpec);
        int contentHeight = root.getMeasuredHeight();
        int targetHeight = Math.min(Math.max(contentHeight, dpToPx(180)), maxHeight);

        dialog.getWindow().setLayout(targetWidth, targetHeight);
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.LayoutParams(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            lp.width = targetWidth;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        root.setLayoutParams(lp);
        Log.d(TAG, logTag + " w=" + targetWidth + " h=" + targetHeight
                + " content=" + contentHeight + " screen=" + dm.widthPixels + "x" + dm.heightPixels);
    }

    private void applyOutlineDialogSize() {
        applyFlexibleDialogSize(outlineDialog, outlineDialogRoot, "outline_dialog_size");
    }

    private void scrollOutlineResultToBottom() {
        if (outlineResultScroll == null || outlineResultCard == null
                || outlineResultCard.getVisibility() != View.VISIBLE) {
            return;
        }
        outlineResultScroll.post(() -> outlineResultScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showOutlineTypePicker() {
        View anchor = outlineTypeCard != null ? outlineTypeCard : outlineTypeLabel;
        PopupMenu popup = new PopupMenu(this, anchor);
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
        if (outlineDialogRoot != null) {
            outlineDialogRoot.post(this::applyOutlineDialogSize);
        } else {
            applyOutlineDialogSize();
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
        View root = getLayoutInflater().inflate(R.layout.lolib_dialog_article_generate_v2, null);
        articleDialogRoot = root;

        articleCategoryLabel = root.findViewById(R.id.article_category_label);
        articleSubTypeLabel = root.findViewById(R.id.article_subtype_label);
        articleSubTypeCard = root.findViewById(R.id.article_subtype_card);
        articleStageHint = root.findViewById(R.id.article_stage_hint);
        articleStageForm = root.findViewById(R.id.article_stage_form);
        articleFormContainer = root.findViewById(R.id.article_form_container);
        articleGenerateBtnText = root.findViewById(R.id.article_generate_btn_text);
        articleResultCard = root.findViewById(R.id.article_result_card);
        articleResultScroll = root.findViewById(R.id.article_result_scroll);
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
        applyFlexibleDialogSize(articleDialog, articleDialogRoot, "article_dialog_size");
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
        if (articleResultScroll == null || articleResultCard == null
                || articleResultCard.getVisibility() != View.VISIBLE) {
            return;
        }
        articleResultScroll.post(() -> articleResultScroll.fullScroll(View.FOCUS_DOWN));
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

        if (articleDialogRoot != null) {
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
                String message = "insertfile name=" + fileName + " type=graphic data=" + base64;
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
                + " type=graphic data=" + base64 + "\");return 'queued';}"
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
        aiImageRatioLabel = root.findViewById(R.id.ai_image_ratio_label);
        aiImageRatioCard = root.findViewById(R.id.ai_image_ratio_card);
        aiImageGenerateBtn = root.findViewById(R.id.ai_image_generate_btn);
        aiImageGalleryContainer = root.findViewById(R.id.ai_image_gallery_container);
        aiImageMainView = root.findViewById(R.id.ai_image_main);
        aiImageThumbViews[0] = root.findViewById(R.id.ai_image_thumb_1);
        aiImageThumbViews[1] = root.findViewById(R.id.ai_image_thumb_2);
        aiImageThumbViews[2] = root.findViewById(R.id.ai_image_thumb_3);
        aiImageLoading = root.findViewById(R.id.ai_image_loading);
        aiImageDoneRow = root.findViewById(R.id.ai_image_done_row);

        aiImageSelectedRatioIndex = 0;
        if (aiImageRatioLabel != null) {
            aiImageRatioLabel.setText(AI_IMAGE_RATIOS[0]);
        }
        if (aiImageRatioCard != null) {
            aiImageRatioCard.setOnClickListener(v -> showAiImageRatioPicker());
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

    private void showAiImageRatioPicker() {
        View anchor = aiImageRatioCard != null ? aiImageRatioCard : aiImageRatioLabel;
        if (anchor == null) {
            return;
        }
        PopupMenu popup = new PopupMenu(this, anchor);
        for (int i = 0; i < AI_IMAGE_RATIOS.length; i++) {
            popup.getMenu().add(0, i, i, AI_IMAGE_RATIOS[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            int idx = item.getItemId();
            if (idx >= 0 && idx < AI_IMAGE_RATIOS.length) {
                aiImageSelectedRatioIndex = idx;
                if (aiImageRatioLabel != null) {
                    aiImageRatioLabel.setText(AI_IMAGE_RATIOS[idx]);
                }
            }
            return true;
        });
        popup.show();
    }

    private void startAiImageGeneration() {
        String prompt = aiImagePromptEdit != null ? aiImagePromptEdit.getText().toString().trim() : "";
        if (prompt.isEmpty()) {
            toastTodo("请输入图片的引导词");
            return;
        }
        int ratioIdx = aiImageSelectedRatioIndex;
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
        int ratioIdx = aiImageSelectedRatioIndex;
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
        // calc_formula 走公式生成弹窗
        if (AiChatCoordinator.MODE_CALC_FORMULA.equals(mode) && calcFormulaPanel != null) {
            openCalcFormulaDialog();
            return;
        }
        // calc_cond_format 走条件格式弹窗
        if (AiChatCoordinator.MODE_CALC_COND_FORMAT.equals(mode) && condFormatPanel != null) {
            openCondFormatDialog();
            return;
        }
        // calc_data_process 走数据处理弹窗
        if (AiChatCoordinator.MODE_CALC_DATA_PROCESS.equals(mode) && dpPanel != null) {
            openDataProcessDialog();
            return;
        }
        // calc_data_analysis 走数据分析弹窗（复用数据处理弹窗）
        if (AiChatCoordinator.MODE_CALC_DATA_ANALYSIS.equals(mode) && dpPanel != null) {
            openDataAnalysisDialog();
            return;
        }
        // calc_chart 走图表生成弹窗
        if (AiChatCoordinator.MODE_CALC_CHART.equals(mode) && chartPanel != null) {
            openChartDialog();
            return;
        }
        // impress_outline 走 PPT 大纲弹窗
        if (AiChatCoordinator.MODE_IMPRESS_OUTLINE.equals(mode) && impressOutlinePanel != null) {
            openImpressOutlineDialog();
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
        if (pendingAutoIsCalcNewTable) {
            String userDescription = pendingAutoUserDescription == null ? "" : pendingAutoUserDescription.trim();
            Log.i(TAG, "ai_calc_new_table_start userDescription=" + userDescription);
            startCalcNewTableRequest(userDescription);
            return;
        }
        String prompt = pendingAutoOpenAiPrompt == null || pendingAutoOpenAiPrompt.isEmpty()
                ? "请先生成文档大纲（章节标题），再基于大纲输出完整正文，风格专业、结构清晰。"
                : pendingAutoOpenAiPrompt;
        Log.i(TAG, "ai_auto_generate_start promptChars=" + prompt.length()
                + " promptPreview=" + (prompt.length() > 80 ? prompt.substring(0, 80) + "..." : prompt));
        showNativeAiPanel();
        startNativeAiRequest("", prompt, true, "chat", false);
    }

    private void startCalcNewTableRequest(String userDescription) {
        runOnUiThread(() -> {
            initCalcNewTableViews();
            showCalcNewTableProgress("正在生成表格数据...", "AI 正在分析你的需求");
        });

        String requestId = java.util.UUID.randomUUID().toString();
        Log.i(TAG, "ai_calc_new_table_request requestId=" + requestId + " input=" + userDescription);

        calcNewTableRequestId = requestId;
        calcNewTableActive = true;
        calcNewTableStreamBuffer.setLength(0);

        final String rawEndpoint = getPrefs().getString(AI_PREF_ENDPOINT, AI_DEFAULT_ENDPOINT);
        final String endpoint = normalizeEndpoint(rawEndpoint, "/chat/completions");
        final String apiKey = getPrefs().getString(AI_PREF_API_KEY, "");
        final String model = getPrefs().getString(AI_PREF_MODEL, AI_DEFAULT_MODEL);
        Log.e(TAG, "ai_calc_debug_probe rawEndpoint=[" + rawEndpoint + "] normalized=[" + endpoint + "] apiKey.len=" + apiKey.length() + " model=[" + model + "]");

        if (apiKey.isEmpty()) {
            runOnUiThread(() -> showCalcNewTableError("AI 模型未配置，请在设置中配置 API Key"));
            return;
        }

        new Thread(() -> {
            try {
                AiRequestSession session = new AiRequestSession();
                aiRequestSessions.put(requestId, session);

                JSONArray messages = AiChatCoordinator.buildNewCalcTableMessages(userDescription);

                aiRequestManager.execute(requestId, endpoint, apiKey, model, messages, session,
                        new AiRequestManager.Callback() {
                            @Override
                            public String sanitizePayload(String callbackRequestId, Object raw, String stage) {
                                return sanitizeAiTextPayload(callbackRequestId, raw, stage);
                            }

                            @Override
                            public void onStreamingState(String callbackRequestId) {
                            }

                            @Override
                            public void onStreamDelta(String callbackRequestId, String delta) throws JSONException {
                                if (!callbackRequestId.equals(calcNewTableRequestId)) return;
                                calcNewTableStreamBuffer.append(delta);
                                runOnUiThread(() -> {
                                    String preview = calcNewTableStreamBuffer.toString();
                                    int len = preview.length();
                                    String detail = len > 50 ? "已接收 " + len + " 字符..." : "正在生成表格数据...";
                                    showCalcNewTableProgress("正在生成表格数据...", detail);
                                });
                            }

                            @Override
                            public void onDone(String callbackRequestId, String fullText) throws JSONException {
                                if (!callbackRequestId.equals(calcNewTableRequestId)) return;
                                calcNewTableActive = false;
                                Log.i(TAG, "ai_calc_new_table_done requestId=" + callbackRequestId + " chars=" + fullText.length());
                                onCalcNewTableDone(fullText);
                            }

                            @Override
                            public void onError(String callbackRequestId, String code, String message) {
                                if (!callbackRequestId.equals(calcNewTableRequestId)) return;
                                calcNewTableActive = false;
                                String safeMsg = message == null ? "" : message;
                                Log.e(TAG, "ai_calc_new_table_error requestId=" + callbackRequestId + " code=" + code + " msg=" + safeMsg);
                                runOnUiThread(() -> showCalcNewTableError("生成失败：" + safeMsg));
                            }
                        });
            } catch (Exception e) {
                calcNewTableActive = false;
                Log.e(TAG, "ai_calc_new_table_request_failed", e);
                runOnUiThread(() -> showCalcNewTableError("请求失败：" + e.getMessage()));
            }
        }, "cool-ai-" + requestId).start();
    }

    private void initCalcNewTableViews() {
        if (calcNewTableOverlay == null) {
            calcNewTableOverlay = findViewById(R.id.calc_new_table_overlay);
            calcNewTablePanel = findViewById(R.id.calc_new_table_panel);
            calcNewTableSpinner = findViewById(R.id.calc_new_table_spinner);
            calcNewTableStatus = findViewById(R.id.calc_new_table_status);
            calcNewTableDetail = findViewById(R.id.calc_new_table_detail);
        }
    }

    private void showCalcNewTableProgress(String status, String detail) {
        if (calcNewTableOverlay != null) {
            calcNewTableOverlay.setVisibility(View.VISIBLE);
            calcNewTablePanel.setVisibility(View.VISIBLE);
        }
        if (calcNewTableSpinner != null) {
            calcNewTableSpinner.setVisibility(View.VISIBLE);
        }
        if (calcNewTableStatus != null) {
            calcNewTableStatus.setText(status);
        }
        if (calcNewTableDetail != null) {
            calcNewTableDetail.setText(detail);
        }
    }

    private void showCalcNewTableError(String message) {
        if (calcNewTableOverlay != null) {
            calcNewTableOverlay.setVisibility(View.VISIBLE);
            calcNewTablePanel.setVisibility(View.VISIBLE);
        }
        if (calcNewTableSpinner != null) {
            calcNewTableSpinner.setVisibility(View.GONE);
        }
        if (calcNewTableStatus != null) {
            calcNewTableStatus.setText("生成失败");
        }
        if (calcNewTableDetail != null) {
            calcNewTableDetail.setText(message);
        }
        getMainHandler().postDelayed(() -> dismissCalcNewTableProgress(), 3000L);
    }

    private void dismissCalcNewTableProgress() {
        if (calcNewTableOverlay != null) {
            calcNewTableOverlay.setVisibility(View.GONE);
            calcNewTablePanel.setVisibility(View.GONE);
        }
    }

    private void onCalcNewTableDone(String fullText) {
        if (fullText == null || fullText.isEmpty()) {
            runOnUiThread(() -> showCalcNewTableError("AI 返回为空，请重试"));
            return;
        }

        String json = fullText.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start > 0 && end > start) {
                json = json.substring(start, end).trim();
            }
        }

        final JSONArray columns;
        final JSONArray rows;
        final int colCount;
        final int rowCount;
        try {
            JSONObject table = new JSONObject(json);
            columns = table.optJSONArray("columns");
            rows = table.optJSONArray("data");
            if (columns == null || columns.length() == 0 || rows == null || rows.length() == 0) {
                throw new JSONException("Missing columns or data in JSON response");
            }
            colCount = columns.length();
            rowCount = rows.length();
        } catch (JSONException e) {
            Log.e(TAG, "ai_calc_new_table_json_parse_failed json=" + json, e);
            runOnUiThread(() -> showCalcNewTableError("AI 返回数据格式错误，请重试"));
            return;
        }

        Log.i(TAG, "ai_calc_new_table_parsed cols=" + colCount + " rows=" + rowCount);

        runOnUiThread(() -> {
            showCalcNewTableProgress("正在写入表格数据...", colCount + " 列 × " + rowCount + " 行");

            ensureEditModeThen(() -> {
                new Thread(() -> {
                    try {
                        boolean success = writeCalcTableData(columns, rows, colCount, rowCount);
                        runOnUiThread(() -> {
                            if (success) {
                                showCalcNewTableProgress("完成", "表格数据已生成");
                                getMainHandler().postDelayed(LOActivity.this::dismissCalcNewTableProgress, 2000L);
                                Toast.makeText(LOActivity.this, "表格数据已生成", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "ai_calc_new_table_write_failed", e);
                        runOnUiThread(() -> showCalcNewTableError("写入失败：" + e.getMessage()));
                    }
                }).start();
            });
        });
    }

    private boolean writeCalcTableData(JSONArray columns, JSONArray rows, int colCount, int rowCount) {
        try {
            StringBuilder tsv = new StringBuilder();
            for (int c = 0; c < colCount; c++) {
                if (c > 0) tsv.append('\t');
                tsv.append(columns.optString(c, ""));
            }
            tsv.append('\n');
            for (int r = 0; r < rowCount; r++) {
                JSONArray row = rows.optJSONArray(r);
                if (row == null) continue;
                for (int c = 0; c < Math.min(row.length(), colCount); c++) {
                    if (c > 0) tsv.append('\t');
                    Object val = row.opt(c);
                    if (val != null) {
                        String cellVal = val.toString();
                        if (cellVal.indexOf('\t') >= 0 || cellVal.indexOf('\n') >= 0) {
                            cellVal = cellVal.replace('\t', ' ').replace('\n', ' ');
                        }
                        tsv.append(cellVal);
                    }
                }
                tsv.append('\n');
            }

            Log.i(TAG, "writeCalcTableData tsvPreview="
                    + tsv.substring(0, Math.min(tsv.length(), 200)).replace('\n', '¶').replace('\t', '→'));

            paste("text/plain;charset=utf-8", tsv.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "writeCalcTableData error", e);
            return false;
        }
    }

    private String getCellRef(int col, int row) {
        StringBuilder colRef = new StringBuilder();
        int c = col;
        while (c >= 0) {
            colRef.insert(0, (char) ('A' + (c % 26)));
            c = c / 26 - 1;
        }
        return colRef.toString() + (row + 1);
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
        if (!stale) {
            return false;
        }
        // Impress 大纲/生成走独立弹窗，不占用 aiActiveRequestId；迟到的 stream/done 属正常情况
        if (requestId.startsWith("impress-outline-") || requestId.startsWith("impress-generate-")) {
            if ("ai.stream".equals(type) || "ai.done".equals(type)) {
                return stale;
            }
        }
        if ("ai.stream".equals(type)) {
            return stale;
        }
        // allow post-done late state events without noisy stale logs
        if (!"ai.state".equals(type) || (aiActiveRequestId != null && !aiActiveRequestId.isEmpty())) {
            Log.i(TAG, "ai_stream_drop_stale_request requestId=" + requestId + " active=" + aiActiveRequestId
                    + " type=" + type);
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
