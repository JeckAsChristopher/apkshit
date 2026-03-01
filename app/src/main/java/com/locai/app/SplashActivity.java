package com.locai.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class SplashActivity extends AppCompatActivity {

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
        new Handler(Looper.getMainLooper()).postDelayed(this::startChecks, 900);
    }

    private void startChecks() {
        if (!StorageChecker.hasSufficientStorage()) {
            showStorageError(StorageChecker.getAvailableBytes());
            return;
        }
        if (!modelFileExists()) {
            showNoWeightsDialog();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        startDotAnimation();
        loadModel();
    }

    private boolean modelFileExists() {
        return new File(ModelDownloader.MODEL_FILE).exists();
    }

    private void showNoWeightsDialog() {
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("No AI weights found")
                .setMessage(
                        "LOCAI needs an AI model to run.\n\n" +
                        "No model was found at:\n" + ModelDownloader.MODEL_FILE +
                        "\n\nYou can download one now — needs internet once, " +
                        "then LOCAI runs fully offline forever."
                )
                .setPositiveButton("OK — Choose a model", (d, w) -> goToInstaller())
                .setNegativeButton("Exit", (d, w) -> {
                    finishAffinity();
                    System.exit(0);
                })
                .setCancelable(false)
                .show();
    }

    private void goToInstaller() {
        getWindow().getDecorView().animate().alpha(0f).setDuration(250)
                .withEndAction(() -> {
                    startActivity(new Intent(this, ModelInstallActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                }).start();
    }

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

    private void goToMain() {
        getWindow().getDecorView().animate().alpha(0f).setDuration(300)
                .withEndAction(() -> {
                    startActivity(new Intent(this, MainActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                }).start();
    }

    private void showStorageError(long available) {
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("\u26A0 Insufficient Storage")
                .setMessage("LOCAI requires at least 1.6 GB free.\n\n" +
                        "Available: " + StorageChecker.formatBytes(available) +
                        "\nRequired:  1.6 GB")
                .setPositiveButton("Exit", (d, w) -> { finishAffinity(); System.exit(1); })
                .setCancelable(false).show();
    }

    private void showModelError(String error) {
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("Failed to Load Model")
                .setMessage(error)
                .setPositiveButton("Reinstall Model", (d, w) -> goToInstaller())
                .setNegativeButton("Continue", (d, w) -> goToMain())
                .setCancelable(false).show();
    }

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
