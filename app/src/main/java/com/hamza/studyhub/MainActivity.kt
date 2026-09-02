package com.hamza.studyhub

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hamza.studyhub.homework.HomeworkInterpreter
import com.hamza.studyhub.homework.UpdateType
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var feedContainer: LinearLayout
    private lateinit var newCountText: TextView
    private lateinit var shareStatusText: TextView
    private val dataFile by lazy { File(filesDir, "school_notifications.jsonl") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        processIncomingShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIncomingShare(intent)
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

        val readerHelp = TextView(this).apply {
            text = "📖 علشان البرنامج يقرأ الواجب كاملًا: افتح الواجب في Teams أو Untis ثم Share → Hamza Study Hub. ولو مفيش Share، شارك Screenshot للواجب والبرنامج هيقرأ النص منها."
            textSize = 15f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            gravity = Gravity.END
            background = roundedBackground(Color.rgb(255, 247, 222), dp(14))
            setTextColor(Color.rgb(92, 67, 15))
        }

        shareStatusText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.END
            setPadding(0, dp(6), 0, dp(4))
            setTextColor(Color.rgb(52, 124, 89))
            visibility = View.GONE
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
        root.addView(readerHelp)
        root.addView(shareStatusText)
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
        val imported = item.optBoolean("imported", false)
        val interpretation = HomeworkInterpreter.interpret(title, body)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.WHITE, dp(18))
            elevation = dp(2).toFloat()
        }

        val sourceColor = when {
            source.equals("Teams", true) -> Color.rgb(92, 94, 191)
            source.equals("Untis", true) -> Color.rgb(52, 124, 89)
            else -> Color.rgb(88, 96, 110)
        }

        val sourceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }

        val sourceBadge = TextView(this).apply {
            text = when {
                source.equals("Teams", true) -> "Teams"
                source.equals("Untis", true) -> "Untis"
                imported -> "محتوى مقروء"
                else -> source
            }
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

        val typeBadge = TextView(this).apply {
            text = "${interpretation.emoji} ${interpretation.label}"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(typeColor(interpretation.type))
            setPadding(0, dp(9), 0, 0)
            gravity = Gravity.END
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, dp(8), 0, dp(4))
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
        card.addView(typeBadge)
        card.addView(titleText)
        card.addView(bodyText)

        if (interpretation.type == UpdateType.HOMEWORK || imported) {
            val taskTitle = TextView(this).apply {
                text = "🎯 المطلوب من حمزة"
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setPadding(0, dp(14), 0, dp(6))
                setTextColor(Color.rgb(20, 77, 115))
            }
            card.addView(taskTitle)

            val stepsText = TextView(this).apply {
                val steps = interpretation.steps.ifEmpty {
                    listOf("اقرأ النص المرفق بالكامل وحدد تعليمات المدرس قبل البدء.")
                }
                text = steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n")
                textSize = 15.5f
                gravity = Gravity.END
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBackground(Color.rgb(238, 247, 255), dp(12))
                setTextColor(Color.rgb(31, 55, 73))
            }
            card.addView(stepsText)

            if (interpretation.needsFullText && !imported) {
                card.addView(TextView(this).apply {
                    text = "⚠️ الإشعار مختصر. علشان أقول المطلوب بدقة، افتح الواجب وشاركه للتطبيق أو شارك Screenshot."
                    textSize = 14f
                    gravity = Gravity.END
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = roundedBackground(Color.rgb(255, 243, 218), dp(10))
                    setTextColor(Color.rgb(116, 76, 6))
                })
            }
        }

        card.addView(timeText)

        if (source.equals("Teams", true) || source.equals("Untis", true)) {
            val openButton = Button(this).apply {
                text = "فتح $source"
                setOnClickListener { openSourceApp(source) }
            }
            card.addView(openButton)
        }

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

    private fun processIncomingShare(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return

        when {
            incoming.type?.startsWith("text/") == true -> {
                val text = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) {
                    saveImportedContent("واجب تمت مشاركته", text, "Shared text")
                    showShareStatus("✅ تم قراءة النص المشارك وإضافته إلى الجديد")
                    refreshFeed()
                }
            }

            incoming.type?.startsWith("image/") == true -> {
                @Suppress("DEPRECATION")
                val uri = incoming.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                if (uri != null) {
                    readHomeworkScreenshot(uri)
                }
            }
        }
    }

    private fun readHomeworkScreenshot(uri: Uri) {
        showShareStatus("⏳ جاري قراءة Screenshot الواجب...")
        runCatching { InputImage.fromFilePath(this, uri) }
            .onSuccess { image ->
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener { result ->
                        val text = result.text.trim()
                        if (text.isBlank()) {
                            showShareStatus("⚠️ لم أستطع استخراج نص واضح من الصورة")
                        } else {
                            saveImportedContent("واجب مقروء من Screenshot", text, "Screenshot OCR")
                            showShareStatus("✅ تم قراءة Screenshot وتحويل المطلوب إلى خطوات")
                            refreshFeed()
                        }
                    }
                    .addOnFailureListener {
                        showShareStatus("⚠️ حدث خطأ أثناء قراءة الصورة")
                    }
            }
            .onFailure {
                showShareStatus("⚠️ لم أستطع فتح الصورة")
            }
    }

    private fun saveImportedContent(title: String, text: String, importMethod: String) {
        val item = JSONObject().apply {
            put("source", "Shared")
            put("packageName", "")
            put("title", title)
            put("text", text)
            put("bigText", text)
            put("timestamp", System.currentTimeMillis())
            put("isNew", true)
            put("imported", true)
            put("importMethod", importMethod)
        }
        dataFile.appendText(item.toString() + "\n")
    }

    private fun showShareStatus(message: String) {
        shareStatusText.text = message
        shareStatusText.visibility = View.VISIBLE
    }

    private fun openSourceApp(source: String) {
        val packageName = when {
            source.equals("Teams", true) -> "com.microsoft.teams"
            source.equals("Untis", true) -> "com.grupet.web.app"
            else -> return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            showShareStatus("⚠️ تطبيق $source غير موجود على الجهاز")
        }
    }

    private fun readNotifications(): List<JSONObject> {
        if (!dataFile.exists()) return emptyList()

        return dataFile.readLines()
            .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
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

    private fun typeColor(type: UpdateType): Int {
        return when (type) {
            UpdateType.HOMEWORK -> Color.rgb(36, 91, 156)
            UpdateType.DEADLINE_CHANGE -> Color.rgb(194, 89, 25)
            UpdateType.TIMETABLE_CHANGE -> Color.rgb(112, 70, 156)
            UpdateType.SCHOOL_MESSAGE -> Color.rgb(44, 118, 81)
            UpdateType.UNKNOWN -> Color.DKGRAY
        }
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