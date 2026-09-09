package com.hamza.studyhub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.hamza.studyhub.ui.HamzaUi
import com.hamza.studyhub.webuntis.WebUntisConfigStore
import com.hamza.studyhub.webuntis.WebUntisSyncWorker

class LaunchActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var qrText: EditText

    private val chooseQrImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) readQrFromImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (WebUntisConfigStore.isConfigured(this)) {
            WebUntisSyncWorker.schedule(this)
            openHome()
            return
        }
        setContentView(buildSetupScreen())
    }

    private fun buildSetupScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(HamzaUi.dp(this@LaunchActivity, 18), HamzaUi.dp(this@LaunchActivity, 24), HamzaUi.dp(this@LaunchActivity, 18), HamzaUi.dp(this@LaunchActivity, 28))
            setBackgroundColor(HamzaUi.bg)
        }
        content.addView(HamzaUi.title(this, "إعداد Hamza Study Hub"))
        content.addView(HamzaUi.subtitle(this, "اربط WebUntis مرة واحدة، وبعدها يشتغل كمصدر رسمي تلقائيًا."), HamzaUi.marginTop(this, 5))

        val card = HamzaUi.card(this)
        val body = HamzaUi.cardContent(card)
        body.addView(HamzaUi.title(this, "ربط WebUntis", 19f))
        body.addView(HamzaUi.subtitle(this,
            "من WebUntis على الكمبيوتر افتح Profil / Freigaben أو Data access، اعرض QR الخاص بـ Untis Mobile ثم اختار صورته هنا."), HamzaUi.marginTop(this, 6))
        body.addView(HamzaUi.primaryButton(this, "اختيار صورة QR").apply {
            tag = "setup_pick_qr"; setOnClickListener { chooseQrImage.launch("image/*") }
        }, HamzaUi.marginTop(this, 12))
        content.addView(card, HamzaUi.marginTop(this, 16))

        content.addView(HamzaUi.section(this, "أو استخدم نص QR"))
        qrText = EditText(this).apply {
            hint = "untis://setschool?url=..."
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(HamzaUi.dp(this@LaunchActivity, 12), HamzaUi.dp(this@LaunchActivity, 10), HamzaUi.dp(this@LaunchActivity, 12), HamzaUi.dp(this@LaunchActivity, 10))
            background = HamzaUi.rounded(0xFFFFFFFF.toInt(), HamzaUi.dp(this@LaunchActivity, 14).toFloat())
        }
        content.addView(qrText)
        content.addView(HamzaUi.primaryButton(this, "ربط WebUntis الآن").apply {
            tag = "setup_connect_untis"; setOnClickListener { connectQr(qrText.text?.toString().orEmpty()) }
        }, HamzaUi.marginTop(this, 10))

        statusText = HamzaUi.statusBox(this, "جاهز للربط.", 0xFFF0F4F8.toInt(), HamzaUi.muted)
        content.addView(statusText, HamzaUi.marginTop(this, 12))

        content.addView(HamzaUi.secondaryButton(this, "فتح البرنامج بدون ربط الآن").apply {
            tag = "setup_skip"; setOnClickListener { openHome() }
        }, HamzaUi.marginTop(this, 14))

        content.addView(HamzaUi.subtitle(this,
            "بيانات WebUntis تظل داخل التطبيق على الموبايل. يمكنك إدارة المصادر لاحقًا من لوحة المتابعة."), HamzaUi.marginTop(this, 14))
        return ScrollView(this).apply { isFillViewport = true; addView(content) }
    }

    private fun readQrFromImage(uri: Uri) {
        statusText.text = getString(R.string.qr_reading)
        runCatching { InputImage.fromFilePath(this, uri) }
            .onSuccess { image -> BarcodeScanning.getClient().process(image)
                .addOnSuccessListener { barcodes ->
                    val qr = barcodes.mapNotNull { it.rawValue }.firstOrNull { it.startsWith("untis://setschool", ignoreCase = true) }
                    if (qr == null) statusText.text = getString(R.string.qr_image_invalid)
                    else { qrText.setText(qr); connectQr(qr) }
                }
                .addOnFailureListener { statusText.text = getString(R.string.qr_read_failed) } }
            .onFailure { statusText.text = getString(R.string.image_open_failed) }
    }

    private fun connectQr(raw: String) {
        val parsed = WebUntisConfigStore.parseQr(raw)
        if (parsed == null) {
            statusText.text = getString(R.string.qr_invalid)
            return
        }
        WebUntisConfigStore.save(this, raw)
        WebUntisSyncWorker.schedule(this)
        statusText.text = getString(R.string.untis_connected_first_sync, parsed.server)
        Toast.makeText(this, "تم ربط WebUntis", Toast.LENGTH_SHORT).show()
        statusText.postDelayed({ openHome() }, 800)
    }

    private fun openHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
