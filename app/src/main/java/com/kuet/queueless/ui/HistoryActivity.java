package com.kuet.queueless.ui;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.kuet.queueless.databinding.ActivityHistoryBinding;
import com.kuet.queueless.model.Token;
import com.kuet.queueless.repository.AuthRepository;
import com.kuet.queueless.repository.TokenRepository;
import com.kuet.queueless.util.Callbacks;
import com.kuet.queueless.util.Constants;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Student: own token history.
 * Operator: history + analytics for one service (counts, average service time, busiest hour, hourly chart).
 */
public class HistoryActivity extends AppCompatActivity {
    private ActivityHistoryBinding b;
    private final TokenRepository tokenRepo = new TokenRepository();
    private final HistoryAdapter adapter = new HistoryAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.rvHistory.setLayoutManager(new LinearLayoutManager(this));
        b.rvHistory.setAdapter(adapter);

        String role = getIntent().getStringExtra(Constants.EXTRA_ROLE);
        boolean operator = Constants.ROLE_OPERATOR.equals(role);
        String serviceId = getIntent().getStringExtra(Constants.EXTRA_SERVICE_ID);

        if (operator && serviceId != null) {
            setTitle("History & analytics");
            b.tvChartTitle.setVisibility(View.VISIBLE);
            b.chart.setVisibility(View.VISIBLE);
            tokenRepo.loadAllByService(serviceId, Callbacks.<List<Token>>toast(this, list -> show(list, true)));
        } else {
            setTitle("My history");
            String uid = new AuthRepository().currentUid();
            if (uid == null) { finish(); return; }
            tokenRepo.loadHistoryByUser(uid, Callbacks.<List<Token>>toast(this, list -> show(list, false)));
        }
    }

    private void show(List<Token> list, boolean operator) {
        int completed = 0, cancelled = 0, skipped = 0, active = 0;
        long totalSeconds = 0;
        int timed = 0;
        int[] hours = new int[24];

        for (Token t : list) {
            switch (t.statusEnum()) {
                case COMPLETED:
                    completed++;
                    if (t.serviceSeconds > 0) { totalSeconds += t.serviceSeconds; timed++; }
                    break;
                case CANCELLED: cancelled++; break;
                case SKIPPED: skipped++; break;
                default: active++; break;
            }
            if (t.createdAt != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(t.createdAt);
                hours[c.get(Calendar.HOUR_OF_DAY)]++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Total tokens: ").append(list.size())
                .append("\nCompleted: ").append(completed)
                .append("   Cancelled: ").append(cancelled)
                .append("   Skipped: ").append(skipped)
                .append("   Active: ").append(active);

        if (operator) {
            if (timed > 0) {
                sb.append(String.format(Locale.US, "\nAverage service time: %.1f min", totalSeconds / 60.0 / timed));
            } else {
                sb.append("\nAverage service time: no completed tokens yet");
            }
            int busiest = 0;
            for (int i = 1; i < 24; i++) if (hours[i] > hours[busiest]) busiest = i;
            if (hours[busiest] > 0) {
                sb.append(String.format(Locale.US, "\nBusiest hour: %02d:00\u2013%02d:00 (%d tokens)",
                        busiest, (busiest + 1) % 24, hours[busiest]));
            }
            b.chart.setData(hours);
        }

        b.tvSummary.setText(sb.toString());
        adapter.submitList(list);
        b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
