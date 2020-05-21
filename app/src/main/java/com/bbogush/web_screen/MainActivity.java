package com.bbogush.web_screen;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;

import java.io.IOException;
import java.lang.annotation.IncompleteAnnotationException;

public class MainActivity extends AppCompatActivity {

    private HttpServer httpServer = null;
    private MouseAccessibilityService mouseAccessibilityService;
    private static final int PERM_REQ_INTERNET = 0;
    private static final int PERM_READ_EXTERNAL_STORAGE = 1;
    private static final int PERM_BIND_ACCESSIBILITY_SERVICE = 2;
    private static final int PERM_MEDIA_PROJECTION_SERVICE = 3;
    private Bitmap bitmap;
    private MjpegStream mjpegStream;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private boolean internetPermission = false;
    private boolean diskPermission = false;
    private boolean accessibilityPermission = false;
    private boolean mediaProjectionPermission = false;
    ScreenCapture screenCapture;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            ImageView imageView = (ImageView) findViewById(R.id.imageView);
            Bitmap bitmap = screenCapture.getBitmap();
            if (bitmap != null)
                imageView.setImageBitmap(bitmap);
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View v = findViewById(android.R.id.content).getRootView();
        if (v != null)
            v.setOnTouchListener(handleTouch);
        else
            Log.d("View", "Not found");

        startService();
        mediaProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        requestHttpServerPermissions();

        startActivityForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), PERM_BIND_ACCESSIBILITY_SERVICE);
        accessibilityPermission = true;

        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), PERM_MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onDestroy() {
        stopService();
        screenCapture.stop();
        if (httpServer != null)
            httpServer.stop();
        super.onDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startHttpServer() {
        screenCapture = new ScreenCapture();
        screenCapture.start(mediaProjection);
        mjpegStream = new MjpegStream(screenCapture);

        httpServer = new HttpServer(screenCapture, 8080, getApplicationContext());
        try {
            httpServer.start();
        } catch(IOException ioe) {
            Log.w("Httpd", "The server could not start.");
            ioe.printStackTrace();
            return;
        }
        Log.w("Httpd", "Web server initialized.");

        new Thread(new Runnable() {
            public void run(){
                while (true) {
                    handler.sendEmptyMessage(0);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
            }
        }).start();
    }

    public void startService() {
        Intent serviceIntent = new Intent(this, AppService.class);
        serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android");
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    public void stopService() {
        Intent serviceIntent = new Intent(this, AppService.class);
        stopService(serviceIntent);
    }

    private boolean requestHttpServerPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            Log.i("perm", "granted");
            internetPermission = true;
        } else {
            Log.i("perm", "denied");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET}, PERM_REQ_INTERNET);
            return false;
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Log.i("perm", "granted");
            diskPermission = true;
        } else {
            Log.i("perm", "denied");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERM_READ_EXTERNAL_STORAGE);
            return false;
        }

        return true;
    }

     @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Bitmap getCapture() {
//        View view = findViewById(android.R.id.content).getRootView();
//        if (view == null) {
//            Log.d("View", "null");
//            return null;
//        }
//        View screenView = view.getRootView();
//
//        screenView.setDrawingCacheEnabled(true);
//        Bitmap bitmap = Bitmap.createBitmap(screenView.getDrawingCache());
//        screenView.setDrawingCacheEnabled(false);
//        return bitmap;

//        int w = 20, h = 20;
//
//        Bitmap.Config conf = Bitmap.Config.ARGB_8888; // see other conf types
//        Bitmap bmp = Bitmap.createBitmap(w, h, conf); // this creates a MUTABLE bitmap

//        SurfaceView surfaceView = (SurfaceView) view.findViewById(R.id.surface);
//        mSurface = mSurfaceView.getHolder().getSurface();

        screenCapture = new ScreenCapture();
        screenCapture.start(mediaProjection);
        Bitmap bmp = screenCapture.getBitmap();

        return bmp;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PERM_MEDIA_PROJECTION_SERVICE && resultCode == RESULT_OK) {
            Log.d("onActivityResult", "Media projection permission granted");
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);

            mediaProjectionPermission = true;
        }

        if (requestCode == PERM_BIND_ACCESSIBILITY_SERVICE && resultCode == RESULT_OK) {
            Log.d("onActivityResult", "Accessibility permission granted");
            accessibilityPermission = true;
        }

        if (internetPermission && diskPermission && accessibilityPermission && mediaProjectionPermission) {
            Log.d("onActivityResult", "Start http server");
            startHttpServer();
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
