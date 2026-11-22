package com.hamza.studyhub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import com.hamza.studyhub.homework.HomeworkScreenPolicy
import com.hamza.studyhub.homework.HomeworkInterpreter
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
    private enum class FeedMode { TO_DO, ATTENTION, ALL }

    private lateinit var feedContainer: LinearLayout
    private lateinit var newCountText: TextView
    private lateinit var attentionCountText: TextView
    private lateinit var syncStatusText: TextView
    private lateinit var shareStatusText: TextView
    private lateinit var todoButton: MaterialButton
    private lateinit var allButton: MaterialButton
    private lateinit var attentionButton: MaterialButton
    private lateinit var sourcesRow: LinearLayout
    private lateinit var sourcesDetailsText: TextView
    private lateinit var homeworkScroll: ScrollView
    private var feedMode = FeedMode.TO_DO
    private val expandedDetails = mutableSetOf<String>()
    private var pendingTodayFocus: String? = null

    companion object {
        const val EXTRA_TODAY_FOCUS = "com.hamza.studyhub.TODAY_FOCUS"
        private const val STATE_TODAY_FOCUS = "todayFocus"
        fun todayFocusKey(item: JSONObject): String = item.optString("externalId").takeIf { it.isNotBlank() }?.let { "id:$it" }
            ?: "time:${item.optLong("timestamp", 0L)}"
    }

    private val bookStore by lazy { BookLibraryStore(this) }
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        feedMode = if (intent.getBooleanExtra("open_attention", false)) FeedMode.ATTENTION else FeedMode.TO_DO
        pendingTodayFocus = savedInstanceState?.getString(STATE_TODAY_FOCUS)
            ?: intent.takeUnless { it.action == Intent.ACTION_SEND }?.getStringExtra(EXTRA_TODAY_FOCUS)
        setContentView(buildScreen())
        requestNotificationPermissionIfNeeded()
        processIncomingShare(intent)
        scheduleSources()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingTodayFocus?.let { outState.putString(STATE_TODAY_FOCUS, it) }
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
                HamzaUi.dp(this@MainActivity, 16),
                HamzaUi.dp(this@MainActivity, 16),
                HamzaUi.dp(this@MainActivity, 16),
                HamzaUi.dp(this@MainActivity, 12)
            )
            setBackgroundColor(HamzaUi.bg)
        }
        root.addView(HamzaUi.title(this, "واجبات حمزة", 25f))
        root.addView(
            HamzaUi.subtitle(this, "ابدأ بالأقرب تسليمًا، وعلّم على كل خطوة تنتهي منها."),
            HamzaUi.marginTop(this, 3)
        )

        val summaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        newCountText = HamzaUi.softPill(this, "0\nواجب متبقٍّ", 0xFFE8F1FF.toInt(), HamzaUi.blue).apply {
            tag = "homework_open_count"
            gravity = Gravity.CENTER
            minHeight = HamzaUi.dp(this@MainActivity, 54)
        }
        attentionCountText = HamzaUi.softPill(this, "0\nيحتاج تأكيد", 0xFFFFEFE2.toInt(), HamzaUi.amber).apply {
            tag = "homework_review_count"
            gravity = Gravity.CENTER
            minHeight = HamzaUi.dp(this@MainActivity, 54)
        }
        summaryRow.addView(newCountText, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = HamzaUi.dp(this@MainActivity, 5)
        })
        summaryRow.addView(attentionCountText, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = HamzaUi.dp(this@MainActivity, 5)
        })
        root.addView(summaryRow, HamzaUi.marginTop(this, 12))

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        todoButton = HamzaUi.primaryButton(this, "المطلوب").apply {
            tag = "feed_todo"
            setOnClickListener {
                feedMode = FeedMode.TO_DO
                homeworkScroll.scrollTo(0, 0)
                refreshFeed()
            }
        }
        attentionButton = HamzaUi.secondaryButton(this, "مراجعة", HamzaUi.amber).apply {
            tag = "feed_attention"
            setOnClickListener {
                feedMode = FeedMode.ATTENTION
                homeworkScroll.scrollTo(0, 0)
                refreshFeed()
            }
        }
        allButton = HamzaUi.secondaryButton(this, "الكل").apply {
            tag = "feed_all"
            setOnClickListener {
                feedMode = FeedMode.ALL
                homeworkScroll.scrollTo(0, 0)
                refreshFeed()
            }
        }
        listOf(todoButton, attentionButton, allButton).forEachIndexed { index, button ->
            tabs.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index != 0) marginStart = HamzaUi.dp(this@MainActivity, 5)
            })
        }
        root.addView(tabs, HamzaUi.marginTop(this, 10))

        syncStatusText = HamzaUi.subtitle(this, "").apply {
            tag = "homework_sync_status"
            textSize = 12.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(syncStatusText, HamzaUi.marginTop(this, 8))

        shareStatusText = HamzaUi.statusBox(this, "", 0xFFEAF6EF.toInt(), HamzaUi.green).apply {
            visibility = View.GONE
        }
        root.addView(shareStatusText, HamzaUi.marginTop(this, 8))

        feedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, HamzaUi.dp(this@MainActivity, 10), 0, HamzaUi.dp(this@MainActivity, 12))
        }
        homeworkScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(feedContainer)
        }
        root.addView(homeworkScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val toolsButton = HamzaUi.secondaryButton(this, "المصادر والكتب").apply {
            tag = "feed_manage_sources"
            textSize = 13f
            setOnClickListener {
                val show = sourcesRow.visibility != View.VISIBLE
                sourcesRow.visibility = if (show) View.VISIBLE else View.GONE
                sourcesDetailsText.visibility = if (show) View.VISIBLE else View.GONE
                text = if (show) "إخفاء إعدادات المصادر" else "المصادر والكتب"
            }
        }
        root.addView(toolsButton, HamzaUi.marginTop(this, 4))

        sourcesDetailsText = HamzaUi.subtitle(this, "").apply {
            tag = "feed_sources_details"
            visibility = View.GONE
        }
        root.addView(sourcesDetailsText, HamzaUi.marginTop(this, 4))

        sourcesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        sourcesRow.addView(
            HamzaUi.secondaryButton(this, "ربط المصادر").apply {
                tag = "feed_sources"
                setOnClickListener { startActivity(Intent(this@MainActivity, SourceHubActivity::class.java)) }
            }, LinearLayout.LayoutParams(0, -2, 1f)
        )
        sourcesRow.addView(
            HamzaUi.secondaryButton(this, "مكتبة الكتب").apply {
                tag = "feed_books"
                setOnClickListener { startActivity(Intent(this@MainActivity, BookLibraryActivity::class.java)) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = HamzaUi.dp(this@MainActivity, 8)
            })
        root.addView(sourcesRow, HamzaUi.marginTop(this, 4))
        return root
    }

    private fun refreshFeed() {
        val previousScroll = if (::homeworkScroll.isInitialized) homeworkScroll.scrollY else 0
        val allItems = readAndUpgradeNotifications()
        val openCount = allItems.count {
            HomeworkScreenPolicy.isHomework(it) && !HomeworkScreenPolicy.completed(it)
        }
        val reviewCount = allItems.count(HomeworkScreenPolicy::needsReview)
        newCountText.text = "$openCount\nواجب متبقٍّ"
        attentionCountText.text = "$reviewCount\nيحتاج تأكيد"
        updateSyncStatus()

        todoButton.alpha = if (feedMode == FeedMode.TO_DO) 1f else 0.66f
        attentionButton.alpha = if (feedMode == FeedMode.ATTENTION) 1f else 0.66f
        allButton.alpha = if (feedMode == FeedMode.ALL) 1f else 0.66f

        val filter = when (feedMode) {
            FeedMode.TO_DO -> HomeworkScreenPolicy.Filter.TO_DO
            FeedMode.ATTENTION -> HomeworkScreenPolicy.Filter.REVIEW
            FeedMode.ALL -> HomeworkScreenPolicy.Filter.ALL
        }
        val visible = HomeworkScreenPolicy.visible(allItems, filter)
        val focused = pendingTodayFocus?.let { key ->
            visible.firstOrNull { todayFocusKey(it) == key }
        }
        pendingTodayFocus = null
        if (focused != null) expandedDetails += detailKey(focused)
        feedContainer.removeAllViews()
        if (visible.isEmpty()) {
            val message = when (feedMode) {
                FeedMode.TO_DO -> "لا توجد واجبات مفتوحة حاليًا. 🎉\nاستخدم «الكل» لمراجعة التحديثات السابقة."
                FeedMode.ATTENTION -> "لا توجد عناصر معلقة تحتاج تأكيدًا منك."
                FeedMode.ALL -> "لا توجد تحديثات محفوظة بعد. اربط مصادر المدرسة لبدء المتابعة."
            }
            feedContainer.addView(HamzaUi.statusBox(this, message, Color.WHITE, HamzaUi.muted))
            return
        }
        var focusedCard: View? = null
        visible.forEach { item ->
            val card = buildNotificationCard(item)
            feedContainer.addView(card)
            if (item === focused) focusedCard = card
        }
        focusedCard?.let { card ->
            homeworkScroll.post { homeworkScroll.scrollTo(0, card.top) }
        } ?: run {
            if (previousScroll > 0) homeworkScroll.post { homeworkScroll.scrollTo(0, previousScroll) }
        }
    }

    private fun detailKey(item: JSONObject) = item.optString("externalId")
        .ifBlank { item.optString("contentFingerprint") }
        .ifBlank { item.optLong("timestamp").toString() }

    private fun buildNotificationCard(item: JSONObject): View {
        val source = item.optString("source", "School")
        val originalTitle = item.optString("title").ifBlank { "تحديث مدرسي" }
        val bodyText = item.optString("bigText").ifBlank { item.optString("text") }.trim()
        val homework = HomeworkScreenPolicy.isHomework(item)
        val needsReview = HomeworkScreenPolicy.needsReview(item)
        val subject = item.optString("subject")
            .ifBlank { originalTitle.substringAfter("•", "").trim() }
        val heading = if (homework) subject.ifBlank { originalTitle } else originalTitle
        val idTag = item.optLong("timestamp")
        val detailKey = detailKey(item)
        val due = HomeworkScreenPolicy.dueStatus(item.optInt("dueDate", 0))
        val referenceDetected = item.optBoolean("bookReferenceDetected", false)
        val resolved = if (referenceDetected) BookAssignmentResolver(bookStore)
            .resolve(subject, originalTitle, bodyText) else null
        val exercises = item.optJSONArray("bookExercises")?.toStringList().orEmpty()
        val page = item.optInt("bookPage", 0).takeIf { it > 0 } ?: resolved?.reference?.page

        val card = HamzaUi.card(this, radius = 17, padding = 14).apply {
            tag = "homework_card_$idTag"
        }
        val content = HamzaUi.cardContent(card)
        val headline = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        headline.addView(HamzaUi.title(this, heading, 19f).apply {
            tag = "assignment_title_$idTag"
            gravity = Gravity.START
            maxLines = 2
        }, LinearLayout.LayoutParams(-1, -2))

        if (homework) {
            val tone = when (due.tone) {
                HomeworkScreenPolicy.Tone.URGENT -> HamzaUi.danger
                HomeworkScreenPolicy.Tone.WARNING, HomeworkScreenPolicy.Tone.UNKNOWN -> HamzaUi.amber
                HomeworkScreenPolicy.Tone.NORMAL -> HamzaUi.blue
            }
            val fill = when (due.tone) {
                HomeworkScreenPolicy.Tone.URGENT -> 0xFFFCE8E9.toInt()
                HomeworkScreenPolicy.Tone.WARNING, HomeworkScreenPolicy.Tone.UNKNOWN -> 0xFFFFEFE2.toInt()
                HomeworkScreenPolicy.Tone.NORMAL -> 0xFFE8F1FF.toInt()
            }
            headline.addView(HamzaUi.softPill(this, due.text, fill, tone).apply {
                tag = "assignment_due_$idTag"
            }, LinearLayout.LayoutParams(-2, -2).apply {
                topMargin = HamzaUi.dp(this@MainActivity, 5)
                gravity = Gravity.START
            })
        }
        content.addView(headline)

        if (homework && subject.isNotBlank() &&
            !originalTitle.equals("Hausaufgabe • $subject", ignoreCase = true) &&
            !originalTitle.equals(subject, ignoreCase = true)
        ) {
            content.addView(HamzaUi.subtitle(this, originalTitle), HamzaUi.marginTop(this, 3))
        }

        val preview = HomeworkScreenPolicy.preview(bodyText).ifBlank {
            "التعليمات غير متوفرة في التحديث. افتح المصدر للتأكد من المطلوب."
        }
        content.addView(TextView(this).apply {
            tag = "assignment_summary_$idTag"
            text = preview
            textSize = 15.5f
            gravity = Gravity.START
            setTextColor(HamzaUi.ink)
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, HamzaUi.marginTop(this, 9))

        // Provenance is useful at a glance; do not present imported text as verified school data.
        val trustLabel = when (item.optString("evidenceTrust")) {
            "OFFICIAL" -> "معلومة من النظام المدرسي"
            "OFFICIAL_SIGNAL" -> "من إشعار المدرسة"
            "COMMUNITY" -> "معلومة غير رسمية"
            "USER_IMPORTED" -> "مشاركة يدوية"
            else -> "المصدر غير مؤكد"
        }
        val visibleSource = when {
            source.equals("Teams", true) -> "Teams"
            source.equals("Untis", true) -> "WebUntis"
            else -> source
        }
        content.addView(HamzaUi.subtitle(this, "$visibleSource • $trustLabel").apply {
            tag = "assignment_provenance_$idTag"
            textSize = 12.5f
        }, HamzaUi.marginTop(this, 4))

        if (needsReview) {
            val shortReason = HomeworkScreenPolicy.preview(
                item.optString("attentionReason").ifBlank { "راجع التفاصيل في المصدر الرسمي." }, 90
            )
            content.addView(HamzaUi.statusBox(
                this,
                "يحتاج تأكيد • $shortReason",
                0xFFFFF3E6.toInt(),
                HamzaUi.amber
            ).apply { tag = "assignment_warning_$idTag" }, HamzaUi.marginTop(this, 9))
        }

        val checklist = if (homework) item.optJSONArray("taskChecklist") else null
        val steps = if (checklist == null) emptyList() else buildList {
            for (i in 0 until checklist.length()) {
                checklist.optJSONObject(i)?.let { add(i to it) }
            }
        }
        val firstSteps = steps.filterNot { it.second.optBoolean("done", false) }.take(2)
            .ifEmpty { steps.take(2) }
        val firstStepIndices = firstSteps.map { it.first }.toSet()
        if (steps.isNotEmpty()) {
            val progress = item.optInt("progressPercent", 0).coerceIn(0, 100)
            content.addView(HamzaUi.subtitle(
                this, "خطوات الحل  •  $progress% مكتمل"
            ).apply { tag = "assignment_progress_label_$idTag" }, HamzaUi.marginTop(this, 12))
            content.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                tag = "assignment_progress_$idTag"
                max = 100
                this.progress = progress
                progressTintList = ColorStateList.valueOf(HamzaUi.green)
                setMinimumHeight(HamzaUi.dp(this@MainActivity, 5))
            }, HamzaUi.marginTop(this, 4))
            firstSteps.forEach { (index, task) ->
                content.addView(checklistControl(item, index, task), HamzaUi.marginTop(this, 2))
            }
            if (steps.size > firstSteps.size) {
                content.addView(HamzaUi.subtitle(this, "باقي ${steps.size - firstSteps.size} خطوة في التفاصيل"),
                    HamzaUi.marginTop(this, 3))
            }
        }

        val sourceUrl = item.optString("sourceUrl")
        val hasSafeLink = runCatching {
            sourceUrl.toUri().scheme.equals("https", ignoreCase = true)
        }.getOrDefault(false)
        val canOpenSource = hasSafeLink || source.equals("Teams", true) || source.equals("Untis", true)
        val primaryActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (canOpenSource) {
            primaryActions.addView(HamzaUi.primaryButton(this, "فتح الواجب").apply {
                tag = "assignment_source_$idTag"
                setOnClickListener {
                    if (hasSafeLink) startActivity(Intent(Intent.ACTION_VIEW, sourceUrl.toUri()))
                    else openSourceApp(source)
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        if (referenceDetected) {
            primaryActions.addView(HamzaUi.secondaryButton(this, "صوّر الحل").apply {
                tag = "assignment_capture_$idTag"
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, StudentWorkCaptureActivity::class.java).apply {
                        putExtra(StudentWorkCaptureActivity.EXTRA_ASSIGNMENT_FINGERPRINT,
                            item.optString("externalId").ifBlank { item.optString("contentFingerprint") }
                                .ifBlank { item.optLong("timestamp").toString() })
                        putExtra(StudentWorkCaptureActivity.EXTRA_SUBJECT, subject)
                        putExtra(StudentWorkCaptureActivity.EXTRA_BOOK_ID, resolved?.book?.id.orEmpty())
                        putExtra(StudentWorkCaptureActivity.EXTRA_PAGE, page ?: 0)
                        putExtra(StudentWorkCaptureActivity.EXTRA_EXERCISE, exercises.firstOrNull().orEmpty())
                    })
                }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (canOpenSource) marginStart = HamzaUi.dp(this@MainActivity, 7)
            })
        }
        if (primaryActions.isNotEmpty()) content.addView(primaryActions, HamzaUi.marginTop(this, 11))

        // Keep metadata, long source text and administrative actions one tap away.
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (detailKey in expandedDetails) View.VISIBLE else View.GONE
            tag = "assignment_details_panel_$idTag"
        }
        if (bodyText.isNotBlank() && preview != bodyText.replace(Regex("\\s+"), " ").trim()) {
            details.addView(HamzaUi.section(this, "التعليمات الكاملة"))
            details.addView(HamzaUi.subtitle(this, bodyText))
        }
        steps.filterNot { it.first in firstStepIndices }.forEach { (index, task) ->
            details.addView(checklistControl(item, index, task), HamzaUi.marginTop(this, 3))
        }
        if (referenceDetected) {
            val bookMessage = when {
                resolved?.status == ResolutionStatus.RESOLVED && resolved.book != null ->
                    "${resolved.book.title} • صفحة ${page ?: "؟"}" +
                        if (exercises.isNotEmpty()) " • تمرين ${exercises.joinToString(", ")}" else ""
                else -> "الكتاب أو الصفحة يحتاجان ربطًا أو مراجعة."
            }
            details.addView(HamzaUi.subtitle(this, bookMessage), HamzaUi.marginTop(this, 10))
            if (resolved?.book == null) {
                details.addView(HamzaUi.secondaryButton(this, "ربط الكتاب").apply {
                    tag = "assignment_book_$idTag"
                    setOnClickListener { startActivity(Intent(this@MainActivity, BookLibraryActivity::class.java)) }
                }, HamzaUi.marginTop(this, 6))
            }
        }
        if (needsReview) {
            details.addView(HamzaUi.secondaryButton(this, "أكدت المراجعة", HamzaUi.amber).apply {
                tag = "assignment_resolve_$idTag"
                setOnClickListener {
                    resolveAttention(item)
                    refreshFeed()
                }
            }, HamzaUi.marginTop(this, 9))
        } else if (item.optBoolean("isNew", false)) {
            details.addView(HamzaUi.secondaryButton(this, "اطلعت على التحديث").apply {
                tag = "assignment_review_$idTag"
                setOnClickListener {
                    markAsReviewed(item)
                    refreshFeed()
                }
            }, HamzaUi.marginTop(this, 9))
        }
        val sourceLabel = when {
            source.equals("Teams", true) -> "Teams"
            source.equals("Untis", true) -> "WebUntis"
            else -> "مصدر: $source"
        }
        val timeText = formatTime(item.optLong("timestamp"))
        details.addView(HamzaUi.subtitle(this,
            listOf(sourceLabel, timeText).filter { it.isNotBlank() }.joinToString(" • ")
        ), HamzaUi.marginTop(this, 9))

        content.addView(HamzaUi.secondaryButton(this,
            if (detailKey in expandedDetails) "إخفاء التفاصيل" else "عرض التفاصيل"
        ).apply {
            tag = "assignment_details_$idTag"
            textSize = 13f
            setOnClickListener {
                val show = details.visibility != View.VISIBLE
                details.visibility = if (show) View.VISIBLE else View.GONE
                if (show) expandedDetails.add(detailKey) else expandedDetails.remove(detailKey)
                text = if (show) "إخفاء التفاصيل" else "عرض التفاصيل"
            }
        }, HamzaUi.marginTop(this, 8))
        content.addView(details, HamzaUi.marginTop(this, 4))

        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = HamzaUi.dp(this@MainActivity, 9)
        }
        return card
    }

    private fun checklistControl(item: JSONObject, index: Int, task: JSONObject): CheckBox =
        CheckBox(this).apply {
            val stepId = task.optString("id")
            text = task.optString("title")
            textSize = 15f
            minHeight = HamzaUi.dp(this@MainActivity, 48)
            setMinimumHeight(HamzaUi.dp(this@MainActivity, 48))
            setTextColor(HamzaUi.ink)
            isChecked = task.optBoolean("done", false)
            tag = "assignment_task_${item.optLong("timestamp")}_$index"
            contentDescription = "خطوة ${index + 1}: ${task.optString("title")}"
            setOnClickListener {
                if (stepId.isNotBlank()) {
                    updateChecklistStep(item, stepId, isChecked)
                    refreshFeed()
                }
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
        val untisTime = WebUntisConfigStore.lastSync(this)
        val teamsTime = TeamsAuthStore.lastSync(this)
        val latest = maxOf(untisTime, teamsTime)
        val untisError = !WebUntisConfigStore.lastError(this).isNullOrBlank()
        val teamsError = !TeamsAuthStore.lastError(this).isNullOrBlank()
        syncStatusText.text = when {
            untisError || teamsError -> "اتصال المدرسة يحتاج مراجعة • افتح المصادر للتفاصيل"
            latest > 0L -> "آخر مزامنة: ${formatTime(latest)}"
            else -> "لم تتم مزامنة المدرسة بعد • افتح المصادر والكتب للربط"
        }
        val untis = when {
            !WebUntisConfigStore.isConfigured(this) -> "WebUntis غير متصل"
            untisError -> "WebUntis يحتاج مراجعة"
            untisTime > 0 -> "WebUntis محدث ${formatShortTime(untisTime)}"
            else -> "WebUntis متصل"
        }
        val teams = when {
            TeamsAuthStore.account(this) != null && teamsTime > 0 ->
                "Teams محدث ${formatShortTime(teamsTime)}"
            TeamsAuthStore.account(this) != null -> "Teams متصل"
            else -> "Teams غير متصل"
        }
        sourcesDetailsText.text = getString(R.string.source_sync_summary, untis, teams)
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
