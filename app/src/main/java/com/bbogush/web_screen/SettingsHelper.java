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
    private static final String SETTINGS_NAME_PORT = "port";
    private static final String SETTINGS_NAME_REMOTE_CONTROL = "remote_control";
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
        String portString = sharedPreferences.getString(SETTINGS_NAME_PORT,
                Integer.toString(HTTP_SERVER_PORT_DEFAULT));
        try {
            port = Integer.parseInt(portString);
        } catch (Exception e) {
            Log.d(TAG, "Failed to parse port settings");
            port = HTTP_SERVER_PORT_DEFAULT;
        }

        return port;
    }

    public void setRemoteControlEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(SETTINGS_NAME_REMOTE_CONTROL, enabled);
        editor.commit();
    }

    public boolean isRemoteControlEnabled() {
        boolean isEnabled;
        try {
            isEnabled = sharedPreferences.getBoolean(SETTINGS_NAME_REMOTE_CONTROL, false);
        } catch (Exception e) {
            Log.d(TAG, "Failed to parse remote control settings");
            isEnabled = false;
        }

        return isEnabled;
    }

    private class SharedPreferenceChangeListener implements
            SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (onSettingsChangeListener == null)
                return;
            if (key.equals(SETTINGS_NAME_PORT)) {
                onSettingsChangeListener.onPortChange(getPort());
            }
        }
    }
}
