package com.locai.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {

    public interface OnModelSelectedListener {
        void onModelSelected(ModelInfo model);
    }

    private final ModelInfo[]            models;
    private final OnModelSelectedListener listener;
    private       int                    selectedPos = 0;

    public ModelAdapter(ModelInfo[] models, OnModelSelectedListener listener) {
        this.models   = models;
        this.listener = listener;
    }

    public ModelInfo getSelected() {
        return models[selectedPos];
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_model_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        ModelInfo m     = models[pos];
        boolean   sel   = pos == selectedPos;
        Context   ctx   = h.itemView.getContext();

        h.tvName.setText(m.name);
        h.tvSubtitle.setText(m.subtitle);
        h.tvDescription.setText(m.description);
        h.tvSize.setText(m.getSizeLabel());
        h.tvSpeed.setText(m.getSpeedLabel());
        h.tvQuality.setText(starsFor(m.qualityStars));

        // Selected state
        h.itemView.setBackground(ctx.getDrawable(
                sel ? R.drawable.bg_model_card_selected : R.drawable.bg_model_card));
        h.tvName.setTextColor(ctx.getColor(
                sel ? R.color.white : R.color.text_secondary_light));
        h.selectionDot.setVisibility(sel ? View.VISIBLE : View.INVISIBLE);

        h.itemView.setOnClickListener(v -> {
            int prev = selectedPos;
            selectedPos = h.getAdapterPosition();
            notifyItemChanged(prev);
            notifyItemChanged(selectedPos);
            listener.onModelSelected(m);
        });
    }

    @Override
    public int getItemCount() { return models.length; }

    private String starsFor(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < n ? "★" : "☆");
        return sb.toString();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvSubtitle, tvDescription, tvSize, tvSpeed, tvQuality;
        View     selectionDot;

        ViewHolder(View v) {
            super(v);
            tvName        = v.findViewById(R.id.tvModelName);
            tvSubtitle    = v.findViewById(R.id.tvModelSubtitle);
            tvDescription = v.findViewById(R.id.tvModelDescription);
            tvSize        = v.findViewById(R.id.tvModelSize);
            tvSpeed       = v.findViewById(R.id.tvModelSpeed);
            tvQuality     = v.findViewById(R.id.tvModelQuality);
            selectionDot  = v.findViewById(R.id.selectionDot);
        }
    }
}
