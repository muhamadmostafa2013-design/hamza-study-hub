package com.hamza.studyhub.imports

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.hamza.studyhub.MainActivity
import com.hamza.studyhub.R
import com.hamza.studyhub.agents.AgentOrchestrator
import com.hamza.studyhub.sources.SchoolFeedStore
import com.hamza.studyhub.ui.HamzaUi
import org.json.JSONObject
import java.util.UUID

class ManualHomeworkImportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.whatsapp_import_title)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            val padding = HamzaUi.dp(context, 20)
            setPaddingRelative(padding, padding, padding, padding)
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        body.addView(TextView(this).apply {
            setText(R.string.whatsapp_import_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
            gravity = Gravity.START
            ViewCompat.setAccessibilityHeading(this, true)
        })
        body.addView(TextView(this).apply {
            setText(R.string.whatsapp_import_help)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            gravity = Gravity.START
        }, HamzaUi.marginTop(this, 12))

        val editor = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 4
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            gravity = Gravity.TOP or Gravity.START
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            // The draft may contain other people's information. Do not restore or autofill it.
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        val field = TextInputLayout(this).apply {
            hint = getString(R.string.whatsapp_import_field)
            isSaveEnabled = false
            addView(editor, LinearLayout.LayoutParams(-1, -2))
        }
        body.addView(field, HamzaUi.marginTop(this, 20))
        body.addView(HamzaUi.primaryButton(this, getString(R.string.whatsapp_import_save)).apply {
            setOnClickListener {
                val raw = editor.text?.toString().orEmpty()
                val snippet = ManualHomeworkImport.validatedSnippet(raw)
                if (snippet == null) {
                    field.error = getString(if (raw.isBlank()) R.string.whatsapp_import_empty else R.string.whatsapp_import_too_long)
                    return@setOnClickListener
                }
                field.error = null
                AlertDialog.Builder(this@ManualHomeworkImportActivity)
                    .setTitle(R.string.whatsapp_import_save)
                    .setMessage(R.string.whatsapp_import_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.whatsapp_import_save) { _, _ ->
                        val saved = runCatching {
                            SchoolFeedStore.append(this@ManualHomeworkImportActivity, ManualHomeworkImport.item(snippet))
                        }.isSuccess
                        if (!saved) {
                            field.error = getString(R.string.whatsapp_import_error)
                            return@setPositiveButton
                        }
                        editor.text?.clear()
                        AlertDialog.Builder(this@ManualHomeworkImportActivity)
                            .setMessage(R.string.whatsapp_import_saved)
                            .setCancelable(false)
                            .setPositiveButton(R.string.whatsapp_import_open) { _, _ ->
                                startActivity(Intent(this@ManualHomeworkImportActivity, MainActivity::class.java))
                                finish()
                            }
                            .setNegativeButton(R.string.whatsapp_import_done) { _, _ -> finish() }
                            .show()
                    }.show()
            }
        }, HamzaUi.marginTop(this, 16))
        body.addView(HamzaUi.secondaryButton(this, getString(android.R.string.cancel)).apply {
            setOnClickListener { finish() }
        }, HamzaUi.marginTop(this, 8))
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        })
    }
}

internal object ManualHomeworkImport {
    private const val MAX_LENGTH = 2000

    fun validatedSnippet(text: String): String? =
        text.trim().takeIf { it.isNotBlank() && it.length <= MAX_LENGTH }

    fun item(snippet: String, timestamp: Long = System.currentTimeMillis()): JSONObject {
        val text = requireNotNull(validatedSnippet(snippet))
        return AgentOrchestrator.enrich(JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("source", "WhatsApp")
            .put("title", "واجب WhatsApp · Homework")
            .put("text", text)
            .put("bigText", text)
            .put("timestamp", timestamp)
            .put("isNew", true)
            .put("imported", true)
            .put("importMethod", "Manual WhatsApp copy/paste")
            .put("attentionResolved", false))
    }
}
