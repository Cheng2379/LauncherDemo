package com.example.launcherdemo.bean

import android.graphics.drawable.Drawable

/**
 *
 * @author cheng
 * @since 2025/4/1
 */
class AppInfoBean(
    val packageName: String,
    val mainActivity: String,
    val appName: String,
    val icon: Drawable
) {
    override fun toString(): String {
        return "AppInfoBean(packageName='$packageName', mainClassName='$mainActivity', appName='$appName', icon=$icon)"
    }
}