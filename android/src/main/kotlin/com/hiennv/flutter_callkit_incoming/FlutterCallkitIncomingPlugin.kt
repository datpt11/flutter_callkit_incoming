package com.hiennv.flutter_callkit_incoming

//import com.hiennv.flutter_callkit_incoming.telecom.TelecomUtilities
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.startActivity
import com.hiennv.flutter_callkit_incoming.Utils.Companion.reapCollection
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.lang.ref.WeakReference


/** FlutterCallkitIncomingPlugin */
class FlutterCallkitIncomingPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {
    companion object {

        const val EXTRA_CALLKIT_CALL_DATA = "EXTRA_CALLKIT_CALL_DATA"

        @SuppressLint("StaticFieldLeak")
        private lateinit var instance: FlutterCallkitIncomingPlugin

//        @SuppressLint("StaticFieldLeak")
//        private lateinit var telecomUtilities: TelecomUtilities

        public fun getInstance(): FlutterCallkitIncomingPlugin {
            return instance
        }

        public fun hasInstance(): Boolean {
            return ::instance.isInitialized
        }

        private val methodChannels = mutableMapOf<BinaryMessenger, MethodChannel>()
        private val eventChannels = mutableMapOf<BinaryMessenger, EventChannel>()
        private val eventHandlers = mutableListOf<WeakReference<EventCallbackHandler>>()

        fun sendEvent(event: String, body: Map<String, Any>) {
            eventHandlers.reapCollection().forEach {
                it.get()?.send(event, body)
            }
        }

        public fun sendEventCustom(event: String, body: Map<String, Any>) {
            eventHandlers.reapCollection().forEach {
                it.get()?.send(event, body)
            }
        }


        fun sharePluginWithRegister(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
            initSharedInstance(flutterPluginBinding.applicationContext, flutterPluginBinding.binaryMessenger)
        }

        fun initSharedInstance(context: Context, binaryMessenger: BinaryMessenger) {
            if (!::instance.isInitialized) {
                instance = FlutterCallkitIncomingPlugin()
                instance.callkitNotificationManager = CallkitNotificationManager(context)
                instance.context = context
            }

            val channel = MethodChannel(binaryMessenger, "flutter_callkit_incoming")
            methodChannels[binaryMessenger] = channel
            channel.setMethodCallHandler(instance)

            val events = EventChannel(binaryMessenger, "flutter_callkit_incoming_events")
            eventChannels[binaryMessenger] = events
            val handler = EventCallbackHandler()
            eventHandlers.add(WeakReference(handler))
            events.setStreamHandler(handler)

//            telecomUtilities = TelecomUtilities(context)
//            TelecomUtilities.telecomUtilitiesSingleton = telecomUtilities
        }

    }

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private var activity: Activity? = null
    private var context: Context? = null
    private var callkitNotificationManager: CallkitNotificationManager? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        sharePluginWithRegister(flutterPluginBinding)
    }

    public fun showIncomingNotification(data: Data) {
        data.from = "notification"
        callkitNotificationManager?.showIncomingNotification(data.toBundle())
        //send BroadcastReceiver
        context?.sendBroadcast(
                CallkitIncomingBroadcastReceiver.getIntentIncoming(
                        requireNotNull(context),
                        data.toBundle()
                )
        )
    }

    public fun showMissCallNotification(data: Data) {
        callkitNotificationManager?.showIncomingNotification(data.toBundle())
    }

    public fun startCall(data: Data) {
        context?.sendBroadcast(
                CallkitIncomingBroadcastReceiver.getIntentStart(
                        requireNotNull(context),
                        data.toBundle()
                )
        )
    }

    public fun endCall(data: Data) {
        context?.sendBroadcast(
                CallkitIncomingBroadcastReceiver.getIntentEnded(
                        requireNotNull(context),
                        data.toBundle()
                )
        )
    }

    public fun endAllCalls() {
        val calls = getDataActiveCalls(context)
        calls.forEach {
            context?.sendBroadcast(
                    CallkitIncomingBroadcastReceiver.getIntentEnded(
                            requireNotNull(context),
                            it.toBundle()
                    )
            )
        }
        removeAllCalls(context)
    }

    public fun sendEventCustom(body: Map<String, Any>) {
        eventHandlers.reapCollection().forEach {
            it.get()?.send(CallkitConstants.ACTION_CALL_CUSTOM, body)
        }
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        try {
            when (call.method) {
                "showCallkitIncoming" -> {
                    val data = Data(call.arguments() ?: HashMap())
                    data.from = "notification"
                    //send BroadcastReceiver
                    context?.sendBroadcast(
                            CallkitIncomingBroadcastReceiver.getIntentIncoming(
                                    requireNotNull(context),
                                    data.toBundle()
                            )
                    )

                    // only report to telecom if it's a voice call
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        telecomUtilities.reportIncomingCall(data)
//                    }

                    result.success("OK")
                }

                "showCallkitIncomingSilently" -> {
                    val data = Data(call.arguments() ?: HashMap())
                    data.from = "notification"

                    // we don't need to send a broadcast, we only need to report the data to telecom
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        telecomUtilities.reportIncomingCall(data)
//                    }

                    result.success("OK")
                }

                "showMissCallNotification" -> {
                    val data = Data(call.arguments() ?: HashMap())
                    data.from = "notification"
                    callkitNotificationManager?.showMissCallNotification(data.toBundle())
                    result.success("OK")
                }

                "startCall" -> {
                    val data = Data(call.arguments() ?: HashMap())
                    context?.sendBroadcast(
                            CallkitIncomingBroadcastReceiver.getIntentStart(
                                    requireNotNull(context),
                                    data.toBundle()
                            )
                    )

//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        telecomUtilities.startCall(data)
//                    }

                    result.success("OK")
                }

                "muteCall" -> {
                    val map = buildMap {
                        val args = call.arguments
                        if (args is Map<*, *>) {
                            putAll(args as Map<String, Any>)
                        }
                    }
                    sendEvent(CallkitConstants.ACTION_CALL_TOGGLE_MUTE, map)

                    val data = Data(call.arguments() ?: HashMap())
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        telecomUtilities.muteCall(data)
//                    }

                    result.success("OK")
                }

                "holdCall" -> {
                    val map = buildMap {
                        val args = call.arguments
                        if (args is Map<*, *>) {
                            putAll(args as Map<String, Any>)
                        }
                    }
                    sendEvent(CallkitConstants.ACTION_CALL_TOGGLE_HOLD, map)

                    val data = Data(call.arguments() ?: HashMap())
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        if (data.isOnHold) {
//                            telecomUtilities.holdCall(data)
//                        } else {
//                            telecomUtilities.unHoldCall(data)
//                        }
//                    }

                    result.success("OK")
                }

                "isMuted" -> {
                    result.success(false)
                }

                "endCall" -> {
                    val data = Data(call.arguments() ?: HashMap())
                    context?.sendBroadcast(
                            CallkitIncomingBroadcastReceiver.getIntentEnded(
                                    requireNotNull(context),
                                    data.toBundle()
                            )
                    )

//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        telecomUtilities.endCall(data)
//                    }

                    result.success("OK")
                }

                "callConnected" -> {
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        telecomUtilities.acceptCall(Data(call.arguments() ?: HashMap()))
//                    }

                    result.success("OK")
                }

                "endAllCalls" -> {
                    val calls = getDataActiveCalls(context)
                    calls.forEach {
                        if (it.isAccepted) {
                            context?.sendBroadcast(
                                    CallkitIncomingBroadcastReceiver.getIntentEnded(
                                            requireNotNull(context),
                                            it.toBundle()
                                    )
                            )
                        } else {
                            context?.sendBroadcast(
                                    CallkitIncomingBroadcastReceiver.getIntentDecline(
                                            requireNotNull(context),
                                            it.toBundle()
                                    )
                            )
                        }
                    }
                    removeAllCalls(context)

                    //Additional safety net
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        telecomUtilities.endAllActiveCalls()
//                    }

                    result.success("OK")
                }

                "activeCalls" -> {
                    result.success(getDataActiveCallsForFlutter(context))
                }

                "getDevicePushTokenVoIP" -> {
                    result.success("")
                }

                "silenceEvents" -> {
                    val silence = call.arguments as? Boolean ?: false
                    CallkitIncomingBroadcastReceiver.silenceEvents = silence
                    result.success("")
                }

                "requestNotificationPermission" -> {
                    val map = buildMap {
                        val args = call.arguments
                        if (args is Map<*, *>) {
                            putAll(args as Map<String, Any>)
                        }
                    }
                    callkitNotificationManager?.requestNotificationPermission(activity, map)
                }
                // EDIT - clear the incoming notification/ring (after accept/decline/timeout)
                "hideCallkitIncoming" -> {
                    val data = Data(call.arguments() ?: HashMap())
                    context?.stopService(Intent(context, CallkitSoundPlayerService::class.java))
                    callkitNotificationManager?.clearIncomingNotification(data.toBundle(), false)
                }

                "endNativeSubsystemOnly" -> {
//                    val data = Data(call.arguments() ?: HashMap())
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                        telecomUtilities.endCall(data)
//                    }
                }

                "setAudioRoute" -> {
                    val data = Data(call.arguments() ?: HashMap())
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                        telecomUtilities.setAudioRoute(data)
//                    }
                }
                "showWhenLocked" -> {
                    var show = call.argument<Boolean>("show")
                    val keyguardManager = context?.getSystemService(FlutterFragmentActivity.KEYGUARD_SERVICE) as KeyguardManager
                    val isPhoneLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        keyguardManager.isDeviceLocked
                    } else {
                        keyguardManager.inKeyguardRestrictedInputMode();
                    };

                    if (isPhoneLocked) {
                        if (show == true) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
//                                Handler(Looper.getMainLooper()).postDelayed({
                                    activity?.setTurnScreenOn(true)
                                    activity?.setShowWhenLocked(true)
                                    val intent = Intent(context, activity!!.javaClass)
                                    activity?.startActivity(intent)
//                                }, 1000)
                            } else {
                                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
                            }
                        } else {

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    activity?.setTurnScreenOn(false)
                                    activity?.setShowWhenLocked(false)
                                    activity?.finish()
                                    activity?.moveTaskToBack(true)
                                }, 1000)
                            } else {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
                                    activity?.moveTaskToBack(true)
                                }, 1000)
                            }
                        }
                    }
                    result.success(true);
                }
            }
        } catch (error: Exception) {
            result.error("error", error.message, "")
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannels.remove(binding.binaryMessenger)?.setMethodCallHandler(null)
        eventChannels.remove(binding.binaryMessenger)?.setStreamHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        instance.context = binding.activity.applicationContext
        instance.activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        instance.context = binding.activity.applicationContext
        instance.activity = binding.activity
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivity() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d("FlutterCallkitPlugin", "onDetachedFromActivity: called -- activity destroyed? ${activity?.isDestroyed}")
//            if (activity?.isDestroyed == true) telecomUtilities.endAllActiveCalls()
        }
    }

    class EventCallbackHandler : EventChannel.StreamHandler {

        private var eventSink: EventChannel.EventSink? = null

        override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
            eventSink = sink
        }

        fun send(event: String, body: Map<String, Any>) {
            val data = mapOf(
                    "event" to event,
                    "body" to body
            )
            Handler(Looper.getMainLooper()).post {
                eventSink?.success(data)
            }
        }

        override fun onCancel(arguments: Any?) {
            eventSink = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        instance.callkitNotificationManager?.onRequestPermissionsResult(instance.activity, requestCode, grantResults)
        return true
    }


}
