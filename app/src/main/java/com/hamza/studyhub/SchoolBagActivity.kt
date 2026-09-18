package com.hamza.studyhub

import android.content.Intent
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
import com.hamza.studyhub.books.BookLibraryStore
import com.hamza.studyhub.ui.HamzaUi
import com.hamza.studyhub.webuntis.WebUntisClient
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class SchoolBagActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private val bagChecks = mutableListOf<CheckBox>()
    private val bagPrefs by lazy { getSharedPreferences("hamza_school_bag", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(
                HamzaUi.dp(this@SchoolBagActivity, 18),
                HamzaUi.dp(this@SchoolBagActivity, 18),
                HamzaUi.dp(this@SchoolBagActivity, 18),
                HamzaUi.dp(this@SchoolBagActivity, 32)
            )
            setBackgroundColor(HamzaUi.bg)
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        })

        loadInstantBag()
    }

    private fun loadInstantBag() {
        val cached = readCachedTimetable()
        val target = nextDateFromCache(cached) ?: nextSchoolDay(LocalDate.now())
        val cachedForDay = cached.filter { it.date == target.toBasicInt() }

        if (cachedForDay.isNotEmpty()) {
            renderBag(
                target = target,
                lessons = cachedForDay,
                syncMessage = "⚡ ظهرت فورًا من آخر نسخة محفوظة من Untis"
            )
        } else {
            renderLoading(target)
        }

        refreshFromWebUntis(target, hadCache = cachedForDay.isNotEmpty())
    }

    private fun refreshFromWebUntis(target: LocalDate, hadCache: Boolean) {
        val config = WebUntisConfigStore.load(this)
        if (config == null) {
            if (!hadCache) renderConnectionNeeded(target)
            return
        }

        WebUntisSyncWorker.syncNow(this)

        Thread {
            val result = runCatching {
                WebUntisClient(config).fetchOwnTimetable(target, target)
            }
            runOnUiThread {
                result.onSuccess { fresh ->
                    renderBag(
                        target = target,
                        lessons = fresh,
                        syncMessage = "✅ محدثة الآن من WebUntis"
                    )
                }.onFailure { error ->
                    if (hadCache) {
                        showTransientNote("📡 عرضنا آخر جدول محفوظ. تعذر التحديث الآن: ${error.message.orEmpty()}")
                    } else {
                        renderLoadError(target, error.message.orEmpty())
                    }
                }
            }
        }.start()
    }

    private fun renderLoading(target: LocalDate) {
        content.removeAllViews()
        bagChecks.clear()
        addHero(target)
        content.addView(
            HamzaUi.statusBox(
                this,
                "🎒 بنجهز شنطة حمزة…\nأول مرة بنقرأ جدول WebUntis، وبعدها الشنطة هتظهر فورًا من الكاش.",
                0xFFEFF5FC.toInt(),
                HamzaUi.blue
            ),
            HamzaUi.marginTop(this, 16)
        )
    }

    private fun renderConnectionNeeded(target: LocalDate) {
        content.removeAllViews()
        bagChecks.clear()
        addHero(target)
        content.addView(
            HamzaUi.statusBox(
                this,
                "WebUntis مش متوصل على الجهاز ده، فمش هقدر أعرف حصص بكرة تلقائيًا.",
                0xFFFFEFE2.toInt(),
                HamzaUi.amber
            ),
            HamzaUi.marginTop(this, 16)
        )
        content.addView(
            HamzaUi.primaryButton(this, "🔗 افتح مصادر المدرسة").apply {
                setOnClickListener { startActivity(Intent(this@SchoolBagActivity, SourceHubActivity::class.java)) }
            },
            HamzaUi.marginTop(this, 10)
        )
    }

    private fun renderLoadError(target: LocalDate, message: String) {
        content.removeAllViews()
        bagChecks.clear()
        addHero(target)
        content.addView(
            HamzaUi.statusBox(
                this,
                "تعذر قراءة جدول Untis الآن. ${message.ifBlank { "جرّب التحديث بعد لحظات." }}",
                0xFFFFEFE2.toInt(),
                HamzaUi.amber
            ),
            HamzaUi.marginTop(this, 16)
        )
        content.addView(
            HamzaUi.secondaryButton(this, "🔄 حاول تاني").apply {
                setOnClickListener {
                    renderLoading(target)
                    refreshFromWebUntis(target, hadCache = false)
                }
            },
            HamzaUi.marginTop(this, 10)
        )
    }

    private fun renderBag(
        target: LocalDate,
        lessons: List<WebUntisClient.TimetableEntry>,
        syncMessage: String
    ) {
        content.removeAllViews()
        bagChecks.clear()
        addHero(target)

        val active = lessons.filterNot { it.cancelled }
        val grouped = active
            .groupBy { it.subject.ifBlank { "حصة" } }
            .toList()
            .sortedBy { (_, items) -> items.minOfOrNull { it.startTime } ?: Int.MAX_VALUE }

        val summary = HamzaUi.card(this, radius = 22, padding = 16).apply {
            setCardBackgroundColor(0xFFE8F4EA.toInt())
            strokeWidth = 0
        }
        val summaryBody = HamzaUi.cardContent(summary)
        val progress = TextView(this).apply {
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(HamzaUi.green)
            gravity = Gravity.END
        }
        progress.tag = "bag_progress"
        summaryBody.addView(progress)
        summaryBody.addView(
            HamzaUi.subtitle(
                this,
                if (grouped.isEmpty()) "مفيش حصص مؤكدة في Untis لليوم ده."
                else "${active.size} حصة • ${grouped.size} مادة • علّم كل مادة بعد ما تحط حاجتها"
            ),
            HamzaUi.marginTop(this, 4)
        )
        content.addView(summary, HamzaUi.marginTop(this, 14))

        content.addView(
            HamzaUi.statusBox(
                this,
                syncMessage,
                0xFFF0F4F8.toInt(),
                HamzaUi.muted
            ),
            HamzaUi.marginTop(this, 10)
        )

        if (grouped.isEmpty()) {
            content.addView(
                HamzaUi.statusBox(
                    this,
                    "🌤️ مفيش شنطة مواد مطلوبة حسب Untis. راجع أي إعلان استثنائي من المدرسة.",
                    Color.WHITE,
                    HamzaUi.muted
                ),
                HamzaUi.marginTop(this, 12)
            )
        } else {
            content.addView(HamzaUi.section(this, "📚 مواد بكرة"))
            grouped.forEach { (subject, subjectLessons) ->
                content.addView(
                    buildSubjectCard(target, subject, subjectLessons),
                    HamzaUi.marginTop(this, 9)
                )
            }
        }

        content.addView(HamzaUi.section(this, "⭐ الأساسيات"))
        content.addView(buildEssentialsCard(target), HamzaUi.marginTop(this, 4))

        content.addView(
            HamzaUi.secondaryButton(this, "🔄 تحديث الشنطة من Untis").apply {
                setOnClickListener {
                    isEnabled = false
                    text = "جاري التحديث…"
                    refreshFromWebUntis(target, hadCache = active.isNotEmpty())
                }
            },
            HamzaUi.marginTop(this, 16)
        )

        content.addView(
            HamzaUi.subtitle(
                this,
                "ملحوظة: ملفات Teams العادية داخل منشورات القنوات ليست جزءًا من جدول Untis؛ الواجبات المتزامنة تظهر في قسم Hausaufgaben."
            ),
            HamzaUi.marginTop(this, 12)
        )

        updateProgress(progress)
    }

    private fun addHero(target: LocalDate) {
        val hero = HamzaUi.card(this, radius = 26, padding = 20).apply {
            setCardBackgroundColor(0xFF173F6B.toInt())
            strokeWidth = 0
        }
        val body = HamzaUi.cardContent(hero)
        body.addView(TextView(this).apply {
            text = "🎒 SCHULTASCHE"
            textSize = 12f
            letterSpacing = .08f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFC9DCF2.toInt())
            gravity = Gravity.END
        })
        body.addView(TextView(this).apply {
            text = "شنطة حمزة جاهزة؟"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            setPadding(0, HamzaUi.dp(this@SchoolBagActivity, 7), 0, 0)
        })
        body.addView(TextView(this).apply {
            text = "مواد ${formatArabicDate(target)} • نجهزها في دقيقتين ✨"
            textSize = 15f
            setTextColor(0xFFE6EFF8.toInt())
            gravity = Gravity.END
        })
        content.addView(hero)
    }

    private fun buildSubjectCard(
        target: LocalDate,
        subject: String,
        lessons: List<WebUntisClient.TimetableEntry>
    ): View {
        val card = HamzaUi.card(this, radius = 20, padding = 15)
        val body = HamzaUi.cardContent(card)

        val times = lessons
            .sortedBy { it.startTime }
            .joinToString(" • ") { "${formatTime(it.startTime)}–${formatTime(it.endTime)}" }
        val room = lessons.map { it.room }.firstOrNull { it.isNotBlank() }

        body.addView(TextView(this).apply {
            text = subjectIcon(subject) + "  " + subject
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF173F6B.toInt())
            gravity = Gravity.END
        })
        body.addView(
            HamzaUi.subtitle(
                this,
                buildString {
                    append(times)
                    if (!room.isNullOrBlank()) append(" • غرفة ").append(room)
                }
            ),
            HamzaUi.marginTop(this, 3)
        )

        val book = findBookForSubject(subject)
        val items = packingItems(subject, book)
        items.forEach { item ->
            body.addView(TextView(this).apply {
                text = "• $item"
                textSize = 14.5f
                setTextColor(HamzaUi.ink)
                gravity = Gravity.END
                setPadding(0, HamzaUi.dp(this@SchoolBagActivity, 3), 0, 0)
            })
        }

        val substitution = lessons.map { it.substitutionText }.firstOrNull { it.isNotBlank() }
        if (!substitution.isNullOrBlank()) {
            body.addView(
                HamzaUi.statusBox(
                    this,
                    "⚠ $substitution",
                    0xFFFFEFE2.toInt(),
                    HamzaUi.amber
                ),
                HamzaUi.marginTop(this, 8)
            )
        }

        val teamsNotes = relatedTeamsNotes(subject)
        if (teamsNotes.isNotEmpty()) {
            body.addView(
                HamzaUi.statusBox(
                    this,
                    "📎 Teams مرتبط بالمادة\n" + teamsNotes.take(2).joinToString("\n") { "• $it" },
                    0xFFF0EBFF.toInt(),
                    HamzaUi.purple
                ),
                HamzaUi.marginTop(this, 8)
            )
        }

        val check = CheckBox(this).apply {
            text = "✅ اتحطت حاجات $subject"
            textSize = 15f
            gravity = Gravity.END
            isChecked = bagPrefs.getBoolean(checkKey(target, "subject:$subject"), false)
            setOnCheckedChangeListener { _, checked ->
                bagPrefs.edit().putBoolean(checkKey(target, "subject:$subject"), checked).apply()
                findProgressView()?.let(::updateProgress)
            }
        }
        bagChecks.add(check)
        body.addView(check, HamzaUi.marginTop(this, 8))
        return card
    }

    private fun buildEssentialsCard(target: LocalDate): View {
        val card = HamzaUi.card(this, radius = 20, padding = 15).apply {
            setCardBackgroundColor(0xFFFFF3D8.toInt())
            strokeWidth = 0
        }
        val body = HamzaUi.cardContent(card)
        body.addView(HamzaUi.title(this, "الحاجات اللي بننساها 😄", 18f))

        listOf(
            "✏️ المقلمة",
            "💧 زجاجة المياه",
            "🥪 اللانش بوكس"
        ).forEach { label ->
            val check = CheckBox(this).apply {
                text = label
                textSize = 15f
                gravity = Gravity.END
                isChecked = bagPrefs.getBoolean(checkKey(target, "essential:$label"), false)
                setOnCheckedChangeListener { _, checked ->
                    bagPrefs.edit().putBoolean(checkKey(target, "essential:$label"), checked).apply()
                    findProgressView()?.let(::updateProgress)
                }
            }
            bagChecks.add(check)
            body.addView(check)
        }
        return card
    }

    private fun updateProgress(view: TextView) {
        val done = bagChecks.count { it.isChecked }
        val total = bagChecks.size
        view.text = when {
            total == 0 -> "🎒 مفيش حاجات تتجهز"
            done == total -> "🎉 الشنطة جاهزة 100%"
            else -> "🎒 جهزنا $done من $total"
        }
    }

    private fun findProgressView(): TextView? =
        content.findViewWithTag<View>("bag_progress") as? TextView

    private fun showTransientNote(message: String) {
        content.addView(
            HamzaUi.statusBox(this, message, 0xFFFFEFE2.toInt(), HamzaUi.amber),
            HamzaUi.marginTop(this, 8)
        )
    }

    private fun readCachedTimetable(): List<WebUntisClient.TimetableEntry> {
        val file = File(filesDir, "webuntis_timetable.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONObject(file.readText()).optJSONArray("lessons") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        WebUntisClient.TimetableEntry(
                            id = item.optLong("id", Long.MIN_VALUE),
                            date = item.optInt("date", 0),
                            startTime = item.optInt("startTime", 0),
                            endTime = item.optInt("endTime", 0),
                            subject = item.optString("subject"),
                            teacher = item.optString("teacher"),
                            room = item.optString("room"),
                            cancelled = item.optBoolean("cancelled", false),
                            substitutionText = item.optString("substitutionText")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun nextDateFromCache(lessons: List<WebUntisClient.TimetableEntry>): LocalDate? {
        val today = LocalDate.now()
        return lessons
            .filterNot { it.cancelled }
            .mapNotNull { entry ->
                runCatching {
                    LocalDate.parse(entry.date.toString(), DateTimeFormatter.BASIC_ISO_DATE)
                }.getOrNull()
            }
            .filter { it.isAfter(today) }
            .minOrNull()
    }

    private fun findBookForSubject(subject: String): String? {
        val normalized = normalize(subject)
        return BookLibraryStore(this).allBooks().firstOrNull { book ->
            normalize(book.subject).contains(normalized) ||
                normalized.contains(normalize(book.subject)) ||
                book.aliases.any { normalize(it).contains(normalized) || normalized.contains(normalize(it)) }
        }?.title
    }

    private fun packingItems(subject: String, bookTitle: String?): List<String> {
        val s = normalize(subject)
        return when {
            s.contains("sport") || s.contains("physical") ->
                listOf("👟 لبس وحذاء الرياضة")
            s.contains("kunst") || s.contains("art") ->
                listOf("🎨 أدوات الرسم / الملف")
            s.contains("musik") || s.contains("music") ->
                listOf("🎵 دفتر أو ملف الموسيقى")
            else -> buildList {
                add("📘 ${bookTitle ?: "كتاب المادة"}")
                add("📓 الكراسة / الدفتر")
                add("📂 أي أوراق أو ملف مطلوب للمادة")
            }
        }
    }

    private fun relatedTeamsNotes(subject: String): List<String> {
        val file = File(filesDir, "school_notifications.jsonl")
        if (!file.exists()) return emptyList()
        val needle = normalize(subject)
        return file.readLines()
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .filter { it.optString("source").equals("Teams", true) }
            .filter { item ->
                val haystack = normalize(
                    listOf(
                        item.optString("subject"),
                        item.optString("title"),
                        item.optString("text"),
                        item.optString("bigText")
                    ).joinToString(" ")
                )
                needle.isNotBlank() && haystack.contains(needle)
            }
            .sortedByDescending { it.optLong("timestamp") }
            .map { it.optString("title").ifBlank { "تحديث من Teams" } }
            .distinct()
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace("ä", "a")
            .replace("ö", "o")
            .replace("ü", "u")
            .replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), "")

    private fun subjectIcon(subject: String): String {
        val s = normalize(subject)
        return when {
            s.contains("deutsch") -> "🇩🇪"
            s.contains("mathe") || s.contains("math") -> "➗"
            s.contains("engl") -> "🇬🇧"
            s.contains("geschichte") -> "🏛️"
            s.contains("erdkunde") || s.contains("geo") -> "🌍"
            s.contains("bio") || s.contains("science") -> "🔬"
            s.contains("sport") -> "⚽"
            s.contains("kunst") -> "🎨"
            s.contains("musik") -> "🎵"
            else -> "📚"
        }
    }

    private fun nextSchoolDay(from: LocalDate): LocalDate {
        var day = from.plusDays(1)
        while (day.dayOfWeek == DayOfWeek.FRIDAY || day.dayOfWeek == DayOfWeek.SATURDAY) {
            day = day.plusDays(1)
        }
        return day
    }

    private fun LocalDate.toBasicInt(): Int =
        format(DateTimeFormatter.BASIC_ISO_DATE).toInt()

    private fun formatArabicDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE d/M", Locale("ar")))

    private fun formatTime(value: Int): String =
        "%02d:%02d".format(value / 100, value % 100)

    private fun checkKey(date: LocalDate, item: String): String =
        "bag_${date.toBasicInt()}_${item.hashCode()}"
}
