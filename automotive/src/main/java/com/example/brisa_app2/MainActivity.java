package com.example.brisa_app2;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Collect needed permissions
        String[] neededPermissions = getMissingPermissions();

        if (neededPermissions.length > 0) {
            requestPermissions(neededPermissions, REQUEST_PERMISSIONS_CODE);
            return;
        }

        startBothServicesAndExit();
    }

    private String[] getMissingPermissions() {
        boolean needPostNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;

        boolean needLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;

        boolean needCoarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;

        // Collect only what's not granted
        java.util.ArrayList<String> list = new java.util.ArrayList<>();

        if (needPostNotifications) list.add(Manifest.permission.POST_NOTIFICATIONS);
        if (needLocation) list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (needCoarse) list.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        return list.toArray(new String[0]);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startBothServicesAndExit();
            } else {
                // Permission denied, show message or quit silently
                finish();
            }
        }
    }

    private void startBothServicesAndExit() {
        startService(new Intent(this, BackgroundService.class));
        startService(new Intent(this, ForegroundService.class));
        finish(); // exit launcher activity
    }
}
