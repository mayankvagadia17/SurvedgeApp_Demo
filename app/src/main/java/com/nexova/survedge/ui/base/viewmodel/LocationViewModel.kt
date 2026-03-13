package com.nexova.survedge.ui.base.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val locationManager: LocationManager =
        application.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun checkLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun checkLocationPermission(context: Context): Boolean {
        val fineLocation = PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        
        return fineLocation || coarseLocation
    }
}

