package com.hamza.studyhub

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.hamza.studyhub.agents.SchoolFeedProjector
import com.hamza.studyhub.certificates.CertificateStorage
import com.hamza.studyhub.certificates.CertificatesActivity
import com.hamza.studyhub.learning.LearningEvidenceStore
import com.hamza.studyhub.learning.PendingCameraCapture
import com.hamza.studyhub.learning.StudentWorkCaptureActivity
import com.hamza.studyhub.sources.SchoolFeedStore
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class PrivateWorkflowRegressionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        listOf("school_notifications.jsonl", "student_attempts.jsonl", "student_work").forEach {
            File(context.filesDir, it).deleteRecursively()
        }
        File(context.noBackupFilesDir, "local-certificates").deleteRecursively()
    }

    @Test fun androidSharedTextIsNotSavedWithoutConfirmation() {
        val snippet = "واجب تجريبي صفحة ١٢ تمرين ٣"
        val cancelled = Robolectric.buildActivity(MainActivity::class.java, shareIntent(snippet)).setup()
        assertTrue(SchoolFeedStore.read(context).isEmpty())
        reviewDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idleMain()
        assertTrue(SchoolFeedStore.read(context).isEmpty())
        val dismissedReview = reviewDialog()
        cancelled.recreate()
        assertTrue(ShadowDialog.getLatestDialog() === dismissedReview)
        cancelled.pause().stop().destroy()

        val unrelated = "SYNTHETIC CONTACT 000"
        val confirmed = Robolectric.buildActivity(
            MainActivity::class.java, shareIntent("$snippet\n$unrelated")
        ).setup()
        val editableReview = reviewDialog()
        val editor = views(checkNotNull(editableReview.window).decorView).filterIsInstance<EditText>().single()
        assertTrue(editor.text.toString().contains(unrelated))
        editor.setText(snippet)
        editableReview.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleMain()
        val saved = SchoolFeedStore.read(context).single()
        assertEquals(snippet, saved.getString("text"))
        assertFalse(saved.toString().contains(unrelated))
        assertEquals("Shared", saved.getString("source"))
        assertEquals("Shared text", saved.getString("importMethod"))
        val savedReview = reviewDialog()
        confirmed.recreate()
        assertTrue(ShadowDialog.getLatestDialog() === savedReview)
        assertEquals(1, SchoolFeedStore.read(context).size)
        confirmed.pause().stop().destroy()
    }

    @Test fun pendingCameraResultRestoresAcrossActivityRecreation() {
        val launch = Intent(context, StudentWorkCaptureActivity::class.java)
            .putExtra(StudentWorkCaptureActivity.EXTRA_ASSIGNMENT_FINGERPRINT, "synthetic-camera")
            .putExtra(StudentWorkCaptureActivity.EXTRA_PAGE, 12)
        val cameraScreen = Robolectric.buildActivity(StudentWorkCaptureActivity::class.java, launch).setup()
        val activity = cameraScreen.get()
        val cameraButton = activity.window.decorView.findViewWithTag<View>("attempt_camera")
        assertTrue(cameraButton.performClick())

        val request = checkNotNull(shadowOf(activity).nextStartedActivityForResult)
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, request.intent.action)
        val output = checkNotNull(request.intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT))
        val stagedPhoto = checkNotNull(PendingCameraCapture.resolve(context.filesDir, output.lastPathSegment))
        // A stand-in for bytes written by the external camera. OCR itself isn't under test here.
        val capturedBytes = byteArrayOf(16, 32, 48, 64)
        stagedPhoto.writeBytes(capturedBytes)

        cameraScreen.recreate()
        assertTrue(cameraScreen.get().activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, null))
        idleMain()
        val attempt = LearningEvidenceStore(context).attempts().single()
        assertEquals("synthetic-camera", attempt.assignmentFingerprint)
        assertEquals(12, attempt.page)
        val storedFile = File(checkNotNull(Uri.parse(checkNotNull(attempt.imageUri)).path))
        assertEquals(stagedPhoto.canonicalFile, storedFile.canonicalFile)
        assertArrayEquals(capturedBytes, storedFile.readBytes())
        cameraScreen.pause().stop().destroy()
    }

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl")
    fun arabicTodayUsesRtlAndExposesTitleAsHeading() {
        assertTodayLayout(View.LAYOUT_DIRECTION_RTL, "ar")
    }

    @Test
    @Config(qualifiers = "de-rDE-ldltr")
    fun germanTodayUsesLtrAndExposesTitleAsHeading() {
        assertTodayLayout(View.LAYOUT_DIRECTION_LTR, "de")
    }

    @Test fun currentProjectionRetainsSavedHistoryAndPartialProgress() {
        val day = LocalDate.of(2026, 9, 16)
        val now = day.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        fun homework(id: String, due: Int, time: Long, progress: Int) = JSONObject().apply {
            put("externalId", id)
            put("title", "Synthetic assignment")
            put("dueDate", due)
            put("timestamp", time)
            put("completed", false)
            put("progressPercent", progress)
            put("isNew", true)
            put("syncChange", "unchanged")
        }
        SchoolFeedStore.append(context, homework("past", 20260910, now - 6L * 86_400_000, 10))
        SchoolFeedStore.append(context, homework("current", 20260917, now, 60))
        val storeFile = File(context.filesDir, "school_notifications.jsonl")
        val persistedBytes = storeFile.readBytes()

        val current = SchoolFeedProjector.current(SchoolFeedStore.read(context), day, now).single()
        assertEquals("current", current.optString("externalId"))
        assertEquals(60, current.optInt("progressPercent"))
        assertEquals(listOf("past", "current"), SchoolFeedStore.read(context).map { it.optString("externalId") })
        assertArrayEquals(persistedBytes, storeFile.readBytes())
    }

    @Test fun certificateListReadsOnlyPrivateNoBackupDirectory() {
        val recordName = CertificateStorage.newId()
        val folder = File(context.noBackupFilesDir, "local-certificates").apply { mkdirs() }
        File(folder, recordName).writeText("%PDF-synthetic test")
        File(folder, "import-unfinished.part").writeText("%PDF-synthetic temporary test")
        File(context.filesDir, CertificateStorage.newId()).writeText("%PDF-synthetic outside list")

        val controller = Robolectric.buildActivity(CertificatesActivity::class.java).setup()
        fun certificateButtons(label: Int) = views(controller.get().window.decorView).filterIsInstance<TextView>()
            .filter { it.text.toString() == controller.get().getString(label) }.toList()
        assertEquals(1, certificateButtons(R.string.certificates_open).size)
        val remove = certificateButtons(R.string.certificates_remove).single()
        assertTrue(remove.performClick())
        reviewDialog().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idleMain()
        assertTrue(File(folder, recordName).isFile)
        assertTrue(remove.performClick())
        reviewDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleMain()
        assertFalse(File(folder, recordName).exists())
        assertEquals(0, certificateButtons(R.string.certificates_open).size)
        controller.pause().stop().destroy()
    }

    private fun assertTodayLayout(expectedDirection: Int, language: String) {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val activity = controller.get()
        val all = views(activity.window.decorView).toList()
        val scroll = all.filterIsInstance<ScrollView>().first()
        val headline = all.filterIsInstance<TextView>().first { it.text.toString() == activity.getString(R.string.today_title) }
        assertEquals(expectedDirection, scroll.layoutDirection)
        assertEquals(language, headline.textLocale.language)
        assertTrue(ViewCompat.isAccessibilityHeading(headline))
        controller.pause().stop().destroy()
    }

    private fun shareIntent(text: String) = Intent(Intent.ACTION_SEND).apply {
        setClass(context, MainActivity::class.java)
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }

    private fun reviewDialog() = ShadowDialog.getLatestDialog() as AlertDialog
    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun views(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) for (index in 0 until root.childCount) yieldAll(views(root.getChildAt(index)))
    }
}
