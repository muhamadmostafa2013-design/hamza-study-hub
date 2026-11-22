package com.hamza.studyhub

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.hamza.studyhub.agents.SchoolFeedProjector
import com.hamza.studyhub.books.BookLibraryActivity
import com.hamza.studyhub.certificates.CertificatesActivity
import com.hamza.studyhub.homework.HomeworkScreenPolicy
import com.hamza.studyhub.imports.ManualHomeworkImportActivity
import com.hamza.studyhub.sources.SchoolFeedStore
import com.hamza.studyhub.teams.TeamsAuthStore
import com.hamza.studyhub.teams.TeamsSyncWorker
import com.hamza.studyhub.ui.HamzaUi
import com.hamza.studyhub.update.AppUpdateManager
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    private lateinit var assignmentTitle: TextView
    private lateinit var assignmentDetail: TextView
    private lateinit var primaryAction: MaterialButton
    private var nextAssignment: JSONObject? = null
    private var refreshJob: Job? = null
    private val german get() = resources.configuration.locales[0]?.language == "de"
    private val displayLocale get() = Locale.forLanguageTag(if (german) "de-DE" else "ar-EG")
    private val direction get() = if (german) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_HamzaStudyHub_Today)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(buildScreen())
        if (WebUntisConfigStore.isConfigured(this)) WebUntisSyncWorker.schedule(this)
        if (TeamsAuthStore.account(this) != null) TeamsSyncWorker.schedule(this)
        AppUpdateManager.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        AppUpdateManager.resumePendingInstall(this)
        AppUpdateManager.offerReadyUpdate(this)
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    HomeworkScreenPolicy.visible(
                        SchoolFeedProjector.current(SchoolFeedStore.read(this@HomeActivity)),
                        HomeworkScreenPolicy.Filter.TO_DO
                    ).firstOrNull()
                }
            }
            showAssignment(result.getOrNull(), result.isFailure)
        }
    }

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutDirection = direction
            setBackgroundColor(0xFFF6F8FC.toInt())
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = direction
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }
        scroll.addView(content)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            content.setPadding(dp(20) + bars.left, dp(24) + bars.top, dp(20) + bars.right, dp(28) + bars.bottom)
            insets
        }

        content.addView(label(getString(R.string.today_title), 34f, true, HamzaUi.ink).apply {
            ViewCompat.setAccessibilityHeading(this, true)
        })
        content.addView(label(getString(R.string.today_intro), 16f, false, HamzaUi.muted), gap(4))

        val featured = HamzaUi.card(this, radius = 28, padding = 22).apply {
            setCardBackgroundColor(0xFF173F6B.toInt())
            strokeWidth = 0
            cardElevation = 0f
        }
        val card = HamzaUi.cardContent(featured).apply { layoutDirection = direction }
        card.addView(label(getString(R.string.today_next), 14f, true, 0xFFE0EBFA.toInt()))
        assignmentTitle = label("", 25f, true, Color.WHITE).apply {
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        }
        card.addView(assignmentTitle, gap(9))
        assignmentDetail = label("", 16f, false, 0xFFE0EBFA.toInt())
        card.addView(assignmentDetail, gap(6))
        primaryAction = HamzaUi.primaryButton(this, getString(R.string.today_homework)).apply {
            minHeight = dp(54)
            backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            setTextColor(0xFF173F6B.toInt())
            tag = "today_primary"
            setOnClickListener {
                val intent = Intent(this@HomeActivity, MainActivity::class.java)
                nextAssignment?.let { selected ->
                    intent.putExtra(MainActivity.EXTRA_TODAY_FOCUS, MainActivity.todayFocusKey(selected))
                }
                startActivity(intent)
            }
        }
        card.addView(primaryAction, gap(16))
        content.addView(featured, gap(20))
        content.addView(label(getString(R.string.today_local), 13f, false, HamzaUi.muted), gap(9))

        section(content, R.string.today_links)
        val shortcuts = listOf(
            R.string.today_homework to MainActivity::class.java,
            R.string.today_books to BookLibraryActivity::class.java,
            R.string.today_bag to SchoolBagActivity::class.java,
            R.string.today_plan to StudyPlanActivity::class.java
        )
        if (resources.configuration.screenWidthDp >= 600 && resources.configuration.fontScale < 1.5f) {
            shortcuts.chunked(2).forEach { group ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                group.forEachIndexed { index, (title, target) ->
                    row.addView(action(title) { startActivity(Intent(this, target)) },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { if (index == 0) marginEnd = dp(8) })
                }
                content.addView(row, gap(6))
            }
        } else {
            shortcuts.forEach { (title, target) ->
                content.addView(action(title) { startActivity(Intent(this, target)) }, gap(6))
            }
        }

        content.addView(action(R.string.certificates_title) {
            startActivity(Intent(this, CertificatesActivity::class.java))
        }, gap(6))

        section(content, R.string.today_parent)
        content.addView(action(R.string.today_whatsapp) { startActivity(Intent(this, ManualHomeworkImportActivity::class.java)) }, gap(6))
        content.addView(action(R.string.today_sources) { startActivity(Intent(this, SourceHubActivity::class.java)) }, gap(6))
        content.addView(action(R.string.today_update) { AppUpdateManager.check(this, silentIfCurrent = false) }, gap(6))
        content.addView(action(R.string.today_other_language) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (german) "ar" else "de"))
        }, gap(10))

        assignmentTitle.text = getString(R.string.today_loading)
        primaryAction.isEnabled = false
        return scroll
    }

    private fun showAssignment(item: JSONObject?, hasError: Boolean) {
        nextAssignment = item
        primaryAction.isEnabled = true
        if (item == null) {
            assignmentTitle.text = getString(if (hasError) R.string.today_unavailable else R.string.today_no_assignment)
            assignmentDetail.text = getString(if (hasError) R.string.today_read_error else R.string.today_no_assignment_hint)
            primaryAction.text = getString(R.string.today_homework)
            return
        }
        assignmentTitle.text = item.optString("title").trim().ifEmpty {
            item.optString("subject").trim().ifEmpty { getString(R.string.today_assignment_fallback) }
        }
        val date = HomeworkScreenPolicy.dueStatus(item.optInt("dueDate", 0)).date
        val dueLabel = if (date == null) getString(R.string.today_due_unknown) else getString(
            R.string.today_due, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(displayLocale))
        )
        assignmentDetail.text = if (item.optString("evidenceTrust") == "COMMUNITY")
            getString(R.string.today_unverified, dueLabel) else dueLabel
        val progress = item.optInt("progressPercent", 0) > 0
        primaryAction.text = getString(if (progress) R.string.today_resume else R.string.today_start)
    }

    private fun section(container: LinearLayout, title: Int) {
        container.addView(label(getString(title), 19f, true, HamzaUi.ink).apply {
            ViewCompat.setAccessibilityHeading(this, true)
        }, gap(22))
    }

    private fun action(title: Int, click: () -> Unit) =
        HamzaUi.secondaryButton(this, getString(title), 0xFF173F6B.toInt()).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minHeight = dp(52)
            setOnClickListener { click() }
        }

    private fun label(value: String, size: Float, bold: Boolean, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        textLocale = displayLocale
        gravity = Gravity.START
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(value: Int) = HamzaUi.dp(this, value)
    private fun gap(value: Int) = HamzaUi.marginTop(this, value)
}
