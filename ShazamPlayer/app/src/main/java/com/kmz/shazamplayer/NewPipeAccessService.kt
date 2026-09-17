package com.kmz.shazamplayer

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Android requires an enabled notification-listener component before NewPipe trusts an external
 * MediaBrowser client. This service never reads or stores notifications and unbinds immediately.
 */
class NewPipeAccessService : NotificationListenerService() {
    override fun onNotificationPosted(notification: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(notification: StatusBarNotification?) = Unit

    override fun onListenerConnected() {
        super.onListenerConnected()
        requestUnbind()
    }
}
