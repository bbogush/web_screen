package com.bbogush.web_screen;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

public class SettingsHelper {
    public interface OnSettingsChangeListener {
        void onPortChange(int port);
    }

    private static final String TAG = SettingsHelper.class.getSimpleName();

    private static final int HTTP_SERVER_PORT_DEFAULT = 8080;
    private static final String HTTP_SERVER_PORT_SETTINGS_NAME = "port";
    private OnSettingsChangeListener onSettingsChangeListener;

    private SharedPreferenceChangeListener sharedPreferenceChangeListener;
    private SharedPreferences sharedPreferences;

    SettingsHelper(Context context, OnSettingsChangeListener listener) {
        sharedPreferenceChangeListener = new SharedPreferenceChangeListener();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        onSettingsChangeListener = listener;
    }

    public void close() {
        onSettingsChangeListener = null;
        sharedPreferences.
                unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        sharedPreferenceChangeListener = null;
        sharedPreferences = null;
    }

    public int getPort() {
        int port;
        String portString = sharedPreferences.getString(HTTP_SERVER_PORT_SETTINGS_NAME,
                Integer.toString(HTTP_SERVER_PORT_DEFAULT));
        try {
            port = Integer.parseInt(portString);
        } catch (Exception e) {
            Log.d(TAG, "Failed to parse port settings");
            port = HTTP_SERVER_PORT_DEFAULT;
        }

        return port;
    }

    private class SharedPreferenceChangeListener implements
            SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (onSettingsChangeListener == null)
                return;
            if (key.equals(HTTP_SERVER_PORT_SETTINGS_NAME)) {
                onSettingsChangeListener.onPortChange(getPort());
            }
        }
    }
}
