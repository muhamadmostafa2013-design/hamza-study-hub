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
            setPadding(dp(18), dp(24), dp(18), dp(18))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(246, 247, 251))
        }

        root.addView(TextView(this).apply {
            text = "Hamza Study Hub"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(20, 24, 33))
        })

        root.addView(TextView(this).apply {
            text = "Auto Sync + Background Monitor"
            textSize = 16f
            setPadding(0, dp(3), 0, dp(12))
            gravity = Gravity.END
            setTextColor(Color.DKGRAY)
        })

        val counters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        newCountText = TextView(this).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.CENTER
            background = roundedBackground(Color.rgb(225, 238, 255), dp(14))
            setTextColor(Color.rgb(25, 83, 153))
        }
        attentionCountText = TextView(this).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.CENTER
            background = roundedBackground(Color.rgb(255, 236, 218), dp(14))
            setTextColor(Color.rgb(155, 73, 18))
        }
        counters.addView(newCountText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(5) })
        counters.addView(attentionCountText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(5) })
        root.addView(counters)

        syncStatusText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.END
            setPadding(0, dp(9), 0, dp(5))
            setTextColor(Color.rgb(52, 124, 89))
        }
        root.addView(syncStatusText)

        val syncButton = Button(this).apply {
            text = if (WebUntisConfigStore.isConfigured(this@MainActivity)) "🔄 مزامنة Untis الآن" else "🔗 ربط WebUntis"
            setOnClickListener {
                if (WebUntisConfigStore.isConfigured(this@MainActivity)) {
                    WebUntisSyncWorker.syncNow(this@MainActivity)
                    showShareStatus("⏳ طلبت مزامنة WebUntis الآن...")
                } else {
                    startActivity(Intent(this@MainActivity, LaunchActivity::class.java))
                }
            }
        }
        root.addView(syncButton)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(5))
        }
        showAllButton = Button(this).apply {
            text = "🆕 الجديد والكل"
            setOnClickListener {
                feedMode = FeedMode.ALL
                refreshFeed()
            }
        }
        showAttentionButton = Button(this).apply {
            text = "⚠️ يحتاج انتباه"
            setOnClickListener {
                feedMode = FeedMode.ATTENTION
                refreshFeed()
            }
        }
        tabs.addView(showAllButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabs.addView(showAttentionButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabs)

        val monitorHelp = TextView(this).apply {
            text = "🤖 WebUntis يتفحص تلقائيًا في الخلفية تقريبًا كل 15 دقيقة بعد الربط. Teams يُلتقط من الإشعارات تلقائيًا. لو Teams أرسل عنوان الواجب فقط بدون التفاصيل، يفضل ظاهر في «يحتاج انتباه» لحد ما نفتحه مرة واحدة. Screenshot أصبح حل احتياطي فقط."
            textSize = 14.5f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            gravity = Gravity.END
            background = roundedBackground(Color.rgb(255, 247, 222), dp(14))
            setTextColor(Color.rgb(92, 67, 15))
        }
        root.addView(monitorHelp)

        shareStatusText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.END
            setPadding(0, dp(6), 0, dp(4))
            setTextColor(Color.rgb(52, 124, 89))
            visibility = View.GONE
        }
        root.addView(shareStatusText)

        root.addView(Button(this).apply {
            text = "إعداد الوصول لإشعارات Teams وUntis"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        })

        feedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(24))
        }

        root.addView(ScrollView(this).apply { addView(feedContainer) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        return root
    }

    private fun refreshFeed() {
        val allItems = readNotifications().sortedByDescending { it.optLong("timestamp") }
        val newCount = allItems.count { it.optBoolean("isNew", true) }
        val attentionItems = allItems.filter {
            it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false)
        }

        newCountText.text = if (newCount == 0) "✅ لا جديد" else "🆕 جديد: $newCount"
        attentionCountText.text = if (attentionItems.isEmpty()) "✅ لا يحتاج انتباه" else "⚠️ يحتاج انتباه: ${attentionItems.size}"

        updateSyncStatus()
        updateModeButtons()

        val items = if (feedMode == FeedMode.ATTENTION) attentionItems else allItems
        feedContainer.removeAllViews()

        if (items.isEmpty()) {
            feedContainer.addView(TextView(this).apply {
                text = if (feedMode == FeedMode.ATTENTION) {
                    "✅ ممتاز. مفيش حاجة محتاجة تدخل منك حاليًا."
                } else {
                    "لسه مفيش تحديثات محفوظة. فعّل الوصول للإشعارات واربط WebUntis."
                }
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(40), dp(18), dp(40))
                setTextColor(Color.GRAY)
            })
            return
        }

        items.forEach { item -> feedContainer.addView(buildNotificationCard(item)) }
    }

    private fun updateModeButtons() {
        showAllButton.alpha = if (feedMode == FeedMode.ALL) 1f else 0.55f
        showAttentionButton.alpha = if (feedMode == FeedMode.ATTENTION) 1f else 0.55f
    }

    private fun updateSyncStatus() {
        if (!WebUntisConfigStore.isConfigured(this)) {
            syncStatusText.text = "⚪ WebUntis غير مربوط بعد"
            return
        }
        val error = WebUntisConfigStore.lastError(this)
        val last = WebUntisConfigStore.lastSync(this)
        syncStatusText.text = when {
            !error.isNullOrBlank() -> "⚠️ آخر مزامنة واجهت مشكلة: ${error.take(120)}"
            last > 0L -> "✅ WebUntis Auto Sync شغال • آخر مزامنة ${formatTime(last)}"
            else -> "⏳ WebUntis مربوط • في انتظار أول مزامنة"
        }
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
        sourceRow.addView(TextView(this).apply {
            text = if (isNew) "NEW 🆕" else "تمت المراجعة ✓"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (isNew) Color.rgb(190, 35, 45) else Color.GRAY)
            setPadding(dp(10), 0, dp(10), 0)
        })
        sourceRow.addView(TextView(this).apply {
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
        })
        card.addView(sourceRow)

        card.addView(TextView(this).apply {
            val change = item.optString("syncChange")
            text = buildString {
                append("${interpretation.emoji} ${interpretation.label}")
                if (change == "new") append(" • جديد تلقائيًا")
                if (change == "updated") append(" • تم تعديله")
            }
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(typeColor(interpretation.type))
            setPadding(0, dp(9), 0, 0)
            gravity = Gravity.END
        })

        card.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, dp(8), 0, dp(4))
            setTextColor(Color.rgb(25, 28, 36))
        })

        val dueDate = item.optInt("dueDate", 0)
        if (dueDate > 0) {
            card.addView(TextView(this).apply {
                text = "📅 التسليم: ${formatUntisDate(dueDate)}"
                textSize = 14.5f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setTextColor(Color.rgb(130, 73, 22))
            })
        }

        card.addView(TextView(this).apply {
            text = body.ifBlank { "لا يوجد نص إضافي في المصدر." }
            textSize = 16f
            gravity = Gravity.END
            setTextColor(Color.DKGRAY)
        })

        if (needsAttention) {
            card.addView(TextView(this).apply {
                text = "⚠️ يحتاج انتباه\n${attentionReason.ifBlank { "المعلومة تحتاج مراجعة منك." }}"
                textSize = 14.5f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBackground(Color.rgb(255, 236, 218), dp(12))
                setTextColor(Color.rgb(139, 67, 18))
            })
        }

        if (interpretation.type == UpdateType.HOMEWORK || imported) {
            card.addView(TextView(this).apply {
                text = "🎯 المطلوب من حمزة"
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setPadding(0, dp(14), 0, dp(6))
                setTextColor(Color.rgb(20, 77, 115))
            })

            card.addView(TextView(this).apply {
                val steps = interpretation.steps.ifEmpty {
                    listOf("اقرأ النص الكامل وحدد تعليمات المدرس قبل البدء.")
                }
                text = steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n")
                textSize = 15.5f
                gravity = Gravity.END
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBackground(Color.rgb(238, 247, 255), dp(12))
                setTextColor(Color.rgb(31, 55, 73))
            })
        }

        card.addView(TextView(this).apply {
            text = formatTime(timestamp)
            textSize = 13f
            gravity = Gravity.END
            setPadding(0, dp(10), 0, 0)
            setTextColor(Color.GRAY)
        })

        if (source.equals("Teams", true) || source.equals("Untis", true)) {
            card.addView(Button(this).apply {
                text = "فتح $source"
                setOnClickListener { openSourceApp(source) }
            })
        }

        if (needsAttention) {
            card.addView(Button(this).apply {
                text = "✅ تم التعامل مع التنبيه"
                setOnClickListener {
                    resolveAttention(item)
                    refreshFeed()
                }
            })
        }

        if (isNew) {
            card.addView(Button(this).apply {
                text = "تمت المراجعة"
                setOnClickListener {
                    markAsReviewed(item)
                    refreshFeed()
                }
            })
        }

        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }

        return card
    }

    private fun processIncomingShare(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return

        when {
            incoming.type?.startsWith("text/") == true -> {
                val text = incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (text.isNotBlank()) {
                    saveImportedContent("واجب تمت مشاركته", text, "Shared text")
                    showShareStatus("✅ تم قراءة النص المشارك وإضافته")
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
                    .addOnFailureListener { showShareStatus("⚠️ حدث خطأ أثناء قراءة الصورة") }
            }
            .onFailure { showShareStatus("⚠️ لم أستطع فتح الصورة") }
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
        showShareStatus("⚠️ تطبيق $source غير موجود على الجهاز")
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
