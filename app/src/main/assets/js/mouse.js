/*
 *  Copyright (c) 2020 The WebScreen project authors. All Rights Reserved.
 */

var mouseDown = false;
var mouseWebSocket = null;

function mouseInit(ws) {
    mouseWebSocket = ws;
    document.getElementById('screen').ondragstart = function() { return false; };
    document.getElementById('screen').addEventListener('mousedown', mouseDownHandler);
    document.getElementById('screen').addEventListener('mousemove', mouseMoveHandler);
    document.getElementById('screen').addEventListener('mouseup', mouseUpHandler);
    document.getElementById('screen').addEventListener('wheel', mouseWheelHandler);
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
    var MOUSE_LEFT_BUTTON_NUMBER = 1;

    return e.buttons === undefined ? e.which === MOUSE_LEFT_BUTTON_NUMBER :
        e.buttons === MOUSE_LEFT_BUTTON_NUMBER;
}

function mouseHandler(e, action) {
    var position = getPosition(e);
    var params = 'type=mouse_' + action + '&x=' + position.x + '&y=' + position.y;
    mouseWebSocket.send(params);
}

function getPosition(e) {
    var rect = e.target.getBoundingClientRect();
    var x = e.clientX - rect.left;
    var y = e.clientY - rect.top;

    x = Math.round(x * e.target.videoWidth * 1.0 / e.target.clientWidth);
    y = Math.round(y * e.target.videoHeight * 1.0 / e.target.clientHeight);

    return {x, y};
}

function backButtonHandler() {
    buttonHandler("back");
}

function homeButtonHandler() {
    buttonHandler("home");
}

function recentButtonHandler() {
    buttonHandler("recent");
}

function powerButtonHandler() {
    buttonHandler("power");
}

function lockButtonHandler() {
    buttonHandler("lock");
}

function buttonHandler(button) {
    mouseWebSocket.send("type=button_" + button);
}


