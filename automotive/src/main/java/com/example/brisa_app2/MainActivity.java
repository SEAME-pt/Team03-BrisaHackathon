package com.example.brisa_app2;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Optional Toast for confirmation
        new Handler().postDelayed(() -> {
            Toast.makeText(getApplicationContext(), "Brisa App Running 🚗", Toast.LENGTH_LONG).show();
        }, 3000);
    }
}
