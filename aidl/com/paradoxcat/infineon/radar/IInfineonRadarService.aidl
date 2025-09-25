package com.paradoxcat.infineon.radar;

import com.paradoxcat.infineon.radar.IRangeDopplerListener;

interface IInfineonRadarService {
    /**
     * Subscribe for stream of range doppler images.
     *
     * @param[in] listener Callback interface to be implemented by the caller
     * @return Unique subscription id to be stored by the client to later unsubscribe
     */
    long subscribe(in IRangeDopplerListener listener);

    /**
     * Unsubscribe from stream of range doppler images.
     *
     * @param subscriptionId Unique subscription id returned by subscribe() call
     */
    void unsubscribe(in long subscriptionId);
}

