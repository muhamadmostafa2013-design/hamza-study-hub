package com.hamza.studyhub.imports

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.hamza.studyhub.MainActivity
import com.hamza.studyhub.R
import com.hamza.studyhub.sources.SchoolFeedStore
import com.hamza.studyhub.ui.HamzaUi
import org.json.JSONObject
import java.util.UUID

class SchoolExcerptActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        title = getString(R.string.school_excerpt_title)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        body.addView(HamzaUi.title(this, getString(R.string.school_excerpt_title)))
        body.addView(HamzaUi.subtitle(this, getString(R.string.school_excerpt_help)))
        val provider = Spinner(this).apply {
            tag = "school_excerpt_provider"
            isSaveEnabled = false
            contentDescription = getString(R.string.school_excerpt_source)
            adapter = ArrayAdapter(
                this@SchoolExcerptActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Google Classroom", "Microsoft Teams")
            )
        }
        body.addView(provider, HamzaUi.marginTop(this, 12))

        fun field(hint: Int, name: String, input: Int): Pair<TextInputLayout, TextInputEditText> {
            val editor = TextInputEditText(this).apply {
                tag = name
                inputType = input or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                isSaveEnabled = false
                textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            }
            return TextInputLayout(this).apply {
                isSaveEnabled = false
                this.hint = getString(hint)
                addView(editor, LinearLayout.LayoutParams(-1, -2))
                body.addView(this, HamzaUi.marginTop(this@SchoolExcerptActivity, 12))
            } to editor
        }

        val (titleField, titleEditor) = field(R.string.school_excerpt_task, "school_excerpt_task", InputType.TYPE_CLASS_TEXT)
        val (excerptField, excerptEditor) = field(
            R.string.school_excerpt_text, "school_excerpt_text",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
        excerptEditor.minLines = 3
        val (dateField, dateEditor) = field(R.string.school_excerpt_date, "school_excerpt_date", InputType.TYPE_CLASS_TEXT)
        body.addView(HamzaUi.primaryButton(this, getString(R.string.school_excerpt_preview)).apply {
            tag = "school_excerpt_preview"
            setOnClickListener {
                for (field in listOf(titleField, excerptField, dateField)) field.error = null
                val selected = if (provider.selectedItemPosition == 1) "Teams" else "Classroom"
                val draft = try {
                    SchoolExcerpt.review(selected, titleEditor.text.toString(), excerptEditor.text.toString(), dateEditor.text.toString())
                } catch (error: IllegalArgumentException) {
                    when (error.message) {
                        "title" -> titleField.error = getString(R.string.school_excerpt_title_error)
                        "date" -> dateField.error = getString(R.string.school_excerpt_date_error)
                        else -> excerptField.error = getString(R.string.school_excerpt_text_error)
                    }
                    return@setOnClickListener
                }
                val due = draft.dueDate?.toString()?.let { "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6)}" }
                AlertDialog.Builder(this@SchoolExcerptActivity)
                    .setTitle(R.string.school_excerpt_preview)
                    .setMessage(listOf(provider.selectedItem.toString(), draft.title, draft.excerpt,
                        due ?: getString(R.string.school_excerpt_no_date)).joinToString("\n\n"))
                    .setNegativeButton(R.string.school_excerpt_edit, null)
                    .setPositiveButton(R.string.school_excerpt_save) { _, _ ->
                        val saved = runCatching {
                            SchoolFeedStore.append(this@SchoolExcerptActivity, JSONObject()
                                .put("id", UUID.randomUUID().toString())
                                .put("source", if (draft.source == "Teams") "Teams (manual)" else "Classroom (manual)")
                                .put("sourceProvider", draft.source)
                                .put("sourceMode", "caregiver_snapshot")
                                .put("title", draft.title)
                                .put("text", draft.excerpt)
                                .put("bigText", draft.excerpt)
                                .put("timestamp", System.currentTimeMillis())
                                .put("updateType", "HOMEWORK")
                                .put("isNew", true)
                                .put("imported", true)
                                .put("importMethod", "Caregiver-selected summary excerpt")
                                .put("needsAttention", draft.dueDate == null)
                                .put("attentionResolved", false)
                                .apply {
                                    if (draft.dueDate != null) put("dueDate", draft.dueDate)
                                    else put("attentionReason", getString(R.string.school_excerpt_no_date))
                                })
                        }.isSuccess
                        if (!saved) {
                            excerptField.error = getString(R.string.school_excerpt_save_error)
                            return@setPositiveButton
                        }
                        startActivity(Intent(this@SchoolExcerptActivity, MainActivity::class.java)
                            .putExtra("open_attention", draft.dueDate == null))
                        finish()
                    }.show()
            }
        }, HamzaUi.marginTop(this, 16))
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        HamzaUi.insetScrollable(this, scroll, body, 20, 20, 20, avoidIme = true)
        setContentView(scroll)
    }
}
