package com.kuet.queueless.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.ListenerRegistration;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.kuet.queueless.R;
import com.kuet.queueless.databinding.ActivityOperatorBinding;
import com.kuet.queueless.databinding.DialogServiceBinding;
import com.kuet.queueless.model.AppUser;
import com.kuet.queueless.model.ServiceItem;
import com.kuet.queueless.model.Token;
import com.kuet.queueless.repository.AuthRepository;
import com.kuet.queueless.repository.ServiceRepository;
import com.kuet.queueless.repository.TokenRepository;
import com.kuet.queueless.util.Callbacks;
import com.kuet.queueless.util.Constants;
import com.kuet.queueless.util.Navigator;
import com.kuet.queueless.util.ResultCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Operator dashboard: manage the live queue of one of the operator's services. */
public class OperatorActivity extends AppCompatActivity {
    private ActivityOperatorBinding b;
    private final AuthRepository auth = new AuthRepository();
    private final ServiceRepository serviceRepo = new ServiceRepository();
    private final TokenRepository tokenRepo = new TokenRepository();

    private QueueTokenAdapter adapter;
    private String uid;

    private List<ServiceItem> services = new ArrayList<>();
    private List<String> spinnerNames = new ArrayList<>();
    private String selectedId;
    private ServiceItem selected;
    private List<Token> queue = new ArrayList<>();

    private ListenerRegistration servicesReg;
    private ListenerRegistration queueReg;
    private String queueRegFor;
    private boolean updatingSwitch = false;

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result != null && result.getContents() != null) verifyQr(result.getContents());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOperatorBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Operator dashboard");

        uid = auth.currentUid();
        if (uid == null) {
            Navigator.toLogin(this);
            return;
        }

        adapter = new QueueTokenAdapter(t -> tokenRepo.skip(t.id,
                Callbacks.<String>toast(this, n -> toast("Skipped " + n))));
        b.rvQueue.setLayoutManager(new LinearLayoutManager(this));
        b.rvQueue.setAdapter(adapter);

        b.spService.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos < 0 || pos >= services.size()) return;
                ServiceItem s = services.get(pos);
                if (!s.id.equals(selectedId)) {
                    selectedId = s.id;
                    selected = s;
                    ensureQueueListener();
                    applyService();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        b.switchOpen.setOnCheckedChangeListener((v, checked) -> {
            if (updatingSwitch || selected == null) return;
            serviceRepo.setOpen(selected.id, checked, Callbacks.<String>toast(this, this::toast));
        });

        b.btnCallNext.setOnClickListener(v -> {
            if (selected == null) return;
            tokenRepo.callNext(selected.id, Callbacks.<String>toast(this, n -> toast("Now serving " + n)));
        });
        b.btnComplete.setOnClickListener(v -> {
            if (selected == null || !selected.hasCurrent()) return;
            tokenRepo.complete(selected.currentTokenId,
                    Callbacks.<String>toast(this, n -> toast("Completed " + n)));
        });
        b.btnSkipCurrent.setOnClickListener(v -> {
            if (selected == null || !selected.hasCurrent()) return;
            tokenRepo.skip(selected.currentTokenId,
                    Callbacks.<String>toast(this, n -> toast("Skipped " + n)));
        });
        b.btnScan.setOnClickListener(v -> {
            ScanOptions o = new ScanOptions();
            o.setPrompt("Scan the student's token QR code");
            o.setBeepEnabled(false);
            o.setOrientationLocked(false);
            scanLauncher.launch(o);
        });
        b.btnAnnounce.setOnClickListener(v -> showAnnounceDialog());
        b.btnClear.setOnClickListener(v -> confirmClear());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (uid == null) return;
        servicesReg = serviceRepo.listenByProvider(uid,
                Callbacks.<List<ServiceItem>>toast(this, this::onServices));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (servicesReg != null) servicesReg.remove();
        servicesReg = null;
        detachQueue();
    }

    private void detachQueue() {
        if (queueReg != null) queueReg.remove();
        queueReg = null;
        queueRegFor = null;
    }

    // ------------------------------------------------------------------ data -> UI

    private void onServices(List<ServiceItem> list) {
        services = list;
        boolean empty = list.isEmpty();
        b.tvNoService.setVisibility(empty ? View.VISIBLE : View.GONE);
        b.contentGroup.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.spService.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            selected = null;
            selectedId = null;
            detachQueue();
            return;
        }

        List<String> names = new ArrayList<>();
        for (ServiceItem s : list) names.add(s.name);

        int idx = 0;
        if (selectedId != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id.equals(selectedId)) { idx = i; break; }
            }
        }
        selected = list.get(idx);
        selectedId = selected.id;

        if (!names.equals(spinnerNames)) {
            spinnerNames = names;
            ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            b.spService.setAdapter(a);
            b.spService.setSelection(idx);
        } else if (b.spService.getSelectedItemPosition() != idx) {
            b.spService.setSelection(idx);
        }

        ensureQueueListener();
        applyService();
    }

    private void ensureQueueListener() {
        if (selectedId == null || selectedId.equals(queueRegFor)) return;
        detachQueue();
        queue = new ArrayList<>();
        queueRegFor = selectedId;
        queueReg = tokenRepo.listenActiveByService(selectedId,
                Callbacks.<List<Token>>toast(this, q -> {
                    queue = q;
                    renderQueue();
                }));
        renderQueue();
    }

    private void applyService() {
        if (selected == null) return;
        b.tvNowServing.setText(selected.hasCurrent() ? selected.nowServing : "\u2014");
        b.tvWaiting.setText(selected.waitingCount + " waiting");
        b.tvAvg.setText(String.format(Locale.US, "Average service time: %.1f min", selected.avg()));

        boolean hasNote = selected.announcement != null && !selected.announcement.isEmpty();
        b.tvAnnouncement.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        if (hasNote) b.tvAnnouncement.setText("\u26A0 " + selected.announcement);

        updatingSwitch = true;
        b.switchOpen.setChecked(selected.open);
        updatingSwitch = false;

        b.btnCallNext.setEnabled(!selected.hasCurrent() && selected.waitingCount > 0);
        b.btnComplete.setEnabled(selected.hasCurrent());
        b.btnSkipCurrent.setEnabled(selected.hasCurrent());
    }

    private void renderQueue() {
        List<Token> waiting = new ArrayList<>();
        for (Token t : queue) {
            if (t.statusEnum().name().equals("WAITING")) waiting.add(t);
        }
        adapter.submitList(waiting);
        b.tvEmpty.setVisibility(waiting.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------ dialogs

    private void showAnnounceDialog() {
        if (selected == null) return;
        final String serviceId = selected.id;
        final EditText et = new EditText(this);
        et.setHint("e.g. Printer jammed - about 10 minutes delay");
        et.setText(selected.announcement == null ? "" : selected.announcement);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(et);

        new AlertDialog.Builder(this)
                .setTitle("Announcement for students")
                .setMessage("Leave empty to clear it.")
                .setView(box)
                .setPositiveButton("Post", (d, w) -> serviceRepo.setAnnouncement(serviceId,
                        et.getText().toString().trim(), Callbacks.<String>toast(this, this::toast)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClear() {
        if (selected == null) return;
        final String serviceId = selected.id;
        new AlertDialog.Builder(this)
                .setTitle("Clear the whole queue?")
                .setMessage("All waiting tokens will be cancelled and the current one skipped. Use this at the end of the day.")
                .setPositiveButton("Clear", (d, w) -> tokenRepo.clearQueue(serviceId,
                        Callbacks.<String>toast(this, this::toast)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddServiceDialog() {
        final DialogServiceBinding d = DialogServiceBinding.inflate(getLayoutInflater());
        new AlertDialog.Builder(this)
                .setTitle("Add service")
                .setView(d.getRoot())
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = String.valueOf(d.etName.getText()).trim();
                    String location = String.valueOf(d.etLocation.getText()).trim();
                    String desc = String.valueOf(d.etDescription.getText()).trim();
                    String prefix = String.valueOf(d.etPrefix.getText()).trim().toUpperCase(Locale.US);
                    double avg;
                    try {
                        avg = Double.parseDouble(String.valueOf(d.etAvg.getText()).trim());
                    } catch (NumberFormatException e) {
                        avg = Constants.DEFAULT_AVG_MINUTES;
                    }
                    if (name.isEmpty() || prefix.isEmpty()) {
                        toast("Name and prefix are required");
                        return;
                    }
                    if (avg < 1) avg = 1;
                    serviceRepo.create(uid, name, location, desc, prefix, avg,
                            Callbacks.<String>toast(this, id -> toast("Service created")));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ QR verification

    private void verifyQr(String content) {
        if (!content.startsWith(Constants.QR_PREFIX)) {
            toast("Not a QueueLess token QR code");
            return;
        }
        String tokenId = content.substring(Constants.QR_PREFIX.length());
        tokenRepo.getToken(tokenId, Callbacks.<Token>toast(this, token ->
                auth.fetchUser(token.userId, new ResultCallback<AppUser>() {
                    @Override public void onSuccess(AppUser u) { showVerification(token, u.name); }
                    @Override public void onError(String message) { showVerification(token, "Unknown"); }
                })));
    }

    private void showVerification(Token token, String studentName) {
        boolean sameService = selectedId != null && selectedId.equals(token.serviceId);
        boolean active = token.statusEnum().isActive();
        String verdict;
        if (!sameService) verdict = "\u2716 NOT VALID: token belongs to a different service";
        else if (!active) verdict = "\u2716 NOT VALID: token is " + token.statusEnum().name();
        else verdict = "\u2714 VALID";

        new AlertDialog.Builder(this)
                .setTitle("Token " + token.displayNumber)
                .setMessage(verdict + "\n\nStudent: " + studentName + "\nStatus: " + token.statusEnum().name())
                .setPositiveButton("OK", null)
                .show();
    }

    // ------------------------------------------------------------------ menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_operator, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_add_service) {
            showAddServiceDialog();
            return true;
        } else if (id == R.id.action_analytics) {
            if (selectedId == null) {
                toast("Create a service first");
            } else {
                Intent i = new Intent(this, HistoryActivity.class);
                i.putExtra(Constants.EXTRA_ROLE, Constants.ROLE_OPERATOR);
                i.putExtra(Constants.EXTRA_SERVICE_ID, selectedId);
                startActivity(i);
            }
            return true;
        } else if (id == R.id.action_logout) {
            auth.logout();
            Navigator.toLogin(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
