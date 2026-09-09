package com.hamza.studyhub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import com.hamza.studyhub.monitor.BackgroundAlert
import com.hamza.studyhub.teams.TeamsAuthStore
import com.hamza.studyhub.teams.TeamsConnectionActivity
import com.hamza.studyhub.teams.TeamsSyncWorker
import com.hamza.studyhub.ui.HamzaUi
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class SourceHubActivity : AppCompatActivity() {
    private lateinit var untisStatus: TextView
    private lateinit var teamsStatus: TextView
    private lateinit var newCountText: TextView
    private lateinit var attentionCountText: TextView
    private lateinit var sourcesCountText: TextView
    private lateinit var focusText: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var notificationAction: Button
    private lateinit var notificationTest: Button

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshNotificationHealth()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(
                HamzaUi.dp(this@SourceHubActivity, 18),
                HamzaUi.dp(this@SourceHubActivity, 20),
                HamzaUi.dp(this@SourceHubActivity, 18),
                HamzaUi.dp(this@SourceHubActivity, 30)
            )
            setBackgroundColor(HamzaUi.bg)
        }
        content.addView(HamzaUi.title(this, "المصادر والمزامنة"))
        content.addView(
            HamzaUi.subtitle(this, "راقب صحة WebUntis وTeams والتنبيهات من مكان واحد."),
            HamzaUi.marginTop(this, 4)
        )

        content.addView(HamzaUi.section(this, "نظرة سريعة"))
        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        newCountText = metric("الجديد", 0xFFE8F1FF.toInt(), HamzaUi.blue)
        attentionCountText = metric("يحتاج انتباه", 0xFFFFEFE2.toInt(), HamzaUi.amber)
        sourcesCountText = metric("المصادر", 0xFFEAF6EF.toInt(), HamzaUi.green)
        metrics.addView(newCountText, weight(4))
        metrics.addView(attentionCountText, weight(4))
        metrics.addView(sourcesCountText, weight(0))
        content.addView(metrics)

        content.addView(HamzaUi.section(this, "أولوية اليوم"))
        focusText = HamzaUi.statusBox(this, "", Color.WHITE, HamzaUi.ink)
        content.addView(focusText)

        content.addView(HamzaUi.section(this, "التنبيهات"))
        content.addView(notificationCard())

        content.addView(HamzaUi.section(this, "مصادر المدرسة"))
        content.addView(
            sourceCard(
                "WebUntis",
                "المصدر الرسمي للواجبات والمواعيد المسجلة في Untis",
                HamzaUi.green
            ) {
                untisStatus = it
                HamzaUi.secondaryButton(this, "مزامنة Untis الآن", HamzaUi.green).apply {
                    tag = "source_untis_sync"
                    setOnClickListener {
                        if (WebUntisConfigStore.isConfigured(this@SourceHubActivity)) {
                            WebUntisSyncWorker.syncNow(this@SourceHubActivity)
                            untisStatus.text = getString(R.string.untis_sync_requested)
                        } else {
                            startActivity(Intent(this@SourceHubActivity, LaunchActivity::class.java))
                        }
                    }
                }
            }
        )
        content.addView(
            sourceCard(
                "Microsoft Teams",
                "إشعارات للسرعة + Deep Sync لالتقاط الواجبات حتى لو لم يصل إشعار",
                HamzaUi.purple
            ) {
                teamsStatus = it
                HamzaUi.secondaryButton(this, "إدارة Teams Deep Sync", HamzaUi.purple).apply {
                    tag = "source_teams_manage"
                    setOnClickListener {
                        startActivity(Intent(this@SourceHubActivity, TeamsConnectionActivity::class.java))
                    }
                }
            }
        )

        content.addView(HamzaUi.primaryButton(this, "فتح الواجبات").apply {
            tag = "source_open_feed"
            setOnClickListener { startActivity(Intent(this@SourceHubActivity, MainActivity::class.java)) }
        }, HamzaUi.marginTop(this, 4))
        return ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
    }

    private fun notificationCard(): View {
        val card = HamzaUi.card(this)
        val body = HamzaUi.cardContent(card)
        notificationStatus = HamzaUi.statusBox(this, "", 0xFFF0F4F8.toInt(), HamzaUi.muted)
        body.addView(notificationStatus)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        notificationAction = HamzaUi.primaryButton(this, "تفعيل التنبيهات").apply {
            tag = "notifications_enable"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    refreshNotificationHealth()
                }
            }
        }
        notificationTest = HamzaUi.secondaryButton(this, "اختبار إشعار", HamzaUi.green).apply {
            tag = "notifications_test"
            setOnClickListener {
                BackgroundAlert.notify(
                    this@SourceHubActivity,
                    getString(R.string.app_name),
                    "التنبيهات تعمل بنجاح.",
                    4199
                )
            }
        }
        row.addView(
            notificationAction,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = HamzaUi.dp(this@SourceHubActivity, 5)
            }
        )
        row.addView(
            notificationTest,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = HamzaUi.dp(this@SourceHubActivity, 5)
            }
        )
        body.addView(row, HamzaUi.marginTop(this, 9))
        body.addView(HamzaUi.secondaryButton(this, "إعدادات إشعارات التطبيق").apply {
            tag = "notifications_settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                })
            }
        }, HamzaUi.marginTop(this, 8))
        return card
    }

    private fun sourceCard(title: String, subtitle: String, accent: Int, builder: (TextView) -> View): View {
        val card = HamzaUi.card(this)
        val body = HamzaUi.cardContent(card)
        body.addView(HamzaUi.title(this, title, 18f).apply { setTextColor(accent) })
        body.addView(HamzaUi.subtitle(this, subtitle), HamzaUi.marginTop(this, 3))
        val status = HamzaUi.statusBox(this, "", 0xFFF0F4F8.toInt(), HamzaUi.muted)
        body.addView(status, HamzaUi.marginTop(this, 9))
        body.addView(builder(status), HamzaUi.marginTop(this, 9))
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = HamzaUi.dp(this@SourceHubActivity, 10)
        }
        return card
    }

    private fun refresh() {
        refreshMetrics()
        refreshNotificationHealth()
        if (::untisStatus.isInitialized) {
            val last = WebUntisConfigStore.lastSync(this)
            val error = WebUntisConfigStore.lastError(this)
            untisStatus.text = when {
                !WebUntisConfigStore.isConfigured(this) -> "غير متصل"
                !error.isNullOrBlank() -> "آخر مزامنة واجهت مشكلة: ${error.take(120)}"
                last > 0 -> "متصل • آخر مزامنة ${formatTime(last)}"
                else -> "متصل • في انتظار أول مزامنة"
            }
        }
        if (::teamsStatus.isInitialized) {
            val account = TeamsAuthStore.account(this)
            val last = TeamsAuthStore.lastSync(this)
            val error = TeamsAuthStore.lastError(this)
            teamsStatus.text = when {
                !error.isNullOrBlank() -> "آخر محاولة: ${error.take(150)}"
                account != null && last > 0 -> "متصل • آخر Deep Sync ${formatTime(last)}"
                account != null -> "الحساب متصل • في انتظار أول Deep Sync"
                else -> "جاهز لتسجيل دخول حساب حمزة"
            }
            if (account != null) TeamsSyncWorker.schedule(this)
        }
    }

    private fun refreshMetrics() {
        val items = readFeed()
        val newCount = items.count { it.optBoolean("isNew", true) }
        val attention = items.count {
            it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false)
        }
        val sourceCount = listOf(
            WebUntisConfigStore.isConfigured(this),
            TeamsAuthStore.account(this) != null
        ).count { it }
        if (::newCountText.isInitialized) newCountText.text = getString(R.string.metric_new, newCount)
        if (::attentionCountText.isInitialized) attentionCountText.text = getString(R.string.metric_attention, attention)
        if (::sourcesCountText.isInitialized) sourcesCountText.text = getString(R.string.metric_sources, sourceCount)
        if (::focusText.isInitialized) {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val overdue = items.count {
                !it.optBoolean("completed", false) &&
                    parseDueDate(it.optInt("dueDate", 0))?.isBefore(today) == true
            }
            val urgent = items.count {
                val due = parseDueDate(it.optInt("dueDate", 0))
                !it.optBoolean("completed", false) &&
                    due != null && !due.isBefore(today) && !due.isAfter(tomorrow)
            }
            focusText.text = when {
                overdue > 0 -> "$overdue واجب متأخر يحتاج مراجعة أولًا."
                urgent > 0 -> "$urgent واجب موعده اليوم أو غدًا."
                attention > 0 -> "$attention عنصر يحتاج تأكيد أو مراجعة."
                else -> "لا توجد أولوية عاجلة مكتشفة حاليًا."
            }
        }
    }

    private fun refreshNotificationHealth() {
        if (!::notificationStatus.isInitialized) return
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        notificationStatus.text = if (granted) {
            "التنبيهات مفعّلة للتطبيق."
        } else {
            "التنبيهات غير مفعّلة للتطبيق نفسه."
        }
        notificationStatus.setTextColor(if (granted) HamzaUi.green else HamzaUi.amber)
        notificationAction.visibility = if (granted) View.GONE else View.VISIBLE
        notificationTest.visibility = if (granted) View.VISIBLE else View.GONE
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun readFeed(): List<JSONObject> {
        val file = File(filesDir, "school_notifications.jsonl")
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun parseDueDate(value: Int): LocalDate? =
        if (value <= 0) null else runCatching {
            LocalDate.parse(value.toString(), DateTimeFormatter.BASIC_ISO_DATE)
        }.getOrNull()

    private fun metric(label: String, bg: Int, fg: Int) =
        HamzaUi.softPill(this, "0\n$label", bg, fg).apply {
            minHeight = HamzaUi.dp(this@SourceHubActivity, 64)
            gravity = Gravity.CENTER
        }

    private fun weight(end: Int) =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = HamzaUi.dp(this@SourceHubActivity, end)
        }

    private fun formatTime(timestamp: Long) =
        SimpleDateFormat("dd/MM • hh:mm a", Locale.getDefault()).format(Date(timestamp))
}
