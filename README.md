
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

# References
- Design a service: [https://developer.android.com/develop/background-work/services#java](https://developer.android.com/develop/background-work/services#java)
- Send notifcations: [https://developer.android.com/training/cars/platforms/automotive-os/notifications](https://developer.android.com/training/cars/platforms/automotive-os/notifications)
- Grant location permission (usefull to then get gps data): [https://developer.android.com/develop/sensors-and-location/location/permissions#foreground](https://developer.android.com/develop/sensors-and-location/location/permissions#foreground)
