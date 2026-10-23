package org.libreoffice.androidlib;

import android.content.Context;
import android.view.ViewGroup;

/**
 * 设置项开关统一样式（查找设置、本地模型、功能面板等）：{@link LolibSettingsSwitch} 44×26 + 正圆滑块。
 */
public final class SwitchUiHelper {

    private SwitchUiHelper() {
    }

    public static LolibSettingsSwitch createSettingsSwitch(Context context) {
        return new LolibSettingsSwitch(context);
    }

    public static ViewGroup.LayoutParams createSettingsSwitchLayoutParams(Context context) {
        int trackWidth = context.getResources().getDimensionPixelSize(
                R.dimen.lolib_settings_switch_track_width);
        int trackHeight = context.getResources().getDimensionPixelSize(
                R.dimen.lolib_settings_switch_track_height);
        return new ViewGroup.LayoutParams(trackWidth, trackHeight);
    }
}
