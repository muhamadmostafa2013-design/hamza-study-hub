package com.hamza.studyhub.teams

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hamza.studyhub.ui.HamzaUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TeamsConnectionActivity : AppCompatActivity() {
    private lateinit var clientIdInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        refreshStatus()
    }

    override fun onResume() {
        super.onResume(); refreshStatus()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(HamzaUi.dp(this@TeamsConnectionActivity, 18), HamzaUi.dp(this@TeamsConnectionActivity, 24), HamzaUi.dp(this@TeamsConnectionActivity, 18), HamzaUi.dp(this@TeamsConnectionActivity, 28))
            setBackgroundColor(HamzaUi.bg)
        }
        content.addView(HamzaUi.title(this, "Microsoft Teams"))
        content.addView(HamzaUi.subtitle(this, "Deep Sync يلتقط Assignments من Microsoft حتى لو لم يصل Notification."), HamzaUi.marginTop(this, 4))

        val statusCard = HamzaUi.card(this)
        val statusBody = HamzaUi.cardContent(statusCard)
        statusBody.addView(HamzaUi.title(this, "حالة الاتصال", 18f))
        statusText = HamzaUi.statusBox(this, "", 0xFFF0F4F8.toInt(), HamzaUi.muted)
        statusBody.addView(statusText, HamzaUi.marginTop(this, 8))
        content.addView(statusCard, HamzaUi.marginTop(this, 14))

        content.addView(HamzaUi.section(this, "إعداد Microsoft Entra"))
        val redirect = runCatching { TeamsAuthStore.redirectUri(this) }.getOrElse { "تعذر حساب Redirect URI: ${it.message}" }
        val setupCard = HamzaUi.card(this)
        val setup = HamzaUi.cardContent(setupCard)
        setup.addView(HamzaUi.subtitle(this, "Package name"))
        setup.addView(TextView(this).apply { text = packageName; textSize = 14.5f; setTextIsSelectable(true); setTextColor(HamzaUi.ink) })
        setup.addView(HamzaUi.subtitle(this, "Redirect URI"), HamzaUi.marginTop(this, 9))
        setup.addView(TextView(this).apply { text = redirect; textSize = 14f; setTextIsSelectable(true); setTextColor(HamzaUi.ink); gravity = Gravity.START })
        val setupActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        setupActions.addView(HamzaUi.secondaryButton(this, "نسخ Redirect URI").apply {
            tag = "teams_copy_redirect"; setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Hamza Teams redirect URI", redirect)); showStatus("تم نسخ Redirect URI.")
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = HamzaUi.dp(this@TeamsConnectionActivity, 5) })
        setupActions.addView(HamzaUi.secondaryButton(this, "فتح Entra").apply {
            tag = "teams_open_entra"; setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://entra.microsoft.com/"))) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = HamzaUi.dp(this@TeamsConnectionActivity, 5) })
        setup.addView(setupActions, HamzaUi.marginTop(this, 10))
        content.addView(setupCard)

        content.addView(HamzaUi.section(this, "Application (client) ID"))
        clientIdInput = EditText(this).apply {
            hint = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            setSingleLine(true); gravity = Gravity.START
            setText(TeamsAuthStore.clientId(this@TeamsConnectionActivity).orEmpty())
            background = HamzaUi.rounded(0xFFFFFFFF.toInt(), HamzaUi.dp(this@TeamsConnectionActivity, 13).toFloat())
            setPadding(HamzaUi.dp(this@TeamsConnectionActivity, 12), HamzaUi.dp(this@TeamsConnectionActivity, 10), HamzaUi.dp(this@TeamsConnectionActivity, 12), HamzaUi.dp(this@TeamsConnectionActivity, 10))
        }
        content.addView(clientIdInput)
        content.addView(HamzaUi.secondaryButton(this, "حفظ Client ID").apply {
            tag = "teams_save_client"; setOnClickListener { saveClientId() }
        }, HamzaUi.marginTop(this, 8))

        content.addView(HamzaUi.section(this, "الاتصال والمزامنة"))
        content.addView(HamzaUi.primaryButton(this, "تسجيل دخول حساب حمزة + أول Deep Sync").apply {
            tag = "teams_connect"; setOnClickListener { connectAndSync() }
        })
        content.addView(HamzaUi.secondaryButton(this, "مزامنة Teams الآن", HamzaUi.purple).apply {
            tag = "teams_sync_now"; setOnClickListener {
                if (!TeamsAuthStore.isConfigured(this@TeamsConnectionActivity)) showStatus("احفظ Client ID الأول.")
                else { TeamsSyncWorker.syncNow(this@TeamsConnectionActivity); showStatus("تم طلب مزامنة Teams الآن.") }
            }
        }, HamzaUi.marginTop(this, 8))

        content.addView(HamzaUi.statusBox(this,
            "الصلاحية المطلوبة: Microsoft Graph → Delegated → EduAssignments.ReadBasic. لو المدرسة تمنع User Consent، Microsoft ستطلب Admin Approval.",
            0xFFEFF5FC.toInt(), HamzaUi.blue), HamzaUi.marginTop(this, 14))
        return ScrollView(this).apply { isFillViewport = true; addView(content) }
    }

    private fun saveClientId(): Boolean {
        val value = clientIdInput.text?.toString().orEmpty().trim()
        val valid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(value)
        if (!valid) { showStatus("Client ID غير صحيح. انسخ Application (client) ID من Entra."); return false }
        TeamsAuthStore.saveClientId(this, value); showStatus("تم حفظ Client ID على الجهاز."); return true
    }

    private fun connectAndSync() {
        if (!TeamsAuthStore.isConfigured(this) && !saveClientId()) return
        showStatus("جاري فتح تسجيل دخول Microsoft…")
        TeamsAuthManager.signInAndSync(
            activity = this,
            onStatus = { message -> runOnUiThread { showStatus(message) } },
            onFinished = { result -> result.onSuccess { summary ->
                showStatus("تم ربط Teams. جديد: ${summary.discovered} • متعدل: ${summary.updated} • يحتاج انتباه: ${summary.attention}")
            }.onFailure { error -> showStatus("تعذر ربط Teams: ${error.message ?: error.javaClass.simpleName}") } }
        )
    }

    private fun refreshStatus() {
        if (!::statusText.isInitialized) return
        val account = TeamsAuthStore.account(this)
        val last = TeamsAuthStore.lastSync(this)
        val error = TeamsAuthStore.lastError(this)
        statusText.text = when {
            !error.isNullOrBlank() -> "آخر محاولة واجهت مشكلة: ${error.take(180)}"
            account != null && last > 0 -> "متصل بـ $account\nآخر Deep Sync: ${formatTime(last)}"
            account != null -> "الحساب $account متصل • في انتظار أول Deep Sync مكتمل"
            TeamsAuthStore.isConfigured(this) -> "Client ID محفوظ • يحتاج تسجيل دخول حساب حمزة"
            else -> "Teams Deep Sync غير متصل بعد"
        }
    }

    private fun showStatus(message: String) { statusText.text = message }
    private fun formatTime(timestamp: Long) = SimpleDateFormat("dd/MM/yyyy • hh:mm a", Locale.getDefault()).format(Date(timestamp))
}
