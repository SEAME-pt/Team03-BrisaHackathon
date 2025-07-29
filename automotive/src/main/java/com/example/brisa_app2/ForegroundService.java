package com.example.brisa_app2;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.Person;

import androidx.core.app.NotificationCompat;

public class ForegroundService extends Service {

    private static final String CHANNEL_ID = "foreground_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("ForegroundService", "Foreground service starting");
        Person user = new Person.Builder().setName("Brisa").build();

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(user)
                .addMessage("You passed through a toll road.", System.currentTimeMillis(), user);

        Notification alertNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setStyle(messagingStyle)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        // Important: startForeground with ID and Notification
        startForeground(1, alertNotification);

        // Optional: stop after some time (demo purposes)
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                Log.d("ForegroundService", "Stopping foreground service");
                stopSelf();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Popup Foreground Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
