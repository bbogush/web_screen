package com.bbogush.web_screen;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.LinkAddress;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.net.Inet6Address;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int PERM_ACCESS_NETWORK_STATE = 0;
    private static final int PERM_INTERNET = 1;
    private static final int PERM_READ_EXTERNAL_STORAGE = 2;
    private static final int PERM_FOREGROUND_SERVICE = 3;
    private static final int PERM_ACTION_ACCESSIBILITY_SERVICE = 4;
    private static final int PERM_MEDIA_PROJECTION_SERVICE = 5;

    private static final int HANDLER_MESSAGE_UPDATE_NETWORK = 0;

    private HttpServer httpServer = null;
    private int httpServerPort;
    private MediaProjectionManager mediaProjectionManager;
    ScreenCapture screenCapture;
    private MouseAccessibilityService mouseAccessibilityService = null;

    AppService appService = null;
    AppServiceConnection serviceConnection = null;

    private NetworkHelper networkHelper = null;
    private SettingsHelper settingsHelper = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Activity create");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ToggleButton startButton = findViewById(R.id.startButton);
        startButton.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    start();
                else
                    stop();
            }
        });

        final Switch remoteControl = findViewById(R.id.remoteControlEnableSwitch);
        remoteControl.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                remoteControlEnable(isChecked);
            }
        });

        if (AppService.isServiceRunning())
            setStartButton();

        createUrl();

        initSettings();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Activity destroy");

        uninitSettings();

        if (networkHelper != null)
            networkHelper.close();
        unbindService();
        super.onDestroy();
    }

    private void start() {
        Log.d(TAG, "Stream start");
        if (AppService.isServiceRunning()) {
            bindService();
            return;
        }

        checkInternetPermission();
    }

    private void stop() {
        Log.d(TAG, "Stream stop");
        if (!AppService.isServiceRunning())
            return;

        stopService();
    }

    private void checkInternetPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) ==
                PackageManager.PERMISSION_GRANTED) {
            checkExternalStoragePermission();
            return;
        }

        ActivityCompat.requestPermissions(this, new String[]{ Manifest.permission.INTERNET },
                PERM_INTERNET);
    }

    private void checkExternalStoragePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            checkForegroundServicePermission();
            return;
        }

        ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE },
                PERM_READ_EXTERNAL_STORAGE);
    }

    private void checkForegroundServicePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED) {
            startService();
            return;
        }

        ActivityCompat.requestPermissions(this,
                new String[]{ Manifest.permission.FOREGROUND_SERVICE },
                PERM_FOREGROUND_SERVICE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        switch (requestCode) {
            case PERM_ACCESS_NETWORK_STATE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    networkHelper = new NetworkHelper(getApplicationContext(),
                            onNetworkChangeListener);
                    urlUpdate();
                }
                break;
            case PERM_INTERNET:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkExternalStoragePermission();
                } else {
                    resetStartButton();
                }
                break;
            case PERM_READ_EXTERNAL_STORAGE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkForegroundServicePermission();
                } else {
                    resetStartButton();
                }
                break;
            case PERM_FOREGROUND_SERVICE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startService();
                } else {
                    resetStartButton();
                }
                break;
        }
    }

    private void startService() {
        Intent serviceIntent = new Intent(this, AppService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        serviceConnection = new AppServiceConnection();
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void stopService() {
        unbindService();
        Intent serviceIntent = new Intent(this, AppService.class);
        stopService(serviceIntent);
    }

    private void bindService() {
        Intent serviceIntent = new Intent(this, AppService.class);
        serviceConnection = new AppServiceConnection();
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindService() {
        if (serviceConnection == null)
            return;

        unbindService(serviceConnection);
        serviceConnection = null;
    }

    private class AppServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AppService.AppServiceBinder binder = (AppService.AppServiceBinder)service;
            appService = binder.getService();

            httpServer = appService.getHttpServer();
            if (httpServer == null)
                askMediaProjectionPermission();
            else {
                mouseAccessibilityService = httpServer.getMouseAccessibilityService();
                if (mouseAccessibilityService != null) {
                    setRemoteControlSwitch();
                }
                screenCapture = appService.getScreenCapture();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            appService = null;
            resetStartButton();
            Log.e(TAG, "Service unexpectedly exited");
        }
    }

    private void askMediaProjectionPermission() {
            mediaProjectionManager =
                    (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),
                PERM_MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PERM_MEDIA_PROJECTION_SERVICE:
                if (resultCode == RESULT_OK) {
                    startMediaProjection(data);
                    startHttpServer();
                }
                else {
                    resetStartButton();
                    stopService();
                }
                break;
            case PERM_ACTION_ACCESSIBILITY_SERVICE:
                if (isAccessibilityServiceEnabled())
                    enableAccessibilityService(true);
                else
                    resetRemoteControlSwitch();
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startMediaProjection(Intent data) {
        MediaProjection mediaProjection = mediaProjectionManager.getMediaProjection(RESULT_OK,
                data);
        screenCapture = new ScreenCapture(mediaProjection, getApplicationContext());
        appService.setScreenCapture(screenCapture);
        appService.startScreenCapture();
    }

    private void startHttpServer() {
        httpServer = new HttpServer(screenCapture, mouseAccessibilityService, httpServerPort,
                getApplicationContext());
        appService.setHttpServer(httpServer);
        appService.startHttpServer();
    }

    private void stopHttpServer() {
        appService.stopHttpServer();
    }

    private void setStartButton() {
        ToggleButton startButton = findViewById(R.id.startButton);
        startButton.setChecked(true);
    }

    private void resetStartButton() {
        ToggleButton startButton = findViewById(R.id.startButton);
        startButton.setChecked(false);
    }

    private void enableAccessibilityService(boolean isEnabled) {
        if (isEnabled) {
            if (httpServer != null && httpServer.getMouseAccessibilityService() != null)
                return;
            mouseAccessibilityService = new MouseAccessibilityService();
            if (httpServer != null)
                httpServer.setMouseAccessibilityService(mouseAccessibilityService);
        } else {
            if (httpServer != null)
                httpServer.setMouseAccessibilityService(null);
            mouseAccessibilityService = null;
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        Context context = getApplicationContext();
        ComponentName compName = new ComponentName(context, MouseAccessibilityService.class);
        String flatName = compName.flattenToString();
        String enabledList = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledList != null && enabledList.contains(flatName);
    }

    private void setRemoteControlSwitch() {
        Switch remoteControl = findViewById(R.id.remoteControlEnableSwitch);
        remoteControl.setChecked(true);
    }

    private void resetRemoteControlSwitch() {
        Switch remoteControl = findViewById(R.id.remoteControlEnableSwitch);
        remoteControl.setChecked(false);
    }

    private void remoteControlEnable(boolean isEnabled) {
        if (isEnabled) {
            if (!isAccessibilityServiceEnabled()) {
                startActivityForResult(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                        PERM_ACTION_ACCESSIBILITY_SERVICE);
            } else {
                enableAccessibilityService(true);
            }
        } else {
            enableAccessibilityService(false);
        }

    }

    public void createUrl() {
        LinearLayout urlLayout = findViewById(R.id.urlLinerLayout);
        TextView interfaceTextView = new TextView(this);
        interfaceTextView.setText(getResources().getString(R.string.no_active_connections));
        urlLayout.addView(interfaceTextView);
        checkNetworkStatePermission();
    }

    private void checkNetworkStatePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED) {
            networkHelper = new NetworkHelper(getApplicationContext(), onNetworkChangeListener);
            urlUpdate();
            return;
        }

        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_NETWORK_STATE }, PERM_ACCESS_NETWORK_STATE);
    }

    private NetworkHelper.OnNetworkChangeListener onNetworkChangeListener =
            new NetworkHelper.OnNetworkChangeListener() {
                @Override
                public void onChange() {
                    // Interfaces need some time to update
                    handler.sendEmptyMessageDelayed(HANDLER_MESSAGE_UPDATE_NETWORK, 1000);
                }
            };

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLER_MESSAGE_UPDATE_NETWORK:
                    urlUpdate();
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    private void urlUpdate() {
        LinearLayout urlLayout = findViewById(R.id.urlLinerLayout);
        urlLayout.removeAllViews();

        List<NetworkHelper.IpInfo> ipInfoList = networkHelper.getIpInfo();
        if (ipInfoList.isEmpty()) {
            TextView interfaceTextView = new TextView(this);
            interfaceTextView.setText(getResources().getString(R.string.no_active_connections));
            urlLayout.addView(interfaceTextView);
            return;
        }

        for (NetworkHelper.IpInfo ipInfo : ipInfoList) {
            TextView interfaceTextView = new TextView(this);
            String title = ipInfo.interfaceType + " (" + ipInfo.interfaceName + "):";
            interfaceTextView.setText(title);
            urlLayout.addView(interfaceTextView);

            List<LinkAddress> addresses = ipInfo.addresses;
            for (LinkAddress address : addresses) {
                TextView urlTextView = new TextView(this);
                String url;
                if (address.getAddress() instanceof Inet6Address) {
                    url ="http://[" + address.getAddress().getHostAddress() + "]:" +
                            httpServerPort;
                } else {
                    url = "http://" + address.getAddress().getHostAddress() + ":" + httpServerPort;
                }
                urlTextView.setText(url);
                urlLayout.addView(urlTextView);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void initSettings() {
        settingsHelper = new SettingsHelper(getApplicationContext(),
                new OnSettingsChangeListener());
        httpServerPort = settingsHelper.getPort();
    }

    public void uninitSettings() {
        settingsHelper.close();
        settingsHelper = null;
    }

    private class OnSettingsChangeListener implements SettingsHelper.OnSettingsChangeListener {
        @Override
        public void onPortChange(int port) {
            httpServerPort = port;
            urlUpdate();
            if (AppService.isServiceRunning()) {
                stopHttpServer();
                startHttpServer();
            }
        }
    }
}


