// ILocationReceiverCallback.aidl
package com.example.common_aidl_interfaces;

oneway interface ILocationReceiverCallback {
        /**
         * Called when new location data is available.
         * 'oneway' keyword means calls to this are non-blocking (asynchronous).
         */
        void onNewLocationData(double latitude, double longitude, long timestamp, float accuracy);
}