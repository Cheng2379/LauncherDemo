package com.example.launcherdemo.util

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Build
import android.os.Parcelable
import android.text.Editable
import android.text.Html
import android.text.Spanned
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.RecyclerView
import com.example.launcherdemo.App
import com.example.launcherdemo.bean.MediaAppBean
import com.example.launcherdemo.bean.MediaInfoBean

/**
 * 拓展函数工具类
 *
 * @author Cheng
 * @since 2025/4/09
 */

fun String.showToast(duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(App.mContext, this, duration).show()
}

fun Int.showToast(duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(App.mContext, this, duration).show()
}

fun CharSequence.toHtml(): Spanned {
    return this.toString().toHtml()
}

fun String.toHtml(flags: Int = Html.FROM_HTML_MODE_COMPACT): Spanned {
    return Html.fromHtml(this, flags)
}

@Suppress("DEPRECATION")
inline fun <reified T : Parcelable> Intent.getParcelableExtraByKey(
    key: String
): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key) as? T
    }
}

fun EditText.textChangedListener(
    beforeBlock: ((s: CharSequence?, start: Int, count: Int, after: Int) -> Unit)? = null,
    afterBlock: ((s: Editable?) -> Unit)? = null,
    onTextChangedBlock: ((s: CharSequence?, start: Int, before: Int, count: Int) -> Unit)? = null
) {
    val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            beforeBlock?.invoke(s, start, count, after)
        }

        override fun onTextChanged(
            charSequence: CharSequence?,
            start: Int,
            before: Int,
            count: Int
        ) {
            onTextChangedBlock?.invoke(charSequence, start, before, count)
        }

        override fun afterTextChanged(s: Editable?) {
            afterBlock?.invoke(s)
        }
    }
    // 添加监听器
    this.addTextChangedListener(textWatcher)
}

/**
 * 启动App
 */
fun launchApp(context: Context, packageName: String) {
    // 跳转方式一
    /*val intent = Intent()
    intent.setComponent(
        ComponentName(
            appInfo.packageName,
            appInfo.mainActivity
        )
    )
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    this.startActivity(intent)*/
    // 跳转方式二, 稳定写法
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    intent?.let {
        context.startActivity(intent)
    } ?: {
        "未找到应用, 无法启动".showToast()
    }
}

/**
 * 获取媒体应用信息
 */
fun MediaController.getMediaAppBean(): MediaAppBean? {
    return this.packageName?.let { packageName ->
        Logger.d("packageName: $packageName")
        // 获取媒体应用信息
        val packageManager = App.mContext.packageManager
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        val appName = packageManager.getApplicationLabel(applicationInfo).toString()
        val appIcon = packageManager.getApplicationIcon(applicationInfo).toBitmap()

        MediaAppBean(appName, packageName, appIcon,
            this.getMediaInfoBean(),
            this)
    } ?: run { null }
}

/**
 * 获取媒体信息
 */
fun MediaController.getMediaInfoBean(): MediaInfoBean {
    val metadata = this.metadata
    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "未知歌曲"
    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "未知歌手"
    val state = this.getState()
    val albumBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
    val currentPosition = this.getPosition()
    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
    // 防止除0异常
    val process = if (duration > 0)
        (currentPosition.toFloat() / duration.toFloat())
    else 0.0f

    return MediaInfoBean(
        title,
        artist,
        state,
        albumBitmap,
        process,
        duration,
        null
    )
}

/**
 * 获取媒体信息
 */
fun MediaController.getState(): Int {
    return this.playbackState?.state ?: PlaybackState.STATE_NONE
}

/**
 * 获取媒体进度
 */
fun MediaController.getPosition(): Long {
    return this.playbackState?.position ?: 0
}

fun View.findTextViewById(id: Int): TextView = findViewById(id)

fun View.findEditTextById(id: Int): EditText = findViewById(id)

fun View.findButtonViewById(id: Int): Button = findViewById(id)

fun View.findImageViewById(id: Int): ImageView = findViewById(id)

fun View.findRecyclerViewById(id: Int): RecyclerView = findViewById(id)


