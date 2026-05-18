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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.libreoffice.androidlib.lok.LokClipboardData;
import org.libreoffice.androidlib.lok.LokClipboardEntry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private static final String CLIPBOARD_FILE_PATH = "LibreofficeClipboardFile.data";
    private static final String CLIPBOARD_COOL_SIGNATURE = "cool-clip-magic-4a22437e49a8-";
    private static final String AI_PREF_ENDPOINT = "AI_OPENAI_ENDPOINT";
    private static final String AI_PREF_API_KEY = "AI_OPENAI_API_KEY";
    private static final String AI_PREF_MODEL = "AI_OPENAI_MODEL";
    private static final String AI_STATE_UNCONFIGURED = "unconfigured";
    private static final String AI_STATE_LOADING = "loading";
    private static final String AI_STATE_STREAMING = "streaming";
    private static final String AI_STATE_READY = "ready";
    private static final String AI_STATE_CANCELLED = "cancelled";
    private static final String AI_STATE_ERROR = "error";
    private static final String AI_DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String AI_DEFAULT_MODEL = "gpt-4o-mini";
    private static final String AI_DEFAULT_SYSTEM_PROMPT = "You are a concise office writing assistant. Return only the rewritten or generated content.";
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

    /** In case the mobile-wizard is visible, we have to intercept the Android's Back button. */
    private boolean mMobileWizardVisible = false;
    private boolean mIsEditModeActive = false;

    private ValueCallback<Uri[]> valueCallback;
    private final Map<String, AiRequestSession> aiRequestSessions = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> aiTextByRequestId = new ConcurrentHashMap<>();
    private boolean aiBridgeInjected = false;
    private String aiActiveRequestId = "";
    private AlertDialog aiPanelDialog;
    private EditText aiEndpointInput;
    private EditText aiModelInput;
    private EditText aiKeyInput;
    private EditText aiPromptInput;
    private TextView aiStatusText;
    private TextView aiOutputText;
    private Button aiRunButton;
    private Button aiCancelButton;
    private Button aiAcceptButton;

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

    private static class AiRequestSession {
        private volatile boolean cancelled = false;
        private volatile boolean stateSentStreaming = false;
        private HttpURLConnection connection;

        void bindConnection(HttpURLConnection httpURLConnection) {
            connection = httpURLConnection;
        }

        void cancel() {
            cancelled = true;
            if (connection != null) {
                connection.disconnect();
            }
        }

        boolean isCancelled() {
            return cancelled;
        }

        boolean shouldEmitStreamingState() {
            if (stateSentStreaming) {
                return false;
            }
            stateSentStreaming = true;
            return true;
        }
    }

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
                if (dest != null) dest.close();
                if (source != null) source.close();
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
        mProgressDialog = new ProgressDialog(this);
        if (BuildConfig.GOOGLE_PLAY_ENABLED)
            this.rateAppController = new RateAppController(this);
        else
            this.rateAppController = null;
        this.mActivity = this;

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!documentLoaded) {
                    finishAndRemoveTask();
                    return;
                }

                if (mMobileWizardVisible) {
                    // just return one level up in the mobile-wizard (or close it)
                    callFakeWebsocketOnMessage("mobile: mobilewizardback");
                    return;
                } else if (mIsEditModeActive) {
                    callFakeWebsocketOnMessage("mobile: readonlymode");
                    return;
                }

                finishWithProgress();
            }
        });

        init();
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
                    Toast.makeText(this, getResources().getString(R.string.temp_file_saving_disabled), Toast.LENGTH_SHORT).show();
                }

                // turns out that on ChromeOS, it is not possible to save back
                // to Google Drive; detect it already here to avoid disappointment
                // also the volumeprovider does not work for saving back,
                // which is much more serious :-(
                if (isDocEditable && (getIntent().getData().toString().startsWith("content://org.chromium.arc.chromecontentprovider/externalfile") ||
                                      getIntent().getData().toString().startsWith("content://org.chromium.arc.volumeprovider/"))) {
                    isDocEditable = false;
                    Log.d(TAG, "Disabled editing: Chrome OS unsupported content providers");
                    Toast.makeText(this, getResources().getString(R.string.file_chromeos_read_only), Toast.LENGTH_LONG).show();
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
            //User can't reach here but if he/she does then
            Toast.makeText(this, getString(R.string.failed_to_load_file), Toast.LENGTH_SHORT).show();
            finish();
        }
        // some types don't have export filter so we cannot edit them
        // only set it to false if it returns false otherwise it can break previous controls
        if (!canDocumentBeExported())
            isDocEditable = false;
        if (mTempFile != null)
        {
            mWebView = (COWebView) findViewById(R.id.browser);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                mWebView.setOnApplyWindowInsetsListener((v, windowInsets) -> {
                    Insets insets = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ? WindowInsets.Type.systemOverlays() : 0));

                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                    mlp.leftMargin = insets.left;
                    mlp.topMargin = insets.top;
                    mlp.rightMargin = insets.right;
                    mlp.bottomMargin = insets.bottom;
                    v.setLayoutParams(mlp);

                    return WindowInsets.CONSUMED;
                });

                boolean lightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_YES) == 0;
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(lightMode);
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(lightMode);
            }

            mMobileSocket = mWebView.getWebViewClient().getMobileSocket();

            WebSettings webSettings = mWebView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            mWebView.addJavascriptInterface(this, "COOLMessageHandler");
            setupAiFab();

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
                public boolean onShowFileChooser(WebView mWebView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                    if (valueCallback != null) {
                        valueCallback.onReceiveValue(null);
                        valueCallback = null;
                    }

                    valueCallback = filePathCallback;
                    Intent intent = fileChooserParams.createIntent();

                    try {
                        intent.setType("image/*");
                        startActivityForResult(intent, REQUEST_SELECT_IMAGE_FILE);
                    } catch (ActivityNotFoundException e) {
                        valueCallback = null;
                        Toast.makeText(LOActivity.this, getString(R.string.cannot_open_file_chooser), Toast.LENGTH_LONG).show();
                        return false;
                    }
                    return true;
                }
            });

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "asking for read storage permission");
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_WRITE_EXTERNAL_STORAGE);
            } else {
                loadDocument();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {

        Log.i(TAG, "onNewIntent");

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
                //copyTempBackToIntent();
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
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_INTENT_URI, getIntent().getData().toString());
        outState.putInt(KEY_PROVIDER_ID, providerId);
        if (documentUri != null) {
            outState.putString(KEY_DOCUMENT_URI, documentUri.toString());
        }
        //If this activity was opened via contentUri
        outState.putBoolean(KEY_IS_EDITABLE, isDocEditable);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
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

    /** When we get the file via a content: URI, we need to put it to a temp file. */
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
                @Nullable String intentType = mActivity.getIntent().getType();
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
            // NOTE: might be useful to add some indicator in UI for long copy operations involving network...
            copyThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return copyThread.result;
    }

    /** Check that we have created a temp file, and if yes, copy it back to the content: URI. */
    private void copyTempBackToIntent() {
        if (!isDocEditable || mTempFile == null || getIntent().getData() == null || !getIntent().getData().getScheme().equals(ContentResolver.SCHEME_CONTENT))
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
                            }
                            catch (FileNotFoundException e) {
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
            cancelAllAiRequests();
            super.onDestroy();
            return;
        }
        cancelAllAiRequests();
        nativeLooper.quit();

        // Remove the webview from the hierarchy & destroy
        final ViewGroup viewGroup = (ViewGroup) mWebView.getParent();
        if (viewGroup != null)
            viewGroup.removeView(mWebView);
        mWebView.destroy();
        mWebView = null;
        mMobileSocket = null;

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
        if (resultCode != RESULT_OK) {
            if (requestCode == REQUEST_SELECT_IMAGE_FILE) {
                valueCallback.onReceiveValue(null);
                valueCallback = null;
            }
            return;
        }

        /*
            Copy is just save-as in general but with TakeOwnership.
            Meaning that we will switch to the copied (saved-as) document in the bg
            this way we don't need to reload the activity.
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
                        LOActivity.this.saveAs(tempFile.toURI().toString(), format, requestCopy ? "TakeOwnership" : null);

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
                        Toast.makeText(this, "Something went wrong while Saving as: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        String recentList =  recentPrefs.getString(RECENT_DOCUMENTS_KEY, "");
        recentList = treeFileUri.toString() + "\n" + recentList;
        recentPrefs.edit().putString(RECENT_DOCUMENTS_KEY, recentList).apply();
    }

    private String getFormatForRequestCode(int requestCode) {
        switch(requestCode) {
            case REQUEST_SAVEAS_PDF: return "pdf";
            case REQUEST_SAVEAS_RTF: return "rtf";
            case REQUEST_SAVEAS_ODT: return "odt";
            case REQUEST_SAVEAS_ODP: return "odp";
            case REQUEST_SAVEAS_ODS: return "ods";
            case REQUEST_SAVEAS_DOCX: return "docx";
            case REQUEST_SAVEAS_PPTX: return "pptx";
            case REQUEST_SAVEAS_XLSX: return "xlsx";
            case REQUEST_SAVEAS_DOC: return "doc";
            case REQUEST_SAVEAS_PPT: return "ppt";
            case REQUEST_SAVEAS_XLS: return "xls";
            case REQUEST_SAVEAS_EPUB: return "epub";
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
                //copyTempBackToIntent();

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

        if(isDarkMode()) {
            finalUrlToLoad += "&darkTheme=true";
        }

        // load the page
        mWebView.loadUrl(finalUrlToLoad);

        documentLoaded = true;

        loadDocumentMillis = android.os.SystemClock.uptimeMillis();
    }

    private boolean isDarkMode() {
        SharedPreferences recentPrefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
        int mode = recentPrefs.getInt(NIGHT_MODE_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        switch (mode) {
            case -1:
                int darkModeFlag = getBaseContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
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
    public native void createCOOLWSD(String dataDir, String cacheDir, String apkFile, AssetManager assetManager, String loadFileURL, String uiMode, String userName);

    /**
     * Passing messages from JS (instead of the websocket communication).
     */
    @JavascriptInterface
    public void postMobileMessage(String message) {
        Log.d(TAG, "postMobileMessage: " + message);

        String[] messageAndParameterArray= message.split(" ", 2); // the command and the rest (that can potentially contain spaces too)

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
     * Similar to callFakeWebsocketOnMessage but 'message' is instead any expression evaluable as
     * JavaScript. For example, you should use this to pass Base64ToArrayBuffer invocations to
     * the fake websocket
     */
    void rawCallFakeWebsocketOnMessage(final byte[] message) {
        try {
            mMobileSocket.queueSend(message, () -> {
                mWebView.post(() -> {
                    mWebView.loadUrl("javascript:window.socket.doSend();");
                });
            });
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
                } catch (JSONException ignored) {}

                try {
                    int progress = messageJSON.getInt("value");
                    mProgressDialog.determinateProgress(progress);
                } catch (JSONException ignored) {}
            });
        } else if (messageStartsWith(message, "error:")) {
            runOnUiThread(() -> mProgressDialog.dismiss());
        }
    }

    /**
     * @param message The message to test for the prefix
     * @param prefix The prefix to test for
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
     * return true to pass the message to the native part or false to block the message
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
                        mIsEditModeActive = true;
                        // prompt for file conversion
                        requestForOdf();
                        break;
                    case "off":
                        mIsEditModeActive = false;
                        break;
                }
                return false;
            }
            case "hideProgressbar": {
                if (mProgressDialog != null)
                    mProgressDialog.dismiss();
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
            String requestId = request.optString("requestId", "");
            if (requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
                request.put("requestId", requestId);
            }

            final String finalRequestId = requestId;
            persistAiConfigFromRequest(request);
            cancelAiRequest(finalRequestId);
            dispatchAiState(finalRequestId, AI_STATE_LOADING, "AI request queued");

            AiRequestSession session = new AiRequestSession();
            aiRequestSessions.put(finalRequestId, session);

            Thread requestThread = new Thread(() -> {
                runAiRequest(finalRequestId, request, session);
                aiRequestSessions.remove(finalRequestId);
            }, "cool-ai-" + finalRequestId);
            requestThread.start();
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
        JSONObject context = request.optJSONObject("context");
        String endpoint = context != null ? context.optString("endpoint", "") : "";
        String apiKey = context != null ? context.optString("apiKey", "") : "";
        String model = context != null ? context.optString("model", "") : "";

        if (endpoint.isEmpty()) {
            endpoint = getPrefs().getString(AI_PREF_ENDPOINT, AI_DEFAULT_ENDPOINT);
        }
        if (apiKey.isEmpty()) {
            apiKey = getPrefs().getString(AI_PREF_API_KEY, "");
        }
        if (model.isEmpty()) {
            model = getPrefs().getString(AI_PREF_MODEL, AI_DEFAULT_MODEL);
        }

        if (endpoint == null || endpoint.isEmpty()) {
            dispatchAiState(requestId, AI_STATE_UNCONFIGURED, "AI endpoint is not configured");
            dispatchAiError(requestId, "config_missing", "AI endpoint is not configured");
            return;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            dispatchAiState(requestId, AI_STATE_UNCONFIGURED, "AI API key is not configured");
            dispatchAiError(requestId, "config_missing", "AI API key is not configured");
            return;
        }

        HttpURLConnection connection = null;
        StringBuilder fullText = new StringBuilder();
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            session.bindConnection(connection);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", true);

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", AI_DEFAULT_SYSTEM_PROMPT));
            messages.put(new JSONObject().put("role", "user").put("content", buildAiUserPrompt(request)));
            body.put("messages", messages);

            byte[] requestBody = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(requestBody);

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String errorText = readStreamAsText(connection.getErrorStream());
                dispatchAiError(requestId, "http_" + statusCode, errorText.isEmpty() ? "AI request failed" : errorText);
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (session.isCancelled()) {
                    return;
                }

                line = line.trim();
                if (!line.startsWith("data:")) {
                    continue;
                }

                String data = line.substring("data:".length()).trim();
                if (data.isEmpty()) {
                    continue;
                }

                if ("[DONE]".equals(data)) {
                    JSONObject donePayload = new JSONObject();
                    donePayload.put("requestId", requestId);
                    donePayload.put("fullText", fullText.toString());
                    dispatchAiEvent("ai.done", donePayload);
                    dispatchAiState(requestId, AI_STATE_READY, "AI response completed");
                    return;
                }

                try {
                    JSONObject chunk = new JSONObject(data);
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) {
                        continue;
                    }

                    JSONObject choice = choices.optJSONObject(0);
                    if (choice == null) {
                        continue;
                    }

                    String delta = "";
                    JSONObject deltaObj = choice.optJSONObject("delta");
                    if (deltaObj != null) {
                        delta = deltaObj.optString("content", "");
                    }
                    if (delta.isEmpty()) {
                        delta = choice.optString("text", "");
                    }

                    if (delta.isEmpty()) {
                        continue;
                    }

                    if (session.shouldEmitStreamingState()) {
                        dispatchAiState(requestId, AI_STATE_STREAMING, "AI response streaming");
                    }
                    fullText.append(delta);
                    JSONObject streamPayload = new JSONObject();
                    streamPayload.put("requestId", requestId);
                    streamPayload.put("delta", delta);
                    dispatchAiEvent("ai.stream", streamPayload);
                } catch (JSONException parseError) {
                    Log.w(TAG, "Skipping unparsable SSE chunk: " + data, parseError);
                }
            }

            JSONObject donePayload = new JSONObject();
            donePayload.put("requestId", requestId);
            donePayload.put("fullText", fullText.toString());
            dispatchAiEvent("ai.done", donePayload);
            dispatchAiState(requestId, AI_STATE_READY, "AI response completed");
        } catch (Exception e) {
            if (!session.isCancelled()) {
                dispatchAiState(requestId, AI_STATE_ERROR, "AI request failed");
                dispatchAiError(requestId, "request_failed", e.getMessage() == null ? "AI request failed" : e.getMessage());
                Log.e(TAG, "runAiRequest failed", e);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String buildAiUserPrompt(JSONObject request) {
        String taskType = request.optString("taskType", "rewrite");
        String selection = request.optString("selection", "");
        String modelMode = request.optString("modelMode", "cloud");
        String contextString = "";

        JSONObject context = request.optJSONObject("context");
        if (context != null) {
            contextString = context.toString();
        }

        if (selection.isEmpty() && context != null) {
            selection = context.optString("selection", "");
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

    private String readStreamAsText(InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        } catch (IOException e) {
            return "";
        }
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

        String endpoint = context.optString("endpoint", "").trim();
        String model = context.optString("model", "").trim();
        String apiKey = context.optString("apiKey", "").trim();

        SharedPreferences.Editor editor = getPrefs().edit();
        boolean changed = false;
        if (!endpoint.isEmpty()) {
            editor.putString(AI_PREF_ENDPOINT, endpoint);
            changed = true;
        }
        if (!model.isEmpty()) {
            editor.putString(AI_PREF_MODEL, model);
            changed = true;
        }
        if (!apiKey.isEmpty()) {
            editor.putString(AI_PREF_API_KEY, apiKey);
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
                "if(window.__coolAiBridge&&typeof window.__coolAiBridge.onNativeEvent==='function'){window.__coolAiBridge.onNativeEvent(data);}" +
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
        aiFab.setOnClickListener(v -> showNativeAiPanel());
    }

    private void showNativeAiPanel() {
        if (aiPanelDialog != null && aiPanelDialog.isShowing()) {
            return;
        }

        View panel = LayoutInflater.from(this).inflate(R.layout.lolib_dialog_ai_panel, null, false);
        aiEndpointInput = panel.findViewById(R.id.ai_endpoint);
        aiModelInput = panel.findViewById(R.id.ai_model);
        aiKeyInput = panel.findViewById(R.id.ai_api_key);
        aiPromptInput = panel.findViewById(R.id.ai_prompt);
        aiStatusText = panel.findViewById(R.id.ai_status);
        aiOutputText = panel.findViewById(R.id.ai_output);
        aiRunButton = panel.findViewById(R.id.ai_run);
        aiCancelButton = panel.findViewById(R.id.ai_cancel);
        aiAcceptButton = panel.findViewById(R.id.ai_accept);

        aiEndpointInput.setText(getPrefs().getString(AI_PREF_ENDPOINT, AI_DEFAULT_ENDPOINT));
        aiModelInput.setText(getPrefs().getString(AI_PREF_MODEL, AI_DEFAULT_MODEL));
        aiKeyInput.setText(getPrefs().getString(AI_PREF_API_KEY, ""));
        aiPromptInput.setText("Polish and continue the selected text.");
        aiStatusText.setText("Ready");
        aiOutputText.setText("");
        setNativeAiPanelState(AI_STATE_READY, "Ready");

        aiRunButton.setOnClickListener(v -> runAiFromNativePanel());
        aiCancelButton.setOnClickListener(v -> cancelAiFromNativePanel());
        aiAcceptButton.setOnClickListener(v -> acceptAiFromNativePanel());

        aiPanelDialog = new AlertDialog.Builder(this)
                .setTitle("AI Assistant")
                .setView(panel)
                .setOnDismissListener(dialog -> aiPanelDialog = null)
                .create();
        aiPanelDialog.show();
    }

    private void runAiFromNativePanel() {
        if (mWebView == null) {
            return;
        }
        setNativeAiPanelState(AI_STATE_LOADING, "AI request queued");
        mWebView.evaluateJavascript("(function(){try{if(window.app&&app.map&&app.map._clip){return app.map._clip._selectionPlainTextContent||'';}return (window.getSelection&&window.getSelection().toString())||'';}catch(e){return '';}})();",
                value -> startNativeAiRequest(parseJsString(value)));
    }

    private void startNativeAiRequest(String selection) {
        try {
            JSONObject context = new JSONObject();
            context.put("prompt", aiPromptInput != null ? aiPromptInput.getText().toString() : "");
            context.put("source", "android-native-panel");
            context.put("endpoint", aiEndpointInput != null ? aiEndpointInput.getText().toString().trim() : "");
            context.put("model", aiModelInput != null ? aiModelInput.getText().toString().trim() : "");
            context.put("apiKey", aiKeyInput != null ? aiKeyInput.getText().toString().trim() : "");

            JSONObject request = new JSONObject();
            String requestId = "req-" + UUID.randomUUID();
            request.put("requestId", requestId);
            request.put("taskType", "rewrite");
            request.put("selection", selection == null ? "" : selection);
            request.put("context", context);
            request.put("modelMode", "cloud");

            aiActiveRequestId = requestId;
            aiTextByRequestId.put(requestId, new StringBuilder());
            if (aiOutputText != null) {
                aiOutputText.setText("");
            }

            handleAiRequestFromWeb(request.toString());
        } catch (JSONException e) {
            dispatchAiError("", "invalid_payload", "Failed to build native ai.request payload");
        }
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

    private void handleAiNativeEvent(JSONObject event) {
        String type = event.optString("type", "");
        String requestId = event.optString("requestId", "");
        if (!requestId.isEmpty() && (aiActiveRequestId == null || aiActiveRequestId.isEmpty())) {
            aiActiveRequestId = requestId;
        }

        if ("ai.stream".equals(type)) {
            String delta = event.optString("delta", "");
            if (!requestId.isEmpty()) {
                aiTextByRequestId.computeIfAbsent(requestId, ignored -> new StringBuilder()).append(delta);
            }
            if (requestId.equals(aiActiveRequestId) && aiOutputText != null && !delta.isEmpty()) {
                runOnUiThread(() -> aiOutputText.append(delta));
            }
            setNativeAiPanelState(AI_STATE_STREAMING, "AI response streaming");
            return;
        }

        if ("ai.done".equals(type)) {
            String fullText = event.optString("fullText", "");
            if (!requestId.isEmpty()) {
                aiTextByRequestId.put(requestId, new StringBuilder(fullText));
            }
            if (requestId.equals(aiActiveRequestId) && aiOutputText != null) {
                runOnUiThread(() -> aiOutputText.setText(fullText));
            }
            setNativeAiPanelState(AI_STATE_READY, "AI response completed");
            return;
        }

        if ("ai.error".equals(type)) {
            setNativeAiPanelState(AI_STATE_ERROR, event.optString("message", "AI request failed"));
            return;
        }

        if ("ai.state".equals(type)) {
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

            String finalMessage = (message == null || message.isEmpty()) ? state : message;
            aiStatusText.setText("State: " + state + " - " + finalMessage);
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
                    "if(evt.type==='ai.stream'&&id){this.lastTextByRequestId[id]=(this.lastTextByRequestId[id]||'')+(evt.delta||'');}" +
                    "if(evt.type==='ai.done'&&id&&typeof evt.fullText==='string'){this.lastTextByRequestId[id]=evt.fullText;}" +
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

    public static void createNewFileInputDialog(Activity activity, final String defaultFileName, final @Nullable String mimeType, final int requestCode) {
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

    private AlertDialog.Builder buildPrompt(final String mTitle, final String mMessage, final String mPositiveBtnText, final String mNegativeBtnText, DialogInterface.OnClickListener callback) {
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
        if (data == null) return null;

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
        buildPrompt(getString(R.string.ask_for_copy), "", canBeExported ? getString(R.string.edit_copy) : getString(R.string.use_odf), getString(R.string.view_only), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (canBeExported)
                    createNewFileInputDialog(mActivity, getFileName(true), getMimeType(), REQUEST_COPY);
                else {
                    String extension = getOdfExtensionForDocType(getMimeType());
                    createNewFileInputDialog(mActivity, getFileName(false) + "." + extension, getMimeForFormat(extension), REQUEST_COPY);
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

    private String getOdfExtensionForDocType(@Nullable String mimeType)
    {
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
        buildPrompt(getString(R.string.ask_for_convert_odf), getString(R.string.convert_odf_message), getString(R.string.use_odf), getString(R.string.use_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                createNewFileInputDialog(mActivity, getFileName(false) + "." + ext, getMimeForFormat(ext), REQUEST_COPY);
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
            case "pdf": return REQUEST_SAVEAS_PDF;
            case "rtf": return REQUEST_SAVEAS_RTF;
            case "odt": return REQUEST_SAVEAS_ODT;
            case "odp": return REQUEST_SAVEAS_ODP;
            case "ods": return REQUEST_SAVEAS_ODS;
            case "docx": return REQUEST_SAVEAS_DOCX;
            case "pptx": return REQUEST_SAVEAS_PPTX;
            case "xlsx": return REQUEST_SAVEAS_XLSX;
            case "doc": return REQUEST_SAVEAS_DOC;
            case "ppt": return REQUEST_SAVEAS_PPT;
            case "xls": return REQUEST_SAVEAS_XLS;
            case "epub": return REQUEST_SAVEAS_EPUB;
        }
        return 0;
    }

    private String getMimeForFormat(String format) {
        switch(format) {
            case "pdf": return "application/pdf";
            case "rtf": return "text/rtf";
            case "odt": return "application/vnd.oasis.opendocument.text";
            case "odp": return "application/vnd.oasis.opendocument.presentation";
            case "ods": return "application/vnd.oasis.opendocument.spreadsheet";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "doc": return "application/msword";
            case "ppt": return "application/vnd.ms-powerpoint";
            case "xls": return "application/vnd.ms-excel";
            case "epub": return "application/epub+zip";
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
    public final void populateClipboard()
    {
        File clipboardFile = new File(getApplicationContext().getCacheDir(), CLIPBOARD_FILE_PATH);
        if (clipboardFile.exists())
            clipboardFile.delete();

        LokClipboardData clipboardData = new LokClipboardData();
        if (!LOActivity.this.getClipboardContent(clipboardData))
            Log.e(TAG, "no clipboard to copy");
        else
        {
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

    /// Do the paste, and return true if we should short-circuit the paste locally (ie. let the core handle that)
    private final boolean performPaste()
    {
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
            }
            else if (clipDesc.getMimeType(i).startsWith("image/")) {
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

/* vim:set shiftwidth=4 softtabstop=4 expandtab cinoptions=b1,g0,N-s cinkeys+=0=break: */
