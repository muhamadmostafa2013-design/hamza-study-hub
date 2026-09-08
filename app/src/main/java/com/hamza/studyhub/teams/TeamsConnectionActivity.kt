package com.hamza.studyhub.teams

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
        super.onResume()
        refreshStatus()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            setBackgroundColor(Color.rgb(246, 247, 251))
        }

        content.addView(TextView(this).apply {
            text = "Microsoft Teams Deep Sync"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(20, 24, 33))
        })

        content.addView(TextView(this).apply {
            text = "المسار ده بيفحص Assignments من Microsoft مباشرة، فمش بيعتمد على إن Teams يبعت Notification لكل واجب."
            textSize = 16f
            gravity = Gravity.END
            setPadding(0, dp(7), 0, dp(14))
            setTextColor(Color.DKGRAY)
        })

        content.addView(infoCard(
            "🔐 تسجيل دخول آمن",
            "التطبيق Public Client: مفيش Client Secret جوه الـAPK، وكلمات المرور والتوكنات بيديرها Microsoft MSAL. إحنا بنطلب قراءة الواجبات فقط."
        ))

        content.addView(TextView(this).apply {
            text = "بيانات Android اللي هتحتاجها في Microsoft Entra"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, dp(18), 0, dp(6))
            setTextColor(Color.rgb(25, 28, 36))
        })

        val redirect = runCatching { TeamsAuthStore.redirectUri(this) }
            .getOrElse { "تعذر حساب Redirect URI: ${it.message}" }

        content.addView(TextView(this).apply {
            text = "Package name\n$packageName\n\nRedirect URI\n$redirect"
            textSize = 14.5f
            gravity = Gravity.START
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.WHITE, dp(14))
            setTextColor(Color.rgb(39, 55, 73))
            setTextIsSelectable(true)
        })

        content.addView(Button(this).apply {
            text = "نسخ Redirect URI"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Hamza Teams redirect URI", redirect))
                showStatus("✅ تم نسخ Redirect URI")
            }
        })

        content.addView(Button(this).apply {
            text = "فتح Microsoft Entra"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://entra.microsoft.com/")))
            }
        })

        content.addView(TextView(this).apply {
            text = "Application (client) ID"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setPadding(0, dp(18), 0, dp(5))
            setTextColor(Color.rgb(25, 28, 36))
        })

        clientIdInput = EditText(this).apply {
            hint = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            setSingleLine(true)
            gravity = Gravity.START
            setText(TeamsAuthStore.clientId(this@TeamsConnectionActivity).orEmpty())
        }
        content.addView(clientIdInput)

        content.addView(Button(this).apply {
            text = "حفظ Client ID"
            setOnClickListener { saveClientId() }
        })

        content.addView(Button(this).apply {
            text = "🔗 تسجيل دخول حساب حمزة + أول Deep Sync"
            setOnClickListener { connectAndSync() }
        })

        content.addView(Button(this).apply {
            text = "🔄 مزامنة Teams في الخلفية الآن"
            setOnClickListener {
                if (!TeamsAuthStore.isConfigured(this@TeamsConnectionActivity)) {
                    showStatus("⚠️ احفظ Client ID الأول")
                } else {
                    TeamsSyncWorker.syncNow(this@TeamsConnectionActivity)
                    showStatus("⏳ طلبت مزامنة Teams الآن. لو الجلسة ما زالت صالحة هتتم في الخلفية.")
                }
            }
        })

        statusText = TextView(this).apply {
            textSize = 14.5f
            gravity = Gravity.END
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.rgb(232, 246, 238), dp(14))
            setTextColor(Color.rgb(39, 104, 73))
        }
        content.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        content.addView(TextView(this).apply {
            text = "الصلاحية المطلوبة في Entra: Microsoft Graph → Delegated → EduAssignments.ReadBasic. لو المدرسة تمنع User Consent، هيظهر طلب Admin Approval بدل ما التطبيق يحاول يتجاوز سياسة المدرسة."
            textSize = 13.5f
            gravity = Gravity.END
            setPadding(0, dp(14), 0, 0)
            setTextColor(Color.GRAY)
        })

        return ScrollView(this).apply { addView(content) }
    }

    private fun saveClientId(): Boolean {
        val value = clientIdInput.text?.toString().orEmpty().trim()
        val valid = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            .matches(value)
        if (!valid) {
            showStatus("⚠️ Client ID مش بالشكل الصحيح. انسخ Application (client) ID من صفحة Overview في Entra.")
            return false
        }

        TeamsAuthStore.saveClientId(this, value)
        showStatus("✅ تم حفظ Client ID على الموبايل. مفيش Client Secret مطلوب.")
        return true
    }

    private fun connectAndSync() {
        if (!TeamsAuthStore.isConfigured(this) && !saveClientId()) return

        showStatus("⏳ جاري فتح تسجيل دخول Microsoft...")
        TeamsAuthManager.signInAndSync(
            activity = this,
            onStatus = { message -> runOnUiThread { showStatus("⏳ $message") } },
            onFinished = { result ->
                result.onSuccess { summary ->
                    showStatus(
                        "✅ Teams Deep Sync اتربط بنجاح\n" +
                            "مكتشف جديد: ${summary.discovered} • متعدل: ${summary.updated} • بدون تغيير: ${summary.unchanged}\n" +
                            "يحتاج انتباه: ${summary.attention}\n" +
                            "ومن دلوقتي هيشتغل Catch-up في الخلفية تقريبًا كل 15 دقيقة."
                    )
                }.onFailure { error ->
                    showStatus("⚠️ تعذر ربط Teams: ${error.message ?: error.javaClass.simpleName}")
                }
            }
        )
    }

    private fun refreshStatus() {
        if (!::statusText.isInitialized) return
        val account = TeamsAuthStore.account(this)
        val lastSync = TeamsAuthStore.lastSync(this)
        val error = TeamsAuthStore.lastError(this)

        statusText.text = when {
            !error.isNullOrBlank() -> "⚠️ آخر محاولة Teams: ${error.take(220)}"
            account != null && lastSync > 0L -> "✅ Teams مربوط بالحساب $account\nآخر Deep Sync: ${formatTime(lastSync)}"
            account != null -> "✅ الحساب $account محفوظ في MSAL • في انتظار أول Deep Sync مكتمل"
            TeamsAuthStore.isConfigured(this) -> "🟡 Client ID محفوظ • لسه محتاج تسجيل دخول حساب حمزة"
            else -> "⚪ Teams Deep Sync غير مربوط بعد"
        }
    }

    private fun showStatus(message: String) {
        statusText.text = message
    }

    private fun infoCard(title: String, body: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = roundedBackground(Color.rgb(232, 240, 255), dp(14))
        addView(TextView(this@TeamsConnectionActivity).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(Color.rgb(44, 75, 132))
        })
        addView(TextView(this@TeamsConnectionActivity).apply {
            text = body
            textSize = 14f
            gravity = Gravity.END
            setPadding(0, dp(4), 0, 0)
            setTextColor(Color.rgb(50, 65, 90))
        })
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy  hh:mm a", Locale.getDefault()).format(Date(timestamp))

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
