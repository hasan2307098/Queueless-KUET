package com.kuet.queueless.model;

import com.google.firebase.firestore.DocumentId;

import java.util.Date;

public class AppUser {
    @DocumentId public String id;
    public String name;
    public String email;
    public String role;
    public boolean active;
    public Date createdAt;

    public AppUser() { }
}
