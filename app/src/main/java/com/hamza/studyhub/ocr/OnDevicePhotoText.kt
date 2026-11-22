package com.hamza.studyhub.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.hamza.studyhub.R
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Local recognition only. Text is a suggestion and must be reviewed before saving. */
internal object OnDevicePhotoText {
    enum class Language(val label: Int) {
        ARABIC(R.string.photo_ocr_arabic),
        LATIN(R.string.photo_ocr_latin)
    }

    private const val MODEL_ASSET = "tessdata/ara.traineddata"
    private const val MODEL_BYTES = 1432056L
    private const val MAX_IMAGE_EDGE = 2600
    private val worker = ThreadPoolExecutor(
        1, 1, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(2)
    ).apply { allowCoreThreadTimeOut(true) }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    fun recognize(context: Context, uri: Uri, language: Language, result: (String?) -> Unit) {
        val app = context.applicationContext
        try {
            worker.execute {
                try {
                    when (language) {
                        Language.ARABIC -> {
                            val text = readArabic(app, uri)
                            main.post { result(text) }
                        }
                        Language.LATIN -> readLatin(app, uri, result)
                    }
                } catch (_: Exception) {
                    main.post { result(null) }
                } catch (_: LinkageError) {
                    // If the native library cannot load, keep the existing photo and allow manual entry.
                    main.post { result(null) }
                }
            }
        } catch (_: RejectedExecutionException) {
            main.post { result(null) }
        }
    }

    private fun readLatin(context: Context, uri: Uri, result: (String?) -> Unit) {
        val bitmap = readBoundedImage(context, uri)
        val recognizer = try {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image).addOnCompleteListener { task ->
                val text = try {
                    if (task.isSuccessful) task.result.text.trim().takeIf(String::isNotBlank) else null
                } finally {
                    recognizer.close()
                    bitmap.recycle()
                }
                result(text)
            }
        } catch (error: Exception) {
            recognizer.close()
            bitmap.recycle()
            throw error
        }
    }

    private fun readArabic(context: Context, uri: Uri): String? {
        val dataRoot = ensureModel(context)
        val bitmap = readBoundedImage(context, uri)
        try {
            val engine = TessBaseAPI()
            try {
                if (!engine.init(dataRoot.path, "ara", TessBaseAPI.OEM_LSTM_ONLY)) return null
                engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                engine.setImage(bitmap)
                return engine.getUTF8Text()?.trim()?.takeIf(String::isNotBlank)
            } finally {
                engine.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun ensureModel(context: Context): File {
        val root = File(context.noBackupFilesDir, "arabic-ocr-v1")
        val model = File(root, MODEL_ASSET)
        if (model.length() == MODEL_BYTES) return root
        check(model.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        val atomic = AtomicFile(model)
        val output = atomic.startWrite()
        try {
            val bytes = context.assets.open(MODEL_ASSET).use { it.copyTo(output) }
            check(bytes == MODEL_BYTES)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
        return root
    }

    private fun readBoundedImage(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        checkNotNull(context.contentResolver.openInputStream(uri)).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        check(bounds.outWidth > 0 && bounds.outHeight > 0)
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / sample > MAX_IMAGE_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val bitmap = checkNotNull(context.contentResolver.openInputStream(uri)).use {
            checkNotNull(BitmapFactory.decodeStream(it, null, options))
        }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        if (matrix.isIdentity) return bitmap
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } finally {
            bitmap.recycle()
        }
    }
}
