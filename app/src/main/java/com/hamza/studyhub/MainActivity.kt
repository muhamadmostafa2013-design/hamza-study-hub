package com.hamza.studyhub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isNotEmpty
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hamza.studyhub.agents.AgentOrchestrator
import com.hamza.studyhub.books.BookAssignmentResolver
import com.hamza.studyhub.books.BookLibraryActivity
import com.hamza.studyhub.books.BookLibraryStore
import com.hamza.studyhub.books.ResolutionStatus
import com.hamza.studyhub.homework.HomeworkInterpreter
import com.hamza.studyhub.homework.UpdateType
import com.hamza.studyhub.learning.StudentWorkCaptureActivity
import com.hamza.studyhub.teams.TeamsAuthStore
import com.hamza.studyhub.teams.TeamsSyncWorker
import com.hamza.studyhub.ui.HamzaUi
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker
import org.json.JSONArray
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
    private lateinit var syncStatusText: TextView
    private lateinit var shareStatusText: TextView
    private lateinit var allButton: MaterialButton
    private lateinit var attentionButton: MaterialButton
    private var feedMode = FeedMode.ALL

    private val dataFile by lazy { File(filesDir, "school_notifications.jsonl") }
    private val bookStore by lazy { BookLibraryStore(this) }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        feedMode = if (intent.getBooleanExtra("open_attention", false)) FeedMode.ATTENTION else FeedMode.ALL
        setContentView(buildScreen())
        requestNotificationPermissionIfNeeded()
        processIncomingShare(intent)
        scheduleSources()
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
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(
                HamzaUi.dp(this@MainActivity, 18),
                HamzaUi.dp(this@MainActivity, 20),
                HamzaUi.dp(this@MainActivity, 18),
                HamzaUi.dp(this@MainActivity, 16)
            )
            setBackgroundColor(HamzaUi.bg)
        }
        root.addView(HamzaUi.title(this, "الواجبات"))
        root.addView(
            HamzaUi.subtitle(this, "كل تحديث مدرسي في سجل واحد، مع فصل الواضح عن اللي يحتاج تدخل منك."),
            HamzaUi.marginTop(this, 4)
        )

        val summaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        newCountText = HamzaUi.softPill(this, "0\nجديد", 0xFFE8F1FF.toInt(), HamzaUi.blue).apply {
            gravity = Gravity.CENTER
            minHeight = HamzaUi.dp(this@MainActivity, 62)
        }
        attentionCountText = HamzaUi.softPill(this, "0\nيحتاج انتباه", 0xFFFFEFE2.toInt(), HamzaUi.amber).apply {
            gravity = Gravity.CENTER
            minHeight = HamzaUi.dp(this@MainActivity, 62)
        }
        summaryRow.addView(
            newCountText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = HamzaUi.dp(this@MainActivity, 5)
            }
        )
        summaryRow.addView(
            attentionCountText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = HamzaUi.dp(this@MainActivity, 5)
            }
        )
        root.addView(summaryRow, HamzaUi.marginTop(this, 14))

        syncStatusText = HamzaUi.statusBox(this, "", 0xFFF0F4F8.toInt(), HamzaUi.muted)
        root.addView(syncStatusText, HamzaUi.marginTop(this, 10))

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        allButton = HamzaUi.primaryButton(this, "الكل والجديد").apply {
            tag = "feed_all"
            setOnClickListener {
                feedMode = FeedMode.ALL
                refreshFeed()
            }
        }
        attentionButton = HamzaUi.secondaryButton(this, "يحتاج انتباه", HamzaUi.amber).apply {
            tag = "feed_attention"
            setOnClickListener {
                feedMode = FeedMode.ATTENTION
                refreshFeed()
            }
        }
        tabs.addView(
            allButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = HamzaUi.dp(this@MainActivity, 5)
            }
        )
        tabs.addView(
            attentionButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = HamzaUi.dp(this@MainActivity, 5)
            }
        )
        root.addView(tabs, HamzaUi.marginTop(this, 10))

        shareStatusText = HamzaUi.statusBox(this, "", 0xFFEAF6EF.toInt(), HamzaUi.green).apply {
            visibility = View.GONE
        }
        root.addView(shareStatusText, HamzaUi.marginTop(this, 8))

        val utilityRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        utilityRow.addView(
            HamzaUi.secondaryButton(this, "المصادر").apply {
                tag = "feed_sources"
                setOnClickListener { startActivity(Intent(this@MainActivity, SourceHubActivity::class.java)) }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = HamzaUi.dp(this@MainActivity, 5)
            }
        )
        utilityRow.addView(
            HamzaUi.secondaryButton(this, "الكتب").apply {
                tag = "feed_books"
                setOnClickListener { startActivity(Intent(this@MainActivity, BookLibraryActivity::class.java)) }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = HamzaUi.dp(this@MainActivity, 5)
            }
        )
        root.addView(utilityRow, HamzaUi.marginTop(this, 8))

        feedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, HamzaUi.dp(this@MainActivity, 12), 0, HamzaUi.dp(this@MainActivity, 24))
        }
        root.addView(
            ScrollView(this).apply { addView(feedContainer) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        return root
    }

    private fun refreshFeed() {
        val allItems = readAndUpgradeNotifications().sortedByDescending { it.optLong("timestamp") }
        val attention = allItems.filter {
            it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false)
        }
        val newCount = allItems.count { it.optBoolean("isNew", true) }
        newCountText.text = getString(R.string.metric_new, newCount)
        attentionCountText.text = getString(R.string.metric_attention, attention.size)
        updateSyncStatus()
        allButton.alpha = if (feedMode == FeedMode.ALL) 1f else 0.72f
        attentionButton.alpha = if (feedMode == FeedMode.ATTENTION) 1f else 0.72f

        val visible = if (feedMode == FeedMode.ATTENTION) attention else allItems
        feedContainer.removeAllViews()
        if (visible.isEmpty()) {
            feedContainer.addView(
                HamzaUi.statusBox(
                    this,
                    if (feedMode == FeedMode.ATTENTION) "لا يوجد شيء يحتاج تدخل منك حاليًا." else "لا توجد تحديثات محفوظة بعد.",
                    Color.WHITE,
                    HamzaUi.muted
                )
            )
            return
        }
        visible.forEach { feedContainer.addView(buildNotificationCard(it)) }
    }

    private fun buildNotificationCard(item: JSONObject): View {
        val source = item.optString("source", "School")
        val title = item.optString("title").ifBlank { "تحديث جديد" }
        val bodyText = item.optString("bigText").ifBlank { item.optString("text") }
        val interpretation = HomeworkInterpreter.interpret(title, bodyText)
        val needsAttention = item.optBoolean("needsAttention", false) && !item.optBoolean("attentionResolved", false)
        val subject = item.optString("subject").ifBlank { title.substringAfter("•", "").trim() }
        val referenceDetected = item.optBoolean("bookReferenceDetected", false)
        val resolved = if (referenceDetected) BookAssignmentResolver(bookStore).resolve(subject, title, bodyText) else null
        val exercises = item.optJSONArray("bookExercises")?.toStringList().orEmpty()
        val page = item.optInt("bookPage", 0).takeIf { it > 0 } ?: resolved?.reference?.page

        val card = HamzaUi.card(this)
        val body = HamzaUi.cardContent(card)
        val sourceColor = when {
            source.equals("Teams", true) -> HamzaUi.purple
            source.equals("Untis", true) -> HamzaUi.green
            else -> HamzaUi.muted
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(
            HamzaUi.softPill(
                this,
                if (source.equals("Teams", true)) "Teams" else if (source.equals("Untis", true)) "Untis" else source,
                Color.argb(22, Color.red(sourceColor), Color.green(sourceColor), Color.blue(sourceColor)),
                sourceColor
            ),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (item.optBoolean("isNew", true)) {
            topRow.addView(HamzaUi.softPill(this, "جديد", 0xFFFFE9EA.toInt(), HamzaUi.danger))
        }
        body.addView(topRow)

        body.addView(HamzaUi.title(this, title, 18.5f), HamzaUi.marginTop(this, 10))
        val due = item.optInt("dueDate", 0)
        if (due > 0) {
            body.addView(HamzaUi.subtitle(this, "التسليم: ${formatUntisDate(due)}"), HamzaUi.marginTop(this, 3))
        }
        body.addView(TextView(this).apply {
            text = bodyText.ifBlank { "المصدر لم يرسل وصفًا إضافيًا." }
            textSize = 15.5f
            gravity = Gravity.END
            setTextColor(HamzaUi.ink)
        }, HamzaUi.marginTop(this, 8))

        if (needsAttention) {
            body.addView(
                HamzaUi.statusBox(
                    this,
                    item.optString("attentionReason").ifBlank { "هذه المعلومة تحتاج مراجعة منك." },
                    0xFFFFEFE2.toInt(),
                    HamzaUi.amber
                ),
                HamzaUi.marginTop(this, 10)
            )
        }

        val steps = item.optJSONArray("understoodSteps")?.toStringList().orEmpty()
        if (steps.isNotEmpty() && interpretation.type == UpdateType.HOMEWORK) {
            body.addView(
                HamzaUi.statusBox(
                    this,
                    "المطلوب من حمزة\n" + steps.mapIndexed { index, step -> "${index + 1}. $step" }.joinToString("\n"),
                    0xFFEFF5FC.toInt(),
                    HamzaUi.blue
                ),
                HamzaUi.marginTop(this, 10)
            )
        }

        if (referenceDetected) {
            val bookMessage = when {
                resolved?.status == ResolutionStatus.RESOLVED && resolved.book != null ->
                    "${resolved.book.subject} • ${resolved.book.title}\nصفحة ${page ?: "?"}" +
                        if (exercises.isNotEmpty()) " • تمرين ${exercises.joinToString(", ")}" else ""
                bookStore.allBooks().isEmpty() -> "تم اكتشاف صفحة ${page ?: "?"}، لكن لم تتم إضافة كتاب لهذه المادة بعد."
                else -> "تم اكتشاف صفحة ${page ?: "?"}، والكتاب يحتاج تأكيد."
            }
            body.addView(
                HamzaUi.statusBox(this, bookMessage, 0xFFEAF6EF.toInt(), HamzaUi.green),
                HamzaUi.marginTop(this, 10)
            )
            if (resolved?.book == null) {
                body.addView(HamzaUi.secondaryButton(this, "ربط الكتاب").apply {
                    tag = "assignment_book_${item.optLong("timestamp")}"
                    setOnClickListener { startActivity(Intent(this@MainActivity, BookLibraryActivity::class.java)) }
                }, HamzaUi.marginTop(this, 8))
            }
            body.addView(HamzaUi.primaryButton(this, "تصوير حل حمزة بعد الانتهاء").apply {
                tag = "assignment_capture_${item.optLong("timestamp")}"
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, StudentWorkCaptureActivity::class.java).apply {
                        putExtra(
                            StudentWorkCaptureActivity.EXTRA_ASSIGNMENT_FINGERPRINT,
                            item.optString("externalId")
                                .ifBlank { item.optString("contentFingerprint") }
                                .ifBlank { item.optLong("timestamp").toString() }
                        )
                        putExtra(StudentWorkCaptureActivity.EXTRA_SUBJECT, subject)
                        putExtra(StudentWorkCaptureActivity.EXTRA_BOOK_ID, resolved?.book?.id.orEmpty())
                        putExtra(StudentWorkCaptureActivity.EXTRA_PAGE, page ?: 0)
                        putExtra(StudentWorkCaptureActivity.EXTRA_EXERCISE, exercises.firstOrNull().orEmpty())
                    })
                }
            }, HamzaUi.marginTop(this, 8))
        }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sourceUrl = item.optString("sourceUrl")
        val canOpenSource = sourceUrl.isNotBlank() || source.equals("Teams", true) || source.equals("Untis", true)
        if (canOpenSource) {
            actions.addView(HamzaUi.secondaryButton(this, "فتح المصدر").apply {
                tag = "assignment_source_${item.optLong("timestamp")}"
                setOnClickListener {
                    if (sourceUrl.isNotBlank()) {
                        startActivity(Intent(Intent.ACTION_VIEW, sourceUrl.toUri()))
                    } else {
                        openSourceApp(source)
                    }
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = HamzaUi.dp(this@MainActivity, 4)
            })
        }
        if (needsAttention) {
            actions.addView(HamzaUi.secondaryButton(this, "تم التعامل", HamzaUi.amber).apply {
                tag = "assignment_resolve_${item.optLong("timestamp")}"
                setOnClickListener {
                    resolveAttention(item)
                    refreshFeed()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = HamzaUi.dp(this@MainActivity, 4)
            })
        } else if (item.optBoolean("isNew", true)) {
            actions.addView(HamzaUi.secondaryButton(this, "تمت المراجعة").apply {
                tag = "assignment_review_${item.optLong("timestamp")}"
                setOnClickListener {
                    markAsReviewed(item)
                    refreshFeed()
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = HamzaUi.dp(this@MainActivity, 4)
            })
        }
        if (actions.isNotEmpty()) {
            body.addView(actions, HamzaUi.marginTop(this, 10))
        }

        body.addView(HamzaUi.subtitle(this, formatTime(item.optLong("timestamp"))), HamzaUi.marginTop(this, 8))
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = HamzaUi.dp(this@MainActivity, 10)
        }
        return card
    }

    private fun processIncomingShare(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return
        when {
            incoming.type?.startsWith("text/") == true ->
                incoming.getStringExtra(Intent.EXTRA_TEXT).orEmpty().takeIf { it.isNotBlank() }?.let {
                    saveImportedContent("واجب تمت مشاركته", it, "Shared text")
                    showShareStatus("تمت إضافة النص المشارك.")
                    refreshFeed()
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
        runCatching { InputImage.fromFilePath(this, uri) }.onSuccess { image ->
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener { result ->
                    val text = result.text.trim()
                    if (text.isBlank()) {
                        showShareStatus("لم أستطع استخراج نص واضح من الصورة.")
                    } else {
                        saveImportedContent("واجب مقروء من صورة", text, "Screenshot OCR")
                        showShareStatus("تمت قراءة الصورة وإضافتها.")
                        refreshFeed()
                    }
                }
                .addOnFailureListener { showShareStatus("حدث خطأ أثناء قراءة الصورة.") }
        }.onFailure { showShareStatus("تعذر فتح الصورة.") }
    }

    private fun saveImportedContent(title: String, text: String, importMethod: String) {
        val interpretation = HomeworkInterpreter.interpret(title, text)
        val item = JSONObject().apply {
            put("source", "Shared")
            put("title", title)
            put("text", text)
            put("bigText", text)
            put("timestamp", System.currentTimeMillis())
            put("isNew", true)
            put("imported", true)
            put("importMethod", importMethod)
            put("needsAttention", interpretation.type == UpdateType.HOMEWORK && interpretation.needsFullText)
            put("attentionReason", if (interpretation.needsFullText) "النص لا يحتوي تفاصيل كافية لفهم المطلوب بدقة." else "")
            put("attentionResolved", false)
        }
        dataFile.appendText(AgentOrchestrator.enrich(item).toString() + "\n")
    }

    private fun showShareStatus(message: String) {
        shareStatusText.text = message
        shareStatusText.visibility = View.VISIBLE
    }

    private fun readAndUpgradeNotifications(): List<JSONObject> {
        if (!dataFile.exists()) return emptyList()
        var changed = false
        val items = dataFile.readLines()
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .map { item ->
                if (item.optString("agentPipelineVersion") != "3.1") {
                    changed = true
                    AgentOrchestrator.enrich(item)
                } else item
            }
        if (changed) writeItems(items)
        return items
    }

    private fun markAsReviewed(target: JSONObject) = rewriteTarget(target) { it.put("isNew", false) }

    private fun resolveAttention(target: JSONObject) = rewriteTarget(target) {
        it.put("attentionResolved", true)
        it.put("isNew", false)
    }

    private fun rewriteTarget(target: JSONObject, update: (JSONObject) -> Unit) {
        val updated = readAndUpgradeNotifications().map { item ->
            if (sameItem(item, target)) update(item)
            item
        }
        writeItems(updated)
    }

    private fun writeItems(items: List<JSONObject>) {
        dataFile.writeText(items.joinToString("\n") { it.toString() } + if (items.isNotEmpty()) "\n" else "")
    }

    private fun sameItem(a: JSONObject, b: JSONObject): Boolean {
        val ae = a.optString("externalId")
        val be = b.optString("externalId")
        if (ae.isNotBlank() && be.isNotBlank()) return ae == be
        val ak = a.optString("notificationKey")
        val bk = b.optString("notificationKey")
        if (ak.isNotBlank() && bk.isNotBlank()) return ak == bk
        return a.optLong("timestamp") == b.optLong("timestamp") && a.optString("title") == b.optString("title")
    }

    private fun updateSyncStatus() {
        val untis = when {
            !WebUntisConfigStore.isConfigured(this) -> "WebUntis غير متصل"
            !WebUntisConfigStore.lastError(this).isNullOrBlank() -> "WebUntis يحتاج مراجعة"
            WebUntisConfigStore.lastSync(this) > 0 -> "WebUntis محدث ${formatShortTime(WebUntisConfigStore.lastSync(this))}"
            else -> "WebUntis متصل"
        }
        val teams = when {
            TeamsAuthStore.account(this) != null && TeamsAuthStore.lastSync(this) > 0 ->
                "Teams محدث ${formatShortTime(TeamsAuthStore.lastSync(this))}"
            TeamsAuthStore.account(this) != null -> "Teams متصل"
            else -> "Teams Deep Sync غير متصل"
        }
        syncStatusText.text = getString(R.string.source_sync_summary, untis, teams)
    }

    private fun scheduleSources() {
        if (WebUntisConfigStore.isConfigured(this)) WebUntisSyncWorker.schedule(this)
        if (TeamsAuthStore.account(this) != null) TeamsSyncWorker.schedule(this)
    }

    private fun openSourceApp(source: String) {
        val packageName = when {
            source.equals("Teams", true) -> "com.microsoft.teams"
            source.equals("Untis", true) -> "com.grupet.web.app"
            else -> return
        }
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it)
            return
        }
        if (source.equals("Untis", true)) {
            WebUntisConfigStore.load(this)?.let {
                startActivity(Intent(Intent.ACTION_VIEW, "https://${it.server}/WebUntis/".toUri()))
                return
            }
        }
        showShareStatus("تطبيق $source غير موجود على الجهاز.")
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun formatTime(timestamp: Long): String =
        if (timestamp <= 0) "" else SimpleDateFormat("dd/MM/yyyy • hh:mm a", Locale.getDefault()).format(Date(timestamp))

    private fun formatShortTime(timestamp: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

    private fun formatUntisDate(value: Int): String = runCatching {
        LocalDate.parse(value.toString(), DateTimeFormatter.BASIC_ISO_DATE)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrElse { value.toString() }
}
