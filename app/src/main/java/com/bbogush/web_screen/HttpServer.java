package com.bbogush.web_screen;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
    private static final String MIME_JSON = "application/json";
    private static final String INDEX_HTML = "html/index.html";
    private MouseAccessibilityService mouseAccessibilityService;
    private MjpegStream stream;
    private Context context;

    public HttpServer(MjpegStream stream, int port, Context context) {
        super(port);
        mouseAccessibilityService = new MouseAccessibilityService();
        this.stream = stream;
        this.context = context;
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            Method method = session.getMethod();
            String uri = session.getUri();
            Map<String, String> parms = session.getParms();
            return serveRequest(session, uri, method, parms);
            //newFixedLengthResponse(Response.Status.OK, /*MIME_JSON*/MIME_HTML, responseString);
        } catch (IOException ioe) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                    "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
        } catch (ResponseException re) {
            return newFixedLengthResponse(re.getStatus(), MIME_PLAINTEXT, re.getMessage());
        } catch (NotFoundException nfe) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                    "Not Found");
        } catch (Exception ex) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML,
                    "<html><body><h1>Error</h1>" + ex.toString() + "</body></html>");
        }
    }

    private Response serveRequest(IHTTPSession session, String uri, Method method,
                              Map<String, String> parms)  throws IOException, ResponseException {

        if(Method.GET.equals(method)) {
            return handleGet(session, uri, parms);
        }

        //throw new Resources.NotFoundException();

        return newFixedLengthResponse(Response.Status.OK, /*MIME_JSON*/MIME_HTML, "");
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

    @RequiresApi(api = Build.VERSION_CODES.N)
    private Response handleGet(IHTTPSession session, String uri, Map<String, String> parms) {
        String indexHtml = readFile(INDEX_HTML);

        if (uri.contentEquals("/")) {
            String x = session.getParms().get("x");
            String y = session.getParms().get("y");
            Log.d("Coord", "x=" + x + "; y=" + y);
            if (x != null && y != null) {
                mouseAccessibilityService.tap(Integer.parseInt(x), Integer.parseInt(y));
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_HTML, indexHtml);
        } else if (uri.contentEquals("/mjpeg")) {
            Response res;
            String mime = "multipart/x-mixed-replace; boundary=" + MjpegStream.boundary;
            res = newChunkedResponse(Response.Status.OK, mime, stream);
            res.addHeader("Cache-Control", "no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0");
            res.addHeader("Cache-Control", "private");
            res.addHeader("Pragma", "no-cache");
            res.addHeader("Expires", "-1");
            return res;
        }

        return newFixedLengthResponse(Response.Status.OK, /*MIME_JSON*/MIME_HTML, "");
    }

    private class NotFoundException extends RuntimeException {
    }

}
