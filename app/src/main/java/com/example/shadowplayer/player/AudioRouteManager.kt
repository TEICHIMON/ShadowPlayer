package com.example.shadowplayer.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRoute2Info
import android.media.MediaRouter
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioOutputType {
    SPEAKER,
    BLUETOOTH,
    WIRED,
    OTHER
}

data class AudioOutputRoute(
    val name: String = "当前设备",
    val type: AudioOutputType = AudioOutputType.OTHER
) {
    val isBluetooth: Boolean
        get() = type == AudioOutputType.BLUETOOTH
}

@SuppressLint("InlinedApi")
internal fun audioOutputTypeForDeviceType(deviceType: Int): AudioOutputType = when (deviceType) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_BLE_BROADCAST,
    AudioDeviceInfo.TYPE_HEARING_AID -> AudioOutputType.BLUETOOTH

    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET -> AudioOutputType.WIRED

    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> AudioOutputType.SPEAKER

    else -> AudioOutputType.OTHER
}

internal fun defaultRouteNameForOutputType(type: AudioOutputType): String = when (type) {
    AudioOutputType.BLUETOOTH -> "蓝牙设备"
    AudioOutputType.WIRED -> "有线耳机"
    AudioOutputType.SPEAKER -> "手机扬声器"
    AudioOutputType.OTHER -> "当前设备"
}

@Singleton
class AudioRouteManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val legacyRouter =
        appContext.getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val _currentRoute = MutableStateFlow(AudioOutputRoute())
    val currentRoute: StateFlow<AudioOutputRoute> = _currentRoute.asStateFlow()

    private val legacyCallback = object : MediaRouter.SimpleCallback() {
        override fun onRouteSelected(
            router: MediaRouter,
            type: Int,
            info: MediaRouter.RouteInfo
        ) = refreshRoute()

        override fun onRouteUnselected(
            router: MediaRouter,
            type: Int,
            info: MediaRouter.RouteInfo
        ) = refreshRoute()

        override fun onRouteChanged(router: MediaRouter, info: MediaRouter.RouteInfo) =
            refreshRoute()
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshRoute()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) =
            refreshRoute()
    }

    init {
        legacyRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, legacyCallback)
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            registerMediaRouter2Callback()
        }
        refreshRoute()
    }

    fun showOutputSwitcher(context: Context) {
        val shown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            showSystemOutputSwitcher(context)
        } else {
            false
        }
        if (!shown) {
            openBluetoothSettings(context)
        }
    }

    fun refreshRoute() {
        _currentRoute.value = readActualAudioRoute() ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            readMediaRouter2Route()
        } else {
            readLegacyRoute()
        }
    }

    private fun readActualAudioRoute(): AudioOutputRoute? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            routeFromAudioDevices(audioManager.getAudioDevicesForAttributes(mediaAttributes))
                ?.let { return it }
        }

        return routeFromAudioDevices(
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        )
    }

    private fun routeFromAudioDevices(devices: List<AudioDeviceInfo>): AudioOutputRoute? {
        val device = devices
            .filter { it.isSink }
            .minByOrNull { outputPriority(audioOutputTypeForDeviceType(it.type)) }
            ?: return null

        val type = audioOutputTypeForDeviceType(device.type)
        val fallbackName = defaultRouteNameForOutputType(type)
        return AudioOutputRoute(
            name = device.productName?.toString()?.ifBlank { fallbackName } ?: fallbackName,
            type = type
        )
    }

    private fun outputPriority(type: AudioOutputType): Int = when (type) {
        AudioOutputType.BLUETOOTH -> 0
        AudioOutputType.WIRED -> 1
        AudioOutputType.SPEAKER -> 2
        AudioOutputType.OTHER -> 3
    }

    private fun readLegacyRoute(): AudioOutputRoute {
        val route = legacyRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
        return AudioOutputRoute(
            name = route.getName(appContext).toString().ifBlank { "当前设备" },
            type = legacyOutputType(route.deviceType)
        )
    }

    private fun legacyOutputType(deviceType: Int): AudioOutputType = when (deviceType) {
        MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH -> AudioOutputType.BLUETOOTH
        MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER -> AudioOutputType.SPEAKER
        else -> AudioOutputType.OTHER
    }

    private fun openBluetoothSettings(context: Context) {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .recoverCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun registerMediaRouter2Callback() {
        val router = MediaRouter2.getInstance(appContext)
        router.registerControllerCallback(
            ContextCompat.getMainExecutor(appContext),
            object : MediaRouter2.ControllerCallback() {
                override fun onControllerUpdated(
                    controller: MediaRouter2.RoutingController
                ) = updateFromController(controller)
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readMediaRouter2Route(): AudioOutputRoute {
        val controller = MediaRouter2.getInstance(appContext).systemController
        return routeFromController(controller)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun updateFromController(controller: MediaRouter2.RoutingController) {
        refreshRoute()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun routeFromController(
        controller: MediaRouter2.RoutingController
    ): AudioOutputRoute {
        val route = controller.selectedRoutes.firstOrNull() ?: return readLegacyRoute()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            route2OutputType(route)
        } else {
            legacyOutputType(
                legacyRouter.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO).deviceType
            )
        }
        return AudioOutputRoute(
            name = route.name.toString().ifBlank { "当前设备" },
            type = type
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun route2OutputType(route: MediaRoute2Info): AudioOutputType = when (route.type) {
        MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
        MediaRoute2Info.TYPE_BLE_HEADSET,
        MediaRoute2Info.TYPE_HEARING_AID -> AudioOutputType.BLUETOOTH

        MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> AudioOutputType.SPEAKER

        MediaRoute2Info.TYPE_WIRED_HEADPHONES,
        MediaRoute2Info.TYPE_WIRED_HEADSET,
        MediaRoute2Info.TYPE_USB_HEADSET -> AudioOutputType.WIRED

        else -> AudioOutputType.OTHER
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun showSystemOutputSwitcher(context: Context): Boolean =
        runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }
            .getOrDefault(false)
}
