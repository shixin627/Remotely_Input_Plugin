package com.example.remote_input.Models;

import android.app.PendingIntent;
import android.app.Notification;
import android.os.Bundle;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.UUID;

public class NotificationWear {
    public String id = "";
    public String key = "";
    public String packageName = "";
    public PendingIntent pendingIntent;
    public ArrayList<android.app.RemoteInput> remoteInputs = new ArrayList<>();
    public ArrayList<Notification.Action> actions = new ArrayList<>();
    public Bundle bundle;
    public String tag;

    @Override
    public String toString() {
        return "NotificationWear{" +
                "id=" + id +
                ", key=" + key +
                ", packageName=" + packageName +
                ", pendingIntent=" + pendingIntent +
                ", remoteInputs=" + remoteInputs +
                ", actions=" + actions +
                ", bundle=" + bundle +
                ", tag=" + tag +
                '}';
    }
}
