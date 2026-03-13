package com.nexova.survedge

import android.app.Application
import android.os.Build
import android.os.Environment
import org.osmdroid.config.Configuration
import java.io.File

class SurvedgeApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        initializeOsmdroid()
    }
    
    private fun initializeOsmdroid() {
        try {
            Configuration.getInstance().load(
                applicationContext,
                getSharedPreferences("osmdroid", MODE_PRIVATE)
            )
            
            Configuration.getInstance().userAgentValue = packageName
            
            val osmdroidBasePath: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                File(applicationContext.getExternalFilesDir(null), "osmdroid")
            } else {
                File(Environment.getExternalStorageDirectory(), "osmdroid")
            }
            
            if (!osmdroidBasePath.exists()) {
                osmdroidBasePath.mkdirs()
            }
            
            Configuration.getInstance().osmdroidBasePath = osmdroidBasePath
            
            val tileCachePath = File(osmdroidBasePath, "tiles")
            if (!tileCachePath.exists()) {
                tileCachePath.mkdirs()
            }
            Configuration.getInstance().osmdroidTileCache = tileCachePath
            
            Configuration.getInstance().cacheMapTileCount = 2000
            Configuration.getInstance().cacheMapTileOvershoot = 200
            Configuration.getInstance().tileFileSystemCacheMaxBytes = 1000L * 1024 * 1024
            Configuration.getInstance().tileFileSystemCacheTrimBytes = 800L * 1024 * 1024
            Configuration.getInstance().tileDownloadThreads = 4
            Configuration.getInstance().tileDownloadMaxQueueSize = 200
            Configuration.getInstance().tileFileSystemMaxQueueSize = 200
            
        } catch (e: Exception) {
        }
    }
}

