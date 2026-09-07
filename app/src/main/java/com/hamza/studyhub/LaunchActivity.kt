package com.hamza.studyhub

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker

class LaunchActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var qrText: EditText

    private val chooseQrImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) readQrFromImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (WebUntisConfigStore.isConfigured(this)) {
            WebUntisSyncWorker.schedule(this)
            openMain()
            return
        }

        setContentView(buildSetupScreen())
    }

    private fun buildSetupScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(34), dp(22), dp(24))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(246, 247, 251))
        }

        root.addView(TextView(this).apply {
            text = "ربط WebUntis"
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(20, 24, 33))
        })

        root.addView(TextView(this).apply {
            text = "مرة واحدة فقط: هنستخدم QR الخاص بحساب حمزة علشان Hamza Study Hub يقدر يقرأ Hausaufgaben تلقائيًا من WebUntis. بيانات الدخول تفضل داخل التطبيق على الموبايل."
            textSize = 17f
            gravity = Gravity.END
            setPadding(0, dp(12), 0, dp(14))
            setTextColor(Color.DKGRAY)
        })

        root.addView(TextView(this).apply {
            text = "الأسهل:\n1) افتح WebUntis على الكمبيوتر.\n2) افتح Profil / Freigaben أو Data access.\n3) اعرض QR الخاص بـ Untis Mobile.\n4) اعمل Screenshot للـ QR وابعتها للموبايل.\n5) اضغط الزر تحت واختار الصورة."
            textSize = 16f
            gravity = Gravity.END
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(231, 243, 255))
            setTextColor(Color.rgb(33, 70, 104))
        })

        val imageButton = Button(this).apply {
            text = "اختيار صورة QR من الهاتف"
            setOnClickListener { chooseQrImage.launch("image/*") }
        }
        root.addView(imageButton)

        root.addView(TextView(this).apply {
            text = "أو لو عندك نص QR اللي بيبدأ بـ untis://setschool الصقه هنا:"
            textSize = 14f
            gravity = Gravity.END
            setPadding(0, dp(12), 0, dp(6))
            setTextColor(Color.GRAY)
        })

        qrText = EditText(this).apply {
            hint = "untis://setschool?url=..."
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        }
        root.addView(qrText)

        root.addView(Button(this).apply {
            text = "ربط WebUntis الآن"
            setOnClickListener {
                connectQr(qrText.text?.toString().orEmpty())
            }
        })

        statusText = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.END
            setPadding(0, dp(10), 0, dp(10))
            setTextColor(Color.rgb(52, 124, 89))
        }
        root.addView(statusText)

        root.addView(Button(this).apply {
            text = "فتح البرنامج بدون ربط الآن"
            setOnClickListener { openMain() }
        })

        return root
    }

    private fun readQrFromImage(uri: Uri) {
        statusText.text = "⏳ جاري قراءة QR..."

        runCatching { InputImage.fromFilePath(this, uri) }
            .onSuccess { image ->
                BarcodeScanning.getClient()
                    .process(image)
                    .addOnSuccessListener { barcodes ->
                        val qr = barcodes
                            .mapNotNull { it.rawValue }
                            .firstOrNull { it.startsWith("untis://setschool", ignoreCase = true) }

                        if (qr == null) {
                            statusText.text = "⚠️ الصورة لا تحتوي QR صالح لـ WebUntis"
                        } else {
                            qrText.setText(qr)
                            connectQr(qr)
                        }
                    }
                    .addOnFailureListener {
                        statusText.text = "⚠️ لم أستطع قراءة QR من الصورة"
                    }
            }
            .onFailure {
                statusText.text = "⚠️ لم أستطع فتح الصورة"
            }
    }

    private fun connectQr(raw: String) {
        val parsed = WebUntisConfigStore.parseQr(raw)
        if (parsed == null) {
            statusText.text = "⚠️ QR غير صالح. لازم يبدأ بـ untis://setschool"
            return
        }

        WebUntisConfigStore.save(this, raw)
        WebUntisSyncWorker.schedule(this)

        statusText.text = "✅ تم ربط ${parsed.server}. جاري أول مزامنة للواجبات..."
        Toast.makeText(this, "تم ربط WebUntis", Toast.LENGTH_SHORT).show()

        statusText.postDelayed({ openMain() }, 900)
    }

    private fun openMain() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
