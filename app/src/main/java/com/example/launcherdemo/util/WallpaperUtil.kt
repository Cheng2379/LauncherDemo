package com.example.launcherdemo.util

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.Drawable

/**
 * 壁纸工具
 *
 * @author cheng
 * @since 2025/4/24
 */
object WallpaperUtil {
    private lateinit var wallpaperManager: WallpaperManager

    /**
     * 初始化壁纸管理器
     */
    fun initWallpaperManager(context: Context) {
        wallpaperManager = WallpaperManager.getInstance(context)
    }

    /**
     * 获取系统壁纸
     */
    fun getWallpaperDrawable(): Drawable? {
        return wallpaperManager.drawable
    }

    /**
     * 设置壁纸
     */
    fun setWallpaper(resource: Int) {
        wallpaperManager.setResource(resource)
    }


}