package com.bbogush.web_screen;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.RequiresApi;

import java.util.List;

public class MouseAccessibilityService extends AccessibilityService {
    //private WindowManager windowManager;
    private static MouseAccessibilityService instance;

    @Override
    public void onCreate() {
        super.onCreate();

        //windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.d("Access", "Event");
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo == null) {
            Log.d("Access", "null!!!");
        }
    }

    @Override
    public void onInterrupt() {
    }

    private AccessibilityNodeInfo findSmallestNodeAtPoint(AccessibilityNodeInfo sourceNode, int x, int y) {
        Rect bounds = new Rect();
        sourceNode.getBoundsInScreen(bounds);

        if (!bounds.contains(x, y)) {
            return null;
        }

        for (int i = 0; i < sourceNode.getChildCount(); i++) {
            AccessibilityNodeInfo nearestSmaller = findSmallestNodeAtPoint(sourceNode.getChild(i), x, y);
            if (nearestSmaller != null) {
                return nearestSmaller;
            }
        }
        return sourceNode;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static void tap(int x, int y)
    {
        boolean ret = false;
        Log.d("Access", "tap1");
        GestureDescription desc = createClick(x, y, ViewConfiguration.getTapTimeout() + 50);
        if (desc == null) {
            Log.d("desc", "null");
            return;
        }

        try {
            ret = instance.dispatchGesture(desc, null, null);
        } catch (Exception e) {
            Log.w("Access", "Exception");
            e.printStackTrace();
        }

        if (ret) {
            Log.d("Access", "good");
        } else {
            Log.d("Access", "bad");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static GestureDescription createClick(int x, int y, int duration )
    {
        Path clickPath = new Path();
        clickPath.moveTo( x, y );
        GestureDescription.StrokeDescription clickStroke = new GestureDescription.StrokeDescription( clickPath, 0, duration );
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke( clickStroke );
        return clickBuilder.build();
    }

//    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
//    public void click(int x, int y) {
//        Log.d("Access", "click");
//        AccessibilityServiceInfo info = getServiceInfo();
//        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
//        setServiceInfo(info);
//
//        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
//        if (nodeInfo == null) {
//            return;
//        }
//        Log.d("Access", "nodeInfo");
//        AccessibilityNodeInfo nearestNodeToMouse = findSmallestNodeAtPoint(nodeInfo, x, y);
//        if (nearestNodeToMouse != null) {
//            Log.d("Access", "CLICK");
//            nearestNodeToMouse.performAction(AccessibilityNodeInfo.ACTION_CLICK);
//        }
//        nodeInfo.recycle();
//    }

}
