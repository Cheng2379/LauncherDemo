package com.example.launcherdemo.bean

import android.graphics.Bitmap
import android.media.session.PlaybackState

/**
 * 音乐详细信息数据模型
 * @author cheng
 * @since 2025/4/3
 */
data class MediaInfoBean(
    // 歌名
    val title: String,
    // 艺术家、歌手
    val artist: String,
    // 播放状态:    1 -> 终止    2 -> 暂停    3 -> 播放
    val playerStatus: Int = PLAYER_STATE_PAUSED,
    val albumBitmap: Bitmap?,
    // 当前进度百分比, 小数格式
    var progressBar: Float,
    // 总时长
    val duration: Long,
    val localPath: String?
) {
    companion object {
        const val PLAYER_STATE_PAUSED = PlaybackState.STATE_PAUSED
        const val PLAYER_STATE_PLAYING =  PlaybackState.STATE_PLAYING
        // 播放终止状态
        const val PLAYER_STATE_TERMINATION = PlaybackState.STATE_STOPPED
    }
}
