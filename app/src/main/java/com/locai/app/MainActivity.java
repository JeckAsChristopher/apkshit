package com.locai.app;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView    recyclerView;
    private ChatAdapter     adapter;
    private EditText        etInput;
    private ImageButton     btnSend;
    private ImageButton     btnClear;
    private TextView        tvMemoryBadge;
    private View            loadingOverlay;
    private TextView        tvLoadingStatus;

    private MemoryManager   memory;
    private StringBuilder   streamBuffer = new StringBuilder();
    private boolean         isGenerating = false;

    private final Handler   mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupRecyclerView();
        setupInput();
        setupButtons();

        memory = MemoryManager.getInstance(this);
        loadHistory();
    }

    // ─── View binding ────────────────────────────────────────────────────────

    private void bindViews() {
        recyclerView    = findViewById(R.id.recyclerView);
        etInput         = findViewById(R.id.etInput);
        btnSend         = findViewById(R.id.btnSend);
        btnClear        = findViewById(R.id.btnClear);
        tvMemoryBadge   = findViewById(R.id.tvMemoryBadge);
        loadingOverlay  = findViewById(R.id.loadingOverlay);
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus);
    }

    private void setupRecyclerView() {
        adapter      = new ChatAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recyclerView.setLayoutManager(lm);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b < ob) scrollToBottom();
        });
    }

    private void setupInput() {
        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                btnSend.setEnabled(s.toString().trim().length() > 0 && !isGenerating);
                animateSendButton(!s.toString().trim().isEmpty());
            }
        });
    }

    private void setupButtons() {
        btnSend.setOnClickListener(v -> sendMessage());

        btnClear.setOnClickListener(v -> confirmClear());
    }

    // ─── Load history ────────────────────────────────────────────────────────

    private void loadHistory() {
        List<Message> history = memory.loadAll();
        adapter.setMessages(history);
        updateMemoryBadge();
        if (!history.isEmpty()) {
            recyclerView.post(this::scrollToBottom);
        }
    }

    // ─── Send message ────────────────────────────────────────────────────────

    private void sendMessage() {
        String input = etInput.getText().toString().trim();
        if (input.isEmpty() || isGenerating) return;

        // Clear input
        etInput.setText("");
        isGenerating = true;
        btnSend.setEnabled(false);

        // Add user message to UI + DB
        Message userMsg = new Message(Message.ROLE_USER, input);
        memory.save(userMsg);
        adapter.addMessage(userMsg);
        updateMemoryBadge();
        scrollToBottom();

        // Show typing indicator
        adapter.addTypingIndicator();
        scrollToBottom();

        // Load recent history as context
        List<Message> context = memory.loadLastN(40);
        streamBuffer.setLength(0);

        // Generate
        LLMEngine.getInstance().generate(context, input, new LLMEngine.GenerateCallback() {
            @Override
            public void onToken(String token) {
                streamBuffer.append(token);
                mainHandler.post(() -> {
                    adapter.updateLastAiMessage(streamBuffer.toString());
                    scrollToBottom();
                });
            }

            @Override
            public void onComplete(String fullResponse) {
                mainHandler.post(() -> {
                    // Persist AI response
                    Message aiMsg = new Message(Message.ROLE_ASSISTANT, fullResponse);
                    memory.save(aiMsg);
                    adapter.updateLastAiMessage(fullResponse);
                    updateMemoryBadge();
                    isGenerating = false;
                    btnSend.setEnabled(!etInput.getText().toString().trim().isEmpty());
                    scrollToBottom();
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    adapter.updateLastAiMessage("⚠ " + error);
                    isGenerating = false;
                    btnSend.setEnabled(true);
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ─── Clear memory ────────────────────────────────────────────────────────

    private void confirmClear() {
        new AlertDialog.Builder(this, R.style.Theme_LOCAI_Dialog)
                .setTitle("Clear Memory?")
                .setMessage("This will delete all conversation history permanently. LOCAI will forget everything.")
                .setPositiveButton("Clear", (d, w) -> {
                    memory.clearAll();
                    adapter.setMessages(new java.util.ArrayList<>());
                    adapter.resetAnimations();
                    updateMemoryBadge();
                    Toast.makeText(this, "Memory cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void scrollToBottom() {
        int count = adapter.getItemCount();
        if (count > 0) recyclerView.smoothScrollToPosition(count - 1);
    }

    private void updateMemoryBadge() {
        int count = memory.count();
        if (count == 0) {
            tvMemoryBadge.setVisibility(View.INVISIBLE);
        } else {
            tvMemoryBadge.setVisibility(View.VISIBLE);
            tvMemoryBadge.setText(count + " memories");
        }
    }

    private void animateSendButton(boolean hasText) {
        float scale = hasText ? 1f : 0.85f;
        btnSend.animate()
                .scaleX(scale).scaleY(scale)
                .setDuration(150)
                .start();
    }
}
