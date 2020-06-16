package com.bbogush.web_screen;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MouseAccessibilityService extends AccessibilityService {
    private static final String TAG = MouseAccessibilityService.class.getSimpleName();

    private static final int PINCH_DURATION_MS = 400;
    private static final int PINCH_DISTANCE_CLOSE = 200;
    private static final int PINCH_DISTANCE_FAR = 800;

    private static MouseAccessibilityService instance;

    private AtomicBoolean lock = new AtomicBoolean(false);
    private boolean isMouseDown = false;
    private GestureDescription.StrokeDescription currentStroke = null;
    private int prevX = 0, prevY = 0;
    private List<GestureDescription> gestureList = new LinkedList<>();
    private Display display = null;

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

    public void setContext(Context context) {
        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        display = wm.getDefaultDisplay();
    }

    public void mouseDown(int x, int y) {
        Log.d(TAG, "Mouse left button down at x=" + x + " y=" + y);
        synchronized (lock) {
            GestureDescription gesture = buildGesture(x, y, x, y, 0, 1, false, true);
            gestureList.add(gesture);
            if (gestureList.size() == 1)
                dispatchGestureHandler();

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

            GestureDescription gesture = buildGesture(prevX, prevY, x, y, 0, 1, true, true);
            gestureList.add(gesture);
            if (gestureList.size() == 1)
                dispatchGestureHandler();

            prevX = x;
            prevY = y;
        }
    }

    public void mouseUp(int x, int y) {
        Log.d(TAG, "Mouse left button up at x=" + x + " y=" + y);
        synchronized (lock) {
            GestureDescription gesture = buildGesture(prevX, prevY, x, y, 0, 1, true, false);
            gestureList.add(gesture);
            if (gestureList.size() == 1)
                dispatchGestureHandler();

            isMouseDown = false;
        }
    }

    private GestureDescription buildGesture(int x1, int y1, int x2, int y2, long startTime,
                                            long duration, boolean isContinuedGesture,
                                            boolean willContinue) {
        Path path = new Path();
        path.moveTo(x1, y1);
        if (x1 != x2 || y1 != y2)
            path.lineTo(x2, y2);

        GestureDescription.StrokeDescription stroke;
        if (!isContinuedGesture) {
            stroke = new GestureDescription.StrokeDescription(path, startTime, duration,
                    willContinue);
        }
        else {
            stroke = currentStroke.continueStroke(path, startTime, duration, willContinue);
        }

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        GestureDescription gestureDescription = builder.build();

        currentStroke = stroke;

        return gestureDescription;
    }

    public void mouseWheelZoomIn(int x, int y) {
        Log.d(TAG, "Zoom in at x=" + x + " y=" + y);
        synchronized (lock) {
            pinchGesture(x, y, PINCH_DISTANCE_CLOSE, PINCH_DISTANCE_FAR);
        }
    }

    public void mouseWheelZoomOut(int x, int y) {
        Log.d(TAG, "Zoom out at x=" + x + " y=" + y);
        synchronized (lock) {
            pinchGesture(x, y, PINCH_DISTANCE_FAR, PINCH_DISTANCE_CLOSE);
        }
    }

    private void pinchGesture(int x, int y, int startSpacing, int endSpacing) {
        int x1 = x - startSpacing / 2;
        int y1 = y - startSpacing / 2;
        int x2 = x - endSpacing / 2;
        int y2 = y - endSpacing / 2;

        if (x1 < 0)
            x1 = 0;
        if (y1 < 0)
            y1 = 0;
        if (x2 < 0)
            x2 = 0;
        if (y2 < 0)
            y2 = 0;

        Path path1 = new Path();
        path1.moveTo(x1, y1);
        path1.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke1 = new
                GestureDescription.StrokeDescription(path1, 0, PINCH_DURATION_MS, false);

        x1 = x + startSpacing / 2;
        y1 = y + startSpacing / 2;
        x2 = x + endSpacing / 2;
        y2 = y + endSpacing / 2;

        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        if (x1 > metrics.widthPixels)
            x1 = metrics.widthPixels;
        if (y1 > metrics.heightPixels)
            y1 = metrics.heightPixels;
        if (x2 > metrics.widthPixels)
            x2 = metrics.widthPixels;
        if (y2 > metrics.heightPixels)
            y2 = metrics.heightPixels;

        Path path2 = new Path();
        path2.moveTo(x1, y1);
        path2.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke2 = new
                GestureDescription.StrokeDescription(path2, 0, PINCH_DURATION_MS, false);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke1);
        builder.addStroke(stroke2);
        GestureDescription gesture = builder.build();

        gestureList.add(gesture);
        if (gestureList.size() == 1)
            dispatchGestureHandler();
    }

    private void dispatchGestureHandler() {
        GestureDescription gesture = gestureList.get(0);

        if (!instance.dispatchGesture(gesture, gestureResultCallback, null)) {
            Log.e(TAG, "Gesture was not dispatched");
            gestureList.clear();
            return;
        }
    }

    private AccessibilityService.GestureResultCallback gestureResultCallback =
            new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    synchronized (lock) {
                        gestureList.remove(0);
                        if (gestureList.isEmpty())
                            return;
                        dispatchGestureHandler();
                    }

                    super.onCompleted(gestureDescription);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    synchronized (lock) {
                        Log.w(TAG, "Gesture canceled");
                        gestureList.remove(0);
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
