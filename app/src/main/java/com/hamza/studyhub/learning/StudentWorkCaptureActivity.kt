package com.hamza.studyhub.learning

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hamza.studyhub.ui.HamzaUi
import java.io.File
import java.util.UUID

class StudentWorkCaptureActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null
    private val store by lazy { LearningEvidenceStore(this) }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val uri = pendingCameraUri; val file = pendingCameraFile
            if (uri != null && file != null) analyzeAndStore(uri, Uri.fromFile(file).toString())
        } else statusText.text = "لم يتم التقاط صورة."
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val copied = copyIntoPrivateStorage(uri)
            if (copied != null) analyzeAndStore(Uri.fromFile(copied), Uri.fromFile(copied).toString())
            else statusText.text = "تعذر حفظ نسخة من الصورة."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(HamzaUi.dp(this@StudentWorkCaptureActivity, 18), HamzaUi.dp(this@StudentWorkCaptureActivity, 24), HamzaUi.dp(this@StudentWorkCaptureActivity, 18), HamzaUi.dp(this@StudentWorkCaptureActivity, 28))
            setBackgroundColor(HamzaUi.bg)
        }
        content.addView(HamzaUi.title(this, "محاولة حمزة"))
        content.addView(HamzaUi.subtitle(this, "نحفظ طريقة الحل كدليل تعلّم، من غير ما نعرض لحمزة إجابة جاهزة."), HamzaUi.marginTop(this, 4))

        val subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
        val page = intent.getIntExtra(EXTRA_PAGE, 0)
        val exercise = intent.getStringExtra(EXTRA_EXERCISE).orEmpty()
        val taskCard = HamzaUi.card(this)
        val taskBody = HamzaUi.cardContent(taskCard)
        taskBody.addView(HamzaUi.softPill(this, "الواجب المرتبط", 0xFFE8F1FF.toInt(), HamzaUi.blue))
        taskBody.addView(HamzaUi.title(this, buildString {
            if (subject.isNotBlank()) append(subject)
            if (page > 0) append(if (isNotEmpty()) " • صفحة $page" else "صفحة $page")
            if (exercise.isNotBlank()) append(if (isNotEmpty()) " • تمرين $exercise" else "تمرين $exercise")
        }.ifBlank { "واجب حمزة" }, 18f), HamzaUi.marginTop(this, 9))
        content.addView(taskCard, HamzaUi.marginTop(this, 14))

        content.addView(HamzaUi.section(this, "إضافة المحاولة"))
        content.addView(HamzaUi.primaryButton(this, "تصوير الحل الآن").apply {
            tag = "attempt_camera"; setOnClickListener { startCameraCapture() }
        })
        content.addView(HamzaUi.secondaryButton(this, "اختيار صورة موجودة").apply {
            tag = "attempt_gallery"; setOnClickListener { imagePicker.launch("image/*") }
        }, HamzaUi.marginTop(this, 8))

        statusText = HamzaUi.statusBox(this, "لم يتم حفظ محاولة بعد.", 0xFFEAF6EF.toInt(), HamzaUi.green)
        statusText.gravity = Gravity.END
        content.addView(statusText, HamzaUi.marginTop(this, 14))

        content.addView(HamzaUi.subtitle(this,
            "بعد الحفظ يمكن للنظام لاحقًا مقارنة المحاولات واكتشاف نمط الخطأ المتكرر والتحسن عبر الوقت."), HamzaUi.marginTop(this, 14))
        return ScrollView(this).apply { isFillViewport = true; addView(content) }
    }

    private fun startCameraCapture() {
        val dir = File(filesDir, "student_work").apply { mkdirs() }
        val file = File(dir, "attempt-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "${packageName}.files", file)
        pendingCameraFile = file; pendingCameraUri = uri; cameraLauncher.launch(uri)
    }

    private fun analyzeAndStore(inputUri: Uri, stableImageUri: String) {
        statusText.text = "جاري حفظ المحاولة وقراءة الكتابة…"
        runCatching { InputImage.fromFilePath(this, inputUri) }
            .onSuccess { image -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener { saveAttempt(stableImageUri, it.text.trim().takeIf(String::isNotBlank)) }
                .addOnFailureListener { saveAttempt(stableImageUri, null) } }
            .onFailure { saveAttempt(stableImageUri, null) }
    }

    private fun saveAttempt(imageUri: String, extractedText: String?) {
        val fingerprint = intent.getStringExtra(EXTRA_ASSIGNMENT_FINGERPRINT)?.takeIf { it.isNotBlank() }
            ?: "manual-${System.currentTimeMillis()}"
        val attempt = StudentAttempt(
            id = UUID.randomUUID().toString(),
            assignmentFingerprint = fingerprint,
            subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty(),
            bookId = intent.getStringExtra(EXTRA_BOOK_ID)?.takeIf { it.isNotBlank() },
            page = intent.getIntExtra(EXTRA_PAGE, 0).takeIf { it > 0 },
            exercise = intent.getStringExtra(EXTRA_EXERCISE)?.takeIf { it.isNotBlank() },
            capturedAt = System.currentTimeMillis(), imageUri = imageUri, extractedText = extractedText,
            attemptNumber = store.nextAttemptNumber(fingerprint)
        )
        store.appendAttempt(attempt)
        statusText.text = if (!extractedText.isNullOrBlank())
            "تم حفظ المحاولة رقم ${attempt.attemptNumber} وقراءة نص مبدئي منها للتحليل لاحقًا."
        else "تم حفظ المحاولة رقم ${attempt.attemptNumber}. الصورة محفوظة حتى لو لم تُقرأ الكتابة بوضوح."
    }

    private fun copyIntoPrivateStorage(uri: Uri): File? = runCatching {
        val dir = File(filesDir, "student_work").apply { mkdirs() }
        val file = File(dir, "attempt-${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return null
        file
    }.getOrNull()

    companion object {
        const val EXTRA_ASSIGNMENT_FINGERPRINT = "assignment_fingerprint"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_PAGE = "page"
        const val EXTRA_EXERCISE = "exercise"
    }
}
