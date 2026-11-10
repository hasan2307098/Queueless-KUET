package com.kuet.queueless.repository;

import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.kuet.queueless.model.Token;
import com.kuet.queueless.model.TokenStatus;
import com.kuet.queueless.util.Constants;
import com.kuet.queueless.util.ErrorUtil;
import com.kuet.queueless.util.ResultCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * All queue logic lives here. Every state change that must not race (join, call next,
 * complete, skip, cancel) runs inside a Firestore transaction.
 *
 * "One token being served at a time" is enforced with service.currentTokenId:
 * two operators pressing Call Next together cannot both succeed, because the
 * second transaction sees the lock set by the first and is rejected.
 */
public class TokenRepository {
    private static final List<String> ACTIVE = Arrays.asList("WAITING", "CALLED");
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Result of a transaction: business-rule failures are returned, not thrown (no retries). */
    private static class Outcome {
        final boolean ok;
        final String value;
        private Outcome(boolean ok, String value) { this.ok = ok; this.value = value; }
        static Outcome success(String v) { return new Outcome(true, v); }
        static Outcome failure(String m) { return new Outcome(false, m); }
    }

    // ------------------------------------------------------------------ listeners / reads

    public ListenerRegistration listenActiveByService(String serviceId, ResultCallback<List<Token>> cb) {
        return listen(db.collection(Constants.COL_TOKENS)
                .whereEqualTo("serviceId", serviceId).whereIn("status", ACTIVE), cb);
    }

    public ListenerRegistration listenActiveByUser(String uid, ResultCallback<List<Token>> cb) {
        return listen(db.collection(Constants.COL_TOKENS)
                .whereEqualTo("userId", uid).whereIn("status", ACTIVE), cb);
    }

    private ListenerRegistration listen(Query q, ResultCallback<List<Token>> cb) {
        return q.addSnapshotListener((snap, e) -> {
            if (e != null) { cb.onError(ErrorUtil.message(e)); return; }
            if (snap == null) return;
            List<Token> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Token t = d.toObject(Token.class);
                if (t != null) list.add(t);
            }
            sortQueue(list);
            cb.onSuccess(list);
        });
    }

    /** Queue order = creation time (token number as tie-breaker). */
    public static void sortQueue(List<Token> list) {
        Collections.sort(list, (a, b) -> a.isBefore(b) ? -1 : (b.isBefore(a) ? 1 : 0));
    }

    public void loadHistoryByUser(String uid, ResultCallback<List<Token>> cb) {
        load(db.collection(Constants.COL_TOKENS).whereEqualTo("userId", uid), cb);
    }

    public void loadAllByService(String serviceId, ResultCallback<List<Token>> cb) {
        load(db.collection(Constants.COL_TOKENS).whereEqualTo("serviceId", serviceId), cb);
    }

    private void load(Query q, ResultCallback<List<Token>> cb) {
        q.get().addOnSuccessListener(snap -> {
            List<Token> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Token t = d.toObject(Token.class);
                if (t != null) list.add(t);
            }
            Collections.sort(list, (a, b) -> Long.compare(b.createdMillis(), a.createdMillis()));
            cb.onSuccess(list);
        }).addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }

    public void getToken(String tokenId, ResultCallback<Token> cb) {
        db.collection(Constants.COL_TOKENS).document(tokenId).get()
                .addOnSuccessListener(d -> {
                    Token t = d.exists() ? d.toObject(Token.class) : null;
                    if (t == null) cb.onError("Token not found.");
                    else cb.onSuccess(t);
                })
                .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }

    // ------------------------------------------------------------------ student actions

    /** Creates a token. Returns the new token id. A student may hold one active token at a time. */
    public void joinQueue(String serviceId, String uid, ResultCallback<String> cb) {
        db.collection(Constants.COL_TOKENS)
                .whereEqualTo("userId", uid).whereIn("status", ACTIVE).get()
                .addOnSuccessListener(q -> {
                    if (!q.isEmpty()) {
                        String num = q.getDocuments().get(0).getString("displayNumber");
                        cb.onError("You already have an active token (" + num + "). Cancel it first.");
                        return;
                    }
                    runJoin(serviceId, uid, cb);
                })
                .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }

    private void runJoin(String serviceId, String uid, ResultCallback<String> cb) {
        final DocumentReference sRef = db.collection(Constants.COL_SERVICES).document(serviceId);
        final DocumentReference tRef = db.collection(Constants.COL_TOKENS).document();
        final DocumentReference eRef = db.collection(Constants.COL_EVENTS).document();
        final String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());

        Task<Outcome> task = db.runTransaction(tr -> {
            DocumentSnapshot s = tr.get(sRef);
            if (!s.exists()) return Outcome.failure("Service not found.");
            Boolean open = s.getBoolean("open");
            if (open == null || !open) return Outcome.failure("This service is closed right now.");

            Long last = s.getLong("lastTokenNumber");
            String dayKey = s.getString("dayKey");
            long next = ((today.equals(dayKey) && last != null) ? last : 0L) + 1;   // new day -> restart at 1
            String prefix = s.getString("prefix");
            if (prefix == null || prefix.isEmpty()) prefix = "T";
            Long waiting = s.getLong("waitingCount");

            Map<String, Object> token = new HashMap<>();
            token.put("serviceId", serviceId);
            token.put("userId", uid);
            token.put("tokenNumber", next);
            token.put("displayNumber", String.format(Locale.US, "%s-%03d", prefix, next));
            token.put("status", TokenStatus.WAITING.name());
            token.put("createdAt", FieldValue.serverTimestamp());
            tr.set(tRef, token);

            Map<String, Object> upd = new HashMap<>();
            upd.put("lastTokenNumber", next);
            upd.put("dayKey", today);
            upd.put("waitingCount", (waiting == null ? 0L : waiting) + 1);
            tr.update(sRef, upd);

            tr.set(eRef, event(tRef.getId(), serviceId, "JOINED", uid));
            return Outcome.success(tRef.getId());
        });
        deliver(task, cb);
    }

    public void cancel(String tokenId, String uid, ResultCallback<String> cb) {
        changeStatus(tokenId, TokenStatus.CANCELLED, uid, cb);
    }

    // ------------------------------------------------------------------ operator actions

    /** Calls the first WAITING token. Returns its display number. */
    public void callNext(String serviceId, ResultCallback<String> cb) {
        db.collection(Constants.COL_TOKENS)
                .whereEqualTo("serviceId", serviceId).whereEqualTo("status", "WAITING").get()
                .addOnSuccessListener(q -> {
                    List<Token> waiting = new ArrayList<>();
                    for (DocumentSnapshot d : q.getDocuments()) {
                        Token t = d.toObject(Token.class);
                        if (t != null) waiting.add(t);
                    }
                    if (waiting.isEmpty()) { cb.onError("The queue is empty."); return; }
                    sortQueue(waiting);
                    runCallNext(serviceId, waiting.get(0).id, cb);
                })
                .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }

    private void runCallNext(String serviceId, String tokenId, ResultCallback<String> cb) {
        final DocumentReference sRef = db.collection(Constants.COL_SERVICES).document(serviceId);
        final DocumentReference tRef = db.collection(Constants.COL_TOKENS).document(tokenId);
        final DocumentReference eRef = db.collection(Constants.COL_EVENTS).document();
        final String actor = FirebaseAuth.getInstance().getUid();

        Task<Outcome> task = db.runTransaction(tr -> {
            DocumentSnapshot s = tr.get(sRef);
            String current = s.getString("currentTokenId");
            if (current != null && !current.isEmpty()) {
                return Outcome.failure("Complete or skip the current token first.");
            }
            DocumentSnapshot t = tr.get(tRef);
            if (!t.exists() || !TokenStatus.WAITING.name().equals(t.getString("status"))) {
                return Outcome.failure("The queue just changed. Please try again.");
            }
            String display = t.getString("displayNumber");
            Long waiting = s.getLong("waitingCount");

            Map<String, Object> tUpd = new HashMap<>();
            tUpd.put("status", TokenStatus.CALLED.name());
            tUpd.put("calledAt", FieldValue.serverTimestamp());
            tr.update(tRef, tUpd);

            Map<String, Object> sUpd = new HashMap<>();
            sUpd.put("currentTokenId", tokenId);
            sUpd.put("nowServing", display);
            sUpd.put("waitingCount", Math.max(0L, (waiting == null ? 0L : waiting) - 1));
            tr.update(sRef, sUpd);

            tr.set(eRef, event(tokenId, serviceId, "CALLED", actor));
            return Outcome.success(display);
        });
        deliver(task, cb);
    }

    public void complete(String tokenId, ResultCallback<String> cb) {
        changeStatus(tokenId, TokenStatus.COMPLETED, null, cb);
    }

    public void skip(String tokenId, ResultCallback<String> cb) {
        changeStatus(tokenId, TokenStatus.SKIPPED, null, cb);
    }

    /** Cancels every waiting token and skips the one being served (use at the end of the day). */
    public void clearQueue(String serviceId, ResultCallback<String> cb) {
        db.collection(Constants.COL_TOKENS)
                .whereEqualTo("serviceId", serviceId).whereIn("status", ACTIVE).get()
                .addOnSuccessListener(snap -> {
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        boolean waiting = TokenStatus.WAITING.name().equals(d.getString("status"));
                        batch.update(d.getReference(), "status",
                                waiting ? TokenStatus.CANCELLED.name() : TokenStatus.SKIPPED.name());
                    }
                    Map<String, Object> sUpd = new HashMap<>();
                    sUpd.put("waitingCount", 0);
                    sUpd.put("currentTokenId", "");
                    sUpd.put("nowServing", "");
                    batch.update(db.collection(Constants.COL_SERVICES).document(serviceId), sUpd);
                    batch.commit()
                            .addOnSuccessListener(v -> cb.onSuccess("Cleared " + snap.size() + " token(s)"))
                            .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
                })
                .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }

    // ------------------------------------------------------------------ shared transition logic

    /**
     * Moves a token to a new status if the transition is legal, keeping the service's
     * counters, "now serving" lock, rolling average and event history consistent.
     * ownerUid != null means a student is acting on their own token.
     */
    private void changeStatus(String tokenId, TokenStatus target, String ownerUid, ResultCallback<String> cb) {
        final DocumentReference tRef = db.collection(Constants.COL_TOKENS).document(tokenId);
        final DocumentReference eRef = db.collection(Constants.COL_EVENTS).document();
        final String actor = FirebaseAuth.getInstance().getUid();

        Task<Outcome> task = db.runTransaction(tr -> {
            DocumentSnapshot t = tr.get(tRef);
            if (!t.exists()) return Outcome.failure("Token not found.");
            if (ownerUid != null && !ownerUid.equals(t.getString("userId"))) {
                return Outcome.failure("This is not your token.");
            }
            TokenStatus cur = TokenStatus.from(t.getString("status"));
            if (!cur.canTransitionTo(target)) {
                return Outcome.failure("This token is already " + cur.name().toLowerCase(Locale.US) + ".");
            }
            String serviceId = t.getString("serviceId");
            DocumentReference sRef = db.collection(Constants.COL_SERVICES).document(serviceId);
            DocumentSnapshot s = tr.get(sRef);

            Map<String, Object> tUpd = new HashMap<>();
            Map<String, Object> sUpd = new HashMap<>();
            tUpd.put("status", target.name());

            if (cur == TokenStatus.WAITING) {
                Long w = s.getLong("waitingCount");
                sUpd.put("waitingCount", Math.max(0L, (w == null ? 0L : w) - 1));
            } else if (tokenId.equals(s.getString("currentTokenId"))) {
                sUpd.put("currentTokenId", "");
                sUpd.put("nowServing", "");
            }

            if (target == TokenStatus.COMPLETED) {
                Timestamp called = t.getTimestamp("calledAt");
                long seconds = called == null ? 0L
                        : Math.max(1L, (System.currentTimeMillis() - called.toDate().getTime()) / 1000L);
                tUpd.put("completedAt", FieldValue.serverTimestamp());
                tUpd.put("serviceSeconds", seconds);
                if (seconds > 0) {
                    Double avg = s.getDouble("averageServiceMinutes");
                    double old = (avg == null || avg <= 0) ? Constants.DEFAULT_AVG_MINUTES : avg;
                    double next = 0.7 * old + 0.3 * (seconds / 60.0);     // rolling average
                    sUpd.put("averageServiceMinutes", Math.min(60.0, Math.max(1.0, next)));
                }
            }

            tr.update(tRef, tUpd);
            if (!sUpd.isEmpty()) tr.update(sRef, sUpd);
            tr.set(eRef, event(tokenId, serviceId, target.name(), actor));
            return Outcome.success(t.getString("displayNumber"));
        });
        deliver(task, cb);
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> event(String tokenId, String serviceId, String type, String actorId) {
        Map<String, Object> e = new HashMap<>();
        e.put("tokenId", tokenId);
        e.put("serviceId", serviceId);
        e.put("eventType", type);
        e.put("actorId", actorId);
        e.put("timestamp", FieldValue.serverTimestamp());
        return e;
    }

    private void deliver(Task<Outcome> task, ResultCallback<String> cb) {
        task.addOnSuccessListener(o -> {
            if (o.ok) cb.onSuccess(o.value);
            else cb.onError(o.value);
        }).addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }
}
