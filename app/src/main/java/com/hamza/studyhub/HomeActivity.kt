package com.hamza.studyhub

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hamza.studyhub.agents.StudySupervisorAgent
import com.hamza.studyhub.books.BookLibraryActivity
import com.hamza.studyhub.books.BookLibraryStore
import com.hamza.studyhub.learning.LearningEvidenceStore
import com.hamza.studyhub.teams.TeamsAuthStore
import com.hamza.studyhub.teams.TeamsSyncWorker
import com.hamza.studyhub.ui.HamzaUi
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    private lateinit var newMetric: TextView
    private lateinit var attentionMetric: TextView
    private lateinit var attemptsMetric: TextView
    private lateinit var priorityText: TextView
    private lateinit var agentStatusText: TextView
    private lateinit var sourceText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        scheduleConnectedSources()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(HamzaUi.dp(this@HomeActivity, 18), HamzaUi.dp(this@HomeActivity, 20), HamzaUi.dp(this@HomeActivity, 18), HamzaUi.dp(this@HomeActivity, 30))
            setBackgroundColor(HamzaUi.bg)
        }

        val hero = HamzaUi.card(this, radius = 22, padding = 18).apply {
            setCardBackgroundColor(HamzaUi.navy)
            strokeWidth = 0
        }
        val heroBody = HamzaUi.cardContent(hero)
        heroBody.addView(TextView(this).apply {
            text = getString(R.string.brand_name)
            textSize = 12.5f
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(191, 216, 242))
        })
        heroBody.addView(TextView(this).apply {
            text = "متابعة حمزة"
            textSize = 29f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, HamzaUi.dp(this@HomeActivity, 5), 0, 0)
            setTextColor(Color.WHITE)
        })
        heroBody.addView(TextView(this).apply {
            text = "واجبات • مواعيد • كتب • محاولات • تقدم"
            textSize = 14.5f
            gravity = Gravity.END
            setTextColor(Color.rgb(218, 230, 243))
        })
        content.addView(hero)

        content.addView(HamzaUi.section(this, "نظرة سريعة"))
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        newMetric = metric("الجديد", 0xFFE8F1FF.toInt(), HamzaUi.blue)
        attentionMetric = metric("يحتاج انتباه", 0xFFFFEFE2.toInt(), HamzaUi.amber)
        attemptsMetric = metric("المحاولات", 0xFFEAF6EF.toInt(), HamzaUi.green)
        metrics.addView(newMetric, weighted(4)); metrics.addView(attentionMetric, weighted(4)); metrics.addView(attemptsMetric, weighted(0))
        content.addView(metrics)

        content.addView(HamzaUi.section(this, "أولوية اليوم"))
        priorityText = HamzaUi.statusBox(this, "جاري تجهيز الملخص…", Color.WHITE, HamzaUi.ink).apply {
            background = HamzaUi.rounded(Color.WHITE, HamzaUi.dp(this@HomeActivity, 18).toFloat())
        }
        content.addView(priorityText)

        content.addView(HamzaUi.section(this, "وكيل المذاكرة"))
        agentStatusText = HamzaUi.statusBox(this, "جاري قراءة الأدلة وترتيب الأولويات…", 0xFFEFF5FC.toInt(), HamzaUi.blue)
        content.addView(agentStatusText)
        content.addView(HamzaUi.primaryButton(this, "تشغيل الوكيل الآن").apply {
            tag = "home_study_supervisor"
            setOnClickListener { showStudySupervisor(force = true) }
        }, HamzaUi.marginTop(this, 9))
        content.addView(HamzaUi.subtitle(this,
            "الوكيل يرتب فقط ما هو موجود في المصادر والمحاولات المحفوظة. لا يغيّر واجبات المدرسة ولا يخمّن موعدًا غير موجود."),
            HamzaUi.marginTop(this, 7))

        content.addView(HamzaUi.section(this, "الوصول السريع"))
        content.addView(actionCard("الواجبات", "شاهد الجديد وما يحتاج انتباه وافتح المصدر الرسمي.", "فتح المتابعة") {
            startActivity(Intent(this, MainActivity::class.java))
        })
        content.addView(actionCard("كتب حمزة", "اربط ملفات Deutsch وHSU وMathe ليعرف النظام الصفحة والتمرين.", "فتح المكتبة") {
            startActivity(Intent(this, BookLibraryActivity::class.java))
        })
        content.addView(actionCard("المصادر والمزامنة", "WebUntis وTeams وحالة الإشعارات في مكان واحد.", "إدارة المصادر") {
            startActivity(Intent(this, SourceHubActivity::class.java))
        })
        content.addView(actionCard("ملف التعلّم", "راجع عدد المحاولات المحفوظة وبداية بناء اتجاهات الأداء.", "عرض الحالة") {
            showLearningSummary()
        })

        content.addView(HamzaUi.section(this, "حالة المصادر"))
        sourceText = HamzaUi.statusBox(this, "", 0xFFF0F4F8.toInt(), HamzaUi.muted)
        content.addView(sourceText)

        content.addView(HamzaUi.subtitle(this,
            "المعلومة غير المؤكدة تظل في «يحتاج انتباه» بدل ما النظام يخمّن. الملفات ومحاولات حمزة تبقى خاصة على الجهاز."),
            HamzaUi.marginTop(this, 16))

        return ScrollView(this).apply { isFillViewport = true; addView(content) }
    }

    private fun refresh() {
        val items = readFeed().sortedByDescending { it.optLong("timestamp") }
        val newCount = items.count { it.optBoolean("isNew", true) }
        val attention = items.filter { it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false) }
        val attempts = LearningEvidenceStore(this).attempts()
        newMetric.text = getString(R.string.metric_new, newCount)
        attentionMetric.text = getString(R.string.metric_attention, attention.size)
        attemptsMetric.text = getString(R.string.metric_attempts, attempts.size)

        val top = attention.firstOrNull() ?: items.firstOrNull { it.optBoolean("isNew", true) }
        priorityText.text = when {
            top == null -> "كل شيء هادئ حاليًا. لا يوجد تحديث يحتاج تدخل منك."
            attention.isNotEmpty() -> "يحتاج انتباه الآن\n${top.optString("title").ifBlank { "تحديث مدرسي" }}\n${top.optString("attentionReason").take(150)}"
            else -> "أحدث تحديث\n${top.optString("title").ifBlank { "تحديث مدرسي" }}"
        }

        val report = StudySupervisorAgent(this).runIfNeeded(items)
        agentStatusText.text = report.compactText()
        agentStatusText.setTextColor(when {
            report.confirmations.isNotEmpty() -> HamzaUi.amber
            report.actions.isNotEmpty() -> HamzaUi.blue
            else -> HamzaUi.green
        })

        val untis = if (WebUntisConfigStore.isConfigured(this)) {
            val last = WebUntisConfigStore.lastSync(this)
            if (last > 0) "WebUntis متصل • ${formatTime(last)}" else "WebUntis متصل"
        } else "WebUntis غير متصل"
        val teams = TeamsAuthStore.account(this)?.let {
            val last = TeamsAuthStore.lastSync(this)
            if (last > 0) "Teams متصل • ${formatTime(last)}" else "Teams متصل بالحساب"
        } ?: "Teams جاهز لتسجيل الدخول"
        sourceText.text = getString(R.string.home_source_summary, untis, teams, BookLibraryStore(this).allBooks().size)
    }

    private fun showStudySupervisor(force: Boolean) {
        val report = StudySupervisorAgent(this).runIfNeeded(readFeed(), force)
        agentStatusText.text = report.compactText()
        AlertDialog.Builder(this)
            .setTitle("وكيل المذاكرة")
            .setMessage(report.parentBrief())
            .setPositiveButton("تمام", null)
            .show()
    }

    private fun scheduleConnectedSources() {
        if (WebUntisConfigStore.isConfigured(this)) WebUntisSyncWorker.schedule(this)
        if (TeamsAuthStore.account(this) != null) TeamsSyncWorker.schedule(this)
    }

    private fun metric(label: String, bg: Int, fg: Int): TextView = HamzaUi.softPill(this, "0\n$label", bg, fg).apply {
        textSize = 14f; gravity = Gravity.CENTER; minHeight = HamzaUi.dp(this@HomeActivity, 66)
    }

    private fun weighted(endMargin: Int) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginEnd = HamzaUi.dp(this@HomeActivity, endMargin)
    }

    private fun actionCard(title: String, subtitle: String, buttonText: String, action: () -> Unit): View {
        val card = HamzaUi.card(this)
        val body = HamzaUi.cardContent(card)
        body.addView(HamzaUi.title(this, title, 18f))
        body.addView(HamzaUi.subtitle(this, subtitle), HamzaUi.marginTop(this, 4))
        body.addView(HamzaUi.secondaryButton(this, buttonText).apply {
            tag = "home_${title.hashCode()}"; setOnClickListener { action() }
        }, HamzaUi.marginTop(this, 10))
        card.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = HamzaUi.dp(this@HomeActivity, 10)
        }
        return card
    }

    private fun showLearningSummary() {
        val attempts = LearningEvidenceStore(this).attempts()
        val subjects = attempts.groupingBy { it.subject.ifBlank { "غير محدد" } }.eachCount()
        val body = if (attempts.isEmpty()) "لسه مفيش محاولات مصوّرة. افتح واجب فيه صفحة وصوّر حل حمزة بعد ما يخلص." else buildString {
            append("عندنا ${attempts.size} محاولة محفوظة.\n\n")
            subjects.entries.sortedByDescending { it.value }.forEach { append("${it.key}: ${it.value}\n") }
            append("\nالتحليل المتقدم للأخطاء المتكررة هيتبني على الأدلة دي بدون تصحيح تلقائي لحمزة.")
        }
        AlertDialog.Builder(this).setTitle("ملف تعلّم حمزة").setMessage(body).setPositiveButton("تمام", null).show()
    }

    private fun readFeed(): List<JSONObject> {
        val file = File(filesDir, "school_notifications.jsonl")
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun formatTime(timestamp: Long): String = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(timestamp))
}
