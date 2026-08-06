/* -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- */
/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.libreoffice.androidapp;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class AboutDialogFragment extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        @SuppressLint("InflateParams") //suppressed because the view will be placed in a dialog
        View view = getActivity().getLayoutInflater().inflate(R.layout.about, null, false);

        ImageButton backButton = view.findViewById(R.id.aboutBackButton);
        backButton.setOnClickListener(v -> dismiss());

        TextView versionRow = view.findViewById(R.id.aboutVersionRow);
        String versionName = getVersionName();
        if (versionName == null || versionName.isEmpty()) {
            versionRow.setText(R.string.about_version_info);
        } else {
            versionRow.setText(versionName);
        }

        View feedbackRow = view.findViewById(R.id.aboutFeedbackRow);
        feedbackRow.setOnClickListener(v -> {
            // TODO: 问题和建议反馈入口（当前占位，后续接反馈链接/邮箱）
        });

        Dialog dialog = new Dialog(getActivity());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
        }
        return dialog;
    }

    private String getVersionName() {
        try {
            return getActivity().getPackageManager()
                    .getPackageInfo(getActivity().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

}

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
