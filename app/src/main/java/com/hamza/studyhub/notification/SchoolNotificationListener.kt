package com.hamza.studyhub.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONObject
import java.io.File

class SchoolNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "HamzaStudyHub"
        private val allowedPackages = setOf(
            "com.microsoft.teams",
            "com.grupet.web.app"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in allowedPackages) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val source = when (sbn.packageName) {
            "com.microsoft.teams" -> "Teams"
            "com.grupet.web.app" -> "Untis"
            else -> sbn.packageName
        }

        val item = JSONObject().apply {
            put("source", source)
            put("packageName", sbn.packageName)
            put("title", title)
            put("text", text)
            put("bigText", bigText)
            put("timestamp", sbn.postTime)
            put("isNew", true)
        }

        appendNotification(item)
        Log.d(TAG, "Captured $source notification: $title")
    }

    private fun appendNotification(item: JSONObject) {
        val file = File(filesDir, "school_notifications.jsonl")
        file.appendText(item.toString() + "\n")
    }
}
