# 2) Add permissions
In AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```
without the right permissions, some statement are linted and the project will not run properly. (typically some operations within the service methods will not be permitted)

# 3) Create the service
Create a class in you source that inherites from Service class and overrides the methods....

# References:
- Types of services: https://developer.android.com/develop/background-work/services
- Grant location permissions to services: https://developer.android.com/develop/sensors-and-location/location/permissions#foreground
