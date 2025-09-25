package com.paradoxcat.infineon.radar

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.paradoxcat.infineon.radar.IInfineonRadarService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "InfineonRadarManager"

/**
 * Encapsulates logic of binding to InifineonRadarService via AIDL and exposes
 * better interface for apps.
 */
public class InfineonRadarManager {
    public fun bind(context: Context) {
        if (mRadarService != null) {
            Log.d(TAG, "bindToInfineonRadarService: already bound")
            return
        }

        var intent = Intent()
        intent.component = ComponentName("com.paradoxcat.infineon.radar",
            "com.paradoxcat.infineon.radar.InfineonRadarService")
        val success = context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        if (!success) {
            throw RemoteException("Failed to bind to InfineonRadarService")
        }
        Log.d(TAG, "bind to InfineonRadarService: success")
    }

    public fun unbind(context: Context) {
        context.unbindService(mConnection)
        Log.d(TAG, "unbind from InfineonRadarService: success")
    }

    public fun imageFlow(): Flow<ByteArray> = callbackFlow {
        Log.d(TAG, "imageFlow: starting")
        val listener = object : IRangeDopplerListener.Stub() {
            override fun onFrameReceived(frame: RangeDopplerImage) {
                trySend(frame.data)
            }
        }
        var subscriptionId = mRadarService?.subscribe(listener)
        while (subscriptionId == null) {
            Log.w(TAG, "Failed to subscribe, retrying in 10 seconds...")
            delay(10_000)
            subscriptionId = mRadarService?.subscribe(listener)
        }
        Log.i(TAG, "Subscribed to InfineonRadarService with id $subscriptionId")
        awaitClose {
            if (subscriptionId != null) {
                Log.i(TAG, "Unsubscribing from InfineonRadarService with id $subscriptionId")
                mRadarService?.unsubscribe(subscriptionId)
            }
        }
    }

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            Log.d(TAG, "onServiceConnected, name: " + name + ", binder: " + binder)
            mRadarService = IInfineonRadarService.Stub.asInterface(binder)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            Log.d(TAG, "onServiceDisconnected, name: " + name)
            mRadarService = null
        }
        override fun onNullBinding(name: ComponentName) {
            Log.d(TAG, "onNullBinding, name: " + name)
            mRadarService = null
        }
        override fun onBindingDied(name: ComponentName) {
            Log.d(TAG, "onBindingDied, name: " + name)
            mRadarService = null
        }
    }

    private var mRadarService: IInfineonRadarService? = null
}