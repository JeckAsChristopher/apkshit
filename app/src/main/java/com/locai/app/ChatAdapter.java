package com.locai.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 0;
    private static final int VIEW_TYPE_AI   = 1;

    private final List<Message> messages = new ArrayList<>();
    private int lastAnimatedPosition = -1;

    // ─── ViewHolders ─────────────────────────────────────────────────────────

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        UserViewHolder(View v) {
            super(v);
            tvContent = v.findViewById(R.id.tvContent);
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent;
        View     typingDot1, typingDot2, typingDot3;
        View     typingContainer;
        AiViewHolder(View v) {
            super(v);
            tvContent        = v.findViewById(R.id.tvContent);
            typingContainer  = v.findViewById(R.id.typingContainer);
            typingDot1       = v.findViewById(R.id.dot1);
            typingDot2       = v.findViewById(R.id.dot2);
            typingDot3       = v.findViewById(R.id.dot3);
        }
    }

    // ─── Adapter overrides ───────────────────────────────────────────────────

    @Override
    public int getItemViewType(int pos) {
        return messages.get(pos).isUser() ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_USER) {
            View v = inf.inflate(R.layout.item_message_user, parent, false);
            return new UserViewHolder(v);
        } else {
            View v = inf.inflate(R.layout.item_message_ai, parent, false);
            return new AiViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);

        if (holder instanceof UserViewHolder) {
            UserViewHolder h = (UserViewHolder) holder;
            h.tvContent.setText(msg.getContent());
        } else {
            AiViewHolder h = (AiViewHolder) holder;
            boolean isTyping = msg.getContent().isEmpty();
            h.tvContent.setVisibility(isTyping ? View.GONE : View.VISIBLE);
            h.typingContainer.setVisibility(isTyping ? View.VISIBLE : View.GONE);
            if (!isTyping) {
                h.tvContent.setText(msg.getContent());
            }
        }

        // Slide-in animation for new items
        if (position > lastAnimatedPosition) {
            int anim = msg.isUser() ? R.anim.slide_in_right : R.anim.slide_in_left;
            holder.itemView.startAnimation(
                    AnimationUtils.loadAnimation(holder.itemView.getContext(), anim));
            lastAnimatedPosition = position;
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    // ─── Public helpers ──────────────────────────────────────────────────────

    public void setMessages(List<Message> list) {
        messages.clear();
        messages.addAll(list);
        notifyDataSetChanged();
    }

    public void addMessage(Message msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    /** Add a placeholder "typing…" bubble for the AI. */
    public int addTypingIndicator() {
        messages.add(new Message(Message.ROLE_ASSISTANT, ""));
        int pos = messages.size() - 1;
        notifyItemInserted(pos);
        return pos;
    }

    /** Update the typing placeholder with real streamed content. */
    public void updateLastAiMessage(String content) {
        if (messages.isEmpty()) return;
        Message last = messages.get(messages.size() - 1);
        if (!last.isUser()) {
            last.setContent(content);
            notifyItemChanged(messages.size() - 1);
        }
    }

    public void resetAnimations() {
        lastAnimatedPosition = -1;
    }
}
