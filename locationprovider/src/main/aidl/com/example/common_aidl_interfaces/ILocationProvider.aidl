// ILocationProvider.aidl
package com.example.common_aidl_interfaces;

import com.example.common_aidl_interfaces.ILocationReceiverCallback;

interface ILocationProvider {
        /**
         * Registers a callback for the client to receive updates.
         */
        void registerCallback(ILocationReceiverCallback callback);

        /**
         * Unregisters a callback.
         */
        void unregisterCallback(ILocationReceiverCallback callback);

        /**
         * A simple method the client can call (optional for this example).
         */
        String getServiceMessage();
    }