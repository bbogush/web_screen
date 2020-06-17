package com.bbogush.web_screen;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
    private static final String INDEX_HTML = "html/index.html";
    private static final String MOUSE_PARAM = "mouse";
    private static final String MOUSE_PARAM_VALUE_DOWN = "down";
    private static final String MOUSE_PARAM_VALUE_MOVE = "move";
    private static final String MOUSE_PARAM_VALUE_UP = "up";
    private static final String MOUSE_PARAM_VALUE_ZOOM_IN = "zoomIn";
    private static final String MOUSE_PARAM_VALUE_ZOOM_OUT = "zoomOut";
    private static final String MOUSE_PARAM_X = "x";
    private static final String MOUSE_PARAM_Y = "y";
    private static final String BUTTON_PARAM = "button";
    private static final String BUTTON_PARAM_VALUE_BACK = "back";
    private static final String BUTTON_PARAM_VALUE_HOME = "home";
    private static final String BUTTON_PARAM_VALUE_RECENT = "recent";
    private static final String BUTTON_PARAM_VALUE_POWER = "power";

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

    @Override
    public Response serve(IHTTPSession session) {
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

    private Response handleGet(IHTTPSession session, String uri) {
        if (uri.contentEquals("/")) {
            return handleRootRequest(session);
        } else if (uri.contentEquals("/mjpeg")) {
            return handleMjpegRequest();
        }

        return notFoundResponse();
    }

    private Response handleRootRequest(IHTTPSession session) {
        String indexHtml = readFile(INDEX_HTML);
        Map<String, List<String>> parameters = session.getParameters();

        if (parameters != null)
            handleParameters(parameters);

        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, indexHtml);
    }

    private void handleParameters(Map<String, List<String>> parameters) {

        if (mouseAccessibilityService == null)
            return;

        handleMouseParameters(parameters);
        handleButtonParameters(parameters);
    }

    private void handleMouseParameters(Map<String, List<String>> parameters) {
        List<String> listAction = parameters.get(MOUSE_PARAM);
        if (listAction == null || listAction.isEmpty())
            return;

        List<String> listX = parameters.get(MOUSE_PARAM_X);
        if (listX == null || listX.isEmpty())
            return;

        List<String> listY = parameters.get(MOUSE_PARAM_Y);
        if (listY == null || listY.isEmpty())
            return;

        String actionString = listAction.get(0);
        String xString = listX.get(0);
        String yString = listY.get(0);

        if (actionString == null || actionString.isEmpty())
            return;
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

        if (actionString.contentEquals(MOUSE_PARAM_VALUE_DOWN))
            mouseAccessibilityService.mouseDown(x, y);
        else if (actionString.contentEquals(MOUSE_PARAM_VALUE_MOVE))
            mouseAccessibilityService.mouseMove(x, y);
        else if (actionString.contentEquals(MOUSE_PARAM_VALUE_UP))
            mouseAccessibilityService.mouseUp(x, y);
        else if (actionString.contentEquals(MOUSE_PARAM_VALUE_ZOOM_IN))
            mouseAccessibilityService.mouseWheelZoomIn(x, y);
        else if (actionString.contentEquals(MOUSE_PARAM_VALUE_ZOOM_OUT))
            mouseAccessibilityService.mouseWheelZoomOut(x, y);
    }

    private void handleButtonParameters(Map<String, List<String>> parameters) {
        List<String> listButton = parameters.get(BUTTON_PARAM);
        if (listButton == null || listButton.isEmpty())
            return;

        String button = listButton.get(0);
        if (button == null)
            return;

        if (button.contentEquals(BUTTON_PARAM_VALUE_BACK))
            mouseAccessibilityService.backButtonClick();
        else if (button.contentEquals(BUTTON_PARAM_VALUE_HOME))
            mouseAccessibilityService.homeButtonClick();
        else if (button.contentEquals(BUTTON_PARAM_VALUE_RECENT))
            mouseAccessibilityService.recentButtonClick();
        else if (button.contentEquals(BUTTON_PARAM_VALUE_POWER))
            mouseAccessibilityService.powerButtonClick();
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

    private Response handleMjpegRequest() {
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

    public void setMouseAccessibilityService(MouseAccessibilityService mouseAccessibilityService) {
        this.mouseAccessibilityService = mouseAccessibilityService;
    }

    public MouseAccessibilityService getMouseAccessibilityService() {
        return mouseAccessibilityService;
    }
}
