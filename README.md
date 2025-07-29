
# 🚧 Foreground Service + Heads-Up Notifications in Android / Android Automotive

This branch explores how to trigger visible notifications (ideally pop-ups / heads-up notifications) using foreground services on Android — including in the restricted environment of **Android Automotive OS**.

---

## ✅ Summary of What We Did

### 1. **Add Required Permissions**

In your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

These are **mandatory** for modern Android (especially Android 13+), otherwise the service or its notifications may silently fail or crash the app. (Some methods are linted if the permissions are not properly set)

---

### 2. **Define and Register the Service**

We created a `ForegroundService` class and a `BackgroundService` that extends `Service` and displays a notification when started.

```java
public class ForegroundService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚗 Toll Detected!")
            .setContentText("You passed through a toll road.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(Notification.DEFAULT_ALL)
            .build();

        startForeground(1, notification);

        // Optional: stop after 5 seconds (for testing)
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                stopSelf();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        return START_NOT_STICKY;
    }
}
```

The services are declared in `AndroidManifest.xml`. depending on the type of service you specify you need to grant specific permission (see above):

```xml
<service 
    android:name=".ForegroundService"
    android:foregroundServiceType="location" 
    android:exported="false" />

<service
android:name=".BackgroundService"
android:foregroundServiceType="location"
android:exported="false" />
```

---

### 3. **Create the Notification Channel**

Required for Android 8+. This object will carry the notification sent by our services:

```java
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
```

---

### 4. Runtime permissions
Since Android 6.0 (API 23), apps must request dangerous permissions at runtime, not just in the manifest.

`MainActivity` implementation is designed to request all necessary permissions at launch, then start the background and foreground services once permissions are granted, and exit. The permissions are requested ONCE only (the first time the app is opened).

### 5. **Tried Heads-Up Notifications (Popups)**

We tested:

* `IMPORTANCE_HIGH` notification channels ✅
* `PRIORITY_MAX` notification priority ✅
* `CATEGORY_MESSAGE` category ✅
* `Notification.DEFAULT_ALL` for sound/vibration ✅
* `MessagingStyle` for conversation-like format ✅

However, on **Android Automotive OS**, **none of these reliably produced a heads-up popup** probably due to platform-level UX restrictions (as stated on the docs).

---

## ❌ What Didn't Work (And Why)

Despite doing everything “correctly,” heads-up notifications **did not appear as popups** on Android Automotive OS due to:

### 🚗 Android Automotive OS Restrictions

* **No guaranteed heads-up UI** — only shows under certain OEM conditions (e.g. parked, specific categories).
* Only `CATEGORY_CALL`, `CATEGORY_MESSAGE`, and `CATEGORY_NAVIGATION` **may** trigger heads-up, and even then, **OEM may suppress**.
* Notification styles like `BigTextStyle`, `InboxStyle`, `MediaStyle` are **ignored or reduced**.

**See official docs:**
[https://developer.android.com/training/cars/platforms/automotive-os/notifications](https://developer.android.com/training/cars/platforms/automotive-os/notifications)

---

# How to try 

Run the project with the drop down menu of the main screen open (in the emulator). you will see two notifications. The one from the foreground service will disapear after 5s once the service is killed.

