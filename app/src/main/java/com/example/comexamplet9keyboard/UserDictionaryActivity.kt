package com.example.comexamplet9keyboard

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class UserDictionaryActivity : AppCompatActivity() {

    private val BG = "#141C22".toColorInt()
    private val CARD_BG = "#1C2830".toColorInt()
    private val ITEM_BG = "#263642".toColorInt()
    private val ACCENT = "#7EEEEF".toColorInt()
    private val ACCENT_DARK = "#0E7C86".toColorInt()
    private val TXT = "#F8FAFC".toColorInt()
    private val SUBTXT = "#94A3B8".toColorInt()
    private val RED = "#EF4444".toColorInt()
    private val GREEN = "#10B981".toColorInt()
    private val GREEN_BG = "#1E3A2F".toColorInt()

    private val trLocale = Locale.forLanguageTag("tr-TR")

    private lateinit var wordsContainer: LinearLayout
    private lateinit var countText: TextView
    private lateinit var statusText: TextView
    private lateinit var inputSmart: EditText
    private lateinit var btnAdd: TextView      // "Ekle" butonu (kelime sözlükte yoksa)
    private lateinit var btnEdit: TextView     // "Düzenle" butonu (kelime sözlükte varsa)
    private lateinit var btnDelete: TextView   // "Sil" butonu (kelime sözlükte varsa)

    private var allWords = mutableListOf<UserWord>()
    private var filterQuery = ""
    private var visibleLimit = 50   // her seferinde en fazla bu kadar kart çizilir (hızlı silme/yazma için)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = BG
            window.navigationBarColor = BG
        }

        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        val scroll = ScrollView(this).apply { isFillViewport = true }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            container.setPadding(dp(16), statusBarHeight + dp(12), dp(16), navBarHeight + dp(16))
            insets
        }

        // Header (Geri Butonu ve Başlık)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        val btnBack = TextView(this).apply {
            text = "← Geri"
            textSize = 16f
            setTextColor(ACCENT)
            setPadding(0, dp(8), dp(16), dp(8))
            setOnClickListener { finish() }
        }

        val title = TextView(this).apply {
            text = "Kullanıcı Sözlüğü"
            textSize = 22f
            setTextColor(TXT)
        }

        header.addView(btnBack)
        header.addView(title)
        container.addView(header)

        countText = TextView(this).apply {
            text = "Yükleniyor..."
            textSize = 14f
            setTextColor(SUBTXT)
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(countText)

        // ── Birleşik Arama Kartı ──
        val smartCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD_BG)
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val searchIcon = TextView(this).apply {
            text = "🔍"
            textSize = 16f
            setPadding(0, 0, dp(8), 0)
        }

        inputSmart = EditText(this).apply {
            hint = "Ara..."
            setHintTextColor(SUBTXT)
            setTextColor(TXT)
            textSize = 15f
            background = null
            isSingleLine = true
            setImeOptions(imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    filterQuery = s?.toString()?.trim()?.lowercase(trLocale) ?: ""
                    visibleLimit = 50
                    renderList()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        // "Ekle" butonu – kelime sözlükte yokken görünür
        btnAdd = TextView(this).apply {
            text = "+ Ekle"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(ACCENT_DARK)
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(14), dp(8), dp(14), dp(8))
            visibility = View.GONE
            setOnClickListener {
                val clean = filterQuery.trim()
                if (clean.length < 2) {
                    Toast.makeText(this@UserDictionaryActivity, "Kelime en az 2 harfli olmalıdır", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                UserDictionaryStore.add(this@UserDictionaryActivity, clean)
                Toast.makeText(this@UserDictionaryActivity, "\"$clean\" sözlüğe eklendi", Toast.LENGTH_SHORT).show()
                refreshList()
            }
        }

        // "Düzenle" butonu – kelime sözlükte varken görünür
        btnEdit = TextView(this).apply {
            text = "✏️"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(6), dp(6))
            visibility = View.GONE
            setOnClickListener {
                val match = allWords.firstOrNull { it.word.lowercase(trLocale) == filterQuery }
                if (match != null) showEditDialog(match)
            }
        }

        // "Sil" butonu – kelime sözlükte varken görünür
        btnDelete = TextView(this).apply {
            text = "🗑️"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(4), dp(6))
            visibility = View.GONE
            setOnClickListener {
                val match = allWords.firstOrNull { it.word.lowercase(trLocale) == filterQuery }
                if (match != null) showDeleteDialog(match)
            }
        }

        smartCard.addView(searchIcon)
        smartCard.addView(inputSmart, LinearLayout.LayoutParams(0, -2, 1f))
        smartCard.addView(btnAdd, LinearLayout.LayoutParams(-2, -2))
        smartCard.addView(btnEdit, LinearLayout.LayoutParams(-2, -2))
        smartCard.addView(btnDelete, LinearLayout.LayoutParams(-2, -2))
        container.addView(smartCard)

        // Durum Bilgilendirme Metni
        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(SUBTXT)
            visibility = View.GONE
            setPadding(dp(4), dp(8), dp(4), dp(12))
        }
        container.addView(statusText)

        // Kelime Listesi Konteyneri
        wordsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(wordsContainer)

        scroll.addView(container)
        root.addView(scroll)
        setContentView(root)

        refreshList()
    }

    // Klavyedeki ⊕ ile (veya başka yerden) sözlük değişince liste hemen yenilensin
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "uc") runOnUiThread { refreshList() }
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("t9", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        refreshList()
    }

    override fun onStop() {
        getSharedPreferences("t9", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onStop()
    }

    private fun refreshList() {
        allWords = UserDictionaryStore.load(this)
        renderList()
    }

    private fun renderList() {
        wordsContainer.removeAllViews()
        val query = filterQuery.trim()

        val filtered = if (query.isBlank()) {
            allWords
        } else {
            allWords.filter { it.word.lowercase(trLocale).contains(query) }
        }

        countText.text = "Toplam ${allWords.size} özel kelime kaydedildi"

        // ── Akıllı Buton & Durum Güncelleme ──
        if (query.length >= 2) {
            val exactExists = allWords.any { it.word.lowercase(trLocale) == query }

            if (exactExists) {
                // Kelime sözlükte VAR → "Düzenle" ve "Sil" butonları göster
                btnAdd.visibility = View.GONE
                btnEdit.visibility = View.VISIBLE
                btnDelete.visibility = View.VISIBLE

                statusText.visibility = View.VISIBLE
                statusText.text = "✓ \"$query\" sözlüğünüzde kayıtlı."
                statusText.setTextColor(GREEN)
            } else {
                // Kelime sözlükte YOK → "Ekle" butonu göster
                btnAdd.visibility = View.VISIBLE
                btnEdit.visibility = View.GONE
                btnDelete.visibility = View.GONE
                btnAdd.text = "+ Ekle"
                btnAdd.background = GradientDrawable().apply {
                    setColor(ACCENT_DARK)
                    cornerRadius = dp(8).toFloat()
                }

                statusText.visibility = View.VISIBLE
                statusText.text = "➕ \"$query\" sözlükte yok. Eklemek için + Ekle'ye basın."
                statusText.setTextColor(ACCENT)
            }
        } else {
            btnAdd.visibility = View.GONE
            btnEdit.visibility = View.GONE
            btnDelete.visibility = View.GONE
            statusText.visibility = View.GONE
        }

        if (filtered.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(CARD_BG)
                    cornerRadius = dp(12).toFloat()
                }
                setPadding(dp(16), dp(24), dp(16), dp(24))
            }

            val msgText = TextView(this).apply {
                text = if (allWords.isEmpty()) {
                    "Henüz sözlüğe eklenmiş özel bir kelime yok."
                } else {
                    "\"$query\" kelimesi sözlükte bulunamadı."
                }
                textSize = 14f
                setTextColor(SUBTXT)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(12))
            }

            emptyCard.addView(msgText)

            if (query.length >= 2 && !allWords.any { it.word.lowercase(trLocale) == query }) {
                val btnDirectAdd = TextView(this).apply {
                    text = "+ \"$query\" kelimesini sözlüğe ekle"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        setColor(ACCENT_DARK)
                        cornerRadius = dp(8).toFloat()
                    }
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    setOnClickListener {
                        UserDictionaryStore.add(this@UserDictionaryActivity, query)
                        Toast.makeText(this@UserDictionaryActivity, "\"$query\" sözlüğe eklendi", Toast.LENGTH_SHORT).show()
                        refreshList()
                    }
                }
                emptyCard.addView(btnDirectAdd, LinearLayout.LayoutParams(-2, -2))
            }

            wordsContainer.addView(emptyCard)
            return
        }

        filtered.take(visibleLimit).forEach { userWord ->
            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(ITEM_BG)
                    cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }

            val textInfo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val wordTitle = TextView(this).apply {
                text = userWord.word
                textSize = 16f
                setTextColor(TXT)
            }

            val wordBadge = TextView(this).apply {
                text = "Kullanım: ${userWord.count}x"
                textSize = 12f
                setTextColor(SUBTXT)
            }

            textInfo.addView(wordTitle)
            textInfo.addView(wordBadge)

            // Butonlar (Düzenle ve Sil)
            val itemBtnEdit = TextView(this).apply {
                text = "✏️ Düzenle"
                textSize = 13f
                setTextColor(ACCENT)
                setPadding(dp(8), dp(6), dp(12), dp(6))
                setOnClickListener { showEditDialog(userWord) }
            }

            val itemBtnDelete = TextView(this).apply {
                text = "🗑️ Sil"
                textSize = 13f
                setTextColor(RED)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener { showDeleteDialog(userWord) }
            }

            itemCard.addView(textInfo, LinearLayout.LayoutParams(0, -2, 1f))
            itemCard.addView(itemBtnEdit)
            itemCard.addView(itemBtnDelete)

            val lpItem = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lpItem.setMargins(0, 0, 0, dp(8))
            wordsContainer.addView(itemCard, lpItem)
        }

        if (filtered.size > visibleLimit) {
            val more = TextView(this).apply {
                text = "Daha fazla göster (${filtered.size - visibleLimit})"
                textSize = 14f
                setTextColor(ACCENT)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { visibleLimit += 50; renderList() }
            }
            wordsContainer.addView(more, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun showEditDialog(userWord: UserWord) {
        val input = EditText(this).apply {
            setText(userWord.word)
            setSelection(userWord.word.length)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        AlertDialog.Builder(this)
            .setTitle("Kelimeyi Düzenle")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                val newW = input.text.toString().trim()
                if (newW.length < 2) {
                    Toast.makeText(this, "Kelime en az 2 harfli olmalıdır", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                UserDictionaryStore.update(this, userWord.word, newW)
                Toast.makeText(this, "Kelime güncellendi", Toast.LENGTH_SHORT).show()
                refreshList()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showDeleteDialog(userWord: UserWord) {
        AlertDialog.Builder(this)
            .setTitle("Kelimeyi Sil")
            .setMessage("\"${userWord.word}\" kelimesini sözlükten silmek istediğinize emin misiniz?")
            .setPositiveButton("Sil") { _, _ ->
                UserDictionaryStore.remove(this, userWord.word)
                Toast.makeText(this, "\"${userWord.word}\" silindi", Toast.LENGTH_SHORT).show()
                refreshList()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
