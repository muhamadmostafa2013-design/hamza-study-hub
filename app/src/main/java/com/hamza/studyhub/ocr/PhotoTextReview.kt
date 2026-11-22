package com.hamza.studyhub.ocr

import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.hamza.studyhub.R
import com.hamza.studyhub.ui.HamzaUi

internal object PhotoTextReview {
    private const val MAX_TEXT_LENGTH = 2000

    fun chooseLanguage(
        activity: AppCompatActivity,
        onSelected: (OnDevicePhotoText.Language) -> Unit,
        onDismissed: () -> Unit = {}
    ): AlertDialog {
        val languages = OnDevicePhotoText.Language.entries
        var selected = false
        return AlertDialog.Builder(activity)
            .setTitle(R.string.photo_ocr_language)
            .setItems(languages.map { activity.getString(it.label) }.toTypedArray()) { _, which ->
                selected = true
                onSelected(languages[which])
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onDismissed() }
            .create().apply {
                setOnCancelListener { if (!selected) onDismissed() }
                show()
            }
    }

    fun edit(
        activity: AppCompatActivity,
        text: String?,
        onSave: (String) -> Unit,
        onDismissed: () -> Unit = {}
    ): AlertDialog {
        val initialText = text?.takeIf { it.length <= MAX_TEXT_LENGTH }.orEmpty()
        val editor = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            textDirection = android.view.View.TEXT_DIRECTION_FIRST_STRONG
            minLines = 3
            maxLines = 9
            filters = arrayOf(InputFilter.LengthFilter(MAX_TEXT_LENGTH))
            hint = activity.getText(R.string.photo_ocr_text_hint)
            setText(initialText)
        }
        val inset = HamzaUi.dp(activity, 24)
        val container = LinearLayout(activity).apply {
            setPadding(inset, 0, inset, 0)
            addView(editor, LinearLayout.LayoutParams(-1, -2))
        }
        var saved = false
        return AlertDialog.Builder(activity)
            .setTitle(R.string.photo_ocr_review)
            .setMessage(
                when {
                    initialText.isNotBlank() -> R.string.photo_ocr_check_text
                    text.isNullOrBlank() -> R.string.photo_ocr_empty_text
                    else -> R.string.photo_ocr_long_text
                }
            )
            .setView(container)
            .setNegativeButton(android.R.string.cancel) { _, _ -> onDismissed() }
            .setPositiveButton(R.string.photo_ocr_save) { _, _ ->
                val confirmed = editor.text.toString().trim()
                if (confirmed.isNotBlank()) {
                    saved = true
                    onSave(confirmed)
                }
            }
            .create().apply {
                setOnCancelListener { if (!saved) onDismissed() }
                show()
                val saveButton = getButton(AlertDialog.BUTTON_POSITIVE)
                saveButton.isEnabled = initialText.isNotBlank()
                editor.doAfterTextChanged { saveButton.isEnabled = !it.isNullOrBlank() }
            }
    }
}
