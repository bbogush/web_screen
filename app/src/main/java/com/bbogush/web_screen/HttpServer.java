package com.bbogush.web_screen;

import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
    private static final String MIME_JSON = "application/json";
    private MouseAccessibilityService mouseAccessibilityService;
    private MjpegStream stream;

    public HttpServer(MjpegStream stream, int port) {
        super(port);
        mouseAccessibilityService = new MouseAccessibilityService();
        this.stream = stream;
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

    private String response = "<html>" +
            "<script type='text/javascript'>" +
            "window.onload = init;" +
            "function init() {" +
            "if (window.Event) {" +
            "document.captureEvents(Event.CLICK);" +
            "}" +
            "document.onclick = getCursorXY;" +
            "}" +
            "function getCursorXY(e) {" +
            "document.getElementById('cursorX').value = (window.Event) ? e.pageX : event.clientX + (document.documentElement.scrollLeft ? document.documentElement.scrollLeft : document.body.scrollLeft);" +
            "document.getElementById('cursorY').value = (window.Event) ? e.pageY : event.clientY + (document.documentElement.scrollTop ? document.documentElement.scrollTop : document.body.scrollTop);" +
            "var url = window.location.href;" +
            "var params = 'x=' + e.pageX+ '&y=' + e.pageY;" +
            "var http = new XMLHttpRequest();" +
            "http.open('GET', url+'?' + params, true);" +
            "http.onreadystatechange = function() {" +
            "if (http.readyState == 4 && http.status == 200) {"+
            "};" +
            "};" +
            "http.send(null);" +
            "} " +
            "</script>" +
            "<body> " +
            "<input type='text' id='cursorX' size='3'> X-position of the mouse cursor" +
            "<br /><br /> " +
            "<input type='text' id='cursorY' size='3'> Y-position of the mouse cursor " +
            "<p><p><img src=/mjpeg />" +
            "</body> " +
            "</html>";
//    private String response = "<html><script type='text/javascript'></script><body><h1>Test</h1></body></html>";


    @RequiresApi(api = Build.VERSION_CODES.N)
    private Response handleGet(IHTTPSession session, String uri, Map<String, String> parms) {
        if (uri.contentEquals("/")) {
            String x = session.getParms().get("x");
            String y = session.getParms().get("y");
            Log.d("Coord", "x=" + x + "; y=" + y);
            if (x != null && y != null) {
                mouseAccessibilityService.tap(Integer.parseInt(x), Integer.parseInt(y));
            }
            return newFixedLengthResponse(Response.Status.OK, MIME_HTML, response);
        } else if (uri.contentEquals("/mjpeg")) {
            Response res;
            res = newChunkedResponse(Response.Status.OK,
                    "multipart/x-mixed-replace; boundary=my_jpeg", stream);
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
