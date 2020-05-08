package com.bbogush.web_screen;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private HttpServer httpServer = null;
    private MouseAccessibilityService mouseAccessibilityService;
    private static final int PERM_REQ_INTERNET = 0;
    private static final int PERM_READ_EXTERNAL_STORAGE = 1;
    private static final int PERM_BIND_ACCESSIBILITY_SERVICE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View v = findViewById(android.R.id.content).getRootView();
        if (v != null)
            v.setOnTouchListener(handleTouch);
        else
            Log.d("View", "Not found");

        startActivityForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), 1);

        if (requestHttpServerPermissions()) {
            startHttpServer();
        }
    }

    @Override
    public void onDestroy() {
        if (httpServer != null)
            httpServer.stop();
        super.onDestroy();
    }

    private void startHttpServer() {
        httpServer = new HttpServer(8080);
        try {
            httpServer.start();
        } catch(IOException ioe) {
            Log.w("Httpd", "The server could not start.");
            ioe.printStackTrace();
        }
        Log.w("Httpd", "Web server initialized.");
    }

    private boolean requestHttpServerPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            Log.i("perm", "granted");
        } else {
            Log.i("perm", "denied");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET}, PERM_REQ_INTERNET);
            return false;
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Log.i("perm", "granted");
        } else {
            Log.i("perm", "denied");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERM_READ_EXTERNAL_STORAGE);
            return false;
        }

        return true;
    }

     @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERM_REQ_INTERNET:
            case PERM_READ_EXTERNAL_STORAGE:
            case PERM_BIND_ACCESSIBILITY_SERVICE:
                {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    Log.i("perm", "Permission granted: " + requestCode);

                    if (requestHttpServerPermissions())
                        startHttpServer();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Log.i("perm", "Permission denied: " + requestCode);
                }
            }
        }
    }

    private View.OnTouchListener handleTouch = new View.OnTouchListener() {

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            int x = (int) event.getX();
            int y = (int) event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.i("TAG", "touched down");
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.i("TAG", "moving: (" + x + ", " + y + ")");
                    break;
                case MotionEvent.ACTION_UP:
                    Log.i("TAG", "touched up");
                    break;
            }

            return true;
        }
    };
}
