package org.libreoffice.androidlib;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** A centered shadow with an explicit gutter, including on pre-28 hardware canvases. */
public final class RoundedPopupShadow extends Drawable {
    private final int gutter;
    private final float radius;
    private final float blur;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap image;

    public RoundedPopupShadow(Resources resources, float radiusDp) {
        float density = resources.getDisplayMetrics().density;
        gutter = Math.round(24 * density);
        radius = radiusDp * density;
        blur = 22.35f * density;
    }

    public int gutter() { return gutter; }

    @Override
    protected void onBoundsChange(Rect bounds) {
        image = null;
        if (bounds.width() <= gutter * 2 || bounds.height() <= gutter * 2) return;
        image = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0xFFFFFFFF);
        shadow.setShadowLayer(blur, 0, 0, 0x4D000000);
        new Canvas(image).drawRoundRect(new RectF(gutter, gutter,
                bounds.width() - gutter, bounds.height() - gutter), radius, radius, shadow);
    }

    @Override public boolean getPadding(Rect padding) { padding.set(gutter, gutter, gutter, gutter); return true; }
    @Override public void draw(Canvas canvas) {
        if (image != null) canvas.drawBitmap(image, getBounds().left, getBounds().top, paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
