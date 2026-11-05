package com.example.comexamplet9keyboard

import java.util.Locale

/**
 * Türkçe T9 tuş eşlemesi. Saf (Android'e bağımlı olmayan) olduğundan JVM testleriyle denenebilir.
 */
object T9Keymap {
    val tr: Locale = Locale.forLanguageTag("tr-TR")

    val groups: Map<Char, String> = mapOf(
        '2' to "abcçâ", '3' to "def", '4' to "ghiığî", '5' to "jkl",
        '6' to "mnoö", '7' to "pqrsş", '8' to "tuvüû", '9' to "wxyz"
    )

    val charToDigit: Map<Char, Char> =
        groups.flatMap { (d, s) -> s.map { it to d } }.toMap()

    /** Kelimenin tuş kodu; haritada olmayan karakterler '?' olur. Her çağrıda yeni StringBuilder kullanır (iş parçacığı güvenli). */
    fun codeOf(w: String): String {
        val sb = StringBuilder(w.length)
        for (c in w) sb.append(charToDigit[c] ?: '?')
        return sb.toString()
    }
}
