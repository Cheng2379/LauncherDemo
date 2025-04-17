package com.example.launcherdemo.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.launcherdemo.bean.AppInfoBean
import com.example.launcherdemo.bean.MediaAppBean
import com.example.launcherdemo.bean.MediaInfoBean
import com.example.launcherdemo.util.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 *
 * @author cheng
 * @since 2025/4/16
 */
class MainViewModel(application: Application): AndroidViewModel(application) {
    private val _apps = MutableStateFlow<List<AppInfoBean>>(emptyList())
    val apps= _apps.asStateFlow()

    init {
        loadApplicationInfoList()
    }

    fun loadApplicationInfoList() {
        viewModelScope.launch {
            // 调用 AppRepository 的挂起函数获取应用列表
            val apps = AppRepository.getInstalledApps(getApplication())
            // 按应用名称排序并更新 StateFlow
            _apps.value = apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
        }
    }
}