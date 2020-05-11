package com.bbogush.web_screen;

import android.app.Service;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;

public class ScreenCapture {
    private Display display;
    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    Bitmap bitmap = null;
    VirtualDisplay virtualDisplay;
    Handler handler = new Handler(Looper.getMainLooper());

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void start(MediaProjection mediaProjection) {
        //Point screenSize = new Point();
        //DisplayMetrics screenMetrics = new DisplayMetrics();
        //Display display = getWindowManager().getDefaultDisplay();

        // start capture handling thread

//        new Thread()
//        {
//            @Override
//            public void run()
//            {
//                Looper.prepare();
//                handler = new Handler(Looper.getMainLooper());
//                Looper.loop();
//            }
//        }.start();


        DisplayMetrics screenMetrics = Resources.getSystem().getDisplayMetrics();


        //display.getRealSize(screenSize);
        //display.getMetrics(screenMetrics);

        Log.d("ScreenCapture", "w=" + screenMetrics.widthPixels + " h=" + screenMetrics.heightPixels);

        try {
            imageReader = ImageReader.newInstance(838, 1244,
                    PixelFormat.RGBA_8888, 1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        imageReader.setOnImageAvailableListener(
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Log.d("ScreenCapture", "setOnImageAvailableListener");
                        Image image = imageReader.acquireLatestImage();
                        if (image != null) {
                            processScreenImage(image);
                            image.close();
                        }
                    }
                }, null);

        try {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenStreamVirtualDisplay", 838, 1244,
                    440, DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                    imageReader.getSurface(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void stop() {
        virtualDisplay.release();
        imageReader.close();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void processScreenImage(Image image) {
        Log.d("ScreenCapture", "processScreenImage");
        Image.Plane [] planes = image.getPlanes();
        bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(),
                Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(planes[0].getBuffer());
    }

    public Bitmap getBitmap() {
        return bitmap;
    }
}
