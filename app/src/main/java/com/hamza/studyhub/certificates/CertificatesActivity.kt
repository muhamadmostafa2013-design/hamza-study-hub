package com.hamza.studyhub.certificates

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hamza.studyhub.R
import com.hamza.studyhub.ui.HamzaUi
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CertificatesActivity : AppCompatActivity() {
    private val work = Executors.newSingleThreadExecutor()
    private lateinit var list: LinearLayout
    private lateinit var status: android.widget.TextView
    private lateinit var importButton: View
    private val folder get() = File(noBackupFilesDir, "local-certificates")
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPdf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            setPadding(dp(18), dp(24), dp(18), dp(28))
            setBackgroundColor(HamzaUi.bg)
        }
        body.addView(HamzaUi.title(this, getString(R.string.certificates_title)))
        body.addView(HamzaUi.subtitle(this, getString(R.string.certificates_private)), HamzaUi.marginTop(this, 8))
        importButton = HamzaUi.primaryButton(this, getString(R.string.certificates_add)).apply {
            setOnClickListener {
                runCatching { picker.launch(arrayOf("application/pdf")) }
                    .onFailure { status.setText(R.string.certificates_picker_error) }
            }
        }
        body.addView(importButton, HamzaUi.marginTop(this, 18))
        status = HamzaUi.subtitle(this, "").apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(status, HamzaUi.marginTop(this, 8))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list, HamzaUi.marginTop(this, 8))
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        HamzaUi.insetScrollable(this, scroll, body, 18, 24, 28)
        setContentView(scroll)
        refresh()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                importStatus.collect { message ->
                    importButton.isEnabled = message != R.string.certificates_importing
                    if (message != 0) status.setText(message)
                    refresh()
                }
            }
        }
    }

    private fun refresh() {
        val records = folder.listFiles().orEmpty()
            .filter { CertificateStorage.fileIn(folder, it.name) == it && it.isFile }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
        list.removeAllViews()
        if (records.isEmpty()) list.addView(HamzaUi.subtitle(this, getString(R.string.certificates_empty)))
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        records.forEachIndexed { index, record ->
            val card = HamzaUi.card(this)
            val content = HamzaUi.cardContent(card).apply { layoutDirection = View.LAYOUT_DIRECTION_LOCALE }
            val label = getString(R.string.certificates_record, index + 1, dateFormat.format(Date(record.lastModified())))
            content.addView(HamzaUi.title(this, label, 17f))
            content.addView(HamzaUi.primaryButton(this, getString(R.string.certificates_open)).apply {
                contentDescription = getString(R.string.certificates_open_record, label)
                setOnClickListener { preview(record) }
            }, HamzaUi.marginTop(this, 10))
            content.addView(HamzaUi.secondaryButton(this, getString(R.string.certificates_remove)).apply {
                contentDescription = getString(R.string.certificates_remove_record, label)
                setOnClickListener {
                    AlertDialog.Builder(this@CertificatesActivity)
                        .setTitle(R.string.certificates_remove)
                        .setMessage(R.string.certificates_remove_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.certificates_remove) { _, _ ->
                            val removed = CertificateStorage.fileIn(folder, record.name)?.delete() == true
                            status.setText(if (removed) R.string.certificates_removed else R.string.certificates_error)
                            refresh()
                        }.show()
                }
            }, HamzaUi.marginTop(this, 8))
            list.addView(card, HamzaUi.marginTop(this, 10))
        }
    }

    private fun importPdf(uri: Uri) {
        if (!importRunning.compareAndSet(false, true)) return
        val app = applicationContext
        val resolver = app.contentResolver
        val folder = this.folder
        importStatus.value = R.string.certificates_importing
        importWork.execute {
            var staged: File? = null
            val message = try {
                check(folder.mkdirs() || folder.isDirectory)
                val destination = checkNotNull(CertificateStorage.fileIn(folder, CertificateStorage.newId()))
                val temp = File.createTempFile("import-", ".part", folder)
                staged = temp
                val source = resolver.openInputStream(uri) ?: throw CertificateStorage.InvalidPdf()
                source.use { input -> temp.outputStream().use { output -> CertificateStorage.copyPdf(input, output) } }
                checkNotNull(IsolatedPdfClient.request(app, temp, PdfIpc.VALIDATE))
                check(!destination.exists() && temp.renameTo(destination))
                R.string.certificates_imported
            } catch (_: CertificateStorage.PdfTooLarge) {
                R.string.certificates_too_large
            } catch (_: Exception) {
                R.string.certificates_error
            } finally {
                staged?.delete()
            }
            importRunning.set(false)
            importStatus.value = message
        }
    }

    private fun preview(record: File) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val caption = HamzaUi.subtitle(this, getString(R.string.certificates_loading))
        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.certificates_page_image)
        }
        layout.addView(caption)
        layout.addView(image, ViewGroup.LayoutParams.MATCH_PARENT, dp(340))
        val controls = LinearLayout(this).apply {
            orientation = if (resources.configuration.fontScale >= 1.5f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        layout.addView(controls)
        val previous = HamzaUi.secondaryButton(this, getString(R.string.certificates_previous))
        val next = HamzaUi.secondaryButton(this, getString(R.string.certificates_next))
        fun controlParams() = if (controls.orientation == LinearLayout.VERTICAL)
            LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f)
        controls.addView(previous, controlParams())
        controls.addView(next, controlParams())
        val previewScroll = ScrollView(this).apply { addView(layout) }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.certificates_title)
            .setView(previewScroll).setPositiveButton(android.R.string.ok, null).create()
        var pageIndex = 0
        var bitmap: Bitmap? = null
        fun display(page: Int) {
            previous.isEnabled = false
            next.isEnabled = false
            work.execute {
                val result = runCatching {
                    val file = checkNotNull(CertificateStorage.fileIn(folder, record.name))
                    val result = checkNotNull(IsolatedPdfClient.request(applicationContext, file, page))
                    checkNotNull(result.image) to result.pages
                }.getOrNull()
                runOnUiThread {
                    if (!dialog.isShowing || isDestroyed || isFinishing) {
                        result?.first?.recycle()
                    } else if (result == null) {
                        caption.setText(R.string.certificates_error)
                    } else {
                        pageIndex = page
                        val old = bitmap
                        bitmap = result.first
                        image.setImageBitmap(bitmap)
                        old?.recycle()
                        caption.text = getString(R.string.certificates_page, page + 1, result.second)
                        previous.isEnabled = page > 0
                        next.isEnabled = page + 1 < result.second
                    }
                }
            }
        }
        previous.setOnClickListener { display(pageIndex - 1) }
        next.setOnClickListener { display(pageIndex + 1) }
        dialog.setOnDismissListener { image.setImageDrawable(null); bitmap?.recycle(); bitmap = null }
        dialog.show()
        display(0)
    }

    private companion object {
        val importWork = Executors.newSingleThreadExecutor()
        val importRunning = AtomicBoolean(false)
        val importStatus = MutableStateFlow(0)
    }

    override fun onDestroy() {
        work.shutdown()
        super.onDestroy()
    }

    private fun dp(size: Int) = HamzaUi.dp(this, size)
}
