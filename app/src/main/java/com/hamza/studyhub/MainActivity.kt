package com.hamza.studyhub

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var feedContainer: LinearLayout
    private lateinit var newCountText: TextView
    private val dataFile by lazy { File(filesDir, "school_notifications.jsonl") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshFeed()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(246, 247, 251))
        }

        val title = TextView(this).apply {
            text = "Hamza Study Hub"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(20, 24, 33))
        }

        val subtitle = TextView(this).apply {
            text = "الجديد من Teams وUntis"
            textSize = 18f
            setPadding(0, dp(4), 0, dp(14))
            gravity = Gravity.END
            setTextColor(Color.DKGRAY)
        }

        newCountText = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            gravity = Gravity.CENTER
            background = roundedBackground(Color.rgb(225, 238, 255), dp(14))
            setTextColor(Color.rgb(25, 83, 153))
        }

        val accessButton = Button(this).apply {
            text = "إعداد الوصول لإشعارات Teams وUntis"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        feedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(24))
        }

        val scrollView = ScrollView(this).apply {
            addView(feedContainer)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(newCountText)
        root.addView(accessButton)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        return root
    }

    private fun refreshFeed() {
        val items = readNotifications().sortedByDescending { it.optLong("timestamp") }
        val newCount = items.count { it.optBoolean("isNew", true) }

        newCountText.text = when (newCount) {
            0 -> "✅ لا يوجد تحديثات جديدة"
            1 -> "🆕 يوجد تحديث جديد واحد"
            else -> "🆕 $newCount تحديثات جديدة"
        }

        feedContainer.removeAllViews()

        if (items.isEmpty()) {
            feedContainer.addView(TextView(this).apply {
                text = "لسه مفيش إشعارات محفوظة.\nفعّل الوصول للإشعارات، وأول إشعار من Teams أو Untis هيظهر هنا تلقائيًا."
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(44), dp(18), dp(44))
                setTextColor(Color.GRAY)
            })
            return
        }

        items.forEach { item -> feedContainer.addView(buildNotificationCard(item)) }
    }

    private fun buildNotificationCard(item: JSONObject): View {
        val source = item.optString("source", "School")
        val title = item.optString("title").ifBlank { "تحديث جديد" }
        val body = item.optString("bigText").ifBlank { item.optString("text") }
        val timestamp = item.optLong("timestamp")
        val isNew = item.optBoolean("isNew", true)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.WHITE, dp(18))
            elevation = dp(2).toFloat()
        }

        val sourceColor = if (source.equals("Teams", true)) {
            Color.rgb(92, 94, 191)
        } else {
            Color.rgb(52, 124, 89)
        }

        val sourceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }

        val sourceBadge = TextView(this).apply {
            text = if (source.equals("Teams", true)) "Teams" else "Untis"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = roundedBackground(sourceColor, dp(20))
        }

        val newBadge = TextView(this).apply {
            text = if (isNew) "NEW  🆕" else "تمت المراجعة ✓"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (isNew) Color.rgb(190, 35, 45) else Color.GRAY)
            setPadding(dp(10), 0, dp(10), 0)
        }

        sourceRow.addView(newBadge)
        sourceRow.addView(sourceBadge)

        val titleText = TextView(this).apply {
            text = title
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, dp(12), 0, dp(4))
            setTextColor(Color.rgb(25, 28, 36))
        }

        val bodyText = TextView(this).apply {
            text = body.ifBlank { "لا يوجد نص إضافي في الإشعار." }
            textSize = 16f
            gravity = Gravity.END
            setTextColor(Color.DKGRAY)
        }

        val timeText = TextView(this).apply {
            text = formatTime(timestamp)
            textSize = 13f
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
            setTextColor(Color.GRAY)
        }

        card.addView(sourceRow)
        card.addView(titleText)
        card.addView(bodyText)
        card.addView(timeText)

        if (isNew) {
            val reviewedButton = Button(this).apply {
                text = "تمت المراجعة"
                setOnClickListener {
                    markAsReviewed(item)
                    refreshFeed()
                }
            }
            card.addView(reviewedButton)
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }
        card.layoutParams = params

        return card
    }

    private fun readNotifications(): List<JSONObject> {
        if (!dataFile.exists()) return emptyList()

        return dataFile.readLines()
            .mapNotNull { line ->
                runCatching { JSONObject(line) }.getOrNull()
            }
    }

    private fun markAsReviewed(target: JSONObject) {
        val targetTimestamp = target.optLong("timestamp")
        val updated = readNotifications().map { item ->
            if (item.optLong("timestamp") == targetTimestamp) {
                item.put("isNew", false)
            }
            item
        }

        dataFile.writeText(updated.joinToString("\n") { it.toString() } + if (updated.isNotEmpty()) "\n" else "")
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return SimpleDateFormat("dd/MM/yyyy  hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
