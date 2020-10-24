package com.bbogush.web_screen;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

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
    private static final String JS_DIR = "js/";
    private static final String INDEX_HTML = "index.html";
    private static final String MAIN_JS = "main.js";
    private static final String IMAGE_BACK = "back.svg";
    private static final String IMAGE_HOME = "home.svg";
    private static final String IMAGE_RECENT = "recent.svg";
    private static final String IMAGE_POWER = "power.svg";
    private static final String IMAGE_LOCK = "lock.svg";
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
    private static final String MOUSE_PARAM_X = "x";
    private static final String MOUSE_PARAM_Y = "y";
    private static final String SDP_PARAM = "sdp";
    private static final String ICE_PARAM = "ice";


    private MouseAccessibilityService mouseAccessibilityService;
    private ScreenCapture capture;
    private Context context;
    private Intent permissionIntent;
    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    PeerConnection localPeer;
    VideoCapturer videoCapturer;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    MediaConstraints sdpConstraints;
    Ws webSocket;

    List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();

    public HttpServer(ScreenCapture capture, MouseAccessibilityService mouseAccessibilityService,
                      int port, Context context, VideoCapturer vc) {
        super(port);
        this.capture = capture;
        this.context = context;
        this.mouseAccessibilityService = mouseAccessibilityService;
        //this.permissionIntent = permissionIntent;
        this.videoCapturer = vc;
        initWebRTC();
        createPeerConnection();
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

            HashMap<String, String> params = new HashMap<>();
            String dataString = message.getTextPayload();
            List<String> keyValueList = new ArrayList<>(Arrays.asList(dataString.split("&")));
            for (String keyValue : keyValueList) {
                String[] parts = keyValue.split("=", 2);
                if (parts.length == 2)
                    params.put(parts[0], parts[1]);
            }

            handleParameters(this, params);
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
        } else if (uri.contentEquals("/mjpeg")) {
            return handleMjpegRequest(session);
        }

        return handleFileRequest(session, uri);
    }

    private Response handleRootRequest(IHTTPSession session) {
        String indexHtml = readFile(HTML_DIR + INDEX_HTML);

        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, indexHtml);
    }

    private void handleParameters(Ws ws, Map<String, String> parameters) {
        String type = parameters.get(TYPE_PARAM);
        if (type == null)
            return;

        switch (type) {
            case TYPE_VALUE_MOUSE_UP:
            case TYPE_VALUE_MOUSE_MOVE:
            case TYPE_VALUE_MOUSE_DOWN:
            case TYPE_VALUE_MOUSE_ZOOM_IN:
            case TYPE_VALUE_MOUSE_ZOOM_OUT:
                if (mouseAccessibilityService == null)
                    return;
                handleMouseParameters(type, parameters);
                break;
            case TYPE_VALUE_BUTTON_BACK:
                if (mouseAccessibilityService == null)
                    return;
                mouseAccessibilityService.backButtonClick();
                break;
            case TYPE_VALUE_BUTTON_HOME:
                if (mouseAccessibilityService == null)
                    return;
                mouseAccessibilityService.homeButtonClick();
                break;
            case TYPE_VALUE_BUTTON_RECENT:
                if (mouseAccessibilityService == null)
                    return;
                mouseAccessibilityService.recentButtonClick();
                break;
            case TYPE_VALUE_BUTTON_POWER:
                if (mouseAccessibilityService == null)
                    return;
                mouseAccessibilityService.powerButtonClick();
                break;
            case TYPE_VALUE_BUTTON_LOCK:
                if (mouseAccessibilityService == null)
                    return;
                mouseAccessibilityService.lockButtonClick();
                break;
            case TYPE_VALUE_SDP:
                String data = parameters.get(SDP_PARAM);
                if (data == null)
                    break;
                onAnswerReceived(data);
                //onOffer(ws, data);
                break;
            case TYPE_VALUE_JOIN:
                onTryToStart(ws);
                break;
            case TYPE_VALUE_ICE:
                String iceData = parameters.get(ICE_PARAM);
                if (iceData == null)
                    break;
                onIceCandidateReceived(iceData);
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

    private EglBase rootEglBase;
    private PeerConnectionFactory peerConnectionFactory;

    private void initWebRTC() {
        rootEglBase = EglBase.create();

        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */false,  /*
                enableH264HighProfile */true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();

        //XXX enable camera for test
        //videoCapturer = createCameraCapturer(new Camera1Enumerator(false));

        audioConstraints = new MediaConstraints();
        videoConstraints = new MediaConstraints();

        SurfaceTextureHelper surfaceTextureHelper;
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
        VideoSource videoSource =
                peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(surfaceTextureHelper, context, videoSource.getCapturerObserver());

        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        display.getRealMetrics(metrics);
        if (videoCapturer != null) {
            videoCapturer.startCapture(metrics.widthPixels, metrics.heightPixels, 30);
        }
    }

    public void onTryToStart(Ws ws) {
        Log.d(TAG, new Object(){}.getClass().getEnclosingMethod().getName());
        createPeerConnection();
        doCall(ws);
    }

    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig,
                new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                onIceCandidateReceived(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                //showToast("Received Remote stream");
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }
        });

        addStreamToLocalPeer();
    }

    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        Log.d(TAG, new Object(){}.getClass().getEnclosingMethod().getName());
        try {
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);

            webSocket.send(object.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
//        JSONObject obj = new JSONObject();
//        try {
//            obj.put("type", iceCandidate.);
//            obj.put("sdp", iceCandidate.description);
//        } catch (JSONException e) {
//            e.printStackTrace();
//            return;
//        }
//        String jsonStr = obj.toString();
//        webSocket.send(jsonStr);
        //we have received ice candidate. We can set it to the other peer.
        //XXX SignallingClient.getInstance().emitIceCandidate(iceCandidate);
    }

    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
//        final VideoTrack videoTrack = stream.videoTracks.get(0);
//        runOnUiThread(() -> {
//            try {
//                remoteVideoView.setVisibility(View.VISIBLE);
//                videoTrack.addSink(remoteVideoView);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
    }

    private void addStreamToLocalPeer() {
        //creating local mediastream
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);
    }

    private void doCall(Ws ws) {
        Log.d(TAG, new Object(){}.getClass().getEnclosingMethod().getName());
        sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                Log.d("onCreateSuccess", "SignallingClient emit ");

                JSONObject obj = new JSONObject();
                try {
                    obj.put("type", sessionDescription.type.canonicalForm());
                    obj.put("sdp", sessionDescription.description);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                String jsonStr = obj.toString();

                try {
                    ws.send(jsonStr);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
                Log.d(TAG, jsonStr);
            }
        }, sdpConstraints);
    }

    private void onOffer(Ws ws, String data) {
        Log.d(TAG, "Offer: SDP=" + data);

        String sdp;
        try {
            JSONObject json = new JSONObject(data);
            sdp = json.getString("sdp");
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"),
                new SessionDescription(SessionDescription.Type.OFFER, sdp));
        doAnswer(ws);
    }

    public void onAnswerReceived(String data) {
        Log.d(TAG, new Object(){}.getClass().getEnclosingMethod().getName());
        //showToast("Received Answer");

        JSONObject json;
        try {
            json = new JSONObject(data);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        try {
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"),
                    new SessionDescription(SessionDescription.Type.fromCanonicalForm(json.getString(
                            "type").toLowerCase()), json.getString("sdp")));
            //updateVideoViews(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void doAnswer(Ws ws) {
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"),
                        sessionDescription);
                //SignallingClient.getInstance().emitMessage(sessionDescription);
                JSONObject obj = new JSONObject();
                try {
                    obj.put("type", sessionDescription.type.canonicalForm());
                    obj.put("sdp", sessionDescription.description);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                String jsonStr = obj.toString();

                try {
                    ws.send(jsonStr);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
                Log.d(TAG, jsonStr);
            }
        }, new MediaConstraints());
    }

    public void onIceCandidateReceived(String data) {
        Log.d(TAG, new Object(){}.getClass().getEnclosingMethod().getName());

        JSONObject json;
        try {
            json = new JSONObject(data);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        try {
            localPeer.addIceCandidate(new IceCandidate(json.getString("id"), json.getInt("label"),
                    json.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        Log.d(TAG, new Object(){}.getClass().getEnclosingMethod().getName());
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

}
