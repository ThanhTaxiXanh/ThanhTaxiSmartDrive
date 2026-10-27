package com.thanhtaxi.smartdrive.media

import android.service.notification.NotificationListenerService

/** Receives the user-granted media-session access; never requests notification content for upload. */
class MediaAccessService : NotificationListenerService()
