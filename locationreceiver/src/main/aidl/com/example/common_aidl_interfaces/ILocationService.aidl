// ILocationService.aidl
package com.example.common_aidl_interfaces;

import com.example.common_aidl_interfaces.IMyCallback;

interface ILocationService {
        /**
         * Registers a callback for the client to receive updates.
         */
        void registerCallback(IMyCallback callback);

        /**
         * Unregisters a callback.
         */
        void unregisterCallback(IMyCallback callback);

        /**
         * A simple method the client can call (optional for this example).
         */
        String getServiceMessage();
    }