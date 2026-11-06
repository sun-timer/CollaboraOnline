package org.libreoffice.androidlib;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Camera and adjustable crop surface for the existing text-extraction request. */
@SuppressWarnings("deprecation")
public class TextExtractCameraActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "TextExtractCamera";
    private static final int PICK_IMAGE = 1;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextureView preview;
    private FrameLayout viewport;
    private ImageView still;
    private CropOverlay crop;
    private TextView shutter;
    private TextView source;
    private Camera camera;
    private Camera.CameraInfo cameraInfo;
    private Camera.Size previewSize;
    private Bitmap image;
    private Uri outputUri;
    private boolean resumed;
    private boolean busy;
    private boolean destroyed;
    private RectF capturedArea;
    private int capturedWidth;
    private int capturedHeight;
    private boolean capturePending;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        outputUri = getIntent().getParcelableExtra(MediaStore.EXTRA_OUTPUT);
        if (outputUri == null) { finish(); return; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.lolib_ic_impress_subpage_back);
        back.setColorFilter(Color.WHITE);
        back.setPadding(dp(20), dp(16), dp(20), dp(16));
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(64), dp(56)));
        TextView title = label("文字提取", 18);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        root.addView(header);
        viewport = new FrameLayout(this);
        viewport.setClipChildren(true);
        preview = new TextureView(this);
        preview.setSurfaceTextureListener(this);
        viewport.addView(preview, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        still = new ImageView(this);
        still.setScaleType(ImageView.ScaleType.FIT_CENTER);
        still.setVisibility(View.GONE);
        viewport.addView(still, new FrameLayout.LayoutParams(-1, -1));
        crop = new CropOverlay();
        viewport.addView(crop, new FrameLayout.LayoutParams(-1, -1));
        viewport.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> resizePreview());
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(16), dp(20), dp(16), dp(20));
        source = label("相册导入", 14);
        setAlbumIcon();
        source.setCompoundDrawablePadding(dp(8));
        source.setOnClickListener(v -> {
            if (busy) return;
            if (image != null) { retake(); return; }
            try {
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(pick, PICK_IMAGE);
            } catch (android.content.ActivityNotFoundException e) { error("未找到相册应用", e); }
        });
        footer.addView(source, new LinearLayout.LayoutParams(0, dp(76), 1));
        shutter = label("", 16);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(Color.WHITE);
        circle.setStroke(dp(7), Color.LTGRAY);
        shutter.setBackground(circle);
        shutter.setTextColor(Color.BLACK);
        shutter.setContentDescription("拍照");
        shutter.setOnClickListener(v -> { if (!busy) { if (image == null) capture(); else confirmCrop(); } });
        footer.addView(shutter, new LinearLayout.LayoutParams(dp(75), dp(75)));
        footer.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        root.addView(footer);
        setContentView(root);
    }

    private void setAlbumIcon() {
        android.graphics.drawable.Drawable icon = getDrawable(R.drawable.lolib_ic_figma_camera_album);
        icon.setBounds(0, 0, dp(24), dp(24));
        source.setCompoundDrawables(null, icon, null, null);
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (preview != null && preview.isAvailable() && image == null && !busy) openCamera();
    }

    @Override protected void onPause() {
        resumed = false;
        releaseCamera();
        if (capturePending) { capturePending = false; busy = false; }
        super.onPause();
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        if (image == null && !busy) { releaseCamera(); viewport.post(this::openCamera); }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        worker.shutdownNow();
        releaseCamera();
        if (image != null) image.recycle();
        super.onDestroy();
    }

    private void openCamera() {
        if (!resumed || camera != null || !preview.isAvailable() || image != null || busy) return;
        try {
            int cameraId = 0;
            cameraInfo = new Camera.CameraInfo();
            for (int id = 0; id < Camera.getNumberOfCameras(); id++) {
                Camera.getCameraInfo(id, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { cameraId = id; break; }
            }
            Camera.getCameraInfo(cameraId, cameraInfo);
            camera = Camera.open(cameraId);
            Camera.Parameters params = camera.getParameters();
            int orientation = cameraOrientation();
            double ratio = (orientation % 180 == 0)
                    ? (double) viewport.getWidth() / Math.max(1, viewport.getHeight())
                    : (double) viewport.getHeight() / Math.max(1, viewport.getWidth());
            previewSize = chooseSize(params.getSupportedPreviewSizes(), ratio, 1920 * 1080);
            params.setPreviewSize(previewSize.width, previewSize.height);
            Camera.Size picture = chooseSize(params.getSupportedPictureSizes(),
                    (double) previewSize.width / previewSize.height, 12000000);
            params.setPictureSize(picture.width, picture.height);
            List<String> focus = params.getSupportedFocusModes();
            if (focus != null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            int rotation = displayDegrees();
            params.setRotation(cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                    ? (cameraInfo.orientation - rotation + 360) % 360
                    : (cameraInfo.orientation + rotation) % 360);
            camera.setParameters(params);
            camera.setDisplayOrientation(orientation);
            camera.setPreviewTexture(preview.getSurfaceTexture());
            resizePreview();
            camera.startPreview();
            Log.i(TAG, "camera_ready");
        } catch (Exception e) { releaseCamera(); error("无法启动相机，可使用相册导入", e); }
    }

    private Camera.Size chooseSize(List<Camera.Size> sizes, double ratio, int maxPixels) {
        Camera.Size best = sizes.get(0);
        double bestScore = Double.MAX_VALUE;
        for (Camera.Size size : sizes) {
            int pixels = size.width * size.height;
            double score = Math.abs((double) size.width / size.height - ratio) * 1000
                    + (pixels > maxPixels ? 10000 : 0) - pixels / 100000000.0;
            if (score < bestScore) { best = size; bestScore = score; }
        }
        return best;
    }

    private int displayDegrees() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            default: return 0;
        }
    }

    private int cameraOrientation() {
        int rotation = displayDegrees();
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - (cameraInfo.orientation + rotation) % 360) % 360;
        }
        return (cameraInfo.orientation - rotation + 360) % 360;
    }

    private void resizePreview() {
        if (previewSize == null || cameraInfo == null || viewport.getWidth() == 0) return;
        int width = previewSize.width, height = previewSize.height;
        if (cameraOrientation() % 180 != 0) { width = previewSize.height; height = previewSize.width; }
        float scale = Math.max((float) viewport.getWidth() / width, (float) viewport.getHeight() / height);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) preview.getLayoutParams();
        int targetWidth = Math.round(width * scale), targetHeight = Math.round(height * scale);
        if (params.width != targetWidth || params.height != targetHeight) {
            params.width = targetWidth; params.height = targetHeight;
            preview.setLayoutParams(params);
        }
    }

    private void releaseCamera() {
        if (camera == null) return;
        try { camera.stopPreview(); } catch (RuntimeException ignored) { }
        camera.release(); camera = null;
    }

    private void capture() {
        if (camera == null) { openCamera(); return; }
        busy = true;
        capturePending = true;
        capturedArea = new RectF(crop.area);
        capturedWidth = viewport.getWidth();
        capturedHeight = viewport.getHeight();
        Camera captureCamera = camera;
        try {
            captureCamera.takePicture(null, null, (jpeg, ignored) -> {
                if (destroyed || camera != captureCamera) return;
                capturePending = false;
                releaseCamera();
                loadImage(jpeg, null);
            });
        } catch (RuntimeException e) { capturePending = false; busy = false; error("拍摄失败，请重试", e); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_IMAGE && result == RESULT_OK && data != null && data.getData() != null) {
            releaseCamera(); busy = true; loadImage(null, data.getData());
        }
    }

    private void loadImage(byte[] jpeg, Uri uri) {
        if (destroyed) return;
        boolean mirrorCameraImage = jpeg != null && cameraInfo != null
                && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
        worker.execute(() -> {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream input = openImage(jpeg, uri)) { BitmapFactory.decodeStream(input, null, options); }
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                Bitmap bitmap;
                try (InputStream input = openImage(jpeg, uri)) { bitmap = BitmapFactory.decodeStream(input, null, options); }
                if (bitmap == null) throw new java.io.IOException("Unsupported image");
                Matrix matrix = new Matrix();
                int orientation = ExifInterface.ORIENTATION_NORMAL;
                try (InputStream input = openImage(jpeg, uri)) {
                    orientation = new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
                } catch (java.io.IOException e) {
                    Log.d(TAG, "image_without_exif");
                }
                    switch (orientation) {
                        case 2: matrix.setScale(-1, 1); break;
                        case 3: matrix.setRotate(180); break;
                        case 4: matrix.setScale(1, -1); break;
                        case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                        case 6: matrix.setRotate(90); break;
                        case 7: matrix.setRotate(270); matrix.postScale(-1, 1); break;
                        case 8: matrix.setRotate(270); break;
                        default: break;
                    }
                if (mirrorCameraImage) matrix.postScale(-1, 1);
                Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (oriented != bitmap) bitmap.recycle();
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) { oriented.recycle(); return; }
                    releaseCamera();
                    if (image != null) image.recycle();
                    image = oriented; busy = false;
                    still.setImageBitmap(image); still.setVisibility(View.VISIBLE);
                    crop.resetToImage();
                    if (jpeg != null && capturedArea != null && capturedWidth > 0 && capturedHeight > 0) {
                        float scale = Math.max((float) capturedWidth / image.getWidth(),
                                (float) capturedHeight / image.getHeight());
                        float offsetX = (capturedWidth - image.getWidth() * scale) / 2;
                        float offsetY = (capturedHeight - image.getHeight() * scale) / 2;
                        RectF bounds = crop.bounds;
                        crop.area.set(bounds.left + (capturedArea.left - offsetX) / scale / image.getWidth() * bounds.width(),
                                bounds.top + (capturedArea.top - offsetY) / scale / image.getHeight() * bounds.height(),
                                bounds.left + (capturedArea.right - offsetX) / scale / image.getWidth() * bounds.width(),
                                bounds.top + (capturedArea.bottom - offsetY) / scale / image.getHeight() * bounds.height());
                        crop.area.intersect(bounds);
                        crop.invalidate();
                    }
                    preview.setVisibility(View.INVISIBLE);
                    source.setText("重拍"); source.setCompoundDrawables(null, null, null, null);
                    shutter.setText("识别"); shutter.setContentDescription("识别选中区域");
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!destroyed) { busy = false; error("图片读取失败，请重试", e); openCamera(); } });
            }
        });
    }

    private InputStream openImage(byte[] jpeg, Uri uri) throws java.io.IOException {
        if (jpeg != null) return new ByteArrayInputStream(jpeg);
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) throw new java.io.IOException("Image unavailable");
        return input;
    }

    private void retake() {
        still.setImageDrawable(null); still.setVisibility(View.GONE);
        if (image != null) { image.recycle(); image = null; }
        crop.resetToImage();
        preview.setVisibility(View.VISIBLE);
        source.setText("相册导入");
        setAlbumIcon();
        shutter.setText(""); shutter.setContentDescription("拍照");
        openCamera();
    }

    private void confirmCrop() {
        if (image == null || viewport.getWidth() == 0 || viewport.getHeight() == 0) return;
        busy = true;
        RectF area = new RectF(crop.area);
        Bitmap input = image;
        float scale = Math.min((float) viewport.getWidth() / input.getWidth(), (float) viewport.getHeight() / input.getHeight());
        float left = (viewport.getWidth() - input.getWidth() * scale) / 2;
        float top = (viewport.getHeight() - input.getHeight() * scale) / 2;
        int x = Math.max(0, Math.min(input.getWidth() - 1, Math.round((area.left - left) / scale)));
        int y = Math.max(0, Math.min(input.getHeight() - 1, Math.round((area.top - top) / scale)));
        int width = Math.max(1, Math.min(input.getWidth() - x, Math.round(area.width() / scale)));
        int height = Math.max(1, Math.min(input.getHeight() - y, Math.round(area.height() / scale)));
        // Own a copy before the worker starts, so Activity destruction can release the displayed bitmap.
        Bitmap region = Bitmap.createBitmap(input, x, y, width, height);
        Bitmap selected = region.copy(Bitmap.Config.ARGB_8888, false);
        if (region != input) region.recycle();
        worker.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(outputUri, "w")) {
                if (output == null || !selected.compress(Bitmap.CompressFormat.JPEG, 92, output)) throw new java.io.IOException("Image write failed");
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    Log.i(TAG, "crop_ready width=" + width + " height=" + height);
                    setResult(RESULT_OK, new Intent().setData(outputUri)); finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { if (!destroyed) { busy = false; error("图片保存失败，请重试", e); } });
            } finally { selected.recycle(); }
        });
    }

    private void error(String message, Exception e) {
        Log.w(TAG, message, e);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) { openCamera(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) { resizePreview(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { releaseCamera(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) { }

    private final class CropOverlay extends View {
        private final RectF area = new RectF();
        private final RectF bounds = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lastX, lastY;
        private int edges;

        CropOverlay() { super(TextExtractCameraActivity.this); setContentDescription("拖动边框调整文字识别区域"); }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            RectF previous = new RectF(area);
            RectF oldBounds = new RectF(bounds);
            resetToImage();
            if (image != null && oldBounds.width() > 0 && oldBounds.height() > 0) {
                area.set(bounds.left + (previous.left - oldBounds.left) / oldBounds.width() * bounds.width(),
                        bounds.top + (previous.top - oldBounds.top) / oldBounds.height() * bounds.height(),
                        bounds.left + (previous.right - oldBounds.left) / oldBounds.width() * bounds.width(),
                        bounds.top + (previous.bottom - oldBounds.top) / oldBounds.height() * bounds.height());
            }
        }

        void resetToImage() {
            int w = getWidth(), h = getHeight();
            bounds.set(0, 0, w, h);
            if (image != null) {
                float scale = Math.min((float) w / image.getWidth(), (float) h / image.getHeight());
                float width = image.getWidth() * scale, height = image.getHeight() * scale;
                bounds.set((w - width) / 2, (h - height) / 2, (w + width) / 2, (h + height) / 2);
                area.set(bounds);
            } else { area.set(dp(12), h * 0.12f, w - dp(12), h * 0.88f); }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(0x88000000);
            canvas.drawRect(0, 0, getWidth(), area.top, paint);
            canvas.drawRect(0, area.bottom, getWidth(), getHeight(), paint);
            canvas.drawRect(0, area.top, area.left, area.bottom, paint);
            canvas.drawRect(area.right, area.top, getWidth(), area.bottom, paint);
            paint.setColor(0xFF1278D9); paint.setStrokeWidth(dp(1)); paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(area, paint);
            for (int i = 1; i < 3; i++) {
                float x = area.left + area.width() * i / 3, y = area.top + area.height() * i / 3;
                canvas.drawLine(x, area.top, x, area.bottom, paint);
                canvas.drawLine(area.left, y, area.right, y, paint);
            }
            paint.setStrokeWidth(dp(3));
            float length = dp(20);
            for (float x : new float[] {area.left, area.right}) {
                for (float y : new float[] {area.top, area.bottom}) {
                    canvas.drawLine(x, y, x + (x == area.left ? length : -length), y, paint);
                    canvas.drawLine(x, y, x, y + (y == area.top ? length : -length), paint);
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (busy) return true;
            float x = event.getX(), y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!new RectF(area.left - dp(24), area.top - dp(24), area.right + dp(24), area.bottom + dp(24)).contains(x, y)) return false;
                    edges = (Math.abs(x - area.left) < dp(24) ? 1 : 0) | (Math.abs(x - area.right) < dp(24) ? 2 : 0)
                            | (Math.abs(y - area.top) < dp(24) ? 4 : 0) | (Math.abs(y - area.bottom) < dp(24) ? 8 : 0);
                    lastX = x; lastY = y; return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = x - lastX, dy = y - lastY;
                    if (edges == 0) {
                        area.offset(Math.max(bounds.left - area.left, Math.min(bounds.right - area.right, dx)),
                                Math.max(bounds.top - area.top, Math.min(bounds.bottom - area.bottom, dy)));
                    } else {
                        float minWidth = Math.min(dp(64), bounds.width()), minHeight = Math.min(dp(64), bounds.height());
                        if ((edges & 1) != 0) area.left = Math.max(bounds.left, Math.min(area.right - minWidth, area.left + dx));
                        if ((edges & 2) != 0) area.right = Math.min(bounds.right, Math.max(area.left + minWidth, area.right + dx));
                        if ((edges & 4) != 0) area.top = Math.max(bounds.top, Math.min(area.bottom - minHeight, area.top + dy));
                        if ((edges & 8) != 0) area.bottom = Math.min(bounds.bottom, Math.max(area.top + minHeight, area.bottom + dy));
                    }
                    lastX = x; lastY = y; invalidate(); return true;
                case MotionEvent.ACTION_UP: performClick(); return true;
                case MotionEvent.ACTION_CANCEL: return true;
                default: return true;
            }
        }

        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
