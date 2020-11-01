package com.bbogush.web_screen;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.KeyManagerFactory;

import fi.iki.elonen.NanoWSD;

public class HttpServer extends NanoWSD {
    private static final String TAG = HttpServer.class.getSimpleName();

    private static final String HTML_DIR = "html/";
    private static final String INDEX_HTML = "index.html";
    private static final String MIME_IMAGE_SVG = "image/svg+xml";
    private static final String MIME_JS = "text/javascript";
    private static final String MIME_TEXT_PLAIN_JS = "text/plain";
    private static final String MIME_TEXT_CSS = "text/css";
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
    private static final String TYPE_VALUE_JOIN = "join";
    private static final String TYPE_VALUE_SDP = "sdp";
    private static final String TYPE_VALUE_ICE = "ice";

    private Context context;
    Ws webSocket = null;

    private HttpServer.HttpServerInterface httpServerInterface;

    public HttpServer(int port, Context context,
                      HttpServer.HttpServerInterface httpServerInterface) {
        super(port);
        this.context = context;
        this.httpServerInterface = httpServerInterface;
        configSecurity();
    }

    private void configSecurity() {
        final String keyPassword = "presscott";
        final String certPassword = "presscott";

        try {
            InputStream keyStoreStream = context.getAssets().open("private/keystore.bks");
            KeyStore keyStore = KeyStore.getInstance("BKS");
            keyStore.load(keyStoreStream, keyPassword.toCharArray());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
                    .getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, certPassword.toCharArray());
            makeSecure(makeSSLSocketFactory(keyStore, keyManagerFactory), null);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
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
            Log.d(TAG, "Message from client: " + message.getTextPayload());

            JSONObject json;

            try {
                json = new JSONObject(message.getTextPayload());
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }

            handleRequest(json);
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
        webSocket = new Ws(handshake);
        return webSocket;
    }

    @Override
    protected Response serveHttp(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        return serveRequest(session, uri, method);
    }

    public interface HttpServerInterface {
        void onMouseDown(JSONObject message);
        void onMouseMove(JSONObject message);
        void onMouseUp(JSONObject message);
        void onMouseZoomIn(JSONObject message);
        void onMouseZoomOut(JSONObject message);
        void onButtonBack();
        void onButtonHome();
        void onButtonRecent();
        void onButtonPower();
        void onButtonLock();
        void onJoin(HttpServer server);
        void onSdp(JSONObject message);
        void onIceCandidate(JSONObject message);
    }

    public void send(String message) throws IOException {
        if (webSocket != null)
            webSocket.send(message);
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
        }

        return handleFileRequest(session, uri);
    }

    private Response handleRootRequest(IHTTPSession session) {
        String indexHtml = readFile(HTML_DIR + INDEX_HTML);

        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, indexHtml);
    }

    private void handleRequest(JSONObject json) {
        String type;
        try {
            type = json.getString(TYPE_PARAM);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        switch (type) {
            case TYPE_VALUE_MOUSE_DOWN:
                httpServerInterface.onMouseDown(json);
                break;
            case TYPE_VALUE_MOUSE_MOVE:
                httpServerInterface.onMouseMove(json);
                break;
            case TYPE_VALUE_MOUSE_UP:
                httpServerInterface.onMouseUp(json);
                break;
            case TYPE_VALUE_MOUSE_ZOOM_IN:
                httpServerInterface.onMouseZoomIn(json);
                break;
            case TYPE_VALUE_MOUSE_ZOOM_OUT:
                httpServerInterface.onMouseZoomOut(json);
                break;
            case TYPE_VALUE_BUTTON_BACK:
                httpServerInterface.onButtonBack();
                break;
            case TYPE_VALUE_BUTTON_HOME:
                httpServerInterface.onButtonHome();
                break;
            case TYPE_VALUE_BUTTON_RECENT:
                httpServerInterface.onButtonRecent();
                break;
            case TYPE_VALUE_BUTTON_POWER:
                httpServerInterface.onButtonPower();
                break;
            case TYPE_VALUE_BUTTON_LOCK:
                httpServerInterface.onButtonLock();
                break;
            case TYPE_VALUE_JOIN:
                httpServerInterface.onJoin(this);
                break;
            case TYPE_VALUE_SDP:
                httpServerInterface.onSdp(json);
                break;
            case TYPE_VALUE_ICE:
                httpServerInterface.onIceCandidate(json);
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

    private Response handleFileRequest(IHTTPSession session, String uri) {
        String relativePath = uri.startsWith("/") ? uri.substring(1) : uri;

        InputStream fileStream;
        try {
            fileStream = context.getAssets().open(relativePath);
        } catch (IOException e) {
            e.printStackTrace();
            return notFoundResponse();
        }

        String mime;
        if (uri.contains(".js"))
            mime = MIME_JS;
        else if (uri.contains(".svg"))
            mime = MIME_IMAGE_SVG;
        else if (uri.contains(".css"))
            mime = MIME_TEXT_CSS;
        else
            mime = MIME_TEXT_PLAIN_JS;

        return newChunkedResponse(Response.Status.OK, mime, fileStream);
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
}
