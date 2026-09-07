package com.hamza.studyhub.books

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Private family Book Library. The user explicitly selects local PDF/image files.
 * We retain only Android's read permission + metadata; no book is uploaded here.
 */
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

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(24), dp(18), dp(18))
            setBackgroundColor(Color.rgb(246, 247, 251))
        }

        root.addView(TextView(this).apply {
            text = "📚 كتب حمزة"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(20, 24, 33))
        })

        root.addView(TextView(this).apply {
            text = "مكتبة خاصة لفهم مراجع الواجبات مثل: Deutsch S. 37 Nr. 3-5. الملفات تظل على جهازك ولا يتم نشرها."
            textSize = 14.5f
            gravity = Gravity.END
            setPadding(0, dp(6), 0, dp(10))
            setTextColor(Color.DKGRAY)
        })

        countText = TextView(this).apply {
            textSize = 14.5f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(38, 91, 145))
        }
        root.addView(countText)

        root.addView(Button(this).apply {
            text = "➕ إضافة كتاب PDF أو صورة"
            setOnClickListener { documentPicker.launch(arrayOf("application/pdf", "image/*")) }
        })

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(24))
        }

        root.addView(ScrollView(this).apply { addView(listContainer) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        return root
    }

    private fun handleSelectedDocument(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val displayName = queryDisplayName(uri) ?: "كتاب حمزة"
        val titleInput = EditText(this).apply {
            hint = "اسم الكتاب"
            setText(displayName.substringBeforeLast('.'))
        }
        val subjectInput = EditText(this).apply { hint = "المادة: Deutsch / HSU / Mathe..." }
        val editionInput = EditText(this).apply { hint = "الإصدار أو السنة (اختياري)" }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
            addView(titleInput)
            addView(subjectInput)
            addView(editionInput)
        }

        AlertDialog.Builder(this)
            .setTitle("بيانات الكتاب")
            .setMessage("اكتب المادة بدقة مرة واحدة؛ بعدها النظام يربط أرقام الصفحات بالكتاب تلقائيًا.")
            .setView(form)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = titleInput.text.toString().trim()
                        val subject = subjectInput.text.toString().trim()
                        if (title.isBlank()) {
                            titleInput.error = "اكتب اسم الكتاب"
                            return@setOnClickListener
                        }
                        if (subject.isBlank()) {
                            subjectInput.error = "اكتب المادة"
                            return@setOnClickListener
                        }

                        val mime = contentResolver.getType(uri)
                        val pageCount = if (mime == "application/pdf") pdfPageCount(uri) else 1
                        val edition = editionInput.text.toString().trim().takeIf { it.isNotBlank() }
                        val book = BookAsset(
                            id = stableBookId(title, subject, edition),
                            title = title,
                            subject = subject,
                            edition = edition,
                            localUri = uri.toString(),
                            mimeType = mime,
                            pageCount = pageCount,
                            aliases = setOf(displayName.substringBeforeLast('.'))
                        )
                        store.upsert(book)
                        dialog.dismiss()
                        refresh()
                    }
                }
                dialog.show()
            }
    }

    private fun refresh() {
        val books = store.allBooks()
        countText.text = if (books.isEmpty()) "لا توجد كتب مضافة بعد" else "${books.size} كتاب/ملف مرجعي"
        listContainer.removeAllViews()

        if (books.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "ابدأ بإضافة كتب Deutsch وHSU والمواد اللي الواجبات فيها بتذكر أرقام صفحات."
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(36), dp(18), dp(36))
                setTextColor(Color.GRAY)
            })
            return
        }

        books.forEach { book -> listContainer.addView(buildBookCard(book)) }
    }

    private fun buildBookCard(book: BookAsset): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.WHITE, dp(18))
            elevation = dp(2).toFloat()
        }

        card.addView(TextView(this).apply {
            text = "${book.subject} • ${book.title}"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(25, 28, 36))
        })
        card.addView(TextView(this).apply {
            text = buildString {
                book.pageCount?.let { append("📄 $it صفحة") }
                book.edition?.let {
                    if (isNotEmpty()) append(" • ")
                    append("إصدار $it")
                }
                if (!book.mimeType.isNullOrBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(if (book.mimeType == "application/pdf") "PDF" else "صورة")
                }
            }.ifBlank { "مرجع خاص" }
            textSize = 14f
            gravity = Gravity.END
            setPadding(0, dp(5), 0, dp(6))
            setTextColor(Color.DKGRAY)
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "فتح"
            setOnClickListener { openBook(book) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = "حذف"
            setOnClickListener { confirmDelete(book) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(buttons)

        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        return card
    }

    private fun openBook(book: BookAsset) {
        val uri = book.localUri?.let(Uri::parse) ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, book.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    private fun confirmDelete(book: BookAsset) {
        AlertDialog.Builder(this)
            .setTitle("حذف المرجع؟")
            .setMessage("هيتم حذف الربط من Hamza Study Hub فقط؛ ملف الكتاب الأصلي لن يُحذف.")
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حذف") { _, _ ->
                store.delete(book.id)
                refresh()
            }
            .show()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(0)
        }
    }

    private fun pdfPageCount(uri: Uri): Int? {
        return runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { it.pageCount }
            }
        }.getOrNull()
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
