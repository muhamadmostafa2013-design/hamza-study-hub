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
import android.widget.CheckBox
import android.text.TextUtils
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
import com.hamza.studyhub.homework.HomeworkFeedPresentation
import com.hamza.studyhub.homework.UpdateType
import com.hamza.studyhub.learning.StudentWorkCaptureActivity
import com.hamza.studyhub.sources.SchoolFeedStore
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
    private enum class FeedMode { HOMEWORK, ATTENTION, ALL }

    private lateinit var feedContainer: LinearLayout
    private lateinit var newCountText: TextView
    private lateinit var attentionCountText: TextView
    private lateinit var syncStatusText: TextView
    private lateinit var shareStatusText: TextView
    private lateinit var homeworkButton: MaterialButton
    private lateinit var allButton: MaterialButton
    private lateinit var attentionButton: MaterialButton
    private lateinit var feedScrollView: ScrollView
    private val expandedItems = mutableSetOf<String>()
    private var feedMode = FeedMode.HOMEWORK

    private val bookStore by lazy { BookLibraryStore(this) }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        feedMode = if (intent.getBooleanExtra("open_attention", false)) FeedMode.ATTENTION else FeedMode.HOMEWORK
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
                HamzaUi.dp(this@MainActivity, 16), HamzaUi.dp(this@MainActivity, 16),
                HamzaUi.dp(this@MainActivity, 16), HamzaUi.dp(this@MainActivity, 10)
            )
            setBackgroundColor(HamzaUi.bg)
        }
        root.addView(HamzaUi.title(this, "واجبات حمزة", 25f))
        root.addView(
            HamzaUi.subtitle(this, "شوف الأقرب للتسليم وابدأ بالخطوة الجاية."),
            HamzaUi.marginTop(this, 2)
        )

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        newCountText = HamzaUi.softPill(this, "0\nمطلوب", 0xFFE8F1FF.toInt(), HamzaUi.blue).apply {
            tag = "summary_open_homework"
            gravity = Gravity.CENTER
            minHeight = HamzaUi.dp(this@MainActivity, 54)
        }
        attentionCountText = HamzaUi.softPill(this, "0\nعاجل", 0xFFFFEFE2.toInt(), HamzaUi.amber).apply {
            tag = "summary_urgent_homework"
            gravity = Gravity.CENTER
            minHeight = HamzaUi.dp(this@MainActivity, 54)
        }
        summary.addView(newCountText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = HamzaUi.dp(this@MainActivity, 5)
        })
        summary.addView(attentionCountText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = HamzaUi.dp(this@MainActivity, 5)
        })
        root.addView(summary, HamzaUi.marginTop(this, 12))

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        homeworkButton = HamzaUi.primaryButton(this, "الواجبات").apply {
            tag = "feed_homework"
            setOnClickListener { feedMode = FeedMode.HOMEWORK; refreshFeed() }
        }
        attentionButton = HamzaUi.secondaryButton(this, "مراجعة").apply {
            tag = "feed_attention"
            setOnClickListener { feedMode = FeedMode.ATTENTION; refreshFeed() }
        }
        allButton = HamzaUi.secondaryButton(this, "الكل").apply {
            tag = "feed_all"
            setOnClickListener { feedMode = FeedMode.ALL; refreshFeed() }
        }
        listOf(homeworkButton, attentionButton, allButton).forEachIndexed { index, button ->
            tabs.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, if (index == 0) 1.1f else 1f).apply {
                if (index > 0) marginStart = HamzaUi.dp(this@MainActivity, 4)
            })
        }
        root.addView(tabs, HamzaUi.marginTop(this, 10))

        shareStatusText = HamzaUi.statusBox(this, "", 0xFFEAF6EF.toInt(), HamzaUi.green).apply {
            visibility = View.GONE
        }
        root.addView(shareStatusText, HamzaUi.marginTop(this, 8))

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        feedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, HamzaUi.dp(this@MainActivity, 10), 0, HamzaUi.dp(this@MainActivity, 10))
        }
        scrollContent.addView(feedContainer)
        scrollContent.addView(HamzaUi.section(this, "أدوات أخرى"))

        val utilityRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        utilityRow.addView(
            HamzaUi.secondaryButton(this, "مصادر المدرسة").apply {
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
        scrollContent.addView(utilityRow, HamzaUi.marginTop(this, 4))
        syncStatusText = HamzaUi.subtitle(this, "")
        scrollContent.addView(syncStatusText, HamzaUi.marginTop(this, 10))

        feedScrollView = ScrollView(this).apply {
            fillViewport = false
            addView(scrollContent)
        }
        root.addView(feedScrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun refreshFeed() {
        val oldScroll = if (::feedScrollView.isInitialized) feedScrollView.scrollY else 0
        val allItems = readAndUpgradeNotifications().sortedByDescending { it.optLong("timestamp") }
        val active = HomeworkFeedPresentation.currentHomework(allItems)
        val attention = HomeworkFeedPresentation.currentAttention(allItems)
        newCountText.text = "${active.size}\nمطلوب"
        attentionCountText.text = "${active.count { HomeworkFeedPresentation.isUrgent(it) }}\nعاجل"
        attentionButton.text = "مراجعة (${attention.size})"
        updateSyncStatus()

        homeworkButton.alpha = if (feedMode == FeedMode.HOMEWORK) 1f else 0.70f
        attentionButton.alpha = if (feedMode == FeedMode.ATTENTION) 1f else 0.70f
        allButton.alpha = if (feedMode == FeedMode.ALL) 1f else 0.70f
        val visible = when (feedMode) {
            FeedMode.HOMEWORK -> active
            FeedMode.ATTENTION -> attention
            FeedMode.ALL -> allItems
        }
        feedContainer.removeAllViews()
        if (visible.isEmpty()) {
            val message = when (feedMode) {
                FeedMode.HOMEWORK -> "مفيش واجبات مفتوحة حاليًا. تقدر تشوف التحديثات القديمة من «الكل»."
                FeedMode.ATTENTION -> "مفيش تحديثات تحتاج مراجعة حاليًا."
                FeedMode.ALL -> "مفيش تحديثات محفوظة لسه."
            }
            feedContainer.addView(HamzaUi.statusBox(this, message, Color.WHITE, HamzaUi.muted))
        } else {
            visible.forEach { feedContainer.addView(buildNotificationCard(it)) }
        }
        feedScrollView.post { feedScrollView.scrollTo(0, oldScroll) }
    }

    private fun buildNotificationCard(item: JSONObject): View {
        val source = item.optString("source", "School")
        val title = item.optString("title").ifBlank { "تحديث مدرسي" }
        val subject = item.optString("subject").ifBlank { title.substringAfter("•", "").trim() }
        val label = subject.ifBlank { title }
        val isHomework = HomeworkFeedPresentation.isHomework(item)
        val needsAttention = HomeworkFeedPresentation.isAttention(item)
        val key = HomeworkFeedPresentation.key(item)
        val expanded = key in expandedItems
        val sourceUrl = item.optString("sourceUrl")
        val canOpenSource = sourceUrl.isNotBlank() || source.equals("Teams", true) || source.equals("Untis", true)
        val card = HamzaUi.card(this, radius = 18, padding = 15).apply {
            tag = "homework_card_$key"
        }
        val body = HamzaUi.cardContent(card)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(HamzaUi.title(this, label, 19f).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val badge = when {
            HomeworkFeedPresentation.isFinished(item) -> Triple("مكتمل", 0xFFEAF6EF.toInt(), HamzaUi.green)
            isHomework && HomeworkFeedPresentation.isUrgent(item) ->
                Triple(HomeworkFeedPresentation.deadlineLabel(item), 0xFFFFE9EA.toInt(), HamzaUi.danger)
            isHomework && HomeworkFeedPresentation.dueRank(item) == 2 ->
                Triple("التسليم بكرة", 0xFFFFEFE2.toInt(), HamzaUi.amber)
            else -> Triple(
                if (isHomework) HomeworkFeedPresentation.deadlineLabel(item) else "تحديث",
                0xFFE8F1FF.toInt(), HamzaUi.blue
            )
        }
        top.addView(HamzaUi.softPill(this, badge.first, badge.second, badge.third))
        body.addView(top)

        val sourceLabel = when {
            source.equals("Teams", true) -> "Teams"
            source.equals("Untis", true) -> "WebUntis"
            else -> "تمت إضافته"
        }
        val metadata = buildString {
            append(sourceLabel)
            if (title != label) append(" • ").append(title.take(52))
            val progress = item.optInt("progressPercent", 0)
            if (isHomework && progress in 1..99) append(" • أنجز ${progress}%")
        }
        body.addView(HamzaUi.subtitle(this, metadata), HamzaUi.marginTop(this, 4))

        val preview = TextView(this).apply {
            text = HomeworkFeedPresentation.preview(item)
            textSize = 15f
            gravity = Gravity.END
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(HamzaUi.ink)
            tag = "assignment_preview_$key"
        }
        body.addView(preview, HamzaUi.marginTop(this, 10))

        val nextStep = if (isHomework && !HomeworkFeedPresentation.isFinished(item))
            HomeworkFeedPresentation.nextStep(item) else null
        if (nextStep != null) {
            body.addView(HamzaUi.subtitle(this, "الخطوة الجاية"), HamzaUi.marginTop(this, 11))
            body.addView(studyCheckbox(item, nextStep, 0), HamzaUi.marginTop(this, 1))
        }
        if (needsAttention) {
            body.addView(HamzaUi.statusBox(
                this,
                "راجع المطلوب • " + item.optString("attentionReason").ifBlank { "المعلومة تحتاج تأكيد." },
                0xFFFFEFE2.toInt(), HamzaUi.amber
            ).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, HamzaUi.marginTop(this, 8))
        }

        val primaryActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (canOpenSource) {
            primaryActions.addView(HamzaUi.primaryButton(this, "فتح الواجب").apply {
                tag = "assignment_source_${item.optLong("timestamp")}"
                setOnClickListener {
                    if (sourceUrl.isNotBlank()) {
                        startActivity(Intent(Intent.ACTION_VIEW, sourceUrl.toUri()))
                    } else {
                        openSourceApp(source)
                    }
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f).apply {
                marginEnd = HamzaUi.dp(this@MainActivity, 4)
            })
        }
        primaryActions.addView(HamzaUi.secondaryButton(
            this, if (expanded) "إخفاء التفاصيل" else "التفاصيل"
        ).apply {
            tag = "assignment_expand_${item.optLong("timestamp")}"
            setOnClickListener {
                if (expanded) expandedItems.remove(key) else expandedItems.add(key)
                refreshFeed()
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(primaryActions, HamzaUi.marginTop(this, 12))

        if (expanded) {
            val raw = item.optString("bigText").ifBlank { item.optString("text") }
            body.addView(HamzaUi.section(this, "التعليمات الكاملة"))
            body.addView(TextView(this).apply {
                text = raw.ifBlank { "لا توجد تفاصيل إضافية. راجع المصدر الرسمي." }
                textSize = 15f
                gravity = Gravity.END
                setTextColor(HamzaUi.ink)
                tag = "assignment_full_text_$key"
            }, HamzaUi.marginTop(this, 4))

            val tasks = item.optJSONArray("taskChecklist")
            if (tasks != null && isHomework && tasks.length() > 0) {
                body.addView(HamzaUi.section(this, "خطوات الواجب"))
                for (i in 0 until tasks.length()) {
                    val task = tasks.optJSONObject(i) ?: continue
                    if (task.optString("id").isBlank() || task === nextStep) continue
                    body.addView(studyCheckbox(item, task, i), HamzaUi.marginTop(this, 2))
                }
            }

            if (isHomework) {
                val referenceDetected = item.optBoolean("bookReferenceDetected", false)
                val resolved = if (referenceDetected)
                    BookAssignmentResolver(bookStore).resolve(subject, title, raw) else null
                val exercises = item.optJSONArray("bookExercises")?.toStringList().orEmpty()
                val page = item.optInt("bookPage", 0).takeIf { it > 0 } ?: resolved?.reference?.page
                if (referenceDetected) {
                    val bookMessage = when {
                        resolved?.status == ResolutionStatus.RESOLVED && resolved.book != null ->
                            "${resolved.book.subject} • ${resolved.book.title} • صفحة ${page ?: "?"}"
                        else -> "الواجب يشير لصفحة في كتاب؛ تأكد من ربط الكتاب الصحيح."
                    }
                    body.addView(HamzaUi.statusBox(this, bookMessage, 0xFFEAF6EF.toInt(), HamzaUi.green),
                        HamzaUi.marginTop(this, 8))
                    if (resolved?.book == null) {
                        body.addView(HamzaUi.secondaryButton(this, "ربط الكتاب").apply {
                            tag = "assignment_book_${item.optLong("timestamp")}"
                            setOnClickListener { startActivity(Intent(this@MainActivity, BookLibraryActivity::class.java)) }
                        }, HamzaUi.marginTop(this, 6))
                    }
                }
                body.addView(HamzaUi.secondaryButton(this, "تصوير حل حمزة").apply {
                    tag = "assignment_capture_${item.optLong("timestamp")}"
                    setOnClickListener {
                        startActivity(Intent(this@MainActivity, StudentWorkCaptureActivity::class.java).apply {
                            putExtra(StudentWorkCaptureActivity.EXTRA_ASSIGNMENT_FINGERPRINT, key)
                            putExtra(StudentWorkCaptureActivity.EXTRA_SUBJECT, subject)
                            putExtra(StudentWorkCaptureActivity.EXTRA_BOOK_ID, resolved?.book?.id.orEmpty())
                            putExtra(StudentWorkCaptureActivity.EXTRA_PAGE, page ?: 0)
                            putExtra(StudentWorkCaptureActivity.EXTRA_EXERCISE, exercises.firstOrNull().orEmpty())
                        })
                    }
                }, HamzaUi.marginTop(this, 8))
            }
            if (needsAttention) {
                body.addView(HamzaUi.secondaryButton(this, "تمت المراجعة والتأكد", HamzaUi.amber).apply {
                    tag = "assignment_resolve_${item.optLong("timestamp")}"
                    setOnClickListener { resolveAttention(item); refreshFeed() }
                }, HamzaUi.marginTop(this, 6))
            } else if (item.optBoolean("isNew", false)) {
                body.addView(HamzaUi.secondaryButton(this, "اطلعت على التحديث").apply {
                    tag = "assignment_review_${item.optLong("timestamp")}"
                    setOnClickListener { markAsReviewed(item); refreshFeed() }
                }, HamzaUi.marginTop(this, 6))
            }
            val createdAt = formatTime(item.optLong("timestamp"))
            if (createdAt.isNotBlank()) {
                body.addView(HamzaUi.subtitle(this, "آخر تحديث: $createdAt"), HamzaUi.marginTop(this, 8))
            }
        }
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = HamzaUi.dp(this@MainActivity, 10) }
        return card
    }

    private fun studyCheckbox(item: JSONObject, task: JSONObject, index: Int): CheckBox =
        CheckBox(this).apply {
            text = task.optString("title")
            textSize = 15f
            maxLines = 3
            gravity = Gravity.CENTER_VERTICAL
            minHeight = HamzaUi.dp(this@MainActivity, 48)
            setTextColor(HamzaUi.ink)
            isChecked = task.optBoolean("done", false)
            tag = "assignment_task_${item.optLong("timestamp")}_$index"
            setOnClickListener {
                updateChecklistStep(item, task.optString("id"), isChecked)
                refreshFeed()
            }
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
        SchoolFeedStore.append(this, AgentOrchestrator.enrich(item))
    }

    private fun showShareStatus(message: String) {
        shareStatusText.text = message
        shareStatusText.visibility = View.VISIBLE
    }

    private fun readAndUpgradeNotifications(): List<JSONObject> =
        SchoolFeedStore.edit(this) { entries ->
            entries.forEachIndexed { index, item ->
                if (item.optString("agentPipelineVersion") != "3.1") {
                    entries[index] = AgentOrchestrator.enrich(item)
                }
            }
            entries.toList()
        }

    private fun markAsReviewed(target: JSONObject) = rewriteTarget(target) { it.put("isNew", false) }

    private fun resolveAttention(target: JSONObject) = rewriteTarget(target) {
        it.put("attentionResolved", true)
        it.put("isNew", false)
    }

    private fun rewriteTarget(target: JSONObject, update: (JSONObject) -> Unit) {
        SchoolFeedStore.edit(this) { entries ->
            entries.forEach { item -> if (sameItem(item, target)) update(item) }
        }
    }

    private fun updateChecklistStep(target: JSONObject, taskId: String, done: Boolean) {
        SchoolFeedStore.edit(this) { entries ->
            val item = entries.firstOrNull { sameItem(it, target) } ?: return@edit
            val tasks = item.optJSONArray("taskChecklist") ?: return@edit
            var completed = 0
            for (index in 0 until tasks.length()) {
                val task = tasks.optJSONObject(index) ?: continue
                if (task.optString("id") == taskId) task.put("done", done)
                if (task.optBoolean("done", false)) completed++
            }
            val percent = if (tasks.length() == 0) 0 else (completed * 100 / tasks.length())
            item.put("progressPercent", percent)
            item.put("progressStatus", when {
                percent >= 100 -> "DONE"
                percent > 0 -> "IN_PROGRESS"
                else -> "NOT_STARTED"
            })
        }
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
