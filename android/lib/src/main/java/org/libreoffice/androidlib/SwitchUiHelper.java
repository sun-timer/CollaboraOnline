package org.libreoffice.androidlib;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

/**
 * 设置项开关统一样式（查找设置、本地模型等），对齐 44×26 轨道 + 白底描边滑块。
 */
public final class SwitchUiHelper {

    private SwitchUiHelper() {
    }

    public static void applySettingsSwitch(SwitchCompat toggle) {
        if (toggle == null) {
            return;
        }
        toggle.setTrackResource(R.drawable.lolib_bg_find_toggle_track);
        toggle.setThumbResource(R.drawable.lolib_bg_find_toggle_thumb);
        toggle.setShowText(false);
        toggle.setSplitTrack(false);
        // 避免 Material 主题 tint 把自定义 drawable 洗成浅色、滑块「看不清」
        toggle.setThumbTintList(null);
        toggle.setTrackTintList(null);
        int minWidth = toggle.getResources().getDimensionPixelSize(
                R.dimen.lolib_settings_switch_min_width);
        int padding = toggle.getResources().getDimensionPixelSize(
                R.dimen.lolib_settings_switch_padding);
        toggle.setSwitchMinWidth(minWidth);
        toggle.setSwitchPadding(padding);
        int thumbPx = toggle.getResources().getDimensionPixelSize(
                R.dimen.lolib_settings_switch_thumb_size);
        Drawable thumb = ContextCompat.getDrawable(toggle.getContext(),
                R.drawable.lolib_ic_switch_thumb_circle);
        if (thumb != null) {
            thumb.setBounds(0, 0, thumbPx, thumbPx);
            toggle.setThumbDrawable(thumb);
        }
        toggle.post(() -> {
            Drawable current = toggle.getThumbDrawable();
            if (current != null) {
                current.setBounds(0, 0, thumbPx, thumbPx);
            }
        });
    }

    /** 与轨道 44×26 一致，避免父布局把 Switch 横向拉伸导致滑块变椭圆。 */
    public static ViewGroup.LayoutParams createSettingsSwitchLayoutParams(Context context) {
        int trackWidth = context.getResources().getDimensionPixelSize(
                R.dimen.lolib_settings_switch_track_width);
        int trackHeight = context.getResources().getDimensionPixelSize(
                R.dimen.lolib_settings_switch_track_height);
        return new ViewGroup.LayoutParams(trackWidth, trackHeight);
    }
}
