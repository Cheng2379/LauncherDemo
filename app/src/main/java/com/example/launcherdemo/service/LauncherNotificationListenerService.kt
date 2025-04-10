package com.example.launcherdemo.service

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat

/**
 * 监听通知栏服务
 * @author cheng
 * @since 2025/4/7
 */
class LauncherNotificationListenerService: NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.notification?.extras?.let {  extras ->
            val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSessionCompat.Token::class.java)
            }else {
                 extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSessionCompat.Token
            }
            token?.let {
                val mediaController = MediaControllerCompat(applicationContext, it)

            }

        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)

    }
}