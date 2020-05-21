package com.bbogush.web_screen;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MjpegStream extends InputStream {
    private static final String TAG = MjpegStream.class.getSimpleName();;

    private static final int BOUNDARY_LEN = 20;
    public static final String boundary = Utils.randomString(BOUNDARY_LEN);

    private static final String contentType = "Content-type: image/jpeg\r\n";
    private static final String contentLength = "Content-Length: %d\r\n\r\n";
    public static final String boundaryLine = "\r\n--" + boundary + "\r\n";

    private ScreenCapture screenCapture;
    private ByteArrayOutputStream imageStream = new ByteArrayOutputStream();
    private ByteArrayOutputStream dataStream = new ByteArrayOutputStream();

    private boolean isFirstBoundary = true;
    private int pos = 0;

    private static final long STREAM_DELAY_MS = 40;
    private long timestamp;

    public MjpegStream(ScreenCapture screenCapture) {
        super();

        this.screenCapture = screenCapture;

        timestamp = System.nanoTime();
    }

    @Override
    public int available() {
        throw new UnsupportedOperationException("available() method is not implemented");
    }

    @Override
    public int read() {
        throw new UnsupportedOperationException("read() method is not implemented");
    }

    private boolean rateLimit() {
        long timeElapsedMs = (System.nanoTime() - timestamp) / 1000;
        if (timeElapsedMs < STREAM_DELAY_MS) {
            try {
                Thread.sleep(STREAM_DELAY_MS - timeElapsedMs);
            } catch (InterruptedException e) {
                Log.d(TAG, "Thread interrupted");
                Thread.interrupted();
                return false;
            }
        }
        timestamp = System.nanoTime();

        return true;
    }

    private void updateImage() {
        synchronized (screenCapture.bitmapDataLock) {
            Bitmap bitmap = screenCapture.getBitmap();
            if (bitmap == null)
                bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);

            imageStream.reset();

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, imageStream);
        }
    }

    private void createContent() {
        dataStream.reset();
        if (isFirstBoundary) {
            dataStream.write(boundaryLine.getBytes(StandardCharsets.US_ASCII), 0,
                    boundaryLine.toCharArray().length);
            isFirstBoundary = false;
        }
        dataStream.write(contentType.getBytes(StandardCharsets.US_ASCII), 0, contentType.toCharArray().length);
        String contentLengthString = String.format(contentLength, imageStream.size());
        dataStream.write(contentLengthString.getBytes(StandardCharsets.US_ASCII), 0,
                contentLengthString.toCharArray().length);
        dataStream.write(imageStream.toByteArray(), 0, imageStream.size());
        dataStream.write(boundaryLine.getBytes(StandardCharsets.US_ASCII), 0, boundaryLine.toCharArray().length);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
        int copy = 0;

        if (pos == 0) {
            if (!rateLimit()) {
                Log.d(TAG, "Close stream");
                return -1;
            }

            updateImage();
            createContent();
        }

        copy = Math.min(length, dataStream.size() - pos);
        System.arraycopy(dataStream.toByteArray(), pos, buffer, offset, copy);
        pos += copy;
        if(pos >= dataStream.size())
            pos = 0;

        return copy;
    }

    @Override
    public void close() {
        Log.d(TAG, "Stream is closed");
        isFirstBoundary = true;
        pos = 0;
    }
}