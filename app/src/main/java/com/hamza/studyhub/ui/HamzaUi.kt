package com.hamza.studyhub.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

object HamzaUi {
    val bg = Color.rgb(245, 247, 250)
    val surface = Color.WHITE
    val ink = Color.rgb(22, 28, 38)
    val muted = Color.rgb(103, 112, 124)
    val navy = Color.rgb(24, 54, 91)
    val blue = Color.rgb(37, 99, 169)
    val green = Color.rgb(47, 132, 91)
    val amber = Color.rgb(176, 91, 30)
    val purple = Color.rgb(92, 94, 191)
    val danger = Color.rgb(178, 54, 58)
    val border = Color.rgb(226, 231, 237)

    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    fun title(context: Context, text: String, size: Float = 27f): TextView = TextView(context).apply {
        this.text = text
        textSize = size
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.END
        setTextColor(ink)
    }

    fun subtitle(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 14.5f
        gravity = Gravity.END
        setTextColor(muted)
    }

    fun section(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 16.5f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.END
        setPadding(0, dp(context, 18), 0, dp(context, 7))
        setTextColor(ink)
    }

    fun card(context: Context, radius: Int = 18, padding: Int = 15): MaterialCardView = MaterialCardView(context).apply {
        setCardBackgroundColor(surface)
        cardElevation = dp(context, 1).toFloat()
        this.radius = dp(context, radius).toFloat()
        strokeWidth = dp(context, 1)
        strokeColor = border
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        })
    }

    fun cardContent(card: MaterialCardView): LinearLayout = card.getChildAt(0) as LinearLayout

    fun primaryButton(context: Context, text: String): MaterialButton = MaterialButton(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 14.5f
        cornerRadius = dp(context, 13)
        minHeight = dp(context, 48)
        backgroundTintList = ColorStateList.valueOf(navy)
        setTextColor(Color.WHITE)
        insetTop = 0
        insetBottom = 0
    }

    fun secondaryButton(context: Context, text: String, color: Int = blue): MaterialButton = MaterialButton(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        cornerRadius = dp(context, 13)
        minHeight = dp(context, 46)
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        strokeColor = ColorStateList.valueOf(color)
        strokeWidth = dp(context, 1)
        setTextColor(color)
        insetTop = 0
        insetBottom = 0
    }

    fun softPill(context: Context, text: String, background: Int, foreground: Int): TextView = TextView(context).apply {
        this.text = text
        textSize = 13.5f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(dp(context, 11), dp(context, 8), dp(context, 11), dp(context, 8))
        this.background = rounded(background, dp(context, 13).toFloat())
        setTextColor(foreground)
    }

    fun statusBox(context: Context, text: String, background: Int, foreground: Int): TextView = TextView(context).apply {
        this.text = text
        textSize = 14f
        gravity = Gravity.END
        setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
        this.background = rounded(background, dp(context, 13).toFloat())
        setTextColor(foreground)
    }

    fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    fun marginTop(context: Context, value: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(context, value) }
}
