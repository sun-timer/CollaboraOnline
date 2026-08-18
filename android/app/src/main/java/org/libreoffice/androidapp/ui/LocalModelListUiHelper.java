package org.libreoffice.androidapp.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidlib.ai.LocalDeviceCapability;
import org.libreoffice.androidlib.ai.LocalModelManager;

import java.util.HashMap;
import java.util.Map;

/** 本地模型列表行绑定：仅在状态变化时重建，进度更新只改文字。 */
final class LocalModelListUiHelper {
    interface ModelRowListener {
        void onDownloadRequested(LocalModelManager.CatalogEntry entry);

        void onSelectRequested(LocalModelManager.CatalogEntry entry);
    }

    static final class Controller {
        private final LinearLayout container;
        private final Context context;
        private final LocalModelManager modelManager;
        private final ModelRowListener rowListener;
        private final Map<String, TextView> actionViews = new HashMap<>();

        Controller(LinearLayout container, Context context, LocalModelManager modelManager,
                ModelRowListener rowListener) {
            this.container = container;
            this.context = context;
            this.modelManager = modelManager;
            this.rowListener = rowListener;
        }

        void refreshAll() {
            if (container == null) {
                return;
            }
            container.removeAllViews();
            actionViews.clear();

            final int orange = Color.parseColor("#FA6200");
            final int textDark = Color.parseColor("#333333");
            final int textMuted = Color.parseColor("#999999");
            final LocalDeviceCapability capability = LocalDeviceCapability.assess(context);

            for (LocalModelManager.CatalogEntry entry : LocalModelManager.getCatalogEntries()) {
                View row = LayoutInflater.from(context)
                        .inflate(R.layout.item_local_model_list_row, container, false);
                TextView nameView = row.findViewById(R.id.localModelRowName);
                TextView actionView = row.findViewById(R.id.localModelRowAction);
                nameView.setText(entry.displayName);
                actionViews.put(entry.id, actionView);

                actionView.setOnClickListener(null);
                if (modelManager.isEntryDownloading(entry)) {
                    actionView.setText(context.getString(R.string.local_model_downloading_progress,
                            modelManager.getLastProgressPercent()));
                    actionView.setTextColor(orange);
                    actionView.setBackgroundResource(R.drawable.bg_local_model_action_btn_downloading);
                    actionView.setEnabled(false);
                } else if (modelManager.isEntryActive(entry)) {
                    actionView.setText(R.string.local_model_in_use);
                    actionView.setTextColor(orange);
                    actionView.setBackgroundResource(R.drawable.bg_local_model_action_btn);
                    actionView.setEnabled(false);
                } else if (modelManager.isEntryDownloaded(entry)) {
                    actionView.setText(R.string.local_model_available);
                    actionView.setTextColor(orange);
                    actionView.setBackgroundResource(R.drawable.bg_local_model_action_btn);
                    actionView.setEnabled(true);
                    actionView.setOnClickListener(v -> rowListener.onSelectRequested(entry));
                } else if (!capability.canDownloadModel(entry)) {
                    actionView.setText(R.string.local_model_config_low);
                    actionView.setTextColor(textMuted);
                    actionView.setBackgroundResource(R.drawable.bg_local_model_action_btn);
                    actionView.setEnabled(true);
                    actionView.setOnClickListener(v -> Toast.makeText(context,
                            LocalModelManager.getModelCapabilityMessage(capability, entry),
                            Toast.LENGTH_LONG).show());
                } else {
                    actionView.setText(R.string.local_model_download_btn);
                    actionView.setTextColor(textDark);
                    actionView.setBackgroundResource(R.drawable.bg_local_model_action_btn);
                    actionView.setEnabled(true);
                    actionView.setOnClickListener(v -> rowListener.onDownloadRequested(entry));
                }
                container.addView(row);
            }
        }

        void updateProgress(int percent) {
            if (!modelManager.isDownloadActive()) {
                return;
            }
            TextView actionView = actionViews.get(modelManager.getDownloadingModelId());
            if (actionView != null) {
                actionView.setText(context.getString(R.string.local_model_downloading_progress, percent));
            }
        }
    }

    private LocalModelListUiHelper() {}
}
