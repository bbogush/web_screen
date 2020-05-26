package com.bbogush.web_screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class AppService extends Service {
    private static final String TAG = AppService.class.getSimpleName();

    private static boolean isRunning = false;

    // Unique notification identifier
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    private final IBinder iBinder = new AppServiceBinder();

    private ScreenCapture screenCapture;
    private HttpServer httpServer;
    private boolean isServerRunning = false;

    @Override
    public void onCreate() {
        isRunning = true;
    }

    @Override
    public void onDestroy() {
        httpServer.stop();
        screenCapture.stop();
        isRunning = false;
    }

    public static boolean isServiceRunning() {
        return isRunning;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText("Content text")
                //.setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);
        //do heavy work on a background thread
        //stopSelf();
        Log.d("Ser", "Service started");
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    public class AppServiceBinder extends Binder {
        AppService getService() {
            return AppService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    public void startScreenCapture(ScreenCapture capture) {
        screenCapture = capture;
        screenCapture.start();
    }

    public void startHttpServer(HttpServer server) {
        httpServer = server;
        try {
            httpServer.start();
        } catch(IOException ioe) {
            Log.e(TAG, "The HTTP server could not start");
            ioe.printStackTrace();
            return;
        }

        isServerRunning = true;
    }

    public boolean isHttpServerRunning() {
        return isServerRunning;
    }
}
