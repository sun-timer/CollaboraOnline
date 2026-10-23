package org.libreoffice.androidlib;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Non-modal native information card for a tapped Writer comment or revision. */
final class WriterCommentInfoOverlay {
    interface Listener {
        void onClose();
        void onEdit(String commentId, String text);
        void onDelete(String commentId, String text);
    }

    private final Activity activity;
    private TouchThroughOverlay overlay;
    private LinearLayout cardStack;
    private final java.util.ArrayList<View> actionViews = new java.util.ArrayList<>();
    private View closeView;

    WriterCommentInfoOverlay(Activity activity) {
        this.activity = activity;
    }

    boolean isVisible() {
        return overlay != null && overlay.getParent() != null;
    }

    void show(JSONObject payload, Listener listener) {
        if (activity == null || activity.isFinishing()) return;
        hide();
        FrameLayout content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        overlay = new TouchThroughOverlay(activity);
        overlay.setFocusable(false);
        overlay.setClickable(false);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout card = new FrameLayout(activity);
        overlay.setCard(card);
        card.setFocusable(true);
        card.setClickable(true);
        card.setElevation(dp(12));
        card.setBackground(roundRect(Color.WHITE, dp(16)));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cardParams.leftMargin = dp(12);
        cardParams.rightMargin = dp(12);
        cardParams.bottomMargin = dp(16);
        overlay.addView(card, cardParams);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(16), dp(10), dp(16), dp(14));
        card.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        String kind = payload == null ? "comment" : payload.optString("kind", "comment");
        title.setText("revision".equals(kind) ? "修订" : "批注");
        title.setTextColor(Color.rgb(51, 51, 51));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(activity);
        close.setText("×");
        close.setTextSize(26);
        close.setTextColor(Color.rgb(90, 90, 90));
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭批注信息");
        closeView = close;
        close.setOnClickListener(v -> {
            if (listener != null) listener.onClose();
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(40)));
        shell.addView(header);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        shell.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.min(dp(300), availableHeight(content))));
        cardStack = new LinearLayout(activity);
        cardStack.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(cardStack, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionViews.clear();

        JSONArray comments = payload == null ? null : payload.optJSONArray("comments");
        if (comments == null || comments.length() == 0) {
            addCommentCard(cardStack, payload == null ? new JSONObject() : payload, kind, listener);
        } else {
            for (int i = 0; i < comments.length(); i++) {
                JSONObject item = comments.optJSONObject(i);
                if (item != null) addCommentCard(cardStack, item, kind, listener);
            }
        }
        content.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.bringChildToFront(overlay);
    }

    void setBusy(boolean busy) {
        if (closeView != null) closeView.setEnabled(!busy);
        for (View view : actionViews) view.setEnabled(!busy);
    }

    void hide() {
        if (overlay != null && overlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }
        overlay = null;
        cardStack = null;
        closeView = null;
        actionViews.clear();
    }

    private void addCommentCard(LinearLayout parent, JSONObject comment, String parentKind,
            Listener listener) {
        String kind = comment.optString("kind", parentKind);
        String id = comment.optString("id", "");
        String author = comment.optString("author", "");
        String dateTime = comment.optString("dateTime", "");
        String text = comment.optString("text", "");
        boolean revision = "revision".equals(kind);
        boolean canEdit = !revision && comment.optBoolean("canEdit", false)
                && comment.optBoolean("isOwnAuthor", false)
                && !comment.optBoolean("resolved", false)
                && !id.isEmpty() && !"new".equals(id);

        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(10), dp(12), dp(12));
        item.setBackground(roundRect(Color.rgb(242, 243, 245), dp(12)));
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemParams.topMargin = dp(8);
        parent.addView(item, itemParams);

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView initials = new TextView(activity);
        String trimmed = author.trim();
        initials.setText(trimmed.isEmpty() ? "?" : trimmed.substring(0, 1));
        initials.setTextColor(Color.rgb(91, 98, 107));
        initials.setGravity(Gravity.CENTER);
        initials.setTextSize(13);
        initials.setBackground(roundRect(Color.WHITE, dp(18)));
        top.addView(initials, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(8), 0, dp(4), 0);
        TextView authorView = new TextView(activity);
        authorView.setText(author.isEmpty() ? (revision ? "修订" : "批注") : author);
        authorView.setTextColor(Color.rgb(51, 51, 51));
        authorView.setTextSize(14);
        authorView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.addView(authorView);
        TextView dateView = new TextView(activity);
        dateView.setText(dateTime);
        dateView.setTextColor(Color.rgb(120, 120, 120));
        dateView.setTextSize(11);
        labels.addView(dateView);
        top.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (canEdit) {
            ImageButton edit = actionButton(R.drawable.lolib_ic_impress_comment_edit, "编辑批注");
            edit.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(id, text);
            });
            top.addView(edit, new LinearLayout.LayoutParams(dp(36), dp(36)));
            actionViews.add(edit);
            ImageButton delete = actionButton(R.drawable.lolib_ic_impress_comment_delete, "删除批注");
            delete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(id, text);
            });
            top.addView(delete, new LinearLayout.LayoutParams(dp(36), dp(36)));
            actionViews.add(delete);
        }
        item.addView(top);

        TextView body = new TextView(activity);
        body.setTextColor(Color.rgb(51, 51, 51));
        body.setTextSize(14);
        body.setPadding(dp(42), dp(8), 0, 0);
        if (revision) {
            StringBuilder detail = new StringBuilder();
            appendDetail(detail, comment.optString("revisionType", ""));
            appendDetail(detail, comment.optString("description", ""));
            appendDetail(detail, text);
            body.setText(detail.length() == 0 ? "修订信息不可用" : detail.toString());
        } else {
            body.setText(text.isEmpty() ? "（无内容）" : text);
        }
        item.addView(body);
    }

    private ImageButton actionButton(int drawableId, String description) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(drawableId);
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(description);
        button.setPadding(dp(6), dp(6), dp(6), dp(6));
        return button;
    }

    private void appendDetail(StringBuilder builder, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (builder.length() > 0) builder.append('\n');
        builder.append(value.trim());
    }

    private int availableHeight(View root) {
        int height = root.getHeight();
        return height > 0 ? Math.max(dp(120), Math.min(dp(300), height / 3)) : dp(240);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundRect(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private static final class TouchThroughOverlay extends FrameLayout {
        private View card;

        TouchThroughOverlay(Activity activity) {
            super(activity);
        }

        void setCard(View card) {
            this.card = card;
        }

        @Override
        public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            if (card != null && event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                int[] location = new int[2];
                card.getLocationOnScreen(location);
                float x = event.getRawX() - location[0];
                float y = event.getRawY() - location[1];
                if (x < 0 || y < 0 || x > card.getWidth() || y > card.getHeight()) return false;
            }
            return super.dispatchTouchEvent(event);
        }
    }
}
