package com.hamza.studyhub

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Hamza Study Hub"
            textSize = 28f
        }

        val subtitle = TextView(this).apply {
            text = "الخطوة الأولى: اسمح للتطبيق بقراءة إشعارات Teams وUntis فقط، حتى نعرض أي واجب أو تحديث جديد بوضوح."
            textSize = 18f
            setPadding(0, 32, 0, 32)
        }

        val button = Button(this).apply {
            text = "تفعيل الوصول للإشعارات"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(button)
        setContentView(layout)
    }
}
