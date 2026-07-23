package org.libreoffice.androidlib.ai;

import android.content.res.Configuration;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class AiPanelController {
    private static final String TAG = "AiPanelController";
    /** 与 Calc「功能」面板一致，BottomSheet 贴底且占屏比例相同。 */
    private static final float SHEET_HEIGHT_RATIO = 1066f / 1624f;

    public interface ScrollCallbacks {
        boolean canMessagesScrollConsume(float deltaY);

        void onTouchCancelled();
    }

    private float scrollLastY = Float.NaN;
    private boolean scrollLastDisallow = false;
    private long scrollInterceptLogAt = 0L;

    public void configureBottomSheet(BottomSheetDialog dialog, View contentRoot,
            int screenHeight, int screenWidth, int orientation) {
        if (dialog == null) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }

        ensureBottomSheetContentMatchParent(contentRoot);

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE || screenWidth > screenHeight;
        float targetRatio = isLandscape ? 0.52f : SHEET_HEIGHT_RATIO;
        int targetHeight = Math.round(screenHeight * targetRatio);
        if (layoutParams != null) {
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = targetHeight;
            if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
                CoordinatorLayout.LayoutParams clp = (CoordinatorLayout.LayoutParams) layoutParams;
                clp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                clp.leftMargin = 0;
                clp.rightMargin = 0;
            } else if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
                marginParams.leftMargin = 0;
                marginParams.rightMargin = 0;
            }
            bottomSheet.setLayoutParams(layoutParams);
        }
        bottomSheet.setBackgroundResource(android.R.color.transparent);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setHideable(false);
        behavior.setDraggable(false);
        bottomSheet.post(() -> {
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            Log.i(TAG, "ai_sheet_force_expanded height=" + bottomSheet.getHeight()
                    + " target=" + targetHeight
                    + " screenHeight=" + screenHeight
                    + " ratio=" + targetRatio);
        });
    }

    /**
     * AI 功能面板：高度随内容 Hug，上限 maxScreenRatio（避免小屏内容过高时仍可滚动）。
     */
    public void configureBottomSheetFitContent(BottomSheetDialog dialog, View contentRoot,
            int screenHeight, int screenWidth, int orientation, float maxScreenRatio) {
        if (dialog == null || contentRoot == null) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }

        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE || screenWidth > screenHeight;
        float ratioCap = maxScreenRatio > 0f ? maxScreenRatio : (isLandscape ? 0.52f : 0.55f);
        int maxHeight = (int) (screenHeight * ratioCap);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        contentRoot.measure(widthSpec, heightSpec);
        int contentHeight = contentRoot.getMeasuredHeight();
        int targetHeight = Math.min(Math.max(contentHeight, 1), maxHeight);

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        ensureBottomSheetContentMatchParent(contentRoot);

        if (layoutParams != null) {
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = targetHeight;
            if (layoutParams instanceof CoordinatorLayout.LayoutParams) {
                CoordinatorLayout.LayoutParams clp = (CoordinatorLayout.LayoutParams) layoutParams;
                clp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                clp.leftMargin = 0;
                clp.rightMargin = 0;
            } else if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
                marginParams.leftMargin = 0;
                marginParams.rightMargin = 0;
            }
            bottomSheet.setLayoutParams(layoutParams);
        }
        bottomSheet.setBackgroundResource(android.R.color.transparent);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setHideable(false);
        behavior.setDraggable(false);
        bottomSheet.post(() -> behavior.setState(BottomSheetBehavior.STATE_EXPANDED));
        Log.i(TAG, "ai_sheet_fit_content height=" + targetHeight
                + " content=" + contentHeight
                + " max=" + maxHeight
                + " screenHeight=" + screenHeight);
    }

    private static void ensureBottomSheetContentMatchParent(View contentRoot) {
        if (contentRoot == null) {
            return;
        }
        ViewGroup.LayoutParams lp = contentRoot.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        contentRoot.setLayoutParams(lp);
    }

    public void installMessageScrollTouchPolicy(ScrollView scrollView, ScrollCallbacks callbacks) {
        if (scrollView == null || callbacks == null) {
            return;
        }
        scrollView.setOnTouchListener((v, event) -> {
            ViewParent parent = v.getParent();
            if (parent != null) {
                final int action = event.getActionMasked();
                boolean disallow = false;
                if (action == MotionEvent.ACTION_DOWN) {
                    scrollLastY = event.getY();
                    disallow = callbacks.canMessagesScrollConsume(0f);
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float previousY = Float.isNaN(scrollLastY) ? event.getY() : scrollLastY;
                    float deltaY = event.getY() - previousY;
                    scrollLastY = event.getY();
                    disallow = callbacks.canMessagesScrollConsume(deltaY);
                } else if (action == MotionEvent.ACTION_UP) {
                    scrollLastY = Float.NaN;
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    scrollLastY = Float.NaN;
                    callbacks.onTouchCancelled();
                }

                parent.requestDisallowInterceptTouchEvent(disallow);
                maybeLogScrollIntercept(action, disallow);
            }
            return false;
        });
    }

    public void resetTransientState() {
        scrollLastY = Float.NaN;
        scrollLastDisallow = false;
        scrollInterceptLogAt = 0L;
    }

    private void maybeLogScrollIntercept(int action, boolean disallow) {
        long now = android.os.SystemClock.uptimeMillis();
        if (disallow != scrollLastDisallow || now - scrollInterceptLogAt > 1200) {
            Log.i(TAG, "ai_scroll_disallow_intercept action=" + action + " disallow=" + disallow);
            scrollLastDisallow = disallow;
            scrollInterceptLogAt = now;
        }
    }
}
