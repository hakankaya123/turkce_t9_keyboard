@file:Suppress("unused")

package com.example.comexamplet9keyboard

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import androidx.core.view.isVisible

class T9Service : InputMethodService() {

    private val tr = T9Keymap.tr
    private val handler = Handler(Looper.getMainLooper())

    private var BG = "#212121".toColorInt()
    private var KEY = "#2E3236".toColorInt()
    private var ACCENT = "#5FD3C5".toColorInt()
    private var ACCENT_DARK = "#0E7C86".toColorInt()
    private var DIM = "#262829".toColorInt()
    private var TXT = Color.WHITE
    private var SUBTXT = "#8A8E92".toColorInt()
    private var ICON = Color.LTGRAY
    private var SELBG = "#3F6B66".toColorInt()
    private var SHIFTOFF = "#7FA8A3".toColorInt()
    private var lightTheme = false
    private lateinit var themePage: View
    private lateinit var numPage: View
    private var numActionLabel: TextView? = null

    private val groups = T9Keymap.groups
    private val charToDigit = T9Keymap.charToDigit

    private fun codeOf(w: String) = T9Keymap.codeOf(w)

    private val words: ArrayList<String> get() = dd.words
    private val codes: ArrayList<String> get() = dd.codes
    private val wordSet: HashSet<String> get() = dd.wordSet
    private val builtinBigrams: HashMap<String, ArrayList<String>> get() = dd.builtinBigrams
    private val userCount: HashMap<String, Int> get() = dd.userCount
    private val suffixCount: HashMap<String, Int> get() = dd.suffixCount
    private val wordIdx: HashMap<String, Int> get() = dd.wordIdx
    private val bigram: HashMap<String, Int> get() = dd.bigram
    private val charTotal: HashMap<Char, Int> get() = dd.charTotal
    private val userBigram: HashMap<String, HashMap<String, Int>> get() = dd.userBigram
    private val userWords: HashSet<String> get() = dd.userWords
    private val suffixIndex: Map<String, Int> by lazy {
        SUFFIXES.withIndex().associate { it.value to it.index }
    }
    private var learnedSinceSave = 0
    private var lastUc = ""
    private val prefs by lazy { getSharedPreferences("t9", MODE_PRIVATE) }
    private var dd = DictData(this, { prefs }, handler)
    private var voiceInputEnabled: Boolean
        get() = prefs.getBoolean("voice_input_enabled", true)
        set(v) = prefs.edit { putBoolean("voice_input_enabled", v) }

    private var vibrateEnabled: Boolean
        get() = prefs.getBoolean("vibrate_enabled", true)
        set(v) = prefs.edit { putBoolean("vibrate_enabled", v) }

    private var autoAddWords: Boolean
        get() = prefs.getBoolean("addword_auto", false)
        set(v) = prefs.edit { putBoolean("addword_auto", v) }

    private fun playClick() {
        if (vibrateEnabled && keyboardRootView != null) {
            try {
                keyboardRootView?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (_: Exception) {
            }
        }
    }

    private var predictive = true
    private val word = StringBuilder()
    private var pendKey: String? = null
    private var pendChars = ""
    private var pendIdx = 0
    private val seq = StringBuilder()
    private var cands: List<String> = emptyList()
    private var symComposing = false
    private var symIdx = 0
    private val forced = ArrayList<Char?>()
    private var colActive = false
    private var lastColSig = "?"
    private lateinit var leftCol: LinearLayout
    private var upper = false
    private var shift = false
    private var caps = false
    private var lastShiftTap = 0L
    private val letterLabels = ArrayList<Pair<TextView, String>>()
    private var prevWord: String? = null
    private var attach: String? = null
    private var chipWord: String? = null
    private var recentUnknownWord: String? = null
    private var stashedChip: String? = null
    private var stashedChipTime = 0L
    private var keyboardRootView: LinearLayout? = null
    private var navBottomPx = 0
    private var keyboardFrameView: FrameLayout? = null
    private var contentLayoutView: FrameLayout? = null
    private var letterMode = false
    private var letterPos = -1
    private var suggestions: List<String> = emptyList()

    private lateinit var iconBar: LinearLayout
    private lateinit var suggestScroll: HorizontalScrollView
    private lateinit var suggestBox: LinearLayout
    private lateinit var closeBtn: TextView
    private lateinit var suggestLeftSpacer: View
    private lateinit var suggestRow: LinearLayout
    private lateinit var pages: List<View>
    private lateinit var t9Page: View
    private lateinit var sym1: View
    private lateinit var sym2: View
    private lateinit var emojiPage: View
    private lateinit var cursorPage: View
    private lateinit var clipPage: View
    private lateinit var settingsPage: View
    private lateinit var clipLeft: LinearLayout
    private lateinit var clipRight: LinearLayout
    private lateinit var selKey: FrameLayout
    private lateinit var hideOrReturnTool: TextView
    private var selectMode = false
    private var autoSpaced = false
    private val spacePunct = setOf(".", ",", "?", "!", ":", ";")
    private val clipManager by lazy { ClipManager(this) { currentInputEditorInfo } }
    private var predView: TextView? = null
    private var shiftIcon: IconView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val confirmRunnable = Runnable { confirmPending(); updateSuggestions() }
    private val symRunnable = Runnable {
        currentInputConnection?.finishComposingText()
        symComposing = false
        updateSuggestions()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val isLandscape: Boolean
        get() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (keyboardRootView != null) {
            setInputView(onCreateInputView())
            updateSuggestions()
        }
    }

    // Ekran döndüğünde veya klavye yeniden oluşturulduğunda tuş takımı kaybolmasın
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    // Tam ekran (extract) modunu tamamen devre dışı bırak: her zaman kendi klavyemizi göster
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreate() {
        super.onCreate()
        lightTheme = prefs.getBoolean("theme_light", false)
        applyTheme()

        serviceScope.launch(Dispatchers.IO) {
            loadDictionary()
            loadUserData()
            withContext(Dispatchers.Main) {
                updateSuggestions()
            }
        }

        clipManager.start()
    }

    override fun onDestroy() {
        clipManager.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        syncUserDictionary()
        val keep = if (restarting && stashedChip != null &&
            SystemClock.uptimeMillis() - stashedChipTime < 2500
        ) stashedChip else null
        stashedChip = null
        resetState()
        if (keep != null) {
            recentUnknownWord = keep
            updateSuggestions()
        }
        updatePredLook()
        if (::pages.isInitialized) {
            updateNumActionLabel(info)
            if (!restarting) showPage(if (isNumericField(info)) numPage else t9Page, toggle = false)
        }
    }

    override fun onFinishInput() {
        stashedChip = recentUnknownWord
        stashedChipTime = SystemClock.uptimeMillis()
        commitWord(false)
        saveUserData()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )
        if (autoSpaced && currentInputConnection?.getTextBeforeCursor(1, 0)
                ?.toString() != " "
        ) autoSpaced = false
        if (composingActive()) checkCursorMoved()
        val a = attach
        if (a != null && !composingActive()) {
            val b = currentInputConnection?.getTextBeforeCursor(a.length, 0)?.toString()
            if (b != a) {
                attach = null; updateSuggestions()
            }
        }
    }

    private fun resetState() {
        handler.removeCallbacks(confirmRunnable)
        handler.removeCallbacks(symRunnable)
        word.setLength(0); seq.setLength(0); forced.clear(); colActive = false
        pendKey = null; symComposing = false
        cands = emptyList(); upper = false; prevWord = null
        attach = null; chipWord = null; recentUnknownWord = null
        shift = false; caps = false; updateShiftLook()
        updateSuggestions()
    }

    private fun addWord(w: String) = dd.addWord(w)

    private fun loadDictionary() {
        dd.loadBase()
    }

    private fun loadUserData() {
        val migrate = !prefs.getBoolean("uc_v2", false)
        dd.loadUser(migrate)
        lastUc = dd.uc
        if (migrate) {
            prefs.edit { putBoolean("uc_v2", true) }
            saveUserData()
        }
    }

    private fun saveUserData() {
        if ((prefs.getString("uc", "") ?: "") != lastUc) {
            rebuildDictionary(); return
        }
        if (rebuilding) return
        learnedSinceSave = 0
        serviceScope.launch(Dispatchers.IO) {
            val a = StringBuilder()
            val lc = StringBuilder()
            userCount.forEach { (w, c) ->
                if (userWords.contains(w)) a.append(w).append('\t').append(c).append('\n')
                else lc.append(w).append('\t').append(c).append('\n')
            }
            val b = StringBuilder()
            userBigram.forEach { (p, m) ->
                m.forEach { (w, c) ->
                    b.append(p).append('\t').append(w).append('\t').append(c).append('\n')
                }
            }
            lastUc = a.toString()
            prefs.edit {
                putString("uc", lastUc).putString("lc", lc.toString()).putString("ub", b.toString())
            }
        }
    }

    private var rebuilding = false
    private fun rebuildDictionary() {
        if (rebuilding) return
        rebuilding = true
        serviceScope.launch(Dispatchers.IO) {
            val nd = DictData(this@T9Service, { prefs }, handler)
            try {
                nd.loadBase(); nd.loadUser(false)
            } catch (e: Exception) {
                Log.w(TAG, "Sözlük yeniden oluşturulamadı", e)
            }
            withContext(Dispatchers.Main) {
                rebuilding = false
                val cur = prefs.getString("uc", "") ?: ""
                if (cur != nd.uc) {
                    rebuildDictionary()
                } else {
                    dd = nd
                    lastUc = nd.uc
                    updateSuggestions()
                }
            }
        }
    }

    private fun syncUserDictionary() {
        val cur = prefs.getString("uc", "") ?: ""
        if (cur != lastUc) rebuildDictionary()
    }

    private fun gluedToLetter(composingLen: Int): Boolean {
        if (composingLen <= 0) return false
        val ic = currentInputConnection ?: return false
        val t = ic.getTextBeforeCursor(composingLen + 2, 0)?.toString() ?: return false
        val idx = t.length - composingLen - 1
        if (idx < 0) return false
        val c = t[idx]
        return c.isLetter() || ((c == '\'' || c == '’') && idx >= 1 && t[idx - 1].isLetter())
    }

    private fun composingGlued(): Boolean = gluedToLetter(composingString()?.length ?: 0)

    private fun learn(w0: String) {
        val w = w0.lowercase(tr)
        if (w.length < 2 || !w.all { charToDigit.containsKey(it) }) {
            prevWord = null; return
        }
        if (!wordSet.contains(w)) {
            if (autoAddWords) {
                // Otomatik mod: hangi şekilde yazılmış olursa olsun sessizce ekle
                addToDictionary(w, silent = true)
            } else {
                // Manuel mod: chip (⊕) göster, kullanıcı tıklayınca eklesin
                markUnknown(w)
            }
        } else {
            userCount[w] = (userCount[w] ?: 0) + 1
        }
        prevWord?.let { p ->
            val m = userBigram.getOrPut(p) { HashMap() }
            m[w] = (m[w] ?: 0) + 1
        }
        prevWord = w
        learnedSinceSave++
        if (learnedSinceSave >= 5) saveUserData()
    }

    private fun matchesForced(w: String): Boolean {
        for (k in forced.indices) {
            val f = forced[k]
            if (f != null && (k >= w.length || w[k] != f)) return false
        }
        return true
    }

    private fun candidates(s: String): List<String> {
        val matchedIndices = dd.t9Trie.search(s)
        val userMatches = userCount.keys.asSequence()
            .mapNotNull { w ->
                wordIdx[w]?.takeIf { idx ->
                    codes[idx].startsWith(s) && matchesForced(
                        w
                    )
                }
            }

        return (matchedIndices.asSequence() + userMatches)
            .filter { matchesForced(words[it]) }
            .distinct()
            .sortedWith(
                compareBy<Int> { if (codes[it].length == s.length) 0 else 1 }
                    .thenByDescending { userCount[words[it]] ?: 0 }
                    .thenBy { codes[it].length }
                    .thenBy { it }
            )
            .take(8)
            .map { words[it] }
            .toList()
    }

    private fun letterScore(prev: Char, c: Char): Double {
        val b = (bigram["$prev$c"] ?: 0) + 0.5
        val t = (charTotal[prev] ?: 0) + 15.0
        return ln(b / t)
    }

    private fun startPrev(): Char =
        attach?.lastOrNull()?.let { it.toString().lowercase(tr)[0] } ?: '^'

    private fun chainScore(start: Char, frag: String): Double {
        var prev = start
        var sc = 0.0
        for (c in frag) {
            sc += letterScore(prev, c); prev = c
        }
        return sc
    }

    private fun guessSpelling(): String {
        var beam: List<Pair<String, Double>> = listOf("" to 0.0)
        for (k in seq.indices) {
            val opts = forced.getOrNull(k)?.toString() ?: groups[seq[k]] ?: "?"
            val next = ArrayList<Pair<String, Double>>()
            for ((word, score) in beam) for (c in opts) {
                val prev = if (word.isEmpty()) startPrev() else word.last()
                next.add((word + c) to (score + letterScore(prev, c)))
            }
            beam = next.sortedByDescending { it.second }.take(10)
        }
        return beam.firstOrNull()?.first ?: ""
    }

    private fun letterCandidates(): List<String> {
        val u = forced.indexOfLast { it == null }
        letterPos = u
        if (u < 0 || u >= seq.length) return emptyList()
        val guess = guessSpelling()
        val prev = if (u > 0 && u - 1 < guess.length) guess[u - 1] else startPrev()
        val opts = groups[seq[u]] ?: return emptyList()
        return opts.map { it.toString() }.sortedByDescending { letterScore(prev, it[0]) }
    }

    private fun fragScore(f: String): Int =
        suffixIndex[f]?.let { 1_000_000 - it } ?: (suffixCount[f] ?: 0)

    private fun fragmentCandidates(): List<String> {
        val n = seq.length
        if (n == 0 || n > 4) return emptyList()
        var combos = listOf("")
        for (k in 0 until n) {
            val opts = forced.getOrNull(k)?.toString() ?: groups[seq[k]] ?: return emptyList()
            combos = combos.flatMap { c -> opts.map { c + it } }
        }
        val start = startPrev()
        return combos.sortedWith(
            compareByDescending<String> { fragScore(it) }.thenByDescending { chainScore(start, it) }
        ).take(8)
    }

    private fun suffixCompletions(prefix: String): List<String> {
        val p = prefix.lowercase(tr)
        return SUFFIXES.asSequence()
            .filter { it.startsWith(p) && it != p }
            .take(6)
            .toList()
    }

    private fun prefixCandidates(prefix: String): List<String> {
        val p = prefix.lowercase(tr)
        val builtInHits = words.asSequence()
            .mapIndexedNotNull { idx, word -> if (word.startsWith(p)) idx else null }
            .take(30)
        val userHits = userCount.keys.asSequence()
            .mapNotNull { w -> wordIdx[w]?.takeIf { w.startsWith(p) } }

        return (builtInHits + userHits)
            .distinct()
            .sortedByDescending { userCount[words[it]] ?: 0 }
            .take(6)
            .map { words[it] }
            .toList()
    }

    private fun nextWords(prev: String?): List<String> {
        if (prev == null) return emptyList()
        val userNext = userBigram[prev]?.entries?.asSequence()
            ?.sortedByDescending { it.value }
            ?.map { it.key } ?: emptySequence()
        val builtInNext = builtinBigrams[prev]?.asSequence() ?: emptySequence()

        return (userNext + builtInNext)
            .distinct()
            .take(6)
            .toList()
    }


    private fun shown(s: String) = when {
        caps -> s.uppercase(tr)
        upper && s.isNotEmpty() -> s.substring(0, 1).uppercase(tr) + s.substring(1)
        else -> s
    }

    private fun composingActive() =
        seq.isNotEmpty() || word.isNotEmpty() || pendKey != null || symComposing

    private fun composingString(): String? = when {
        seq.isNotEmpty() -> shown(predText())
        word.isNotEmpty() || pendKey != null -> shown(pendingText())
        symComposing -> "@.-_/"[symIdx].toString()
        else -> null
    }

    private fun abandonComposing() {
        handler.removeCallbacks(confirmRunnable)
        handler.removeCallbacks(symRunnable)
        currentInputConnection?.finishComposingText()
        word.setLength(0); seq.setLength(0); forced.clear(); colActive = false
        pendKey = null; symComposing = false
        cands = emptyList(); upper = false
        attach = null; prevWord = null; autoSpaced = false
        updateSuggestions()
    }

    private fun checkCursorMoved() {
        if (!composingActive()) return
        val ic = currentInputConnection ?: return
        val c = composingString() ?: return
        val before = ic.getTextBeforeCursor(c.length, 0)?.toString() ?: return
        if (!before.equals(c, ignoreCase = true)) abandonComposing()
    }

    private fun ensureSpaceAfterPunct() {
        if (composingActive() || attach != null) return
        val ic = currentInputConnection ?: return
        val b = ic.getTextBeforeCursor(2, 0)?.toString() ?: return
        if (b.isEmpty()) return
        val last = b.last().toString()
        if (last in spacePunct && shouldAutoSpace(ic, last)) ic.commitText(" ", 1)
    }

    private fun pendingText() =
        word.toString() + (if (pendKey != null) pendChars[pendIdx].toString() else "")

    private fun predText(): String {
        val exact = cands.firstOrNull { it.length == seq.length }
        return exact?.take(seq.length) ?: guessSpelling()
    }

    private fun confirmPending() {
        if (pendKey != null) {
            word.append(pendChars[pendIdx]); pendKey = null
        }
    }

    private fun tapMulti(id: String, chars: String) {
        val ic = currentInputConnection ?: return
        autoSpaced = false
        checkCursorMoved()
        ensureSpaceAfterPunct()
        if (seq.isNotEmpty() || symComposing) commitWord(false)
        if (pendKey == id) {
            pendIdx = (pendIdx + 1) % chars.length
        } else {
            confirmPending()
            if (word.isEmpty()) {
                upper = shift; shift = false; updateShiftLook()
            }
            pendKey = id; pendChars = chars; pendIdx = 0
        }
        colActive = true
        ic.setComposingText(shown(pendingText()), 1)
        handler.removeCallbacks(confirmRunnable)
        handler.postDelayed(confirmRunnable, 900)
        updateSuggestions()
    }

    private fun tapPredictive(digit: Char) {
        val ic = currentInputConnection ?: return
        autoSpaced = false
        checkCursorMoved()
        ensureSpaceAfterPunct()
        if (symComposing || word.isNotEmpty() || pendKey != null) commitWord(false)
        if (seq.isEmpty()) {
            upper = shift; shift = false; updateShiftLook()
        }
        seq.append(digit); forced.add(null); colActive = true
        refreshPredictive(ic)
    }

    private fun refreshPredictive(ic: InputConnection) {
        cands = if (attach != null) fragmentCandidates() else candidates(seq.toString())
        ic.setComposingText(shown(predText()), 1)
        updateSuggestions()
    }

    private fun tapSymbolKey() {
        val ic = currentInputConnection ?: return
        val chars = "@.-_/"
        if (symComposing) symIdx = (symIdx + 1) % chars.length
        else {
            commitWord(false); attach = null; symIdx = 0; symComposing = true
        }
        ic.setComposingText(chars[symIdx].toString(), 1)
        handler.removeCallbacks(symRunnable)
        handler.postDelayed(symRunnable, 900)
        updateSuggestions()
    }

    private fun commitWord(
        space: Boolean,
        chosen: String? = null,
        learnIt: Boolean = true,
        finalizeWord: Boolean = false
    ) {
        val ic = currentInputConnection ?: return
        checkCursorMoved()
        handler.removeCallbacks(confirmRunnable)
        handler.removeCallbacks(symRunnable)
        val before = attach
        autoSpaced = false
        val text: String? = when {
            seq.isNotEmpty() -> {
                if (chosen != null) chosen
                else {
                    val exact = cands.firstOrNull { it.length == seq.length }
                    exact ?: guessSpelling()
                }
            }

            word.isNotEmpty() || pendKey != null -> chosen ?: pendingText()
            else -> null
        }
        var newAttach: String? = null
        var committedFull: String? = null
        if (text != null) {
            val out = shown(text)
            ic.commitText(out + (if (space) " " else ""), 1)
            committedFull = (before ?: "") + out
            if (learnIt) learn((before ?: "") + text)
            if (!space && chosen != null) newAttach = (before ?: "") + out
        } else {
            ic.finishComposingText()
            if (space) ic.commitText(" ", 1) else newAttach = before
        }
        val fullWord = committedFull
        recentUnknownWord = null
        if (autoAddWords) {
            // Otomatik mod: commit sonrası da kontrol et (learn() çağrılmadığı durumlar için)
            if (fullWord != null) autoAddIfNew(fullWord)
            if (space) detectUnknownWord()?.let { autoAddIfNew(it) }
        } else {
            // Manuel mod: commit sonrası boşlukla biten bilinmeyen kelimeyi chip olarak işaretle
            if (space) detectUnknownWord()?.let { recentUnknownWord = it }
        }
        word.setLength(0); seq.setLength(0); forced.clear(); colActive = false
        pendKey = null; symComposing = false
        cands = emptyList(); upper = false
        attach = newAttach
        updateSuggestions()
    }

    private fun chipAllowedField(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: 0
        if (t != 0 && (t and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        return when (t and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false

            else -> true
        }
    }

    private fun autoAddIfNew(typed: String) {
        if (!chipAllowedField()) return
        val w = typed.lowercase(tr)
        if (w.length < 2 || !w.all { charToDigit.containsKey(it) } || wordSet.contains(w)) return
        addToDictionary(w, silent = true)
    }

    private fun markUnknown(typed: String) {
        recentUnknownWord = null
        if (!chipAllowedField()) return
        val w = typed.lowercase(tr)
        if (w.length < 2 || !w.all { charToDigit.containsKey(it) } || wordSet.contains(w)) return
        recentUnknownWord = typed
    }

    private fun detectUnknownWord(): String? {
        recentUnknownWord = null
        val ic = currentInputConnection ?: return null
        if (!chipAllowedField()) return null
        val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: return null
        if (!before.endsWith(" ")) return null
        val trimmed = before.trimEnd(' ')
        val token = trimmed.takeLastWhile { it.isLetter() }
        if (token.length < 2) return null
        val prevCh = trimmed.getOrNull(trimmed.length - token.length - 1)
        if (prevCh != null && (prevCh.isDigit() || prevCh in "@/.:#_-'’")) return null
        val w = token.lowercase(tr)
        if (!w.all { charToDigit.containsKey(it) }) return null
        return token
    }

    private fun typeText(s: String) {
        val wasAuto = autoSpaced
        commitWord(false, finalizeWord = true)
        attach = null
        val ic = currentInputConnection
        if (ic != null) {
            if (wasAuto && s in spacePunct) {
                val b = ic.getTextBeforeCursor(2, 0)?.toString()
                if (b != null && b.length == 2 && b[1] == ' ' && b[0].toString() in spacePunct) {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            ic.commitText(s, 1)
            autoSpaced = false
            if (s in spacePunct && shouldAutoSpace(ic, s)) {
                ic.commitText(" ", 1)
                autoSpaced = true
            }
        }
        prevWord = null
        updateSuggestions()
    }

    private fun startVoiceInput() {
        if (!voiceInputEnabled) {
            android.widget.Toast.makeText(
                this,
                getString(R.string.voice_input_disabled),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        commitWord(false)
        attach = null
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        try {
            val imes =
                imm.enabledInputMethodList.sortedBy { if (it.packageName.contains("google")) 0 else 1 }
            for (imi in imes) {
                if (imi.packageName == packageName) continue
                val voice = imm.getEnabledInputMethodSubtypeList(imi, true)
                    .firstOrNull { it.mode == "voice" }
                    ?: (0 until imi.subtypeCount).map { imi.getSubtypeAt(it) }
                        .firstOrNull { it.mode == "voice" }
                if (voice != null) {
                    if (Build.VERSION.SDK_INT >= 28) {
                        switchInputMethod(imi.id, voice)
                    } else {
                        @Suppress("DEPRECATION")
                        imm.setInputMethodAndSubtype(
                            window.window!!.attributes.token,
                            imi.id,
                            voice
                        )
                    }
                    return
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sesle yazma yöntemi aranırken hata", e)
        }
        android.widget.Toast.makeText(
            this,
            getString(R.string.voice_input_not_found),
            android.widget.Toast.LENGTH_LONG
        ).show()
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Giriş yöntemi ayarları açılamadı", e)
        }
    }

    private fun shouldAutoSpace(ic: InputConnection, s: String): Boolean {
        val t = currentInputEditorInfo?.inputType ?: 0
        if ((t and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        when (t and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> return false
        }
        if ((t and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false
        val recent = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        if (recent.contains("@") || recent.contains("www.") || recent.contains(".com") || recent.contains(
                ".net"
            ) || recent.contains(".org")
        ) return false

        if (s == "." || s == "," || s == ":") {
            val b = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (b.length >= 2 && b[b.length - 2].isDigit()) return false
        }
        return true
    }

    private fun backspace() {
        recentUnknownWord = null
        val ic = currentInputConnection ?: return
        autoSpaced = false
        checkCursorMoved()
        when {
            seq.isNotEmpty() -> {
                seq.setLength(seq.length - 1)
                if (forced.isNotEmpty()) forced.removeAt(forced.lastIndex)
                colActive = false
                if (seq.isEmpty()) {
                    ic.setComposingText("", 1); ic.finishComposingText()
                    cands = emptyList(); upper = false; updateSuggestions()
                } else refreshPredictive(ic)
            }

            pendKey != null || word.isNotEmpty() -> {
                handler.removeCallbacks(confirmRunnable)
                colActive = false
                if (pendKey != null) pendKey = null else word.setLength(word.length - 1)
                if (word.isEmpty()) {
                    ic.setComposingText("", 1); ic.finishComposingText(); upper = false
                } else ic.setComposingText(shown(word.toString()), 1)
                updateSuggestions()
            }

            symComposing -> {
                handler.removeCallbacks(symRunnable)
                ic.setComposingText("", 1); ic.finishComposingText()
                symComposing = false; updateSuggestions()
            }

            else -> {
                val sel = ic.getSelectedText(0)
                if (!sel.isNullOrEmpty()) {
                    ic.commitText("", 1); attach = null
                } else {
                    ic.deleteSurroundingTextInCodePoints(1, 0)
                    attach = attach?.dropLast(1)?.takeIf { it.isNotEmpty() }
                }
                updateSuggestions()
            }
        }
    }

    private fun enter() {
        commitWord(false, finalizeWord = true)
        attach = null
        prevWord = null
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        updateSuggestions()
    }

    private fun editorAction() {
        commitWord(false, finalizeWord = true)
        attach = null
        prevWord = null
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        }
        updateSuggestions()
    }

    private fun enterKey(): View {
        val k = key("", accent = true, onLong = { editorAction() }) { enter() }
        k.addView(
            IconView(this, IconView.ENTER, ACCENT, dp(3).toFloat()),
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        )
        return k
    }

    private fun pickSuggestion(s: String) {
        if (composingActive()) {
            commitWord(false, s)
        } else {
            currentInputConnection?.commitText(s, 1)
            learn(s)
        }
        seq.setLength(0)
        word.setLength(0)
        cands = emptyList()
        attach = null
        prevWord = s
        updateSuggestions()
    }

    private fun computeChip(): String? {
        if (autoAddWords) return null
        if (recentUnknownWord != null && !composingActive() && attach == null) {
            return recentUnknownWord
        }
        val shownFrag: String
        when {
            seq.isNotEmpty() -> {
                if (attach == null) {
                    if (cands.any { it.length == seq.length }) return null
                } else if (!forced.all { it != null }) return null
                shownFrag = shown(predText())
            }

            word.isNotEmpty() || pendKey != null -> {
                if (attach != null && pendKey != null) return null
                shownFrag = shown(pendingText())
            }

            attach != null -> shownFrag = ""
            else -> return null
        }
        val whole = (attach ?: "") + shownFrag
        val w = whole.lowercase(tr)
        if (w.length < 2 || wordSet.contains(w) || !w.all { charToDigit.containsKey(it) }) return null
        if (attach == null && composingActive() && composingGlued()) return null
        return whole
    }

    private fun addToDictionary(w0: String, silent: Boolean = false) {
        syncUserDictionary()
        val w = w0.lowercase(tr)
        if (w.length < 2 || !w.all { charToDigit.containsKey(it) }) return
        addWord(w)
        userWords.add(w)
        userCount[w] = (userCount[w] ?: 0) + 1
        if ((prefs.getString("uc", "") ?: "") != lastUc || rebuilding) {
            UserDictionaryStore.add(this, w)
            rebuildDictionary()
        } else saveUserData()
        try {
            android.provider.UserDictionary.Words.addWord(this, w, 250, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Sistem sözlüğüne kelime eklenemedi", e)
        }
        recentUnknownWord = null
        if (silent) return
        android.widget.Toast.makeText(
            this,
            "\"$w\" sözlüğe eklendi",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        val ic = currentInputConnection
        if (seq.isNotEmpty() && ic != null && attach == null) refreshPredictive(ic) else updateSuggestions()
    }

    private fun dismissBar() {
        recentUnknownWord = null
        commitWord(false, learnIt = false)
        attach = null
        prevWord = null
        seq.setLength(0)
        word.setLength(0)
        cands = emptyList()
        letterMode = false
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (composingActive() || attach != null) recentUnknownWord = null
        if (recentUnknownWord != null) {
            suggestions = emptyList()
        } else {
            letterMode = seq.isNotEmpty() && attach == null && cands.isEmpty()
            suggestions = when {
                letterMode -> letterCandidates()
                seq.length == 1 -> {
                    val letters = letterCandidates()
                    (letters + cands).distinct()
                }

                seq.isNotEmpty() -> cands
                word.isNotEmpty() || pendKey != null ->
                    if (attach != null) suffixCompletions(pendingText()) else prefixCandidates(
                        pendingText()
                    )

                symComposing -> emptyList()
                attach != null -> emptyList()
                else -> nextWords(prevWord)
            }
        }
        chipWord = computeChip()
        autoCap()
        updateLeftColumn()
        renderSuggestions()
    }

    private fun renderSuggestions() {
        if (!::suggestRow.isInitialized) return
        suggestRow.removeAllViews()
        val chip = chipWord
        if (suggestions.isEmpty() && chip == null) {
            iconBar.visibility = View.VISIBLE
            suggestBox.visibility = View.GONE
            return
        }
        iconBar.visibility = View.GONE
        suggestBox.visibility = View.VISIBLE
        val isChipOnly = chip != null && suggestions.isEmpty()
        closeBtn.visibility =
            if (chip != null || suggestions.isNotEmpty()) View.VISIBLE else View.GONE
        suggestLeftSpacer.visibility = if (isChipOnly) View.VISIBLE else View.GONE
        suggestRow.gravity =
            if (isChipOnly) Gravity.CENTER else (Gravity.START or Gravity.CENTER_VERTICAL)

        fun addChip() {
            if (chip == null) return
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener { addToDictionary(chip) }
            }
            c.addView(TextView(this).apply { text = "⊕"; textSize = 28f; setTextColor(ACCENT) })
            c.addView(TextView(this).apply {
                text = chip; textSize = 19f; setTextColor(TXT); setPadding(dp(8), 0, 0, 0)
            })
            suggestRow.addView(c, LinearLayout.LayoutParams(-2, MATCH_PARENT))
        }

        val chipFirst = suggestions.isEmpty() || letterMode
        if (chipFirst) addChip()
        suggestions.forEachIndexed { i, s ->
            val tv = TextView(this).apply {
                text = shown(s); textSize = if (letterMode) 24f else 19f; gravity = Gravity.CENTER
                setTextColor(if (i == 0 && composingActive() && !(chipFirst && chip != null && !letterMode)) ACCENT else TXT)
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener { if (letterMode) pickCandidateLetter(s[0]) else pickSuggestion(s) }
            }
            suggestRow.addView(tv, LinearLayout.LayoutParams(-2, MATCH_PARENT))
        }
        if (!chipFirst) addChip()
        suggestScroll.scrollTo(0, 0)
    }

    private fun updateShiftLook() {
        shiftIcon?.setLook(
            when {
                caps -> IconView.SHIFT_CAPS; shift -> IconView.SHIFT_ON; else -> IconView.SHIFT_OFF
            },
            if (caps || shift) ACCENT else SHIFTOFF
        )
        updateKeyLabels()
    }

    private fun updateKeyLabels() {
        val up = shift || caps
        letterLabels.forEach { (tv, base) ->
            tv.text = if (up) base.uppercase(tr) else base.lowercase(tr)
        }
    }

    private fun onShiftTap() {
        val now = System.currentTimeMillis()
        when {
            caps -> {
                caps = false; shift = false
            }

            shift && now - lastShiftTap < 450 -> {
                caps = true; shift = false
            }

            else -> shift = !shift
        }
        lastShiftTap = now
        updateShiftLook()
    }

    private fun autoCap() {
        if (caps || composingActive()) return
        val ic = currentInputConnection ?: return
        val t = currentInputEditorInfo?.inputType ?: return
        if ((t and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return
        val want = ic.getCursorCapsMode(t) != 0
        if (want != shift) {
            shift = want; updateShiftLook()
        }
    }

    // Şifre / e-posta / URI alanlarında ve "öneri yok" bayraklı alanlarda tahmin (T9) kullanılmaz;
    // tuşlar otomatik olarak çoklu dokunuşla (multi-tap) çalışır. Kullanıcının tahmin seçimi (predictive) korunur.
    private fun predictionBlockedField(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: return false
        if ((t and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        if ((t and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return true
        return when (t and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> true

            else -> false
        }
    }

    private fun predictiveEffective(): Boolean = predictive && !predictionBlockedField()

    private fun updatePredLook() {
        predView?.setTextColor(if (predictiveEffective()) ACCENT else SUBTXT)
    }

    private fun pickCandidateLetter(ch: Char) {
        val ic = currentInputConnection ?: return
        checkCursorMoved()
        if (!composingActive()) return
        if (letterPos in seq.indices && forced.size >= seq.length) {
            forced[letterPos] = ch
            refreshPredictive(ic)
        }
    }

    private fun updateLeftColumn() {
        if (!::leftCol.isInitialized) return
        leftCol.removeAllViews()
        listOf("!", "?", "'", "~").forEach { sym -> add(leftCol, key(sym) { typeText(sym) }, 1f) }
    }

    private fun showPage(p: View, toggle: Boolean = true) {
        commitWord(false)
        attach = null
        if (p !== cursorPage && selectMode) {
            selectMode = false; updateSelLook()
        }
        val target = if (toggle && p !== t9Page && p.isVisible) t9Page else p
        if (target === emojiPage) {
            loadRecentEmojis()
            populateRecentRow()
        }
        pages.forEach { it.visibility = if (it === target) View.VISIBLE else View.GONE }
        if (::hideOrReturnTool.isInitialized) {
            val isReturn = (target !== t9Page && target !== numPage)
            hideOrReturnTool.text = if (isReturn) "↩" else "⌄"
            hideOrReturnTool.setTextColor(if (isReturn) ACCENT else ICON)
        }
        if (target === clipPage) refreshClips()
    }

    private fun menuAction(id: Int) {
        commitWord(false)
        attach = null
        currentInputConnection?.performContextMenuAction(id)
    }

    private fun add(parent: LinearLayout, v: View, w: Float) {
        val lp = if (parent.orientation == LinearLayout.HORIZONTAL)
            LinearLayout.LayoutParams(0, MATCH_PARENT, w)
        else LinearLayout.LayoutParams(MATCH_PARENT, 0, w)
        val m = if (isLandscape) dp(1) else dp(2)
        lp.setMargins(m, m, m, m)
        parent.addView(v, lp)
    }

    private fun key(
        label: String,
        sub: String? = null,
        topSub: String? = null,
        accent: Boolean = false,
        size: Float = 22f,
        repeat: Boolean = false,
        accel: Boolean = false,
        bg: Int = KEY,
        onLong: (() -> Unit)? = null,
        onClick: () -> Unit
    ): FrameLayout {
        val f = FrameLayout(this)
        f.background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(6).toFloat() }
        val scaledSize = if (isLandscape) (size * 0.82f) else size
        val main = TextView(this).apply {
            text = label; textSize = scaledSize; gravity = Gravity.CENTER
            setTextColor(if (accent) ACCENT else TXT)
        }
        f.tag = main
        f.addView(main, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        if (sub != null) {
            val subSize = if (isLandscape) 9f else 11f
            val s = TextView(this).apply { text = sub; textSize = subSize; setTextColor(SUBTXT) }
            f.addView(s, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(4), dp(2))
            })
        }
        if (topSub != null) {
            val topSubSize = if (isLandscape) 10f else 12f
            val ts =
                TextView(this).apply { text = topSub; textSize = topSubSize; setTextColor(SUBTXT) }
            f.addView(ts, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(2), dp(4), 0)
            })
        }
        bindTouch(f, repeat, onLong, accel, onClick)
        return f
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindTouch(
        f: View,
        repeat: Boolean,
        onLong: (() -> Unit)?,
        accel: Boolean,
        onClick: () -> Unit
    ) {
        if (repeat) {
            var delay = 35L
            val r = object : Runnable {
                override fun run() {
                    playClick(); onClick()
                    if (accel) delay = maxOf(25L, (delay * 0.6).toLong())
                    handler.postDelayed(this, delay)
                }
            }
            f.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.alpha = 0.6f
                        delay = if (accel) 90L else 35L
                        playClick(); onClick(); handler.postDelayed(r, 400)
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.alpha = 1f; handler.removeCallbacks(r)
                    }
                }
                true
            }
        } else {
            f.setOnClickListener { playClick(); onClick() }
            if (onLong != null) f.setOnLongClickListener { playClick(); onLong(); true }
        }
    }

    private fun iconKey(kind: Int, repeat: Boolean = false, onClick: () -> Unit): FrameLayout {
        val f = FrameLayout(this)
        f.background = GradientDrawable().apply { setColor(DIM); cornerRadius = dp(6).toFloat() }
        f.addView(
            IconView(this, kind, ACCENT, dp(2).toFloat() * 1.2f),
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        )
        bindTouch(f, repeat, null, false, onClick)
        return f
    }

    private fun t9Key(label: String, chars: String, digit: String): View {
        val k = key(
            if (digit == "1") label else label.lowercase(tr),
            digit,
            onLong = { typeText(digit) }) {
            when {
                digit == "1" -> tapSymbolKey()
                predictiveEffective() -> tapPredictive(digit[0])
                else -> tapMulti(label, chars)
            }
        }
        if (digit != "1") letterLabels.add((k.tag as TextView) to label)
        return k
    }

    private fun spaceKey(label: String, w: Float = 1f): View {
        val k = key(label, size = 14f, onLong = { startVoiceInput() }) {
            if (autoSpaced && !composingActive()) autoSpaced = false
            else commitWord(true)
        }
        val gray = SUBTXT
        (k.tag as? TextView)?.apply {
            setTextColor(gray)
            if (label.isNotEmpty()) {
                val sp = android.text.SpannableString(label)
                label.forEachIndexed { i, c ->
                    if (c == '◂' || c == '▸') {
                        sp.setSpan(
                            android.text.style.RelativeSizeSpan(0.7f), i, i + 1,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                text = sp
            }
        }
        k.addView(
            IconView(
                this,
                IconView.MIC,
                if (lightTheme) Color.BLACK else Color.WHITE,
                dp(2).toFloat()
            ),
            FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(6)
                rightMargin = dp(8)
            }
        )
        return k
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun navInset(): Int {
        if (Build.VERSION.SDK_INT < 35) return 0
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun qwertyKey(baseLetter: String, sub: String? = null, weight: Float = 1f): View {
        val k = key(
            baseLetter.lowercase(tr),
            sub = sub,
            size = 18f,
            onLong = sub?.let { s -> { typeText(s) } }) {
            val isUp = shift || caps || upper
            val textToType = if (isUp) baseLetter.uppercase(tr) else baseLetter.lowercase(tr)
            typeText(textToType)
            if (shift) {
                shift = false
                updateShiftLook()
            }
        }
        letterLabels.add((k.tag as TextView) to baseLetter)
        return k
    }

    private fun buildQwertyPage(): View {
        letterLabels.clear()
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Row 1: Q W E R T Y U I O P Ğ Ü
        val row1Items = listOf(
            "Q" to "1", "W" to "2", "E" to "3", "R" to "4",
            "T" to "5", "Y" to "6", "U" to "7", "I" to "8",
            "O" to "9", "P" to "0", "Ğ" to "₺", "Ü" to "»"
        )
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1Items.forEach { (b, s) -> add(row1, qwertyKey(b, s), 1f) }

        // Row 2: A S D F G H J K L Ş İ
        val row2Items = listOf(
            "A" to "\\", "S" to "_", "D" to "(", "F" to ":",
            "G" to ")", "H" to "&", "J" to "#", "K" to "*",
            "L" to "\"", "Ş" to "|", "İ" to "€"
        )
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2Items.forEach { (b, s) -> add(row2, qwertyKey(b, s), 1f) }

        // Row 3: Shift + Z X C V B N M Ö Ç + Backspace
        val row3Items = listOf(
            "Z" to "@", "X" to "/", "C" to "-", "V" to "'",
            "B" to "!", "N" to "?", "M" to ";", "Ö" to "`", "Ç" to "÷"
        )
        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sk = key("", accent = true) { onShiftTap() }
        val shiftIc = IconView(this, IconView.SHIFT_OFF, SHIFTOFF, dp(3).toFloat())
        sk.addView(shiftIc, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        shiftIcon = shiftIc
        add(row3, sk, 1.3f)

        row3Items.forEach { (b, s) -> add(row3, qwertyKey(b, s), 1f) }

        val bk = key("⌫", accent = true, bg = DIM, repeat = true, accel = true) { backspace() }
        add(row3, bk, 1.3f)

        // Row 4: 12# , Space . Enter
        val row4 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val b12 = key("12#", accent = true) { showPage(sym1) }
        val commaKey = key(",", sub = "`", onLong = { typeText("`") }) { typeText(",") }
        val space = spaceKey("◂ Türkçe ▸")
        val dotKey = key(".", onLong = { typeText("…") }) { typeText(".") }
        val ent = enterKey()

        add(row4, b12, 1.3f)
        add(row4, commaKey, 1f)
        add(row4, space, 4.5f)
        add(row4, dotKey, 1f)
        add(row4, ent, 1.3f)

        add(page, row1, 1f)
        add(page, row2, 1f)
        add(page, row3, 1f)
        add(page, row4, 1f)

        updateShiftLook()
        return page
    }

    private fun buildT9Page(): View {
        if (isLandscape) return buildQwertyPage()
        letterLabels.clear()
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val main = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        leftCol = left
        lastColSig = "?"
        updateLeftColumn()
        add(main, left, 0.65f)

        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = listOf(
            listOf(Triple("@", "", "1"), Triple("ABC", "abcçâ", "2"), Triple("DEF", "def", "3")),
            listOf(
                Triple("GHİ", "ghiığî", "4"),
                Triple("JKL", "jkl", "5"),
                Triple("MNO", "mnoö", "6")
            ),
            listOf(
                Triple("PQRS", "pqrsş", "7"),
                Triple("TUV", "tuvüû", "8"),
                Triple("WXYZ", "wxyz", "9")
            )
        )
        rows.forEach { r ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.forEach { (l, c, d) -> add(row, t9Key(l, c, d), 1f) }
            add(mid, row, 1f)
        }
        add(main, mid, 3f)

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(right, key("⌫", accent = true, repeat = true, accel = true) { backspace() }, 1f)
        val pk = key("a✓", sub = "0", accent = true, onLong = { typeText("0") }) {
            commitWord(false)
            predictive = !predictive
            updatePredLook()
        }
        predView = pk.tag as TextView
        updatePredLook()
        add(right, pk, 1f)
        val sk = key("", accent = true) { onShiftTap() }
        val shiftIc = IconView(this, IconView.SHIFT_OFF, SHIFTOFF, dp(3).toFloat())
        sk.addView(shiftIc, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        shiftIcon = shiftIc
        updateShiftLook()
        add(right, sk, 1f)
        add(main, right, 0.65f)

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        add(bottom, key("12#", accent = true) { showPage(sym1) }, 0.65f)
        add(bottom, key(",") { typeText(",") }, 0.45f)
        add(bottom, spaceKey("◂ Türkçe ▸"), 1.95f)
        add(bottom, key(".") { typeText(".") }, 0.45f)
        add(bottom, enterKey(), 0.65f)

        add(page, main, 3f)
        add(page, bottom, 1f)
        return page
    }

    private class SK(val l: String, val sub: String? = null, val w: Float = 1f)

    private fun symRow(items: List<SK>, backspaceW: Float = 0f): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { k ->
            val long: (() -> Unit)? = k.sub?.let { s -> { typeText(s) } }
            add(row, key(k.l, k.sub, size = 20f, onLong = long) { typeText(k.l) }, k.w)
        }
        if (backspaceW > 0f) add(
            row,
            key("⌫", accent = true, repeat = true, accel = true) { backspace() },
            backspaceW
        )
        return row
    }

    private fun buildSym1(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val d = 1.4f
        add(
            page, symRow(
                listOf(
                    SK("1", null, d), SK("2", null, d), SK("3", null, d),
                    SK("+"), SK("-", "_"), SK("@", "&"), SK("₺", "€"), SK("(", "<"), SK(")", ">")
                )
            ), 1f
        )
        add(
            page, symRow(
                listOf(
                    SK("4", null, d), SK("5", null, d), SK("6", null, d),
                    SK("*"), SK("/"), SK("'", "‹"), SK("\"", "«"), SK(":", ";"), SK("#", "%")
                )
            ), 1f
        )
        add(
            page, symRow(
                listOf(
                    SK("7", null, d), SK("8", null, d), SK("9", null, d),
                    SK(","), SK("="), SK("!", "¡"), SK("?", "¿")
                ), 2f
            ), 1f
        )
        val b = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        add(b, key("abc", accent = true) { showPage(t9Page) }, 1.4f)
        add(b, key("0", size = 20f) { typeText("0") }, 1.4f)
        add(b, key(".", size = 20f) { typeText(".") }, 1.4f)
        add(b, spaceKey(""), 3.6f)
        add(b, key("1/2", accent = true) { showPage(sym2) }, 1.2f)
        add(b, enterKey(), 1.2f)
        add(page, b, 1f)
        return page
    }

    private fun buildSym2(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(
            page,
            symRow(listOf("[", "]", "{", "}", "<", ">", "&", "_", "%", "√").map { SK(it) }),
            1f
        )
        add(
            page, symRow(
                listOf(
                    SK("|", "¦"), SK("\\", "§"), SK("~"), SK("•", "°"), SK("`"),
                    SK("…"), SK("€", "$"), SK("¥"), SK("£"), SK("¢")
                )
            ), 1f
        )
        add(
            page, symRow(
                listOf(
                    SK("α"), SK("β"), SK("^", "Δ"), SK("®"), SK("©"),
                    SK("™"), SK("π", "Π"), SK("¤", "Φ")
                ), 2f
            ), 1f
        )
        val b = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        add(b, key("abc", accent = true) { showPage(t9Page) }, 1.4f)
        add(b, key("☺") { showPage(emojiPage) }, 1.4f)
        add(b, spaceKey(""), 4.2f)
        add(b, key("2/2", accent = true) { showPage(sym1) }, 1.5f)
        add(b, enterKey(), 1.5f)
        add(page, b, 1f)
        return page
    }

    private val recentEmojis = mutableListOf<String>()

    private fun loadRecentEmojis() {
        recentEmojis.clear()
        val saved = prefs.getString("recent_emojis", "👍 😀 😂 🙏 ❤️ 🔥 ✨ 😊") ?: ""
        recentEmojis.addAll(saved.split(" ").filter { it.isNotEmpty() }.take(8))
    }

    private val skinToneBaseEmojis = setOf(
        "👍", "👎", "👏", "🙏", "💪", "👋", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘",
        "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "✍️",
        "🙋", "💁", "🙅", "🙆", "🙇", "🤷", "🤦", "💆", "💇", "🚶", "🏃",
        "🧑", "👦", "👧", "👨", "👩", "🧓", "👴", "👵", "🤝", "🤲", "🦾", "🦿"
    )

    private fun showSkinTonePopup(anchor: View, baseEmoji: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor("#2D3748".toColorInt())
                cornerRadius = dp(24).toFloat()
            }
        }
        val variants = listOf(baseEmoji) + listOf(
            "\uD83C\uDFFB",
            "\uD83C\uDFFC",
            "\uD83C\uDFFD",
            "\uD83C\uDFFE",
            "\uD83C\uDFFF"
        ).map { baseEmoji + it }
        val popup = PopupWindow(layout, -2, -2, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
        }
        variants.forEach { v ->
            val tv = TextView(this).apply {
                text = v
                textSize = 26f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setOnClickListener {
                    typeText(v)
                    recordRecentEmoji(v)
                    popup.dismiss()
                }
            }
            layout.addView(tv)
        }
        layout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val screenWidth = resources.displayMetrics.widthPixels
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        var screenX = loc[0] + (anchor.width - layout.measuredWidth) / 2
        val rightMargin = dp(16)
        if (screenX + layout.measuredWidth > screenWidth - rightMargin) {
            screenX = screenWidth - layout.measuredWidth - rightMargin
        }
        if (screenX < rightMargin) {
            screenX = rightMargin
        }
        val xOff = screenX - loc[0]
        val yOff = -layout.measuredHeight - anchor.height - dp(8)
        popup.showAsDropDown(anchor, xOff, yOff)
    }

    private lateinit var recentRowContainer: LinearLayout

    private fun recordRecentEmoji(e: String) {
        recentEmojis.remove(e)
        recentEmojis.add(0, e)
        if (recentEmojis.size > 8) recentEmojis.removeAt(recentEmojis.size - 1)
        prefs.edit {putString("recent_emojis", recentEmojis.joinToString(" "))}
        populateRecentRow()
    }

    private fun populateRecentRow() {
        if (!::recentRowContainer.isInitialized) return
        recentRowContainer.removeAllViews()
        for (i in 0 until 8) {
            val e = recentEmojis.getOrNull(i) ?: ""
            val tv = TextView(this).apply {
                text = e
                textSize = 26f
                gravity = Gravity.CENTER
                if (e.isNotEmpty()) {
                    setOnClickListener {
                        typeText(e)
                        recordRecentEmoji(e)
                    }
                    if (skinToneBaseEmojis.any { e.startsWith(it) }) {
                        setOnLongClickListener {
                            showSkinTonePopup(it, e.substring(0, 2))
                            true
                        }
                    }
                }
            }
            recentRowContainer.addView(tv, LinearLayout.LayoutParams(0, dp(46), 1f))
        }
    }

    private fun buildEmojiPage(): View {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        loadRecentEmojis()

        val titleRecent = TextView(this).apply {
            text = "Son kullanılanlar"
            textSize = 13f
            setTextColor(SUBTXT)
            setPadding(dp(16), dp(10), dp(16), dp(2))
        }
        col.addView(titleRecent)

        recentRowContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        populateRecentRow()
        col.addView(recentRowContainer)

        val titleAll = TextView(this).apply {
            text = "Yüz ifadeleri ve insanlar"
            textSize = 13f
            setTextColor(SUBTXT)
            setPadding(dp(16), dp(14), dp(16), dp(2))
        }
        col.addView(titleAll)

        EMOJIS.split(" ").filter { it.isNotEmpty() }.chunked(8).forEach { chunk ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in 0 until 8) {
                val e = chunk.getOrNull(i)
                val tv = TextView(this).apply {
                    text = e ?: ""; textSize = 26f; gravity = Gravity.CENTER
                    if (e != null) {
                        setOnClickListener {
                            typeText(e)
                            recordRecentEmoji(e)
                        }
                        if (e in skinToneBaseEmojis) {
                            setOnLongClickListener {
                                showSkinTonePopup(it, e)
                                true
                            }
                        }
                    }
                }
                row.addView(tv, LinearLayout.LayoutParams(0, dp(46), 1f))
            }
            col.addView(row)
        }
        scroll.addView(col, FrameLayout.LayoutParams(MATCH_PARENT, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        add(bottom, key("ABC", accent = true, size = 18f) { showPage(t9Page) }, 1.5f)
        add(bottom, spaceKey("", 1f), 4f)
        add(bottom, key("⌫", accent = true, repeat = true, accel = true) { backspace() }, 1.5f)
        panel.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, dp(52)))
        return panel
    }

    private fun sendNav(code: Int) {
        commitWord(false)
        attach = null
        val ic = currentInputConnection ?: return
        val t = SystemClock.uptimeMillis()
        val meta = if (selectMode) KeyEvent.META_SHIFT_ON else 0
        if (selectMode) ic.sendKeyEvent(
            KeyEvent(
                t,
                t,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_SHIFT_LEFT,
                0,
                KeyEvent.META_SHIFT_ON
            )
        )
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
        if (selectMode) ic.sendKeyEvent(
            KeyEvent(
                t,
                t,
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_SHIFT_LEFT,
                0,
                0
            )
        )
    }

    private fun updateSelLook() {
        if (!::selKey.isInitialized) return
        (selKey.background as? GradientDrawable)?.setColor(if (selectMode) SELBG else KEY)
        (selKey.tag as? TextView)?.setTextColor(if (selectMode) ACCENT else TXT)
    }

    private fun buildCursorPage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun row(vararg k: View): LinearLayout {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            k.forEach { add(r, it, 1f) }
            return r
        }

        fun txt(label: String, f: () -> Unit) = key(label, size = 16f) { f() }
        selKey = key("Seç", size = 16f) { selectMode = !selectMode; updateSelLook() }
        updateSelLook()
        add(
            page, row(
            txt("Pano") { showPage(clipPage) },
            iconKey(IconView.UP, repeat = true) { sendNav(KeyEvent.KEYCODE_DPAD_UP) },
            txt("Tümü") { menuAction(android.R.id.selectAll) },
            txt("Kopyala") { menuAction(android.R.id.copy) },
            key("⌫", accent = true, bg = DIM, repeat = true, accel = true) { backspace() }), 1f
        )
        add(
            page, row(
            iconKey(IconView.LEFT, repeat = true) { sendNav(KeyEvent.KEYCODE_DPAD_LEFT) },
            selKey,
            iconKey(IconView.RIGHT, repeat = true) { sendNav(KeyEvent.KEYCODE_DPAD_RIGHT) },
            txt("Kes") { menuAction(android.R.id.cut) },
            iconKey(IconView.SPACE) { commitWord(true) }), 1f
        )
        add(
            page, row(
            txt("Giriş") { sendNav(KeyEvent.KEYCODE_MOVE_HOME) },
            iconKey(IconView.DOWN, repeat = true) { sendNav(KeyEvent.KEYCODE_DPAD_DOWN) },
            txt("Son") { sendNav(KeyEvent.KEYCODE_MOVE_END) },
            txt("Yapıştır") { menuAction(android.R.id.paste) },
            key("↩", accent = true, bg = DIM) { showPage(t9Page) }), 1f
        )
        return page
    }

    private fun openClipEditor(text: String?, pinned: Boolean) {
        val i = Intent(this, ClipEditActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (text != null) {
            i.putExtra("text", text); i.putExtra("pinned", pinned)
        }
        startActivity(i)
    }

    private fun pasteClip(text: String) {
        commitWord(false)
        attach = null
        currentInputConnection?.commitText(text, 1)
        prevWord = null
        showPage(t9Page)
        updateSuggestions()
    }

    private fun makeCard(
        text: String, bg: Int, pinned: Boolean, swipeable: Boolean,
        onTap: () -> Unit, onLong: () -> Unit, onDelete: () -> Unit
    ): View {
        val card = FrameLayout(this)
        card.background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(6).toFloat() }
        card.addView(TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor("#222222".toColorInt())
            maxLines = 8
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }, FrameLayout.LayoutParams(MATCH_PARENT, -2))
        if (pinned) {
            card.addView(
                TextView(this).apply { this.text = "📌"; textSize = 12f },
                FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END)
            )
        }
        var downX = 0f
        var downY = 0f
        var dragging = false
        var longFired = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val longRunnable = Runnable { longFired = true; onLong() }
        card.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; dragging = false; longFired = false
                    handler.postDelayed(longRunnable, 500)
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (swipeable && !dragging && abs(dx) > slop && abs(dx) > abs(dy)) {
                        dragging = true
                        handler.removeCallbacks(longRunnable)
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        handler.removeCallbacks(longRunnable)
                    }
                    if (dragging) {
                        v.translationX = dx
                        v.alpha = 1f - min(1f, abs(dx) / v.width)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longRunnable)
                    val dx = e.rawX - downX
                    if (dragging) {
                        if (abs(dx) > v.width / 3) onDelete()
                        else v.animate().translationX(0f).alpha(1f).setDuration(120).start()
                    } else if (!longFired && abs(dx) < slop && abs(e.rawY - downY) < slop) {
                        onTap()
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longRunnable)
                    v.translationX = 0f
                    v.alpha = 1f
                }
            }
            true
        }
        return card
    }

    private fun refreshClips() {
        if (!::clipLeft.isInitialized) return
        clipManager.capture(false)
        clipLeft.removeAllViews()
        clipRight.removeAllViews()
        var hl = 0
        var hr = 0
        fun put(v: View, lines: Int) {
            val lp = LinearLayout.LayoutParams(MATCH_PARENT, -2)
                .apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
            if (hl <= hr) {
                clipLeft.addView(v, lp); hl += lines
            } else {
                clipRight.addView(v, lp); hr += lines
            }
        }

        val hintBg = "#F8EDC8".toColorInt()
        if (!prefs.getBoolean("hint_del", false)) {
            put(
                makeCard(
                "Kartı silmek için sola ya da sağa kaydırın", hintBg, pinned = false,
                swipeable = true,
                onTap = {},
                onLong = {}) {
                prefs.edit { putBoolean("hint_del", true) }; refreshClips()
            }, 3
            )
        }
        if (!prefs.getBoolean("hint_edit", false)) {
            put(
                makeCard(
                "Düzenlemek ve kilitlemek için uzun basın", hintBg, pinned = false,
                swipeable = true,
                onTap = {},
                onLong = {}) {
                prefs.edit { putBoolean("hint_edit", true) }; refreshClips()
            }, 3
            )
        }
        val all = ClipStore.load(this)
        val sorted = all.filter { it.pinned } + all.filter { !it.pinned }
        if (sorted.isEmpty()) {
            put(
                makeCard(
                "Henüz kopyalanan bir şey yok.", hintBg, pinned = false,
                swipeable = false,
                onTap = {},
                onLong = {},
                onDelete = {}), 3
            )
        }
        sorted.forEach { c ->
            put(
                makeCard(
                    c.text, "#FAFAFA".toColorInt(), c.pinned, !c.pinned,
                    { pasteClip(c.text) },
                    { openClipEditor(c.text, c.pinned) },
                    { ClipStore.remove(this, c.text); refreshClips() }),
                minOf(8, c.text.length / 16 + 1)
            )
        }
    }

    private fun buildClipPage(): View {
        val page = FrameLayout(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = TextView(this).apply {
            text = "+   YENİ OLUŞTUR"
            textSize = 17f
            setTextColor(TXT)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(16), 0)
            setOnClickListener { openClipEditor(null, false) }
        }
        box.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, dp(48)))
        val scroll = ScrollView(this)
        val cols = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), 0, dp(4), dp(8))
        }
        clipLeft = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clipRight = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cols.addView(clipLeft, LinearLayout.LayoutParams(0, -2, 1f))
        cols.addView(clipRight, LinearLayout.LayoutParams(0, -2, 1f))
        scroll.addView(cols)
        box.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        page.addView(box, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return page
    }

    private fun buildSettingsPage(): View {
        val page = FrameLayout(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }

        var predLabel: TextView? = null

        fun makeTile(
            iconText: String?,
            iconKind: Int?,
            titleText: String,
            onClick: () -> Unit,
            onLabelCreated: ((TextView) -> Unit)? = null
        ): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(4), dp(8), dp(4), dp(8))
                val iconView: View = if (iconKind != null) {
                    FrameLayout(context).apply {
                        addView(
                            IconView(context, iconKind, TXT, dp(2).toFloat()),
                            FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER)
                        )
                    }
                } else {
                    TextView(context).apply {
                        text = iconText ?: ""
                        textSize = 28f
                        gravity = Gravity.CENTER
                        setTextColor(TXT)
                    }
                }
                val label = TextView(context).apply {
                    text = titleText
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(ICON)
                    setPadding(0, dp(6), 0, 0)
                }
                onLabelCreated?.invoke(label)
                addView(iconView, LinearLayout.LayoutParams(MATCH_PARENT, -2))
                addView(label, LinearLayout.LayoutParams(MATCH_PARENT, -2))
                setOnClickListener { onClick() }
            }
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tTemalar = makeTile("👕", null, "Temalar", { showPage(themePage) })
        var sesLabel: TextView? = null
        val tSes = makeTile(
            null,
            IconView.MIC,
            if (voiceInputEnabled) "Sesle Yazma AÇIK" else "Sesle Yazma KAPALI",
            {
                voiceInputEnabled = !voiceInputEnabled
                sesLabel?.text = if (voiceInputEnabled) "Sesle Yazma AÇIK" else "Sesle Yazma KAPALI"
                sesLabel?.setTextColor(if (voiceInputEnabled) ACCENT else ICON)
            },
            onLabelCreated = { lbl ->
                sesLabel = lbl
                lbl.setTextColor(if (voiceInputEnabled) ACCENT else ICON)
            })
        val tBoyut = makeTile("⤢", null, "Yeniden Boyutlandır", { showResizeOverlay() })
        val tSozluk = makeTile("📖", null, "Sözlüğüm", { openUserDictionaryActivity() })
        listOf(tTemalar, tSes, tBoyut, tSozluk).forEach {
            row1.addView(it, LinearLayout.LayoutParams(0, -2, 1f))
        }

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        val tTahmin = makeTile("🔤", null, if (predictive) "Tahmin AÇIK" else "Tahmin KAPALI", {
            predictive = !predictive
            updatePredLook()
            predLabel?.text = if (predictive) "Tahmin AÇIK" else "Tahmin KAPALI"
            predLabel?.setTextColor(if (predictive) ACCENT else ICON)
            val ic = currentInputConnection
            if (seq.isNotEmpty() && ic != null && attach == null) refreshPredictive(ic) else updateSuggestions()
        }, onLabelCreated = { lbl ->
            predLabel = lbl
            lbl.setTextColor(if (predictive) ACCENT else ICON)
        })
        val tDil = makeTile("🌐", null, "Dil", {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        val tPano = makeTile("📋", null, "Pano", { showPage(clipPage) })
        var kelimeLabel: TextView? = null
        fun kelimeText() = if (autoAddWords) "Kelime Ekleme\nOtomatik" else "Kelime Ekleme\nManuel"
        val tAyarlar = makeTile("⊕", null, kelimeText(), {
            autoAddWords = !autoAddWords
            if (autoAddWords) recentUnknownWord = null
            kelimeLabel?.text = kelimeText()
            kelimeLabel?.setTextColor(if (autoAddWords) ACCENT else ICON)
            updateSuggestions()
        }, onLabelCreated = { lbl ->
            kelimeLabel = lbl
            lbl.setTextColor(if (autoAddWords) ACCENT else ICON)
        })
        listOf(tTahmin, tDil, tPano, tAyarlar).forEach {
            row2.addView(it, LinearLayout.LayoutParams(0, -2, 1f))
        }

        container.addView(row1, LinearLayout.LayoutParams(MATCH_PARENT, -2))
        container.addView(row2, LinearLayout.LayoutParams(MATCH_PARENT, -2))
        page.addView(container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return page
    }

    private fun openUserDictionaryActivity() {
        saveUserData()
        val i =
            Intent(this, UserDictionaryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    private fun isNumericField(info: EditorInfo?): Boolean {
        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        return cls == InputType.TYPE_CLASS_NUMBER ||
                cls == InputType.TYPE_CLASS_PHONE ||
                cls == InputType.TYPE_CLASS_DATETIME
    }

    private fun updateNumActionLabel(info: EditorInfo?) {
        val lbl: String = run {
            if (info == null) return@run "↵"
            val custom = info.actionLabel
            if (!custom.isNullOrEmpty()) return@run custom.toString()
            when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_DONE -> "Bitti"
                EditorInfo.IME_ACTION_GO -> "Git"
                EditorInfo.IME_ACTION_NEXT -> "İleri"
                EditorInfo.IME_ACTION_SEARCH -> "Ara"
                EditorInfo.IME_ACTION_SEND -> "Gönder"
                EditorInfo.IME_ACTION_PREVIOUS -> "Geri"
                else -> "↵"
            }
        }
        numActionLabel?.apply {
            text = lbl
            textSize = if (lbl == "↵") 26f else 16f
        }
    }

    private fun typeNum(ch: String) {
        val t = currentInputEditorInfo?.inputType ?: 0
        if (t == 0 && ch.length == 1 && ch[0] in '0'..'9') {
            commitWord(false)
            sendDownUpKeyEvents(KeyEvent.KEYCODE_0 + (ch[0] - '0'))
        } else {
            typeText(ch)
        }
    }

    private fun numBackspace() {
        if ((currentInputEditorInfo?.inputType ?: 0) == 0) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        } else {
            backspace()
        }
    }

    private fun numAction() {
        val ei = currentInputEditorInfo
        val a = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (a != EditorInfo.IME_ACTION_NONE && a != EditorInfo.IME_ACTION_UNSPECIFIED) editorAction() else enter()
    }

    private fun buildNumPage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun digit(d: String) = key(d, size = 28f) { typeNum(d) }
        fun sym(s: String) = key(s, accent = true, bg = DIM, size = 24f) { typeNum(s) }
        fun row(vararg v: View): LinearLayout {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            v.forEachIndexed { i, view ->
                add(
                    r,
                    view,
                    if (i == 0 || i == v.size - 1) 0.85f else 1f
                )
            }
            return r
        }

        val back = key("↩", accent = true, bg = DIM, size = 26f) { showPage(t9Page) }
        val space = iconKey(IconView.SPACE) { currentInputConnection?.commitText(" ", 1) }
        val action = key("↵", accent = true, bg = DIM, size = 26f) { numAction() }
        numActionLabel = action.tag as? TextView
        add(
            page, row(
            sym("+"), digit("1"), digit("2"), digit("3"),
            key("⌫", accent = true, bg = DIM, repeat = true, accel = true) { numBackspace() }), 1f
        )
        add(page, row(sym("-"), digit("4"), digit("5"), digit("6"), back), 1f)
        add(page, row(sym("."), digit("7"), digit("8"), digit("9"), space), 1f)
        add(page, row(sym("/"), sym("*"), digit("0"), sym("#"), action), 1f)
        return page
    }

    private fun applyTheme() {
        if (lightTheme) {
            BG = "#E4E7EB".toColorInt()
            KEY = "#FFFFFF".toColorInt()
            DIM = "#D3D8DD".toColorInt()
            ACCENT = "#00897B".toColorInt()
            TXT = "#202124".toColorInt()
            SUBTXT = Color.parseColor("#6F757A")
            ICON = Color.parseColor("#5F6368")
            SELBG = Color.parseColor("#B2DFDB")
            SHIFTOFF = Color.parseColor("#5FA79D")
        } else {
            BG = Color.parseColor("#212121")
            KEY = Color.parseColor("#2E3236")
            DIM = Color.parseColor("#262829")
            ACCENT = Color.parseColor("#5FD3C5")
            TXT = Color.WHITE
            SUBTXT = Color.parseColor("#8A8E92")
            ICON = Color.LTGRAY
            SELBG = Color.parseColor("#3F6B66")
            SHIFTOFF = Color.parseColor("#7FA8A3")
        }
    }

    private fun applyNavBarAppearance() {
        try {
            val w = window?.window ?: return
            androidx.core.view.WindowCompat.getInsetsController(
                w,
                w.decorView
            ).isAppearanceLightNavigationBars = lightTheme
        } catch (e: Exception) {
            Log.w(TAG, "Gezinti çubuğu görünümü ayarlanamadı", e)
        }
    }

    private fun setLightTheme(light: Boolean) {
        if (lightTheme == light) return
        lightTheme = light
        prefs.edit().putBoolean("theme_light", light).apply()
        applyTheme()
        setInputView(onCreateInputView())
        showPage(themePage)
        android.widget.Toast.makeText(
            this,
            if (light) "Beyaz tema" else "Koyu tema",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun buildThemePage(): View {
        val page = FrameLayout(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        fun card(name: String, light: Boolean): View {
            val selected = lightTheme == light
            val pBg = Color.parseColor(if (light) "#E4E7EB" else "#212121")
            val pKey = Color.parseColor(if (light) "#FFFFFF" else "#2E3236")
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(10).toFloat()
                    if (selected) setStroke(dp(2), ACCENT)
                }
                setOnClickListener { setLightTheme(light) }
            }
            val preview = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(6), dp(6), dp(6))
                background = GradientDrawable().apply {
                    setColor(pBg)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.GRAY)
                }
            }
            for (r in 0 until 3) {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (c in 0 until 4) {
                    val k = View(this).apply {
                        background = GradientDrawable().apply {
                            setColor(pKey); cornerRadius = dp(3).toFloat()
                        }
                    }
                    line.addView(
                        k,
                        LinearLayout.LayoutParams(0, dp(16), 1f)
                            .apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                }
                preview.addView(line, LinearLayout.LayoutParams(MATCH_PARENT, -2))
            }
            box.addView(preview, LinearLayout.LayoutParams(dp(130), -2))
            box.addView(TextView(this).apply {
                text = if (selected) "✓ $name" else name
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(if (selected) ACCENT else TXT)
                setPadding(0, dp(8), 0, 0)
            })
            return box
        }
        row.addView(card("Koyu", false), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(card("Beyaz", true), LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(row, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return page
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        keyboardRootView = root
        navBottomPx = navInset()
        applyRootPadding()
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            navBottomPx = if (nav > 0) nav else navInset()
            applyRootPadding()
            insets
        }

        val topFrame = FrameLayout(this)
        iconBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val toolSize = if (isLandscape) 18f else 22f
        fun tool(txt: String, f: () -> Unit) = TextView(this).apply {
            text = txt; textSize = toolSize; gravity = Gravity.CENTER
            setTextColor(ICON); setOnClickListener { f() }
        }

        val hideBtn = tool("⌄") {
            if (::t9Page.isInitialized && ::numPage.isInitialized &&
                t9Page.visibility != View.VISIBLE && numPage.visibility != View.VISIBLE
            ) {
                showPage(t9Page)
            } else {
                requestHideSelf(0)
            }
        }

        fun iconTool(kind: Int, f: () -> Unit): FrameLayout {
            val fv = FrameLayout(this)
            fv.addView(
                IconView(this, kind, ICON, dp(2).toFloat()),
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            )
            fv.setOnClickListener { f() }
            return fv
        }
        hideOrReturnTool = hideBtn
        listOf(
            tool("☰") { showPage(settingsPage) },
            iconTool(IconView.CURSOR) { showPage(cursorPage) },
            tool("📋") { showPage(clipPage) },
            tool("☺") { showPage(emojiPage) },
            hideBtn
        ).forEach { iconBar.addView(it, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)) }
        suggestLeftSpacer = View(this)
        suggestScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
        }
        suggestRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        suggestScroll.addView(suggestRow, FrameLayout.LayoutParams(-2, MATCH_PARENT))
        closeBtn = TextView(this).apply {
            text = "✕"; textSize = if (isLandscape) 16f else 20f; gravity = Gravity.CENTER
            setTextColor(ICON); setOnClickListener { dismissBar() }
        }
        suggestBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        suggestBox.addView(suggestLeftSpacer, LinearLayout.LayoutParams(dp(48), MATCH_PARENT))
        suggestBox.addView(suggestScroll, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        suggestBox.addView(closeBtn, LinearLayout.LayoutParams(dp(48), MATCH_PARENT))
        topFrame.addView(iconBar, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        topFrame.addView(suggestBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val topBarH = if (isLandscape) dp(32) else dp(44)
        root.addView(topFrame, LinearLayout.LayoutParams(MATCH_PARENT, topBarH))

        t9Page = buildT9Page()
        sym1 = buildSym1()
        sym2 = buildSym2()
        emojiPage = buildEmojiPage()
        cursorPage = buildCursorPage()
        clipPage = buildClipPage()
        settingsPage = buildSettingsPage()
        themePage = buildThemePage()
        numPage = buildNumPage()
        pages = listOf(
            t9Page,
            sym1,
            sym2,
            emojiPage,
            cursorPage,
            clipPage,
            settingsPage,
            themePage,
            numPage
        )
        val content = FrameLayout(this)
        contentLayoutView = content
        pages.forEach { content.addView(it, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }
        pages.forEach { it.visibility = if (it === t9Page) View.VISIBLE else View.GONE }
        val savedHeight = prefs.getInt("kb_height", 0)
        val contentHeight = if (isLandscape) {
            val maxH =
                (resources.displayMetrics.heightPixels * 0.38f).toInt().coerceAtLeast(dp(100))
            minOf(dp(135), maxH)
        } else {
            if (savedHeight > 0) savedHeight.coerceIn(dp(160), dp(520)) else dp(4 * 58)
        }
        root.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, contentHeight))

        renderSuggestions()

        val kbFrame = FrameLayout(this).apply { setBackgroundColor(BG) }
        kbFrame.addView(root, FrameLayout.LayoutParams(MATCH_PARENT, -2))
        keyboardFrameView = kbFrame
        val outer = FrameLayout(this)
        val lift = if (isLandscape) 0 else prefs.getInt("kb_lift", 0).coerceIn(0, dp(200))
        outer.addView(kbFrame, FrameLayout.LayoutParams(MATCH_PARENT, -2, Gravity.BOTTOM).apply {
            bottomMargin = lift
        })
        applyNavBarAppearance()
        return outer
    }

    private fun applyRootPadding() {
        val bottomPx = if (isLandscape) 0 else navBottomPx
        keyboardRootView?.setPadding(
            dp(3) + prefs.getInt("kb_ml", 0), 0,
            dp(3) + prefs.getInt("kb_mr", 0), dp(4) + bottomPx
        )
        syncResizeOverlay()
    }

    private fun syncResizeOverlay() {
        val frame = keyboardFrameView ?: return
        val ov = frame.findViewWithTag<View>("resize_overlay") ?: return
        val lp = ov.layoutParams as FrameLayout.LayoutParams
        lp.leftMargin = dp(3) + prefs.getInt("kb_ml", 0)
        lp.rightMargin = dp(3) + prefs.getInt("kb_mr", 0)
        lp.bottomMargin = dp(4) + navBottomPx
        ov.layoutParams = lp
    }

    private fun showResizeOverlay() {
        showPage(t9Page)
        val frame = keyboardFrameView ?: return
        val content = contentLayoutView ?: return
        frame.findViewWithTag<View>("resize_overlay")?.let { frame.removeView(it) }

        val screenW = resources.displayMetrics.widthPixels
        val minH = dp(170)
        val maxH = dp(520)
        val maxSide = (screenW * 0.35f).toInt()
        val maxLift = dp(200)
        val blue = Color.parseColor("#2F8CFF")

        val overlay = FrameLayout(this).apply {
            tag = "resize_overlay"
            isClickable = true
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#B3000000"))
                setStroke(dp(1), Color.WHITE, dp(6).toFloat(), dp(4).toFloat())
            }
        }

        fun setHeight(h: Int) {
            val nh = h.coerceIn(minH, maxH)
            content.layoutParams = content.layoutParams.apply { height = nh }
            content.requestLayout()
            prefs.edit().putInt("kb_height", nh).apply()
        }

        fun setSide(key: String, m: Int) {
            prefs.edit().putInt(key, m.coerceIn(0, maxSide)).apply()
            applyRootPadding()
        }

        fun setLift(v: Int) {
            val nv = v.coerceIn(0, maxLift)
            prefs.edit().putInt("kb_lift", nv).apply()
            (frame.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.bottomMargin = nv
                frame.layoutParams = it
            }
        }

        fun makeDrag(
            target: View,
            horizontal: Boolean,
            startValue: () -> Int,
            onDelta: (Int, Int) -> Unit
        ) {
            var down = 0f
            var start = 0
            target.setOnTouchListener { v, e ->
                val pos = if (horizontal) e.rawX else e.rawY
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        down = pos
                        start = startValue()
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    MotionEvent.ACTION_MOVE -> onDelta(start, (pos - down).toInt())
                }
                true
            }
        }

        val stroke = dp(3).toFloat()

        val joy = JoystickView(this)
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun btn(label: String, bg: Int, fg: Int, f: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(fg)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(6).toFloat() }
            setOnClickListener { f() }
        }
        buttons.addView(btn("Sıfırla", KEY, TXT) {
            prefs.edit().remove("kb_height").remove("kb_ml").remove("kb_mr").remove("kb_lift")
                .apply()
            content.layoutParams = content.layoutParams.apply { height = dp(4 * 58) }
            content.requestLayout()
            (frame.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.bottomMargin = 0
                frame.layoutParams = it
            }
            applyRootPadding()
            android.widget.Toast.makeText(
                this,
                "Boyut sıfırlandı",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(12) })
        buttons.addView(btn("✓ Tamam", ACCENT, Color.parseColor("#10201A")) {
            frame.removeView(overlay)
            android.widget.Toast.makeText(
                this,
                "Klavye boyutu kaydedildi",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        })
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        center.addView(joy, LinearLayout.LayoutParams(dp(76), dp(76)))
        center.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        overlay.addView(center, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        makeDrag(joy, false, { prefs.getInt("kb_lift", 0) }) { s0, d -> setLift(s0 - d) }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        top.addView(
            IconView(this, IconView.UP, blue, stroke),
            LinearLayout.LayoutParams(dp(64), dp(30))
        )
        top.addView(
            IconView(this, IconView.DOWN, blue, stroke),
            LinearLayout.LayoutParams(dp(64), dp(30))
        )
        overlay.addView(
            top,
            FrameLayout.LayoutParams(dp(150), dp(64), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        )
        makeDrag(
            top,
            false,
            { if (content.height > 0) content.height else dp(4 * 58) }) { s0, d -> setHeight(s0 - d) }

        val left = IconView(this, IconView.RIGHT, blue, stroke)
        overlay.addView(
            left,
            FrameLayout.LayoutParams(dp(56), dp(140), Gravity.START or Gravity.CENTER_VERTICAL)
        )
        makeDrag(left, true, { prefs.getInt("kb_ml", 0) }) { s0, d -> setSide("kb_ml", s0 + d) }
        val right = IconView(this, IconView.LEFT, blue, stroke)
        overlay.addView(
            right,
            FrameLayout.LayoutParams(dp(56), dp(140), Gravity.END or Gravity.CENTER_VERTICAL)
        )
        makeDrag(right, true, { prefs.getInt("kb_mr", 0) }) { s0, d -> setSide("kb_mr", s0 - d) }

        frame.addView(overlay, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            leftMargin = dp(3) + prefs.getInt("kb_ml", 0)
            rightMargin = dp(3) + prefs.getInt("kb_mr", 0)
            bottomMargin = dp(4) + navBottomPx
        })
    }

    private companion object {
        const val TAG = "T9Service"
        val SUFFIXES =
            ("lar ler da de dan den ın in un ün a e ı i u ü ya ye yı yi yu yü nın nin nun nün " +
                    "la le ım im um üm sı si su sü ta te tan ten dı di du dü tı ti tu tü mı mi mu mü lı li lu lü " +
                    "lık lik luk lük cı ci cu cü çı çi çu çü ki nda nde ndan nden na ne ca ce ça çe sız siz suz süz " +
                    "m n k ız iz uz üz ları leri lara lere ların lerin ında inde ımız imiz umuz ümüz yor yorum yorsun " +
                    "yoruz dır dir dur dür tır tir ma me mak mek an en ar er ır ir ur ür ı").split(" ")
                .distinct()

        const val EMOJIS = "😀 😁 😂 🤣 😊 😍 😘 😎 🤔 😢 😭 😡 👍 👎 👏 🙏 💪 ❤️ 💔 🔥 ✨ 🎉 🎂 🌹 " +
                "☀️ 🌙 ⭐ 😴 🤗 😅 😉 🙂 🙄 😬 😱 😇 🥰 🤩 😜 🤪 🥳 😏 😒 😞 😔 😟 😤 🤯 " +
                "😳 🥺 😋 🤤 🤭 🤫 🤐 😷 🤒 🤮 😈 👻 💀 🙈 🙉 🙊 🐶 🐱 🐻 🦁 🐸 🐵 🐔 🦋 " +
                "🌸 🌳 🍎 🍕 🍔 🍟 ☕ 🍺 🍷 ⚽ 🏀 🎮 🎵 🎶 📱 💻 📷 🚗 ✈️ 🏠 💰 🎁 📞 " +
                "✅ ❌ ⚠️ 💯 👋 🤝 ✌️ 👌 🤞 👀 💋 💐 🌈 ☔"

    }
}
