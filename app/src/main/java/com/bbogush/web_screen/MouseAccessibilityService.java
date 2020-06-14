package com.bbogush.web_screen;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MouseAccessibilityService extends AccessibilityService {
    private static final String TAG = MouseAccessibilityService.class.getSimpleName();

    private class Gesture {
        public int x1, y1, x2, y2;
        public boolean isContinuedGesture;
        public boolean isContinue;

        Gesture(int x1, int y1, int x2, int y2, boolean isContinuedGesture, boolean isContinue) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.isContinuedGesture = isContinuedGesture;
            this.isContinue = isContinue;
        }
    }

    private static MouseAccessibilityService instance;

    private AtomicBoolean lock = new AtomicBoolean(false);
    private boolean isMouseDown = false;
    private GestureDescription.StrokeDescription currentStroke;
    private int prevX, prevY;
    List<Gesture> gestureList = new ArrayList<>();

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

    public void mouseDown(int x, int y) {
        synchronized (lock) {
            Gesture gesture = new Gesture(x, y, x, y, false, true);
            scheduleGesture(gesture);

            prevX = x;
            prevY = y;
            isMouseDown = true;
        }
    }

    public void mouseMove(int x, int y) {
        synchronized (lock) {
            if (!isMouseDown)
                return;
            if (prevX == x && prevY == y)
                return;

            Gesture gesture = new Gesture(prevX, prevY, x, y, true, true);
            scheduleGesture(gesture);

            prevX = x;
            prevY = y;
        }
    }

    public void mouseUp(int x, int y) {
        synchronized (lock) {
            Gesture gesture = new Gesture(prevX, prevY, x, y, true, false);
            scheduleGesture(gesture);

            isMouseDown = false;
        }
    }

    private void scheduleGesture(Gesture gesture) {
        if (gestureList.isEmpty()) {
            gestureList.add(gesture);
            dispatchGestureHandler();
        } else {
            gestureList.add(gesture);
        }
    }

    private void dispatchGestureHandler() {
        Gesture gesture = gestureList.get(0);

        Path path = new Path();
        path.moveTo(gesture.x1, gesture.y1);
        if (gesture.x1 != gesture.x2 || gesture.y1 != gesture.y2)
            path.lineTo(gesture.x2, gesture.y2);

        GestureDescription.StrokeDescription stroke;
        if (!gesture.isContinuedGesture)
            stroke = new GestureDescription.StrokeDescription(path, 0, 1, gesture.isContinue);
        else
            stroke = currentStroke.continueStroke(path, 0, 1, gesture.isContinue);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        GestureDescription gestureDescriptionNext = builder.build();
        if (!instance.dispatchGesture(gestureDescriptionNext, gestureResultCallback, null)) {
            Log.e(TAG, "Gesture was not dispatched");
            gestureList.clear();
            return;
        }

        gestureList.remove(0);
        currentStroke = stroke;
    }

    private AccessibilityService.GestureResultCallback gestureResultCallback =
            new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    synchronized (lock) {
                        if (gestureList.isEmpty())
                            return;
                        dispatchGestureHandler();
                    }

                    super.onCompleted(gestureDescription);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    synchronized (lock) {
                        Log.d(TAG, "Gesture canceled");
                        super.onCancelled(gestureDescription);
                    }
                }
            };

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
}
