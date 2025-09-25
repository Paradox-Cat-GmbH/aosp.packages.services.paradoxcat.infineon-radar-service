package com.paradoxcat.infineon.radar

import android.content.Context
import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import vendor.infineon.radar.FrameData
import vendor.infineon.radar.IRadarSdk
import vendor.infineon.radar.IRawDataListener
import vendor.infineon.radar.SensorConfig

private const val HAL_SERVICE_NAME = "vendor.infineon.radar.IRadarSdk/default"
private const val TAG = "InfineonRadarHalManager"

/**
 * Consider this class to be a Manager to Inifineon Radar HAL AIDL.
 * It relieves you from binding to HAL and exposes nice koltin API.
 */
public class InfineonRadarHalManager {
    fun rawDataFlow(): Flow<FrameData> = callbackFlow {
        Log.d(TAG, "rawDataFlow: starting")
        // TODO let users provide config
        val config = SensorConfig()
        config.sample_rate_Hz = 2000000;
        config.rx_mask = 7; // antennas RX1, RX2, RX3
        config.tx_mask = 1; // antenna TX1
        config.tx_power_level = 31;
        config.if_gain_dB = 30;
        config.start_frequency_Hz = 58500000000;
        config.end_frequency_Hz = 62500000000;
        config.num_samples_per_chirp = numSamplesPerChirp;
        config.num_chirps_per_frame = numChirps;
        config.chirp_repetition_time_s = 2.997875E-4F;
        config.frame_repetition_time_s = 0.0300446F; // ~33 FPS
        config.hp_cutoff_Hz = 80000;
        config.aaf_cutoff_Hz = 500000;
        config.mimo_mode = 0; // IFX_MIMO_OFF
        val listener = object : IRawDataListener.Stub() {
            override fun onFrameReceived(frame: FrameData) {
                trySend(frame)
            }
            override fun getInterfaceVersion() = IRawDataListener.VERSION // see https://source.android.com/docs/core/architecture/aidl/stable-aidl
            override fun getInterfaceHash() = IRawDataListener.HASH // see https://source.android.com/docs/core/architecture/aidl/stable-aidl
        }
        bindToHalService()
        var subscriptionId = mRadarHal?.subscribe(listener, config)
        while (subscriptionId != null && subscriptionId < 0) {
            Log.w(TAG, "Failed to subscribe, retrying in 10 seconds...")
            delay(10_000)
            subscriptionId = mRadarHal?.subscribe(listener, config)
        }
        Log.i(TAG, "Subscribed to InfineonRadarHal with id $subscriptionId")
        awaitClose {
            if (subscriptionId != null && subscriptionId != -1L) {
                Log.i(TAG, "Unsubscribing from InfineonRadarHal with id $subscriptionId")
                mRadarHal?.unsubscribe(subscriptionId)
            }
        }
    }

    fun unsubscribeAll() {
        bindToHalService()
        mRadarHal?.unsubscribeAll() ?: Log.e(TAG, "error: service not bound")
    }

    private fun bindToHalService() {
        Log.d(TAG, "bindToHalService()")
        if (mRadarHal != null) {
            Log.d(TAG, "bindToHalService: already bound")
            return
        }
        // TODO try to get rid of system dependency by using bindToService() instead
        mRadarHal = IRadarSdk.Stub.asInterface(ServiceManager.getService(HAL_SERVICE_NAME))
        if (mRadarHal == null) {
            Log.e(TAG, "There is no $HAL_SERVICE_NAME service running on this system")
        }
    }
    
    private var mRadarHal: IRadarSdk? = null
}