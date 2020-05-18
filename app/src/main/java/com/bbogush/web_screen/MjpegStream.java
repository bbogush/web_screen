package com.bbogush.web_screen;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MjpegStream extends InputStream {
    enum State { BOUND, TYPE, LENGTH, JPEG };

    private static final int BOUNDARY_LEN = 20;
    public static final String boundary = Utils.randomString(BOUNDARY_LEN);

    private static final String contentType = "Content-type: image/jpeg\n";
    private static final String contentLength = "Content-Length: %d\n\n";
    public static final String boundaryLine = "\n--" + boundary + "\n";

    private ScreenCapture screenCapture;

    private State state;
    int len = 0;
    private String contentLengthString;
    int pos;

    private byte[] byteArray;

    private int lastImageIndex = -1;
    private static int imageIndex = 0;

    public MjpegStream(ScreenCapture screenCapture) {
        super();

        state = State.TYPE;
        pos = 0;

        this.screenCapture = screenCapture;
    }

    @Override
    public int available() {
        throw new UnsupportedOperationException("available() method is not implemented");
    }

    @Override
    public int read() {
        throw new UnsupportedOperationException("read() method is not implemented");
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
        int copy = 0;

        if(lastImageIndex == imageIndex)
            return 0;

        switch(state) {
            case BOUND:
                copy = Math.min(length, boundaryLine.length() - pos);
                System.arraycopy(boundaryLine.getBytes(), pos, buffer, 0, copy);
                pos += copy;
                if(pos >= boundaryLine.length()) {
                    pos = 0;
                    state = State.TYPE;
                }
                break;
            case TYPE:
                updateBitmap();
                copy = Math.min(length, contentType.length() - pos);
                System.arraycopy(contentType.getBytes(), pos, buffer, 0, copy);
                pos += copy;
                if(pos >= contentType.length()) {
                    len = byteArray.length;
                    contentLengthString = String.format(contentLength, len);

                    state = State.LENGTH;
                    pos = 0;
                }
                break;
            case LENGTH:
                copy = Math.min(length, contentLengthString.length() - pos);
                System.arraycopy(contentLengthString.getBytes(), pos, buffer, 0, copy);
                pos += copy;
                if(pos >= contentLengthString.length()) {
                    state = State.JPEG;
                    pos = 0;
                }
                break;
            case JPEG:
                copy = Math.min(length, byteArray.length - pos);

                if(copy <= 0) {
                    state = State.BOUND;
                    pos = 0;
                    copy = -1;
                }
                else {
                    System.arraycopy(byteArray, pos, buffer, 0, copy);
                    pos += copy;
                    if(pos >= byteArray.length) {
                        state = State.BOUND;
                        pos = 0;
                    }
                }
                break;
        }
        return copy;
    }

    private void updateBitmap() {
        Bitmap bitmap = screenCapture.getBitmap();
        if (bitmap == null) {
            int w = 20, h = 20;
            Bitmap.Config conf = Bitmap.Config.ARGB_8888;
            bitmap = Bitmap.createBitmap(w, h, conf);
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byteArray = stream.toByteArray();
    }
}