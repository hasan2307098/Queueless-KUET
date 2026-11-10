package com.kuet.queueless.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.kuet.queueless.R;
import com.kuet.queueless.databinding.ItemServiceBinding;
import com.kuet.queueless.model.ServiceItem;
import com.kuet.queueless.util.WaitTimeCalculator;

import java.util.Objects;

public class ServiceAdapter extends ListAdapter<ServiceItem, ServiceAdapter.VH> {

    public interface OnServiceClick {
        void onClick(ServiceItem service);
    }

    private static final DiffUtil.ItemCallback<ServiceItem> DIFF = new DiffUtil.ItemCallback<ServiceItem>() {
        @Override public boolean areItemsTheSame(@NonNull ServiceItem a, @NonNull ServiceItem b) {
            return Objects.equals(a.id, b.id);
        }
        @Override public boolean areContentsTheSame(@NonNull ServiceItem a, @NonNull ServiceItem b) {
            return Objects.equals(a.name, b.name) && Objects.equals(a.location, b.location)
                    && a.open == b.open && a.waitingCount == b.waitingCount
                    && a.averageServiceMinutes == b.averageServiceMinutes
                    && Objects.equals(a.announcement, b.announcement)
                    && Objects.equals(a.nowServing, b.nowServing);
        }
    };

    private final OnServiceClick listener;

    public ServiceAdapter(OnServiceClick listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemServiceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ServiceItem s = getItem(position);
        h.b.tvName.setText(s.name);
        h.b.tvLocation.setText(s.location);
        h.b.tvStatus.setText(s.open ? "\u25CF Open" : "\u25CF Closed");
        h.b.tvStatus.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                s.open ? R.color.success : R.color.error));

        int est = WaitTimeCalculator.estimateMinutes(s.waitingCount, s.hasCurrent(), s.avg());
        String serving = (s.nowServing == null || s.nowServing.isEmpty()) ? "" : "Now serving " + s.nowServing + "  \u00B7  ";
        h.b.tvInfo.setText(serving + s.waitingCount + " waiting  \u00B7  new token wait: " + WaitTimeCalculator.format(est));

        boolean hasNote = s.announcement != null && !s.announcement.isEmpty();
        h.b.tvAnnouncement.setVisibility(hasNote ? android.view.View.VISIBLE : android.view.View.GONE);
        if (hasNote) h.b.tvAnnouncement.setText("\u26A0 " + s.announcement);

        h.itemView.setOnClickListener(v -> listener.onClick(s));
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemServiceBinding b;
        VH(ItemServiceBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
