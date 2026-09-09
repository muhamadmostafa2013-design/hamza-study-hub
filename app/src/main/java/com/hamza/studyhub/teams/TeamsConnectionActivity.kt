package com.hamza.studyhub.teams

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.hamza.studyhub.ui.HamzaUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TeamsConnectionActivity : AppCompatActivity() {
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(
                HamzaUi.dp(this@TeamsConnectionActivity, 18),
                HamzaUi.dp(this@TeamsConnectionActivity, 24),
                HamzaUi.dp(this@TeamsConnectionActivity, 18),
                HamzaUi.dp(this@TeamsConnectionActivity, 28)
            )
            setBackgroundColor(HamzaUi.bg)
        }
        content.addView(HamzaUi.title(this, "Microsoft Teams"))
        content.addView(
            HamzaUi.subtitle(this, "قراءة الواجبات الرسمية حتى لو Teams لم يرسل إشعارًا."),
            HamzaUi.marginTop(this, 4)
        )

        val statusCard = HamzaUi.card(this)
        val statusBody = HamzaUi.cardContent(statusCard)
        statusBody.addView(HamzaUi.title(this, "حالة الاتصال", 18f))
        statusText = HamzaUi.statusBox(this, "", 0xFFF0F4F8.toInt(), HamzaUi.muted)
        statusBody.addView(statusText, HamzaUi.marginTop(this, 8))
        content.addView(statusCard, HamzaUi.marginTop(this, 14))

        content.addView(HamzaUi.section(this, "حساب المدرسة"))
        val schoolCard = HamzaUi.card(this)
        val schoolBody = HamzaUi.cardContent(schoolCard)
        schoolBody.addView(HamzaUi.title(this, "Beverly Hills Schools", 18f))
        schoolBody.addView(
            HamzaUi.subtitle(this, "التطبيق مربوط بتسجيل Microsoft Entra الخاص بحمزة ومقيد بمؤسسة المدرسة."),
            HamzaUi.marginTop(this, 4)
        )
        schoolBody.addView(
            HamzaUi.statusBox(
                this,
                "الصلاحية: EduAssignments.ReadBasic\nقراءة الواجبات بدون درجات وبدون صلاحية كتابة.",
                0xFFEAF6EF.toInt(),
                HamzaUi.green
            ),
            HamzaUi.marginTop(this, 10)
        )
        content.addView(schoolCard)

        content.addView(HamzaUi.section(this, "الاتصال والمزامنة"))
        content.addView(HamzaUi.primaryButton(this, "تسجيل دخول حساب حمزة + أول مزامنة").apply {
            tag = "teams_connect"
            setOnClickListener { connectAndSync() }
        })
        content.addView(HamzaUi.secondaryButton(this, "مزامنة Teams الآن", HamzaUi.purple).apply {
            tag = "teams_sync_now"
            setOnClickListener {
                TeamsSyncWorker.syncNow(this@TeamsConnectionActivity)
                showStatus("تم طلب مزامنة Teams الآن.")
            }
        }, HamzaUi.marginTop(this, 8))

        content.addView(HamzaUi.section(this, "إعداد متقدم"))
        val redirect = runCatching { TeamsAuthStore.redirectUri(this) }
            .getOrElse { "تعذر حساب Redirect URI: ${it.message}" }
        val advancedCard = HamzaUi.card(this)
        val advancedBody = HamzaUi.cardContent(advancedCard)
        advancedBody.addView(HamzaUi.subtitle(this, "Android Redirect URI"))
        advancedBody.addView(TextView(this).apply {
            text = redirect
            textSize = 13.5f
            setTextIsSelectable(true)
            setTextColor(HamzaUi.ink)
            gravity = Gravity.START
        }, HamzaUi.marginTop(this, 4))
        val advancedActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        advancedActions.addView(HamzaUi.secondaryButton(this, "نسخ Redirect URI").apply {
            tag = "teams_copy_redirect"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Hamza Teams redirect URI", redirect))
                showStatus("تم نسخ Redirect URI.")
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = HamzaUi.dp(this@TeamsConnectionActivity, 5)
        })
        advancedActions.addView(HamzaUi.secondaryButton(this, "فتح Entra").apply {
            tag = "teams_open_entra"
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, "https://entra.microsoft.com/".toUri())) }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = HamzaUi.dp(this@TeamsConnectionActivity, 5)
        })
        advancedBody.addView(advancedActions, HamzaUi.marginTop(this, 10))
        content.addView(advancedCard)

        content.addView(
            HamzaUi.statusBox(
                this,
                "إذا ظهر طلب Admin Approval، فده معناه إن إدارة IT في المدرسة لازم توافق على صلاحية القراءة مرة واحدة.",
                0xFFFFF4E8.toInt(),
                HamzaUi.amber
            ),
            HamzaUi.marginTop(this, 14)
        )
        return ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
    }

    private fun connectAndSync() {
        showStatus("جاري فتح تسجيل دخول Microsoft…")
        TeamsAuthManager.signInAndSync(
            activity = this,
            onStatus = { message -> runOnUiThread { showStatus(message) } },
            onFinished = { result ->
                result.onSuccess { summary ->
                    showStatus("تم ربط Teams. جديد: ${summary.discovered} • متعدل: ${summary.updated} • يحتاج انتباه: ${summary.attention}")
                }.onFailure { error ->
                    showStatus("تعذر ربط Teams: ${error.message ?: error.javaClass.simpleName}")
                }
            }
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
            else -> "جاهز لتسجيل دخول حساب حمزة."
        }
    }

    private fun showStatus(message: String) {
        statusText.text = message
    }

    private fun formatTime(timestamp: Long) =
        SimpleDateFormat("dd/MM/yyyy • hh:mm a", Locale.getDefault()).format(Date(timestamp))
}
