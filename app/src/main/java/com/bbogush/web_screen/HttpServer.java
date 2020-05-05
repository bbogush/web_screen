package com.bbogush.web_screen;

import android.content.res.Resources;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
    private static final String MIME_JSON = "application/json";

    public HttpServer(int port) {
        super(port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            Method method = session.getMethod();
            String uri = session.getUri();
            Map<String, String> parms = session.getParms();
            String responseString = serveClock(session, uri, method, parms);
            return newFixedLengthResponse(Response.Status.OK, /*MIME_JSON*/MIME_HTML, responseString);
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

    private String serveClock(IHTTPSession session, String uri, Method method,
                              Map<String, String> parms)  throws IOException, ResponseException {
        String responseString = "";
        do {
            if(Method.GET.equals(method)) {
                responseString = handleGet(session, parms);
                break;
            }

            if(Method.POST.equals(method)) {
                responseString = handlePost(session);
                break;
            }

            throw new Resources.NotFoundException();

        } while(false);

        return responseString;
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
            "</body> " +
            "</html>";
//    private String response = "<html><script type='text/javascript'></script><body><h1>Test</h1></body></html>";


    private String handleGet(IHTTPSession session, Map<String, String> parms) {
        //return server.handleRequest("{'name':'status', 'value':''}");
        String x = session.getParms().get("x");
        String y = session.getParms().get("y");
        Log.d("Coord", "x="+ x + "; y=" + y);
        return response;
    }

    private String handlePost(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> files = new HashMap<String, String>();
        session.parseBody(files);

        //return server.handleRequest(files.get("postData"));
        return "";
    }


    private class NotFoundException extends RuntimeException {
    }

}
