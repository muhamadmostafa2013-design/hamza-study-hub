package com.hamza.studyhub.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.hamza.studyhub.homework.HomeworkInterpreter
import com.hamza.studyhub.homework.UpdateType
import org.json.JSONObject
import java.io.File

class SchoolNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "HamzaStudyHub"
        private const val DUPLICATE_WINDOW_MS = 5 * 60 * 1000L
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
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString() }
            .orEmpty()

        val source = when (sbn.packageName) {
            "com.microsoft.teams" -> "Teams"
            "com.grupet.web.app" -> "Untis"
            else -> sbn.packageName
        }

        val fullText = buildList {
            if (bigText.isNotBlank()) add(bigText)
            if (text.isNotBlank() && text != bigText) add(text)
            textLines.filter { it.isNotBlank() }.forEach { if (it !in this) add(it) }
            if (subText.isNotBlank()) add(subText)
            if (summaryText.isNotBlank()) add(summaryText)
        }.joinToString("\n").trim()

        val interpretation = HomeworkInterpreter.interpret(title, fullText)
        val needsAttention = interpretation.type == UpdateType.HOMEWORK && interpretation.needsFullText
        val attentionReason = if (needsAttention) {
            "الإشعار يقول إن فيه واجب، لكن تفاصيل المطلوب غير كاملة. افتح الواجب مرة واحدة لو Teams لم يرسل النص كاملًا."
        } else {
            ""
        }

        val item = JSONObject().apply {
            put("source", source)
            put("packageName", sbn.packageName)
            put("title", title)
            put("text", text)
            put("bigText", fullText)
            put("timestamp", sbn.postTime)
            put("isNew", true)
            put("notificationKey", sbn.key)
            put("updateType", interpretation.type.name)
            put("needsAttention", needsAttention)
            put("attentionReason", attentionReason)
            put("attentionResolved", false)
        }

        if (isRecentDuplicate(item)) {
            Log.d(TAG, "Ignored duplicate $source notification: $title")
            return
        }

        appendNotification(item)
        Log.d(TAG, "Captured $source notification: $title")
    }

    private fun isRecentDuplicate(candidate: JSONObject): Boolean {
        val file = File(filesDir, "school_notifications.jsonl")
        if (!file.exists()) return false

        val candidateSource = candidate.optString("source")
        val candidateTitle = candidate.optString("title")
        val candidateBody = candidate.optString("bigText")
        val candidateTime = candidate.optLong("timestamp")

        return file.readLines()
            .asReversed()
            .take(25)
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .any { old ->
                old.optString("source") == candidateSource &&
                    old.optString("title") == candidateTitle &&
                    old.optString("bigText") == candidateBody &&
                    candidateTime - old.optLong("timestamp") in 0..DUPLICATE_WINDOW_MS
            }
    }

    private fun appendNotification(item: JSONObject) {
        val file = File(filesDir, "school_notifications.jsonl")
        file.appendText(item.toString() + "\n")
    }
}
