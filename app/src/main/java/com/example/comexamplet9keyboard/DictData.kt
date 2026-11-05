package com.example.comexamplet9keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log

/**
 * Sözlük verisi: temel kelime listesi (words_tr.bin / .txt), kullanıcı kelimeleri,
 * bigram ve harf istatistikleri ile T9 ağacı. Servisten bağımsızdır; Context, SharedPreferences
 * (tembel sağlayıcı olarak) ve ana iş parçacığı Handler'ı dışarıdan alır.
 */
class DictData(
    private val context: Context,
    private val prefsProvider: () -> SharedPreferences,
    private val mainHandler: Handler
) {
    // Servis nesnesi oluşurken Context henüz bağlı olmadığından SharedPreferences ilk kullanımda alınır.
    private val prefs: SharedPreferences get() = prefsProvider()

    private companion object {
        const val TAG = "DictData"
    }

    val words = ArrayList<String>()
    val codes = ArrayList<String>()
    val wordSet = HashSet<String>()
    val builtinBigrams = HashMap<String, ArrayList<String>>()
    val userCount = HashMap<String, Int>()
    val suffixCount = HashMap<String, Int>()
    val wordIdx = HashMap<String, Int>()
    val bigram = HashMap<String, Int>()
    val charTotal = HashMap<Char, Int>()
    val userBigram = HashMap<String, HashMap<String, Int>>()
    val userWords = HashSet<String>()

    val t9Trie = T9Trie()
    var uc = ""

    fun addWord(w: String) {
        if (w.isEmpty() || !w.all { T9Keymap.charToDigit.containsKey(it) }) return
        if (!wordSet.add(w)) return
        val index = words.size
        wordIdx[w] = index
        words.add(w)

        val codeStr = T9Keymap.codeOf(w)
        codes.add(codeStr)

        t9Trie.insert(T9Code(codeStr), WordIndex(index))

        addSuffixes(w)
        addLetterStats(w)
    }

    private fun addSuffixes(w: String) {
        for (len in 1..4) if (w.length > len) {
            val k = w.takeLast(len)
            suffixCount[k] = (suffixCount[k] ?: 0) + 1
        }
    }

    private fun addLetterStats(w: String) {
        var prev = '^'
        for (c in w) {
            val k = "$prev$c"
            bigram[k] = (bigram[k] ?: 0) + 1
            charTotal[prev] = (charTotal[prev] ?: 0) + 1
            prev = c
        }
    }

    fun loadBase() {
        var loaded = false
        try {
            val binaryReader = BinaryDictionaryReader(context, "words_tr.bin")
            if (binaryReader.open()) {
                val entries = binaryReader.readAllEntries()
                if (entries.isNotEmpty()) {
                    // Sıralama frekansa göre (azalan). .bin zaten sıralı olduğundan sıra değişmez;
                    // sortedByDescending kararlıdır, yani eşit frekanslarda dosya sırası korunur.
                    entries.sortedByDescending { it.frequency }.forEach { addWord(it.word) }
                    loaded = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sözlük okunamadı", e)
        }

        if (!loaded) {
            val list = ArrayList<Pair<String, Long>>()
            var hasFreq = false
            try {
                context.assets.open("words_tr.txt").bufferedReader().useLines { ls ->
                    ls.forEach { line ->
                        val t = line.trim()
                        if (t.isNotEmpty()) {
                            val parts = t.split(' ', '\t', ',', ';').filter { it.isNotEmpty() }
                            val f = parts.getOrNull(1)?.toLongOrNull()
                            if (f != null) hasFreq = true
                            list.add(parts[0].lowercase(T9Keymap.tr) to (f ?: 0L))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "words_tr.txt okunamadı", e)
            }
            if (list.isNotEmpty()) {
                val ordered = if (hasFreq) list.sortedByDescending { it.second } else list
                ordered.forEach { addWord(it.first) }
            } else {
                Log.e(TAG, "Sözlük dosyaları okunamadı, yedek sözlük kullanılıyor")
                mainHandler.post {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.dictionary_load_failed),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                DefaultDictionary.WORDS.split(" ").forEach { addWord(it) }
            }
        }

        DefaultDictionary.BIGRAMS.split(";").forEach { e ->
            val p = e.split(":")
            if (p.size == 2) builtinBigrams.getOrPut(p[0]) { ArrayList() }
                .addAll(p[1].split(","))
        }
    }

    fun loadUser(migrate: Boolean) {
        try {
            uc = prefs.getString("uc", "") ?: ""
            uc.lines().forEach { l ->
                val p = l.split('\t')
                if (p.size == 2) {
                    val c = p[1].toIntOrNull()
                    if (c != null) {
                        val builtin = migrate && wordSet.contains(p[0])
                        userCount[p[0]] = c
                        if (!builtin) {
                            userWords.add(p[0]); addWord(p[0])
                        }
                    }
                }
            }
            (prefs.getString("lc", "") ?: "").lines().forEach { l ->
                val p = l.split('\t')
                if (p.size == 2) {
                    val c = p[1].toIntOrNull()
                    if (c != null && !userCount.containsKey(p[0])) userCount[p[0]] = c
                }
            }
            (prefs.getString("ub", "") ?: "").lines().forEach { l ->
                val p = l.split('\t')
                if (p.size == 3) {
                    val c = p[2].toIntOrNull()
                    if (c != null) userBigram.getOrPut(p[0]) { HashMap() }[p[1]] = c
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kullanıcı sözlüğü yüklenemedi", e)
        }
    }
}
