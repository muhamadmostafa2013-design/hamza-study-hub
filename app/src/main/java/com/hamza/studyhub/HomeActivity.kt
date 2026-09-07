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
import com.hamza.studyhub.books.BookLibraryActivity
import com.hamza.studyhub.books.BookLibraryStore
import com.hamza.studyhub.learning.LearningEvidenceStore
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker
import org.json.JSONObject
import java.io.File

/** Parent-first dashboard. Complex agents stay behind a very small set of actions. */
class HomeActivity : AppCompatActivity() {
    private lateinit var summaryText: TextView
    private lateinit var sourceStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshSummary()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(26), dp(18), dp(28))
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
            text = "مركز متابعة تعلّم حمزة"
            textSize = 16f
            gravity = Gravity.END
            setPadding(0, dp(2), 0, dp(14))
            setTextColor(Color.DKGRAY)
        })

        summaryText = TextView(this).apply {
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = roundedBackground(Color.WHITE, dp(18))
            setTextColor(Color.rgb(31, 55, 73))
        }
        content.addView(summaryText)

        sourceStatusText = TextView(this).apply {
            textSize = 14.5f
            gravity = Gravity.END
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.rgb(232, 246, 238), dp(14))
            setTextColor(Color.rgb(39, 104, 73))
        }
        content.addView(sourceStatusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        content.addView(sectionTitle("اليوم"))
        content.addView(actionCard(
            title = "🧭 متابعة الواجبات",
            subtitle = "الجديد • يحتاج انتباه • المطلوب من حمزة • الكتاب والصفحة",
            buttonText = "فتح المتابعة"
        ) { startActivity(Intent(this, HomeworkFeedActivity::class.java)) })

        content.addView(actionCard(
            title = "📚 كتب حمزة",
            subtitle = "اربط Deutsch / HSU / Mathe بالصفحات والتمارين مرة واحدة",
            buttonText = "فتح المكتبة"
        ) { startActivity(Intent(this, BookLibraryActivity::class.java)) })

        content.addView(sectionTitle("التعلّم مش مجرد Done / Not Done"))
        content.addView(actionCard(
            title = "📈 ملف التعلّم",
            subtitle = "المحاولات المصوّرة والأخطاء المتكررة هتتحول هنا إلى اتجاهات حقيقية مع الوقت",
            buttonText = "عرض الحالة الحالية"
        ) {
            val attempts = LearningEvidenceStore(this).attempts().size
            android.app.AlertDialog.Builder(this)
                .setTitle("ملف تعلّم حمزة")
                .setMessage(if (attempts == 0) {
                    "لسه مفيش محاولات مصوّرة. لما واجب يحتوي صفحة/تمرين هنظهر زر تصوير حل حمزة ونبدأ نبني خط التعلّم."
                } else {
                    "عندنا $attempts محاولة محفوظة كأدلة تعلّم. تحليل الاتجاهات التفصيلي هو الخطوة التالية."
                })
                .setPositiveButton("تمام", null)
                .show()
        })

        content.addView(sectionTitle("المزامنة"))
        content.addView(Button(this).apply {
            text = "🔄 مزامنة WebUntis الآن"
            setOnClickListener {
                if (WebUntisConfigStore.isConfigured(this@HomeActivity)) {
                    WebUntisSyncWorker.syncNow(this@HomeActivity)
                    sourceStatusText.text = "⏳ طلبت مزامنة WebUntis الآن..."
                } else {
                    startActivity(Intent(this@HomeActivity, LaunchActivity::class.java))
                }
            }
        })

        content.addView(TextView(this).apply {
            text = "المبدأ: نجمع الدليل من المدرسة، نفهم المطلوب، نتابع محاولة حمزة، ثم نقيس هل نقطة الضعف اتحسنت — من غير اختراع واجبات أو تصحيح تلقائي يلغّي دوره."
            textSize = 13.5f
            gravity = Gravity.END
            setPadding(0, dp(16), 0, 0)
            setTextColor(Color.GRAY)
        })

        return ScrollView(this).apply { addView(content) }
    }

    private fun refreshSummary() {
        val items = readFeed()
        val newCount = items.count { it.optBoolean("isNew", true) }
        val attentionCount = items.count {
            it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false)
        }
        val bookCount = BookLibraryStore(this).allBooks().size
        val attemptCount = LearningEvidenceStore(this).attempts().size

        summaryText.text = buildString {
            append("حمزة الآن\n")
            append("🆕 $newCount جديد   •   ⚠️ $attentionCount يحتاج انتباه\n")
            append("📚 $bookCount كتب   •   📷 $attemptCount محاولات محفوظة")
        }

        sourceStatusText.text = if (WebUntisConfigStore.isConfigured(this)) {
            val last = WebUntisConfigStore.lastSync(this)
            if (last > 0) "✅ WebUntis مربوط وAuto Sync شغال • Teams notification monitor يظل مسارًا سريعًا" else
                "✅ WebUntis مربوط • في انتظار أول مزامنة"
        } else {
            "⚪ WebUntis غير مربوط بعد"
        }
    }

    private fun readFeed(): List<JSONObject> {
        val file = File(filesDir, "school_notifications.jsonl")
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun sectionTitle(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.END
        setPadding(0, dp(18), 0, dp(7))
        setTextColor(Color.rgb(25, 28, 36))
    }

    private fun actionCard(
        title: String,
        subtitle: String,
        buttonText: String,
        action: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = roundedBackground(Color.WHITE, dp(18))
            elevation = dp(2).toFloat()

            addView(TextView(this@HomeActivity).apply {
                text = title
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setTextColor(Color.rgb(25, 28, 36))
            })
            addView(TextView(this@HomeActivity).apply {
                text = subtitle
                textSize = 14.5f
                gravity = Gravity.END
                setPadding(0, dp(4), 0, dp(7))
                setTextColor(Color.DKGRAY)
            })
            addView(Button(this@HomeActivity).apply {
                text = buttonText
                setOnClickListener { action() }
            })

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
