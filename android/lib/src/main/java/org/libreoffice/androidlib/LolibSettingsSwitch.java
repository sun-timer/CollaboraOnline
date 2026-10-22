package org.libreoffice.androidlib;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

/**
 * 44×26 设置开关：正圆 18dp 滑块，避免 {@link androidx.appcompat.widget.SwitchCompat} 把 thumb 拉成椭圆。
 */
public class LolibSettingsSwitch extends FrameLayout {

    private final ImageView trackView;
    private final ImageView thumbView;
    private boolean checked;
    private CompoundButton.OnCheckedChangeListener listener;
    private boolean broadcasting;

    public LolibSettingsSwitch(Context context) {
        this(context, null);
    }

    public LolibSettingsSwitch(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
        setFocusable(true);

        trackView = new ImageView(context);
        applyTrackDrawable();
        trackView.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(trackView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        thumbView = new ImageView(context);
        thumbView.setImageResource(R.drawable.lolib_ic_switch_thumb_circle);
        thumbView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int thumbPx = getResources().getDimensionPixelSize(
                R.dimen.lolib_settings_switch_thumb_size);
        LayoutParams thumbLp = new LayoutParams(thumbPx, thumbPx);
        thumbLp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        addView(thumbView, thumbLp);

        setOnClickListener(v -> setChecked(!checked));
        post(this::updateThumbPosition);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value) {
        if (checked == value) {
            return;
        }
        checked = value;
        applyTrackDrawable();
        updateThumbPosition();
        if (!broadcasting && listener != null) {
            listener.onCheckedChanged(null, checked);
        }
    }

    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener l) {
        listener = l;
    }

    /** 批量初始化时避免回调风暴。 */
    public void setCheckedSilent(boolean value) {
        broadcasting = true;
        setChecked(value);
        broadcasting = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = getResources().getDimensionPixelSize(R.dimen.lolib_settings_switch_track_width);
        int h = getResources().getDimensionPixelSize(R.dimen.lolib_settings_switch_track_height);
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateThumbPosition();
    }

    private void updateThumbPosition() {
        if (thumbView.getLayoutParams() == null) {
            return;
        }
        int trackW = getWidth();
        int thumbW = thumbView.getLayoutParams().width;
        if (trackW <= 0 || thumbW <= 0) {
            return;
        }
        int pad = getResources().getDimensionPixelSize(R.dimen.lolib_settings_switch_padding);
        float travel = Math.max(0, trackW - thumbW - pad * 2);
        thumbView.setTranslationX(checked ? travel + pad : pad);
    }

    private void applyTrackDrawable() {
        trackView.setImageResource(checked
                ? R.drawable.lolib_bg_find_toggle_track_on
                : R.drawable.lolib_bg_find_toggle_track_off);
    }
}
