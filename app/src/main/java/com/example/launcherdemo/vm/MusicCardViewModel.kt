package com.example.launcherdemo.vm

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.launcherdemo.bean.AppInfoBean
import com.example.launcherdemo.bean.MediaAppBean
import com.example.launcherdemo.service.LauncherNotificationListenerService
import com.example.launcherdemo.util.Logger
import com.example.launcherdemo.util.getAllMediaApp
import com.example.launcherdemo.util.getMediaAppBean
import com.example.launcherdemo.util.getState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 *
 * @author cheng
 * @since 2025/4/17
 */
class MusicCardViewModel : ViewModel() {
    private val _currentMediaApp = MutableStateFlow<MediaAppBean?>(null)
    val currentMediaApp = _currentMediaApp.asStateFlow()

    private val _activeMusicList = MutableStateFlow<List<MediaAppBean>>(emptyList())
    val activeMusicList = _activeMusicList.asStateFlow()

    private val _allMediaApps = MutableStateFlow<List<AppInfoBean>>(emptyList())
    val allMediaApps = _allMediaApps.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _progressPercent = MutableStateFlow(0f)
    val progressPercent = _progressPercent.asStateFlow()

    private var mediaSessionManager: MediaSessionManager? = null

    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    // MediaController用于控制对应的媒体应用操作
    private var currentController: MediaController? = null

    private var componentName: ComponentName? = null

    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
        }
    }

    /** 周期性检查活跃媒体 */
    private var isPeriodicCheckRunning = false

    /** 检查活动媒体 */
    private val checkActiveMediaRunnable = object : Runnable {
        override fun run() {
            if (!isPeriodicCheckRunning) return
            try {
                if (componentName == null) return

                val activeSession = mediaSessionManager?.getActiveSessions(componentName)
                activeSession?.let { controllers ->
                    var firstPlayingController: MediaController? = null

                    controllers.forEach { controller ->
                        val state = controller.getState()
                        if (state == PlaybackState.STATE_PLAYING) {
                            firstPlayingController = controller
                            return@forEach
                        }
                    }
                    if (firstPlayingController != null && currentController?.packageName != firstPlayingController?.packageName) {
                        currentController = firstPlayingController
                        registerPlaybackCallback()
                        updateStateBasedOnController()
                    }
                }
                handler.postDelayed(this, 3000)
            } catch (e: Exception) {
                Logger.e("Error checking active media: ${e.message}")
                isPeriodicCheckRunning = false
            }
        }
    }

    /**
     * 注册播放器监听回调
     */
    private val playbackCallback = object : MediaController.Callback() {
        // 播放状态更改
        override fun onPlaybackStateChanged(playbackState: PlaybackState?) {
            super.onPlaybackStateChanged(playbackState)
            updateStateBasedOnController()
            playbackState?.let {
                val isPlaying = it.state == PlaybackState.STATE_PLAYING
                _isPlaying.value = isPlaying

                // 更新当前应用信息
                currentController?.getMediaAppBean()?.let { mediaApp ->
                    _currentMediaApp.value = mediaApp
                }

                if (isPlaying) {
                    startProgressUpdate()
                } else {
                    stopProgressUpdate()
                }
            }
        }


        // 音乐信息更改
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            currentController?.getMediaAppBean()?.let { mediaApp ->
                _currentMediaApp.value = mediaApp
            }
        }
    }

    private fun updateProgress() {
        currentController?.let { controller ->
            val progress = controller.playbackState?.position ?: 0L
            _currentMediaApp.value?.mediaInfoBean?.let { mediaInfoBean ->
                val duration = mediaInfoBean.duration
                val progressValue = if (duration > 0)
                    (progress.toFloat() / duration.toFloat())
                else 0.0f
                _progressPercent.value = progressValue
                //mProgressBarView.progress =
                //    (mediaInfoBean.progressBar * mProgressBarView.max).toInt()
            }
        }
    }

    /**
     * 初始化媒体会话
     */
    fun initMediaSession(context: Context) {
        viewModelScope.launch {
            try {
                componentName =
                    ComponentName(context, LauncherNotificationListenerService::class.java)
                mediaSessionManager =
                    context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                sessionListener =
                    MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                        Logger.d("Listener controller size: ${controllers?.size}")
                        handleActiveSessionsChanged(controllers)
                    }
                mediaSessionManager?.addOnActiveSessionsChangedListener(
                    sessionListener!!,
                    componentName,
                    handler
                )
                handleActiveSessionsChanged(mediaSessionManager?.getActiveSessions(componentName))

                _allMediaApps.value = getAllMediaApp(context)

                startPeriodicMediaCheck()
            } catch (e: Exception) {
                Logger.e("Error initializing media session: ${e.message}")
            }
        }
    }

    /**
     * 处理实时媒体会话
     */
    private fun handleActiveSessionsChanged(controllers: List<MediaController>?) {
        val activeApps = mutableListOf<MediaAppBean>()
        if (!controllers.isNullOrEmpty()) {
            var playingController: MediaController? = null
            val previousPackageName = currentController?.packageName

            controllers.forEach { controller ->
                controller.packageName?.let {
                    controller.getMediaAppBean()?.let {
                        activeApps.add(it)

                        // 获取正在活跃的媒体应用Controller
                        if (playingController == null && controller.getState() == PlaybackState.STATE_PLAYING) {
                            playingController = controller
                        }
                    }
                } ?: run {
                    Logger.e("not find app, packageName: ${controller.packageName}")
                }
            }
            _activeMusicList.value = activeApps

            val newController = playingController ?: controllers.firstOrNull()
            // 增加新旧控制器和包名对比, 判断是否需要更新媒体UI信息
            val needUpdateUi = currentController != newController || (newController != null
                    && previousPackageName != newController.packageName)
            if (needUpdateUi) {
                currentController?.unregisterCallback(playbackCallback)
                currentController = newController
                currentController?.registerCallback(playbackCallback)

                updateStateBasedOnController()
                updateProgress()

                if (_isPlaying.value) {
                    startProgressUpdate()
                } else {
                    stopProgressUpdate()
                }
            }
        } else {
            Logger.d("没有活动的媒体应用")
            updateStateBasedOnController()
            updateProgress()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (_isPlaying.value && !handler.hasCallbacks(progressRunnable)) {
                    startProgressUpdate()
                } else {
                    stopProgressUpdate()
                }
            }
        }
    }

    /**
     * 统一更新状态的函数
     */
    private fun updateStateBasedOnController() {
        val currentMedia = currentController?.getMediaAppBean()
        _currentMediaApp.value = currentMedia
        _isPlaying.value = currentController?.getState() == PlaybackState.STATE_PLAYING
        Logger.d("StateUpdated -> App: ${currentMedia?.appName}, Playing: ${_isPlaying.value}")
    }

    /**
     * 注册播放器监听回调
     */
    private fun registerPlaybackCallback() {
        currentController?.unregisterCallback(playbackCallback)
        currentController?.registerCallback(playbackCallback)
    }


    /**
     * 开始定期检查活跃的媒体应用
     */
    fun startPeriodicMediaCheck() {
        if (!isPeriodicCheckRunning) {
            isPeriodicCheckRunning = true
            handler.post(checkActiveMediaRunnable)
            Logger.d("开始定期检查媒体应用")
        }
    }

    /**
     * 停止定期检查活跃的媒体应用
     */
    fun stopPeriodicMediaCheck() {
        isPeriodicCheckRunning = false
        handler.removeCallbacks(checkActiveMediaRunnable)
        Logger.d("停止定期检查媒体应用")
    }

    /**
     * 开始定时更新进度
     */
    private fun startProgressUpdate() {
        handler.removeCallbacks(progressRunnable)
        if (_isPlaying.value) {
            handler.post(progressRunnable)
        }
    }

    /**
     * 停止定时更新进度
     */
    private fun stopProgressUpdate() {
        handler.removeCallbacks(progressRunnable)
    }

    fun playMusic() {
        currentController?.transportControls?.play()
    }

    fun pauseMusic() {
        currentController?.transportControls?.pause()
    }

    fun playPrevious() {
        currentController?.transportControls?.skipToPrevious()
    }

    fun playNext() {
        currentController?.transportControls?.skipToNext()
    }

    fun seekTo(progress: Long) {
        _currentMediaApp.value?.mediaInfoBean?.duration?.let {  duration ->
            if (duration > 0) {
                val seekPosition = progress * duration
                currentController?.transportControls?.seekTo(progress)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        stopPeriodicMediaCheck()
        handler.removeCallbacksAndMessages(null)
        currentController?.unregisterCallback(playbackCallback)
        sessionListener?.let {  listener ->
            componentName?.let {
                try {
                    mediaSessionManager?.removeOnActiveSessionsChangedListener(listener)
                } catch (e: Exception) {
                    Logger.e("Error removing ActiveSessionsChangedListener, ${e.message}")
                }
            }
        }
        mediaSessionManager = null
        currentController = null
    }


}