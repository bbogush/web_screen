package com.bbogush.web_screen;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.util.DisplayMetrics;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenCapture {
    private ImageReader imageReader;
    Bitmap bitmap = null;
    public AtomicBoolean bitmapDataLock = new AtomicBoolean(false);
    VirtualDisplay virtualDisplay;
    DisplayMetrics screenMetrics;

    public void start(MediaProjection mediaProjection) {
        screenMetrics = Resources.getSystem().getDisplayMetrics();

        try {
            imageReader = ImageReader.newInstance(screenMetrics.widthPixels,
                    screenMetrics.heightPixels, PixelFormat.RGBA_8888, 2);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        imageReader.setOnImageAvailableListener(
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = imageReader.acquireLatestImage();
                        if (image != null) {
                            processScreenImage(image);
                            image.close();
                        }
                    }
                }, null);

        try {
            virtualDisplay = mediaProjection.createVirtualDisplay("VirtualDisplay",
                    screenMetrics.widthPixels, screenMetrics.heightPixels, screenMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, imageReader.getSurface(),
                    null, null);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public void stop() {
        imageReader.close();
        virtualDisplay.release();
    }

    private void processScreenImage(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int width = planes[0].getRowStride() / planes[0].getPixelStride();

        synchronized (bitmapDataLock) {
            if (width > image.getWidth()) {
                Bitmap tempBitmap = Bitmap.createBitmap(width, screenMetrics.heightPixels,
                        Bitmap.Config.ARGB_8888);
                tempBitmap.copyPixelsFromBuffer(buffer);
                bitmap = Bitmap.createBitmap(tempBitmap, 0, 0, image.getWidth(),
                        image.getHeight());
            } else {
                bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(),
                        Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(planes[0].getBuffer());
            }
        }
    }

    public Bitmap getBitmap() {
        return bitmap;
    }
}
