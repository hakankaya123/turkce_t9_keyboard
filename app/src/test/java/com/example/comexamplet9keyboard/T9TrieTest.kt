package com.example.comexamplet9keyboard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class T9TrieTest {

    private fun trie(vararg entries: Pair<String, Int>) = T9Trie().apply {
        entries.forEach { (code, idx) -> insert(T9Code(code), WordIndex(idx)) }
    }

    @Test
    fun exactCodeReturnsInsertedWord() {
        val t = trie("4663" to 0)
        assertArrayEquals(intArrayOf(0), t.search("4663"))
    }

    @Test
    fun prefixReturnsLongerWords() {
        val t = trie("46" to 0, "466" to 1, "4663" to 2)
        // Önce tam eşleşme, sonra daha uzun kodlar gelir
        assertArrayEquals(intArrayOf(0, 1, 2), t.search("46"))
    }

    @Test
    fun sameCodeKeepsInsertionOrder() {
        // Aynı tuş dizisi: ekleme sırası (= sözlükteki frekans sırası) korunmalı
        val t = trie("4663" to 5, "4663" to 2, "4663" to 9)
        assertArrayEquals(intArrayOf(5, 2, 9), t.search("4663"))
    }

    @Test
    fun duplicateIndexIsNotAddedTwice() {
        val t = trie("22" to 1, "22" to 1)
        assertArrayEquals(intArrayOf(1), t.search("22"))
    }

    @Test
    fun unknownPrefixReturnsEmpty() {
        val t = trie("22" to 1)
        assertEquals(0, t.search("99").size)
    }

    @Test
    fun nonDigitQueryReturnsEmpty() {
        val t = trie("22" to 1)
        assertEquals(0, t.search("2a").size)
    }

    @Test
    fun maxResultsIsRespected() {
        val t = T9Trie()
        for (i in 0 until 50) t.insert(T9Code("2"), WordIndex(i))
        assertEquals(10, t.search("2", maxResults = 10).size)
    }

    @Test
    fun emptyPrefixSearchesWholeTrie() {
        val t = trie("2" to 0, "3" to 1)
        val r = t.search("")
        assertTrue(r.contains(0) && r.contains(1))
    }
}
