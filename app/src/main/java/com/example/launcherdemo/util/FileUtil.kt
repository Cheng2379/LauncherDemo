package com.example.launcherdemo.util

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Environment
import com.example.launcherdemo.bean.MediaInfoBean
import java.io.File
import java.util.Locale

/**
 *
 * @author cheng
 * @since 2025/4/8
 */
object FileUtil {
    private val musicDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    private val downloadDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    fun scanLocalMusic(context: Context): List<MediaInfoBean>? {
        val allMusic = mutableListOf<MediaInfoBean>()
        try {
            if (musicDir.exists()) {
                scanMusicFiles(context, musicDir).takeIf {
                    it.isNotEmpty()
                }?.let {
                    allMusic.addAll(it)
                }
            }
            if (downloadDir.exists()) {
                scanMusicFiles(context, downloadDir).takeIf {
                    it.isNotEmpty()
                }?.let {
                    allMusic.addAll(it)
                }
            }
            Logger.d("scanLocalMusic finish, size: ${allMusic.size}")
            return allMusic
        } catch (e: Exception) {
            Logger.e("scanMusicDirectory fail", e)
        }
        return null
    }

    fun scanMusicFiles(context: Context, directory: File): List<MediaInfoBean> {
        val mediaInfoBeanList = mutableListOf<MediaInfoBean>()
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanMusicFiles(context, file)
            }
            if (isMusicFile(file.name)) {
                try {
                    val musicBean = extractMusicInfo(context, file)
                    mediaInfoBeanList.add(musicBean)
                } catch (e: Exception) {
                    Logger.e("extractMusicInfo fail: ${file.name}", e)
                }
            }
        }
        return mediaInfoBeanList
    }

    fun isMusicFile(fileName: String): Boolean {
        // 转换为小写
        val lowerCaseName = fileName.lowercase(Locale.ROOT)
        return lowerCaseName.endsWith(".mp3") ||
                lowerCaseName.endsWith(".wav") ||
                lowerCaseName.endsWith(".flac") ||
                lowerCaseName.endsWith(".aac") ||
                lowerCaseName.endsWith(".ogg")
    }

    fun extractMusicInfo(context: Context, file: File): MediaInfoBean {
        val retriever = MediaMetadataRetriever().apply {
            setDataSource(file.absolutePath)
        }
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?: file.nameWithoutExtension
        val artist =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
        val duration =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        //val album =
        //    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "未知专辑"
        // 获取专辑封面图片
        val albumArt = try {
            retriever.embeddedPicture?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }
        } catch (e: Exception) {
            null
        }
        return MediaInfoBean(
            title,
            artist,
            MediaInfoBean.PLAYER_STATE_TERMINATION,
            albumArt,
            0f,
            duration,
            file.absolutePath
        )
    }

}