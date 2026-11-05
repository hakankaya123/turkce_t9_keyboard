package com.example.comexamplet9keyboard

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

data class UserWord(val word: String, val count: Int)

object UserDictionaryStore {
    private const val PREF_KEY_UC = "uc"
    private val trLocale = Locale.forLanguageTag("tr-TR")

    fun load(context: Context): MutableList<UserWord> {
        val prefs = context.getSharedPreferences("t9", Context.MODE_PRIVATE)
        val uc = prefs.getString(PREF_KEY_UC, "") ?: ""
        val list = mutableListOf<UserWord>()
        uc.lines().forEach { line ->
            val parts = line.split('\t')
            if (parts.size == 2) {
                val count = parts[1].toIntOrNull()
                val word = parts[0].trim()
                if ((count != null) && word.isNotEmpty()) {
                    list.add(UserWord(word, count))
                }
            }
        }
        return list
    }

    fun save(context: Context, list: List<UserWord>) {
        val prefs = context.getSharedPreferences("t9", Context.MODE_PRIVATE)
        val sb = StringBuilder()
        list.forEach { (word, count) ->
            if (word.isNotBlank()) {
                sb.append(word.trim()).append('\t').append(count).append('\n')
            }
        }
        // putString("") boş listeyi de yazar; son kelime silindiğinde anahtar güncellenir.
        prefs.edit { putString(PREF_KEY_UC, sb.toString()) }
    }

    // Sistem sözlüğünden (best-effort) siler. WRITE_USER_DICTIONARY izni olmayan cihazlarda
    // SecurityException fırlatılır; bu durum sessizce yok sayılır ve klavyenin kendi sözlüğünü etkilemez.
    private fun removeFromSystemDictionary(context: Context, word: String) {
        try {
            context.contentResolver.delete(
                android.provider.UserDictionary.Words.CONTENT_URI,
                "${android.provider.UserDictionary.Words.WORD}=?",
                arrayOf(word)
            )
        } catch (_: Exception) {
        }
    }

    fun add(context: Context, word: String, count: Int = 1): Boolean {
        val clean = word.trim().lowercase(trLocale)
        if (clean.length < 2) return false

        val current = load(context)
        val index = current.indexOfFirst { (w) -> w.lowercase(trLocale) == clean }
        if (index >= 0) {
            current[index] = UserWord(clean, current[index].count + count)
        } else {
            current.add(UserWord(clean, count))
        }
        save(context, current)
        try {
            android.provider.UserDictionary.Words.addWord(context, clean, 250, null, null)
        } catch (_: Exception) { }
        return true
    }

    fun update(context: Context, oldWord: String, newWord: String): Boolean {
        val oldClean = oldWord.trim().lowercase(trLocale)
        val newClean = newWord.trim().lowercase(trLocale)
        if (newClean.length < 2) return false

        val current = load(context)
        val index = current.indexOfFirst { (w) -> w.lowercase(trLocale) == oldClean }
        if (index >= 0) {
            val existingCount = current[index].count
            current.removeAt(index)
            val newIndex = current.indexOfFirst { (w) -> w.lowercase(trLocale) == newClean }
            if (newIndex >= 0) {
                current[newIndex] = UserWord(newClean, current[newIndex].count + existingCount)
            } else {
                current.add(UserWord(newClean, existingCount))
            }
            save(context, current)
            removeFromSystemDictionary(context, oldClean)
            return true
        }
        return false
    }

    fun remove(context: Context, word: String) {
        val clean = word.trim().lowercase(trLocale)
        val current = load(context).filter { (w) -> w.lowercase(trLocale) != clean }
        save(context, current)
        removeFromSystemDictionary(context, clean)
    }
}
