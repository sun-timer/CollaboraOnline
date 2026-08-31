package org.libreoffice.androidlib;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import java.io.InputStream;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.libreoffice.androidlib.ai.AiDialogHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 插入图片选择/预览 (Figma 3100:61787 选择页 / 3141:23253 预览页)。
 * 相册最近 120 张缩略图后台解码 + 多选 + 逐张 base64 插入(经 Host.insertLocalImages)。
 */
public class ImageInsertSheetController {

    private static final int MAX_GALLERY_ITEMS = 120;

    private final Host host;
    private BottomSheetDialog sheet;
    private Dialog previewDialog;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Gallery state
    private final List<Long> imageIds = new ArrayList<>();
    private final List<Uri> imageUris = new ArrayList<>();
    private final Map<Long, Bitmap> thumbCache = new HashMap<>();
    private final Set<Integer> selected = new HashSet<>();
    private GridAdapter gridAdapter;
    private RecyclerView grid;
    private TextView confirmButton;
    private TextView emptyText;

    private final Runnable loadRunnable = new Runnable() {
        @Override
        public void run() {
            List<Long> ids = new ArrayList<>();
            List<Uri> uris = new ArrayList<>();
            Activity activity = host.getActivity();
            if (activity == null) return;

            String[] projection = {"_id"};
            try {
                Cursor cursor = activity.getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, "date_added DESC");
                if (cursor != null) {
                    int idCol = cursor.getColumnIndexOrThrow("_id");
                    while (cursor.moveToNext() && ids.size() < MAX_GALLERY_ITEMS) {
                        long id = cursor.getLong(idCol);
                        ids.add(id);
                        uris.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id)));
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                Log.w("ImageInsertSheet", "gallery query failed", e);
            }

            mainHandler.post(() -> {
                imageIds.clear();
                imageUris.clear();
                imageIds.addAll(ids);
                imageUris.addAll(uris);
                selected.clear();
                thumbCache.clear();
                if (gridAdapter != null) {
                    gridAdapter.notifyDataSetChanged();
                }
                updateConfirmLabel();
                if (emptyText != null) {
                    emptyText.setVisibility(ids.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
        }
    };

    public ImageInsertSheetController(Host host) {
        this.host = host;
    }

    public void show() {
        Activity activity = host.getActivity();
        if (activity == null) return;
        sheet = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity).inflate(R.layout.lolib_dialog_insert_image, null, false);
        sheet.setContentView(content);

        content.findViewById(R.id.insertImageBackBtn).setOnClickListener(v -> sheet.dismiss());
        TextView albumTab = content.findViewById(R.id.insertImageAlbumTab);
        TextView cameraTab = content.findViewById(R.id.insertImageCameraTab);
        albumTab.setOnClickListener(v -> new Thread(loadRunnable, "gallery-refresh").start());
        cameraTab.setOnClickListener(v -> {
            sheet.dismiss();
            host.launchCameraPicker();
        });

        grid = content.findViewById(R.id.insertImageGrid);
        grid.setLayoutManager(new GridLayoutManager(activity, 3));
        gridAdapter = new GridAdapter(activity);
        grid.setAdapter(gridAdapter);

        emptyText = content.findViewById(R.id.insertImageEmptyText);
        confirmButton = content.findViewById(R.id.insertImageConfirmBtn);
        confirmButton.setOnClickListener(v -> confirmInsert());
        content.findViewById(R.id.insertImagePreviewBtn).setOnClickListener(v -> {
            if (!selected.isEmpty()) {
                showPreview(imageUris.get(selected.iterator().next()));
            }
        });

        updateConfirmLabel();
        new Thread(loadRunnable, "gallery-load").start();
        sheet.setOnShowListener(d -> anchorInsertImageSheet(content));
        sheet.show();
    }

    private void anchorInsertImageSheet(View content) {
        if (sheet == null || content == null) return;
        AiDialogHelper.applyNoDimScrim(sheet);
        FrameLayout bottomSheet = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundResource(android.R.color.white);
        }
        BottomSheetAnchorHelper.Options options =
                BottomSheetAnchorHelper.overlayDocumentSheetOptions(content.getContext(), "ImageInsertSheet");
        BottomSheetAnchorHelper.clearAppliedHeight(sheet);
        BottomSheetAnchorHelper.expandRatio(sheet, 0.85f, options);
    }

    private void updateConfirmLabel() {
        if (confirmButton != null) {
            confirmButton.setText(host.getActivity().getString(R.string.insert_image_confirm, selected.size()));
        }
    }

    private void confirmInsert() {
        if (selected.isEmpty()) return;
        List<Uri> uris = new ArrayList<>();
        for (Integer pos : selected) {
            if (pos >= 0 && pos < imageUris.size()) {
                uris.add(imageUris.get(pos));
            }
        }
        if (sheet != null) {
            sheet.dismiss();
        }
        host.insertLocalImages(uris);
    }

    public void showPreview(Uri uri) {
        Activity activity = host.getActivity();
        if (activity == null) return;
        previewDialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View content = LayoutInflater.from(activity).inflate(R.layout.lolib_dialog_insert_image_preview, null, false);
        previewDialog.setContentView(content);

        ImageView image = content.findViewById(R.id.insertImagePreviewImage);
        image.setImageURI(uri);

        content.findViewById(R.id.insertImagePreviewBackBtn).setOnClickListener(v -> previewDialog.dismiss());

        TextView previewConfirm = content.findViewById(R.id.insertImagePreviewConfirmBtn);
        previewConfirm.setText(activity.getString(R.string.insert_image_confirm, selected.size()));
        previewConfirm.setOnClickListener(v -> {
            previewDialog.dismiss();
            confirmInsert();
        });

        ImageView checkbox = content.findViewById(R.id.insertImagePreviewCheckbox);
        int index = imageUris.indexOf(uri);
        checkbox.setImageResource(selected.contains(index)
                ? R.drawable.lolib_ic_checkbox_circle_checked
                : R.drawable.lolib_ic_checkbox_circle_unchecked);
        checkbox.setOnClickListener(v -> {
            if (index < 0) return;
            if (selected.contains(index)) {
                selected.remove(index);
                checkbox.setImageResource(R.drawable.lolib_ic_checkbox_circle_unchecked);
            } else {
                selected.add(index);
                checkbox.setImageResource(R.drawable.lolib_ic_checkbox_circle_checked);
            }
            if (gridAdapter != null) {
                gridAdapter.notifyItemChanged(index);
            }
            updateConfirmLabel();
            previewConfirm.setText(activity.getString(R.string.insert_image_confirm, selected.size()));
        });

        previewDialog.show();
    }

    private void toggleSelected(int position) {
        if (position < 0 || position >= imageIds.size()) return;
        if (selected.contains(position)) {
            selected.remove(position);
        } else {
            selected.add(position);
        }
        if (gridAdapter != null) {
            gridAdapter.notifyItemChanged(position);
        }
        updateConfirmLabel();
    }

    private void loadThumbnail(int position, ImageView imageView) {
        if (position < 0 || position >= imageIds.size()) return;
        long id = imageIds.get(position);
        Activity activity = host.getActivity();
        if (activity == null) return;

        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                InputStream in = activity.getContentResolver().openInputStream(uri);
                if (in == null) return;
                BitmapFactory.decodeStream(in, null, opts);
                in.close();

                int target = (int) (220.0f * activity.getResources().getDisplayMetrics().density);
                int sample = 1;
                while (opts.outWidth / sample > target * 2 || opts.outHeight / sample > target * 2) {
                    sample *= 2;
                }

                opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                in = activity.getContentResolver().openInputStream(uri);
                if (in != null) {
                    bitmap = BitmapFactory.decodeStream(in, null, opts);
                    in.close();
                }
            } catch (Exception e) {
                Log.w("ImageInsertSheet", "thumb decode failed id=" + id, e);
            }

            if (bitmap == null) return;
            Bitmap result = bitmap;
            mainHandler.post(() -> {
                thumbCache.put(id, result);
                imageView.setImageBitmap(result);
            });
        }, "image-thumb-" + position).start();
    }

    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.Holder> {

        private final Context context;

        GridAdapter(Context context) {
            this.context = context;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(context).inflate(R.layout.lolib_insert_image_item, parent, false);
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) item.getLayoutParams();
            if (lp != null) {
                lp.topMargin = (int) (3.0f * context.getResources().getDisplayMetrics().density);
                lp.bottomMargin = (int) (3.0f * context.getResources().getDisplayMetrics().density);
            }
            Holder holder = new Holder(item);
            item.setOnClickListener(v -> toggleSelected(holder.getBindingAdapterPosition()));
            return holder;
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            holder.check.setImageResource(selected.contains(position)
                    ? R.drawable.lolib_ic_checkbox_circle_checked
                    : R.drawable.lolib_ic_checkbox_circle_unchecked);
            long id = imageIds.get(position);
            Bitmap cached = thumbCache.get(id);
            if (cached != null) {
                holder.image.setImageBitmap(cached);
            } else {
                holder.image.setImageDrawable(null);
                loadThumbnail(position, holder.image);
            }
        }

        @Override
        public int getItemCount() {
            return imageIds.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final ImageView check;

            Holder(View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.insertImageItemImage);
                check = itemView.findViewById(R.id.insertImageItemCheck);
            }
        }
    }

    public interface Host {
        Activity getActivity();
        void insertLocalImages(List<Uri> uris);
        void launchCameraPicker();
    }
}