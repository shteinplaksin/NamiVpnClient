package io.github.hhwkart.nami

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

const val ACCESS_LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/** Policy for the Android 17 runtime permission that gates LAN socket access. */
object LocalNetworkPermissionPolicy {
    const val PERMISSION_REQUIRED_FROM_SDK = 37

    fun isPermissionRequired(sdkInt: Int): Boolean = sdkInt >= PERMISSION_REQUIRED_FROM_SDK

    fun shouldExposeToLan(
        allowAccess: Boolean,
        sdkInt: Int,
        hasPermission: Boolean,
        forExport: Boolean = false,
        forTest: Boolean = false,
    ): Boolean {
        if (!allowAccess || forTest) return false
        if (forExport) return true
        return !isPermissionRequired(sdkInt) || hasPermission
    }

    fun shouldForceReloadServiceForPermissionChange(
        permissionWasGranted: Boolean,
        permissionIsGranted: Boolean,
        serviceStarted: Boolean,
    ): Boolean =
        permissionWasGranted != permissionIsGranted && serviceStarted
}

/** Tracks activity-visible permission transitions so multiple callbacks reload at most once. */
class LocalNetworkPermissionTransition(initiallyGranted: Boolean? = null) {
    private var lastKnownPermission = initiallyGranted

    fun shouldForceReload(
        permissionIsGranted: Boolean,
        serviceStarted: Boolean,
        sdkInt: Int,
    ): Boolean {
        val permissionWasGranted = lastKnownPermission
        lastKnownPermission = permissionIsGranted
        if (permissionWasGranted == null) {
            if (!LocalNetworkPermissionPolicy.isPermissionRequired(sdkInt)) return false
            if (!serviceStarted) {
                // The runtime config may belong to a surviving background
                // service that the activity has not connected to yet. Keep
                // the unknown snapshot until a service start is observed,
                // regardless of the current LAN preference value.
                lastKnownPermission = null
                return false
            }
            return true
        }
        return LocalNetworkPermissionPolicy.shouldForceReloadServiceForPermissionChange(
            permissionWasGranted = permissionWasGranted,
            permissionIsGranted = permissionIsGranted,
            serviceStarted = serviceStarted,
        )
    }
}

fun Context.hasLocalNetworkPermission(): Boolean =
    !LocalNetworkPermissionPolicy.isPermissionRequired(Build.VERSION.SDK_INT) ||
        checkSelfPermission(ACCESS_LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED
