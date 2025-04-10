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
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import com.example.launcherdemo.util.findImageViewById
import com.example.launcherdemo.util.getActiveMediaAppBean
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

    //// MediaBrowserCompat用于连接到媒体播放应用(酷狗、网易云)
    //private var mediaBrowser: MediaBrowserCompat? = null
    // MediaController用于控制对应的媒体应用操作
    private var currentController: MediaController? = null
    private var currentMediaAppBean: MediaAppBean? = null
    private var currentMediaInfoBean: MediaInfoBean? = null
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



    /**
     *
     */
    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    fun initView() {
        getActiveAllMediaInfo()?.let { musicList ->
            // 设置当前播放控制器
            currentController = musicList[0].mediaController
            // 设置当前音频数据
            currentMediaAppBean = musicList[0]

            registerPlaybackCallback()
            currentController?.let {
                updateCurrentMusicInfo()
                updatePlayOrPauseButton(it.getState())
            }
        }

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
    }

    private fun registerPlaybackCallback() {
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
     * 获取系统活跃媒体信息
     */
    private fun getActiveAllMediaInfo(): List<MediaAppBean>? {
        val allActiveMusicList = mutableListOf<MediaAppBean>()
        // 非系统级App必须申请通知栏监听服务, 否则无法获取到MediaSession信息
        val componentName = ComponentName(context, LauncherNotificationListenerService::class.java)
        val mediaSessionManager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val activeSessions = mediaSessionManager.getActiveSessions(componentName)

        if (activeSessions.isNotEmpty()) {
            activeSessions.forEach { controller ->
                controller?.packageName?.let {
                    controller.getActiveMediaAppBean()?.let {
                        currentMediaAppBean = it
                        allActiveMusicList.add(it)
                    }
                } ?: run {
                    Logger.e("not find app, packageName: ${controller?.packageName}")
                }
            }
            return allActiveMusicList
        } else {
            Logger.d("没有活动的媒体应用")
        }
        return null
    }

    fun playMusic() {
        currentController?.let { controller ->
            controller.transportControls.play()
            Logger.d("点击播放, 当前状态: ${controller.getState()}")
        }
    }

    fun pauseMusic() {
        currentController?.let { controller ->
            controller.transportControls.pause()
            Logger.d("点击暂停, 当前状态: ${controller.getState()}")
        }
    }

    fun playPrevious() {
        currentController?.let { controller ->
            controller.transportControls.skipToPrevious()
            Logger.d("点击上一首, 当前状态: ${controller.getState()}")
        }
    }

    fun playNext() {
        currentController?.let { controller ->
            controller.transportControls.skipToNext()
            Logger.d("点击下一首, 当前状态: ${controller.getState()}")
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
        currentController?.let {
            // 获取并设置最新的数据
            currentMediaAppBean = it.getActiveMediaAppBean()

            currentMediaInfoBean = currentMediaAppBean?.mediaInfoBean
            Logger.d("currentMediaInfoBean: $currentMediaInfoBean")
            mMusicLogo.setImageBitmap(
                currentMediaAppBean?.logoImg
                    ?: resources.getDrawable(R.drawable.img_music, null).toBitmap()
            )
            mResourceView.text = currentMediaAppBean?.appName ?: "媒体中心"

            mAlbumView.setImageBitmap(
                currentMediaInfoBean!!.albumBitmap ?: resources.getDrawable(
                    R.drawable.img_album,
                    null
                )
                    .toBitmap()
            )
            mNameAndSingerView.text =
                currentMediaInfoBean!!.title + "-" + currentMediaInfoBean!!.artist

            // 此处先设置最大值，再设置当前值，防止进度被重置
            mProgressBarView.max = currentMediaInfoBean!!.duration.toInt()
            mProgressBarView.progress =
                (currentMediaInfoBean!!.progressBar * mProgressBarView.max).toInt()
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
    }


}