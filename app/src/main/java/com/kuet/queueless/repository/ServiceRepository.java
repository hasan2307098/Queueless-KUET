package com.kuet.queueless.repository;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.kuet.queueless.model.ServiceItem;
import com.kuet.queueless.util.Constants;
import com.kuet.queueless.util.ErrorUtil;
import com.kuet.queueless.util.ResultCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public ListenerRegistration listenAll(ResultCallback<List<ServiceItem>> cb) {
        return listen(db.collection(Constants.COL_SERVICES), cb);
    }

    public ListenerRegistration listenByProvider(String providerId, ResultCallback<List<ServiceItem>> cb) {
        return listen(db.collection(Constants.COL_SERVICES).whereEqualTo("providerId", providerId), cb);
    }

    private ListenerRegistration listen(Query q, ResultCallback<List<ServiceItem>> cb) {
        return q.addSnapshotListener((snap, e) -> {
            if (e != null) { cb.onError(ErrorUtil.message(e)); return; }
            if (snap == null) return;
            List<ServiceItem> list = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                ServiceItem s = d.toObject(ServiceItem.class);
                if (s != null) list.add(s);
            }
            Collections.sort(list, (a, b) -> String.valueOf(a.name).compareToIgnoreCase(String.valueOf(b.name)));
            cb.onSuccess(list);
        });
    }

    public ListenerRegistration listenOne(String serviceId, ResultCallback<ServiceItem> cb) {
        return db.collection(Constants.COL_SERVICES).document(serviceId).addSnapshotListener((d, e) -> {
            if (e != null) { cb.onError(ErrorUtil.message(e)); return; }
            if (d == null || !d.exists()) { cb.onError("Service not found."); return; }
            ServiceItem s = d.toObject(ServiceItem.class);
            if (s != null) cb.onSuccess(s);
        });
    }

    public void create(String providerId, String name, String location, String description,
                       String prefix, double avgMinutes, ResultCallback<String> cb) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("location", location);
        m.put("description", description);
        m.put("providerId", providerId);
        m.put("prefix", prefix);
        m.put("open", true);
        m.put("announcement", "");
        m.put("averageServiceMinutes", avgMinutes);
        m.put("lastTokenNumber", 0);
        m.put("waitingCount", 0);
        m.put("currentTokenId", "");
        m.put("nowServing", "");
        m.put("dayKey", "");
        m.put("createdAt", FieldValue.serverTimestamp());
        db.collection(Constants.COL_SERVICES).add(m)
                .addOnSuccessListener(ref -> cb.onSuccess(ref.getId()))
                .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }

    public void setOpen(String serviceId, boolean open, ResultCallback<String> cb) {
        update(serviceId, "open", open, open ? "Service opened" : "Service closed", cb);
    }

    public void setAnnouncement(String serviceId, String text, ResultCallback<String> cb) {
        update(serviceId, "announcement", text, "Announcement updated", cb);
    }

    private void update(String serviceId, String field, Object value, String okMsg, ResultCallback<String> cb) {
        db.collection(Constants.COL_SERVICES).document(serviceId).update(field, value)
                .addOnSuccessListener(v -> cb.onSuccess(okMsg))
                .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }
}
