package com.example.launcherdemo.util

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 *
 * @author cheng
 * @since 2025/4/7
 */
object PermissionUtil {

    /**
     * 检查权限是否被授予, 常规方式, 旧版API
     */
    fun checkPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 请求权限, 常规方式， 旧版API
     */
    fun requestPermission(activity: Activity, permission: String, code: Int) {
        if (!checkPermission(activity, permission)) {
            activity.requestPermissions(
                arrayOf(permission),
                code
            )
        }
    }

    /**
     * 新版API
     */
    fun registerMultiplePermissionsLauncher(
        activity: ComponentActivity,
        onResult: (Map<String, Boolean>) -> Unit
    ): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
            onResult
        )
    }

    /**
     * 获取通知栏权限
     */
    fun requestNotificationPermission(
        context: Context,
    ) {
        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    /**
     * 检查通知权限是否打开
     */
    fun isNotificationServiceEnabled(
        contentResolver: ContentResolver,
        packageName: String
    ): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }


}