package com.hamza.studyhub

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hamza.studyhub.ui.HamzaUi
import com.hamza.studyhub.webuntis.WebUntisClient
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class SchoolBagActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            setPadding(24, 24, 24, 24)
            setBackgroundColor(HamzaUi.bg)
        }
        root.addView(HamzaUi.title(this, "🎒 شنطة حمزة"))
        val tomorrow = nextSchoolDay(LocalDate.now())
        root.addView(HamzaUi.subtitle(this, "مواد ${tomorrow.format(DateTimeFormatter.ofPattern("EEEE d/M", Locale("ar")))} من Untis"))
        val status = TextView(this).apply {
            text = "جاري قراءة جدول Untis…"
            gravity = Gravity.END
            textSize = 16f
            setPadding(8, 24, 8, 24)
        }
        root.addView(status)
        setContentView(root)

        Thread {
            val result = runCatching {
                val config = WebUntisConfigStore.load(this) ?: error("اربط WebUntis أولًا")
                WebUntisClient(config).fetchOwnTimetable(tomorrow, tomorrow)
            }
            runOnUiThread {
                status.text = ""
                result.onSuccess { lessons ->
                    val active = lessons.filterNot { it.cancelled }
                    if (active.isEmpty()) {
                        status.text = "لا توجد حصص مؤكدة في Untis لهذا اليوم."
                    } else {
                        active.distinctBy { it.subject.ifBlank { "lesson-${it.id}" } }.forEach { lesson ->
                            val title = lesson.subject.ifBlank { "حصة" }
                            val detail = buildString {
                                append(formatTime(lesson.startTime)).append("–").append(formatTime(lesson.endTime))
                                if (lesson.room.isNotBlank()) append(" • غرفة ").append(lesson.room)
                                if (lesson.substitutionText.isNotBlank()) append("\n⚠ ").append(lesson.substitutionText)
                            }
                            val card = HamzaUi.card(this)
                            val body = HamzaUi.cardContent(card)
                            body.addView(HamzaUi.title(this, title, 18f))
                            body.addView(HamzaUi.subtitle(this, detail), HamzaUi.marginTop(this, 4))
                            root.addView(card, HamzaUi.marginTop(this, 8))
                        }
                        root.addView(
                            HamzaUi.statusBox(
                                this,
                                "ضع في الشنطة مواد الحصص الظاهرة فقط. الحصص الملغاة مستبعدة تلقائيًا.",
                                0xFFEAF6EF.toInt(),
                                HamzaUi.green
                            ),
                            HamzaUi.marginTop(this, 12)
                        )
                    }
                }.onFailure { status.text = "تعذر تجهيز الشنطة: ${it.message}" }
            }
        }.start()
    }

    private fun nextSchoolDay(from: LocalDate): LocalDate {
        var day = from.plusDays(1)
        while (day.dayOfWeek == DayOfWeek.FRIDAY || day.dayOfWeek == DayOfWeek.SATURDAY) day = day.plusDays(1)
        return day
    }

    private fun formatTime(value: Int): String = "%02d:%02d".format(value / 100, value % 100)
}
