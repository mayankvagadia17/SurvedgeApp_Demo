package com.nexova.survedge

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class SurvedgeApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize MapLibre before any MapView is inflated to avoid MapLibreConfigurationException
        MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre)
    }
}

