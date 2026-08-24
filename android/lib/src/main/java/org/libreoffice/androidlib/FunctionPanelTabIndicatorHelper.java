package org.libreoffice.androidlib;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Positions the function-panel tab underline using view hierarchy coordinates.
 * Avoids {@link View#getLocationInWindow(int[])} which breaks on curved screens and after sheet re-anchor.
 */
public final class FunctionPanelTabIndicatorHelper {

    private FunctionPanelTabIndicatorHelper() {
    }

    public static void updateForSelectedTab(TextView selectedTab, LinearLayout tabBar, View indicator) {
        if (selectedTab == null || tabBar == null || indicator == null) {
            return;
        }
        ViewParent tabBarParent = tabBar.getParent();
        if (!(tabBarParent instanceof HorizontalScrollView)) {
            return;
        }
        HorizontalScrollView scrollView = (HorizontalScrollView) tabBarParent;
        scrollView.post(() -> {
            scrollSelectedTabIntoView(scrollView, selectedTab);
            alignIndicator(selectedTab, tabBar, scrollView, indicator);
            scrollView.postDelayed(() -> alignIndicator(selectedTab, tabBar, scrollView, indicator), 120);
        });
    }

    private static void scrollSelectedTabIntoView(HorizontalScrollView scrollView, TextView tab) {
        if (tab.getWidth() == 0 || scrollView.getWidth() == 0) {
            return;
        }
        int tabLeft = tab.getLeft();
        int tabWidth = tab.getWidth();
        int viewportWidth = scrollView.getWidth();
        int targetScroll = tabLeft - (viewportWidth - tabWidth) / 2;
        if (targetScroll < 0) {
            targetScroll = 0;
        }
        if (scrollView.getChildCount() == 0) {
            return;
        }
        View scrollContent = scrollView.getChildAt(0);
        int maxScroll = Math.max(0, scrollContent.getWidth() - viewportWidth);
        if (targetScroll > maxScroll) {
            targetScroll = maxScroll;
        }
        scrollView.smoothScrollTo(targetScroll, 0);
    }

    private static void alignIndicator(
            TextView selectedTab,
            LinearLayout tabBar,
            HorizontalScrollView scrollView,
            View indicator) {
        if (selectedTab.getWidth() == 0) {
            return;
        }
        int scrollX = scrollView.getScrollX();
        int tabCenterX = scrollView.getLeft()
                + tabBar.getLeft()
                + selectedTab.getLeft()
                + selectedTab.getWidth() / 2
                - scrollX;

        ViewGroup.LayoutParams layoutParams = indicator.getLayoutParams();
        if (!(layoutParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) layoutParams;
        int indicatorWidth = frameParams.width > 0
                ? frameParams.width
                : Math.round(24f * indicator.getResources().getDisplayMetrics().density);
        frameParams.width = indicatorWidth;
        frameParams.leftMargin = Math.max(0, tabCenterX - indicatorWidth / 2);
        frameParams.gravity = Gravity.BOTTOM | Gravity.START;
        indicator.setLayoutParams(frameParams);
        indicator.setVisibility(View.VISIBLE);
    }
}
