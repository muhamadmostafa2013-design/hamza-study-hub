package com.hamza.studyhub.books

import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hamza.studyhub.ui.HamzaUi

class BookLibraryActivity : AppCompatActivity() {
    private lateinit var listContainer: LinearLayout
    private lateinit var countText: TextView
    private val store by lazy { BookLibraryStore(this) }

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleSelectedDocument(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::listContainer.isInitialized) refresh()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(HamzaUi.dp(this@BookLibraryActivity, 18), HamzaUi.dp(this@BookLibraryActivity, 24), HamzaUi.dp(this@BookLibraryActivity, 18), HamzaUi.dp(this@BookLibraryActivity, 28))
            setBackgroundColor(HamzaUi.bg)
        }
        content.addView(HamzaUi.title(this, "مكتبة حمزة"))
        content.addView(HamzaUi.subtitle(this, "اربط كتب المدرسة مرة واحدة ليعرف النظام الصفحة والتمرين المقصودين تلقائيًا."), HamzaUi.marginTop(this, 4))

        val headerCard = HamzaUi.card(this)
        val header = HamzaUi.cardContent(headerCard)
        countText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.END
            setTextColor(HamzaUi.ink)
        }
        header.addView(countText)
        header.addView(HamzaUi.primaryButton(this, "إضافة كتاب أو ملف مرجعي").apply {
            tag = "book_add"
            setOnClickListener { documentPicker.launch(arrayOf("application/pdf", "image/*")) }
        }, HamzaUi.marginTop(this, 10))
        content.addView(headerCard, HamzaUi.marginTop(this, 14))

        content.addView(HamzaUi.section(this, "الكتب المضافة"))
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listContainer)

        return ScrollView(this).apply { isFillViewport = true; addView(content) }
    }

    private fun handleSelectedDocument(uri: Uri) {
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val displayName = queryDisplayName(uri) ?: "كتاب حمزة"
        val titleInput = EditText(this).apply { hint = "اسم الكتاب"; setText(displayName.substringBeforeLast('.')) }
        val subjectInput = EditText(this).apply { hint = "المادة: Deutsch / HSU / Mathe..." }
        val editionInput = EditText(this).apply { hint = "الإصدار أو السنة (اختياري)" }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(HamzaUi.dp(this@BookLibraryActivity, 18), HamzaUi.dp(this@BookLibraryActivity, 8), HamzaUi.dp(this@BookLibraryActivity, 18), 0)
            addView(titleInput); addView(subjectInput); addView(editionInput)
        }
        AlertDialog.Builder(this)
            .setTitle("بيانات الكتاب")
            .setMessage("اكتب المادة بدقة. الملف يظل على جهازك ويُستخدم كمرجع خاص فقط.")
            .setView(form)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = titleInput.text.toString().trim()
                        val subject = subjectInput.text.toString().trim()
                        if (title.isBlank()) { titleInput.error = "اكتب اسم الكتاب"; return@setOnClickListener }
                        if (subject.isBlank()) { subjectInput.error = "اكتب المادة"; return@setOnClickListener }
                        val mime = contentResolver.getType(uri)
                        val edition = editionInput.text.toString().trim().takeIf { it.isNotBlank() }
                        store.upsert(BookAsset(
                            id = stableBookId(title, subject, edition),
                            title = title,
                            subject = subject,
                            edition = edition,
                            localUri = uri.toString(),
                            mimeType = mime,
                            pageCount = if (mime == "application/pdf") pdfPageCount(uri) else 1,
                            aliases = setOf(displayName.substringBeforeLast('.'))
                        ))
                        dialog.dismiss(); refresh()
                    }
                }
                dialog.show()
            }
    }

    private fun refresh() {
        val books = store.allBooks()
        countText.text = if (books.isEmpty()) "لا توجد كتب مضافة بعد" else "${books.size} كتاب/ملف مرجعي جاهز للربط"
        listContainer.removeAllViews()
        if (books.isEmpty()) {
            listContainer.addView(HamzaUi.statusBox(this,
                "ابدأ بكتب Deutsch وHSU والمواد التي تذكر الواجبات فيها أرقام صفحات.",
                0xFFEFF4FA.toInt(), HamzaUi.muted))
            return
        }
        books.forEach { book ->
            val card = HamzaUi.card(this)
            val body = HamzaUi.cardContent(card)
            body.addView(HamzaUi.title(this, "${book.subject} • ${book.title}", 18f))
            body.addView(HamzaUi.subtitle(this, buildString {
                book.pageCount?.let { append("$it صفحة") }
                book.edition?.let { if (isNotEmpty()) append(" • "); append("إصدار $it") }
                if (!book.mimeType.isNullOrBlank()) { if (isNotEmpty()) append(" • "); append(if (book.mimeType == "application/pdf") "PDF" else "صورة") }
            }.ifBlank { "مرجع خاص" }), HamzaUi.marginTop(this, 4))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(HamzaUi.secondaryButton(this, "فتح").apply {
                tag = "book_open_${book.id}"; setOnClickListener { openBook(book) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = HamzaUi.dp(this@BookLibraryActivity, 6) })
            actions.addView(HamzaUi.secondaryButton(this, "حذف", HamzaUi.danger).apply {
                tag = "book_delete_${book.id}"; setOnClickListener { confirmDelete(book) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = HamzaUi.dp(this@BookLibraryActivity, 6) })
            body.addView(actions, HamzaUi.marginTop(this, 10))
            listContainer.addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = HamzaUi.dp(this@BookLibraryActivity, 10) })
        }
    }

    private fun openBook(book: BookAsset) {
        val uri = book.localUri?.let(Uri::parse) ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, book.mimeType ?: "*/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }) }.onFailure {
            AlertDialog.Builder(this).setMessage("تعذر فتح الملف من التطبيق المسؤول على الجهاز.").setPositiveButton("تمام", null).show()
        }
    }

    private fun confirmDelete(book: BookAsset) {
        AlertDialog.Builder(this).setTitle("حذف المرجع؟")
            .setMessage("سيتم حذف الربط فقط؛ ملف الكتاب الأصلي لن يُحذف.")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف") { _, _ -> store.delete(book.id); refresh() }.show()
    }

    private fun queryDisplayName(uri: Uri): String? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (!c.moveToFirst()) null else c.getString(0)
    }

    private fun pdfPageCount(uri: Uri): Int? = runCatching {
        contentResolver.openFileDescriptor(uri, "r")?.use { d -> PdfRenderer(d).use { it.pageCount } }
    }.getOrNull()
}
