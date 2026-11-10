package com.kuet.queueless.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.kuet.queueless.databinding.ActivityTokenBinding;
import com.kuet.queueless.model.ServiceItem;
import com.kuet.queueless.model.Token;
import com.kuet.queueless.model.TokenStatus;
import com.kuet.queueless.repository.AuthRepository;
import com.kuet.queueless.repository.ServiceRepository;
import com.kuet.queueless.repository.TokenRepository;
import com.kuet.queueless.util.Callbacks;
import com.kuet.queueless.util.Constants;
import com.kuet.queueless.util.WaitTimeCalculator;

import java.util.ArrayList;
import java.util.List;

/** Student view of one service: join the queue, see live position, cancel, show QR. */
public class TokenActivity extends AppCompatActivity {
    private ActivityTokenBinding b;
    private final AuthRepository auth = new AuthRepository();
    private final ServiceRepository serviceRepo = new ServiceRepository();
    private final TokenRepository tokenRepo = new TokenRepository();

    private String serviceId;
    private String uid;
    private ServiceItem service;
    private Token myToken;
    private List<Token> queue = new ArrayList<>();
    private String lastQrTokenId;
    private ListenerRegistration serviceReg, tokenReg, queueReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityTokenBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Queue");

        serviceId = getIntent().getStringExtra(Constants.EXTRA_SERVICE_ID);
        uid = auth.currentUid();
        if (serviceId == null || uid == null) {
            finish();
            return;
        }

        b.btnGetToken.setOnClickListener(v -> getToken());
        b.btnCancel.setOnClickListener(v -> confirmCancel());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (serviceId == null || uid == null) return;

        serviceReg = serviceRepo.listenOne(serviceId, Callbacks.<ServiceItem>toast(this, s -> {
            service = s;
            refresh();
        }));
        tokenReg = tokenRepo.listenActiveByUser(uid, Callbacks.<List<Token>>toast(this, list -> {
            Token old = myToken;
            myToken = list.isEmpty() ? null : list.get(0);
            if (old != null && myToken == null) announceEnded(old.id);
            refresh();
        }));
        queueReg = tokenRepo.listenActiveByService(serviceId, Callbacks.<List<Token>>toast(this, q -> {
            queue = q;
            refresh();
        }));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (serviceReg != null) serviceReg.remove();
        if (tokenReg != null) tokenReg.remove();
        if (queueReg != null) queueReg.remove();
        serviceReg = tokenReg = queueReg = null;
        myToken = null; // avoids a false "token ended" message when the screen restarts
    }

    private void announceEnded(String tokenId) {
        tokenRepo.getToken(tokenId, Callbacks.<Token>silent(t -> {
            TokenStatus st = t.statusEnum();
            if (st == TokenStatus.COMPLETED) {
                Toast.makeText(this, "Token " + t.displayNumber + " completed. Thank you!", Toast.LENGTH_LONG).show();
            } else if (st == TokenStatus.SKIPPED) {
                Toast.makeText(this, "Token " + t.displayNumber + " was skipped by the operator.", Toast.LENGTH_LONG).show();
            }
        }));
    }

    private void refresh() {
        if (service == null) return;
        b.tvServiceName.setText(service.name);
        b.tvServiceLocation.setText(service.location);
        boolean hasNote = service.announcement != null && !service.announcement.isEmpty();
        b.tvAnnouncement.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        if (hasNote) b.tvAnnouncement.setText("\u26A0 " + service.announcement);

        boolean mine = myToken != null && serviceId.equals(myToken.serviceId);
        if (mine) {
            showMyToken();
            return;
        }

        b.groupToken.setVisibility(View.GONE);
        b.groupNoToken.setVisibility(View.VISIBLE);
        if (myToken != null) {
            b.tvNoTokenInfo.setText("You already hold token " + myToken.displayNumber
                    + " at another service. Cancel it there before joining this queue.");
            b.btnGetToken.setEnabled(false);
            b.btnGetToken.setText("Get token");
        } else {
            int est = WaitTimeCalculator.estimateMinutes(service.waitingCount, service.hasCurrent(), service.avg());
            b.tvNoTokenInfo.setText(service.waitingCount + " waiting now.\nEstimated wait for a new token: "
                    + WaitTimeCalculator.format(est));
            b.btnGetToken.setEnabled(service.open);
            b.btnGetToken.setText(service.open ? "Get token" : "Service closed");
        }
    }

    private void showMyToken() {
        b.groupNoToken.setVisibility(View.GONE);
        b.groupToken.setVisibility(View.VISIBLE);
        b.tvTokenNumber.setText(myToken.displayNumber);

        if (myToken.statusEnum() == TokenStatus.CALLED) {
            b.tvTokenStatus.setText("It's your turn! Please go to the counter.");
            b.tvAhead.setText("Now being served");
            b.tvWait.setText("");
            b.btnCancel.setVisibility(View.GONE);
        } else {
            int ahead = WaitTimeCalculator.countAhead(queue, myToken);
            boolean serving = WaitTimeCalculator.isServing(queue);
            int est = WaitTimeCalculator.estimateMinutes(ahead, serving, service.avg());
            b.tvTokenStatus.setText("Waiting in queue");
            b.tvAhead.setText(ahead == 1 ? "1 person ahead of you" : ahead + " people ahead of you");
            b.tvWait.setText(WaitTimeCalculator.format(est));
            b.btnCancel.setVisibility(View.VISIBLE);
        }

        if (!myToken.id.equals(lastQrTokenId)) {
            try {
                Bitmap bm = new BarcodeEncoder().encodeBitmap(
                        Constants.QR_PREFIX + myToken.id, BarcodeFormat.QR_CODE, 600, 600);
                b.ivQr.setImageBitmap(bm);
                lastQrTokenId = myToken.id;
            } catch (WriterException e) {
                b.ivQr.setImageDrawable(null);
            }
        }
    }

    private void getToken() {
        setBusy(true);
        tokenRepo.joinQueue(serviceId, uid, Callbacks.<String>toast(this, id -> {
            setBusy(false);
            Toast.makeText(this, "Token issued", Toast.LENGTH_SHORT).show();
        }));
        // Errors are toasted by Callbacks.toast; also re-enable the button after a short delay.
        b.btnGetToken.postDelayed(() -> setBusy(false), 3000);
    }

    private void confirmCancel() {
        if (myToken == null) return;
        final String tokenId = myToken.id;
        new AlertDialog.Builder(this)
                .setTitle("Cancel token?")
                .setMessage("You will lose your place in the queue.")
                .setPositiveButton("Cancel token", (d, w) -> {
                    setBusy(true);
                    tokenRepo.cancel(tokenId, uid, Callbacks.<String>toast(this, n -> {
                        setBusy(false);
                        Toast.makeText(this, "Token cancelled", Toast.LENGTH_SHORT).show();
                    }));
                    b.btnCancel.postDelayed(() -> setBusy(false), 3000);
                })
                .setNegativeButton("Keep it", null)
                .show();
    }

    private void setBusy(boolean busy) {
        b.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        b.btnGetToken.setClickable(!busy);
        b.btnCancel.setClickable(!busy);
    }
}
