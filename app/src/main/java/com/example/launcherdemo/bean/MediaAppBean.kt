package com.example.launcherdemo.bean

import android.graphics.Bitmap
import android.media.session.MediaController

/**
 * 音乐App数据模型
 * @author cheng
 * @since 2025/4/8
 */
data class MediaAppBean(
    val appName: String = "媒体中心",
    val packageName: String,
    val logoImg: Bitmap?,
    val mediaInfoBean: MediaInfoBean?,
    // 一个音乐软件对应一个控制器
    val mediaController: MediaController
)
