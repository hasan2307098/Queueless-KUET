package com.kuet.queueless.repository;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.kuet.queueless.model.AppUser;
import com.kuet.queueless.util.Constants;
import com.kuet.queueless.util.ErrorUtil;
import com.kuet.queueless.util.ResultCallback;

import java.util.HashMap;
import java.util.Map;

public class AuthRepository {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public String currentUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u == null ? null : u.getUid();
    }

    public void logout() {
        auth.signOut();
    }

    public void register(String name, String email, String password, String role,
                         ResultCallback<String> cb) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> {
                    String uid = r.getUser().getUid();
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", name);
                    m.put("email", email);
                    m.put("role", role);
                    m.put("active", true);
                    m.put("createdAt", FieldValue.serverTimestamp());
                    db.collection(Constants.COL_USERS).document(uid).set(m)
                            .addOnSuccessListener(v -> cb.onSuccess(role))
                            .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
                })
                .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }

    public void login(String email, String password, ResultCallback<String> cb) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> fetchRole(r.getUser().getUid(), cb))
                .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }

    public void fetchRole(String uid, ResultCallback<String> cb) {
        db.collection(Constants.COL_USERS).document(uid).get()
                .addOnSuccessListener(d -> {
                    String role = d.getString("role");
                    if (role == null) cb.onError("Account profile not found.");
                    else cb.onSuccess(role);
                })
                .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }

    public void fetchUser(String uid, ResultCallback<AppUser> cb) {
        db.collection(Constants.COL_USERS).document(uid).get()
                .addOnSuccessListener(d -> {
                    AppUser u = d.exists() ? d.toObject(AppUser.class) : null;
                    if (u == null) cb.onError("User not found.");
                    else cb.onSuccess(u);
                })
                .addOnFailureListener(e -> cb.onError(ErrorUtil.message(e)));
    }
}
