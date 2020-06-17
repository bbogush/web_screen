package com.bbogush.web_screen;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionHelper {

    public interface OnPermissionGrantedListener {
        void onAccessNetworkStatePermissionGranted(boolean isGranted);
        void onInternetPermissionGranted(boolean isGranted);
        void onReadExternalStoragePermissionGranted(boolean isGranted);
        void onWakeLockPermissionGranted(boolean isGranted);
        void onForegroundServicePermissionGranted(boolean isGranted);
    }

    private static final int PERM_ACCESS_NETWORK_STATE = 0;
    private static final int PERM_INTERNET = 1;
    private static final int PERM_READ_EXTERNAL_STORAGE = 2;
    private static final int PERM_WAKE_LOCK = 3;
    private static final int PERM_FOREGROUND_SERVICE = 4;

    private Activity activity;
    private OnPermissionGrantedListener onPermissionGrantedListener;

    public PermissionHelper(Activity a, OnPermissionGrantedListener listener) {
        activity = a;
        onPermissionGrantedListener = listener;
    }

    public void requestAccessNetworkStatePermission() {
        if (ContextCompat.checkSelfPermission(activity.getApplicationContext(),
                Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED) {
            onPermissionGrantedListener.onAccessNetworkStatePermissionGranted(true);
            return;
        }

        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.ACCESS_NETWORK_STATE }, PERM_ACCESS_NETWORK_STATE);
    }

    public void requestInternetPermission() {
        if (onPermissionGrantedListener == null)
            return;

        if (ContextCompat.checkSelfPermission(activity.getApplicationContext(),
                Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            onPermissionGrantedListener.onInternetPermissionGranted(true);
            return;
        }

        ActivityCompat.requestPermissions(activity, new String[]{ Manifest.permission.INTERNET },
                PERM_INTERNET);
    }

    public void requestReadExternalStoragePermission() {
        if (ContextCompat.checkSelfPermission(activity.getApplicationContext(),
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            onPermissionGrantedListener.onReadExternalStoragePermissionGranted(true);
            return;
        }

        ActivityCompat.requestPermissions(activity,
                new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE },
                PERM_READ_EXTERNAL_STORAGE);
    }

    public void requestWakeLockPermission() {
        if (ContextCompat.checkSelfPermission(activity.getApplicationContext(),
                Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED) {
            onPermissionGrantedListener.onWakeLockPermissionGranted(true);
            return;
        }

        ActivityCompat.requestPermissions(activity,
                new String[]{ Manifest.permission.WAKE_LOCK },
                PERM_WAKE_LOCK);
    }

    public void requestForegroundServicePermission() {
        if (ContextCompat.checkSelfPermission(activity.getApplicationContext(),
                Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED) {
            onPermissionGrantedListener.onForegroundServicePermissionGranted(true);
            return;
        }

        ActivityCompat.requestPermissions(activity,
                new String[]{ Manifest.permission.FOREGROUND_SERVICE },
                PERM_FOREGROUND_SERVICE);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        switch (requestCode) {
            case PERM_ACCESS_NETWORK_STATE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onPermissionGrantedListener.onAccessNetworkStatePermissionGranted(true);
                } else {
                    onPermissionGrantedListener.onAccessNetworkStatePermissionGranted(false);
                }
                break;
            case PERM_INTERNET:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onPermissionGrantedListener.onInternetPermissionGranted(true);
                } else {
                    onPermissionGrantedListener.onInternetPermissionGranted(false);
                }
                break;
            case PERM_READ_EXTERNAL_STORAGE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onPermissionGrantedListener.onReadExternalStoragePermissionGranted(true);
                } else {
                    onPermissionGrantedListener.onReadExternalStoragePermissionGranted(false);
                }
                break;
            case PERM_WAKE_LOCK:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onPermissionGrantedListener.onWakeLockPermissionGranted(true);
                } else {
                    onPermissionGrantedListener.onWakeLockPermissionGranted(false);
                }
                break;
            case PERM_FOREGROUND_SERVICE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onPermissionGrantedListener.onForegroundServicePermissionGranted(true);
                } else {
                    onPermissionGrantedListener.onForegroundServicePermissionGranted(false);
                }
                break;
        }
    }
}
