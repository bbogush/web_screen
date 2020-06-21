package com.bbogush.web_screen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCapture {
    private static final String TAG = ScreenCapture.class.getSimpleName();

    private static final String VIRTUAL_DISPLAY_NAME = "ScreenCaptureVirtualDisplay";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay = null;
    private ImageReader imageReader = null;

    Context context;
    private Display display;
    private DisplayMetrics screenMetrics = new DisplayMetrics();
    private Thread rotationDetectorThread;

    private Handler handler = null;

    private Bitmap bitmap = null;

    public interface OnBitmapAvailableListener {
        void onBitmapAvailable(Bitmap bitmap);
    }
    private final List<OnBitmapAvailableListener> bitmapListenersList = new ArrayList<>();

    public ScreenCapture(MediaProjection mediaProjection, Context context) {
        this.mediaProjection = mediaProjection;
        this.context = context;

        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        display = wm.getDefaultDisplay();
    }

    public void start() {
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                handler = new Handler();
                Looper.loop();
            }
        }.start();

        createVirtualDisplay();

        startRotationDetector();

        mediaProjection.registerCallback(new MediaProjectionStopCallback(), handler);
    }

    public void stop() {
        stopRotationDetector();
        handler.post(new Runnable() {
            @Override
            public void run() {
                mediaProjection.stop();
            }
        });
    }

    private void reset() {
        releaseVirtualDisplay();
        createVirtualDisplay();
    }

    private void startRotationDetector() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Rotation detector start");
                display.getRealMetrics(screenMetrics);
                while (true) {
                    DisplayMetrics metrics = new DisplayMetrics();
                    display.getRealMetrics(metrics);
                    if (metrics.widthPixels != screenMetrics.widthPixels ||
                            metrics.heightPixels != screenMetrics.heightPixels) {
                        Log.d(TAG, "Rotation detected\n" + "w=" + metrics.widthPixels + " h=" +
                                metrics.heightPixels + " d=" + metrics.densityDpi);
                        screenMetrics = metrics;
                        reset();
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Rotation detector exit");
                        Thread.interrupted();
                    }
                }
            }
        };
        rotationDetectorThread = new Thread(runnable);
        rotationDetectorThread.start();
    }

    private void stopRotationDetector() {
        rotationDetectorThread.interrupt();
    }

    private void createVirtualDisplay() {
        display.getRealMetrics(screenMetrics);

        imageReader = ImageReader.newInstance(screenMetrics.widthPixels,
                screenMetrics.heightPixels, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(VIRTUAL_DISPLAY_NAME,
                screenMetrics.widthPixels, screenMetrics.heightPixels, screenMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, imageReader.getSurface(),
                null, handler);

        imageReader.setOnImageAvailableListener(new ImageAvailableListener(), handler);
    }

    private void releaseVirtualDisplay() {
        if (virtualDisplay != null)
            virtualDisplay.release();
        if (imageReader != null)
            imageReader.setOnImageAvailableListener(null, null);
    }

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image == null)
                return;
            processScreenImage(image);
            image.close();
        }
    }

    private synchronized void processScreenImage(Image image) {
        if (bitmapListenersList.isEmpty())
            return;

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int width = planes[0].getRowStride() / planes[0].getPixelStride();

        if (width > image.getWidth()) {
            Bitmap tempBitmap = Bitmap.createBitmap(width, image.getHeight(),
                    Bitmap.Config.ARGB_8888);
            tempBitmap.copyPixelsFromBuffer(buffer);
            bitmap = Bitmap.createBitmap(tempBitmap, 0, 0, image.getWidth(), image.getHeight());
        } else {
            bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(),
                    Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(planes[0].getBuffer());
        }

        for (OnBitmapAvailableListener listener : bitmapListenersList)
            listener.onBitmapAvailable(bitmap);
        bitmap.recycle();
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    releaseVirtualDisplay();
                    mediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                }
            });
        }
    }

    public synchronized void registerOnBitmapAvailableListener(OnBitmapAvailableListener listener) {
        bitmapListenersList.add(listener);
        reset();
    }

    public synchronized void
        unregisterOnBitmapAvailableListener(OnBitmapAvailableListener listener) {
        bitmapListenersList.remove(listener);
    }
}
