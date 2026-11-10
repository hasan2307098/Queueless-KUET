package com.kuet.queueless.ui;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.queueless.databinding.ItemQueueTokenBinding;
import com.kuet.queueless.model.Token;

import java.util.Objects;

/** Operator's waiting list: token number, join time and a Skip button. */
public class QueueTokenAdapter extends ListAdapter<Token, QueueTokenAdapter.VH> {

    public interface OnSkip {
        void onSkip(Token token);
    }

    private static final DiffUtil.ItemCallback<Token> DIFF = new DiffUtil.ItemCallback<Token>() {
        @Override public boolean areItemsTheSame(@NonNull Token a, @NonNull Token b) {
            return Objects.equals(a.id, b.id);
        }
        @Override public boolean areContentsTheSame(@NonNull Token a, @NonNull Token b) {
            return Objects.equals(a.displayNumber, b.displayNumber)
                    && Objects.equals(a.status, b.status)
                    && a.createdMillis() == b.createdMillis();
        }
    };

    private final OnSkip listener;

    public QueueTokenAdapter(OnSkip listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemQueueTokenBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Token t = getItem(position);
        h.b.tvNumber.setText((position + 1) + ".  " + t.displayNumber);
        h.b.tvTime.setText(t.createdAt == null ? "just now" : DateFormat.format("hh:mm a", t.createdAt));
        h.b.btnSkip.setOnClickListener(v -> listener.onSkip(t));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemQueueTokenBinding b;
        VH(ItemQueueTokenBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
