package com.example.brisa_app2;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import android.util.Log;

public class BackgroundService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationChannel channel = new NotificationChannel(
                "background_channel",
                "background Channel",
                NotificationManager.IMPORTANCE_MAX
        );

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        // Here we basically build a notif and publish it on app start. It doesnt appear as a pop up but in the notification center.
        Notification notification = new NotificationCompat.Builder(this, "background_channel")
                .setContentTitle("BackgroundService")
                .setContentText("BackgroundService is running")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .build();

        //notify that is it alive
//        manager.notify(1001, notification);

        return START_NOT_STICKY; // doesnt restart when kill
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // not a bound service
    }
}
