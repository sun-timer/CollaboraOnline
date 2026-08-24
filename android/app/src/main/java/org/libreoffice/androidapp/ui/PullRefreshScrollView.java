package org.libreoffice.androidapp.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * NestedScrollView with Figma-style pull-to-refresh: drag down at top reveals an arrow
 * and "松手刷新页面"; release past threshold triggers refresh.
 */
public class PullRefreshScrollView extends NestedScrollView {
    public interface OnRefreshListener {
        void onRefresh();
    }

    private static final float PULL_DAMPING = 0.55f;

    private View refreshHeader;
    private ImageView refreshArrow;
    private LinearLayout scrollContent;
    private OnRefreshListener refreshListener;

    private int triggerDistancePx;
    private int maxPullDistancePx;
    private float pullStartY;
    private float currentPull;
    private boolean pulling;
    private boolean refreshing;

    public PullRefreshScrollView(Context context) {
        super(context);
        init();
    }

    public PullRefreshScrollView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PullRefreshScrollView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        triggerDistancePx = (int) (density * 80f);
        maxPullDistancePx = (int) (density * 120f);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    public void bindRefreshViews(View refreshHeader, ImageView refreshArrow, LinearLayout scrollContent) {
        this.refreshHeader = refreshHeader;
        this.refreshArrow = refreshArrow;
        this.scrollContent = scrollContent;
        resetPullVisuals(false);
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.refreshListener = listener;
    }

    public void setRefreshing(boolean refreshing) {
        this.refreshing = refreshing;
        setEnabled(!refreshing);
        if (!refreshing) {
            resetPullVisuals(true);
        } else if (refreshHeader != null) {
            refreshHeader.setVisibility(VISIBLE);
            refreshHeader.setAlpha(1f);
            if (refreshArrow != null) {
                refreshArrow.animate().cancel();
                refreshArrow.setRotation(0f);
                refreshArrow.animate()
                        .rotation(360f)
                        .setDuration(800L)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            if (this.refreshing && refreshArrow != null) {
                                refreshArrow.setRotation(0f);
                                refreshArrow.animate().rotation(360f).setDuration(800L).start();
                            }
                        })
                        .start();
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (refreshing) {
            return super.onInterceptTouchEvent(ev);
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && getScrollY() <= 0 && pulling) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (refreshing) {
            return true;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pullStartY = ev.getY();
                pulling = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (getScrollY() <= 0) {
                    float dy = (ev.getY() - pullStartY) * PULL_DAMPING;
                    if (dy > 0f || pulling) {
                        pulling = true;
                        currentPull = Math.min(Math.max(0f, dy), maxPullDistancePx);
                        applyPullVisuals(currentPull);
                        if (currentPull > 0f && getScrollY() > 0) {
                            scrollTo(0, 0);
                        }
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pulling) {
                    finishPull();
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onTouchEvent(ev);
    }

    private void finishPull() {
        if (currentPull >= triggerDistancePx) {
            setRefreshing(true);
            if (refreshListener != null) {
                refreshListener.onRefresh();
            }
        } else {
            resetPullVisuals(true);
        }
        pulling = false;
        currentPull = 0f;
    }

    private void applyPullVisuals(float pull) {
        if (refreshHeader == null || scrollContent == null) {
            return;
        }
        refreshHeader.setVisibility(pull > 0f ? VISIBLE : INVISIBLE);
        refreshHeader.setAlpha(Math.min(1f, pull / triggerDistancePx));
        float headerHeight = Math.min(pull, triggerDistancePx);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) refreshHeader.getLayoutParams();
        if (lp != null) {
            lp.height = (int) headerHeight;
            refreshHeader.setLayoutParams(lp);
        }
        scrollContent.setTranslationY(pull);
        if (refreshArrow != null) {
            float progress = Math.min(1f, pull / triggerDistancePx);
            refreshArrow.setRotation(progress * 180f);
        }
    }

    private void resetPullVisuals(boolean animate) {
        if (refreshHeader == null || scrollContent == null) {
            return;
        }
        Runnable reset = () -> {
            if (refreshHeader != null) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) refreshHeader.getLayoutParams();
                if (lp != null) {
                    lp.height = 0;
                    refreshHeader.setLayoutParams(lp);
                }
                refreshHeader.setVisibility(INVISIBLE);
                refreshHeader.setAlpha(0f);
            }
            if (scrollContent != null) {
                scrollContent.setTranslationY(0f);
            }
            if (refreshArrow != null) {
                refreshArrow.animate().cancel();
                refreshArrow.setRotation(0f);
            }
            currentPull = 0f;
        };
        if (animate && (scrollContent.getTranslationY() > 0f || currentPull > 0f)) {
            scrollContent.animate().translationY(0f).setDuration(180L).withEndAction(reset).start();
        } else {
            reset.run();
        }
    }
}
