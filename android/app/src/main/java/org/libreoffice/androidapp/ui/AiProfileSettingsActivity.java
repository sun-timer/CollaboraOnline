package org.libreoffice.androidapp.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidlib.SystemUiHelper;

import java.io.File;

public class AiProfileSettingsActivity extends AppCompatActivity {
    private static final int REQUEST_PICK_AVATAR = 9301;
    private static final int REQUEST_TAKE_AVATAR = 9302;
    private static final int PERMISSION_AVATAR_CAMERA = 9303;

    private ImageView avatarView;
    private TextView nicknameView;
    private SharedPreferences prefs;
    private boolean hasChanged = false;
    private Uri pendingAvatarCameraUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_profile_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        SystemUiHelper.applySecondaryActivityChrome(this, findViewById(R.id.profileSettingsRoot), 0, 0);

        prefs = AiSettingsStore.prefs(this);
        avatarView = findViewById(R.id.profileAvatarValue);
        nicknameView = findViewById(R.id.profileNicknameValue);

        ImageButton backButton = findViewById(R.id.profileBackButton);
        View avatarRow = findViewById(R.id.profileAvatarRow);
        View nicknameRow = findViewById(R.id.profileNicknameRow);

        backButton.setOnClickListener(v -> finishWithResult());
        avatarRow.setOnClickListener(v -> showAvatarPickerDialog());
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

    private void showAvatarPickerDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_set_avatar_shell);

        View card = dialog.findViewById(R.id.setAvatarDialogCard);
        View takePhotoOption = dialog.findViewById(R.id.setAvatarTakePhoto);
        View pickGalleryOption = dialog.findViewById(R.id.setAvatarPickGallery);
        View cancelButton = dialog.findViewById(R.id.setAvatarDialogCancel);

        if (card != null) {
            card.setOnClickListener(v -> { /* keep dialog open when tapping card */ });
        }
        Runnable dismiss = dialog::dismiss;
        cancelButton.setOnClickListener(v -> dismiss.run());
        takePhotoOption.setOnClickListener(v -> {
            dismiss.run();
            takeAvatarPhoto();
        });
        pickGalleryOption.setOnClickListener(v -> {
            dismiss.run();
            chooseAvatarFromGallery();
        });

        applyStyledDialogWindow(dialog, false);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void chooseAvatarFromGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_AVATAR);
    }

    private void takeAvatarPhoto() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_AVATAR_CAMERA);
            return;
        }
        startCameraForAvatar();
    }

    private void startCameraForAvatar() {
        try {
            File photoFile = new File(getCacheDir(), "avatar_" + System.currentTimeMillis() + ".jpg");
            pendingAvatarCameraUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingAvatarCameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_TAKE_AVATAR);
        } catch (Exception e) {
            Toast.makeText(this, R.string.ai_avatar_camera_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showNicknameDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_edit_nickname_shell);

        View card = dialog.findViewById(R.id.editNicknameDialogCard);
        EditText input = dialog.findViewById(R.id.nicknameInput);
        View closeButton = dialog.findViewById(R.id.editNicknameDialogClose);
        View cancelButton = dialog.findViewById(R.id.editNicknameDialogCancel);
        View confirmButton = dialog.findViewById(R.id.editNicknameDialogConfirm);

        input.setText(nicknameView.getText());
        input.setSelection(input.getText().length());

        if (card != null) {
            card.setOnClickListener(v -> { /* keep dialog open when tapping card */ });
        }
        Runnable dismiss = dialog::dismiss;
        closeButton.setOnClickListener(v -> dismiss.run());
        cancelButton.setOnClickListener(v -> dismiss.run());
        confirmButton.setOnClickListener(v -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (value.isEmpty()) {
                value = "orangepi";
            }
            prefs.edit().putString(AiSettingsStore.KEY_PROFILE_NAME, value).apply();
            nicknameView.setText(value);
            hasChanged = true;
            dismiss.run();
        });

        applyStyledDialogWindow(dialog, true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        dialog.show();
    }

    private void applyStyledDialogWindow(Dialog dialog, boolean showKeyboard) {
        if (showKeyboard) {
            ResponsiveUiHelper.applyKeyboardFriendlyDialogWindow(dialog);
        } else {
            ResponsiveUiHelper.applyOverlayDialogWindow(dialog);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_AVATAR_CAMERA
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraForAvatar();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        Uri uri = null;
        if (requestCode == REQUEST_PICK_AVATAR) {
            if (data == null || data.getData() == null) {
                return;
            }
            uri = data.getData();
            int flags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {
            }
        } else if (requestCode == REQUEST_TAKE_AVATAR) {
            uri = pendingAvatarCameraUri;
            pendingAvatarCameraUri = null;
            if (uri == null) {
                return;
            }
        } else {
            return;
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
