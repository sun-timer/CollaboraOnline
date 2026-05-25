package org.libreoffice.androidapp.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.libreoffice.androidapp.R;

public class AiProfileSettingsActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_AVATAR = 9301;

    private ImageView avatarView;
    private TextView nicknameView;
    private SharedPreferences prefs;
    private boolean hasChanged = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_profile_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        prefs = AiSettingsStore.prefs(this);
        avatarView = findViewById(R.id.profileAvatarValue);
        nicknameView = findViewById(R.id.profileNicknameValue);

        ImageButton backButton = findViewById(R.id.profileBackButton);
        View avatarRow = findViewById(R.id.profileAvatarRow);
        View nicknameRow = findViewById(R.id.profileNicknameRow);

        backButton.setOnClickListener(v -> finishWithResult());
        avatarRow.setOnClickListener(v -> chooseAvatar());
        nicknameRow.setOnClickListener(v -> showNicknameDialog());

        renderProfile();
    }

    private void renderProfile() {
        String nickname = prefs.getString(AiSettingsStore.KEY_PROFILE_NAME, "orangepi");
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = "orangepi";
        }
        nicknameView.setText(nickname);

        String avatarUri = prefs.getString(AiSettingsStore.KEY_PROFILE_AVATAR_URI, "");
        if (avatarUri == null || avatarUri.isEmpty()) {
            avatarView.setImageResource(R.drawable.drawer_header);
            return;
        }

        try {
            avatarView.setImageURI(Uri.parse(avatarUri));
        } catch (Exception ignored) {
            avatarView.setImageResource(R.drawable.drawer_header);
        }
    }

    private void chooseAvatar() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_AVATAR);
    }

    private void showNicknameDialog() {
        final View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_nickname, null, false);
        final EditText input = dialogView.findViewById(R.id.nicknameInput);
        input.setText(nicknameView.getText());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.ai_edit_nickname)
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (value.isEmpty()) {
                        value = "orangepi";
                    }
                    prefs.edit().putString(AiSettingsStore.KEY_PROFILE_NAME, value).apply();
                    nicknameView.setText(value);
                    hasChanged = true;
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_AVATAR || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
        }
        prefs.edit().putString(AiSettingsStore.KEY_PROFILE_AVATAR_URI, uri.toString()).apply();
        avatarView.setImageURI(uri);
        hasChanged = true;
    }

    @Override
    public void onBackPressed() {
        finishWithResult();
    }

    private void finishWithResult() {
        if (hasChanged) {
            setResult(RESULT_OK);
        }
        finish();
    }
}
