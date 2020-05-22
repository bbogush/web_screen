package com.bbogush.web_screen;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
    private static final String INDEX_HTML = "html/index.html";
    private MouseAccessibilityService mouseAccessibilityService;
    private ScreenCapture capture;
    private Context context;

    public HttpServer(ScreenCapture capture, int port, Context context) {
        super(port);
        mouseAccessibilityService = new MouseAccessibilityService();
        this.capture = capture;
        this.context = context;
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
        List<String> listX = session.getParameters().get("x");
        List<String> listY = session.getParameters().get("y");
        String x = listX == null ? null : (listX.isEmpty() ? null : listX.get(0));
        String y = listY == null ? null : (listY.isEmpty() ? null : listY.get(0));

        Log.d("Coord", "x=" + x + "; y=" + y);
        if (x != null && y != null) {
            mouseAccessibilityService.tap(Integer.parseInt(x), Integer.parseInt(y));
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, indexHtml);
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
                "no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0");
        res.addHeader("Cache-Control", "private");
        res.addHeader("Pragma", "no-cache");
        res.addHeader("Expires", "-1");

        return res;
    }
}
