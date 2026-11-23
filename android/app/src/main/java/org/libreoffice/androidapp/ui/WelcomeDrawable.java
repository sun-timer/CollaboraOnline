package org.libreoffice.androidapp.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.WindowInsets;

import org.libreoffice.androidapp.R;

/** Responsive cold-start artwork from Figma 546:28044 and 692:29587. */
final class WelcomeDrawable extends Drawable {
    private final Context context;
    private final Bitmap artwork;
    private final Bitmap logo;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    WelcomeDrawable(Context context) {
        this.context = context;
        density = context.getResources().getDisplayMetrics().density;
        artwork = BitmapFactory.decodeResource(context.getResources(), R.drawable.lolib_img_welcome_art);
        logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.lolib_img_welcome_brand);
        label.setColor(0xFF101010);
        label.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || artwork == null || logo == null) return;
        int topInset = 0;
        int bottomInset = 0;
        if (context instanceof Activity) {
            WindowInsets insets = ((Activity) context).getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                topInset = insets.getStableInsetTop();
                bottomInset = insets.getStableInsetBottom();
            }
        }
        // The empty bottom strip supplies the original full-width background colors.
        canvas.drawBitmap(artwork, new Rect(0, artwork.getHeight() - 4,
                artwork.getWidth(), artwork.getHeight()), new RectF(bounds), paint);
        float unit = Math.min(density, Math.min(bounds.width() / 375f, bounds.height() / 360f));
        float safeTop = bounds.top + topInset;
        float safeBottom = bounds.bottom - bottomInset;
        float logoSize = 60 * unit;
        label.setTextSize(14 * unit);
        float labelBaseline = safeBottom - 16 * unit - label.descent();
        float logoBottom = labelBaseline + label.ascent() - 20 * unit;
        float logoTop = logoBottom - logoSize;
        float subjectBottom = logoTop - 24 * unit;
        int sourceHeight = Math.round(artwork.getHeight() * 0.72f);
        float scale = Math.min(bounds.width() / (float) artwork.getWidth(),
                Math.max(1, subjectBottom - safeTop) / sourceHeight);
        float subjectWidth = artwork.getWidth() * scale;
        float subjectHeight = sourceHeight * scale;
        float subjectLeft = bounds.exactCenterX() - subjectWidth / 2;
        canvas.drawBitmap(artwork, new Rect(0, 0, artwork.getWidth(), sourceHeight),
                new RectF(subjectLeft, safeTop, subjectLeft + subjectWidth, safeTop + subjectHeight), paint);
        float logoLeft = bounds.exactCenterX() - logoSize / 2;
        canvas.drawBitmap(logo, null, new RectF(logoLeft, logoTop, logoLeft + logoSize, logoBottom), paint);
        canvas.drawText("AI Office｜智启办公，高效随行", bounds.exactCenterX(), labelBaseline, label);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); label.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
