package com.hamza.studyhub

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hamza.studyhub.teams.TeamsAuthStore
import com.hamza.studyhub.teams.TeamsConnectionActivity
import com.hamza.studyhub.teams.TeamsSyncWorker
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SourceHubActivity : AppCompatActivity() {
    private lateinit var untisStatus: TextView
    private lateinit var teamsStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(28), dp(18), dp(28))
            setBackgroundColor(Color.rgb(246, 247, 251))
        }

        content.addView(TextView(this).apply {
            text = "Hamza Study Hub"
            textSize = 29f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(20, 24, 33))
        })
        content.addView(TextView(this).apply {
            text = "مصادر المدرسة — مصدر سريع + مصدر موثوق"
            textSize = 15.5f
            gravity = Gravity.END
            setPadding(0, dp(3), 0, dp(16))
            setTextColor(Color.DKGRAY)
        })

        content.addView(sourceCard(
            title = "🟢 WebUntis",
            subtitle = "Auto Sync رسمي للواجبات الموجودة في WebUntis"
        ) {
            untisStatus = it
            Button(this).apply {
                text = "🔄 مزامنة Untis الآن"
                setOnClickListener {
                    if (WebUntisConfigStore.isConfigured(this@SourceHubActivity)) {
                        WebUntisSyncWorker.syncNow(this@SourceHubActivity)
                        untisStatus.text = "⏳ طلبت مزامنة WebUntis الآن..."
                    } else {
                        startActivity(Intent(this@SourceHubActivity, LaunchActivity::class.java))
                    }
                }
            }
        })

        content.addView(sourceCard(
            title = "🟣 Microsoft Teams",
            subtitle = "Notifications للسرعة + Deep Sync لالتقاط الواجبات اللي ما جاش عليها إشعار"
        ) {
            teamsStatus = it
            Button(this).apply {
                text = "ربط / إدارة Teams Deep Sync"
                setOnClickListener {
                    startActivity(Intent(this@SourceHubActivity, TeamsConnectionActivity::class.java))
                }
            }
        })

        content.addView(TextView(this).apply {
            text = "الهدف: لو واجب اتنشر في Teams من غير Notification، الـDeep Sync يلقطه. ولو وصل Notification الأول، يفضل المسار الأسرع ويُدمج مع نفس سجل الواجب بدون تكرار."
            textSize = 14f
            gravity = Gravity.END
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.rgb(255, 247, 222), dp(14))
            setTextColor(Color.rgb(92, 67, 15))
        })

        content.addView(Button(this).apply {
            text = "📚 فتح متابعة واجبات حمزة"
            setOnClickListener {
                startActivity(Intent(this@SourceHubActivity, MainActivity::class.java))
            }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        return ScrollView(this).apply { addView(content) }
    }

    private fun refresh() {
        if (::untisStatus.isInitialized) {
            val last = WebUntisConfigStore.lastSync(this)
            untisStatus.text = when {
                !WebUntisConfigStore.isConfigured(this) -> "⚪ غير مربوط"
                last > 0L -> "✅ مربوط • آخر مزامنة ${formatTime(last)}"
                else -> "🟡 مربوط • في انتظار أول مزامنة"
            }
        }

        if (::teamsStatus.isInitialized) {
            val account = TeamsAuthStore.account(this)
            val last = TeamsAuthStore.lastSync(this)
            val error = TeamsAuthStore.lastError(this)
            teamsStatus.text = when {
                !error.isNullOrBlank() -> "⚠️ آخر محاولة: ${error.take(160)}"
                account != null && last > 0L -> "✅ مربوط بـ $account • آخر Deep Sync ${formatTime(last)}"
                account != null -> "🟡 الحساب مربوط • في انتظار أول Deep Sync"
                TeamsAuthStore.isConfigured(this) -> "🟡 Client ID محفوظ • محتاج تسجيل دخول حساب حمزة"
                else -> "⚪ Deep Sync غير مربوط بعد"
            }

            if (account != null) TeamsSyncWorker.schedule(this)
        }
    }

    private fun sourceCard(
        title: String,
        subtitle: String,
        contentBuilder: (TextView) -> View
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = roundedBackground(Color.WHITE, dp(18))
        elevation = dp(2).toFloat()

        addView(TextView(this@SourceHubActivity).apply {
            text = title
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(25, 28, 36))
        })
        addView(TextView(this@SourceHubActivity).apply {
            text = subtitle
            textSize = 14.5f
            gravity = Gravity.END
            setPadding(0, dp(3), 0, dp(8))
            setTextColor(Color.DKGRAY)
        })

        val status = TextView(this@SourceHubActivity).apply {
            textSize = 14f
            gravity = Gravity.END
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = roundedBackground(Color.rgb(238, 247, 255), dp(12))
            setTextColor(Color.rgb(31, 55, 73))
        }
        addView(status)
        addView(contentBuilder(status))

        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("dd/MM  hh:mm a", Locale.getDefault()).format(Date(timestamp))

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
