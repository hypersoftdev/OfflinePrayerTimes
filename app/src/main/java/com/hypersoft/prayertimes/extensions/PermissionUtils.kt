package com.hypersoft.prayertimes.extensions

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.app.ActivityCompat

fun Activity?.isLocationFineCoarseApproved(): Boolean {
    this?.let {
        try {

            return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                it,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) || PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                it,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

        } catch (e: Exception) {
            return false
        }

    } ?: run { return false }

}

fun Activity?.isGpsNetworkProviderEnabled(locationManager: LocationManager): Boolean {
    this?.let {
        try {
            return !(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        } catch (e: Exception) {
            return false
        }
    } ?: run { return false }
}