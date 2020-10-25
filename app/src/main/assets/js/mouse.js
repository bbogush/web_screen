
let mouseDown = false;
let mouseWebSocket = null;
let remoteVideoRect;

function mouseInit(ws) {
    mouseWebSocket = ws;

    remoteVideoRect = document.getElementById('screen');
    remoteVideoRect.addEventListener('mousedown', mouseDownHandler);
    remoteVideoRect.addEventListener('mousemove', mouseMoveHandler);
    remoteVideoRect.addEventListener('mouseup', mouseUpHandler);
    remoteVideoRect.addEventListener('wheel', mouseWheelHandler);
    remoteVideoRect.addEventListener('ondragstart', onDragStart);
}

function mouseUninit() {
    remoteVideoRect.removeEventListener('ondragstart', onDragStart);
    remoteVideoRect.removeEventListener('wheel', mouseWheelHandler);
    remoteVideoRect.removeEventListener('mouseup', mouseUpHandler);
    remoteVideoRect.removeEventListener('mousemove', mouseMoveHandler);
    remoteVideoRect.removeEventListener('mousedown', mouseDownHandler);
    remoteVideoRect = null;
}

function onDragStart() {
    return false;
}

function mouseDownHandler(e) {
    if (!isMouseLeftButtonPressed(e))
        return;
    mouseDown = true;
    mouseHandler(e, 'down');
}

function mouseMoveHandler(e) {
    if (!mouseDown)
        return;
    if (!isMouseLeftButtonPressed(e)) {
        mouseDown = false;
        mouseHandler(e, 'up');
        return;
    }
    mouseHandler(e, 'move');
}

function mouseUpHandler(e) {
    if (!mouseDown)
        return;
    if (isMouseLeftButtonPressed(e))
        return;
    mouseDown = false;
    mouseHandler(e, 'up');
}

function mouseWheelHandler(e) {
    if (!e.ctrlKey)
        return;
    if (e.deltaY > 0)
        mouseHandler(e, 'zoom_out');
    else if (e.deltaY < 0)
        mouseHandler(e, 'zoom_in');
    e.preventDefault();
}

function isMouseLeftButtonPressed(e) {
    let MOUSE_LEFT_BUTTON_NUMBER = 1;

    return e.buttons === undefined ? e.which === MOUSE_LEFT_BUTTON_NUMBER :
        e.buttons === MOUSE_LEFT_BUTTON_NUMBER;
}

function mouseHandler(e, action) {
    let position = getPosition(e);
    let params = '{type=mouse_' + action + ',x=' + position.x + ',y=' + position.y + '}';
    sendMouseMessage(params);
}

function getPosition(e) {
    let rect = e.target.getBoundingClientRect();
    let x = e.clientX - rect.left;
    let y = e.clientY - rect.top;

    x = Math.round(x * e.target.videoWidth * 1.0 / e.target.clientWidth);
    y = Math.round(y * e.target.videoHeight * 1.0 / e.target.clientHeight);

    return {x, y};
}

function backButtonHandler() {
    buttonHandler('back');
}

function homeButtonHandler() {
    buttonHandler('home');
}

function recentButtonHandler() {
    buttonHandler('recent');
}

function powerButtonHandler() {
    buttonHandler('power');
}

function lockButtonHandler() {
    buttonHandler('lock');
}

function buttonHandler(button) {
    sendMouseMessage('{type=button_' + button + '}');
}

function sendMouseMessage(message) {
    if (mouseWebSocket == null)
        return;

    mouseWebSocket.send(message);
}


