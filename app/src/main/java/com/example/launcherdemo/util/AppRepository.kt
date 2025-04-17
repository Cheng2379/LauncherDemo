package com.example.launcherdemo.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.launcherdemo.bean.AppInfoBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *
 * @author cheng
 * @since 2025/4/16
 */
object AppRepository {

    /**
     * 获取设备上的应用信息列表 (IO 线程)
     */
    suspend fun getInstalledApps(context: Context): List<AppInfoBean> = withContext(Dispatchers.IO) {
        return@withContext mutableListOf<AppInfoBean>().apply {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            context.packageManager.queryIntentActivities(mainIntent, PackageManager.GET_META_DATA)
                .mapNotNull { resolveInfo ->
                    resolveInfo.activityInfo?.packageName
                        .takeIf {
                            !it.equals("com.example.mvvmdemo.util")
                        }
                        ?.let {
                            add(
                                AppInfoBean(
                                    resolveInfo.activityInfo.packageName,
                                    resolveInfo.activityInfo.name,
                                    resolveInfo.loadLabel(context.packageManager).toString(),
                                    resolveInfo.loadIcon(context.packageManager)
                                )
                            )
                        }
                }
        }
    }

    /**
     * app是否已下载
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }


}