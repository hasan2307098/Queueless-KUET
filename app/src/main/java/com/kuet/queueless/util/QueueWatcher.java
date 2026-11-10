package com.kuet.queueless.util;

import android.content.Context;

import com.google.firebase.firestore.ListenerRegistration;
import com.kuet.queueless.model.Token;
import com.kuet.queueless.model.TokenStatus;
import com.kuet.queueless.repository.TokenRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Watches the signed-in student's active token and posts local notifications
 * ("almost your turn" / "your turn"). Works while the app process is alive.
 * For notifications after the process is killed you would add Firebase Cloud Messaging.
 */
public class QueueWatcher {
    private static QueueWatcher instance;

    public static synchronized QueueWatcher get(Context context) {
        if (instance == null) instance = new QueueWatcher(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final TokenRepository tokens = new TokenRepository();
    private ListenerRegistration userReg;
    private ListenerRegistration queueReg;
    private String watchedServiceId;
    private Token myToken;
    private List<Token> lastQueue = new ArrayList<>();
    private final Set<String> notified = new HashSet<>();

    private QueueWatcher(Context app) { this.app = app; }

    public void start(String uid) {
        stop();
        userReg = tokens.listenActiveByUser(uid, Callbacks.<List<Token>>silent(this::handleMine));
    }

    public void stop() {
        if (userReg != null) userReg.remove();
        userReg = null;
        detachQueue();
        myToken = null;
        notified.clear();
    }

    private void detachQueue() {
        if (queueReg != null) queueReg.remove();
        queueReg = null;
        watchedServiceId = null;
        lastQueue = new ArrayList<>();
    }

    private void handleMine(List<Token> list) {
        if (list.isEmpty()) {
            myToken = null;
            detachQueue();
            return;
        }
        myToken = list.get(0);
        if (myToken.statusEnum() == TokenStatus.CALLED) {
            notifyOnce(myToken.id + ":called", "It's your turn!",
                    "Token " + myToken.displayNumber + " has been called. Please go to the counter.");
        }
        if (!myToken.serviceId.equals(watchedServiceId)) {
            detachQueue();
            watchedServiceId = myToken.serviceId;
            queueReg = tokens.listenActiveByService(watchedServiceId,
                    Callbacks.<List<Token>>silent(q -> { lastQueue = q; evaluate(); }));
        } else {
            evaluate();
        }
    }

    private void evaluate() {
        if (myToken == null || myToken.statusEnum() != TokenStatus.WAITING) return;
        int ahead = WaitTimeCalculator.countAhead(lastQueue, myToken);
        if (ahead <= Constants.NEAR_THRESHOLD) {
            String text = ahead == 0 ? "You're next for token " + myToken.displayNumber + "."
                    : ahead + " ahead of you for token " + myToken.displayNumber + ".";
            notifyOnce(myToken.id + ":near", "Almost your turn", text);
        }
    }

    private void notifyOnce(String key, String title, String text) {
        if (notified.add(key)) {
            NotificationHelper.notifyUser(app, key.hashCode(), title, text);
        }
    }
}
