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
import com.hamza.studyhub.R
import com.hamza.studyhub.ocr.OnDevicePhotoText
import com.hamza.studyhub.ocr.PhotoTextReview
import com.hamza.studyhub.ui.HamzaUi
import java.io.File
import java.util.UUID

class StudentWorkCaptureActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private var pendingCameraName: String? = null
    private var visibleAttemptId: String? = null
    private val store by lazy { LearningEvidenceStore(this) }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = PendingCameraCapture.resolve(filesDir, pendingCameraName)
        pendingCameraName = null
        when {
            !success -> {
                // Only discard this attempt's empty placeholder; a camera may have written
                // a usable photo even if it failed to report success.
                if (file?.isFile == true && file.length() == 0L) file.delete()
                statusText.text = "لم يتم التقاط صورة."
            }
            file?.isFile == true && file.length() > 0L ->
                analyzeAndStore(Uri.fromFile(file), Uri.fromFile(file).toString())
            else -> statusText.text = "تعذر استرجاع الصورة. حاول التقاطها مرة أخرى."
        }
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
        pendingCameraName = savedInstanceState?.getString(STATE_PENDING_CAMERA)
            ?.takeIf { PendingCameraCapture.resolve(filesDir, it) != null }
        setContentView(buildScreen())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCameraName?.let { outState.putString(STATE_PENDING_CAMERA, it) }
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

        content.addView(HamzaUi.subtitle(this, getString(R.string.photo_ocr_attempt_note)), HamzaUi.marginTop(this, 14))
        return ScrollView(this).apply {
            isFillViewport = true
            addView(content)
            HamzaUi.insetScrollable(this@StudentWorkCaptureActivity, this, content, 18, 24, 28)
        }
    }

    private fun startCameraCapture() {
        if (pendingCameraName != null) return
        val name = PendingCameraCapture.newName()
        val file = PendingCameraCapture.resolve(filesDir, name)
        if (file == null) {
            statusText.text = "تعذر فتح الكاميرا. يمكنك اختيار صورة موجودة."
            return
        }
        pendingCameraName = name
        runCatching {
            file.parentFile?.mkdirs()
            val uri = FileProvider.getUriForFile(this, "${packageName}.files", file)
            cameraLauncher.launch(uri)
        }.onFailure {
            pendingCameraName = null
            statusText.text = "تعذر فتح الكاميرا. يمكنك اختيار صورة موجودة."
        }
    }

    private fun analyzeAndStore(inputUri: Uri, stableImageUri: String) {
        val attempt = runCatching { savePhotoAttempt(stableImageUri) }.getOrElse {
            statusText.text = "تعذر تسجيل المحاولة. جرّب مرة أخرى."
            return
        }
        visibleAttemptId = attempt.id
        fun showSavedPhoto() {
            if (!isDestroyed && !isFinishing && visibleAttemptId == attempt.id) {
                statusText.text = getString(R.string.photo_ocr_attempt_saved, attempt.attemptNumber)
            }
        }
        showSavedPhoto()
        PhotoTextReview.chooseLanguage(this, onSelected = { language ->
            statusText.text = getString(R.string.photo_ocr_reading)
            OnDevicePhotoText.recognize(this, inputUri, language) { suggestion ->
                if (isDestroyed || isFinishing || visibleAttemptId != attempt.id) return@recognize
                showSavedPhoto()
                PhotoTextReview.edit(this, suggestion, onSave = { confirmed ->
                    val saved = runCatching { store.updateExtractedText(attempt.id, confirmed) }.getOrDefault(false)
                    statusText.text = getString(
                        if (saved) R.string.photo_ocr_attempt_saved_with_text else R.string.photo_ocr_attempt_text_error,
                        attempt.attemptNumber
                    )
                })
            }
        })
    }

    private fun savePhotoAttempt(imageUri: String): StudentAttempt {
        val fingerprint = intent.getStringExtra(EXTRA_ASSIGNMENT_FINGERPRINT)?.takeIf { it.isNotBlank() }
            ?: "manual-${System.currentTimeMillis()}"
        val attempt = StudentAttempt(
            id = UUID.randomUUID().toString(),
            assignmentFingerprint = fingerprint,
            subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty(),
            bookId = intent.getStringExtra(EXTRA_BOOK_ID)?.takeIf { it.isNotBlank() },
            page = intent.getIntExtra(EXTRA_PAGE, 0).takeIf { it > 0 },
            exercise = intent.getStringExtra(EXTRA_EXERCISE)?.takeIf { it.isNotBlank() },
            capturedAt = System.currentTimeMillis(), imageUri = imageUri
        )
        return store.saveNewAttempt(attempt)
    }

    private fun copyIntoPrivateStorage(uri: Uri): File? = runCatching {
        val dir = File(filesDir, "student_work").apply { mkdirs() }
        val file = File(dir, "attempt-${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return null
        file
    }.getOrNull()

    companion object {
        private const val STATE_PENDING_CAMERA = "pending_camera_name"
        const val EXTRA_ASSIGNMENT_FINGERPRINT = "assignment_fingerprint"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_PAGE = "page"
        const val EXTRA_EXERCISE = "exercise"
    }
}
