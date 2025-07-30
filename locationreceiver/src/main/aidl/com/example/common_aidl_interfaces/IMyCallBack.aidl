// IMyCallBack.aidl
package com.example.common_aidl_interfaces;

oneway interface IMyCallback {
    /**
     * Called when new data is available.
     * 'oneway' keyword means calls to this are non-blocking (asynchronous).
     * The service will not wait for the client to finish processing.
     */
    void onDataReceived(String data);
}