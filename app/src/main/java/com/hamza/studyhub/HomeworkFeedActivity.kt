package com.hamza.studyhub

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hamza.studyhub.agents.AgentOrchestrator
import com.hamza.studyhub.books.BookAssignmentResolver
import com.hamza.studyhub.books.BookLibraryActivity
import com.hamza.studyhub.books.BookLibraryStore
import com.hamza.studyhub.books.ResolutionStatus
import com.hamza.studyhub.learning.StudentWorkCaptureActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Book-aware feed used by the parent. It resolves page references against Hamza's
 * private library and turns a homework card into a learning-evidence capture point.
 */
class HomeworkFeedActivity : AppCompatActivity() {
    private enum class Mode { ALL, ATTENTION }

    private lateinit var feed: LinearLayout
    private lateinit var allButton: Button
    private lateinit var attentionButton: Button
    private var mode = Mode.ALL
    private val dataFile by lazy { File(filesDir, "school_notifications.jsonl") }
    private val books by lazy { BookLibraryStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(24), dp(18), dp(18))
            setBackgroundColor(Color.rgb(246, 247, 251))
        }

        root.addView(TextView(this).apply {
            text = "🧭 متابعة حمزة"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(20, 24, 33))
        })
        root.addView(TextView(this).apply {
            text = "الواجب → الكتاب والصفحة → محاولة حمزة → اتجاه التعلّم"
            textSize = 14.5f
            gravity = Gravity.END
            setPadding(0, dp(3), 0, dp(10))
            setTextColor(Color.DKGRAY)
        })

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        allButton = Button(this).apply {
            text = "الكل"
            setOnClickListener { mode = Mode.ALL; refresh() }
        }
        attentionButton = Button(this).apply {
            text = "⚠️ يحتاج انتباه"
            setOnClickListener { mode = Mode.ATTENTION; refresh() }
        }
        tabs.addView(allButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabs.addView(attentionButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(tabs)

        feed = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(24))
        }
        root.addView(ScrollView(this).apply { addView(feed) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        return root
    }

    private fun refresh() {
        val items = readAndUpgradeItems().sortedByDescending { it.optLong("timestamp") }
        val attention = items.filter {
            it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false)
        }
        allButton.text = "الكل (${items.size})"
        attentionButton.text = "⚠️ يحتاج انتباه (${attention.size})"
        allButton.alpha = if (mode == Mode.ALL) 1f else 0.55f
        attentionButton.alpha = if (mode == Mode.ATTENTION) 1f else 0.55f

        val visible = if (mode == Mode.ATTENTION) attention else items
        feed.removeAllViews()
        if (visible.isEmpty()) {
            feed.addView(TextView(this).apply {
                text = if (mode == Mode.ATTENTION) "✅ مفيش حاجة محتاجة تدخل دلوقتي." else "لسه مفيش واجبات محفوظة."
                textSize = 17f
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(38), dp(20), dp(38))
                setTextColor(Color.GRAY)
            })
            return
        }
        visible.forEach { feed.addView(buildCard(it)) }
    }

    private fun buildCard(item: JSONObject): View {
        val source = item.optString("source", "School")
        val title = item.optString("title").ifBlank { "تحديث" }
        val body = item.optString("bigText").ifBlank { item.optString("text") }
        val subject = item.optString("subject").ifBlank { inferSubject(title) }
        val needsAttention = item.optBoolean("needsAttention", false) && !item.optBoolean("attentionResolved", false)
        val referenceDetected = item.optBoolean("bookReferenceDetected", false)
        val page = item.optInt("bookPage", 0).takeIf { it > 0 }
        val exercises = item.optJSONArray("bookExercises")?.toStringList().orEmpty()
        val resolver = BookAssignmentResolver(books)
        val resolved = if (referenceDetected) resolver.resolve(subject, title, body) else null

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.WHITE, dp(18))
            elevation = dp(2).toFloat()
        }

        card.addView(TextView(this).apply {
            text = buildString {
                append(if (source.equals("Teams", true)) "🟣 Teams" else if (source.equals("Untis", true)) "🟢 Untis" else "🔹 $source")
                val trust = item.optString("verifierStatus")
                if (trust == "VERIFIED") append(" • ✅ رسمي")
            }
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(71, 78, 93))
        })

        card.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, dp(7), 0, dp(3))
            setTextColor(Color.rgb(25, 28, 36))
        })

        val due = item.optInt("dueDate", 0)
        if (due > 0) card.addView(TextView(this).apply {
            text = "📅 التسليم: ${formatUntisDate(due)}"
            textSize = 14.5f
            gravity = Gravity.END
            setTextColor(Color.rgb(130, 73, 22))
        })

        card.addView(TextView(this).apply {
            text = body.ifBlank { "المصدر لم يرسل وصفًا إضافيًا." }
            textSize = 15.5f
            gravity = Gravity.END
            setPadding(0, dp(7), 0, dp(7))
            setTextColor(Color.DKGRAY)
        })

        if (needsAttention) card.addView(TextView(this).apply {
            text = "⚠️ ${item.optString("attentionReason").ifBlank { "يحتاج مراجعة" }}"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundedBackground(Color.rgb(255, 236, 218), dp(12))
            setTextColor(Color.rgb(139, 67, 18))
        })

        val steps = item.optJSONArray("understoodSteps")?.toStringList().orEmpty()
        if (steps.isNotEmpty()) card.addView(TextView(this).apply {
            text = "🎯 المطلوب من حمزة\n" + steps.mapIndexed { index, value -> "${index + 1}. $value" }.joinToString("\n")
            textSize = 15f
            gravity = Gravity.END
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(Color.rgb(238, 247, 255), dp(12))
            setTextColor(Color.rgb(31, 55, 73))
        })

        if (referenceDetected) {
            val bookText = when {
                resolved?.status == ResolutionStatus.RESOLVED && resolved.book != null -> {
                    "📚 ${resolved.book.subject} • ${resolved.book.title}\n📄 صفحة ${page ?: resolved.reference.page ?: "?"}" +
                        if (exercises.isNotEmpty()) " • تمرين ${exercises.joinToString(", ")}" else ""
                }
                books.allBooks().isEmpty() -> "📚 اكتشفت صفحة ${page ?: "?"}، لكن لسه مفيش كتاب مربوط للمادة."
                else -> "📚 اكتشفت صفحة ${page ?: "?"}، والكتاب يحتاج تأكيد."
            }
            card.addView(TextView(this).apply {
                text = bookText
                textSize = 14.5f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBackground(Color.rgb(239, 246, 235), dp(12))
                setTextColor(Color.rgb(49, 104, 65))
            })

            if (resolved?.book == null) {
                card.addView(Button(this).apply {
                    text = "📚 ربط/إضافة الكتاب"
                    setOnClickListener { startActivity(Intent(this@HomeworkFeedActivity, BookLibraryActivity::class.java)) }
                })
            }

            card.addView(Button(this).apply {
                text = "📷 صوّر حل حمزة بعد ما يخلص"
                setOnClickListener {
                    startActivity(Intent(this@HomeworkFeedActivity, StudentWorkCaptureActivity::class.java).apply {
                        putExtra(StudentWorkCaptureActivity.EXTRA_ASSIGNMENT_FINGERPRINT,
                            item.optString("externalId").ifBlank { item.optString("contentFingerprint") }.ifBlank { "${item.optLong("timestamp")}" })
                        putExtra(StudentWorkCaptureActivity.EXTRA_SUBJECT, subject)
                        putExtra(StudentWorkCaptureActivity.EXTRA_BOOK_ID, resolved?.book?.id.orEmpty())
                        putExtra(StudentWorkCaptureActivity.EXTRA_PAGE, page ?: resolved?.reference?.page ?: 0)
                        putExtra(StudentWorkCaptureActivity.EXTRA_EXERCISE, exercises.firstOrNull().orEmpty())
                    })
                }
            })
        }

        val sourceUrl = item.optString("sourceUrl")
        if (sourceUrl.isNotBlank()) card.addView(Button(this).apply {
            text = "فتح الواجب الرسمي"
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))) }
        })

        if (needsAttention) card.addView(Button(this).apply {
            text = "✅ تم التعامل مع التنبيه"
            setOnClickListener { resolveAttention(item); refresh() }
        })
        if (item.optBoolean("isNew", true)) card.addView(Button(this).apply {
            text = "تمت المراجعة"
            setOnClickListener { markReviewed(item); refresh() }
        })

        card.addView(TextView(this).apply {
            text = formatTime(item.optLong("timestamp"))
            textSize = 12.5f
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
            setTextColor(Color.GRAY)
        })

        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
        return card
    }

    private fun readAndUpgradeItems(): List<JSONObject> {
        if (!dataFile.exists()) return emptyList()
        var changed = false
        val items = dataFile.readLines().mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }.map { item ->
            val version = item.optString("agentPipelineVersion")
            if (version != "3.0") {
                changed = true
                AgentOrchestrator.enrich(item)
            } else item
        }
        if (changed) writeItems(items)
        return items
    }

    private fun markReviewed(target: JSONObject) = rewrite(target) { it.put("isNew", false) }
    private fun resolveAttention(target: JSONObject) = rewrite(target) {
        it.put("attentionResolved", true)
        it.put("isNew", false)
    }

    private fun rewrite(target: JSONObject, update: (JSONObject) -> Unit) {
        val items = readAndUpgradeItems().map { item ->
            if (sameItem(item, target)) update(item)
            item
        }
        writeItems(items)
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

    private fun inferSubject(title: String): String = title.substringAfter("•", "").trim()

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun formatTime(timestamp: Long): String = if (timestamp <= 0) "" else
        SimpleDateFormat("dd/MM/yyyy  hh:mm a", Locale.getDefault()).format(Date(timestamp))

    private fun formatUntisDate(value: Int): String = runCatching {
        LocalDate.parse(value.toString(), DateTimeFormatter.BASIC_ISO_DATE)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrElse { value.toString() }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
