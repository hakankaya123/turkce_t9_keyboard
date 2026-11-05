package com.example.comexamplet9keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class T9KeymapTest {

    @Test
    fun basicWordsMapToExpectedCodes() {
        assertEquals("247", T9Keymap.codeOf("bir"))
        assertEquals("4663", T9Keymap.codeOf("gone"))
        assertEquals("6363", T9Keymap.codeOf("mene"))
    }

    @Test
    fun turkishLettersShareKeysWithTheirBaseLetters() {
        assertEquals(T9Keymap.codeOf("cok"), T9Keymap.codeOf("çok"))
        assertEquals(T9Keymap.codeOf("sen"), T9Keymap.codeOf("şen"))
        assertEquals(T9Keymap.codeOf("gul"), T9Keymap.codeOf("gül"))
        assertEquals(T9Keymap.codeOf("ogun"), T9Keymap.codeOf("öğün"))
        assertEquals(T9Keymap.codeOf("kiz"), T9Keymap.codeOf("kız"))
    }

    @Test
    fun unknownCharactersBecomeQuestionMark() {
        assertEquals("2?3", T9Keymap.codeOf("a1d"))
        assertNull(T9Keymap.charToDigit['1'])
    }

    @Test
    fun emptyWordHasEmptyCode() {
        assertEquals("", T9Keymap.codeOf(""))
    }

    @Test
    fun everyLetterGroupIsCoveredByCharToDigit() {
        T9Keymap.groups.forEach { (digit, letters) ->
            letters.forEach { assertEquals(digit, T9Keymap.charToDigit[it]) }
        }
    }

    // words_tr.bin: 36 baytlık kayıtlar, frekansa göre azalan sırada olmalı (yükleme sırası buna dayanır).
    @Test
    fun binaryDictionaryIsWellFormedAndSortedByFrequency() {
        val f = File("src/main/assets/words_tr.bin")
        if (!f.exists()) return
        val bytes = f.readBytes()
        assertEquals(0, bytes.size % BinaryDictionaryReader.RECORD_SIZE)
        val buf = ByteBuffer.wrap(bytes)
        var prev = Int.MAX_VALUE
        var first: String? = null
        for (i in 0 until bytes.size / BinaryDictionaryReader.RECORD_SIZE) {
            val off = i * BinaryDictionaryReader.RECORD_SIZE
            val word = String(bytes, off, BinaryDictionaryReader.WORD_SIZE, Charsets.UTF_8).trimEnd('\u0000')
            val freq = buf.getInt(off + BinaryDictionaryReader.WORD_SIZE)
            if (first == null) first = word
            assertTrue("Frekans sırası bozuk: kayıt $i ($word)", freq <= prev)
            prev = freq
        }
        assertEquals("bir", first)
    }
}
