package com.bbogush.web_screen;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class MjpegStream extends InputStream
{
    enum State { BOUND, TYPE, LENGTH, JPEG };

    public static final String mBoundary = "my_jpeg";

    private static final String mContentType = "Content-type: image/jpeg\n";
    private static final String mContentLength = "Content-Length: %d\n\n";
    public static final String mNext = "\n--" + mBoundary + "\n";
    public static AtomicBoolean gImageDataLock = new AtomicBoolean(false);

    private ScreenCapture screenCapture;

    private State mState;
    int len = 0;
    private String mLength;
    int mPos;

    private byte[] byteArray;

    private int mLastImageIdx = -1;
    private static int gImageIndex = 0;

    public MjpegStream(ScreenCapture screenCapture)
    {
        super();

        mState = State.TYPE;
        mPos = 0;

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
        switch(mState)
        {
            case TYPE:
                if(mLastImageIdx == gImageIndex)
                    return 0;
                Log.d("MjpegStream", "type_len=" + (mContentType.length() - mPos));
                return mContentType.length() - mPos;
            case LENGTH:
                Log.d("MjpegStream", "len_len=" + (mLength.length() - mPos));
                return mLength.length() - mPos;
            case JPEG:
                Log.d("MjpegStream", "barr_len=" + (byteArray.length - mPos));
                return byteArray.length - mPos;
            case BOUND:
                Log.d("MjpegStream", "next_len=" + (mNext.length() - mPos));
                return mNext.length() - mPos;
        }

        return 0;
    }

    @Override
    public int read() throws IOException
    {
        int res = 0;

        if(mLastImageIdx == gImageIndex)
            return 0;

        switch(mState)
        {
            case BOUND:
                res = mNext.charAt(mPos++);
                if(mPos >= mNext.length())
                {
                    mState = State.TYPE;
                    mPos = 0;
                }
                break;
            case TYPE:
                res = mContentType.charAt(mPos++);
                if(mPos >= mContentType.length())
                {
                    //Log.i("MobileWebCam", "HTTP - MJPEG: next image (" + MobileWebCamHttpService.gImageIndex + ")");

// TODO: lock image buffer from now on
                    synchronized(gImageDataLock)
                    {
                        len = byteArray.length;
                        mLength = String.format(mContentLength, len);
                    }

                    mLastImageIdx = gImageIndex;

                    mState = State.LENGTH;
                    mPos = 0;
                }
                break;
            case LENGTH:
                res = mLength.charAt(mPos++);
                if(mPos >= mLength.length())
                {
                    mState = State.JPEG;
                    mPos = 0;
                }
                break;
            case JPEG:
                synchronized(gImageDataLock)
                {
                    //Log.i("MobileWebCam", "HTTP - MJPEG: gImageData " + mPos + " of " + MobileWebCamHttpService.gImageData.length);
                    res = byteArray[mPos++];
                    if(mPos >= byteArray.length)
                    {
// TODO: unlock image buffer
                        mState = State.BOUND;
                        mPos = 0;
                    }
                }
                break;
        }
        return res;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException
    {
        int copy = 0;

        if(mLastImageIdx == gImageIndex)
            return 0;

        switch(mState)
        {
            case BOUND:
                //Log.d("MjpegStream", "BOUND");

                copy = Math.min(length, mNext.length() - mPos);
                System.arraycopy(mNext.getBytes(), mPos, buffer, 0, copy);
                mPos += copy;
                if(mPos >= mNext.length())
                {
                    mPos = 0;
                    mState = State.TYPE;
                }
                break;
            case TYPE:
                updateBitmap();
                //Log.d("MjpegStream", "TYPE");
                copy = Math.min(length, mContentType.length() - mPos);
                System.arraycopy(mContentType.getBytes(), mPos, buffer, 0, copy);
                mPos += copy;
                if(mPos >= mContentType.length())
                {
                    // TODO: lock image buffer from now on
// TODO: lock image buffer from now on
                    synchronized(gImageDataLock)
                    {
                        len = byteArray.length;
                        mLength = String.format(mContentLength, len);
                    }

                    mState = State.LENGTH;
                    mPos = 0;
                }
                break;
            case LENGTH:
                //Log.d("MjpegStream", "LENGTH");
                copy = Math.min(length, mLength.length() - mPos);
                System.arraycopy(mLength.getBytes(), mPos, buffer, 0, copy);
                mPos += copy;
                if(mPos >= mLength.length())
                {
                    mState = State.JPEG;
                    mPos = 0;
                }
                break;
            case JPEG:
                //Log.d("MjpegStream", "JPEG");
                synchronized(gImageDataLock)
                {
                    copy = Math.min(length, byteArray.length - mPos);

                    //Log.i("MobileWebCam", "HTTP - MJPEG: gImageData " + mPos + " of " + MobileWebCamHttpService.gImageData.length);

                    if(copy <= 0)
                    {
                        mState = State.BOUND;
                        mPos = 0;
                        copy = -1;
                    }
                    else
                    {
                        System.arraycopy(byteArray, mPos, buffer, 0, copy);
                        mPos += copy;
                        if(mPos >= byteArray.length)
                        {
// TODO: unlock image buffer
                            mState = State.BOUND;
                            mPos = 0;
                            //mLastImageIdx = gImageIndex;
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