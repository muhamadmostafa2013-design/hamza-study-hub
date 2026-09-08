package com.hamza.studyhub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hamza.studyhub.homework.HomeworkInterpreter
import com.hamza.studyhub.homework.UpdateType
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private enum class FeedMode { ALL, ATTENTION }

    private lateinit var feedContainer: LinearLayout
    private lateinit var newCountText: TextView
    private lateinit var attentionCountText: TextView
    private lateinit var shareStatusText: TextView
    private lateinit var syncStatusText: TextView
    private lateinit var showAllButton: Button
    private lateinit var showAttentionButton: Button
    private var feedMode = FeedMode.ALL

    private val dataFile by lazy { File(filesDir, "school_notifications.jsonl") }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        feedMode = if (intent.getBooleanExtra("open_attention", false)) FeedMode.ATTENTION else FeedMode.ALL
        setContentView(buildScreen())
        requestNotificationPermissionIfNeeded()
        processIncomingShare(intent)

        if (WebUntisConfigStore.isConfigured(this)) {
            WebUntisSyncWorker.schedule(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_attention", false)) feedMode = FeedMode.ATTENTION
        processIncomingShare(intent)
    }

    override fun onResume() {
        super.onResume()
        if (WebUntisConfigStore.isConfigured(this)) {
            val last = WebUntisConfigStore.lastSync(this)
            if (last == 0L || System.currentTimeMillis() - last > 5 * 60 * 1000L) {
                WebUntisSyncWorker.syncNow(this)
            }
        }
        refreshFeed()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(14))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = roundedBackground(Color.rgb(24, 54, 91), dp(22))
            elevation = dp(3).toFloat()

            addView(TextView(this@MainActivity).apply {
                text = "HAMZA STUDY HUB"
                textSize = 11.5f
                letterSpacing = 0.08f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setTextColor(Color.rgb(184, 210, 237))
            })
            addView(TextView(this@MainActivity).apply {
                text = "متابعة واجبات حمزة"
                textSize = 27f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setPadding(0, dp(4), 0, dp(2))
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@MainActivity).apply {
                text = "كل الواجبات والمواعيد المهمة في مكان واحد"
                textSize = 14.5f
                gravity = Gravity.END
                setTextColor(Color.rgb(220, 231, 243))
            })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        val counters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        newCountText = metricCard(
            background = Color.rgb(232, 241, 255),
            foreground = Color.rgb(31, 86, 155)
        )
        attentionCountText = metricCard(
            background = Color.rgb(255, 239, 224),
            foreground = Color.rgb(157, 76, 22)
        )
        counters.addView(newCountText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(5) })
        counters.addView(attentionCountText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(5) })
        root.addView(counters)

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = outlinedBackground(Color.WHITE, Color.rgb(224, 229, 235), dp(16))

            syncStatusText = TextView(this@MainActivity).apply {
                textSize = 13.5f
                gravity = Gravity.END
                setTextColor(Color.rgb(54, 105, 77))
            }
            addView(syncStatusText)

            addView(styledButton(
                if (WebUntisConfigStore.isConfigured(this@MainActivity)) "مزامنة Untis الآن" else "ربط WebUntis",
                Color.rgb(47, 132, 91)
            ).apply {
                setOnClickListener {
                    if (WebUntisConfigStore.isConfigured(this@MainActivity)) {
                        WebUntisSyncWorker.syncNow(this@MainActivity)
                        showShareStatus("جاري تحديث بيانات WebUntis…")
                    } else {
                        startActivity(Intent(this@MainActivity, LaunchActivity::class.java))
                    }
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        root.addView(TextView(this).apply {
            text = "المتابعة"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, dp(15), 0, dp(7))
            setTextColor(Color.rgb(74, 81, 91))
        })

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        showAllButton = styledButton("الكل والجديد", Color.rgb(25, 74, 123)).apply {
            setOnClickListener {
                feedMode = FeedMode.ALL
                refreshFeed()
            }
        }
        showAttentionButton = styledButton("يحتاج انتباه", Color.rgb(180, 91, 29)).apply {
            setOnClickListener {
                feedMode = FeedMode.ATTENTION
                refreshFeed()
            }
        }
        tabs.addView(showAllButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(5) })
        tabs.addView(showAttentionButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(5) })
        root.addView(tabs)

        shareStatusText = TextView(this).apply {
            textSize = 13.5f
            gravity = Gravity.END
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundedBackground(Color.rgb(232, 247, 239), dp(12))
            setTextColor(Color.rgb(42, 110, 76))
            visibility = View.GONE
        }
        root.addView(shareStatusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        root.addView(Button(this).apply {
            text = "⚙ إعدادات مصادر المدرسة والإشعارات"
            isAllCaps = false
            textSize = 13.5f
            setTextColor(Color.rgb(70, 79, 91))
            background = roundedBackground(Color.rgb(238, 241, 245), dp(12))
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        feedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(24))
        }

        root.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(feedContainer)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        return root
    }

    private fun metricCard(background: Int, foreground: Int): TextView = TextView(this).apply {
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        gravity = Gravity.CENTER
        this.background = roundedBackground(background, dp(16))
        setTextColor(foreground)
    }

    private fun styledButton(label: String, color: Int): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        background = roundedBackground(color, dp(13))
    }

    private fun refreshFeed() {
        val allItems = readNotifications().sortedByDescending { it.optLong("timestamp") }
        val newCount = allItems.count { it.optBoolean("isNew", true) }
        val attentionItems = allItems.filter {
            it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false)
        }

        newCountText.text = if (newCount == 0) "لا جديد" else "الجديد  $newCount"
        attentionCountText.text = if (attentionItems.isEmpty()) "لا يحتاج انتباه" else "يحتاج انتباه  ${attentionItems.size}"

        updateSyncStatus()
        updateModeButtons()

        val items = if (feedMode == FeedMode.ATTENTION) attentionItems else allItems
        feedContainer.removeAllViews()

        if (items.isEmpty()) {
            feedContainer.addView(TextView(this).apply {
                text = if (feedMode == FeedMode.ATTENTION) {
                    "ممتاز — لا توجد عناصر تحتاج تدخلك حاليًا."
                } else {
                    "لا توجد تحديثات محفوظة حتى الآن."
                }
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(42), dp(18), dp(42))
                setTextColor(Color.rgb(115, 122, 132))
            })
            return
        }

        items.forEach { item -> feedContainer.addView(buildNotificationCard(item)) }
    }

    private fun updateModeButtons() {
        showAllButton.alpha = if (feedMode == FeedMode.ALL) 1f else 0.42f
        showAttentionButton.alpha = if (feedMode == FeedMode.ATTENTION) 1f else 0.42f
    }

    private fun updateSyncStatus() {
        if (!WebUntisConfigStore.isConfigured(this)) {
            syncStatusText.text = "WebUntis غير مربوط"
            syncStatusText.setTextColor(Color.rgb(120, 126, 136))
            return
        }
        val error = WebUntisConfigStore.lastError(this)
        val last = WebUntisConfigStore.lastSync(this)
        syncStatusText.text = when {
            !error.isNullOrBlank() -> "آخر تحديث واجه مشكلة: ${error.take(100)}"
            last > 0L -> "WebUntis متصل • آخر تحديث ${formatTime(last)}"
            else -> "WebUntis متصل • في انتظار أول تحديث"
        }
        syncStatusText.setTextColor(
            if (!error.isNullOrBlank()) Color.rgb(166, 86, 32) else Color.rgb(47, 119, 82)
        )
    }

    private fun buildNotificationCard(item: JSONObject): View {
        val source = item.optString("source", "School")
        val title = item.optString("title").ifBlank { "تحديث جديد" }
        val body = item.optString("bigText").ifBlank { item.optString("text") }
        val timestamp = item.optLong("timestamp")
        val isNew = item.optBoolean("isNew", true)
        val imported = item.optBoolean("imported", false)
        val needsAttention = item.optBoolean("needsAttention", false) && !item.optBoolean("attentionResolved", false)
        val attentionReason = item.optString("attentionReason")
        val interpretation = HomeworkInterpreter.interpret(title, body)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = outlinedBackground(Color.WHITE, Color.rgb(228, 232, 237), dp(18))
            elevation = dp(1).toFloat()
        }

        val sourceColor = when {
            source.equals("Teams", true) -> Color.rgb(92, 94, 191)
            source.equals("Untis", true) -> Color.rgb(47, 132, 91)
            else -> Color.rgb(91, 99, 111)
        }

        val sourceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        sourceRow.addView(TextView(this).apply {
            text = if (isNew) "جديد" else "تمت المراجعة"
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (isNew) Color.rgb(184, 50, 57) else Color.rgb(120, 126, 136))
            setPadding(dp(10), 0, dp(10), 0)
        })
        sourceRow.addView(TextView(this).apply {
            text = when {
                source.equals("Teams", true) -> "Teams"
                source.equals("Untis", true) -> "WebUntis"
                imported -> "محتوى مستورد"
                else -> source
            }
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dp(11), dp(5), dp(11), dp(5))
            background = roundedBackground(sourceColor, dp(18))
        })
        card.addView(sourceRow)

        card.addView(TextView(this).apply {
            val change = item.optString("syncChange")
            text = buildString {
                append("${interpretation.emoji} ${interpretation.label}")
                if (change == "new") append(" • جديد")
                if (change == "updated") append(" • تم التعديل")
            }
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(typeColor(interpretation.type))
            setPadding(0, dp(10), 0, 0)
            gravity = Gravity.END
        })

        card.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, dp(6), 0, dp(4))
            setTextColor(Color.rgb(28, 33, 40))
        })

        val dueDate = item.optInt("dueDate", 0)
        if (dueDate > 0) {
            card.addView(TextView(this).apply {
                text = "التسليم  ${formatUntisDate(dueDate)}"
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setPadding(0, 0, 0, dp(6))
                setTextColor(Color.rgb(131, 76, 30))
            })
        }

        card.addView(TextView(this).apply {
            text = body.ifBlank { "لا توجد تفاصيل إضافية في المصدر." }
            textSize = 15.5f
            gravity = Gravity.END
            setTextColor(Color.rgb(70, 76, 86))
        })

        if (needsAttention) {
            card.addView(TextView(this).apply {
                text = "يحتاج انتباه\n${attentionReason.ifBlank { "المعلومة تحتاج مراجعة منك." }}"
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBackground(Color.rgb(255, 241, 228), dp(12))
                setTextColor(Color.rgb(145, 72, 24))
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) })
        }

        if (interpretation.type == UpdateType.HOMEWORK || imported) {
            card.addView(TextView(this).apply {
                text = "المطلوب من حمزة"
                textSize = 15.5f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setPadding(0, dp(12), 0, dp(5))
                setTextColor(Color.rgb(25, 74, 123))
            })

            card.addView(TextView(this).apply {
                val steps = interpretation.steps.ifEmpty {
                    listOf("راجع تعليمات المدرس كاملة قبل البدء.")
                }
                text = steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n")
                textSize = 14.5f
                gravity = Gravity.END
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBackground(Color.rgb(239, 246, 253), dp(12))
                setTextColor(Color.rgb(44, 65, 85))
            })
        }

        card.addView(TextView(this).apply {
            text = formatTime(timestamp)
            textSize = 12.5f
            gravity = Gravity.END
            setPadding(0, dp(10), 0, dp(2))
            setTextColor(Color.rgb(132, 138, 147))
        })

        if (source.equals("Teams", true) || source.equals("Untis", true)) {
            card.addView(styledButton("فتح المصدر", sourceColor).apply {
                setOnClickListener { openSourceApp(source) }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) })
        }

        if (needsAttention) {
            card.addView(styledButton("تم التعامل مع التنبيه", Color.rgb(166, 92, 36)).apply {
                setOnClickListener {
                    resolveAttention(item)
                    refreshFeed()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) })
        }

        if (isNew) {
            card.addView(Button(this).apply {
                text = "تحديد كمراجَع"
                isAllCaps = false
                textSize = 13.5f
                setTextColor(Color.rgb(65, 73, 84))
                background = roundedBackground(Color.rgb(237, 240, 244), dp(12))
                setOnClickListener {
                    markAsReviewed(item)
                    refreshFeed()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) })
        }

        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }

        return card
    }

    private fun processIncomingShare(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return

        when {
            incoming.type?.startsWith("text/") == true -> {
                val text = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) {
                    saveImportedContent("واجب تمت مشاركته", text, "Shared text")
                    showShareStatus("تمت قراءة النص المشارك وإضافته")
                    refreshFeed()
                }
            }

            incoming.type?.startsWith("image/") == true -> {
                @Suppress("DEPRECATION")
                val uri = incoming.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                if (uri != null) readHomeworkScreenshot(uri)
            }
        }
    }

    private fun readHomeworkScreenshot(uri: Uri) {
        showShareStatus("جاري قراءة صورة الواجب…")
        runCatching { InputImage.fromFilePath(this, uri) }
            .onSuccess { image ->
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener { result ->
                        val text = result.text.trim()
                        if (text.isBlank()) {
                            showShareStatus("لم أستطع استخراج نص واضح من الصورة")
                        } else {
                            saveImportedContent("واجب مقروء من Screenshot", text, "Screenshot OCR")
                            showShareStatus("تمت قراءة الصورة وتحويل المطلوب إلى خطوات")
                            refreshFeed()
                        }
                    }
                    .addOnFailureListener { showShareStatus("حدث خطأ أثناء قراءة الصورة") }
            }
            .onFailure { showShareStatus("لم أستطع فتح الصورة") }
    }

    private fun saveImportedContent(title: String, text: String, importMethod: String) {
        val interpretation = HomeworkInterpreter.interpret(title, text)
        val needsAttention = interpretation.type == UpdateType.HOMEWORK && interpretation.needsFullText
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
            put("needsAttention", needsAttention)
            put("attentionReason", if (needsAttention) "النص المقروء لا يحتوي تفاصيل كافية لفهم المطلوب بدقة." else "")
            put("attentionResolved", false)
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
            return
        }

        if (source.equals("Untis", true)) {
            val config = WebUntisConfigStore.load(this)
            if (config != null) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://${config.server}/WebUntis/")))
                return
            }
        }
        showShareStatus("تطبيق $source غير موجود على الجهاز")
    }

    private fun readNotifications(): List<JSONObject> {
        if (!dataFile.exists()) return emptyList()
        return dataFile.readLines().mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
    }

    private fun markAsReviewed(target: JSONObject) {
        rewriteTarget(target) { it.put("isNew", false) }
    }

    private fun resolveAttention(target: JSONObject) {
        rewriteTarget(target) {
            it.put("attentionResolved", true)
            it.put("isNew", false)
        }
    }

    private fun rewriteTarget(target: JSONObject, update: (JSONObject) -> Unit) {
        val updated = readNotifications().map { item ->
            if (sameItem(item, target)) update(item)
            item
        }
        dataFile.writeText(updated.joinToString("\n") { it.toString() } + if (updated.isNotEmpty()) "\n" else "")
    }

    private fun sameItem(a: JSONObject, b: JSONObject): Boolean {
        val aExternal = a.optString("externalId")
        val bExternal = b.optString("externalId")
        if (aExternal.isNotBlank() && bExternal.isNotBlank()) return aExternal == bExternal

        val aKey = a.optString("notificationKey")
        val bKey = b.optString("notificationKey")
        if (aKey.isNotBlank() && bKey.isNotBlank()) return aKey == bKey

        return a.optLong("timestamp") == b.optLong("timestamp") &&
            a.optString("source") == b.optString("source") &&
            a.optString("title") == b.optString("title")
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun typeColor(type: UpdateType): Int = when (type) {
        UpdateType.HOMEWORK -> Color.rgb(36, 91, 156)
        UpdateType.DEADLINE_CHANGE -> Color.rgb(194, 89, 25)
        UpdateType.TIMETABLE_CHANGE -> Color.rgb(112, 70, 156)
        UpdateType.SCHOOL_MESSAGE -> Color.rgb(44, 118, 81)
        UpdateType.UNKNOWN -> Color.DKGRAY
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return SimpleDateFormat("dd/MM/yyyy  hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    private fun formatUntisDate(value: Int): String {
        return runCatching {
            val date = LocalDate.parse(value.toString(), DateTimeFormatter.BASIC_ISO_DATE)
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }.getOrElse { value.toString() }
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun outlinedBackground(fill: Int, stroke: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
