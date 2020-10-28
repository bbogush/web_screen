package com.bbogush.web_screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.json.JSONObject;
import org.webrtc.PeerConnection;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.VideoCapturer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AppService extends Service {
    private static final String TAG = AppService.class.getSimpleName();

    private static final int SERVICE_ID = 101;

    private static final String NOTIFICATION_CHANNEL_ID = "WebScreenServiceChannel";
    private static final String NOTIFICATION_CHANNEL_NAME = "WebScreen notification channel";

    private static final String NOTIFICATION_TITLE = "WebScreen is running";
    private static final String NOTIFICATION_CONTENT = "Tap to stop";

    private static boolean isRunning = false;

    private final IBinder iBinder = new AppServiceBinder();

    private VideoCapturer videoCapturerAndroid = null;

    private HttpServer httpServer = null;
    private boolean isWebServerRunning = false;

    private List<IceServer> iceServers = null;
    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();

    private MouseAccessibilityService mouseAccessibilityService = null;

    @Override
    public void onCreate() {
        isRunning = true;
        Log.d(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        serverStop();
        isRunning = false;
        Log.d(TAG, "Service destroyed");
    }

    public static boolean isServiceRunning() {
        return isRunning;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        String channelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                createNotificationChannel() : "";
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this,
                channelId);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText(NOTIFICATION_CONTENT)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(SERVICE_ID, notification);

        Log.d(TAG, "Service started");
        return START_STICKY;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(){
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);

        NotificationManager notificationManager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(channel);
        return NOTIFICATION_CHANNEL_ID;
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

    public boolean serverStart(Intent data, int port,
                               boolean isAccessibilityServiceEnabled, Context context) {
        startMediaProjection(data);
        isWebServerRunning = startHttpServer(port);
        //XXX getIceServers();
        initSignaling();

        accessibilityServiceSet(context, isAccessibilityServiceEnabled);

        return isWebServerRunning;
    }

    public void serverStop() {
        if (!isWebServerRunning)
            return;
        isWebServerRunning = false;

        accessibilityServiceSet(null, false);

        uninitSignaling();
        stopHttpServer();
        stopMediaProjection();
    }

    public boolean isServerRunning() {
        return isWebServerRunning;
    }

    public boolean serverRestart(int port) {
        stopHttpServer();
        isWebServerRunning = startHttpServer(port);

        return isWebServerRunning;
    }

    public void startMediaProjection(Intent data) {
        videoCapturerAndroid = new ScreenCapturerAndroid(data,
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        super.onStop();
                        Log.e(TAG, "User has revoked media projection permissions");
                    }
                });
    }

    public void stopMediaProjection() {
        videoCapturerAndroid = null;
    }

    public boolean startHttpServer(int httpServerPort) {
        httpServer = new HttpServer(mouseAccessibilityService, httpServerPort,
                getApplicationContext(), videoCapturerAndroid);
        try {
            httpServer.start();
        } catch (IOException e) {
            String fmt = getResources().getString(R.string.port_in_use);
            String errorMessage = String.format(Locale.getDefault(), fmt, httpServerPort);
            Toast.makeText(getApplicationContext(),errorMessage, Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    public void stopHttpServer() {
        if (httpServer == null)
            return;
        httpServer.stop();
    }



    public void getIceServers() {
        final String API_ENDPOINT = "https://global.xirsys.net";

        Log.d(TAG, "getIceServers");

        byte[] data = new byte[0];
        try {
            data = ("<xirsys_ident>:<xirsys_secret>").getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return;
        }
        Log.d(TAG, "getIceServers2");

        String authToken = "Basic " + Base64.encodeToString(data, Base64.NO_WRAP);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(API_ENDPOINT)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        Log.d(TAG, "getIceServers3");
        TurnServer turnServer = retrofit.create(TurnServer.class);
        Log.d(TAG, "getIceServers4");
        turnServer.getIceCandidates(authToken).enqueue(new Callback<TurnServerPojo>() {
            @Override
            public void onResponse(@NonNull Call<TurnServerPojo> call,
                                   @NonNull Response<TurnServerPojo> response) {
                Log.d(TAG, "getIceServers Response");
                TurnServerPojo body = response.body();
                if (body != null)
                    iceServers = body.iceServerList.iceServers;

                Log.d(TAG, "getIceServers iceServers=" + iceServers);

                for (IceServer iceServer : iceServers) {
                    if (iceServer.credential == null) {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer
                                .builder(iceServer.url).createIceServer();
                        peerIceServers.add(peerIceServer);
                    } else {
                        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer
                                .builder(iceServer.url)
                                .setUsername(iceServer.username)
                                .setPassword(iceServer.credential)
                                .createIceServer();
                        peerIceServers.add(peerIceServer);
                    }
                }
                Log.d(TAG, "IceServers:\n" + iceServers.toString());
            }

            @Override
            public void onFailure(@NonNull Call<TurnServerPojo> call, @NonNull Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public void initSignaling() {
        SignallingClient.getInstance().init(new Signaling());
    }

    public void uninitSignaling() {
        SignallingClient.getInstance().close();
    }

    private class Signaling implements SignallingClient.SignalingInterface {

        @Override
        public void onRemoteHangUp(String msg) {

        }

        @Override
        public void onOfferReceived(JSONObject data) {

        }

        @Override
        public void onAnswerReceived(JSONObject data) {

        }

        @Override
        public void onIceCandidateReceived(JSONObject data) {

        }

        @Override
        public void onTryToStart() {

        }

        @Override
        public void onCreatedRoom() {

        }

        @Override
        public void onJoinedRoom() {

        }

        @Override
        public void onNewPeerJoined() {

        }
    }

    public void accessibilityServiceSet(Context context, boolean isEnabled) {
        if (isEnabled) {
            if (httpServer != null && httpServer.getMouseAccessibilityService() != null)
                return;
            mouseAccessibilityService = new MouseAccessibilityService();
            mouseAccessibilityService.setContext(context);
            if (httpServer != null)
                httpServer.setMouseAccessibilityService(mouseAccessibilityService);
        } else {
            if (httpServer != null)
                httpServer.setMouseAccessibilityService(null);
            mouseAccessibilityService = null;
        }
    }

    public boolean isMouseAccessibilityServiceAvailable() {
        return mouseAccessibilityService != null;
    }
}
