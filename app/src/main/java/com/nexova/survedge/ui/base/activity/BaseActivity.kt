package com.nexova.survedge.ui.base.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.nexova.survedge.ui.base.viewmodel.LocationViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var locationViewModel: LocationViewModel
    private var locationDialog: AlertDialog? = null
    private var locationMonitoringJob: Job? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            checkLocationEnabled()
        } else {
            onLocationPermissionDenied()
        }
    }

    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkLocationEnabled()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationViewModel = ViewModelProvider(this)[LocationViewModel::class.java]
        checkLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        if (locationViewModel.checkLocationPermission(this)) {
            checkLocationEnabled()
            startLocationMonitoring()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationMonitoring()
        dismissLocationDialog()
    }

    protected fun checkLocationPermission() {
        if (!locationViewModel.checkLocationPermission(this)) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) || shouldShowRequestPermissionRationale(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            ) {
                showPermissionRationale()
            } else {
                requestLocationPermission()
            }
        } else {
            checkLocationEnabled()
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this).setTitle("Location Permission Required")
            .setMessage(getLocationPermissionRationaleMessage())
            .setPositiveButton("Grant Permission") { _, _ ->
                requestLocationPermission()
            }.setNegativeButton("Cancel") { _, _ ->
                onLocationPermissionDenied()
            }.setCancelable(false).show()
    }

    protected open fun getLocationPermissionRationaleMessage(): String {
        return "This app needs location permission to function properly. Please grant location permission to continue."
    }

    protected fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    protected fun checkLocationEnabled() {
        val isEnabled = locationViewModel.checkLocationEnabled()
        if (!isEnabled) {
            showLocationEnableDialog()
        } else {
            dismissLocationDialog()
            onLocationServiceEnabled()
        }
    }

    private fun showLocationEnableDialog() {
        // If dialog is already showing, don't create a new instance
        if (locationDialog?.isShowing == true) {
            return
        }

        // Don't show dialog if activity is finishing or destroyed
        if (isFinishing || isDestroyed) {
            return
        }

        locationDialog?.dismiss()

        locationDialog = AlertDialog.Builder(this).setTitle(getLocationDialogTitle())
            .setMessage(getLocationDialogMessage())
            .setPositiveButton(getLocationDialogPositiveButtonText()) { _, _ ->
                openLocationSettings()
            }.setCancelable(isLocationDialogCancelable()).create()

        locationDialog?.setCanceledOnTouchOutside(false)
        locationDialog?.show()
    }

    private fun dismissLocationDialog() {
        locationDialog?.dismiss()
        locationDialog = null
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        locationSettingsLauncher.launch(intent)
    }

    private fun startLocationMonitoring() {
        stopLocationMonitoring()
        locationMonitoringJob = lifecycleScope.launch {
            while (true) {
                delay(2000) // Check every 2 seconds
                if (locationViewModel.checkLocationPermission(this@BaseActivity)) {
                    val isEnabled = locationViewModel.checkLocationEnabled()
                    if (!isEnabled) {
                        showLocationEnableDialog()
                    } else {
                        dismissLocationDialog()
                        onLocationServiceEnabled()
                    }
                }
            }
        }
    }

    private fun stopLocationMonitoring() {
        locationMonitoringJob?.cancel()
        locationMonitoringJob = null
    }

    protected open fun onLocationPermissionDenied() {
        // Override in child activities if needed
    }

    protected open fun onLocationServiceEnabled() {
        // Override in child activities if needed
    }

    protected open fun getLocationDialogTitle(): String {
        return "Location Required"
    }

    protected open fun getLocationDialogMessage(): String {
        return "Location services are currently turned off. Please turn on location to continue using the app."
    }

    protected open fun getLocationDialogPositiveButtonText(): String {
        return "Turn On Location"
    }

    protected open fun isLocationDialogCancelable(): Boolean {
        return false
    }
}

