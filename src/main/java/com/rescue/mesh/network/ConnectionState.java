package com.rescue.mesh.network;

/** Observable state of one configured outbound peer connection. */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RETRY_WAIT,
    FAILED,
    STOPPED
}
