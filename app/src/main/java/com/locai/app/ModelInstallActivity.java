package com.locai.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ModelInstallActivity extends AppCompatActivity {

    // ─── Selection panel views ───────────────────────────────────────────────
    private View         selectionPanel;
    private RecyclerView rvModels;
    private TextView     tvSelectedInfo;
    private Button       btnInstall;

    // ─── Download panel views ────────────────────────────────────────────────
    private View         downloadPanel;
    private TextView     tvDownloadTitle;
    private TextView     tvByteProgress;   // "320 MB / 1.3 GB"
    private TextView     tvSpeed;          // "5.2 MB/s"
    private TextView     tvEta;            // "~3 min 40 sec remaining"
    private ProgressBar  progressBar;
    private TextView     tvPercent;
    private Button       btnCancel;

    // ─── State ───────────────────────────────────────────────────────────────
    private ModelAdapter    adapter;
    private ModelDownloader downloader;
    private ModelInfo       selectedModel;
    private ValueAnimator   pulseAnim;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_install);

        selectionPanel  = findViewById(R.id.selectionPanel);
        rvModels        = findViewById(R.id.rvModels);
        tvSelectedInfo  = findViewById(R.id.tvSelectedInfo);
        btnInstall      = findViewById(R.id.btnInstall);
        downloadPanel   = findViewById(R.id.downloadPanel);
        tvDownloadTitle = findViewById(R.id.tvDownloadTitle);
        tvByteProgress  = findViewById(R.id.tvByteProgress);
        tvSpeed         = findViewById(R.id.tvSpeed);
        tvEta           = findViewById(R.id.tvEta);
        progressBar     = findViewById(R.id.progressBar);
        tvPercent       = findViewById(R.id.tvPercent);
        btnCancel       = findViewById(R.id.btnCancel);

        downloadPanel.setVisibility(View.GONE);

        setupModelList();

        selectionPanel.setAlpha(0f);
        selectionPanel.setTranslationY(40f);
        selectionPanel.animate().alpha(1f).translationY(0f)
                .setDuration(450)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    @Override
    public void onBackPressed() {
        showExitWarning();
    }

    private void setupModelList() {
        adapter = new ModelAdapter(ModelInfo.ALL, model -> {
            selectedModel = model;
            tvSelectedInfo.setText(model.name + "  ·  " + model.getSizeLabel());
            btnInstall.setText("Install " + model.name);
        });
        rvModels.setLayoutManager(new LinearLayoutManager(this));
        rvModels.setAdapter(adapter);

        // Default selection
        selectedModel = ModelInfo.ALL[0];
        tvSelectedInfo.setText(selectedModel.name + "  ·  " + selectedModel.getSizeLabel());
        btnInstall.setText("Install " + selectedModel.name);
        btnInstall.setOnClickListener(v -> confirmInstall());
    }

    private void confirmInstall() {
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("Install " + selectedModel.name + "?")
                .setMessage(
                        "This will download one file (~" + selectedModel.getSizeLabel() +
                        ") from HuggingFace.\n\n" +
                        "• Requires internet (Wi-Fi recommended)\n" +
                        "• Saved to /storage/emulated/0/locai/model/\n" +
                        "• After this, LOCAI runs 100% offline"
                )
                .setPositiveButton("Download & Install", (d, w) -> startDownload())
                .setNegativeButton("Go back", null)
                .show();
    }

    private void startDownload() {
        selectionPanel.animate().alpha(0f).translationY(-30f).setDuration(300)
                .withEndAction(() -> {
                    selectionPanel.setVisibility(View.GONE);
                    showDownloadPanel();
                }).start();
    }

    private void showDownloadPanel() {
        downloadPanel.setVisibility(View.VISIBLE);
        downloadPanel.setAlpha(0f);
        downloadPanel.setTranslationY(40f);
        downloadPanel.animate().alpha(1f).translationY(0f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        tvDownloadTitle.setText("Installing " + selectedModel.name);
        tvByteProgress.setText("0 B / " + selectedModel.getSizeLabel());
        tvSpeed.setText("-- MB/s");
        tvEta.setText("Connecting\u2026");
        tvPercent.setText("0%");
        progressBar.setMax(1000);
        progressBar.setProgress(0);

        startPulse();

        btnCancel.setOnClickListener(v -> {
            new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                    .setTitle("Cancel Download?")
                    .setMessage("The partial file will be kept so you can resume later.")
                    .setPositiveButton("Cancel Download", (d, w) -> {
                        if (downloader != null) downloader.cancel();
                        goBackToSelection();
                    })
                    .setNegativeButton("Keep downloading", null)
                    .show();
        });

        downloader = new ModelDownloader();
        downloader.download(selectedModel, new ModelDownloader.DownloadCallback() {

            @Override
            public void onStart(long totalBytes) {
                mainHandler.post(() ->
                        tvByteProgress.setText("0 B / " + formatBytes(totalBytes)));
            }

            @Override
            public void onProgress(long bytesDone, long totalBytes,
                                   float speedMbps, long etaSec, int percent) {
                mainHandler.post(() -> updateProgress(
                        bytesDone, totalBytes, speedMbps, etaSec, percent));
            }

            @Override public void onComplete() {
                mainHandler.post(() -> onDownloadComplete());
            }

            @Override public void onError(String msg) {
                mainHandler.post(() -> onDownloadError(msg));
            }
        });
    }

    private void updateProgress(long bytesDone, long totalBytes,
                                 float speedMbps, long etaSec, int percent) {
        int permil = percent * 10;
        ObjectAnimator.ofInt(progressBar, "progress",
                progressBar.getProgress(), permil)
                .setDuration(400).start();

        tvPercent.setText(percent + "%");
        tvByteProgress.setText(formatBytes(bytesDone) + " / " + formatBytes(totalBytes));
        tvSpeed.setText(String.format("%.1f MB/s", speedMbps));

        if (etaSec < 0) {
            tvEta.setText("Calculating\u2026");
        } else if (etaSec < 60) {
            tvEta.setText("~" + etaSec + " sec remaining");
        } else {
            tvEta.setText("~" + (etaSec / 60) + " min " + (etaSec % 60) + " sec remaining");
        }
    }

    private void onDownloadComplete() {
        if (pulseAnim != null) pulseAnim.cancel();
        progressBar.setProgress(1000);
        tvPercent.setText("100%");
        tvEta.setText("Complete! \u2713");
        tvSpeed.setText("");
        btnCancel.setVisibility(View.GONE);

        mainHandler.postDelayed(() -> {
            startActivity(new Intent(this, SplashActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }, 1000);
    }

    private void onDownloadError(String msg) {
        if (pulseAnim != null) pulseAnim.cancel();
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("Download Failed")
                .setMessage(msg + "\n\nThe partial download is saved — tapping Retry will resume.")
                .setPositiveButton("Retry", (d, w) -> {
                    progressBar.setProgress(0);
                    tvPercent.setText("0%");
                    tvEta.setText("Restarting\u2026");
                    showDownloadPanel();
                })
                .setNegativeButton("Choose Different Model", (d, w) -> goBackToSelection())
                .setCancelable(false)
                .show();
    }

    private void goBackToSelection() {
        downloadPanel.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> {
                    downloadPanel.setVisibility(View.GONE);
                    selectionPanel.setVisibility(View.VISIBLE);
                    selectionPanel.setAlpha(0f);
                    selectionPanel.setTranslationY(40f);
                    selectionPanel.animate().alpha(1f).translationY(0f)
                            .setDuration(400).start();
                }).start();
    }

    private void startPulse() {
        pulseAnim = ValueAnimator.ofFloat(1f, 0.5f, 1f);
        pulseAnim.setDuration(1600);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setInterpolator(new LinearInterpolator());
        pulseAnim.addUpdateListener(a -> {
            if (tvEta != null) tvEta.setAlpha((float) a.getAnimatedValue());
        });
        pulseAnim.start();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (pulseAnim != null) pulseAnim.cancel();
    }

    private void showExitWarning() {
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("LOCAI needs a model")
                .setMessage("You must install a model to use LOCAI. Exit?")
                .setPositiveButton("Exit", (d, w) -> { finishAffinity(); System.exit(0); })
                .setNegativeButton("Stay", null).show();
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024)
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024 * 1024)
            return String.format("%.0f MB", bytes / (1024.0 * 1024));
        if (bytes >= 1024)
            return String.format("%.0f KB", bytes / 1024.0);
        return bytes + " B";
    }
}
