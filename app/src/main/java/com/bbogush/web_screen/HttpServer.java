package com.bbogush.web_screen;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import fi.iki.elonen.NanoWSD;

public class HttpServer extends NanoWSD {
    private static final String TAG = HttpServer.class.getSimpleName();

    private static final String HTML_DIR = "html/";
    private static final String INDEX_HTML = "index.html";
    private static final String IMAGE_BACK = "back.svg";
    private static final String IMAGE_HOME = "home.svg";
    private static final String IMAGE_RECENT = "recent.svg";
    private static final String IMAGE_POWER = "power.svg";
    private static final String IMAGE_LOCK = "lock.svg";
    private static final String MIME_IMAGE_SVG = "image/svg+xml";
    private static final String TYPE_PARAM = "type";
    private static final String TYPE_VALUE_MOUSE_UP = "mouse_up";
    private static final String TYPE_VALUE_MOUSE_MOVE = "mouse_move";
    private static final String TYPE_VALUE_MOUSE_DOWN = "mouse_down";
    private static final String TYPE_VALUE_MOUSE_ZOOM_IN = "mouse_zoom_in";
    private static final String TYPE_VALUE_MOUSE_ZOOM_OUT = "mouse_zoom_out";
    private static final String TYPE_VALUE_BUTTON_BACK = "button_back";
    private static final String TYPE_VALUE_BUTTON_HOME = "button_home";
    private static final String TYPE_VALUE_BUTTON_RECENT = "button_recent";
    private static final String TYPE_VALUE_BUTTON_POWER = "button_power";
    private static final String TYPE_VALUE_BUTTON_LOCK = "button_lock";
    private static final String MOUSE_PARAM_X = "x";
    private static final String MOUSE_PARAM_Y = "y";


    private MouseAccessibilityService mouseAccessibilityService;
    private ScreenCapture capture;
    private Context context;

    public HttpServer(ScreenCapture capture, MouseAccessibilityService mouseAccessibilityService,
                      int port, Context context) {
        super(port);
        this.capture = capture;
        this.context = context;
        this.mouseAccessibilityService = mouseAccessibilityService;
    }

    class Ws extends WebSocket {
        private static final int PING_INTERVAL = 20000;
        private Timer pingTimer = new Timer();

        public Ws(IHTTPSession handshakeRequest) {
            super(handshakeRequest);
        }

        @Override
        protected void onOpen() {
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        Ws.this.ping(new byte[0]);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            pingTimer.scheduleAtFixedRate(timerTask, PING_INTERVAL, PING_INTERVAL);
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason,
                               boolean initiatedByRemote) {
            pingTimer.cancel();
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            HashMap<String, String> params = new HashMap<>();
            String dataString = message.getTextPayload();
            List<String> keyValueList = new ArrayList<>(Arrays.asList(dataString.split(",")));
            for (String keyValue : keyValueList) {
                String[] parts = keyValue.split("=", 2);
                if (parts.length == 2)
                    params.put(parts[0], parts[1]);
            }

            handleParameters(params);
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
        }

        @Override
        protected void onException(IOException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        return new Ws(handshake);
    }

    @Override
    protected Response serveHttp(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        return serveRequest(session, uri, method);
    }

    private Response serveRequest(IHTTPSession session, String uri, Method method) {
        if(Method.GET.equals(method))
            return handleGet(session, uri);

        return notFoundResponse();
    }

    private Response notFoundResponse() {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found");
    }

    private Response internalErrorResponse() {
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Internal error");
    }

    private Response handleGet(IHTTPSession session, String uri) {
        if (uri.contentEquals("/")) {
            return handleRootRequest(session);
        } else if (uri.contains(".svg")) {
            return handleImageRequest(session, uri);
        } else if (uri.contentEquals("/mjpeg")) {
            return handleMjpegRequest(session);
        }

        return notFoundResponse();
    }

    private Response handleRootRequest(IHTTPSession session) {
        String indexHtml = readFile(HTML_DIR + INDEX_HTML);

        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, indexHtml);
    }

    private void handleParameters(Map<String, String> parameters) {

        if (mouseAccessibilityService == null)
            return;

        String type = parameters.get(TYPE_PARAM);
        if (type == null)
            return;

        switch (type) {
            case TYPE_VALUE_MOUSE_UP:
            case TYPE_VALUE_MOUSE_MOVE:
            case TYPE_VALUE_MOUSE_DOWN:
            case TYPE_VALUE_MOUSE_ZOOM_IN:
            case TYPE_VALUE_MOUSE_ZOOM_OUT:
                handleMouseParameters(type, parameters);
                break;
            case TYPE_VALUE_BUTTON_BACK:
                mouseAccessibilityService.backButtonClick();
                break;
            case TYPE_VALUE_BUTTON_HOME:
                mouseAccessibilityService.homeButtonClick();
                break;
            case TYPE_VALUE_BUTTON_RECENT:
                mouseAccessibilityService.recentButtonClick();
                break;
            case TYPE_VALUE_BUTTON_POWER:
                mouseAccessibilityService.powerButtonClick();
                break;
            case TYPE_VALUE_BUTTON_LOCK:
                mouseAccessibilityService.lockButtonClick();
                break;
        }
    }

    private void handleMouseParameters(String type, Map<String, String> parameters) {
        String xString = parameters.get(MOUSE_PARAM_X);
        String yString = parameters.get(MOUSE_PARAM_Y);
        if (xString == null || xString.isEmpty())
            return;
        if (yString == null || yString.isEmpty())
            return;

        int x, y;
        try {
            x = Integer.parseInt(xString);
            y = Integer.parseInt(yString);
        } catch (Exception e) {
            return;
        }

        switch (type) {
            case TYPE_VALUE_MOUSE_UP:
                mouseAccessibilityService.mouseUp(x, y);
                break;
            case TYPE_VALUE_MOUSE_MOVE:
                mouseAccessibilityService.mouseMove(x, y);
                break;
            case TYPE_VALUE_MOUSE_DOWN:
                mouseAccessibilityService.mouseDown(x, y);
                break;
            case TYPE_VALUE_MOUSE_ZOOM_IN:
                mouseAccessibilityService.mouseWheelZoomIn(x, y);
                break;
            case TYPE_VALUE_MOUSE_ZOOM_OUT:
                mouseAccessibilityService.mouseWheelZoomOut(x, y);
                break;
        }
    }

    private String readFile(String fileName) {
        InputStream fileStream;
        String string = "";

        try {
            fileStream = context.getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fileStream,
                    "UTF-8"));

            String line;
            while ((line = reader.readLine()) != null)
                string += line;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return string;
    }

    private Response handleImageRequest(IHTTPSession session, String uri) {
        String imageName;
        if (uri.contentEquals("/" + IMAGE_BACK))
            imageName = HTML_DIR + IMAGE_BACK;
        else if (uri.contentEquals("/" + IMAGE_HOME))
            imageName = HTML_DIR + IMAGE_HOME;
        else if (uri.contentEquals("/" + IMAGE_RECENT))
            imageName = HTML_DIR + IMAGE_RECENT;
        else if (uri.contentEquals("/" + IMAGE_LOCK))
            imageName = HTML_DIR + IMAGE_LOCK;
        else if (uri.contentEquals("/" + IMAGE_POWER))
            imageName = HTML_DIR + IMAGE_POWER;
        else
            return notFoundResponse();

        InputStream fileStream;
        try {
            fileStream = context.getAssets().open(imageName);
        } catch (IOException e) {
            e.printStackTrace();
            return internalErrorResponse();
        }

        return newChunkedResponse(Response.Status.OK, MIME_IMAGE_SVG, fileStream);
    }

    private Response handleMjpegRequest(IHTTPSession session) {
        notifyAboutNewConnection(session);

        Response res;
        String mime = "multipart/x-mixed-replace; boundary=" + MjpegStream.boundary;
        res = newChunkedResponse(Response.Status.OK, mime, new MjpegStream(capture));
        res.addHeader("Cache-Control",
                "no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0," +
                        "private");
        res.addHeader("Pragma", "no-cache");
        res.addHeader("Expires", "-1");

        return res;
    }

    private void notifyAboutNewConnection(IHTTPSession session) {
        // The message is used to trigger screen redraw on new connection
        final String remoteAddress = session.getRemoteIpAddress();
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "WebScreen\nNew connection from " + remoteAddress,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setMouseAccessibilityService(MouseAccessibilityService mouseAccessibilityService) {
        this.mouseAccessibilityService = mouseAccessibilityService;
    }

    public MouseAccessibilityService getMouseAccessibilityService() {
        return mouseAccessibilityService;
    }
}
