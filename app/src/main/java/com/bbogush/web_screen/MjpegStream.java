package com.bbogush.web_screen;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MjpegStream extends InputStream {
    private static final String TAG = MjpegStream.class.getSimpleName();

    private static final int BOUNDARY_LEN = 20;
    public static final String boundary = Utils.randomString(BOUNDARY_LEN);

    private static final String contentType = "Content-type: image/jpeg\r\n";
    private static final String contentLength = "Content-Length: %d\r\n\r\n";
    public static final String boundaryLine = "\r\n--" + boundary + "\r\n";

    private static final byte [] boundaryLineByteArray =
            boundaryLine.getBytes(StandardCharsets.US_ASCII);
    private static final byte [] contentTypeByteArray =
            contentType.getBytes(StandardCharsets.US_ASCII);
    private byte [] contentLengthStringByteArray;
    private byte [] imageByteArray;

    private ScreenCapture screenCapture;
    private ByteArrayOutputStream imageStream = new ByteArrayOutputStream();

    private int sPos = 0, dPos = 0;
    private boolean skipWait;

    private static final int STATE_FIRST_BOUNDARY = 0;
    private static final int STATE_CONTENT_TYPE = 1;
    private static final int STATE_CONTENT_LENGTH = 2;
    private static final int STATE_IMAGE = 3;
    private static final int STATE_TRAIL_BOUNDARY = 4;
    private int state = STATE_FIRST_BOUNDARY;

    private final Object syncToken = new Object();
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

    private void initContentLength() {
        String contentLengthString = String.format(Locale.US, contentLength, imageStream.size());
        contentLengthStringByteArray = contentLengthString.getBytes(StandardCharsets.US_ASCII);
    }

    //TODO avoid toByteArray
    private void initImage() {
        imageByteArray = imageStream.toByteArray();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
        int copy = 0, copied = 0;

        switch (state) {
            case STATE_FIRST_BOUNDARY:
                synchronized (syncToken) {
                    try {
                        syncToken.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
                copy = Math.min(length, boundaryLineByteArray.length - sPos);
                System.arraycopy(boundaryLineByteArray, sPos, buffer, offset + dPos, copy);
                sPos += copy;
                dPos += copy;
                copied += copy;

                length -= copy;
                if (length > 0) {
                    sPos = 0;
                    skipWait = true;
                    state = STATE_CONTENT_TYPE;
                    // fall through
                } else if (sPos == boundaryLineByteArray.length) {
                    sPos = 0;
                    dPos = 0;
                    skipWait = true;
                    state = STATE_CONTENT_TYPE;
                    break;
                } else {
                    dPos = 0;
                    break;
                }
            case STATE_CONTENT_TYPE:
                synchronized (syncToken) {
                    try {
                        if (!skipWait)
                            syncToken.wait();
                        else
                            skipWait = false;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return 0;
                    }
                }
                copy = Math.min(length, contentTypeByteArray.length - sPos);
                System.arraycopy(contentTypeByteArray, sPos, buffer, offset + dPos, copy);
                sPos += copy;
                dPos += copy;
                copied += copy;

                length -= copy;
                if (length > 0) {
                    sPos = 0;
                    state = STATE_CONTENT_LENGTH;
                    initContentLength();
                    // fall through
                } else if (sPos == contentTypeByteArray.length) {
                    sPos = 0;
                    dPos = 0;
                    state = STATE_CONTENT_LENGTH;
                    initContentLength();
                    break;
                } else {
                    dPos = 0;
                    break;
                }
            case STATE_CONTENT_LENGTH:
                copy = Math.min(length, contentLengthStringByteArray.length - sPos);
                System.arraycopy(contentLengthStringByteArray, sPos, buffer, offset + dPos, copy);
                sPos += copy;
                dPos += copy;
                copied += copy;

                length -= copy;
                if (length > 0) {
                    sPos = 0;
                    state = STATE_IMAGE;
                    initImage();
                    // fall through
                } else if (sPos == contentLengthStringByteArray.length) {
                    sPos = 0;
                    dPos = 0;
                    state = STATE_IMAGE;
                    initImage();
                    break;
                } else {
                    dPos = 0;
                    break;
                }
            case STATE_IMAGE:
                copy = Math.min(length, imageByteArray.length - sPos);
                System.arraycopy(imageByteArray, sPos, buffer, offset + dPos, copy);
                sPos += copy;
                dPos += copy;
                copied += copy;

                length -= copy;
                if (length > 0) {
                    sPos = 0;
                    state = STATE_TRAIL_BOUNDARY;
                    // fall through
                } else if (sPos == imageByteArray.length) {
                    sPos = 0;
                    dPos = 0;
                    state = STATE_TRAIL_BOUNDARY;
                    break;
                } else {
                    dPos = 0;
                    break;
                }
            case STATE_TRAIL_BOUNDARY:
                copy = Math.min(length, boundaryLineByteArray.length - sPos);
                System.arraycopy(boundaryLineByteArray, sPos, buffer, offset + dPos, copy);
                sPos += copy;
                dPos += copy;
                copied += copy;

                if (sPos == boundaryLineByteArray.length) {
                    sPos = 0;
                    dPos = 0;
                    state = STATE_CONTENT_TYPE;
                    break;
                } else {
                    dPos = 0;
                    break;
                }
        }

        return copied;
    }
}
