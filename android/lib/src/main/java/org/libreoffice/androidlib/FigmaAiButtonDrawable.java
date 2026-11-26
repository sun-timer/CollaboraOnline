package org.libreoffice.androidlib;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Background exported from Figma node 1689:60205, with native labels above it. */
public final class FigmaAiButtonDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FigmaAiButtonDrawable() {}

    @Override
    protected void onBoundsChange(Rect bounds) {
        // Preserve the exported SVG's gradient transform, including its off-canvas end.
        paint.setShader(new LinearGradient(
                bounds.left + bounds.width() * (35.7471f / 622f), bounds.top,
                bounds.left + bounds.width() * (49.5584f / 622f),
                bounds.top + bounds.height() * (130.557f / 70f),
                0xFF1DA8FF, 0xFFDD00CE, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        float radius = getBounds().height() / 2f;
        canvas.drawRoundRect(new RectF(getBounds()), radius, radius, paint);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
