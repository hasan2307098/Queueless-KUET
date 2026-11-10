package com.kuet.queueless.ui;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.queueless.R;
import com.kuet.queueless.databinding.ItemHistoryBinding;
import com.kuet.queueless.model.Token;
import com.kuet.queueless.model.TokenStatus;

import java.util.Objects;

public class HistoryAdapter extends ListAdapter<Token, HistoryAdapter.VH> {

    private static final DiffUtil.ItemCallback<Token> DIFF = new DiffUtil.ItemCallback<Token>() {
        @Override public boolean areItemsTheSame(@NonNull Token a, @NonNull Token b) {
            return Objects.equals(a.id, b.id);
        }
        @Override public boolean areContentsTheSame(@NonNull Token a, @NonNull Token b) {
            return Objects.equals(a.status, b.status) && Objects.equals(a.displayNumber, b.displayNumber);
        }
    };

    public HistoryAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Token t = getItem(position);
        h.b.tvNumber.setText(t.displayNumber);
        h.b.tvDate.setText(t.createdAt == null ? "" : DateFormat.format("dd MMM yyyy, hh:mm a", t.createdAt));
        TokenStatus st = t.statusEnum();
        h.b.tvStatus.setText(st.name());
        int color;
        switch (st) {
            case COMPLETED: color = R.color.success; break;
            case CANCELLED:
            case SKIPPED: color = R.color.error; break;
            default: color = R.color.primary; break;
        }
        h.b.tvStatus.setTextColor(ContextCompat.getColor(h.itemView.getContext(), color));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemHistoryBinding b;
        VH(ItemHistoryBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
