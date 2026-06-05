package org.libreoffice.androidlib.ai;

import android.content.res.Configuration;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class AiPanelController {
    private static final String TAG = "AiPanelController";

    public interface ScrollCallbacks {
        boolean canMessagesScrollConsume(float deltaY);

        void onTouchCancelled();
    }

    private float scrollLastY = Float.NaN;
    private boolean scrollLastDisallow = false;
    private long scrollInterceptLogAt = 0L;

    public void configureBottomSheet(BottomSheetDialog dialog, int screenHeight, int screenWidth, int orientation) {
        if (dialog == null) {
            return;
        }
        FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        ViewGroup.LayoutParams layoutParams = bottomSheet.getLayoutParams();
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE || screenWidth > screenHeight;
        float targetRatio = isLandscape ? 0.52f : 0.62f;
        int targetHeight = (int) (screenHeight * targetRatio);
        if (layoutParams != null) {
            layoutParams.height = targetHeight;
            bottomSheet.setLayoutParams(layoutParams);
        }
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setHideable(false);
        behavior.setDraggable(false);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        Log.i(TAG, "ai_sheet_drag_disabled");
        Log.i(TAG, "ai_sheet_force_expanded height=" + targetHeight
                + " screenHeight=" + screenHeight
                + " screenWidth=" + screenWidth
                + " orientation=" + orientation
                + " ratio=" + targetRatio);
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
