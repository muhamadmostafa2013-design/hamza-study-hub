package com.hamza.studyhub.learning

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.UUID

/**
 * Captures Hamza's actual attempt as learning evidence.
 *
 * We deliberately do not show an automatic answer/correction to Hamza here. The
 * purpose is longitudinal analysis for the parent: strategy, recurring errors and
 * improvement in later attempts.
 */
class StudentWorkCaptureActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null
    private val store by lazy { LearningEvidenceStore(this) }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val uri = pendingCameraUri
            val file = pendingCameraFile
            if (uri != null && file != null) analyzeAndStore(uri, Uri.fromFile(file).toString())
        } else {
            statusText.text = "تم إلغاء التصوير."
        }
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val copied = copyIntoPrivateStorage(uri)
            if (copied != null) analyzeAndStore(Uri.fromFile(copied), Uri.fromFile(copied).toString())
            else statusText.text = "⚠️ لم أستطع حفظ نسخة من الصورة."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(28), dp(20), dp(20))
            setBackgroundColor(Color.rgb(246, 247, 251))
        }

        root.addView(TextView(this).apply {
            text = "📷 محاولة حمزة"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(20, 24, 33))
        })

        val subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
        val page = intent.getIntExtra(EXTRA_PAGE, 0)
        val exercise = intent.getStringExtra(EXTRA_EXERCISE).orEmpty()
        root.addView(TextView(this).apply {
            text = buildString {
                if (subject.isNotBlank()) append(subject)
                if (page > 0) append(if (isNotEmpty()) " • صفحة $page" else "صفحة $page")
                if (exercise.isNotBlank()) append(if (isNotEmpty()) " • تمرين $exercise" else "تمرين $exercise")
            }.ifBlank { "واجب حمزة" }
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, dp(8), 0, dp(6))
            setTextColor(Color.rgb(36, 91, 156))
        })

        root.addView(TextView(this).apply {
            text = "صوّر الحل كما كتبه حمزة. الصورة دي مش علشان نديه الإجابة؛ هي علشان نفهم طريقة حله ونكتشف الأخطاء المتكررة ونشوف هل اتحسنت بعدين."
            textSize = 15.5f
            gravity = Gravity.END
            setPadding(0, dp(4), 0, dp(12))
            setTextColor(Color.DKGRAY)
        })

        root.addView(Button(this).apply {
            text = "📷 صوّر الحل الآن"
            setOnClickListener { startCameraCapture() }
        })

        root.addView(Button(this).apply {
            text = "🖼️ اختيار صورة موجودة"
            setOnClickListener { imagePicker.launch("image/*") }
        })

        statusText = TextView(this).apply {
            text = "لم يتم حفظ محاولة بعد."
            textSize = 15f
            gravity = Gravity.END
            setPadding(0, dp(14), 0, dp(10))
            setTextColor(Color.rgb(52, 124, 89))
        }
        root.addView(statusText)
        return root
    }

    private fun startCameraCapture() {
        val dir = File(filesDir, "student_work").apply { mkdirs() }
        val file = File(dir, "attempt-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.files", file)
        pendingCameraFile = file
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    private fun analyzeAndStore(inputUri: Uri, stableImageUri: String) {
        statusText.text = "⏳ جاري حفظ المحاولة وقراءة الكتابة..."
        runCatching { InputImage.fromFilePath(this, inputUri) }
            .onSuccess { image ->
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener { result ->
                        saveAttempt(stableImageUri, result.text.trim().takeIf { it.isNotBlank() })
                    }
                    .addOnFailureListener {
                        // The image itself is still useful evidence even if OCR fails.
                        saveAttempt(stableImageUri, null)
                    }
            }
            .onFailure {
                saveAttempt(stableImageUri, null)
            }
    }

    private fun saveAttempt(imageUri: String, extractedText: String?) {
        val fingerprint = intent.getStringExtra(EXTRA_ASSIGNMENT_FINGERPRINT)
            ?.takeIf { it.isNotBlank() }
            ?: "manual-${System.currentTimeMillis()}"
        val attempt = StudentAttempt(
            id = UUID.randomUUID().toString(),
            assignmentFingerprint = fingerprint,
            subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty(),
            bookId = intent.getStringExtra(EXTRA_BOOK_ID)?.takeIf { it.isNotBlank() },
            page = intent.getIntExtra(EXTRA_PAGE, 0).takeIf { it > 0 },
            exercise = intent.getStringExtra(EXTRA_EXERCISE)?.takeIf { it.isNotBlank() },
            capturedAt = System.currentTimeMillis(),
            imageUri = imageUri,
            extractedText = extractedText,
            attemptNumber = store.nextAttemptNumber(fingerprint)
        )
        store.appendAttempt(attempt)

        statusText.text = buildString {
            append("✅ تم حفظ المحاولة رقم ${attempt.attemptNumber}.")
            if (!extractedText.isNullOrBlank()) append("\nتم استخراج نص مبدئي من الحل للتحليل لاحقًا.")
            else append("\nالصورة محفوظة، لكن الكتابة لم تُقرأ آليًا بوضوح.")
            append("\nلن نعرض تصحيح تلقائي لحمزة من هذه الشاشة.")
        }
    }

    private fun copyIntoPrivateStorage(uri: Uri): File? {
        return runCatching {
            val dir = File(filesDir, "student_work").apply { mkdirs() }
            val file = File(dir, "attempt-${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            file
        }.getOrNull()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ASSIGNMENT_FINGERPRINT = "assignment_fingerprint"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_PAGE = "page"
        const val EXTRA_EXERCISE = "exercise"
    }
}
