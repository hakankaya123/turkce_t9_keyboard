package com.example.comexamplet9keyboard

import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt

class MainActivity : AppCompatActivity() {

    private lateinit var btnEnable: LinearLayout
    private lateinit var btnSelect: LinearLayout
    private lateinit var btnDict: LinearLayout
    private lateinit var statusEnable: TextView
    private lateinit var statusSelect: TextView
    private lateinit var statusDict: TextView

    private val BG = "#141C22".toColorInt()
    private val CARD_BG = "#1C2830".toColorInt()
    private val ACCENT = "#7EEEEF".toColorInt()
    private val ACCENT_DARK = "#0E7C86".toColorInt()
    private val TXT = "#F8FAFC".toColorInt()
    private val DONE_BG = "#2A3A44".toColorInt()
    private val SUBTXT = "#94A3B8".toColorInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        val scroll = ScrollView(this).apply { isFillViewport = true }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(36))
        }

        // Hero Header: uygulama ikonu
        val logoView = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(26).toFloat())
                }
            }
        }

        val title = TextView(this).apply {
            text = getString(R.string.splash_title)
            textSize = 26f
            setTextColor(TXT)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }

        val subtitle = TextView(this).apply {
            text = getString(R.string.splash_tagline)
            textSize = 14f
            setTextColor(SUBTXT)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(28))
        }

        container.addView(logoView, LinearLayout.LayoutParams(dp(110), dp(110)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        container.addView(title)
        container.addView(subtitle)

        // Setup Steps Card
        val stepsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD_BG)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }

        val stepsTitle = TextView(this).apply {
            text = "Klavye Kurulumu"
            textSize = 18f
            setTextColor(TXT)
            setPadding(0, 0, 0, dp(14))
        }
        stepsCard.addView(stepsTitle)

        // Step 1 Button
        btnEnable = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(ACCENT_DARK)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        }
        val labelEnable = TextView(this).apply {
            text = "1. Klavyeyi Etkinleştir"
            textSize = 15f
            setTextColor(Color.WHITE)
        }
        statusEnable = TextView(this).apply {
            text = "Kontrol..."
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
        }
        btnEnable.addView(labelEnable, LinearLayout.LayoutParams(0, -2, 1f))
        btnEnable.addView(statusEnable, LinearLayout.LayoutParams(-2, -2))

        // Step 2 Button
        btnSelect = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(ACCENT_DARK)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }
        val labelSelect = TextView(this).apply {
            text = "2. Varsayılan Klavye Seç"
            textSize = 15f
            setTextColor(Color.WHITE)
        }
        statusSelect = TextView(this).apply {
            text = "Kontrol..."
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
        }
        btnSelect.addView(labelSelect, LinearLayout.LayoutParams(0, -2, 1f))
        btnSelect.addView(statusSelect, LinearLayout.LayoutParams(-2, -2))

        // Step 3 Button - Sözlük Yönetimi
        btnDict = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(ACCENT_DARK)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, UserDictionaryActivity::class.java))
            }
        }
        val labelDict = TextView(this).apply {
            text = "📖 Kullanıcı Sözlüğü"
            textSize = 15f
            setTextColor(Color.WHITE)
        }
        statusDict = TextView(this).apply {
            text = "Yönet →"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.END
        }
        btnDict.addView(labelDict, LinearLayout.LayoutParams(0, -2, 1f))
        btnDict.addView(statusDict, LinearLayout.LayoutParams(-2, -2))

        stepsCard.addView(btnEnable, LinearLayout.LayoutParams(MATCH_PARENT, -2))
        stepsCard.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(10)))
        stepsCard.addView(btnSelect, LinearLayout.LayoutParams(MATCH_PARENT, -2))
        stepsCard.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(10)))
        stepsCard.addView(btnDict, LinearLayout.LayoutParams(MATCH_PARENT, -2))

        container.addView(stepsCard, LinearLayout.LayoutParams(MATCH_PARENT, -2))

        // Hızlı ve pratik vurgusu
        val fastCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD_BG)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(18), dp(20), dp(18), dp(16))
        }
        fastCard.addView(TextView(this).apply {
            text = "Neden T9 KlavyeTR?"
            textSize = 18f
            setTextColor(TXT)
            setPadding(0, 0, 0, dp(8))
        })
        listOf(
            R.string.splash_desc_1,
            R.string.splash_desc_2,
            R.string.splash_desc_3,
            R.string.splash_desc_4
        ).forEach { id ->
            fastCard.addView(TextView(this).apply {
                text = getString(id)
                textSize = 14f
                setTextColor(SUBTXT)
                setPadding(0, dp(5), 0, dp(5))
            })
        }
        container.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(16)))
        container.addView(fastCard, LinearLayout.LayoutParams(MATCH_PARENT, -2))

        // Features Highlights Card
        val featuresCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD_BG)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }

        val featureTitle = TextView(this).apply {
            text = "Öne Çıkan Özellikler"
            textSize = 18f
            setTextColor(TXT)
            setPadding(0, 0, 0, dp(12))
        }
        featuresCard.addView(featureTitle)

        val features = listOf(
            "⚡ Akıllı Türkçe Tahmin" to "50.000+ kelimelik konuşma dili sözlüğü ve gelişmiş Türkçe ek motoru.",
            "🎙️ Sesle Yazma" to "Boşluk tuşuna uzun basarak sesle Türkçe mesaj yazın.",
            "📖 Kullanıcı Sözlüğü" to "Özel kelimelerinizi ekleyin, düzenleyin ve dilediğiniz gibi yönetin.",
            "🎨 Koyu & Beyaz Temalar" to "Menüden tek tıkla gece ve gündüz temaları arasında geçiş yapın.",
            "📋 Pano & İmleç Yönetimi" to "Hızlı pano kartları ve hassas imleç hareket tuşları."
        )

        features.forEach { (head, desc) ->
            val h = TextView(this).apply { text = head; textSize = 15f; setTextColor(ACCENT); setPadding(0, dp(8), 0, dp(2)) }
            val d = TextView(this).apply { text = desc; textSize = 13f; setTextColor(SUBTXT) }
            featuresCard.addView(h)
            featuresCard.addView(d)
        }

        container.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, dp(16)))
        container.addView(featuresCard, LinearLayout.LayoutParams(MATCH_PARENT, -2))

        scroll.addView(container)
        root.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        checkKeyboardStatus()
    }

    private fun checkKeyboardStatus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        val isEnabled = enabledMethods.any { it.packageName == packageName }

        val currentDefault = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isDefault = currentDefault?.contains(packageName) == true

        statusEnable.text = if (isEnabled) "✓ Etkin" else "Etkinleştir"
        btnEnable.background = GradientDrawable().apply {
            setColor(if (isEnabled) DONE_BG else ACCENT_DARK)
            cornerRadius = dp(12).toFloat()
        }

        statusSelect.text = if (isDefault) "✓ Seçildi" else "Seç"
        btnSelect.background = GradientDrawable().apply {
            setColor(if (isDefault) DONE_BG else ACCENT_DARK)
            cornerRadius = dp(12).toFloat()
        }

        val dictCount = UserDictionaryStore.load(this).size
        statusDict.text = "$dictCount Kelime →"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH_PARENT = LinearLayout.LayoutParams.MATCH_PARENT
    }
}
