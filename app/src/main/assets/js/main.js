
let pc;
let remoteStream;
let turnReady;
let dataWebSocket;
let remoteVideo;

let pcConfig = {
    'iceServers': [{
        'urls': 'stun:stun.l.google.com:19302'
    }]
};

window.onload = init;
window.onbeforeunload = uninit;

function init() {
    console.log('Main: init.');

    varInit();
    webSocketInit();
    turnInit();
}

function uninit() {
    console.log('Main: uninit');

    webSocketUninit();
    varUninit();
}

function varInit() {
    remoteVideo = document.querySelector('#screen');
}

function varUninit() {
    remoteStream = null;
}

function webSocketInit() {
    console.log('WebSocket: init.');

    dataWebSocket = new WebSocket('wss://' + window.location.host);
    dataWebSocket.onopen = onWsOpen;
    dataWebSocket.onclose = onWsClose;
    dataWebSocket.onerror = onWsError;
    dataWebSocket.onmessage = onWsMessage;
}

function webSocketUninit() {
    console.log('WebSocket: uninit.');

    sendMessage('{type:bye}');
    dataWebSocket.close();
    dataWebSocket = null;
}

function onWsOpen(event) {
    console.log("WebSocket: opened");

    sendMessage('{type:join}');

    mouseInit(dataWebSocket);
}

function onWsClose(event) {
    console.log('WebSocket: closed.');

    mouseUninit();
    destroyPeerConnection();
}

function onWsError(error) {
    console.log("WebSocket: error: " + error.message);
}

function onWsMessage(event) {
    console.log('WebSocket: received message:', event);

    let message = JSON.parse(event.data);
    if (message.type === 'sdp')
        handleSdpMessage(message);
    else if (message.type === 'ice')
        handleIceMessage(message);
    else if (message.type === 'bye')
        handleRemoteHangup();
}

function handleSdpMessage(message) {
    if (message.sdp.type === 'offer') {
        createPeerConnection();
        pc.setRemoteDescription(new RTCSessionDescription(message.sdp));
        doAnswer();
    }
}

function createPeerConnection() {
    console.log('WebRTC: create RTCPeerConnnection.');

    try {
        pc = new RTCPeerConnection(null);
        pc.onicecandidate = handleIceCandidate;
        pc.onaddstream = handleRemoteStreamAdded;
        pc.onremovestream = handleRemoteStreamRemoved;
    } catch (e) {
        console.log('WebRTC: Failed to create PeerConnection, exception: ' + e.message);
        return;
    }
}

function destroyPeerConnection() {
    console.log('WebRTC: destroy RTCPeerConnnection.');

    if (pc == null)
        return;
    pc.close();
    pc = null;
}

function doAnswer() {
    console.log('WebRTC: create answer.');

    pc.createAnswer().then(
        setLocalAndSendMessage,
        onCreateSessionDescriptionError
    );
}

function setLocalAndSendMessage(sessionDescription) {
    pc.setLocalDescription(sessionDescription);
    sendSdpMessage(sessionDescription);
}

function sendSdpMessage(message) {
    console.log('WebSocket: client sending message: ', message);

    sendMessage('{type=sdp,sdp=' + JSON.stringify(message) + '}');
}

function onCreateSessionDescriptionError(error) {
    console.log('WebRTC: failed to create session description: ' + error.toString());
}

function handleIceMessage(message) {
    if (message.ice.type === 'candidate') {
        let candidate = new RTCIceCandidate({
            sdpMLineIndex: message.ice.label,
            candidate: message.ice.candidate
        });
        pc.addIceCandidate(candidate).then(onAddIceCandidateSuccess, onAddIceCandidateError);
    }
}

function onAddIceCandidateSuccess() {
    console.log('WebRTC: Ice candidate successfully added.');
}

function onAddIceCandidateError(error) {
    console.log('WebRTC: failed to add ice candidate: ' + error.toString());
}

function sendIceMessage(message) {
    console.log('WebSocket: client sending message: ', message);

    sendMessage('{type=ice,ice=' + JSON.stringify(message) + '}')
}

function handleIceCandidate(event) {
    console.log('WebRTC: icecandidate event: ', event);

    if (event.candidate) {
        sendIceMessage({
            type: 'candidate',
            label: event.candidate.sdpMLineIndex,
            id: event.candidate.sdpMid,
            candidate: event.candidate.candidate
        });
    } else {
        console.log('WebRTC: end of candidates.');
    }
}

function turnInit() {
    if (location.hostname !== 'localhost') {
        requestTurn(
            'https://computeengineondemand.appspot.com/turn?username=41784574&key=4080218913'
        );
    }
}

function requestTurn(turnURL) {
    let turnExists = false;
    for (let i in pcConfig.iceServers) {
        if (pcConfig.iceServers[i].urls.substr(0, 5) === 'turn:') {
            turnExists = true;
            turnReady = true;
            break;
        }
    }
    if (!turnExists) {
        console.log('WebRTC: getting TURN server from ', turnURL);
        // No TURN server. Get one from computeengineondemand.appspot.com:
        let xhr = new XMLHttpRequest();
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4 && xhr.status === 200) {
                let turnServer = JSON.parse(xhr.responseText);
                console.log('Got TURN server: ', turnServer);
                pcConfig.iceServers.push({
                    'urls': 'turn:' + turnServer.username + '@' + turnServer.turn,
                    'credential': turnServer.password
                });
                turnReady = true;
            }
        };
        xhr.open('GET', turnURL, true);
        xhr.send();
    }
}

function handleRemoteStreamAdded(event) {
    console.log('WebRTC: remote stream added.');
    remoteStream = event.stream;
    remoteVideo.srcObject = remoteStream;
}

function handleRemoteStreamRemoved(event) {
    console.log('WebRTC: Remote stream removed. Event: ', event);
}

function handleRemoteHangup() {
    console.log('WebSocket: session terminated by remote party.');
    destroyPeerConnection();
}

function sendMessage(message) {
    if (dataWebSocket == null)
        return;

    console.log('WebSocket: client sending message: ', message);
    dataWebSocket.send(message);
}
