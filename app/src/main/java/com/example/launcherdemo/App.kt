package com.example.launcherdemo

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import com.example.launcherdemo.util.Logger
import com.example.launcherdemo.util.WallpaperUtil

/**
 *
 * @author cheng
 * @since 2025/4/9
 */
class App : Application() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var mContext: Context

        fun getGlobalContext() = mContext
    }

    override fun onCreate() {
        super.onCreate()
        mContext = applicationContext
        Logger.init(Logger.DEBUG)
        Logger.setLogDeep(1)

        WallpaperUtil.initWallpaperManager(mContext)
    }
}