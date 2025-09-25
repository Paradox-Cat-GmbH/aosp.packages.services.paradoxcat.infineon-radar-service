package com.paradoxcat.infineon.radar

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter

private const val TAG = "InfineonRadarService"

/**
 * Bound service which uses vendor.infineon.radar HAL to get raw data from sensor,
 * processes the data, and outputs stream of pictures.
 */
public class InfineonRadarService : LifecycleService() {
    private var mRadarServiceImpl: InfineonRadarServiceImpl? = null

    override fun onCreate() {
        Log.d(TAG, "InfineonRadarService onCreate()")
        super.onCreate()
        mRadarServiceImpl = InfineonRadarServiceImpl()
        startGettingPictures()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return mRadarServiceImpl
    }

    override fun onDestroy() {
        radarHal.unsubscribeAll()
        super.onDestroy()
    }

    private class InfineonRadarServiceImpl : IInfineonRadarService.Stub() {
        private val subscribers = mutableMapOf<Long, IRangeDopplerListener>()
        private var nextSubscriptionId = 1L

        override fun subscribe(listener: IRangeDopplerListener): Long {
            val subscriptionId = nextSubscriptionId++
            subscribers[subscriptionId] = listener
            return subscriptionId
        }

        override fun unsubscribe(subscriptionId: Long): Unit {
            subscribers.remove(subscriptionId)
        }

        fun notifyListeners(image: RangeDopplerImage) {
            subscribers.values.forEach { listener ->
                try {
                    listener.onFrameReceived(image)
                } catch (e: RemoteException) {
                    // log error but continue with other listeners
                    Log.e(TAG, "Error notifying listener", e)
                }
            }
        }
    }

    private fun startGettingPictures() {
        Log.d(TAG, "startGettingPictures()")
        // get rid of older subscribers just in case
        radarHal.unsubscribeAll()

        val interp = Interpreter(loadModelFile("range_doppler.tflite", this))
        val image = Array(32) { ByteArray(32) }
        lifecycleScope.launch(Dispatchers.IO) {
            radarHal.rawDataFlow().collect { frame ->
                Log.v(TAG, "Got a frame from HAL")
                val frameArray = frame.data.reshapeTo3D(numAntennas, numChirps, numSamplesPerChirp)
                interp.runSignature(
                    mapOf("args_0" to frameArray),
                    mapOf("output_0" to image)
                )
                val rangeDopplerImage = RangeDopplerImage()
                rangeDopplerImage.data = image.flatMap { it.asIterable() }.toByteArray()
                mRadarServiceImpl?.notifyListeners(rangeDopplerImage)
            }
        }
    }
}

private fun loadModelFile(path: String, context: Context): MappedByteBuffer {
    val fileDescriptor = context.assets.openFd(path)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    return inputStream.channel.map(
        FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength
    )
}

private val radarHal = InfineonRadarHalManager()