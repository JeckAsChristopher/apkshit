package com.locai.app;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

public class SplashActivity extends AppCompatActivity {

    private static final int    REQ_STORAGE   = 101;
    private static final long   MIN_MODEL_BYTES = 100L * 1024 * 1024; // 100 MB

    private TextView    tvStatus;
    private TextView    tvTagline;
    private ProgressBar progressBar;
    private View        logoContainer;
    private View        dotLeft, dotCenter, dotRight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        tvTagline     = findViewById(R.id.tvTagline);
        tvStatus      = findViewById(R.id.tvStatus);
        progressBar   = findViewById(R.id.progressBar);
        logoContainer = findViewById(R.id.logoContainer);
        dotLeft       = findViewById(R.id.dotLeft);
        dotCenter     = findViewById(R.id.dotCenter);
        dotRight      = findViewById(R.id.dotRight);

        animateLogoIn();
        new Handler(Looper.getMainLooper()).postDelayed(this::checkPermissions, 900);
    }

    // ─── Permission gate ─────────────────────────────────────────────────────

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ — need MANAGE_EXTERNAL_STORAGE for /sdcard access
            if (!Environment.isExternalStorageManager()) {
                showPermissionDialog();
                return;
            }
        } else {
            // Android 6-10 — runtime READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, REQ_STORAGE);
                return;
            }
        }
        // Permission already granted — proceed
        startChecks();
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("Storage Permission Required")
                .setMessage(
                    "LOCAI needs access to your storage to load the AI model from:\n\n" +
                    ModelDownloader.MODEL_FILE +
                    "\n\nTap 'Grant Access' and enable 'Allow access to manage all files'.")
                .setPositiveButton("Grant Access", (d, w) -> {
                    // Opens the special All Files Access settings page
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQ_STORAGE);
                })
                .setNegativeButton("Exit", (d, w) -> { finishAffinity(); System.exit(0); })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startChecks();
            } else {
                new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                        .setTitle("Permission Denied")
                        .setMessage("LOCAI cannot run without storage access to load the model.")
                        .setPositiveButton("Retry", (d, w) -> checkPermissions())
                        .setNegativeButton("Exit", (d, w) -> { finishAffinity(); System.exit(0); })
                        .setCancelable(false).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_STORAGE) {
            // Re-check after returning from settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && Environment.isExternalStorageManager()) {
                startChecks();
            } else {
                showPermissionDialog(); // still not granted
            }
        }
    }

    // ─── Checks ──────────────────────────────────────────────────────────────

    private void startChecks() {
        if (!StorageChecker.hasSufficientStorage()) {
            showStorageError(StorageChecker.getAvailableBytes());
            return;
        }

        File model = new File(ModelDownloader.MODEL_FILE);

        if (!model.exists()) {
            showNoWeightsDialog("No model file found at:\n" + ModelDownloader.MODEL_FILE);
            return;
        }

        if (model.length() < MIN_MODEL_BYTES) {
            model.delete();
            showNoWeightsDialog(
                "Incomplete model file (" + StorageChecker.formatBytes(model.length()) + ").\n\n" +
                "The previous download was interrupted. Please download again.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        startDotAnimation();
        loadModel();
    }

    // ─── Load ─────────────────────────────────────────────────────────────────

    private void loadModel() {
        tvStatus.setText("Loading model\u2026");
        LLMEngine.getInstance().load(this, new LLMEngine.LoadCallback() {
            @Override public void onProgress(String status, int progress) {
                runOnUiThread(() -> {
                    tvStatus.setText(status);
                    animateProgressTo(progress);
                });
            }
            @Override public void onReady() {
                runOnUiThread(() -> {
                    tvStatus.setText("Ready \u2713");
                    animateProgressTo(100);
                    new Handler(Looper.getMainLooper())
                            .postDelayed(SplashActivity.this::goToMain, 500);
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> showModelError(error));
            }
        });
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private void goToMain() {
        getWindow().getDecorView().animate().alpha(0f).setDuration(300)
                .withEndAction(() -> {
                    startActivity(new Intent(this, MainActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                }).start();
    }

    private void goToInstaller() {
        File model = new File(ModelDownloader.MODEL_FILE);
        if (model.exists() && model.length() < MIN_MODEL_BYTES) model.delete();

        getWindow().getDecorView().animate().alpha(0f).setDuration(250)
                .withEndAction(() -> {
                    startActivity(new Intent(this, ModelInstallActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                }).start();
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────

    private void showNoWeightsDialog(String message) {
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("No AI weights found")
                .setMessage(message + "\n\nDownload a model now — needs internet once, " +
                        "then runs fully offline.")
                .setPositiveButton("Download model", (d, w) -> goToInstaller())
                .setNegativeButton("Exit", (d, w) -> { finishAffinity(); System.exit(0); })
                .setCancelable(false).show();
    }

    private void showModelError(String error) {
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("Failed to Load Model")
                .setMessage(error + "\n\nThe model file may be corrupted. Try reinstalling.")
                .setPositiveButton("Reinstall Model", (d, w) -> {
                    new File(ModelDownloader.MODEL_FILE).delete();
                    goToInstaller();
                })
                .setNegativeButton("Continue anyway", (d, w) -> goToMain())
                .setCancelable(false).show();
    }

    private void showStorageError(long available) {
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("\u26A0 Insufficient Storage")
                .setMessage("LOCAI requires at least 1.6 GB free.\n\nAvailable: " +
                        StorageChecker.formatBytes(available) + "\nRequired:  1.6 GB")
                .setPositiveButton("Exit", (d, w) -> { finishAffinity(); System.exit(1); })
                .setCancelable(false).show();
    }

    // ─── Animations ───────────────────────────────────────────────────────────

    private void animateLogoIn() {
        logoContainer.setAlpha(0f);
        logoContainer.setScaleX(0.82f);
        logoContainer.setScaleY(0.82f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(logoContainer, "alpha",  0f, 1f),
                ObjectAnimator.ofFloat(logoContainer, "scaleX", 0.82f, 1f),
                ObjectAnimator.ofFloat(logoContainer, "scaleY", 0.82f, 1f)
        );
        set.setDuration(600);
        set.setInterpolator(new OvershootInterpolator(1.2f));
        set.start();
        tvTagline.setAlpha(0f);
        tvTagline.animate().alpha(1f).setStartDelay(550).setDuration(400).start();
    }

    private void animateProgressTo(int target) {
        ObjectAnimator anim = ObjectAnimator.ofInt(progressBar, "progress",
                progressBar.getProgress(), target);
        anim.setDuration(400);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
    }

    private void startDotAnimation() {
        animateDot(dotLeft,   0L);
        animateDot(dotCenter, 200L);
        animateDot(dotRight,  400L);
    }

    private void animateDot(View dot, long delay) {
        dot.setAlpha(0.2f);
        dot.animate().alpha(1f).scaleX(1.3f).scaleY(1.3f)
                .setStartDelay(delay).setDuration(400)
                .withEndAction(() ->
                    dot.animate().alpha(0.2f).scaleX(1f).scaleY(1f)
                        .setDuration(400)
                        .withEndAction(() -> animateDot(dot, 0))
                        .start()
                ).start();
    }
}
