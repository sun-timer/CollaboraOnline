package org.libreoffice.androidlib;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Figma bottom sheet panel styling: white fill, 48px top radius, shadow via elevation.
 */
public final class BottomSheetStyleHelper {

    private BottomSheetStyleHelper() {
    }

    public static void applyFigmaPanel(BottomSheetDialog dialog, View contentRoot, int elevationPx) {
        if (dialog == null) {
            return;
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        FrameLayout sheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) {
            return;
        }
        sheet.setBackgroundResource(R.drawable.lolib_bg_function_sheet_panel);
        sheet.setElevation(elevationPx);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sheet.setClipToOutline(true);
        }
        View coordinator = (View) sheet.getParent();
        if (coordinator != null) {
            coordinator.setBackgroundColor(Color.TRANSPARENT);
        }
        View touchOutside = dialog.findViewById(com.google.android.material.R.id.touch_outside);
        if (touchOutside != null) {
            touchOutside.setBackgroundColor(Color.TRANSPARENT);
        }
        if (contentRoot != null) {
            contentRoot.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
