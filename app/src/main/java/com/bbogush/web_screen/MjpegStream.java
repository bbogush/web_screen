package com.bbogush.web_screen;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class MjpegStream extends InputStream
{
    enum State { BOUND, TYPE, LENGTH, JPEG };

    private static final int BOUNDARY_LEN = 20;
    public static final String boundary = Utils.randomString(BOUNDARY_LEN);

    private static final String contentType = "Content-type: image/jpeg\n";
    private static final String contentLength = "Content-Length: %d\n\n";
    public static final String boundaryLine = "\n--" + boundary + "\n";
    public static AtomicBoolean imageDataLock = new AtomicBoolean(false);

    private ScreenCapture screenCapture;

    private State state;
    int len = 0;
    private String contentLengthString;
    int pos;

    private byte[] byteArray;

    private int lastImageIndex = -1;
    private static int imageIndex = 0;

    public MjpegStream(ScreenCapture screenCapture)
    {
        super();

        state = State.TYPE;
        pos = 0;

        this.screenCapture = screenCapture;

//        int size = bitmap.getRowBytes() * bitmap.getHeight();
//        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
//        bitmap.copyPixelsToBuffer(byteBuffer);
//        byteArray = byteBuffer.array();

        Log.i("MobileWebCam", "HTTP - MJPEG: new input stream");
    }

    @Override
    public int available() throws IOException
    {
        throw new UnsupportedOperationException("available() method is not implemented");
    }

    @Override
    public int read() throws IOException
    {
        throw new UnsupportedOperationException("read() method is not implemented");
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException
    {
        int copy = 0;

        if(lastImageIndex == imageIndex)
            return 0;

        switch(state)
        {
            case BOUND:
                //Log.d("MjpegStream", "BOUND");

                copy = Math.min(length, boundaryLine.length() - pos);
                System.arraycopy(boundaryLine.getBytes(), pos, buffer, 0, copy);
                pos += copy;
                if(pos >= boundaryLine.length())
                {
                    pos = 0;
                    state = State.TYPE;
                }
                break;
            case TYPE:
                updateBitmap();
                //Log.d("MjpegStream", "TYPE");
                copy = Math.min(length, contentType.length() - pos);
                System.arraycopy(contentType.getBytes(), pos, buffer, 0, copy);
                pos += copy;
                if(pos >= contentType.length())
                {
                    // TODO: lock image buffer from now on
// TODO: lock image buffer from now on
                    synchronized(imageDataLock)
                    {
                        len = byteArray.length;
                        contentLengthString = String.format(contentLength, len);
                    }

                    state = State.LENGTH;
                    pos = 0;
                }
                break;
            case LENGTH:
                //Log.d("MjpegStream", "LENGTH");
                copy = Math.min(length, contentLengthString.length() - pos);
                System.arraycopy(contentLengthString.getBytes(), pos, buffer, 0, copy);
                pos += copy;
                if(pos >= contentLengthString.length())
                {
                    state = State.JPEG;
                    pos = 0;
                }
                break;
            case JPEG:
                //Log.d("MjpegStream", "JPEG");
                synchronized(imageDataLock)
                {
                    copy = Math.min(length, byteArray.length - pos);

                    //Log.i("MobileWebCam", "HTTP - MJPEG: gImageData " + pos + " of " + MobileWebCamHttpService.gImageData.length);

                    if(copy <= 0)
                    {
                        state = State.BOUND;
                        pos = 0;
                        copy = -1;
                    }
                    else
                    {
                        System.arraycopy(byteArray, pos, buffer, 0, copy);
                        pos += copy;
                        if(pos >= byteArray.length)
                        {
// TODO: unlock image buffer
                            state = State.BOUND;
                            pos = 0;
                            //lastImageIndex = imageIndex;
                        }
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
            Bitmap.Config conf = Bitmap.Config.ARGB_8888; // see other conf types
            bitmap = Bitmap.createBitmap(w, h, conf); // this creates a MUTABLE bitmap
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        byteArray = stream.toByteArray();
    }
}