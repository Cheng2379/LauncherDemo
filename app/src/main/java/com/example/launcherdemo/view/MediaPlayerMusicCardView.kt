package com.example.launcherdemo.view

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.media.MediaPlayer
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.launcherdemo.R
import com.example.launcherdemo.bean.MediaInfoBean
import com.example.launcherdemo.service.LauncherNotificationListenerService
import com.example.launcherdemo.util.FileUtil
import com.example.launcherdemo.util.Logger
import androidx.core.net.toUri


/**
 *
 * @author cheng
 * @since 2025/4/3
 */
@SuppressLint("ViewConstructor")
class MediaPlayerMusicCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    private val view: View by lazy {
        LayoutInflater.from(context).inflate(R.layout.view_card_music, this, false)
    }
    private val mResourceView: TextView by lazy { view.findViewById(R.id.music_resource) }
    private val mSwitchMusicView: ImageView by lazy { view.findViewById(R.id.music_switch_resource) }
    private val mAlbumView: ImageView by lazy { view.findViewById(R.id.music_album) }
    private val mNameAndSingerView: TextView by lazy { view.findViewById(R.id.music_name_and_singer) }
    private val mProgressBarView: SeekBar by lazy { view.findViewById(R.id.music_progressbar) }
    private val mPrevious: ImageView by lazy { view.findViewById(R.id.music_previous) }
    private val mPlayView: ImageView by lazy { view.findViewById(R.id.music_play) }
    private val mPauseView: ImageView by lazy { view.findViewById(R.id.music_pause) }
    private val mNextView: ImageView by lazy { view.findViewById(R.id.music_next) }

    private var mediaPlayer: MediaPlayer? = null
    private var currentMusicIndex = 0
    private val musicList = mutableListOf<MediaInfoBean>()
    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    val progress = player.currentPosition.toFloat() / player.duration
                    mProgressBarView.progress = (progress * 100).toInt()
                }
                handler.postDelayed(this, 1000)
            }
        }
    }

    init {
        addView(view)
        setUpProgressBar()
    }

    private fun setUpProgressBar() {
        mProgressBarView.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.let {
                        it.seekTo((progress * it.duration) / 100)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     *
     */
    fun setData(context: Context) {
        //val music = getActiveMediaInfo(context)

        FileUtil.scanLocalMusic(context)?.let {
            musicList.addAll(it)
        }
        musicList.takeIf {
            it.isNotEmpty()
        }?.let {
            Logger.d("music: $it")
            mResourceView.text = "本地\n音乐"
            mNameAndSingerView.text = it[0].title + "-" + it[0].artist
            it[0].albumBitmap?.let { bitmap ->
                mAlbumView.setImageBitmap(bitmap)
            }
            mProgressBarView.progress = (it[0].progressBar * 100).toInt()

            updatePlayOrPauseButton(it[0].playerStatus)
        }


        // 上一首
        mPrevious.setOnClickListener {

        }

        // 播放
        mPlayView.setOnClickListener {
            resumeMusic()
        }
        // 暂停
        mPauseView.setOnClickListener {
            pauseMusic()
        }
        mPauseView.setOnLongClickListener {
            releaseMediaPlayer()
            true
        }
        // 下一首
        mNextView.setOnClickListener {

        }
    }

    /**
     * 获取系统活跃媒体信息
     */
    private fun getActiveMediaInfo(context: Context): MediaInfoBean? {
        val componentName = ComponentName(context, LauncherNotificationListenerService::class.java)
        val mediaSessionManager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val activeSessions = mediaSessionManager.getActiveSessions(componentName)

        if (activeSessions.isNotEmpty()) {
            val mediaController = activeSessions[0]
            val metadata = mediaController.metadata

            Logger.d("session0: $mediaController")
            return MediaInfoBean("", "", 0, null, 0f, 0L, null)
        }
        return null
    }

    private fun playMusic(context: Context, mediaInfoBean: MediaInfoBean) {
        try {
            releaseMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, "file://${mediaInfoBean.localPath}".toUri())
                prepare()
                start()
            }
            Logger.d("开始播放${mediaInfoBean.title}")
            mNameAndSingerView.text = mediaInfoBean.title + "-" + mediaInfoBean.artist
            mResourceView.text = "本地\n音乐"
            mediaInfoBean.albumBitmap?.let {
                mAlbumView.setImageBitmap(it)
            } ?: run {
                mAlbumView.setImageResource(R.drawable.img_album)
            }
            updatePlayOrPauseButton(MediaInfoBean.PLAYER_STATE_PLAYING)
            handler.post(progressRunnable)
            // 播放完成
            mediaPlayer?.setOnCompletionListener {
                Toast.makeText(context, "播放完毕", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Logger.e("播放失败: ${mediaInfoBean.title}", e)
        }

    }

    private fun pauseMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                Logger.d("已暂停, 进度: ${(it.currentPosition.toFloat() / it.duration.toFloat())}, 总时长: ${it.duration.toFloat() / 1000f / 60f}")
                updatePlayOrPauseButton(MediaInfoBean.PLAYER_STATE_PAUSED)
                handler.removeCallbacks(progressRunnable)
            }
        }
    }

    private fun resumeMusic() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                updatePlayOrPauseButton(MediaInfoBean.PLAYER_STATE_PLAYING)
                handler.post(progressRunnable)
            }
        } ?: run {
            musicList.takeIf {
                it.isNotEmpty()
            }?.let {
                playMusic(context, it[0])
            }
        }
    }


    private fun releaseMediaPlayer() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
            Logger.d("已终止")
            handler.removeCallbacks(progressRunnable)
            mProgressBarView.progress = 0
            updatePlayOrPauseButton(MediaInfoBean.PLAYER_STATE_TERMINATION)
        }
        mediaPlayer = null
    }

    /**
     * 更新播放或暂停按钮状态
     */
    private fun updatePlayOrPauseButton(playerStatus: Int) {
        when (playerStatus) {
            MediaInfoBean.PLAYER_STATE_PAUSED, MediaInfoBean.PLAYER_STATE_TERMINATION -> {
                mPlayView.visibility = View.VISIBLE
                mPauseView.visibility = View.GONE
            }

            MediaInfoBean.PLAYER_STATE_PLAYING -> {
                mPauseView.visibility = View.VISIBLE
                mPlayView.visibility = View.GONE
            }
        }
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
        releaseMediaPlayer()
    }

}