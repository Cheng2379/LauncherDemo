package com.example.launcherdemo.view

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import androidx.media.MediaBrowserServiceCompat
import com.example.launcherdemo.util.findImageViewById
import com.example.launcherdemo.util.getMediaAppBean
import com.example.launcherdemo.R
import com.example.launcherdemo.adapter.RecyclerViewAdapter
import com.example.launcherdemo.bean.AppInfoBean
import com.example.launcherdemo.bean.MediaAppBean
import com.example.launcherdemo.bean.MediaInfoBean
import com.example.launcherdemo.fragment.LauncherDialogFragment
import com.example.launcherdemo.service.LauncherNotificationListenerService
import com.example.launcherdemo.util.Logger
import com.example.launcherdemo.util.PermissionUtil
import com.example.launcherdemo.util.findRecyclerViewById
import com.example.launcherdemo.util.findTextViewById
import com.example.launcherdemo.util.getViewParentWidth
import com.example.launcherdemo.util.getState
import com.example.launcherdemo.util.launchApp
import java.util.ArrayList


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
    private val mView: View by lazy {
        LayoutInflater.from(context).inflate(R.layout.view_card_music, this, false)
    }
    private val mMusicLogo by lazy { mView.findImageViewById(R.id.music_logo) }
    private val mZoomView by lazy { mView.findImageViewById(R.id.music_card_zoom) }
    private val mAlbumView: ImageView by lazy { mView.findViewById(R.id.music_album) }
    private val mNameAndSingerView: TextView by lazy { mView.findViewById(R.id.music_name_and_singer) }
    private val mProgressBarView: SeekBar by lazy { mView.findViewById(R.id.music_progressbar) }
    private val mPrevious: ImageView by lazy { mView.findViewById(R.id.music_previous) }
    private val mPlayView: ImageView by lazy { mView.findViewById(R.id.music_play) }
    private val mPauseView: ImageView by lazy { mView.findViewById(R.id.music_pause) }
    private val mNextView: ImageView by lazy { mView.findViewById(R.id.music_next) }

    private var mediaSessionManager: MediaSessionManager? = null
    private var sessionListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    // MediaController用于控制对应的媒体应用操作
    private var currentController: MediaController? = null

    private var currentMediaAppBean: MediaAppBean? = null

    private var allMediaApps: List<AppInfoBean> = emptyList()

    //  保存当前活跃的音乐app列表
    private val activeMusicList = mutableListOf<MediaAppBean>()

    private val handler = Handler(Looper.getMainLooper())

    private val progressRunnable = object : Runnable {
        override fun run() {
            currentController?.let { controller ->
                val progress = controller.playbackState?.position ?: 0L
                currentMediaAppBean?.mediaInfoBean?.let { mediaInfoBean ->
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

    private val checkActiveMediaRunnable = object : Runnable {
        override fun run() {
            try {
                val componentName =
                    ComponentName(context, LauncherNotificationListenerService::class.java)
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
                        updateCurrentMusicCardView()
                        updatePlayOrPauseButton(
                            currentController?.getState() ?: PlaybackState.STATE_NONE
                        )
                    }
                }
                handler.postDelayed(this, 3000)
            } catch (e: Exception) {
                Logger.e("Error checking active media: ${e.message}")
            }
        }
    }

    private var playbackCallback: MediaController.Callback? = null

    init {
        addView(mView)
        // 判断权限再展示
        if (PermissionUtil.isNotificationServiceEnabled(
                context.contentResolver,
                context.packageName
            )
        ) {
            initView()
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    fun initView() {
        registerSessionChangedListener()

        allMediaApps = getAllMediaApp()

        handler.postDelayed(checkActiveMediaRunnable, 3000)
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

        mView.setOnClickListener {
            currentMediaAppBean?.let {
                launchApp(context, it.packageName)
            } ?: run {
                openDialog()
            }
        }
        mView.setOnLongClickListener {
            openDialog()
            true
        }

        // 缩放
        mZoomView.setOnClickListener {
        }
    }

    /**
     * 获取所有的应用列表信息
     */
    private fun getAllMediaApp(): List<AppInfoBean> {
        val allMediaAppInfoList = mutableListOf<AppInfoBean>()
        val intent = Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE)
        val services =
            context.packageManager.queryIntentServices(intent, PackageManager.GET_RESOLVED_FILTER)
        services.takeIf {
            it.isNotEmpty()
        }?.let { list ->
            list.forEach { resolveInfo ->
                resolveInfo.serviceInfo?.let { service ->
                    allMediaAppInfoList.add(
                        AppInfoBean(
                            service.packageName,
                            service.name,
                            resolveInfo.loadLabel(context.packageManager).toString(),
                            resolveInfo.loadIcon(context.packageManager)
                        )
                    )
                }
            }
            return allMediaAppInfoList
        } ?: run {
            return emptyList()
        }
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

                    // 当状态变为播放时，更新当前音乐信息
                    if (playbackState.state == PlaybackState.STATE_PLAYING) {
                        updateCurrentMusicCardView()
                    }
                }
            }

            // 音乐信息更改
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                metadata?.let {
                    updateCurrentMusicCardView()
                }
            }
        }
        currentController?.registerCallback(playbackCallback as MediaController.Callback)
    }

    /**
     * 更新数据模型的进度数据
     */
    private fun updateProgressBar(progress: Long) {
        if (currentController != null) {
            val duration = currentMediaAppBean?.mediaInfoBean?.duration ?: 0
            currentMediaAppBean?.mediaInfoBean?.progressBar = if (duration > 0)
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

        Logger.i("allActiveMusicList: $activeMusicList")
    }

    /**
     * 重置UI
     */
    private fun resetUI() {
        currentController = null
        currentMediaAppBean = null
        mMusicLogo.apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.img_logo))
            clipToOutline = true
        }
        mAlbumView.apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.img_album))
            clipToOutline = true
        }
        mNameAndSingerView.text = "无播放"
        mProgressBarView.progress = 0
        mPlayView.visibility = View.VISIBLE
        mPauseView.visibility = View.GONE
    }

    /**
     * 处理实时媒体会话
     */
    fun handleActiveSessionsChanged(controllerList: List<MediaController>?) {
        activeMusicList.clear()
        if (!controllerList.isNullOrEmpty()) {
            var playingController: MediaController? = null
            val previousPackageName = currentController?.packageName

            controllerList.forEach { controller ->
                controller.packageName?.let {
                    controller.getMediaAppBean()?.let {
                        activeMusicList.add(it)

                        // 获取正在活跃的媒体应用Controller
                        if (playingController == null && controller.getState() == PlaybackState.STATE_PLAYING) {
                            playingController = controller
                        }
                    }
                } ?: run {
                    Logger.e("not find app, packageName: ${controller.packageName}")
                }
            }
            val newController = playingController ?: controllerList.firstOrNull()
            // 增加新旧控制器和包名对比, 判断是否需要更新媒体UI信息
            val needUpdateUi = currentController != newController || (newController != null
                    && previousPackageName != newController.packageName)

            currentController = newController
            currentController?.let {
                registerPlaybackCallback()
                if (needUpdateUi) {
                    updateCurrentMusicCardView()
                }
                updatePlayOrPauseButton(it.getState())
            } ?: resetUI()
        } else {
            Logger.d("没有活动的媒体应用")
            activeMusicList.clear()
            resetUI()
        }
        Logger.d("allActiveMusic: $activeMusicList")
    }

    /**
     * 更新播放或暂停按钮状态
     */
    private fun updatePlayOrPauseButton(playerStatus: Int) {
        handler.post {
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
    }

    /**
     * 更新当前正在播放的音乐视图信息
     */
    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    private fun updateCurrentMusicCardView() {
        currentController?.let { controller ->
            // 获取并设置最新的数据
            currentMediaAppBean = controller.getMediaAppBean()

            Logger.i("currentMediaApp: $currentMediaAppBean")
            mMusicLogo.apply {
                setImageBitmap(
                    currentMediaAppBean?.logoImg
                        ?: resources.getDrawable(R.drawable.img_logo, null).toBitmap()
                )
                clipToOutline = true
            }

            currentMediaAppBean?.mediaInfoBean?.let {
                mAlbumView.apply {
                    setImageBitmap(
                        it.albumBitmap ?: resources.getDrawable(
                            R.drawable.img_album,
                            null
                        ).toBitmap()
                    )
                    clipToOutline = true
                }
                mNameAndSingerView.text =
                    it.title + "-" + it.artist
                // TODO 设置跑马灯
                mNameAndSingerView.getViewParentWidth { partntWidth, parentHeight ->
                    Logger.d("parentWidth: $partntWidth")
                }
                //mNameAndSingerView.isFocusable = true
                //mNameAndSingerView.marqueeRepeatLimit = -1
                //mNameAndSingerView.isFocusableInTouchMode = true
                //mNameAndSingerView.requestFocus()

                // 此处先设置最大值，再设置当前值，防止进度被重置
                mProgressBarView.max = it.duration.toInt()
                mProgressBarView.progress =
                    (it.progressBar * mProgressBarView.max).toInt()
            }
        }
    }

    fun playMusic() {
        currentController?.transportControls?.play()
            ?: Logger.w("playMusic: currentController is null, cannot play")
    }

    fun pauseMusic() {
        currentController?.transportControls?.pause()
            ?: Logger.w("playMusic: currentController is null, cannot pause")
    }

    fun playPrevious() {
        currentController?.transportControls?.skipToPrevious()
            ?: Logger.w("playMusic: currentController is null, cannot skipToPrevious")
    }

    fun playNext() {
        currentController?.transportControls?.skipToNext()
            ?: Logger.w("playMusic: currentController is null, cannot skipToNext")
    }

    private fun startProgressUpdate() {
        handler.post(progressRunnable)
    }

    private fun stopProgressUpdate() {
        handler.removeCallbacks(progressRunnable)
    }

    /**
     * 展示所有媒体应用的弹窗
     */
    private fun openDialog() {
        val dialogFragment =
            LauncherDialogFragment(R.layout.dialog_fg_music_app_info) { view, dialog ->
                val closeImg = view.findImageViewById(R.id.music_app_info_close)
                val appRv = view.findRecyclerViewById(R.id.media_rv_app)

                appRv.adapter = RecyclerViewAdapter(
                    context,
                    ArrayList(allMediaApps),
                    R.layout.item_app
                ) { viewHolder, position ->
                    val appInfoBean = allMediaApps[position]
                    val iconView = viewHolder.itemView.findImageViewById(R.id.app_icon)
                    val appNameView = viewHolder.itemView.findTextViewById(R.id.app_name)

                    iconView.apply {
                        background =
                            ContextCompat.getDrawable(context, R.drawable.shape_img_selector)
                        clipToOutline = true
                        setImageDrawable(appInfoBean.icon)
                    }
                    appNameView.text = appInfoBean.appName
                    appNameView.setTextColor(context.resources.getColor(R.color.black))

                    viewHolder.itemView.setOnClickListener {
                        launchApp(context, appInfoBean.packageName)
                    }
                }

                closeImg.setOnClickListener {
                    dialog.dismiss()
                }
            }
        (context as? FragmentActivity)?.let {
            dialogFragment.show(it.supportFragmentManager, "MediaAppExhibit")
        } ?: Logger.e("Context is not a FragmentActivity, show dialog fail!")
    }

    /**
     * TODO 设置缩放样式
     */
    private fun setScaleView() {

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 测量子视图
        measureChild(mView, widthMeasureSpec, heightMeasureSpec)
        // 测量自身尺寸为子视图准备的尺寸
        setMeasuredDimension(mView.measuredWidth, mView.measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // 布局子视图
        mView.layout(0, 0, mView.measuredWidth, measuredHeight)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        stopProgressUpdate()
        handler.removeCallbacks(checkActiveMediaRunnable)
        playbackCallback?.let { currentController?.unregisterCallback(it) }
        sessionListener?.let { mediaSessionManager?.removeOnActiveSessionsChangedListener(it) }
    }


}