package com.hamza.studyhub.certificates

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal object PdfIpc {
    const val VALIDATE = -1
    const val DESCRIPTOR = "pdf_descriptor"
    const val IMAGE = "pdf_image"
    const val MAX_IMAGE_BYTES = 384 * 1024
    const val MAX_WIDTH = 1000
    const val MAX_HEIGHT = 1300
}

/** The service has no access to app storage or network; each request gives read access to only one staged PDF. */
class IsolatedPdfRenderer : Service() {
    private val worker = HandlerThread("private-pdf-renderer")
    private lateinit var incoming: Messenger

    override fun onCreate() {
        super.onCreate()
        worker.start()
        incoming = Messenger(Handler(worker.looper) { request ->
            val reply = request.replyTo
            val pageIndex = request.arg1
            val response = Message.obtain()
            try {
                @Suppress("DEPRECATION")
                val descriptor = checkNotNull(request.data.getParcelable<ParcelFileDescriptor>(PdfIpc.DESCRIPTOR))
                val renderer = try {
                    PdfRenderer(descriptor)
                } catch (failure: Exception) {
                    descriptor.close()
                    throw failure
                }
                renderer.use { pdf ->
                    val pages = pdf.pageCount
                    require(pages in 1..CertificateStorage.MAX_PAGES)
                    require(pageIndex == PdfIpc.VALIDATE || pageIndex in 0 until pages)
                    pdf.openPage(if (pageIndex == PdfIpc.VALIDATE) 0 else pageIndex).use { page ->
                        if (pageIndex != PdfIpc.VALIDATE) response.data.putByteArray(PdfIpc.IMAGE, render(page))
                    }
                    response.arg1 = pages
                }
            } catch (_: Exception) {
                response.arg1 = 0
            }
            runCatching { reply?.send(response) }
            true
        })
    }

    private fun render(page: PdfRenderer.Page): ByteArray {
        require(page.width > 0 && page.height > 0)
        val scale = minOf(PdfIpc.MAX_WIDTH.toFloat() / page.width, PdfIpc.MAX_HEIGHT.toFloat() / page.height)
        val image = Bitmap.createBitmap(
            (page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        try {
            image.eraseColor(Color.WHITE)
            val matrix = Matrix().apply { setScale(scale, scale) }
            page.render(image, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            for (quality in intArrayOf(85, 65, 45, 30)) {
                val bytes = ByteArrayOutputStream()
                check(image.compress(Bitmap.CompressFormat.JPEG, quality, bytes))
                if (bytes.size() <= PdfIpc.MAX_IMAGE_BYTES) return bytes.toByteArray()
            }
            error("PDF preview exceeds the IPC size limit")
        } finally {
            image.recycle()
        }
    }

    override fun onBind(intent: Intent): IBinder = incoming.binder

    override fun onDestroy() {
        worker.quitSafely()
        super.onDestroy()
    }
}

internal object IsolatedPdfClient {
    data class Result(val pages: Int, val image: Bitmap?)

    /** Work-thread only. Both the binding and read descriptor are released on every policy/error path. */
    fun request(context: Context, file: File, page: Int): Result? {
        check(Looper.myLooper() != Looper.getMainLooper())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            requestBound(context.applicationContext, descriptor, page)
        }
    }

    private fun requestBound(context: Context, descriptor: ParcelFileDescriptor, page: Int): Result? {
        val completed = CountDownLatch(1)
        val reply = AtomicReference<Message?>(null)
        val receiver = Messenger(Handler(Looper.getMainLooper()) { message ->
            reply.set(Message.obtain(message))
            completed.countDown()
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (completed.count == 0L) return
                val request = Message.obtain().apply {
                    arg1 = page
                    replyTo = receiver
                    data = Bundle().apply { putParcelable(PdfIpc.DESCRIPTOR, descriptor) }
                }
                if (runCatching { Messenger(binder).send(request) }.isFailure) completed.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = completed.countDown()
            override fun onBindingDied(name: ComponentName) = completed.countDown()
            override fun onNullBinding(name: ComponentName) = completed.countDown()
        }
        val bound = context.bindService(Intent(context, IsolatedPdfRenderer::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!bound) return null
        try {
            if (!completed.await(10, TimeUnit.SECONDS)) return null
            val result = reply.get() ?: return null
            val pages = result.arg1.takeIf { it in 1..CertificateStorage.MAX_PAGES } ?: return null
            if (page == PdfIpc.VALIDATE) return Result(pages, null)
            if (page !in 0 until pages) return null
            val bytes = result.data.getByteArray(PdfIpc.IMAGE)
                ?.takeIf { it.isNotEmpty() && it.size <= PdfIpc.MAX_IMAGE_BYTES } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..PdfIpc.MAX_WIDTH || bounds.outHeight !in 1..PdfIpc.MAX_HEIGHT) return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { Result(pages, it) }
        } finally {
            completed.countDown()
            context.unbindService(connection)
        }
    }
}
