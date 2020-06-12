package com.bbogush.web_screen;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

public class MouseAccessibilityService extends AccessibilityService {
    private static final String TAG = MouseAccessibilityService.class.getSimpleName();

    private static MouseAccessibilityService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    public void click(int x, int y) {
        Log.d(TAG, "Click x=" + x + " y=" + y);

        GestureDescription desc = createClick(x, y, ViewConfiguration.getTapTimeout() + 50);
        if (!instance.dispatchGesture(desc, null, null))
            Log.w(TAG, "Click gesture was not dispatched");
    }

    public void backButtonClick() {
        Log.d(TAG, "Back button pressed");
        instance.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    }

    public void homeButtonClick() {
        Log.d(TAG, "Home button pressed");
        instance.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
    }

    public void recentButtonClick() {
        Log.d(TAG, "Recent button pressed");
        instance.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
    }

    private static GestureDescription createClick(int x, int y, int duration) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.StrokeDescription clickStroke =
                new GestureDescription.StrokeDescription(clickPath, 0, duration);
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
    }
}
