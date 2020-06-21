package com.bbogush.web_screen;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MjpegStream extends InputStream {
    private static final String TAG = MjpegStream.class.getSimpleName();

    private static final int BOUNDARY_LEN = 20;
    public static final String boundary = Utils.randomString(BOUNDARY_LEN);

    private static final String contentType = "Content-type: image/jpeg\r\n";
    private static final String contentLength = "Content-Length: %d\r\n\r\n";
    public static final String boundaryLine = "\r\n--" + boundary + "\r\n";

    private ScreenCapture screenCapture;
    private ByteArrayOutputStream imageStream = new ByteArrayOutputStream();
    private ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
    private byte [] data;
    private int dataSize;

    private boolean isFirstBoundary = true;
    private int pos = 0;

    private Object syncToken = new Object();
    private OnBitmapAvailableListener bitmapListener = new OnBitmapAvailableListener();

    public MjpegStream(ScreenCapture screenCapture) {
        super();

        this.screenCapture = screenCapture;
        screenCapture.registerOnBitmapAvailableListener(bitmapListener);
    }

    @Override
    public void close() {
        Log.d(TAG, "Stream is closed");
        screenCapture.unregisterOnBitmapAvailableListener(bitmapListener);
        isFirstBoundary = true;
        pos = 0;
    }

    private class OnBitmapAvailableListener implements ScreenCapture.OnBitmapAvailableListener {
        @Override
        public void onBitmapAvailable(Bitmap bitmap) {
            imageStream.reset();
            synchronized(syncToken) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, imageStream);

                syncToken.notify();
            }
        }
    }

    @Override
    public int available() {
        throw new UnsupportedOperationException("available() method is not implemented");
    }

    @Override
    public int read() {
        throw new UnsupportedOperationException("read() method is not implemented");
    }

    private void createContent() {
        dataStream.reset();
        if (isFirstBoundary) {
            dataStream.write(boundaryLine.getBytes(StandardCharsets.US_ASCII), 0,
                    boundaryLine.toCharArray().length);
            isFirstBoundary = false;
        }
        dataStream.write(contentType.getBytes(StandardCharsets.US_ASCII), 0,
                contentType.toCharArray().length);
        String contentLengthString = String.format(contentLength, imageStream.size());
        dataStream.write(contentLengthString.getBytes(StandardCharsets.US_ASCII), 0,
                contentLengthString.toCharArray().length);
        dataStream.write(imageStream.toByteArray(), 0, imageStream.size());
        dataStream.write(boundaryLine.getBytes(StandardCharsets.US_ASCII), 0,
                boundaryLine.toCharArray().length);
        data = dataStream.toByteArray();
        dataSize = dataStream.size();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
        int copy = 0;

        if (pos == 0) {
            synchronized(syncToken) {
                try {
                    syncToken.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return 0;
                }

                createContent();
            }
        }

        copy = Math.min(length, dataSize - pos);
        System.arraycopy(data, pos, buffer, offset, copy);
        pos += copy;
        if(pos >= dataSize)
            pos = 0;

        return copy;
    }
}