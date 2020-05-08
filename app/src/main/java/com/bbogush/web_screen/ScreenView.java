package com.bbogush.web_screen;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class ScreenView extends View implements View.OnTouchListener{

    public ScreenView(Context context) {
        super(context);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                float mx = event.getX();
                float my = event.getY();

                Log.d("Mouse", "down" + mx + " " + my);
                break;
            case MotionEvent.ACTION_MOVE:
                Log.d("Mouse", "move");
                break;
            case MotionEvent.ACTION_UP:
                Log.d("Mouse", "up");
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //I have a bunch of shapes and text here
    }
}
