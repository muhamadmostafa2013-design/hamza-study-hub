package com.hamza.studyhub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
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
            setPadding(dp(18), dp(24), dp(18), dp(30))
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        content.addView(heroCard())

        content.addView(sectionTitle("نظرة سريعة"))
        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        newCountText = metricCard("الجديد", Color.rgb(232, 241, 255), Color.rgb(31, 86, 155))
        attentionCountText = metricCard("يحتاج انتباه", Color.rgb(255, 239, 224), Color.rgb(157, 76, 22))
        sourcesCountText = metricCard("المصادر", Color.rgb(231, 247, 239), Color.rgb(41, 119, 82))
        metrics.addView(newCountText, weightedParams(dp(4)))
        metrics.addView(attentionCountText, weightedParams(dp(4)))
        metrics.addView(sourcesCountText, weightedParams(0))
        content.addView(metrics)

        content.addView(sectionTitle("أولوية اليوم"))
        focusText = TextView(this).apply {
            textSize = 15.5f
            gravity = Gravity.END
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.WHITE, dp(18))
            elevation = dp(1).toFloat()
            setTextColor(Color.rgb(39, 45, 55))
        }
        content.addView(focusText)

        content.addView(sectionTitle("التنبيهات"))
        content.addView(notificationCard())

        content.addView(sectionTitle("مصادر المدرسة"))
        content.addView(sourceCard(
            title = "WebUntis",
            subtitle = "المصدر الرسمي للواجبات والمواعيد المسجلة في Untis",
            accent = Color.rgb(47, 132, 91)
        ) {
            untisStatus = it
            actionButton("مزامنة Untis الآن", Color.rgb(47, 132, 91)).apply {
                setOnClickListener {
                    if (WebUntisConfigStore.isConfigured(this@SourceHubActivity)) {
                        WebUntisSyncWorker.syncNow(this@SourceHubActivity)
                        untisStatus.text = "جاري طلب مزامنة WebUntis…"
                    } else {
                        startActivity(Intent(this@SourceHubActivity, LaunchActivity::class.java))
                    }
                }
            }
        })

        content.addView(sourceCard(
            title = "Microsoft Teams",
            subtitle = "Notification للسرعة + Deep Sync لالتقاط الواجبات حتى لو لم يصل إشعار",
            accent = Color.rgb(92, 94, 191)
        ) {
            teamsStatus = it
            actionButton("ربط / إدارة Teams Deep Sync", Color.rgb(92, 94, 191)).apply {
                setOnClickListener {
                    startActivity(Intent(this@SourceHubActivity, TeamsConnectionActivity::class.java))
                }
            }
        })

        content.addView(actionButton("فتح مركز متابعة واجبات حمزة", Color.rgb(25, 74, 123)).apply {
            textSize = 16f
            setOnClickListener {
                startActivity(Intent(this@SourceHubActivity, MainActivity::class.java))
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        content.addView(TextView(this).apply {
            text = "Hamza Study Hub يجمع الإشارات من المصادر المختلفة في سجل واحد، ويترك المعلومة غير المؤكدة في «يحتاج انتباه» بدل التخمين."
            textSize = 13.5f
            gravity = Gravity.END
            setPadding(dp(8), dp(16), dp(8), 0)
            setTextColor(Color.rgb(104, 111, 121))
        })

        return ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
    }

    private fun heroCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = roundedBackground(Color.rgb(24, 54, 91), dp(22))
        elevation = dp(3).toFloat()

        addView(TextView(this@SourceHubActivity).apply {
            text = "HAMZA STUDY HUB"
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
            gravity = Gravity.END
            setTextColor(Color.rgb(191, 216, 242))
        })
        addView(TextView(this@SourceHubActivity).apply {
            text = "مركز متابعة حمزة"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, dp(5), 0, dp(4))
            setTextColor(Color.WHITE)
        })
        addView(TextView(this@SourceHubActivity).apply {
            text = "واجبات • مواعيد • تنبيهات • تقدم تعليمي"
            textSize = 14.5f
            gravity = Gravity.END
            setTextColor(Color.rgb(218, 230, 243))
        })
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
    }

    private fun notificationCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = roundedBackground(Color.WHITE, dp(18))
        elevation = dp(1).toFloat()

        notificationStatus = TextView(this@SourceHubActivity).apply {
            textSize = 14.5f
            gravity = Gravity.END
            setPadding(dp(11), dp(9), dp(11), dp(9))
        }
        addView(notificationStatus)

        val actions = LinearLayout(this@SourceHubActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        notificationAction = actionButton("تفعيل إشعارات التطبيق", Color.rgb(25, 74, 123)).apply {
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    refreshNotificationHealth()
                }
            }
        }
        notificationTest = actionButton("اختبار إشعار", Color.rgb(72, 118, 84)).apply {
            setOnClickListener {
                BackgroundAlert.notify(
                    this@SourceHubActivity,
                    "Hamza Study Hub",
                    "التنبيهات شغالة بنجاح. هنبلغك عند اكتشاف واجب جديد أو تعديل مهم.",
                    4199
                )
            }
        }
        actions.addView(notificationAction, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(5) })
        actions.addView(notificationTest, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(5) })
        addView(actions)

        addView(Button(this@SourceHubActivity).apply {
            text = "إعدادات إشعارات Hamza Study Hub"
            isAllCaps = false
            textSize = 13.5f
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                })
            }
        })
    }

    private fun refresh() {
        refreshDashboardMetrics()
        refreshNotificationHealth()

        if (::untisStatus.isInitialized) {
            val last = WebUntisConfigStore.lastSync(this)
            val error = WebUntisConfigStore.lastError(this)
            untisStatus.text = when {
                !WebUntisConfigStore.isConfigured(this) -> "غير مربوط"
                !error.isNullOrBlank() -> "آخر مزامنة واجهت مشكلة: ${error.take(120)}"
                last > 0L -> "متصل • آخر مزامنة ${formatTime(last)}"
                else -> "متصل • في انتظار أول مزامنة"
            }
        }

        if (::teamsStatus.isInitialized) {
            val account = TeamsAuthStore.account(this)
            val last = TeamsAuthStore.lastSync(this)
            val error = TeamsAuthStore.lastError(this)
            teamsStatus.text = when {
                !error.isNullOrBlank() -> "آخر محاولة: ${error.take(160)}"
                account != null && last > 0L -> "متصل بـ $account • آخر Deep Sync ${formatTime(last)}"
                account != null -> "الحساب متصل • في انتظار أول Deep Sync"
                TeamsAuthStore.isConfigured(this) -> "Client ID محفوظ • يحتاج تسجيل دخول حساب حمزة"
                else -> "Deep Sync غير مربوط بعد"
            }
            if (account != null) TeamsSyncWorker.schedule(this)
        }
    }

    private fun refreshDashboardMetrics() {
        val items = readFeed()
        val newCount = items.count { it.optBoolean("isNew", true) }
        val attentionCount = items.count {
            it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false)
        }
        val sourceCount = listOf(
            WebUntisConfigStore.isConfigured(this),
            TeamsAuthStore.account(this) != null
        ).count { it }

        if (::newCountText.isInitialized) newCountText.text = "$newCount\nالجديد"
        if (::attentionCountText.isInitialized) attentionCountText.text = "$attentionCount\nيحتاج انتباه"
        if (::sourcesCountText.isInitialized) sourcesCountText.text = "$sourceCount/2\nمصادر متصلة"

        if (::focusText.isInitialized) {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val urgent = items.filter { item ->
                if (item.optBoolean("completed", false)) return@filter false
                val due = parseDueDate(item.optInt("dueDate", 0)) ?: return@filter false
                !due.isBefore(today) && !due.isAfter(tomorrow)
            }
            val overdue = items.filter { item ->
                if (item.optBoolean("completed", false)) return@filter false
                val due = parseDueDate(item.optInt("dueDate", 0)) ?: return@filter false
                due.isBefore(today)
            }
            focusText.text = when {
                overdue.isNotEmpty() -> "عند حمزة ${overdue.size} واجب متأخر يحتاج مراجعة أولًا."
                urgent.isNotEmpty() -> "عند حمزة ${urgent.size} واجب موعده اليوم أو غدًا. افتح مركز المتابعة لترتيب الأولوية."
                attentionCount > 0 -> "لا يوجد موعد عاجل ظاهر، لكن عندك $attentionCount عنصر يحتاج تأكيد أو مراجعة."
                else -> "الوضع هادئ حاليًا. لا توجد أولوية عاجلة مكتشفة من المصادر المتصلة."
            }
        }
    }

    private fun refreshNotificationHealth() {
        if (!::notificationStatus.isInitialized) return
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            notificationStatus.text = "التنبيهات مفعّلة — التطبيق يقدر ينبهك عند اكتشاف واجب جديد أو تعديل."
            notificationStatus.background = roundedBackground(Color.rgb(232, 247, 238), dp(12))
            notificationStatus.setTextColor(Color.rgb(38, 112, 75))
            notificationAction.visibility = View.GONE
            notificationTest.visibility = View.VISIBLE
        } else {
            notificationStatus.text = "التنبيهات غير مفعّلة للتطبيق نفسه. وصول Hamza Study Hub لإشعارات Teams/Untis لا يعني أنه مسموح له بإرسال إشعار لك."
            notificationStatus.background = roundedBackground(Color.rgb(255, 238, 223), dp(12))
            notificationStatus.setTextColor(Color.rgb(146, 72, 23))
            notificationAction.visibility = View.VISIBLE
            notificationTest.visibility = View.GONE
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun readFeed(): List<JSONObject> {
        val file = File(filesDir, "school_notifications.jsonl")
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            runCatching { JSONObject(line) }.getOrNull()
        }
    }

    private fun parseDueDate(value: Int): LocalDate? {
        if (value <= 0) return null
        return runCatching {
            LocalDate.parse(value.toString(), DateTimeFormatter.BASIC_ISO_DATE)
        }.getOrNull()
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.END
        setPadding(0, dp(18), 0, dp(8))
        setTextColor(Color.rgb(31, 37, 47))
    }

    private fun metricCard(label: String, backgroundColor: Int, textColor: Int): TextView = TextView(this).apply {
        text = "0\n$label"
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(12), dp(8), dp(12))
        background = roundedBackground(backgroundColor, dp(16))
        setTextColor(textColor)
    }

    private fun weightedParams(endMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = endMargin
        }

    private fun sourceCard(
        title: String,
        subtitle: String,
        accent: Int,
        contentBuilder: (TextView) -> View
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = roundedBackground(Color.WHITE, dp(18))
        elevation = dp(1).toFloat()

        addView(TextView(this@SourceHubActivity).apply {
            text = title
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(accent)
        })
        addView(TextView(this@SourceHubActivity).apply {
            text = subtitle
            textSize = 14f
            gravity = Gravity.END
            setPadding(0, dp(3), 0, dp(9))
            setTextColor(Color.rgb(87, 94, 104))
        })

        val status = TextView(this@SourceHubActivity).apply {
            textSize = 14f
            gravity = Gravity.END
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = roundedBackground(Color.rgb(242, 245, 248), dp(12))
            setTextColor(Color.rgb(48, 58, 69))
        }
        addView(status)
        addView(contentBuilder(status))

        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
    }

    private fun actionButton(label: String, color: Int): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(12), dp(9), dp(12), dp(9))
        background = roundedBackground(color, dp(13))
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("dd/MM • hh:mm a", Locale.getDefault()).format(Date(timestamp))

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
