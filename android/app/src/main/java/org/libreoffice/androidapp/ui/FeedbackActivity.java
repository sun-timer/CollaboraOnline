package org.libreoffice.androidapp.ui;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidapp.feedback.FeedbackApi;
import org.libreoffice.androidapp.feedback.FeedbackConfig;
import org.libreoffice.androidapp.feedback.FeedbackRecord;
import org.libreoffice.androidapp.feedback.FeedbackStore;
import org.libreoffice.androidapp.feedback.FeedbackTypes;
import org.libreoffice.androidlib.SystemUiHelper;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 问题反馈与建议（Figma 429:20679 起全部反馈页面）。
 * 页面流：表单 → 提交成功 → 反馈记录列表 → 详情(状态/回复/关闭)。
 * 数据：V1.2 反馈 HTTP API（见 {@link org.libreoffice.androidapp.feedback.FeedbackConfig}）。
 */
public class FeedbackActivity extends AppCompatActivity {

    private static final int MAX_ATTACH_COUNT = 3;
    private static final long MAX_ATTACH_BYTES = 5L * 1024 * 1024; // 图片超过5MB

    // 表单状态
    private final int[] chipIds = {
            R.id.feedbackTypeChip0, R.id.feedbackTypeChip1,
            R.id.feedbackTypeChip2, R.id.feedbackTypeChip3};
    private int selectedType = -1;
    private final List<Uri> attachUris = new ArrayList<>();
    private boolean shareLog;
    private View[] chips;

    // 列表
    private RecyclerView.Adapter<FeedbackListHolder> listAdapter;
    private List<FeedbackRecord> records = new ArrayList<>();
    private int listPageNum = 1;
    private int listTotalPages = 1;
    private boolean listLoading = false;

    // 详情
    private FeedbackRecord currentDetail;

    private final androidx.activity.result.ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    handleAttachUris(uris);
                }
            });

    // ==================== 生命周期 ====================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FeedbackStore.clearLegacyMockIfNeeded(this);
        showForm();
    }

    // ==================== 页面切换 ====================

    private void showForm() {
        setContentView(R.layout.feedback_main);
        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.feedbackMainHeader), 0);
        applyFormBottomInsets(findViewById(R.id.feedbackMainBottomBar));

        findViewById(R.id.feedbackMainBackBtn).setOnClickListener(v -> finish());
        findViewById(R.id.feedbackMainRecordsEntry).setOnClickListener(v -> showList());
        findViewById(R.id.feedbackSubmitBtn).setOnClickListener(v -> submitFeedback());

        chips = new View[chipIds.length];
        for (int i = 0; i < chipIds.length; i++) {
            chips[i] = findViewById(chipIds[i]);
            final int idx = i;
            chips[i].setOnClickListener(v -> selectChip(idx));
        }
        selectChip(-1);
        shareLog = false;

        EditText desc = findViewById(R.id.feedbackDescInput);
        TextView count = findViewById(R.id.feedbackDescCount);
        desc.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                count.setText(s.length() + "/500");
            }
        });

        findViewById(R.id.feedbackAttachAddBtn).setOnClickListener(v ->
                imagePicker.launch("image/*"));

        ImageView logCheck = findViewById(R.id.feedbackLogCheck);
        logCheck.setImageResource(R.drawable.ic_feedback_checkbox_off);
        findViewById(R.id.feedbackLogRow).setOnClickListener(v -> {
            shareLog = !shareLog;
            logCheck.setImageResource(shareLog
                    ? R.drawable.ic_feedback_checkbox_on : R.drawable.ic_feedback_checkbox_off);
        });
    }

    /**
     * 表单页底部栏：合并导航栏 + 软键盘 insets，避免在根布局消费 insets 导致顶栏无法预留状态栏。
     */
    private void applyFormBottomInsets(View bottomBar) {
        if (bottomBar == null) {
            return;
        }
        final boolean light = SystemUiHelper.isLightMode(this);
        final int extraBottom = getResources().getDimensionPixelSize(R.dimen.feedback_bottom_bar_padding_v);
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
            int bottom = SystemUiHelper.resolveBottomInset(v.getContext(), insets)
                    + extraBottom + SystemUiHelper.getBottomSafeExtraPx(v.getContext());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            if (insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0) {
                SystemUiHelper.applyImeChrome(this, light);
            } else {
                SystemUiHelper.applyDocumentChrome(this, light);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(bottomBar);
    }

    private void applyFeedbackBottomBarInsets(int bottomBarId) {
        View bottomBar = findViewById(bottomBarId);
        if (bottomBar == null) {
            return;
        }
        SystemUiHelper.applyNavigationBarPadding(bottomBar,
                getResources().getDimensionPixelSize(R.dimen.feedback_bottom_bar_padding_v));
    }

    private void showSuccess() {
        setContentView(R.layout.feedback_success);
        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.feedbackHeader), 0);
        applyFeedbackBottomBarInsets(R.id.feedbackSuccessBottomBar);

        findViewById(R.id.feedbackHeaderBackBtn).setOnClickListener(v -> finish());
        findViewById(R.id.feedbackHeaderRecordsEntry).setOnClickListener(v -> showList());
        findViewById(R.id.feedbackSuccessViewRecordsBtn).setOnClickListener(v -> showList());
    }

    private void showList() {
        if (!FeedbackApi.isConfigured()) {
            toast(R.string.feedback_api_not_configured);
            return;
        }
        setContentView(R.layout.feedback_list);
        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.feedbackListHeader), 0);
        SystemUiHelper.applyNavigationBarPadding(findViewById(R.id.feedbackList),
                getResources().getDimensionPixelSize(R.dimen.feedback_list_bottom_padding_v));

        findViewById(R.id.feedbackListBackBtn).setOnClickListener(v -> showForm());

        RecyclerView list = findViewById(R.id.feedbackList);
        list.setLayoutManager(new LinearLayoutManager(this));
        records = new ArrayList<>();
        listPageNum = 1;
        listTotalPages = 1;
        listLoading = false;
        listAdapter = buildListAdapter();
        list.setAdapter(listAdapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || listLoading) {
                    return;
                }
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) {
                    return;
                }
                int lastVisible = lm.findLastVisibleItemPosition();
                if (lastVisible >= records.size() - 2 && listPageNum < listTotalPages) {
                    loadFeedbackListPage(listPageNum + 1, true);
                }
            }
        });

        loadFeedbackListPage(1, false);
    }

    private void loadFeedbackListPage(int pageNum, boolean append) {
        if (listLoading) {
            return;
        }
        listLoading = true;
        if (!append) {
            toast(R.string.feedback_loading);
        }
        FeedbackApi.fetchListPage(this, pageNum, new FeedbackApi.ListPageCallback() {
            @Override
            public void onSuccess(FeedbackApi.ListPage page) {
                listLoading = false;
                if (!append) {
                    records.clear();
                }
                records.addAll(page.list);
                listPageNum = page.pageNum > 0 ? page.pageNum : pageNum;
                listTotalPages = page.pages > 0 ? page.pages : 1;
                if (!append && records.isEmpty()) {
                    showEmpty();
                    return;
                }
                listAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String reason, String message) {
                listLoading = false;
                toastApiError(reason, message);
            }
        });
    }

    private RecyclerView.Adapter<FeedbackListHolder> buildListAdapter() {
        return new RecyclerView.Adapter<FeedbackListHolder>() {
            @Override
            public FeedbackListHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                View item = getLayoutInflater().inflate(R.layout.feedback_list_item, parent, false);
                return new FeedbackListHolder(item);
            }

            @Override
            public void onBindViewHolder(FeedbackListHolder h, int position) {
                FeedbackRecord r = records.get(position);
                h.type.setText(r.type);
                h.time.setText(formatTime(r.submitTime));
                h.content.setText(formatListSummary(r.content));
                applyStatusStyle(h.dot, h.status, r.status);
                h.itemView.setOnClickListener(v -> showDetail(r.id));
            }

            @Override
            public int getItemCount() {
                return records.size();
            }
        };
    }

    private void showEmpty() {
        setContentView(R.layout.feedback_empty);
        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.feedbackEmptyHeader), 0);
        applyFeedbackBottomBarInsets(R.id.feedbackEmptyBottomBar);

        findViewById(R.id.feedbackEmptyHeaderBackBtn).setOnClickListener(v -> showForm());
        findViewById(R.id.feedbackEmptyBackBtn).setOnClickListener(v -> showForm());
    }

    private void showDetail(String id) {
        if (!FeedbackApi.isConfigured()) {
            toast(R.string.feedback_api_not_configured);
            return;
        }
        if (id == null || id.isEmpty()) {
            showList();
            return;
        }
        setContentView(R.layout.feedback_detail);
        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.feedbackDetailHeader), 0);
        applyFeedbackBottomBarInsets(R.id.feedbackDetailBottomBar);
        findViewById(R.id.feedbackDetailBackBtn).setOnClickListener(v -> showList());

        toast(R.string.feedback_loading);
        FeedbackApi.fetchDetail(this, id, new FeedbackApi.RecordCallback() {
            @Override
            public void onSuccess(FeedbackRecord record) {
                currentDetail = record;
                bindDetailUi(record);
            }

            @Override
            public void onError(String reason, String message) {
                toastApiError(reason, message);
                showList();
            }
        });
    }

    private void bindDetailUi(FeedbackRecord currentDetail) {
        TextView no = findViewById(R.id.feedbackDetailNo);
        no.setText(currentDetail.id);
        TextView time = findViewById(R.id.feedbackDetailTime);
        time.setText(formatTime(currentDetail.submitTime));
        applyStatusStyle(findViewById(R.id.feedbackDetailDot),
                findViewById(R.id.feedbackDetailStatus), currentDetail.status);

        TextView myType = findViewById(R.id.feedbackDetailMyType);
        myType.setText(currentDetail.type);
        TextView myContent = findViewById(R.id.feedbackDetailMyContent);
        myContent.setText(currentDetail.content);
        TextView myTime = findViewById(R.id.feedbackDetailMyTime);
        myTime.setText(getString(R.string.feedback_my_time_submitted,
                formatTime(currentDetail.submitTime)));
        bindDetailMyImages(currentDetail.imageUris);

        boolean hasReplyText = currentDetail.replyText != null
                && !currentDetail.replyText.isEmpty();
        boolean hasReplyImages = currentDetail.replyImageUris != null
                && !currentDetail.replyImageUris.isEmpty();
        boolean hasReply = hasReplyText || hasReplyImages;
        findViewById(R.id.feedbackDetailReplyRow)
                .setVisibility(hasReply ? View.VISIBLE : View.GONE);
        if (hasReply) {
            TextView replyText = findViewById(R.id.feedbackDetailReplyText);
            replyText.setVisibility(hasReplyText ? View.VISIBLE : View.GONE);
            if (hasReplyText) {
                replyText.setText(currentDetail.replyText);
            }
            TextView replyTime = findViewById(R.id.feedbackDetailReplyTime);
            if (currentDetail.replyTime > 0) {
                replyTime.setVisibility(View.VISIBLE);
                replyTime.setText(getString(R.string.feedback_reply_time,
                        formatTime(currentDetail.replyTime)));
            } else {
                replyTime.setVisibility(View.GONE);
            }
            ImageView replyImage = findViewById(R.id.feedbackDetailReplyImage);
            replyImage.setVisibility(View.GONE);
            if (hasReplyImages) {
                String src = currentDetail.replyImageUris.get(0);
                loadImageAsync(replyImage, src, true);
            }
        }

        boolean processing = currentDetail.status == FeedbackRecord.Status.PROCESSING
                || currentDetail.status == FeedbackRecord.Status.SUBMITTED;
        findViewById(R.id.feedbackDetailProcessingBar).setVisibility(
                processing && !hasReply ? View.VISIBLE : View.GONE);
        findViewById(R.id.feedbackDetailRepliedBar).setVisibility(
                currentDetail.canClose() ? View.VISIBLE : View.GONE);
        findViewById(R.id.feedbackDetailClosedBar).setVisibility(
                currentDetail.status == FeedbackRecord.Status.CLOSED ? View.VISIBLE : View.GONE);

        findViewById(R.id.feedbackDetailResolvedBtn).setOnClickListener(v -> showCloseConfirm());
        findViewById(R.id.feedbackDetailCloseBtn).setOnClickListener(v -> showCloseConfirm());
    }

    // ==================== 表单交互 ====================

    private void selectChip(int index) {
        selectedType = index;
        for (int i = 0; i < chips.length; i++) {
            TextView chip = (TextView) chips[i];
            boolean checked = i == index;
            chip.setBackgroundResource(checked
                    ? R.drawable.bg_feedback_chip_checked : R.drawable.bg_feedback_chip);
            chip.setTextColor(checked ? Color.WHITE : Color.parseColor("#333333"));
        }
    }

    private void handleAttachUris(List<Uri> uris) {
        for (Uri uri : uris) {
            if (attachUris.size() >= MAX_ATTACH_COUNT) {
                toast(R.string.feedback_add_image_too_many);
                break;
            }
            long size = querySize(uri);
            if (size > MAX_ATTACH_BYTES) {
                toast(R.string.feedback_image_too_large);
                continue;
            }
            if (!attachUris.contains(uri)) {
                attachUris.add(uri);
            }
        }
        renderAttachRow();
    }

    private void renderAttachRow() {
        LinearLayout row = findViewById(R.id.feedbackAttachRow);
        if (row == null) {
            return;
        }
        // 移除旧的缩略图（保留添加按钮）
        for (int i = row.getChildCount() - 1; i >= 1; i--) {
            row.removeViewAt(i);
        }
        for (int i = 0; i < attachUris.size(); i++) {
            Uri uri = attachUris.get(i);
            ImageView thumb = new ImageView(this);
            int size = dp(80);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(dp(10));
            thumb.setLayoutParams(lp);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundResource(R.drawable.bg_feedback_thumb);
            thumb.setPadding(dp(2), dp(2), dp(2), dp(2));
            thumb.setClipToOutline(false);
            Bitmap bmp = decodeImage(uri);
            if (bmp != null) {
                thumb.setImageBitmap(bmp);
            } else {
                thumb.setImageResource(R.drawable.ic_feedback_attach);
            }
            thumb.setOnLongClickListener(v -> {
                attachUris.remove(uri);
                renderAttachRow();
                return true;
            });
            row.addView(thumb);
        }
    }

    private void submitFeedback() {
        if (!FeedbackApi.isConfigured()) {
            toast(R.string.feedback_api_not_configured);
            return;
        }
        if (selectedType < 0) {
            toast(R.string.feedback_choose_type);
            return;
        }
        String content = ((EditText) findViewById(R.id.feedbackDescInput))
                .getText().toString().trim();
        int len = content.codePointCount(0, content.length());
        if (len < 10) {
            toast(R.string.feedback_desc_too_short);
            return;
        }
        if (len > 500) {
            toast(R.string.feedback_desc_too_long);
            return;
        }
        if (shareLog && !FeedbackConfig.isLogUploadEnabled()) {
            toast(R.string.feedback_log_upload_pending);
            return;
        }
        String contact = ((EditText) findViewById(R.id.feedbackContactInput))
                .getText().toString().trim();
        String feedbackType = getSelectedApiType();
        View submitBtn = findViewById(R.id.feedbackSubmitBtn);
        submitBtn.setEnabled(false);

        List<Uri> images = new ArrayList<>(attachUris);
        FeedbackApi.submitForm(this, feedbackType, content, contact, images, shareLog,
                new FeedbackApi.RecordCallback() {
                    @Override
                    public void onSuccess(FeedbackRecord record) {
                        attachUris.clear();
                        shareLog = false;
                        submitBtn.setEnabled(true);
                        showSuccess();
                    }

                    @Override
                    public void onError(String reason, String message) {
                        submitBtn.setEnabled(true);
                        toastApiError(reason, message);
                    }
                });
    }

    private String getSelectedApiType() {
        return FeedbackTypes.apiLabelForChipIndex(selectedType);
    }

    // ==================== 状态样式 ====================

    private void applyStatusStyle(View dot, TextView text, FeedbackRecord.Status status) {
        switch (status) {
            case REPLIED:
                dot.setBackgroundResource(R.drawable.bg_feedback_dot_blue);
                text.setTextColor(Color.parseColor("#0066FF"));
                text.setText(R.string.feedback_replied);
                break;
            case RESOLVED:
                dot.setBackgroundResource(R.drawable.bg_feedback_dot_blue);
                text.setTextColor(Color.parseColor("#0066FF"));
                text.setText(R.string.feedback_resolved);
                break;
            case CLOSED:
                dot.setBackgroundResource(R.drawable.bg_feedback_dot_gray);
                text.setTextColor(Color.parseColor("#6A6A6A"));
                text.setText(R.string.feedback_closed);
                break;
            case PROCESSING:
                dot.setBackgroundResource(R.drawable.bg_feedback_dot_orange);
                text.setTextColor(Color.parseColor("#FA6200"));
                text.setText(R.string.feedback_processing);
                break;
            case SUBMITTED:
            default:
                dot.setBackgroundResource(R.drawable.bg_feedback_dot_gray);
                text.setTextColor(Color.parseColor("#6A6A6A"));
                text.setText(R.string.feedback_submitted);
                break;
        }
    }

    // ==================== 弹窗 ====================

    private void showCloseConfirm() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.feedback_close_dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(dp(335), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.findViewById(R.id.feedbackCloseDialogCancel)
                .setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.feedbackCloseDialogConfirm).setOnClickListener(v -> {
            dialog.dismiss();
            if (currentDetail == null || !currentDetail.canClose()) {
                return;
            }
            final String feedbackNo = currentDetail.id;
            FeedbackApi.closeFeedback(this, feedbackNo, new FeedbackApi.VoidCallback() {
                @Override
                public void onSuccess() {
                    showDetail(feedbackNo);
                }

                @Override
                public void onError(String reason, String message) {
                    toastApiError(reason, message);
                }
            });
        });
        dialog.show();
    }

    private void showImageViewer(String uriString) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.feedback_image_viewer);
        ImageView image = dialog.findViewById(R.id.feedbackImageViewerImage);
        Bitmap bmp = decodeImageSource(uriString);
        if (bmp != null) {
            image.setImageBitmap(bmp);
        }
        dialog.findViewById(R.id.feedbackImageViewerClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void toastApiError(String reason, String message) {
        if ("feedback_api_not_configured".equals(reason)) {
            toast(R.string.feedback_api_not_configured);
        } else if ("feedback_log_upload_pending".equals(reason)) {
            toast(R.string.feedback_log_upload_pending);
        } else if ("feedback_api".equals(reason) && message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } else {
            toast(R.string.feedback_submit_failed);
        }
    }

    private Bitmap decodeImageSource(String source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        if (source.startsWith("content:") || source.startsWith("file:")) {
            return decodeImage(Uri.parse(source));
        }
        String url = FeedbackConfig.assetUrl(source);
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return decodeImageUrl(url);
        }
        return decodeImage(Uri.parse(source));
    }

    @Nullable
    private Bitmap decodeImageUrl(String urlString) {
        try {
            java.net.URL url = new java.net.URL(urlString);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(15_000);
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 工具 ====================

    private long querySize(Uri uri) {
        try (android.os.ParcelFileDescriptor pfd =
                     getContentResolver().openFileDescriptor(uri, "r")) {
            return pfd != null ? pfd.getStatSize() : 0;
        } catch (FileNotFoundException | SecurityException e) {
            String size = null;
            try {
                size = queryMediaColumn(uri);
            } catch (Exception ignored) {
            }
            return parseSize(size);
        } catch (Exception e) {
            return 0;
        }
    }

    private String queryMediaColumn(Uri uri) {
        android.database.Cursor c = getContentResolver().query(uri,
                new String[]{"_size"}, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getString(0);
                }
            } finally {
                c.close();
            }
        }
        return null;
    }

    private long parseSize(String value) {
        try {
            return value != null ? Long.parseLong(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 缩略解码：目标边长约 240dp，避免大图 OOM。 */
    @Nullable
    private Bitmap decodeImage(Uri uri) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    return null;
                }
                BitmapFactory.decodeStream(is, null, opts);
            }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return null;
            }
            int target = dp(240);
            int sample = 1;
            while (opts.outWidth / sample > target * 2 || opts.outHeight / sample > target * 2) {
                sample *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    return null;
                }
                return BitmapFactory.decodeStream(is, null, opts);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void bindDetailMyImages(List<String> paths) {
        View scroll = findViewById(R.id.feedbackDetailMyImagesScroll);
        LinearLayout row = findViewById(R.id.feedbackDetailMyImages);
        row.removeAllViews();
        if (paths == null || paths.isEmpty()) {
            scroll.setVisibility(View.GONE);
            return;
        }
        scroll.setVisibility(View.VISIBLE);
        int size = dp(72);
        int gap = dp(8);
        for (String path : paths) {
            ImageView thumb = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            if (row.getChildCount() > 0) {
                lp.setMarginStart(gap);
            }
            thumb.setLayoutParams(lp);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundResource(R.drawable.bg_feedback_thumb);
            final String src = path;
            thumb.setOnClickListener(v -> showImageViewer(src));
            loadImageAsync(thumb, src, false);
            row.addView(thumb);
        }
    }

    private void loadImageAsync(ImageView target, String source, boolean centerCrop) {
        if (source == null || source.isEmpty()) {
            return;
        }
        FeedbackApi.runAsync(() -> {
            Bitmap bmp = decodeImageSource(source);
            if (bmp == null) {
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || target.getWindowToken() == null) {
                    return;
                }
                target.setImageBitmap(bmp);
                if (centerCrop) {
                    target.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private static String formatListSummary(String summary) {
        if (summary == null || summary.isEmpty()) {
            return "";
        }
        if (summary.codePointCount(0, summary.length()) >= 50 && !summary.endsWith("...")) {
            return summary + "...";
        }
        return summary;
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(millis));
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ==================== 列表 holder ====================

    private static class FeedbackListHolder extends RecyclerView.ViewHolder {
        TextView type;
        TextView time;
        TextView content;
        View dot;
        TextView status;

        FeedbackListHolder(View itemView) {
            super(itemView);
            type = itemView.findViewById(R.id.feedbackItemType);
            time = itemView.findViewById(R.id.feedbackItemTime);
            content = itemView.findViewById(R.id.feedbackItemContent);
            dot = itemView.findViewById(R.id.feedbackItemDot);
            status = itemView.findViewById(R.id.feedbackItemStatus);
        }
    }
}