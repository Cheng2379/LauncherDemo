package com.example.launcherdemo.view

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.launcherdemo.util.findImageViewById
import com.example.launcherdemo.util.getMediaAppBean
import com.example.launcherdemo.R
import com.example.launcherdemo.bean.MediaAppBean
import com.example.launcherdemo.bean.MediaInfoBean
import com.example.launcherdemo.service.LauncherNotificationListenerService
import com.example.launcherdemo.util.Logger
import com.example.launcherdemo.util.getState


/**
 *
 * @author cheng
 * @since 2025/4/3
 */
@SuppressLint("ViewConstructor")
class MusicCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    private val view: View by lazy {
        LayoutInflater.from(context).inflate(R.layout.view_card_music, this, false)
    }
    private val mMusicLogo by lazy { view.findImageViewById(R.id.music_logo) }
    private val mResourceView: TextView by lazy { view.findViewById(R.id.music_resource) }
    private val mSwitchMusicView: ImageView by lazy { view.findViewById(R.id.music_switch_resource) }
    private val mAlbumView: ImageView by lazy { view.findViewById(R.id.music_album) }
    private val mNameAndSingerView: TextView by lazy { view.findViewById(R.id.music_name_and_singer) }
    private val mProgressBarView: SeekBar by lazy { view.findViewById(R.id.music_progressbar) }
    private val mPrevious: ImageView by lazy { view.findViewById(R.id.music_previous) }
    private val mPlayView: ImageView by lazy { view.findViewById(R.id.music_play) }
    private val mPauseView: ImageView by lazy { view.findViewById(R.id.music_pause) }
    private val mNextView: ImageView by lazy { view.findViewById(R.id.music_next) }

    private var mediaSessionManager: MediaSessionManager? = null
    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    // MediaController用于控制对应的媒体应用操作
    private var currentController: MediaController? = null

    private var currentMediaAppBean: MediaAppBean? = null
    private var currentMediaInfoBean: MediaInfoBean? = null

    //  保存当前活跃的音乐app列表
    private val allActiveMusicList = mutableListOf<MediaAppBean>()

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            currentController?.let { controller ->
                val progress = controller.playbackState?.position ?: 0L
                currentMediaInfoBean?.let { mediaInfoBean ->
                    val duration = mediaInfoBean.duration
                    mediaInfoBean.progressBar = if (duration > 0)
                        (progress.toFloat() / duration.toFloat())
                    else 0.0f
                    mProgressBarView.progress =
                        (mediaInfoBean.progressBar * mProgressBarView.max).toInt()
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    private var playbackCallback: MediaController.Callback? = null

    init {
        addView(view)
        initView()
    }

    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    fun initView() {
        registerSessionChangedListener()
        // 初始化进度条监听器
        mProgressBarView.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentController?.transportControls?.seekTo(progress.toLong())
                    updateProgressBar(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        // 上一首
        mPrevious.setOnClickListener {
            playPrevious()
        }
        // 播放
        mPlayView.setOnClickListener {
            playMusic()
        }
        // 暂停
        mPauseView.setOnClickListener {
            pauseMusic()
        }
        // 下一首
        mNextView.setOnClickListener {
            playNext()
        }

        // 切换音源
        //mSwitchMusicView.setOnClickListener {
        //    val view = LayoutInflater.from(context).inflate(R.layout.item_media_app, this, false)
        //    val popupMenu = PopupMenu(context, view)
        //}
    }

    /**
     * 注册播放器监听回调
     */
    private fun registerPlaybackCallback() {
        playbackCallback?.let { currentController?.unregisterCallback(it) }
        playbackCallback = object : MediaController.Callback() {
            // 播放状态更改
            override fun onPlaybackStateChanged(playbackState: PlaybackState?) {
                super.onPlaybackStateChanged(playbackState)
                playbackState?.let {
                    updatePlayOrPauseButton(it.state)
                    updateProgressBar(it.position)
                }
            }

            // 音乐信息更改
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                metadata?.let {
                    updateCurrentMusicInfo()
                }
            }
        }
        currentController?.registerCallback(playbackCallback as MediaController.Callback)
    }

    /**
     * 更新数据模型的进度数据
     */
    private fun updateProgressBar(progress: Long) {
        if (currentController != null && currentMediaInfoBean != null) {
            val duration = currentMediaInfoBean!!.duration
            currentMediaInfoBean!!.progressBar = if (duration > 0)
                (progress.toFloat() / duration.toFloat())
            else 0.0f
        }
    }

    /**
     * 注册会话改变监听器
     */
    private fun registerSessionChangedListener() {
        // 非系统级App必须申请通知栏监听服务, 否则无法获取到MediaSession信息
        val componentName = ComponentName(context, LauncherNotificationListenerService::class.java)
        mediaSessionManager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllerList ->
            Logger.d("Listener controller: $controllerList")
            handleActiveSessionsChanged(controllerList)
        }
        mediaSessionManager?.addOnActiveSessionsChangedListener(
            sessionListener!!,
            componentName,
            handler
        )

        // 设置完监听器后，再获取当前活动应用，用以初始化显示UI
        try {
            val activeSession = mediaSessionManager?.getActiveSessions(componentName)
            handleActiveSessionsChanged(activeSession)
        } catch (e: Exception) {
            Logger.e("SecurityException: Need Notification Access permission. ${e.message}")
            resetUI()
        }

        Logger.i("allActiveMusicList: $allActiveMusicList")
    }

    /**
     * 重置UI
     */
    private fun resetUI() {
        currentController = null
        currentMediaAppBean = null
        currentMediaInfoBean = null
        mMusicLogo.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.img_music))
        mResourceView.text = "暂无音源"
        mAlbumView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.img_album))
        mNameAndSingerView.text = "无播放"
        mProgressBarView.progress = 0
        mPlayView.visibility = View.VISIBLE
        mPauseView.visibility = View.GONE
    }

    /**
     * 处理实时媒体会话
     */
    fun handleActiveSessionsChanged(controllerList: List<MediaController>?) {
        allActiveMusicList.clear()
        if (!controllerList.isNullOrEmpty()) {
            var playingController: MediaController? = null

            controllerList.forEach { controller ->
                controller.packageName?.let {
                    controller.getMediaAppBean()?.let {
                        allActiveMusicList.add(it)

                        // 获取正在活跃的媒体应用Controller
                        if (playingController == null && controller.getState() == PlaybackState.STATE_PLAYING) {
                            playingController = controller
                        }
                    }
                } ?: run {
                    Logger.e("not find app, packageName: ${controller.packageName}")
                }
            }
            currentController = playingController ?: controllerList.firstOrNull()
            currentController?.let {
                registerPlaybackCallback()
                updateCurrentMusicInfo()
                updatePlayOrPauseButton(it.getState())
            } ?: resetUI()
        } else {
            Logger.d("没有活动的媒体应用")
            allActiveMusicList.clear()
            resetUI()
        }
    }


    fun playMusic() {
        currentController?.let { controller ->
            controller.transportControls.play()
        }
    }

    fun pauseMusic() {
        currentController?.let { controller ->
            controller.transportControls.pause()
        }
    }

    fun playPrevious() {
        currentController?.let { controller ->
            controller.transportControls.skipToPrevious()
        }
    }

    fun playNext() {
        currentController?.let { controller ->
            controller.transportControls.skipToNext()
        }
    }

    /**
     * 更新播放或暂停按钮状态
     */
    private fun updatePlayOrPauseButton(playerStatus: Int) {
        when (playerStatus) {
            MediaInfoBean.PLAYER_STATE_PAUSED, MediaInfoBean.PLAYER_STATE_TERMINATION -> {
                mPlayView.visibility = View.VISIBLE
                mPauseView.visibility = View.GONE
                stopProgressUpdate()
            }

            MediaInfoBean.PLAYER_STATE_PLAYING -> {
                mPauseView.visibility = View.VISIBLE
                mPlayView.visibility = View.GONE
                startProgressUpdate()
            }

            else -> {
                mPlayView.visibility = View.VISIBLE
                mPauseView.visibility = View.GONE
                stopProgressUpdate()
            }
        }
    }

    /**
     * 手动更新当前正在播放的音乐信息
     */
    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    private fun updateCurrentMusicInfo() {
        currentController?.let { controller ->
            // 获取并设置最新的数据
            currentMediaAppBean = controller.getMediaAppBean()

            currentMediaInfoBean = currentMediaAppBean?.mediaInfoBean
            Logger.d("currentMediaInfoBean: $currentMediaInfoBean")
            mMusicLogo.setImageBitmap(
                currentMediaAppBean?.logoImg
                    ?: resources.getDrawable(R.drawable.img_music, null).toBitmap()
            )
            mResourceView.text = currentMediaAppBean?.appName ?: "媒体中心"

            currentMediaInfoBean?.let {
                mAlbumView.setImageBitmap(
                    it.albumBitmap ?: resources.getDrawable(
                        R.drawable.img_album,
                        null
                    )
                        .toBitmap()
                )
                mNameAndSingerView.text =
                    it.title + "-" + it.artist

                // 此处先设置最大值，再设置当前值，防止进度被重置
                mProgressBarView.max = it.duration.toInt()
                mProgressBarView.progress =
                    (it.progressBar * mProgressBarView.max).toInt()
            }
        }
    }

    private fun startProgressUpdate() {
        handler.post(progressRunnable)
    }

    private fun stopProgressUpdate() {
        handler.removeCallbacks(progressRunnable)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 测量子视图
        measureChild(view, widthMeasureSpec, heightMeasureSpec)
        // 测量自身尺寸为子视图准备的尺寸
        setMeasuredDimension(view.measuredWidth, view.measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // 布局子视图
        view.layout(0, 0, view.measuredWidth, measuredHeight)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        stopProgressUpdate()
        playbackCallback?.let { currentController?.unregisterCallback(it) }
        sessionListener?.let { mediaSessionManager?.removeOnActiveSessionsChangedListener(it) }
    }


}