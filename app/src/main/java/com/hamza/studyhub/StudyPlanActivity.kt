package com.hamza.studyhub

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hamza.studyhub.ui.HamzaUi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StudyPlanActivity : AppCompatActivity() {
    data class Task(val time: String, val title: String, val detail: String, val tracked: Boolean = true)

    private val plans = mapOf(
        Calendar.SUNDAY to listOf(
            Task("4:15–4:45", "غداء وتغيير وراحة", "بداية هادئة بعد المدرسة", false),
            Task("4:45–5:15", "حركة وراحة", "بدون مذاكرة", false),
            Task("5:15–5:55", "Deutsch", "قراءة وفهم أو أهم مهمة ألماني"),
            Task("6:05–6:40", "Hausaufgaben", "واجبات المدرسة المطلوبة"),
            Task("7:00–7:35", "Mathematik", "فهم + تدريب قصير على الأخطاء"),
            Task("7:50–8:15", "Goethe German Booster", "نشاط ألماني مناسب للمستوى"),
            Task("8:15–8:30", "تجهيز بكرة", "الشنطة والواجب والكتب")
        ),
        Calendar.MONDAY to listOf(
            Task("4:15–5:15", "راحة وغداء", "وقت استعادة الطاقة", false),
            Task("5:15–5:55", "Mathematik", "المادة الأساسية"),
            Task("6:05–6:40", "Hausaufgaben", "واجبات المدرسة"),
            Task("7:00–7:35", "Erdkunde", "فهم الدرس والمصطلحات"),
            Task("7:50–8:15", "Goethe German Booster", "Hören / Wortschatz"),
            Task("8:15–8:30", "تجهيز بكرة", "الشنطة والكتب")
        ),
        Calendar.TUESDAY to listOf(
            Task("4:15–5:15", "راحة وغداء", "وقت استعادة الطاقة", false),
            Task("5:15–5:55", "Deutsch", "Lesen + Schlüsselwörter + Zusammenfassung"),
            Task("6:05–6:40", "Hausaufgaben", "واجبات المدرسة"),
            Task("7:00–7:35", "Biologie / Science", "فهم ومراجعة"),
            Task("7:50–8:15", "قراءة ألماني", "قراءة قصيرة بصوت واضح"),
            Task("8:15–8:30", "تجهيز بكرة", "الشنطة والكتب")
        ),
        Calendar.WEDNESDAY to listOf(
            Task("4:15–5:15", "راحة وغداء", "وقت استعادة الطاقة", false),
            Task("5:15–5:55", "Mathematik", "حل مركز"),
            Task("6:05–6:40", "Hausaufgaben", "واجبات المدرسة"),
            Task("7:00–7:35", "English", "قراءة وكلمات"),
            Task("7:50–8:15", "Goethe German Booster", "نشاط تفاعلي"),
            Task("8:15–8:30", "مراجعة الأخطاء", "أصعب نقطة ظهرت هذا الأسبوع")
        ),
        Calendar.THURSDAY to listOf(
            Task("4:15–5:15", "راحة وغداء", "وقت استعادة الطاقة", false),
            Task("5:15–5:55", "Deutsch", "مراجعة خفيفة"),
            Task("6:05–6:40", "إنهاء الواجب", "أي مهمة ناقصة"),
            Task("7:00–7:35", "Erdkunde / Biologie", "حسب احتياج الأسبوع"),
            Task("7:50–8:15", "مراجعة الأسبوع", "ما أتقنته وما يحتاج إعادة"),
            Task("8:15–8:30", "تجهيز", "إنهاء اليوم بهدوء")
        ),
        Calendar.FRIDAY to listOf(
            Task("وقت مرن", "راحة أساسية", "يوم أخف بدون ضغط", false),
            Task("30 دقيقة", "نشاط اختياري", "iSchool أو قراءة أو نشاط يحبه")
        ),
        Calendar.SATURDAY to listOf(
            Task("40 دقيقة", "مراجعة أسبوعية", "أهم ما تم دراسته"),
            Task("35 دقيقة", "أصعب نقطة", "إعادة تدريب بدون تكديس"),
            Task("25 دقيقة", "قراءة ألماني", "Lesen / Wortschatz"),
            Task("15 دقيقة", "تجهيز الأسبوع", "كتب وشنطة وأهداف الأسبوع")
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(HamzaUi.dp(this@StudyPlanActivity, 18), HamzaUi.dp(this@StudyPlanActivity, 20), HamzaUi.dp(this@StudyPlanActivity, 18), HamzaUi.dp(this@StudyPlanActivity, 30))
            setBackgroundColor(HamzaUi.bg)
        }
        root.addView(HamzaUi.title(this, "خطة حمزة اليومية", 27f))
        root.addView(HamzaUi.subtitle(this, "من الرجوع 4:15 حتى النوم 9:00 • إنجاز وفهم، مش عدد ساعات"), HamzaUi.marginTop(this, 5))

        val cal = Calendar.getInstance()
        val tasks = plans[cal.get(Calendar.DAY_OF_WEEK)].orEmpty()
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val prefs = getSharedPreferences("study_plan", MODE_PRIVATE)
        val trackedCount = tasks.count { it.tracked }
        val completed = tasks.indices.count { i -> tasks[i].tracked && prefs.getBoolean("$dateKey-$i", false) }
        val percent = if (trackedCount == 0) 100 else completed * 100 / trackedCount

        val status = HamzaUi.statusBox(this, "إنجاز اليوم: $completed/$trackedCount  •  $percent%", Color.WHITE, HamzaUi.ink)
        root.addView(status, HamzaUi.marginTop(this, 16))
        root.addView(HamzaUi.section(this, "مهام اليوم"))

        tasks.forEachIndexed { index, task ->
            val card = HamzaUi.card(this)
            val body = HamzaUi.cardContent(card)
            body.addView(TextView(this).apply {
                text = task.time
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(HamzaUi.blue)
                gravity = Gravity.END
            })
            body.addView(HamzaUi.title(this, task.title, 18f), HamzaUi.marginTop(this, 3))
            body.addView(HamzaUi.subtitle(this, task.detail), HamzaUi.marginTop(this, 3))
            if (task.tracked) {
                body.addView(CheckBox(this).apply {
                    text = "تم الإنجاز والفهم"
                    isChecked = prefs.getBoolean("$dateKey-$index", false)
                    setOnCheckedChangeListener { _, checked ->
                        prefs.edit().putBoolean("$dateKey-$index", checked).apply()
                        recreate()
                    }
                }, HamzaUi.marginTop(this, 7))
            }
            card.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = HamzaUi.dp(this@StudyPlanActivity, 10)
            }
            root.addView(card)
        }

        root.addView(HamzaUi.subtitle(this, "القاعدة: الواجب المطلوب غدًا ← اختبار قريب ← فهم نقطة ضعيفة ← مراجعة ← قراءة. الساعة 8:30 يبدأ الهدوء والنوم 9:00."), HamzaUi.marginTop(this, 12))
        return ScrollView(this).apply { isFillViewport = true; addView(root) }
    }
}
