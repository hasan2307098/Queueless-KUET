package com.kuet.queueless.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.queueless.R;
import com.kuet.queueless.databinding.ActivityStudentHomeBinding;
import com.kuet.queueless.model.ServiceItem;
import com.kuet.queueless.model.Token;
import com.kuet.queueless.repository.AuthRepository;
import com.kuet.queueless.repository.ServiceRepository;
import com.kuet.queueless.repository.TokenRepository;
import com.kuet.queueless.util.Callbacks;
import com.kuet.queueless.util.Constants;
import com.kuet.queueless.util.Navigator;
import com.kuet.queueless.util.NotificationHelper;
import com.kuet.queueless.util.QueueWatcher;

import java.util.List;

public class StudentHomeActivity extends AppCompatActivity {
    private ActivityStudentHomeBinding b;
    private final AuthRepository auth = new AuthRepository();
    private final ServiceRepository serviceRepo = new ServiceRepository();
    private final TokenRepository tokenRepo = new TokenRepository();

    private ServiceAdapter adapter;
    private ListenerRegistration servicesReg;
    private ListenerRegistration tokenReg;
    private Token activeToken;
    private String uid;

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityStudentHomeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("QueueLess KUET");

        uid = auth.currentUid();
        if (uid == null) {
            Navigator.toLogin(this);
            return;
        }

        NotificationHelper.createChannel(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
        QueueWatcher.get(this).start(uid);

        adapter = new ServiceAdapter(s -> openToken(s.id));
        b.rvServices.setLayoutManager(new LinearLayoutManager(this));
        b.rvServices.setAdapter(adapter);
        b.cardActive.setOnClickListener(v -> {
            if (activeToken != null) openToken(activeToken.serviceId);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (uid == null) return;
        servicesReg = serviceRepo.listenAll(Callbacks.<List<ServiceItem>>toast(this, list -> {
            adapter.submitList(list);
            b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        }));
        tokenReg = tokenRepo.listenActiveByUser(uid, Callbacks.<List<Token>>toast(this, list -> {
            activeToken = list.isEmpty() ? null : list.get(0);
            renderActive();
        }));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (servicesReg != null) servicesReg.remove();
        if (tokenReg != null) tokenReg.remove();
        servicesReg = null;
        tokenReg = null;
    }

    private void renderActive() {
        if (activeToken == null) {
            b.cardActive.setVisibility(View.GONE);
            return;
        }
        b.cardActive.setVisibility(View.VISIBLE);
        String state = activeToken.statusEnum().name().equals("CALLED") ? "It's your turn!" : "Waiting";
        b.tvActiveToken.setText("Your token: " + activeToken.displayNumber + "  \u00B7  " + state);
    }

    private void openToken(String serviceId) {
        Intent i = new Intent(this, TokenActivity.class);
        i.putExtra(Constants.EXTRA_SERVICE_ID, serviceId);
        startActivity(i);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_student, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_history) {
            Intent i = new Intent(this, HistoryActivity.class);
            i.putExtra(Constants.EXTRA_ROLE, Constants.ROLE_STUDENT);
            startActivity(i);
            return true;
        } else if (id == R.id.action_logout) {
            QueueWatcher.get(this).stop();
            auth.logout();
            Navigator.toLogin(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
