package com.example.launcherdemo.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.launcherdemo.util.findImageViewById
import com.example.launcherdemo.R
import com.example.launcherdemo.adapter.RecyclerViewAdapter
import com.example.launcherdemo.config.CardComponentState
import com.example.launcherdemo.databinding.ViewCardMusicBinding
import com.example.launcherdemo.fragment.LauncherDialogFragment
import com.example.launcherdemo.util.Logger
import com.example.launcherdemo.util.PermissionUtil
import com.example.launcherdemo.util.findRecyclerViewById
import com.example.launcherdemo.util.findTextViewById
import com.example.launcherdemo.util.launchApp
import com.example.launcherdemo.vm.MusicCardViewModel
import kotlinx.coroutines.launch
import kotlin.collections.ArrayList

/**
 * 音乐卡片
 * @author cheng
 * @since 2025/4/3
 */
@SuppressLint("ViewConstructor")
class MusicCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    private lateinit var musicViewModel: MusicCardViewModel

    private val binding: ViewCardMusicBinding by lazy {
        DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.view_card_music,
            this,
            false
        )
    }
    private val rootView = binding.root
    private val mMusicLogo by lazy { binding.musicLogo }
    private val mZoomView by lazy { binding.musicCardZoom }
    private val mAlbumView by lazy { binding.musicAlbum }
    private val mNameAndSingerView by lazy { binding.musicNameAndSinger }
    private val mProgressBarView: SeekBar by lazy { binding.musicProgressbar }
    private val mPrevious by lazy { binding.musicPrevious }
    private val mPlayView by lazy { binding.musicPlay }
    private val mPauseView by lazy { binding.musicPause }
    private val mNextView by lazy { binding.musicNext }

    private var cardState: CardComponentState = CardComponentState.MINI

    init {
        addView(rootView)
        // 判断权限再展示
        if (PermissionUtil.isNotificationServiceEnabled(
                context.contentResolver,
                context.packageName
            )
        ) {
            initViewModel()
            initView()
        }
    }

    fun initViewModel() {
        (context as? FragmentActivity)?.let { activity ->
            musicViewModel = ViewModelProvider(activity)[MusicCardViewModel::class.java]
            setupDefaultState()
            // 确保有权限后再初始化
            if (PermissionUtil.isNotificationServiceEnabled(context.contentResolver, context.packageName)) {
                musicViewModel.initMediaSession(context)
            } else {
                binding.root.setOnClickListener {
                    PermissionUtil.requestNotificationPermission(activity)
                }
            }

            observeViewModel()
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables", "SetTextI18n")
    fun initView() {
        // 初始化进度条监听器
        mProgressBarView.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicViewModel.seekTo(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        // 上一首
        mPrevious.setOnClickListener {
            musicViewModel.playPrevious()
        }
        // 播放
        mPlayView.setOnClickListener {
            musicViewModel.playMusic()
        }
        // 暂停
        mPauseView.setOnClickListener {
            musicViewModel.pauseMusic()
        }
        // 下一首
        mNextView.setOnClickListener {
            musicViewModel.playNext()
        }

        rootView.setOnClickListener {
            musicViewModel.currentMediaApp.value?.let {
                launchApp(context, it.packageName)
            } ?: run {
                openDialog()
            }
        }
        rootView.setOnLongClickListener {
            openDialog()
            true
        }

        // 缩放
        mZoomView.setOnClickListener {
            cardState = when (cardState) {
                CardComponentState.MINI -> CardComponentState.MEDIUM

                CardComponentState.MEDIUM -> CardComponentState.MINI

                CardComponentState.Large -> CardComponentState.MINI
            }
            toggleLayoutStyle()
        }
    }

    private fun setupDefaultState() {
        binding.musicNameAndSinger.text = "暂无媒体信息"
        mMusicLogo.setImageResource(R.drawable.img_logo)
        mAlbumView.setImageResource(R.drawable.img_album)
    }

    fun observeViewModel() {
        val lifecycleOwner = context as? LifecycleOwner
        val lifecycleScope = lifecycleOwner?.lifecycleScope
        lifecycleScope?.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 观察当前媒体应用
                launch {
                    musicViewModel.currentMediaApp.collect { mediaApp ->
                        // 同步xml的数据
                        binding.mediaAppInfo = mediaApp
                        binding.mediaInfo = mediaApp?.mediaInfoBean
                    }
                }
                launch {
                    musicViewModel.isPlaying.collect { isPlaying ->
                        mPlayView.visibility = if (isPlaying) View.GONE else View.VISIBLE
                        mPauseView.visibility = if (!isPlaying) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    musicViewModel.progressPercent.collect { progress ->
                        musicViewModel.currentMediaApp.value?.mediaInfoBean?.let { mediaInfo ->
                            mProgressBarView.max = mediaInfo.duration.toInt()
                            mProgressBarView.progress = (progress * mediaInfo.duration).toInt()
                        }
                    }
                }
            }
        }

    }

    /**
     * 展示所有媒体应用的弹窗
     */
    private fun openDialog() {
        val dialogFragment =
            LauncherDialogFragment(R.layout.dialog_fg_music_app_info) { view, dialog ->
                val closeImg = view.findImageViewById(R.id.music_app_info_close)
                val appRv = view.findRecyclerViewById(R.id.media_rv_app)

                val allMediaApps = ArrayList(musicViewModel.allMediaApps.value)

                if (allMediaApps.isNotEmpty()) {
                    appRv.adapter = RecyclerViewAdapter(
                        context,
                        allMediaApps,
                        R.layout.item_app
                    ) { viewHolder, position ->
                        val appInfoBean = allMediaApps[position]
                        val iconView = viewHolder.itemView.findImageViewById(R.id.app_icon)
                        val appNameView = viewHolder.itemView.findTextViewById(R.id.app_name)

                        iconView.apply {
                            setImageDrawable(appInfoBean.icon)
                            clipToOutline = true
                        }
                        appNameView.text = appInfoBean.appName
                        appNameView.setTextColor(context.resources.getColor(R.color.black))

                        viewHolder.itemView.setOnClickListener {
                            launchApp(context, appInfoBean.packageName)
                            dialog.dismiss()
                        }
                    }

                    closeImg.setOnClickListener {
                        dialog.dismiss()
                    }
                } else {
                    Logger.w("No media apps found to display in dialog")
                }
            }
        (context as? FragmentActivity)?.let {
            dialogFragment.show(it.supportFragmentManager, "MediaAppExhibit")
        } ?: Logger.e("Context is not a FragmentActivity, show dialog fail!")
    }

    /**
     * 根据状态切换mini、中、大型样式，暂时只支持mini、中切换
     */
    fun toggleLayoutStyle() {
        Logger.d("cardState: $cardState")
        when (cardState) {
            CardComponentState.MINI -> {
                mZoomView.setBackgroundResource(R.drawable.img_enlarge)
                setMiniDimens()
            }

            CardComponentState.MEDIUM -> {
                mZoomView.setBackgroundResource(R.drawable.img_reduce)
                setMediumDimens()
            }

            CardComponentState.Large -> {

            }
        }
    }

    fun setMiniDimens() {

    }

    fun setMediumDimens() {
        //val rootLayout = mView.findLinearLayoutById(R.id.music_card_root)

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 测量子视图
        measureChild(rootView, widthMeasureSpec, heightMeasureSpec)
        // 测量自身尺寸为子视图准备的尺寸
        setMeasuredDimension(rootView.measuredWidth, rootView.measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // 布局子视图
        rootView.layout(0, 0, rootView.measuredWidth, measuredHeight)
    }

}