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
import androidx.appcompat.app.AppCompatActivity
import com.hamza.studyhub.ui.HamzaUi
import com.hamza.studyhub.update.AppUpdateManager
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker
import com.hamza.studyhub.teams.TeamsAuthStore
import com.hamza.studyhub.teams.TeamsSyncWorker
import org.json.JSONObject
import java.io.File

class HomeActivity : AppCompatActivity() {
    private lateinit var todayStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        scheduleConnectedSources()
        AppUpdateManager.check(this, silentIfCurrent = true)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(HamzaUi.dp(this@HomeActivity, 18), HamzaUi.dp(this@HomeActivity, 18), HamzaUi.dp(this@HomeActivity, 18), HamzaUi.dp(this@HomeActivity, 34))
            setBackgroundColor(0xFFF7F8FC.toInt())
        }

        val hero = HamzaUi.card(this, radius = 26, padding = 20).apply { setCardBackgroundColor(0xFF173F6B.toInt()); strokeWidth = 0 }
        val heroBody = HamzaUi.cardContent(hero)
        heroBody.addView(TextView(this).apply { text = "HAMZA STUDY HUB  •  🇩🇪"; textSize = 12f; letterSpacing = .08f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xFFC9DCF2.toInt()); gravity = Gravity.END })
        heroBody.addView(TextView(this).apply { text = "👋 Hallo Hamza!"; textSize = 29f; setTypeface(typeface, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.END; setPadding(0, HamzaUi.dp(this@HomeActivity, 8), 0, 0) })
        heroBody.addView(TextView(this).apply { text = "Bereit für heute?  يلا نخلّص يومنا خطوة خطوة ⭐"; textSize = 15f; setTextColor(0xFFE6EFF8.toInt()); gravity = Gravity.END })
        content.addView(hero)

        content.addView(HamzaUi.section(this, "🎯 مهمتك النهارده"))
        val plan = HamzaUi.card(this, radius = 24, padding = 18).apply { setCardBackgroundColor(0xFFFFF3D8.toInt()); strokeColor = 0xFFFFD36A.toInt() }
        val planBody = HamzaUi.cardContent(plan)
        planBody.addView(TextView(this).apply { text = "📚  Mein Plan"; textSize = 24f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xFF173F6B.toInt()); gravity = Gravity.END })
        planBody.addView(TextView(this).apply { text = "خطة حمزة للمذاكرة • راحة + مذاكرة + تجهيز بكرة"; textSize = 15f; setTextColor(0xFF5D6572.toInt()); gravity = Gravity.END; setPadding(0, HamzaUi.dp(this@HomeActivity, 5), 0, HamzaUi.dp(this@HomeActivity, 10)) })
        planBody.addView(HamzaUi.primaryButton(this, "▶  ابدأ خطة حمزة").apply { setOnClickListener { startActivity(Intent(this@HomeActivity, StudyPlanActivity::class.java)) } })
        content.addView(plan)

        content.addView(HamzaUi.section(this, "🏫 Meine Schule"))
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(shortcut("🎒", "Schultasche", "شنطة بكرة", 0xFFE8F4EA.toInt()) { startActivity(Intent(this, SchoolBagActivity::class.java)) }, tileParams(6))
        row1.addView(shortcut("📝", "Hausaufgaben", "الواجبات", 0xFFE9F2FF.toInt()) { startActivity(Intent(this, MainActivity::class.java)) }, tileParams(0))
        content.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(shortcut("📚", "Bücher", "كتب حمزة", 0xFFFFEFE4.toInt()) { startActivity(Intent(this, com.hamza.studyhub.books.BookLibraryActivity::class.java)) }, tileParams(6))
        row2.addView(shortcut("🔄", "Schulquellen", "Untis + Teams", 0xFFF0EBFF.toInt()) { startActivity(Intent(this, SourceHubActivity::class.java)) }, tileParams(0))
        content.addView(row2, HamzaUi.marginTop(this, 10))

        content.addView(HamzaUi.section(this, "⭐ Heute"))
        todayStatus = HamzaUi.statusBox(this, "لحظة… بنجهز يوم حمزة", Color.WHITE, 0xFF263238.toInt())
        content.addView(todayStatus)

        content.addView(HamzaUi.secondaryButton(this, "🔄 فحص تحديث التطبيق").apply {
            setOnClickListener { AppUpdateManager.check(this@HomeActivity, silentIfCurrent = false) }
        }, HamzaUi.marginTop(this, 16))
        content.addView(TextView(this).apply { text = "🇩🇪  Kleine Schritte. Jeden Tag.  •  خطوة صغيرة كل يوم"; textSize = 13.5f; gravity = Gravity.CENTER; setTextColor(0xFF737B87.toInt()) }, HamzaUi.marginTop(this, 18))

        return ScrollView(this).apply { isFillViewport = true; addView(content) }
    }

    private fun shortcut(icon: String, german: String, arabic: String, bg: Int, action: () -> Unit): View {
        val card = HamzaUi.card(this, radius = 22, padding = 15).apply { setCardBackgroundColor(bg); strokeWidth = 0; isClickable = true; isFocusable = true; setOnClickListener { action() } }
        val body = HamzaUi.cardContent(card); body.gravity = Gravity.CENTER
        body.addView(TextView(this).apply { text = icon; textSize = 31f; gravity = Gravity.CENTER })
        body.addView(TextView(this).apply { text = german; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xFF173F6B.toInt()); gravity = Gravity.CENTER })
        body.addView(TextView(this).apply { text = arabic; textSize = 13f; setTextColor(0xFF68717D.toInt()); gravity = Gravity.CENTER })
        return card
    }

    private fun tileParams(endMargin: Int) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = HamzaUi.dp(this@HomeActivity, endMargin) }

    private fun refresh() {
        val items = readFeed().sortedByDescending { it.optLong("timestamp") }
        val attention = items.filter { it.optBoolean("needsAttention", false) && !it.optBoolean("attentionResolved", false) }
        val homework = items.count { it.optString("type").contains("homework", true) || it.optString("title").contains("Hausaufgabe", true) }
        todayStatus.text = when {
            attention.isNotEmpty() -> "🔔 عندك ${attention.size} حاجة محتاجة مراجعة\n📝 $homework واجب ظاهر من المدرسة\nافتح Mein Plan ونرتبهم بهدوء."
            items.isNotEmpty() -> "🎉 Alles gut! التحديثات موجودة ومفيش حاجة عاجلة دلوقتي.\nابدأ Mein Plan لما تكون جاهز."
            else -> "🌤️ يوم هادي! افتح Mein Plan وابدأ بأول خطوة."
        }
    }

    private fun scheduleConnectedSources() {
        if (WebUntisConfigStore.isConfigured(this)) WebUntisSyncWorker.schedule(this)
        if (TeamsAuthStore.account(this) != null) TeamsSyncWorker.schedule(this)
    }

    private fun readFeed(): List<JSONObject> {
        val file = File(filesDir, "school_notifications.jsonl")
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
    }
}
