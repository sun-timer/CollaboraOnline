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
import org.libreoffice.androidapp.feedback.FeedbackRecord;
import org.libreoffice.androidapp.feedback.FeedbackStore;
import org.libreoffice.androidlib.SystemUiHelper;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 问题反馈与建议（Figma 429:20679 起全部反馈页面）。
 * 页面流：表单 → 提交成功 → 反馈记录列表 → 详情(状态/回复/关闭)。
 * 后端 API 未接入：提交/列表/回复均为本地闭环（见 FeedbackApi 占位）。
 */
public class FeedbackActivity extends AppCompatActivity {

    private static final long MAX_ATTACH_BYTES = 5L * 1024 * 1024; // 图片超过5MB

    // 表单状态
    private final int[] chipIds = {
            R.id.feedbackTypeChip0, R.id.feedbackTypeChip1,
            R.id.feedbackTypeChip2, R.id.feedbackTypeChip3};
    private final String[] typeValues = {"bug", "idea", "ux", "other"};
    private int selectedType = -1;
    private final List<Uri> attachUris = new ArrayList<>();
    private boolean shareLog;
    private View[] chips;

    // 列表
    private RecyclerView.Adapter<FeedbackListHolder> listAdapter;
    private List<FeedbackRecord> records = new ArrayList<>();

    // 详情
    private FeedbackRecord currentDetail;
    private Bitmap replyImageBitmap;

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
        showForm();
    }

    // ==================== 页面切换 ====================

    private void showForm() {
        setContentView(R.layout.feedback_main);
        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.feedbackMainHeader), 0);
        applyImeAwareInsets(findViewById(R.id.feedbackMainRoot));

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
        findViewById(R.id.feedbackLogRow).setOnClickListener(v -> {
            shareLog = !shareLog;
            logCheck.setImageResource(shareLog
                    ? R.drawable.ic_feedback_checkbox_on : R.drawable.ic_feedback_checkbox_off);
        });
    }

    /**
     * 表单页根布局：合并 底部导航 + 软键盘 insets 到根 padding，
     * 键盘弹出时顶起内容并切换 IME 配色（避免遮挡输入框/提交按钮）。
     */
    private void applyImeAwareInsets(View root) {
        final boolean light = SystemUiHelper.isLightMode(this);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int bottom = Math.max(nav, ime);
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            if (ime > 0) {
                SystemUiHelper.applyImeChrome(this, light);
            } else {
                SystemUiHelper.applyDocumentChrome(this, light);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void showSuccess() {
        setContentView(R.layout.feedback_success);
        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.feedbackHeader), 0);
        SystemUiHelper.applyNavigationBarPadding(findViewById(R.id.feedbackSuccessRoot), 0);

        findViewById(R.id.feedbackHeaderBackBtn).setOnClickListener(v -> finish());
        findViewById(R.id.feedbackHeaderRecordsEntry).setOnClickListener(v -> showList());
        findViewById(R.id.feedbackSuccessViewRecordsBtn).setOnClickListener(v -> showList());
    }

    private void showList() {
        records = FeedbackStore.load(this);
        if (records.isEmpty()) {
            showEmpty();
            return;
        }
        setContentView(R.layout.feedback_list);
        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.feedbackListHeader), 0);
        SystemUiHelper.applyNavigationBarPadding(findViewById(R.id.feedbackListRoot), 0);

        findViewById(R.id.feedbackListBackBtn).setOnClickListener(v -> showForm());

        RecyclerView list = findViewById(R.id.feedbackList);
        list.setLayoutManager(new LinearLayoutManager(this));
        listAdapter = new RecyclerView.Adapter<FeedbackListHolder>() {
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
                h.content.setText(r.content);
                applyStatusStyle(h.dot, h.status, r.status);
                h.itemView.setOnClickListener(v -> showDetail(r.id));
            }

            @Override
            public int getItemCount() {
                return records.size();
            }
        };
        list.setAdapter(listAdapter);
    }

    private void showEmpty() {
        setContentView(R.layout.feedback_empty);
        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.feedbackEmptyHeader), 0);
        SystemUiHelper.applyNavigationBarPadding(findViewById(R.id.feedbackEmptyRoot), 0);

        findViewById(R.id.feedbackEmptyHeaderBackBtn).setOnClickListener(v -> showForm());
        findViewById(R.id.feedbackEmptyBackBtn).setOnClickListener(v -> showForm());
    }

    private void showDetail(String id) {
        currentDetail = FeedbackStore.find(this, id);
        if (currentDetail == null) {
            showList();
            return;
        }
        setContentView(R.layout.feedback_detail);
        SystemUiHelper.enableEdgeToEdge(this);
        SystemUiHelper.applyDocumentChrome(this, SystemUiHelper.isLightMode(this));
        SystemUiHelper.applyStatusBarPadding(findViewById(R.id.feedbackDetailHeader), 0);
        // 本地模拟：提交 30 秒后推进为「已回复」，演示 处理中 → 已回复 全流程。
        // API 接入后由服务端状态驱动，此段删除。
        if ((currentDetail.status == FeedbackRecord.Status.SUBMITTED
                || currentDetail.status == FeedbackRecord.Status.PROCESSING)
                && System.currentTimeMillis() - currentDetail.submitTime > 30_000L) {
            FeedbackApi.simulateReply(this, currentDetail);
        }
        SystemUiHelper.applyNavigationBarPadding(findViewById(R.id.feedbackDetailRoot), 0);

        findViewById(R.id.feedbackDetailBackBtn).setOnClickListener(v -> showList());

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
        boolean replied = currentDetail.status == FeedbackRecord.Status.REPLIED;


        findViewById(R.id.feedbackDetailReplyRow)
                .setVisibility(replied ? View.VISIBLE : View.GONE);
        if (replied) {
            TextView replyText = findViewById(R.id.feedbackDetailReplyText);
            replyText.setText(currentDetail.replyText);
            TextView replyTime = findViewById(R.id.feedbackDetailReplyTime);
            replyTime.setText(getString(R.string.feedback_reply_time, formatTime(currentDetail.replyTime)));
            ImageView replyImage = findViewById(R.id.feedbackDetailReplyImage);
            if (!currentDetail.replyImageUris.isEmpty()) {
                Bitmap bmp = decodeImage(Uri.parse(currentDetail.replyImageUris.get(0)));
                if (bmp != null) {
                    replyImage.setImageBitmap(bmp);
                    replyImage.setVisibility(View.VISIBLE);
                    replyImage.setOnClickListener(v -> showImageViewer(currentDetail.replyImageUris.get(0)));
                }
            }
        }

        // 底部状态栏
        findViewById(R.id.feedbackDetailProcessingBar).setVisibility(
                currentDetail.status == FeedbackRecord.Status.PROCESSING
                        || currentDetail.status == FeedbackRecord.Status.SUBMITTED
                        ? View.VISIBLE : View.GONE);
        findViewById(R.id.feedbackDetailRepliedBar).setVisibility(
                replied ? View.VISIBLE : View.GONE);
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
            if (attachUris.size() >= 6) {
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
        if (selectedType < 0) {
            toast(R.string.feedback_choose_type);
            return;
        }
        String content = ((EditText) findViewById(R.id.feedbackDescInput))
                .getText().toString().trim();
        if (content.length() < 10) {
            toast(R.string.feedback_desc_too_short);
            return;
        }
        String contact = ((EditText) findViewById(R.id.feedbackContactInput))
                .getText().toString().trim();

        FeedbackRecord record = new FeedbackRecord();
        record.id = FeedbackApi.newFeedbackId(System.currentTimeMillis());
        record.type = getSelectedTypeText();
        record.submitTime = System.currentTimeMillis();
        record.content = content;
        record.contact = contact;
        record.shareLog = shareLog;
        for (Uri uri : attachUris) {
            record.imageUris.add(uri.toString());
        }
        record.status = FeedbackRecord.Status.PROCESSING;
        // API 接入前：本地落库；回复状态由详情页按时间模拟推进
        FeedbackApi.submit(this, record);

        attachUris.clear();
        showSuccess();
    }

    private String getSelectedTypeText() {
        if (selectedType < 0 || selectedType >= chips.length) {
            return "";
        }
        return ((TextView) chips[selectedType]).getText().toString();
    }

    // ==================== 状态样式 ====================

    private void applyStatusStyle(View dot, TextView text, FeedbackRecord.Status status) {
        switch (status) {
            case REPLIED:
                dot.setBackgroundResource(R.drawable.bg_feedback_dot_blue);
                text.setTextColor(Color.parseColor("#0066FF"));
                text.setText(R.string.feedback_replied);
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
            if (currentDetail != null) {
                currentDetail.status = FeedbackRecord.Status.CLOSED;
                FeedbackStore.update(this, currentDetail);
            }
            showDetail(currentDetail != null ? currentDetail.id : null);
        });
        dialog.show();
    }

    private void showImageViewer(String uriString) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.feedback_image_viewer);
        ImageView image = dialog.findViewById(R.id.feedbackImageViewerImage);
        Bitmap bmp = decodeImage(Uri.parse(uriString));
        if (bmp != null) {
            image.setImageBitmap(bmp);
        }
        dialog.findViewById(R.id.feedbackImageViewerClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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